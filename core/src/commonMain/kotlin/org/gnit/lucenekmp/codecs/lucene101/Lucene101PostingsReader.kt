package org.gnit.lucenekmp.codecs.lucene101

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.BLOCK_SIZE
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.DOC_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.LEVEL1_NUM_DOCS
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.META_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.PAY_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.POS_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.TERMS_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.VERSION_CURRENT
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.VERSION_START
import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.PostingsReaderBase
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.IntBlockTermState
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil
import org.gnit.lucenekmp.internal.vectorization.VectorizationProvider
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.VectorUtil
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.bitCount
import kotlin.math.min

/**
 * Concrete class that reads docId(maybe frq,pos,offset,payloads) list with postings format.
 *
 * @lucene.experimental
 */
class Lucene101PostingsReader(state: SegmentReadState) : PostingsReaderBase() {
    private var docIn: IndexInput? = null
    private var posIn: IndexInput? = null
    private var payIn: IndexInput? = null

    private var maxNumImpactsAtLevel0 = 0
    private var maxImpactNumBytesAtLevel0 = 0
    private var maxNumImpactsAtLevel1 = 0
    private var maxImpactNumBytesAtLevel1 = 0

    /** Sole constructor.  */
    init {
        val metaName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, Lucene101PostingsFormat.META_EXTENSION
            )
        val expectedDocFileLength: Long
        val expectedPosFileLength: Long
        val expectedPayFileLength: Long
        var metaIn: ChecksumIndexInput? = null
        var success = false
        var version: Int
        try {
            metaIn = state.directory.openChecksumInput(metaName)
            version =
                CodecUtil.checkIndexHeader(
                    metaIn,
                    META_CODEC,
                    VERSION_START,
                    VERSION_CURRENT,
                    state.segmentInfo.getId(),
                    state.segmentSuffix
                )
            maxNumImpactsAtLevel0 = metaIn.readInt()
            maxImpactNumBytesAtLevel0 = metaIn.readInt()
            maxNumImpactsAtLevel1 = metaIn.readInt()
            maxImpactNumBytesAtLevel1 = metaIn.readInt()
            expectedDocFileLength = metaIn.readLong()
            if (state.fieldInfos.hasProx()) {
                expectedPosFileLength = metaIn.readLong()
                expectedPayFileLength = if (state.fieldInfos.hasPayloads() || state.fieldInfos.hasOffsets()) {
                    metaIn.readLong()
                } else {
                    -1
                }
            } else {
                expectedPosFileLength = -1
                expectedPayFileLength = -1
            }
            CodecUtil.checkFooter(metaIn, null)
            success = true
        } catch (t: Throwable) {
            if (metaIn != null) {
                CodecUtil.checkFooter(metaIn, t)
                throw AssertionError("unreachable")
            } else {
                throw t
            }
        } finally {
            if (success) {
                metaIn?.close()
            } else {
                IOUtils.closeWhileHandlingException(metaIn)
            }
        }

        success = false
        var docIn: IndexInput? = null
        var posIn: IndexInput? = null
        var payIn: IndexInput? = null

        // NOTE: these data files are too costly to verify checksum against all the bytes on open,
        // but for now we at least verify proper structure of the checksum footer: which looks
        // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
        // such as file truncation.
        val docName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, Lucene101PostingsFormat.DOC_EXTENSION
            )
        try {
            // Postings have a forward-only access pattern, so pass ReadAdvice.NORMAL to perform
            // readahead.
            docIn = state.directory.openInput(docName, state.context.withReadAdvice(ReadAdvice.NORMAL))
            CodecUtil.checkIndexHeader(
                docIn, DOC_CODEC, version, version, state.segmentInfo.getId(), state.segmentSuffix
            )
            CodecUtil.retrieveChecksum(docIn, expectedDocFileLength)

            if (state.fieldInfos.hasProx()) {
                val proxName: String =
                    IndexFileNames.segmentFileName(
                        state.segmentInfo.name, state.segmentSuffix, Lucene101PostingsFormat.POS_EXTENSION
                    )
                posIn = state.directory.openInput(proxName, state.context)
                CodecUtil.checkIndexHeader(
                    posIn, POS_CODEC, version, version, state.segmentInfo.getId(), state.segmentSuffix
                )
                CodecUtil.retrieveChecksum(posIn, expectedPosFileLength)

                if (state.fieldInfos.hasPayloads() || state.fieldInfos.hasOffsets()) {
                    val payName: String =
                        IndexFileNames.segmentFileName(
                            state.segmentInfo.name,
                            state.segmentSuffix,
                            Lucene101PostingsFormat.PAY_EXTENSION
                        )
                    payIn = state.directory.openInput(payName, state.context)
                    CodecUtil.checkIndexHeader(
                        payIn, PAY_CODEC, version, version, state.segmentInfo.getId(), state.segmentSuffix
                    )
                    CodecUtil.retrieveChecksum(payIn, expectedPayFileLength)
                }
            }

            this.docIn = docIn
            this.posIn = posIn
            this.payIn = payIn
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(docIn, posIn, payIn)
            }
        }
    }

    @Throws(IOException::class)
    override fun init(termsIn: IndexInput, state: SegmentReadState) {
        // Make sure we are talking to the matching postings writer
        CodecUtil.checkIndexHeader(
            termsIn,
            TERMS_CODEC,
            VERSION_START,
            VERSION_CURRENT,
            state.segmentInfo.getId(),
            state.segmentSuffix
        )
        val indexBlockSize: Int = termsIn.readVInt()
        check(indexBlockSize == BLOCK_SIZE) {
            ("index-time BLOCK_SIZE ("
                    + indexBlockSize
                    + ") != read-time BLOCK_SIZE ("
                    + BLOCK_SIZE
                    + ")")
        }
    }

    override fun newTermState(): BlockTermState {
        return IntBlockTermState()
    }

    override fun close() {
        IOUtils.close(docIn, posIn, payIn)
    }

    @Throws(IOException::class)
    override fun decodeTerm(
        `in`: DataInput, fieldInfo: FieldInfo, _termState: BlockTermState, absolute: Boolean
    ) {
        val termState: IntBlockTermState = _termState as IntBlockTermState
        if (absolute) {
            termState.docStartFP = 0
            termState.posStartFP = 0
            termState.payStartFP = 0
        }

        val l: Long = `in`.readVLong()
        if ((l and 0x01L) == 0L) {
            termState.docStartFP += l ushr 1
            if (termState.docFreq == 1) {
                termState.singletonDocID = `in`.readVInt()
            } else {
                termState.singletonDocID = -1
            }
        } else {
            require(!absolute)
            require(termState.singletonDocID != -1)
            termState.singletonDocID += BitUtil.zigZagDecode(l ushr 1).toInt()
        }

        if (fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) {
            termState.posStartFP += `in`.readVLong()
            if ((fieldInfo
                    .indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
                || fieldInfo.hasPayloads()
            ) {
                termState.payStartFP += `in`.readVLong()
            }
            if (termState.totalTermFreq > BLOCK_SIZE) {
                termState.lastPosBlockOffset = `in`.readVLong()
            } else {
                termState.lastPosBlockOffset = -1
            }
        }
    }

    @Throws(IOException::class)
    override fun postings(
        fieldInfo: FieldInfo, termState: BlockTermState, reuse: PostingsEnum?, flags: Int
    ): PostingsEnum {
        return (if (reuse is BlockPostingsEnum
            && reuse.canReuse(docIn!!, fieldInfo, flags, false)
        )
            reuse
        else
            this.BlockPostingsEnum(fieldInfo, flags, false)).reset(termState as IntBlockTermState, flags)
    }

    @Throws(IOException::class)
    override fun impacts(fieldInfo: FieldInfo, state: BlockTermState, flags: Int): ImpactsEnum {
        return this.BlockPostingsEnum(fieldInfo, flags, true).reset(state as IntBlockTermState, flags)
    }

    private enum class DeltaEncoding {
        /**
         * Deltas between consecutive docs are stored as packed integers, ie. the block is encoded
         * using Frame Of Reference (FOR).
         */
        PACKED,

        /**
         * Deltas between consecutive docs are stored using unary coding, ie. `delta-1` zero
         * bits followed by a one bit, ie. the block is encoded as an offset plus a bit set.
         */
        UNARY
    }

    internal inner class BlockPostingsEnum(fieldInfo: FieldInfo, val flags: Int, val needsImpacts: Boolean) :
        ImpactsEnum() {


        private var forDeltaUtil: ForDeltaUtil? = null
        private var pforUtil: PForUtil? = null

        /* Variables that store the content of a block and the current position within this block */ /* Shared variables */
        private var encoding: DeltaEncoding? = null
        private var doc = 0 // doc we last read
        private var debugCalls: Long = 0

        /* Variables when the block is stored as packed deltas (Frame Of Reference) */
        private val docBuffer = IntArray(BLOCK_SIZE)

        /* Variables when the block is stored as a bit set */ // Since we use a bit set when it's more storage-efficient, the bit set cannot have more than
        // BLOCK_SIZE*32 bits, which is the maximum possible storage requirement with FOR.
        private val docBitSet: FixedBitSet = FixedBitSet(BLOCK_SIZE * Int.SIZE_BITS)
        private var docBitSetBase = 0

        // Reuse docBuffer for cumulative pop counts of the words of the bit set.
        private val docCumulativeWordPopCounts = docBuffer

        // level 0 skip data
        private var level0LastDocID = 0
        private var level0DocEndFP: Long = 0

        // level 1 skip data
        private var level1LastDocID = 0
        private var level1DocEndFP: Long = 0
        private var level1DocCountUpto = 0

        private var docFreq = 0 // number of docs in this posting list
        private var totalTermFreq: Long = 0 // sum of freqBuffer in this posting list (or docFreq when omitted)

        private var singletonDocID = 0 // docid when there is a single pulsed posting, otherwise -1

        private var docCountLeft = 0 // number of remaining docs in this postings list
        private var prevDocID = 0 // last doc ID of the previous block

        private var docBufferSize = 0
        private var docBufferUpto = 0

        private var docIn: IndexInput? = null
        private var docInUtil: PostingDecodingUtil? = null

        private val freqBuffer = IntArray(BLOCK_SIZE)
        private val posDeltaBuffer: IntArray?

        private val payloadLengthBuffer: IntArray?
        private val offsetStartDeltaBuffer: IntArray?
        private val offsetLengthBuffer: IntArray?

        private var payloadBytes: ByteArray?
        private var payloadByteUpto = 0
        private var payloadLength = 0

        private var lastStartOffset = 0
        private var startOffset = 0
        private var endOffset = 0

        private var posBufferUpto = 0

        var posIn: IndexInput? = null
        var posInUtil: PostingDecodingUtil? = null
        var payIn: IndexInput? = null
        var payInUtil: PostingDecodingUtil? = null
        override var payload: BytesRef? = null
            get() {
                return if (!needsPayloads || payloadLength == 0) {
                    null
                } else {
                    payload
                }
            }

        val options: IndexOptions = fieldInfo.indexOptions
        val indexHasFreq: Boolean = options >= IndexOptions.DOCS_AND_FREQS
        val indexHasPos: Boolean = options >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        val indexHasOffsets: Boolean = options >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
        val indexHasPayloads: Boolean = fieldInfo.hasPayloads()
        val indexHasOffsetsOrPayloads: Boolean = indexHasOffsets || indexHasPayloads

        val needsFreq: Boolean = indexHasFreq && featureRequested(flags, FREQS)
        val needsPos: Boolean = indexHasPos && featureRequested(flags, POSITIONS)
        val needsOffsets: Boolean = indexHasOffsets && featureRequested(flags, OFFSETS)
        val needsPayloads: Boolean = indexHasPayloads && featureRequested(flags, PAYLOADS)
        val needsOffsetsOrPayloads: Boolean = needsOffsets || needsPayloads
        val needsDocsAndFreqsOnly: Boolean = !needsPos && !needsImpacts

        private var freqFP: Long = 0 // offset of the freq block

        private var position = 0 // current position

        // value of docBufferUpto on the last doc ID when positions have been read
        private var posDocBufferUpto = 0

        // how many positions "behind" we are; nextPosition must
        // skip these to "catch up":
        private var posPendingCount = 0

        // File pointer where the last (vInt encoded) pos delta
        // block is.  We need this to know whether to bulk
        // decode vs vInt decode the block:
        private var lastPosBlockFP: Long = 0

        // level 0 skip data
        private var level0PosEndFP: Long = 0
        private var level0BlockPosUpto = 0
        private var level0PayEndFP: Long = 0
        private var level0BlockPayUpto = 0
        private var level0SerializedImpacts: BytesRef? = null
        private var level0Impacts: MutableImpactList? = null

        // level 1 skip data
        private var level1PosEndFP: Long = 0
        private var level1BlockPosUpto = 0
        private var level1PayEndFP: Long = 0
        private var level1BlockPayUpto = 0
        private var level1SerializedImpacts: BytesRef? = null
        private var level1Impacts: MutableImpactList? = null

        // true if we shallow-advanced to a new block that we have not decoded yet
        private var needsRefilling = false

        fun canReuse(
            docIn: IndexInput, fieldInfo: FieldInfo, flags: Int, needsImpacts: Boolean
        ): Boolean {
            return docIn === this@Lucene101PostingsReader.docIn && options === fieldInfo.indexOptions && indexHasPayloads == fieldInfo.hasPayloads() && this.flags == flags && this.needsImpacts == needsImpacts
        }

        @Throws(IOException::class)
        fun reset(termState: IntBlockTermState, flags: Int): BlockPostingsEnum {
            docFreq = termState.docFreq
            singletonDocID = termState.singletonDocID
            if (docFreq > 1) {
                if (docIn == null) {
                    // lazy init
                    docIn = this@Lucene101PostingsReader.docIn!!.clone()
                    docInUtil = VECTORIZATION_PROVIDER.newPostingDecodingUtil(docIn!!)
                }
                prefetchPostings(docIn!!, termState)
            }

            if (forDeltaUtil == null && docFreq >= BLOCK_SIZE) {
                forDeltaUtil = ForDeltaUtil()
            }
            totalTermFreq = if (indexHasFreq) termState.totalTermFreq else termState.docFreq.toLong()
            if (needsFreq && pforUtil == null && totalTermFreq >= BLOCK_SIZE) {
                pforUtil = PForUtil()
            }

            // Where this term's postings start in the .pos file:
            val posTermStartFP: Long = termState.posStartFP
            // Where this term's payloads/offsets start in the .pay
            // file:
            val payTermStartFP: Long = termState.payStartFP
            if (posIn != null) {
                posIn!!.seek(posTermStartFP)
                if (payIn != null) {
                    payIn!!.seek(payTermStartFP)
                }
            }
            level1PosEndFP = posTermStartFP
            level1PayEndFP = payTermStartFP
            level0PosEndFP = posTermStartFP
            level0PayEndFP = payTermStartFP
            posPendingCount = 0
            payloadByteUpto = 0
            lastPosBlockFP = if (termState.totalTermFreq < BLOCK_SIZE) {
                posTermStartFP
            } else if (termState.totalTermFreq == BLOCK_SIZE.toLong()) {
                -1
            } else {
                posTermStartFP + termState.lastPosBlockOffset
            }

            level1BlockPosUpto = 0
            level1BlockPayUpto = 0
            level0BlockPosUpto = 0
            level0BlockPayUpto = 0
            posBufferUpto = BLOCK_SIZE

            doc = -1
            prevDocID = -1
            docCountLeft = docFreq
            freqFP = -1L
            level0LastDocID = -1
            if (docFreq < LEVEL1_NUM_DOCS) {
                level1LastDocID = NO_MORE_DOCS
                if (docFreq > 1) {
                    docIn!!.seek(termState.docStartFP)
                }
            } else {
                level1LastDocID = -1
                level1DocEndFP = termState.docStartFP
            }
            level1DocCountUpto = 0
            docBufferSize = BLOCK_SIZE
            docBufferUpto = BLOCK_SIZE
            posDocBufferUpto = BLOCK_SIZE

            return this
        }

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            if (freqFP != -1L) {
                docIn!!.seek(freqFP)
                pforUtil!!.decode(docInUtil!!, freqBuffer)
                freqFP = -1
            }
            return freqBuffer[docBufferUpto - 1]
        }

        @Throws(IOException::class)
        private fun refillFullBlock() {
            val bitsPerValue: Int = docIn!!.readByte().toInt()
            if (bitsPerValue > 0) {
                // block is encoded as 128 packed integers that record the delta between doc IDs
                forDeltaUtil!!.decodeAndPrefixSum(bitsPerValue, docInUtil!!, prevDocID, docBuffer)
                encoding = DeltaEncoding.PACKED
            } else {
                // block is encoded as a bit set
                require(level0LastDocID != NO_MORE_DOCS)
                docBitSetBase = prevDocID + 1
                val numLongs: Int
                if (bitsPerValue == 0) {
                    // 0 is used to record that all 128 docs in the block are consecutive
                    numLongs = BLOCK_SIZE / Long.SIZE_BITS // 2
                    docBitSet.set(0, BLOCK_SIZE)
                } else {
                    numLongs = -bitsPerValue
                    docIn!!.readLongs(docBitSet.bits, 0, numLongs)
                }
                if (needsFreq) {
                    // Note: we know that BLOCK_SIZE bits are set, so no need to compute the cumulative pop
                    // count at the last index, it will be BLOCK_SIZE.
                    // Note: this for loop auto-vectorizes
                    for (i in 0..<numLongs - 1) {
                        docCumulativeWordPopCounts[i] = Long.bitCount(docBitSet.bits[i])
                    }
                    for (i in 1..<numLongs - 1) {
                        docCumulativeWordPopCounts[i] += docCumulativeWordPopCounts[i - 1]
                    }
                    docCumulativeWordPopCounts[numLongs - 1] = BLOCK_SIZE
                    require(
                        docCumulativeWordPopCounts[numLongs - 2]
                                + Long.bitCount(docBitSet.bits[numLongs - 1])
                                == BLOCK_SIZE
                    )
                }
                encoding = DeltaEncoding.UNARY
            }
            if (indexHasFreq) {
                if (needsFreq) {
                    freqFP = docIn!!.filePointer
                }
                PForUtil.skip(docIn!!)
            }
            docCountLeft -= BLOCK_SIZE
            prevDocID = docBuffer[BLOCK_SIZE - 1]
            docBufferUpto = 0
            posDocBufferUpto = 0
        }

        @Throws(IOException::class)
        private fun refillRemainder() {
            require(docCountLeft >= 0 && docCountLeft < BLOCK_SIZE)
            if (docFreq == 1) {
                docBuffer[0] = singletonDocID
                freqBuffer[0] = totalTermFreq.toInt()
                docBuffer[1] = NO_MORE_DOCS
                require(freqFP == -1L)
                docCountLeft = 0
                docBufferSize = 1
            } else {
                // Read vInts:
                PostingsUtil.readVIntBlock(
                    docIn!!, docBuffer, freqBuffer, docCountLeft, indexHasFreq, needsFreq
                )
                prefixSum(docBuffer, docCountLeft, prevDocID.toLong())
                docBuffer[docCountLeft] = NO_MORE_DOCS
                freqFP = -1L
                docBufferSize = docCountLeft
                docCountLeft = 0
            }
            prevDocID = docBuffer[BLOCK_SIZE - 1]
            docBufferUpto = 0
            posDocBufferUpto = 0
            encoding = DeltaEncoding.PACKED
            require(docBuffer[docBufferSize] == NO_MORE_DOCS)
        }

        @Throws(IOException::class)
        private fun refillDocs() {
            require(docCountLeft >= 0)

            if (docCountLeft >= BLOCK_SIZE) {
                refillFullBlock()
            } else {
                refillRemainder()
            }
        }

        @Throws(IOException::class)
        private fun skipLevel1To(target: Int) {
            while (true) {
                prevDocID = level1LastDocID
                level0LastDocID = level1LastDocID
                docIn!!.seek(level1DocEndFP)
                level0PosEndFP = level1PosEndFP
                level0BlockPosUpto = level1BlockPosUpto
                level0PayEndFP = level1PayEndFP
                level0BlockPayUpto = level1BlockPayUpto
                docCountLeft = docFreq - level1DocCountUpto
                level1DocCountUpto += LEVEL1_NUM_DOCS

                if (docCountLeft < LEVEL1_NUM_DOCS) {
                    level1LastDocID = NO_MORE_DOCS
                    break
                }

                level1LastDocID += docIn!!.readVInt()
                val delta: Long = docIn!!.readVLong()
                level1DocEndFP = delta + docIn!!.filePointer

                if (indexHasFreq) {
                    val skip1EndFP: Long = docIn!!.readShort() + docIn!!.filePointer
                    val numImpactBytes: Int = docIn!!.readShort().toInt()
                    if (needsImpacts && level1LastDocID >= target) {
                        docIn!!.readBytes(level1SerializedImpacts!!.bytes, 0, numImpactBytes)
                        level1SerializedImpacts!!.length = numImpactBytes
                    } else {
                        docIn!!.skipBytes(numImpactBytes.toLong())
                    }
                    if (indexHasPos) {
                        level1PosEndFP += docIn!!.readVLong()
                        level1BlockPosUpto = docIn!!.readByte().toInt()
                        if (indexHasOffsetsOrPayloads) {
                            level1PayEndFP += docIn!!.readVLong()
                            level1BlockPayUpto = docIn!!.readVInt()
                        }
                    }
                    require(docIn!!.filePointer == skip1EndFP)
                }

                if (level1LastDocID >= target) {
                    break
                }
            }
        }

        @Throws(IOException::class)
        private fun doMoveToNextLevel0Block() {
            require(doc == level0LastDocID)
            if (posIn != null) {
                if (level0PosEndFP >= posIn!!.filePointer) {
                    posIn!!.seek(level0PosEndFP)
                    posPendingCount = level0BlockPosUpto
                    if (payIn != null) {
                        require(level0PayEndFP >= payIn!!.filePointer)
                        payIn!!.seek(level0PayEndFP)
                        payloadByteUpto = level0BlockPayUpto
                    }
                    posBufferUpto = BLOCK_SIZE
                } else {
                    require(freqFP == -1L)
                    posPendingCount += sumOverRange(freqBuffer, posDocBufferUpto, BLOCK_SIZE)
                }
            }

            if (docCountLeft >= BLOCK_SIZE) {
                docIn!!.readVLong() // level0NumBytes
                val docDelta = readVInt15(docIn!!)
                level0LastDocID += docDelta
                val blockLength = readVLong15(docIn!!)
                level0DocEndFP = docIn!!.filePointer + blockLength
                if (indexHasFreq) {
                    val numImpactBytes: Int = docIn!!.readVInt()
                    if (needsImpacts) {
                        docIn!!.readBytes(level0SerializedImpacts!!.bytes, 0, numImpactBytes)
                        level0SerializedImpacts!!.length = numImpactBytes
                    } else {
                        docIn!!.skipBytes(numImpactBytes.toLong())
                    }

                    if (indexHasPos) {
                        level0PosEndFP += docIn!!.readVLong()
                        level0BlockPosUpto = docIn!!.readByte().toInt()
                        if (indexHasOffsetsOrPayloads) {
                            level0PayEndFP += docIn!!.readVLong()
                            level0BlockPayUpto = docIn!!.readVInt()
                        }
                    }
                }
                refillFullBlock()
            } else {
                level0LastDocID = NO_MORE_DOCS
                refillRemainder()
            }
        }

        @Throws(IOException::class)
        private fun moveToNextLevel0Block() {
            if (doc == NO_MORE_DOCS) {
                return
            }
            if (doc == level1LastDocID) { // advance level 1 skip data
                skipLevel1To(doc + 1)
            }

            // Now advance level 0 skip data
            prevDocID = level0LastDocID

            if (needsDocsAndFreqsOnly && docCountLeft >= BLOCK_SIZE) {
                // Optimize the common path for exhaustive evaluation
                val level0NumBytes: Long = docIn!!.readVLong()
                val level0End: Long = docIn!!.filePointer + level0NumBytes
                level0LastDocID += readVInt15(docIn!!)
                docIn!!.seek(level0End)
                refillFullBlock()
            } else {
                doMoveToNextLevel0Block()
            }
        }

        @Throws(IOException::class)
        private fun readLevel0PosData() {
            level0PosEndFP += docIn!!.readVLong()
            level0BlockPosUpto = docIn!!.readByte().toInt()
            if (indexHasOffsetsOrPayloads) {
                level0PayEndFP += docIn!!.readVLong()
                level0BlockPayUpto = docIn!!.readVInt()
            }
        }

        @Throws(IOException::class)
        private fun seekPosData(posFP: Long, posUpto: Int, payFP: Long, payUpto: Int) {
            // If nextBlockPosFP is less than the current FP, it means that the block of positions for
            // the first docs of the next block are already decoded. In this case we just accumulate
            // frequencies into posPendingCount instead of seeking backwards and decoding the same pos
            // block again.
            if (posFP >= posIn!!.filePointer) {
                posIn!!.seek(posFP)
                posPendingCount = posUpto
                if (payIn != null) { // needs payloads or offsets
                    require(level0PayEndFP >= payIn!!.filePointer)
                    payIn!!.seek(payFP)
                    payloadByteUpto = payUpto
                }
                posBufferUpto = BLOCK_SIZE
            } else {
                posPendingCount += sumOverRange(freqBuffer, posDocBufferUpto, BLOCK_SIZE)
            }
        }

        @Throws(IOException::class)
        private fun skipLevel0To(target: Int) {
            var posFP: Long
            var posUpto: Int
            var payFP: Long
            var payUpto: Int

            while (true) {
                prevDocID = level0LastDocID

                posFP = level0PosEndFP
                posUpto = level0BlockPosUpto
                payFP = level0PayEndFP
                payUpto = level0BlockPayUpto

                if (docCountLeft >= BLOCK_SIZE) {
                    val numSkipBytes: Long = docIn!!.readVLong()
                    val skip0End: Long = docIn!!.filePointer + numSkipBytes
                    val docDelta = readVInt15(docIn!!)
                    level0LastDocID += docDelta
                    val found = target <= level0LastDocID
                    val blockLength = readVLong15(docIn!!)
                    level0DocEndFP = docIn!!.filePointer + blockLength

                    if (indexHasFreq) {
                        if (!found && !needsPos) {
                            docIn!!.seek(skip0End)
                        } else {
                            val numImpactBytes: Int = docIn!!.readVInt()
                            if (needsImpacts && found) {
                                docIn!!.readBytes(level0SerializedImpacts!!.bytes, 0, numImpactBytes)
                                level0SerializedImpacts!!.length = numImpactBytes
                            } else {
                                docIn!!.skipBytes(numImpactBytes.toLong())
                            }

                            if (needsPos) {
                                readLevel0PosData()
                            } else {
                                docIn!!.seek(skip0End)
                            }
                        }
                    }

                    if (found) {
                        break
                    }

                    docIn!!.seek(level0DocEndFP)
                    docCountLeft -= BLOCK_SIZE
                } else {
                    level0LastDocID = NO_MORE_DOCS
                    break
                }
            }

            if (posIn != null) { // needs positions
                seekPosData(posFP, posUpto, payFP, payUpto)
            }
        }

        @Throws(IOException::class)
        override fun advanceShallow(target: Int) {
            if (target > level0LastDocID) { // advance level 0 skip data
                doAdvanceShallow(target)
                needsRefilling = true
            }
        }

        @Throws(IOException::class)
        private fun doAdvanceShallow(target: Int) {
            if (target > level1LastDocID) { // advance skip data on level 1
                skipLevel1To(target)
            } else if (needsRefilling) {
                docIn!!.seek(level0DocEndFP)
                docCountLeft -= BLOCK_SIZE
            }

            skipLevel0To(target)
        }

        val logger = KotlinLogging.logger {  }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            if (doc == NO_MORE_DOCS) {
                return NO_MORE_DOCS
            }
            debugCalls++
            if (debugCalls <= 5 || debugCalls % 10000L == 0L) {
                logger.debug {
                    "[BlockPostingsEnum.nextDoc] enter doc=$doc level0LastDocID=$level0LastDocID " +
                        "needsRefilling=$needsRefilling docBufferUpto=$docBufferUpto docBufferSize=$docBufferSize " +
                        "encoding=$encoding"
                }
            }
            if (doc == level0LastDocID || needsRefilling) {
                if (needsRefilling) {
                    refillDocs()
                    needsRefilling = false
                } else {
                    moveToNextLevel0Block()
                }
            }

            when (encoding) {
                DeltaEncoding.PACKED -> doc = docBuffer[docBufferUpto]
                DeltaEncoding.UNARY -> {
                    val next: Int = docBitSet.nextSetBit(doc - docBitSetBase + 1)
                    require(next != NO_MORE_DOCS)
                    doc = docBitSetBase + next
                }

                else -> {
                    // This should never happen, but just in case.
                    throw IllegalStateException("Unknown encoding: $encoding")
                }
            }

            ++docBufferUpto
            if (debugCalls <= 5 || debugCalls % 10000L == 0L) {
                logger.debug { "[BlockPostingsEnum.nextDoc] exit doc=$doc docBufferUpto=$docBufferUpto" }
            }
            if (doc == NO_MORE_DOCS) {
                logger.debug { "[BlockPostingsEnum.nextDoc] reached NO_MORE_DOCS" }
            }
            return this.doc
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            if (doc == NO_MORE_DOCS) {
                return NO_MORE_DOCS
            }
            debugCalls++
            if (debugCalls <= 5 || debugCalls % 10000L == 0L) {
                logger.debug {
                    "[BlockPostingsEnum.advance] enter target=$target doc=$doc level0LastDocID=$level0LastDocID " +
                        "needsRefilling=$needsRefilling docBufferUpto=$docBufferUpto docBufferSize=$docBufferSize " +
                        "encoding=$encoding"
                }
            }
            if (target > level0LastDocID || needsRefilling) {
                if (target > level0LastDocID) {
                    doAdvanceShallow(target)
                }
                refillDocs()
                needsRefilling = false
            }

            when (encoding) {
                DeltaEncoding.PACKED -> {
                    val next: Int = VectorUtil.findNextGEQ(docBuffer, target, docBufferUpto, docBufferSize)
                    this.doc = docBuffer[next]
                    docBufferUpto = next + 1
                }

                DeltaEncoding.UNARY -> {
                    val next: Int = docBitSet.nextSetBit(target - docBitSetBase)
                    require(next != NO_MORE_DOCS)
                    this.doc = docBitSetBase + next
                    if (needsFreq) {
                        val wordIndex = next shr 6
                        // Take the cumulative pop count for the given word, and subtract bits on the left of
                        // the current doc.
                        docBufferUpto =
                            (1
                                    + docCumulativeWordPopCounts[wordIndex]
                                    - Long.bitCount(docBitSet.bits[wordIndex] ushr next))
                    } else {
                        // When only docs needed and block is UNARY encoded, we do not need to maintain
                        // docBufferUpTo to record the iteration position in the block.
                        // docBufferUpTo == 0 means the block has not been iterated.
                        // docBufferUpTo != 0 means the block has been iterated.
                        docBufferUpto = 1
                    }
                }

                else -> {
                    // This should never happen, but just in case.
                    throw IllegalStateException("Unknown encoding: $encoding")
                }
            }

            if (debugCalls <= 5 || debugCalls % 10000L == 0L) {
                logger.debug { "[BlockPostingsEnum.advance] exit doc=$doc docBufferUpto=$docBufferUpto" }
            }
            if (doc == NO_MORE_DOCS) {
                logger.debug { "[BlockPostingsEnum.advance] reached NO_MORE_DOCS target=$target" }
            }
            return doc
        }

        @Throws(IOException::class)
        override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
            if (doc >= upTo) {
                return
            }

            // Handle the current doc separately, it may be on the previous docBuffer.
            bitSet.set(doc - offset)

            while (true) {
                if (doc == level0LastDocID) {
                    // refill
                    moveToNextLevel0Block()
                }

                when (encoding) {
                    DeltaEncoding.PACKED -> {
                        val start = docBufferUpto
                        val end = computeBufferEndBoundary(upTo)
                        if (end != 0) {
                            bufferIntoBitSet(start, end, bitSet, offset)
                            doc = docBuffer[end - 1]
                        }
                        docBufferUpto = end
                        if (end != BLOCK_SIZE) {
                            // Either the block is a tail block, or the block did not fully match, we're done.
                            nextDoc()
                            require(doc >= upTo)
                            return
                        }
                    }

                    DeltaEncoding.UNARY -> {
                        val sourceFrom: Int = if (docBufferUpto == 0) {
                            // start from beginning
                            0
                        } else {
                            // start after the current doc
                            doc - docBitSetBase + 1
                        }

                        val destFrom = docBitSetBase - offset + sourceFrom

                        require(level0LastDocID != NO_MORE_DOCS)
                        val sourceTo = min(upTo, level0LastDocID + 1) - docBitSetBase

                        if (sourceTo > sourceFrom) {
                            FixedBitSet.orRange(docBitSet, sourceFrom, bitSet, destFrom, sourceTo - sourceFrom)
                        }
                        if (docBitSetBase + sourceTo <= level0LastDocID) {
                            // We stopped before the end of the current bit set, which means that we're done.
                            // Set the current doc before returning.
                            advance(docBitSetBase + sourceTo)
                            return
                        }
                        doc = level0LastDocID
                        docBufferUpto = BLOCK_SIZE
                    }

                    else -> {
                        // This should never happen, but just in case.
                        throw IllegalStateException("Unknown encoding: $encoding")
                    }
                }
            }
        }

        private fun computeBufferEndBoundary(upTo: Int): Int {
            return if (docBufferSize != 0 && docBuffer[docBufferSize - 1] < upTo) {
                // All docs in the buffer are under upTo
                docBufferSize
            } else {
                // Find the index of the first doc that is greater than or equal to upTo
                VectorUtil.findNextGEQ(docBuffer, upTo, docBufferUpto, docBufferSize)
            }
        }

        @Throws(IOException::class)
        private fun bufferIntoBitSet(start: Int, end: Int, bitSet: FixedBitSet, offset: Int) {
            // bitSet#set and `doc - offset` get auto-vectorized
            for (i in start..<end) {
                val doc = docBuffer[i]
                bitSet.set(doc - offset)
            }
        }

        @Throws(IOException::class)
        private fun skipPositions(freq: Int) {
            // Skip positions now:
            var toSkip = posPendingCount - freq

            // if (DEBUG) {
            //   System.out.println("      FPR.skipPositions: toSkip=" + toSkip);
            // }
            val leftInBlock: Int = BLOCK_SIZE - posBufferUpto
            if (toSkip < leftInBlock) {
                val end = posBufferUpto + toSkip
                if (needsPayloads) {
                    payloadByteUpto += sumOverRange(payloadLengthBuffer!!, posBufferUpto, end)
                }
                posBufferUpto = end
            } else {
                toSkip -= leftInBlock
                while (toSkip >= BLOCK_SIZE) {
                    require(posIn!!.filePointer != lastPosBlockFP)
                    PForUtil.skip(posIn!!)

                    if (payIn != null) {
                        if (indexHasPayloads) {
                            // Skip payloadLength block:
                            PForUtil.skip(payIn!!)

                            // Skip payloadBytes block:
                            val numBytes: Int = payIn!!.readVInt()
                            payIn!!.seek(payIn!!.filePointer + numBytes)
                        }

                        if (indexHasOffsets) {
                            PForUtil.skip(payIn!!)
                            PForUtil.skip(payIn!!)
                        }
                    }
                    toSkip -= BLOCK_SIZE
                }
                refillPositions()
                if (needsPayloads) {
                    payloadByteUpto = sumOverRange(payloadLengthBuffer!!, 0, toSkip)
                }
                posBufferUpto = toSkip
            }
        }

        @Throws(IOException::class)
        private fun refillLastPositionBlock() {
            val count = (totalTermFreq % BLOCK_SIZE).toInt()
            var payloadLength = 0
            var offsetLength = 0
            payloadByteUpto = 0
            for (i in 0..<count) {
                val code: Int = posIn!!.readVInt()
                if (indexHasPayloads) {
                    if ((code and 1) != 0) {
                        payloadLength = posIn!!.readVInt()
                    }
                    if (payloadLengthBuffer != null) { // needs payloads
                        payloadLengthBuffer[i] = payloadLength
                        posDeltaBuffer!![i] = code ushr 1
                        if (payloadLength != 0) {
                            if (payloadByteUpto + payloadLength > payloadBytes!!.size) {
                                payloadBytes = ArrayUtil.grow(payloadBytes!!, payloadByteUpto + payloadLength)
                            }
                            posIn!!.readBytes(payloadBytes!!, payloadByteUpto, payloadLength)
                            payloadByteUpto += payloadLength
                        }
                    } else {
                        posIn!!.skipBytes(payloadLength.toLong())
                    }
                } else {
                    posDeltaBuffer!![i] = code
                }

                if (indexHasOffsets) {
                    val deltaCode: Int = posIn!!.readVInt()
                    if ((deltaCode and 1) != 0) {
                        offsetLength = posIn!!.readVInt()
                    }
                    if (offsetStartDeltaBuffer != null) { // needs offsets
                        offsetStartDeltaBuffer[i] = deltaCode ushr 1
                        offsetLengthBuffer!![i] = offsetLength
                    }
                }
            }
            payloadByteUpto = 0
        }

        @Throws(IOException::class)
        private fun refillOffsetsOrPayloads() {
            if (indexHasPayloads) {
                if (needsPayloads) {
                    pforUtil!!.decode(payInUtil!!, payloadLengthBuffer!!)
                    val numBytes: Int = payIn!!.readVInt()

                    if (numBytes > payloadBytes!!.size) {
                        payloadBytes = ArrayUtil.growNoCopy(payloadBytes!!, numBytes)
                    }
                    payIn!!.readBytes(payloadBytes!!, 0, numBytes)
                } else if (payIn != null) { // needs offsets
                    // this works, because when writing a vint block we always force the first length to be
                    // written
                    PForUtil.skip(payIn!!) // skip over lengths
                    val numBytes: Int = payIn!!.readVInt() // read length of payloadBytes
                    payIn!!.seek(payIn!!.filePointer + numBytes) // skip over payloadBytes
                }
                payloadByteUpto = 0
            }

            if (indexHasOffsets) {
                if (needsOffsets) {
                    pforUtil!!.decode(payInUtil!!, offsetStartDeltaBuffer!!)
                    pforUtil!!.decode(payInUtil!!, offsetLengthBuffer!!)
                } else if (payIn != null) { // needs payloads
                    // this works, because when writing a vint block we always force the first length to be
                    // written
                    PForUtil.skip(payIn!!) // skip over starts
                    PForUtil.skip(payIn!!) // skip over lengths
                }
            }
        }

        @Throws(IOException::class)
        private fun refillPositions() {
            if (posIn!!.filePointer == lastPosBlockFP) {
                refillLastPositionBlock()
                return
            }
            pforUtil!!.decode(posInUtil!!, posDeltaBuffer!!)

            if (indexHasOffsetsOrPayloads) {
                refillOffsetsOrPayloads()
            }
        }

        @Throws(IOException::class)
        private fun accumulatePendingPositions() {
            val freq = freq() // trigger lazy decoding of freqs
            posPendingCount += sumOverRange(freqBuffer, posDocBufferUpto, docBufferUpto)
            posDocBufferUpto = docBufferUpto

            require(posPendingCount > 0)

            if (posPendingCount > freq) {
                skipPositions(freq)
                posPendingCount = freq
            }
        }

        private fun accumulatePayloadAndOffsets() {
            if (needsPayloads) {
                payloadLength = payloadLengthBuffer!![posBufferUpto]
                payload!!.bytes = payloadBytes!!
                payload!!.offset = payloadByteUpto
                payload!!.length = payloadLength
                payloadByteUpto += payloadLength
            }

            if (needsOffsets) {
                startOffset = lastStartOffset + offsetStartDeltaBuffer!![posBufferUpto]
                endOffset = startOffset + offsetLengthBuffer!![posBufferUpto]
                lastStartOffset = startOffset
            }
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            if (!needsPos) {
                return -1
            }

            require(posDocBufferUpto <= docBufferUpto)
            if (posDocBufferUpto != docBufferUpto) {
                // First position we're reading on this doc
                accumulatePendingPositions()
                position = 0
                lastStartOffset = 0
            }

            if (posBufferUpto == BLOCK_SIZE) {
                refillPositions()
                posBufferUpto = 0
            }
            position += posDeltaBuffer!![posBufferUpto]

            if (needsOffsetsOrPayloads) {
                accumulatePayloadAndOffsets()
            }

            posBufferUpto++
            posPendingCount--
            return position
        }

        override fun startOffset(): Int {
            if (!needsOffsets) {
                return -1
            }
            return startOffset
        }

        override fun endOffset(): Int {
            if (!needsOffsets) {
                return -1
            }
            return endOffset
        }

        override fun cost(): Long {
            return docFreq.toLong()
        }

        override val impacts: Impacts = object : Impacts() {
            private val scratch: ByteArrayDataInput = ByteArrayDataInput()

            override fun numLevels(): Int {
                return if (!indexHasFreq || level1LastDocID == NO_MORE_DOCS) 1 else 2
            }

            override fun getDocIdUpTo(level: Int): Int {
                if (!indexHasFreq) {
                    return NO_MORE_DOCS
                }
                if (level == 0) {
                    return level0LastDocID
                }
                return if (level == 1) level1LastDocID else NO_MORE_DOCS
            }

            override fun getImpacts(level: Int): MutableList<Impact> {
                if (indexHasFreq) {
                    if (level == 0 && level0LastDocID != NO_MORE_DOCS) {
                        return readImpacts(level0SerializedImpacts!!, level0Impacts!!)
                    }
                    if (level == 1) {
                        return readImpacts(level1SerializedImpacts!!, level1Impacts!!)
                    }
                }
                return DUMMY_IMPACTS
            }

            private fun readImpacts(serialized: BytesRef, impactsList: MutableImpactList): MutableList<Impact> {
                val scratch: ByteArrayDataInput = this.scratch
                scratch.reset(serialized.bytes, 0, serialized.length)
                readImpacts(scratch, impactsList)
                return impactsList.toMutableList()
            }
        }
            get() {
                require(needsImpacts)
                return field
            }

        init {

            if (!needsFreq) {
                Arrays.fill(freqBuffer, 1)
            }

            if (needsFreq && needsImpacts) {
                level0SerializedImpacts = BytesRef(maxImpactNumBytesAtLevel0)
                level1SerializedImpacts = BytesRef(maxImpactNumBytesAtLevel1)
                level0Impacts = MutableImpactList(maxNumImpactsAtLevel0)
                level1Impacts = MutableImpactList(maxNumImpactsAtLevel1)
            } else {
                level0SerializedImpacts = null
                level1SerializedImpacts = null
                level0Impacts = null
                level1Impacts = null
            }

            if (needsPos) {
                this.posIn = this@Lucene101PostingsReader.posIn!!.clone()
                posInUtil = VECTORIZATION_PROVIDER.newPostingDecodingUtil(posIn!!)
                posDeltaBuffer = IntArray(BLOCK_SIZE)
            } else {
                this.posIn = null
                this.posInUtil = null
                posDeltaBuffer = null
            }

            if (needsOffsets || needsPayloads) {
                this.payIn = this@Lucene101PostingsReader.payIn!!.clone()
                payInUtil = VECTORIZATION_PROVIDER.newPostingDecodingUtil(payIn!!)
            } else {
                this.payIn = null
                payInUtil = null
            }

            if (needsOffsets) {
                offsetStartDeltaBuffer = IntArray(BLOCK_SIZE)
                offsetLengthBuffer = IntArray(BLOCK_SIZE)
            } else {
                offsetStartDeltaBuffer = null
                offsetLengthBuffer = null
                startOffset = -1
                endOffset = -1
            }

            if (indexHasPayloads) {
                payloadLengthBuffer = IntArray(BLOCK_SIZE)
                payloadBytes = ByteArray(128)
                payload = BytesRef()
            } else {
                payloadLengthBuffer = null
                payloadBytes = null
                payload = null
            }
        }
    }

    class MutableImpactList(capacity: Int) : AbstractList<Impact>(), RandomAccess {
        override var size: Int = 0
        val impacts: Array<Impact?> = kotlin.arrayOfNulls(capacity)

        init {
            for (i in 0..<capacity) {
                impacts[i] = Impact(Int.Companion.MAX_VALUE, 1L)
            }
        }

        override fun get(index: Int): Impact {
            return impacts[index]!!
        }
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        if (docIn != null) {
            CodecUtil.checksumEntireFile(docIn!!)
        }
        if (posIn != null) {
            CodecUtil.checksumEntireFile(posIn!!)
        }
        if (payIn != null) {
            CodecUtil.checksumEntireFile(payIn!!)
        }
    }

    override fun toString(): String {
        return (this::class.simpleName
                + "(positions="
                + (posIn != null)
                + ",payloads="
                + (payIn != null)
                + ")")
    }

    companion object {
        val VECTORIZATION_PROVIDER: VectorizationProvider = VectorizationProvider.getInstance()

        // Dummy impacts, composed of the maximum possible term frequency and the lowest possible
        // (unsigned) norm value. This is typically used on tail blocks, which don't actually record
        // impacts as the storage overhead would not be worth any query evaluation speedup, since there's
        // less than 128 docs left to evaluate anyway.
        private val DUMMY_IMPACTS: MutableList<Impact> = mutableListOf<Impact>(Impact(Int.Companion.MAX_VALUE, 1L))

        fun prefixSum(buffer: IntArray, count: Int, base: Long) {
            buffer[0] += base.toInt()
            for (i in 1..<count) {
                buffer[i] += buffer[i - 1]
            }
        }

        private fun sumOverRange(arr: IntArray, start: Int, end: Int): Int {
            var res = 0
            for (i in start..<end) {
                res += arr[i]
            }
            return res
        }

        /**
         * @see Lucene101PostingsWriter.writeVInt15
         */
        @Throws(IOException::class)
        fun readVInt15(`in`: DataInput): Int {
            val s: Short = `in`.readShort()
            return if (s >= 0) {
                s.toInt()
            } else {
                (s.toInt() and 0x7FFF) or (`in`.readVInt() shl 15)
            }
        }

        /**
         * @see Lucene101PostingsWriter.writeVLong15
         */
        @Throws(IOException::class)
        fun readVLong15(`in`: DataInput): Long {
            val s: Short = `in`.readShort()
            return if (s >= 0) {
                s.toLong()
            } else {
                (s.toLong() and 0x7FFFL) or (`in`.readVLong() shl 15)
            }
        }

        @Throws(IOException::class)
        private fun prefetchPostings(docIn: IndexInput, state: IntBlockTermState) {
            require(
                state.docFreq > 1 // Singletons are inlined in the terms dict, nothing to prefetch
            )
            if (docIn.filePointer != state.docStartFP) {
                // Don't prefetch if the input is already positioned at the right offset, which suggests that
                // the caller is streaming the entire inverted index (e.g. for merging), let the read-ahead
                // logic do its work instead. Note that this heuristic doesn't work for terms that have skip
                // data, since skip data is stored after the last term, but handling all terms that have <128
                // docs is a good start already.
                docIn.prefetch(state.docStartFP, 1)
            }
            // Note: we don't prefetch positions or offsets, which are less likely to be needed.
        }

        fun readImpacts(`in`: ByteArrayDataInput, reuse: MutableImpactList): MutableImpactList {
            var freq = 0
            var norm: Long = 0
            var length = 0
            while (`in`.position < `in`.length()) {
                val freqDelta: Int = `in`.readVInt()
                if ((freqDelta and 0x01) != 0) {
                    freq += 1 + (freqDelta ushr 1)
                    try {
                        norm += 1 + `in`.readZLong()
                    } catch (e: IOException) {
                        throw RuntimeException(e) // cannot happen on a BADI
                    }
                } else {
                    freq += 1 + (freqDelta ushr 1)
                    norm++
                }
                val impact: Impact = reuse.impacts[length]!!
                impact.freq = freq
                impact.norm = norm
                length++
            }
            reuse.size = length
            return reuse
        }
    }
}
