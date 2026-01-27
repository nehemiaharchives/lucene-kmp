package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.codecs.lucene90.blocktree.SegmentTermsEnum.OutputAccumulator
import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.util.fst.FST
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.experimental.and

// TODO: can we share this with the frame in STE
internal class IntersectTermsEnumFrame(private val ite: IntersectTermsEnum, val ord: Int) {
    var fp: Long = 0
    var fpOrig: Long = 0
    var fpEnd: Long = 0
    var lastSubFP: Long = 0

    // private static boolean DEBUG = IntersectTermsEnum.DEBUG;
    // State in automaton
    var state: Int = 0
        set(state) {
            field = state
            transitionIndex = 0
            transitionCount = ite.automaton.getNumTransitions(state)
            if (transitionCount != 0) {
                ite.automaton.initTransition(state, transition)
                ite.automaton.getNextTransition(transition)
            } else {
                // Must set min to -1 so the "label < min" check never falsely triggers:

                transition.min = -1

                // Must set max to -1 so we immediately realize we need to step to the next transition and
                // then pop this frame:
                transition.max = -1
            }
        }

    // State just before the last label
    var lastState: Int = 0

    var metaDataUpto: Int = 0

    var suffixBytes: ByteArray = ByteArray(128)
    val suffixesReader: ByteArrayDataInput = ByteArrayDataInput()

    var suffixLengthBytes: ByteArray
    val suffixLengthsReader: ByteArrayDataInput

    var statBytes: ByteArray = ByteArray(64)
    var statsSingletonRunLength: Int = 0
    val statsReader: ByteArrayDataInput = ByteArrayDataInput()

    val floorDataReader: ByteArrayDataInput = ByteArrayDataInput()

    // Length of prefix shared by all terms in this block
    var prefix: Int = 0

    // Number of entries (term or sub-block) in this block
    var entCount: Int = 0

    // Which term we will next read
    var nextEnt: Int = 0

    // True if this block is either not a floor block,
    // or, it's the last sub-block of a floor block
    var isLastInFloor: Boolean = false

    // True if all entries are terms
    var isLeafBlock: Boolean = false

    var numFollowFloorBlocks: Int = 0
    var nextFloorLabel: Int = 0

    val transition: Transition = Transition()
    var transitionIndex: Int = 0
    var transitionCount: Int = 0

    var arc: FST.Arc<BytesRef>? = null

    val termState: BlockTermState = ite.fr.parent.postingsReader.newTermState()

    // metadata buffer
    var bytes: ByteArray = ByteArray(32)

    val bytesReader: ByteArrayDataInput = ByteArrayDataInput()

    var outputNum: Int = 0

    var startBytePos: Int = 0
    var suffix: Int = 0

    init {
        this.termState.totalTermFreq = -1
        suffixLengthBytes = ByteArray(32)
        suffixLengthsReader = ByteArrayDataInput()
    }

    @Throws(IOException::class)
    fun loadNextFloorBlock() {
        require(numFollowFloorBlocks > 0) { "nextFloorLabel=$nextFloorLabel" }

        do {
            fp = fpOrig + (floorDataReader.readVLong() ushr 1)
            numFollowFloorBlocks--
            nextFloorLabel = if (numFollowFloorBlocks != 0) {
                (floorDataReader.readByte() and 0xff.toByte()).toInt()
            } else {
                256
            }
        } while (numFollowFloorBlocks != 0 && nextFloorLabel <= transition.min)

        load(blockCode = null)
    }

    @Throws(IOException::class)
    fun load(frameIndexData: BytesRef) {
        floorDataReader.reset(frameIndexData.bytes, frameIndexData.offset, frameIndexData.length)
        load(ite.fr.readVLongOutput(floorDataReader))
    }

    @Throws(IOException::class)
    fun load(outputAccumulator: OutputAccumulator) {
        outputAccumulator.prepareRead()
        val code = ite.fr.readVLongOutput(outputAccumulator)
        outputAccumulator.setFloorData(floorDataReader)
        load(code)
    }

    @Throws(IOException::class)
    fun load(blockCode: Long?) {
        if (blockCode != null) {
            // This block is the first one in a possible sequence of floor blocks corresponding to a
            // single seek point from the FST terms index
            if ((blockCode and Lucene90BlockTreeTermsReader.OUTPUT_FLAG_IS_FLOOR.toLong()) != 0L) {
                // Floor frame
                numFollowFloorBlocks = floorDataReader.readVInt()
                nextFloorLabel = (floorDataReader.readByte() and 0xff.toByte()).toInt()

                // If current state is not accept, and has transitions, we must process
                // first block in case it has empty suffix:
                if (!ite.runAutomaton.isAccept(state) && transitionCount != 0) {
                    // Maybe skip floor blocks:
                    require(transitionIndex == 0) { "transitionIndex=$transitionIndex" }
                    while (numFollowFloorBlocks != 0 && nextFloorLabel <= transition.min) {
                        fp = fpOrig + (floorDataReader.readVLong() ushr 1)
                        numFollowFloorBlocks--
                        nextFloorLabel = if (numFollowFloorBlocks != 0) {
                            (floorDataReader.readByte() and 0xff.toByte()).toInt()
                        } else {
                            256
                        }
                    }
                }
            }
        }

        ite.`in`.seek(fp)
        val code = ite.`in`.readVInt()
        entCount = code ushr 1
        require(entCount > 0)
        isLastInFloor = (code and 1) != 0

        // term suffixes:
        val codeL = ite.`in`.readVLong()
        isLeafBlock = (codeL and 0x04L) != 0L
        val numSuffixBytes = (codeL ushr 3).toInt()
        if (suffixBytes.size < numSuffixBytes) {
            suffixBytes = ByteArray(ArrayUtil.oversize(numSuffixBytes, 1))
        }
        val compressionAlg: CompressionAlgorithm
        try {
            compressionAlg = CompressionAlgorithm.byCode(codeL.toInt() and 0x03)
        } catch (e: IllegalArgumentException) {
            throw CorruptIndexException(e.message!!, ite.`in`, e)
        }
        compressionAlg.read(ite.`in`, suffixBytes, numSuffixBytes)
        suffixesReader.reset(suffixBytes, 0, numSuffixBytes)

        var numSuffixLengthBytes = ite.`in`.readVInt()
        val allEqual = (numSuffixLengthBytes and 0x01) != 0
        numSuffixLengthBytes = numSuffixLengthBytes ushr 1
        if (suffixLengthBytes.size < numSuffixLengthBytes) {
            suffixLengthBytes = ByteArray(ArrayUtil.oversize(numSuffixLengthBytes, 1))
        }
        if (allEqual) {
            Arrays.fill(suffixLengthBytes, 0, numSuffixLengthBytes, ite.`in`.readByte())
        } else {
            ite.`in`.readBytes(suffixLengthBytes, 0, numSuffixLengthBytes)
        }
        suffixLengthsReader.reset(suffixLengthBytes, 0, numSuffixLengthBytes)

        // stats
        var numBytes = ite.`in`.readVInt()
        if (statBytes.size < numBytes) {
            statBytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
        }
        ite.`in`.readBytes(statBytes, 0, numBytes)
        statsReader.reset(statBytes, 0, numBytes)
        statsSingletonRunLength = 0
        metaDataUpto = 0

        termState.termBlockOrd = 0
        nextEnt = 0

        // metadata
        numBytes = ite.`in`.readVInt()
        if (bytes.size < numBytes) {
            bytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
        }
        ite.`in`.readBytes(bytes, 0, numBytes)
        bytesReader.reset(bytes, 0, numBytes)

        if (!isLastInFloor) {
            // Sub-blocks of a single floor block are always
            // written one after another -- tail recurse:
            fpEnd = ite.`in`.filePointer
        }
    }

    // TODO: maybe add scanToLabel; should give perf boost
    // Decodes next entry; returns true if it's a sub-block
    fun next(): Boolean {
        if (isLeafBlock) {
            nextLeaf()
            return false
        } else {
            return nextNonLeaf()
        }
    }

    fun nextLeaf() {
        require(
            nextEnt != -1 && nextEnt < entCount
        ) { "nextEnt=$nextEnt entCount=$entCount fp=$fp" }
        nextEnt++
        suffix = suffixLengthsReader.readVInt()
        startBytePos = suffixesReader.position
        suffixesReader.skipBytes(suffix.toLong())
    }

    fun nextNonLeaf(): Boolean {
        require(
            nextEnt != -1 && nextEnt < entCount
        ) { "nextEnt=$nextEnt entCount=$entCount fp=$fp" }
        nextEnt++
        val code: Int = suffixLengthsReader.readVInt()
        suffix = code ushr 1
        startBytePos = suffixesReader.position
        suffixesReader.skipBytes(suffix.toLong())
        if ((code and 1) == 0) {
            // A normal term
            termState.termBlockOrd++
            return false
        } else {
            // A sub-block; make sub-FP absolute:
            lastSubFP = fp - suffixLengthsReader.readVLong()
            return true
        }
    }

    val termBlockOrd: Int
        get() = if (isLeafBlock) nextEnt else termState.termBlockOrd

    @Throws(IOException::class)
    fun decodeMetaData() {
        // lazily catch up on metadata decode:

        val limit = this.termBlockOrd
        var absolute = metaDataUpto == 0
        require(limit > 0)

        // TODO: better API would be "jump straight to term=N"
        while (metaDataUpto < limit) {
            // TODO: we could make "tiers" of metadata, ie,
            // decode docFreq/totalTF but don't decode postings
            // metadata; this way caller could get
            // docFreq/totalTF w/o paying decode cost for
            // postings

            // TODO: if docFreq were bulk decoded we could
            // just skipN here:

            // stats

            if (statsSingletonRunLength > 0) {
                termState.docFreq = 1
                termState.totalTermFreq = 1
                statsSingletonRunLength--
            } else {
                val token: Int = statsReader.readVInt()
                if ((token and 1) == 1) {
                    termState.docFreq = 1
                    termState.totalTermFreq = 1
                    statsSingletonRunLength = token ushr 1
                } else {
                    termState.docFreq = token ushr 1
                    if (ite.fr.fieldInfo.indexOptions === IndexOptions.DOCS) {
                        termState.totalTermFreq = termState.docFreq.toLong()
                    } else {
                        termState.totalTermFreq = termState.docFreq + statsReader.readVLong()
                    }
                }
            }
            // metadata
            ite.fr.parent.postingsReader.decodeTerm(bytesReader, ite.fr.fieldInfo, termState, absolute)

            metaDataUpto++
            absolute = false
        }
        termState.termBlockOrd = metaDataUpto
    }
}
