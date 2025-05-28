package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.CompetitiveImpactAccumulator
import org.gnit.lucenekmp.codecs.PushPostingsWriterBase
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.IntBlockTermState
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.VERSION_DENSE_BLOCKS_AS_BITSETS
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.BLOCK_SIZE
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.DOC_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.LEVEL1_MASK
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.META_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.PAY_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.POS_CODEC
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat.Companion.TERMS_CODEC
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.compareUnsigned
import kotlin.math.min

/** Writer for [Lucene101PostingsFormat].  */
class Lucene101PostingsWriter internal constructor(state: SegmentWriteState, private val version: Int) :
    PushPostingsWriterBase() {
    var metaOut: IndexOutput
    var docOut: IndexOutput? = null
    var posOut: IndexOutput? = null
    var payOut: IndexOutput? = null

    var lastState: IntBlockTermState? = null

    // Holds starting file pointers for current term:
    private var docStartFP: Long = 0
    private var posStartFP: Long = 0
    private var payStartFP: Long = 0

    val docDeltaBuffer: IntArray
    val freqBuffer: IntArray
    private var docBufferUpto = 0

    val posDeltaBuffer: IntArray?
    val payloadLengthBuffer: IntArray?
    val offsetStartDeltaBuffer: IntArray?
    val offsetLengthBuffer: IntArray?
    private var posBufferUpto = 0

    private var payloadBytes: ByteArray?
    private var payloadByteUpto = 0

    private var level0LastDocID = 0
    private var level0LastPosFP: Long = 0
    private var level0LastPayFP: Long = 0

    private var level1LastDocID = 0
    private var level1LastPosFP: Long = 0
    private var level1LastPayFP: Long = 0

    private var docID = 0
    private var lastDocID = 0
    private var lastPosition = 0
    private var lastStartOffset = 0
    private var docCount = 0

    private val pforUtil: PForUtil
    private val forDeltaUtil: ForDeltaUtil

    private var fieldHasNorms = false
    private var norms: NumericDocValues? = null
    private val level0FreqNormAccumulator: CompetitiveImpactAccumulator = CompetitiveImpactAccumulator()
    private val level1CompetitiveFreqNormAccumulator: CompetitiveImpactAccumulator = CompetitiveImpactAccumulator()

    private var maxNumImpactsAtLevel0 = 0
    private var maxImpactNumBytesAtLevel0 = 0
    private var maxNumImpactsAtLevel1 = 0
    private var maxImpactNumBytesAtLevel1 = 0

    /** Scratch output that we use to be able to prepend the encoded length, e.g. impacts.  */
    private val scratchOutput: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()

    /**
     * Output for a single block. This is useful to be able to prepend skip data before each block,
     * which can only be computed once the block is encoded. The content is then typically copied to
     * [.level1Output].
     */
    private val level0Output: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()

    /**
     * Output for groups of 32 blocks. This is useful to prepend skip data for these 32 blocks, which
     * can only be done once we have encoded these 32 blocks. The content is then typically copied to
     * [.docCount].
     */
    private val level1Output: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()

    /**
     * Reusable FixedBitSet, for dense blocks that are more efficiently stored by storing them as a
     * bit set than as packed deltas.
     */
    // Since we use a bit set when it's more storage-efficient, the bit set cannot have more than
    // BLOCK_SIZE*32 bits, which is the maximum possible storage requirement with FOR.
    private val spareBitSet: FixedBitSet = FixedBitSet(BLOCK_SIZE * Int.SIZE_BITS)

    /** Sole public constructor.  */
    constructor(state: SegmentWriteState) : this(state, Lucene101PostingsFormat.VERSION_CURRENT)

    /** Constructor that takes a version.  */
    init {
        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, Lucene101PostingsFormat.META_EXTENSION
            )
        val docFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, Lucene101PostingsFormat.DOC_EXTENSION
            )
        metaOut = state.directory.createOutput(metaFileName, state.context)
        var posOut: IndexOutput? = null
        var payOut: IndexOutput? = null
        var success = false
        try {
            docOut = state.directory.createOutput(docFileName, state.context)
            CodecUtil.writeIndexHeader(
                metaOut, META_CODEC, version, state.segmentInfo.getId(), state.segmentSuffix
            )
            CodecUtil.writeIndexHeader(
                docOut!!, DOC_CODEC, version, state.segmentInfo.getId(), state.segmentSuffix
            )
            forDeltaUtil = ForDeltaUtil()
            pforUtil = PForUtil()
            if (state.fieldInfos.hasProx()) {
                posDeltaBuffer = IntArray(BLOCK_SIZE)
                val posFileName: String =
                    IndexFileNames.segmentFileName(
                        state.segmentInfo.name, state.segmentSuffix, Lucene101PostingsFormat.POS_EXTENSION
                    )
                posOut = state.directory.createOutput(posFileName, state.context)
                CodecUtil.writeIndexHeader(
                    posOut, POS_CODEC, version, state.segmentInfo.getId(), state.segmentSuffix
                )

                if (state.fieldInfos.hasPayloads()) {
                    payloadBytes = ByteArray(128)
                    payloadLengthBuffer = IntArray(BLOCK_SIZE)
                } else {
                    payloadBytes = null
                    payloadLengthBuffer = null
                }

                if (state.fieldInfos.hasOffsets()) {
                    offsetStartDeltaBuffer = IntArray(BLOCK_SIZE)
                    offsetLengthBuffer = IntArray(BLOCK_SIZE)
                } else {
                    offsetStartDeltaBuffer = null
                    offsetLengthBuffer = null
                }

                if (state.fieldInfos.hasPayloads() || state.fieldInfos.hasOffsets()) {
                    val payFileName: String =
                        IndexFileNames.segmentFileName(
                            state.segmentInfo.name,
                            state.segmentSuffix,
                            Lucene101PostingsFormat.PAY_EXTENSION
                        )
                    payOut = state.directory.createOutput(payFileName, state.context)
                    CodecUtil.writeIndexHeader(
                        payOut, PAY_CODEC, version, state.segmentInfo.getId(), state.segmentSuffix
                    )
                }
            } else {
                posDeltaBuffer = null
                payloadLengthBuffer = null
                offsetStartDeltaBuffer = null
                offsetLengthBuffer = null
                payloadBytes = null
            }
            this.payOut = payOut
            this.posOut = posOut
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(metaOut, docOut, posOut, payOut)
            }
        }

        docDeltaBuffer = IntArray(BLOCK_SIZE)
        freqBuffer = IntArray(BLOCK_SIZE)
    }

    override fun newTermState(): IntBlockTermState {
        return IntBlockTermState()
    }

    @Throws(IOException::class)
    override fun init(termsOut: IndexOutput, state: SegmentWriteState) {
        CodecUtil.writeIndexHeader(
            termsOut, TERMS_CODEC, version, state.segmentInfo.getId(), state.segmentSuffix
        )
        termsOut.writeVInt(BLOCK_SIZE)
    }

    override fun setField(fieldInfo: FieldInfo) {
        super.setField(fieldInfo)
        lastState = EMPTY_STATE
        fieldHasNorms = fieldInfo.hasNorms()
    }

    override fun startTerm(norms: NumericDocValues) {
        docStartFP = docOut!!.filePointer
        if (writePositions) {
            posStartFP = posOut!!.filePointer
            level0LastPosFP = posStartFP
            level1LastPosFP = level0LastPosFP
            if (writePayloads || writeOffsets) {
                payStartFP = payOut!!.filePointer
                level0LastPayFP = payStartFP
                level1LastPayFP = level0LastPayFP
            }
        }
        lastDocID = -1
        level0LastDocID = -1
        level1LastDocID = -1
        this.norms = norms
        if (writeFreqs) {
            level0FreqNormAccumulator.clear()
        }
    }

    @Throws(IOException::class)
    override fun startDoc(docID: Int, termDocFreq: Int) {
        if (docBufferUpto == BLOCK_SIZE) {
            flushDocBlock(false)
            docBufferUpto = 0
        }

        val docDelta = docID - lastDocID

        if (docID < 0 || docDelta <= 0) {
            throw CorruptIndexException(
                "docs out of order ($docID <= $lastDocID )", docOut!!
            )
        }

        docDeltaBuffer[docBufferUpto] = docDelta
        if (writeFreqs) {
            freqBuffer[docBufferUpto] = termDocFreq
        }

        this.docID = docID
        lastPosition = 0
        lastStartOffset = 0

        if (writeFreqs) {
            val norm: Long
            if (fieldHasNorms) {
                val found: Boolean = norms!!.advanceExact(docID)
                if (!found) {
                    // This can happen if indexing hits a problem after adding a doc to the
                    // postings but before buffering the norm. Such documents are written
                    // deleted and will go away on the first merge.
                    norm = 1L
                } else {
                    norm = norms!!.longValue()
                    require(norm != 0L) { docID }
                }
            } else {
                norm = 1L
            }

            level0FreqNormAccumulator.add(termDocFreq, norm)
        }
    }

    @Throws(IOException::class)
    override fun addPosition(position: Int, payload: BytesRef?, startOffset: Int, endOffset: Int) {
        if (position > IndexWriter.MAX_POSITION) {
            throw CorruptIndexException(
                ("position="
                        + position
                        + " is too large (> IndexWriter.MAX_POSITION="
                        + IndexWriter.MAX_POSITION
                        + ")"),
                docOut!!
            )
        }
        if (position < 0) {
            throw CorruptIndexException("position=$position is < 0", docOut!!)
        }
        posDeltaBuffer!![posBufferUpto] = position - lastPosition
        if (writePayloads) {
            if (payload == null || payload.length == 0) {
                // no payload
                payloadLengthBuffer!![posBufferUpto] = 0
            } else {
                payloadLengthBuffer!![posBufferUpto] = payload.length
                if (payloadByteUpto + payload.length > payloadBytes!!.size) {
                    payloadBytes = ArrayUtil.grow(payloadBytes!!, payloadByteUpto + payload.length)
                }
                System.arraycopy(
                    payload.bytes, payload.offset, payloadBytes!!, payloadByteUpto, payload.length
                )
                payloadByteUpto += payload.length
            }
        }

        if (writeOffsets) {
            require(startOffset >= lastStartOffset)
            require(endOffset >= startOffset)
            offsetStartDeltaBuffer!![posBufferUpto] = startOffset - lastStartOffset
            offsetLengthBuffer!![posBufferUpto] = endOffset - startOffset
            lastStartOffset = startOffset
        }

        posBufferUpto++
        lastPosition = position
        if (posBufferUpto == BLOCK_SIZE) {
            pforUtil.encode(posDeltaBuffer, posOut!!)

            if (writePayloads) {
                pforUtil.encode(payloadLengthBuffer!!, payOut!!)
                payOut!!.writeVInt(payloadByteUpto)
                payOut!!.writeBytes(payloadBytes!!, 0, payloadByteUpto)
                payloadByteUpto = 0
            }
            if (writeOffsets) {
                pforUtil.encode(offsetStartDeltaBuffer!!, payOut!!)
                pforUtil.encode(offsetLengthBuffer!!, payOut!!)
            }
            posBufferUpto = 0
        }
    }

    override fun finishDoc() {
        docBufferUpto++
        docCount++

        lastDocID = docID
    }

    @Throws(IOException::class)
    private fun flushDocBlock(finishTerm: Boolean) {
        require(docBufferUpto != 0)

        if (docBufferUpto < BLOCK_SIZE) {
            require(finishTerm)
            PostingsUtil.writeVIntBlock(
                level0Output, docDeltaBuffer, freqBuffer, docBufferUpto, writeFreqs
            )
        } else {
            if (writeFreqs) {
                val impacts: MutableList<Impact> = level0FreqNormAccumulator.getCompetitiveFreqNormPairs()
                if (impacts.size > maxNumImpactsAtLevel0) {
                    maxNumImpactsAtLevel0 = impacts.size
                }
                writeImpacts(impacts, scratchOutput)
                require(level0Output.size() == 0L)
                if (scratchOutput.size() > maxImpactNumBytesAtLevel0) {
                    maxImpactNumBytesAtLevel0 = Math.toIntExact(scratchOutput.size())
                }
                level0Output.writeVLong(scratchOutput.size())
                scratchOutput.copyTo(level0Output)
                scratchOutput.reset()
                if (writePositions) {
                    level0Output.writeVLong(posOut!!.filePointer - level0LastPosFP)
                    level0Output.writeByte(posBufferUpto.toByte())
                    level0LastPosFP = posOut!!.filePointer

                    if (writeOffsets || writePayloads) {
                        level0Output.writeVLong(payOut!!.filePointer - level0LastPayFP)
                        level0Output.writeVInt(payloadByteUpto)
                        level0LastPayFP = payOut!!.filePointer
                    }
                }
            }
            var numSkipBytes: Long = level0Output.size()
            // Now we need to decide whether to encode block deltas as packed integers (FOR) or unary
            // codes (bit set). FOR makes #nextDoc() a bit faster while the bit set approach makes
            // #advance() usually faster and #intoBitSet() much faster. In the end, we make the decision
            // based on storage requirements, picking the bit set approach whenever it's more
            // storage-efficient than the next number of bits per value (which effectively slightly biases
            // towards the bit set approach).
            val bitsPerValue = forDeltaUtil.bitsRequired(docDeltaBuffer)
            val sum: Int = Math.toIntExact(docDeltaBuffer.sum().toLong())
            val numBitSetLongs: Int = FixedBitSet.bits2words(sum)
            val numBitsNextBitsPerValue: Int = min(Int.SIZE_BITS, bitsPerValue + 1) * BLOCK_SIZE
            if (sum == BLOCK_SIZE) {
                level0Output.writeByte(0.toByte())
            } else if (version < VERSION_DENSE_BLOCKS_AS_BITSETS || numBitsNextBitsPerValue <= sum) {
                level0Output.writeByte(bitsPerValue.toByte())
                forDeltaUtil.encodeDeltas(bitsPerValue, docDeltaBuffer, level0Output)
            } else {
                // Storing doc deltas is more efficient using unary coding (ie. storing doc IDs as a bit
                // set)
                spareBitSet.clear(0, numBitSetLongs shl 6)
                var s = -1
                for (i in docDeltaBuffer) {
                    s += i
                    spareBitSet.set(s)
                }
                // We never use the bit set encoding when it requires more than Integer.SIZE=32 bits per
                // value. So the bit set cannot have more than BLOCK_SIZE * Integer.SIZE / Long.SIZE = 64
                // longs, which fits on a byte.
                require(numBitSetLongs <= BLOCK_SIZE / 2)
                level0Output.writeByte((-numBitSetLongs).toByte())
                for (i in 0..<numBitSetLongs) {
                    level0Output.writeLong(spareBitSet.bits[i])
                }
            }

            if (writeFreqs) {
                pforUtil.encode(freqBuffer, level0Output)
            }

            // docID - lastBlockDocID is at least 128, so it can never fit a single byte with a vint
            // Even if we subtracted 128, only extremely dense blocks would be eligible to a single byte
            // so let's go with 2 bytes right away
            writeVInt15(scratchOutput, docID - level0LastDocID)
            writeVLong15(scratchOutput, level0Output.size())
            numSkipBytes += scratchOutput.size()
            level1Output.writeVLong(numSkipBytes)
            scratchOutput.copyTo(level1Output)
            scratchOutput.reset()
        }

        level0Output.copyTo(level1Output)
        level0Output.reset()
        level0LastDocID = docID
        if (writeFreqs) {
            level1CompetitiveFreqNormAccumulator.addAll(level0FreqNormAccumulator)
            level0FreqNormAccumulator.clear()
        }

        if ((docCount and LEVEL1_MASK) == 0) { // true every 32 blocks (4,096 docs)
            writeLevel1SkipData()
            level1LastDocID = docID
            level1CompetitiveFreqNormAccumulator.clear()
        } else if (finishTerm) {
            level1Output.copyTo(docOut!!)
            level1Output.reset()
            level1CompetitiveFreqNormAccumulator.clear()
        }
    }

    @Throws(IOException::class)
    private fun writeLevel1SkipData() {
        docOut!!.writeVInt(docID - level1LastDocID)
        val level1End: Long
        if (writeFreqs) {
            val impacts: MutableList<Impact> = level1CompetitiveFreqNormAccumulator.getCompetitiveFreqNormPairs()
            if (impacts.size > maxNumImpactsAtLevel1) {
                maxNumImpactsAtLevel1 = impacts.size
            }
            writeImpacts(impacts, scratchOutput)
            val numImpactBytes: Long = scratchOutput.size()
            if (numImpactBytes > maxImpactNumBytesAtLevel1) {
                maxImpactNumBytesAtLevel1 = Math.toIntExact(numImpactBytes)
            }
            if (writePositions) {
                scratchOutput.writeVLong(posOut!!.filePointer - level1LastPosFP)
                scratchOutput.writeByte(posBufferUpto.toByte())
                level1LastPosFP = posOut!!.filePointer
                if (writeOffsets || writePayloads) {
                    scratchOutput.writeVLong(payOut!!.filePointer - level1LastPayFP)
                    scratchOutput.writeVInt(payloadByteUpto)
                    level1LastPayFP = payOut!!.filePointer
                }
            }
            val level1Len: Long = 2 * Short.SIZE_BYTES + scratchOutput.size() + level1Output.size()
            docOut!!.writeVLong(level1Len)
            level1End = docOut!!.filePointer + level1Len
            // There are at most 128 impacts, that require at most 2 bytes each
            require(numImpactBytes <= Short.Companion.MAX_VALUE)
            // Like impacts plus a few vlongs, still way under the max short value
            require(scratchOutput.size() + Short.SIZE_BYTES <= Short.Companion.MAX_VALUE)
            docOut!!.writeShort((scratchOutput.size() + Short.SIZE_BYTES).toShort())
            docOut!!.writeShort(numImpactBytes.toShort())
            scratchOutput.copyTo(docOut!!)
            scratchOutput.reset()
        } else {
            docOut!!.writeVLong(level1Output.size())
            level1End = docOut!!.filePointer + level1Output.size()
        }
        level1Output.copyTo(docOut!!)
        level1Output.reset()
        require(docOut!!.filePointer == level1End) { "${docOut!!.filePointer} $level1End" }
    }

    /** Called when we are done adding docs to this term  */
    @Throws(IOException::class)
    override fun finishTerm(_state: BlockTermState) {
        val state: IntBlockTermState = _state as IntBlockTermState
        require(state.docFreq > 0)

        // TODO: wasteful we are counting this (counting # docs
        // for this term) in two places
        require(state.docFreq == docCount) { "${state.docFreq} vs $docCount" }

        // docFreq == 1, don't write the single docid/freq to a separate file along with a pointer to
        // it.
        val singletonDocID: Int
        if (state.docFreq == 1) {
            // pulse the singleton docid into the term dictionary, freq is implicitly totalTermFreq
            singletonDocID = docDeltaBuffer[0] - 1
        } else {
            singletonDocID = -1
            flushDocBlock(true)
        }

        val lastPosBlockOffset: Long

        if (writePositions) {
            // totalTermFreq is just total number of positions(or payloads, or offsets)
            // associated with current term.
            require(state.totalTermFreq != -1L)
            if (state.totalTermFreq > BLOCK_SIZE) {
                // record file offset for last pos in last block
                lastPosBlockOffset = posOut!!.filePointer - posStartFP
            } else {
                lastPosBlockOffset = -1
            }
            if (posBufferUpto > 0) {
                require(posBufferUpto < BLOCK_SIZE)

                // TODO: should we send offsets/payloads to
                // .pay...  seems wasteful (have to store extra
                // vLong for low (< BLOCK_SIZE) DF terms = vast vast
                // majority)

                // vInt encode the remaining positions/payloads/offsets:
                var lastPayloadLength = -1 // force first payload length to be written
                var lastOffsetLength = -1 // force first offset length to be written
                var payloadBytesReadUpto = 0
                for (i in 0..<posBufferUpto) {
                    val posDelta = posDeltaBuffer!![i]
                    if (writePayloads) {
                        val payloadLength = payloadLengthBuffer!![i]
                        if (payloadLength != lastPayloadLength) {
                            lastPayloadLength = payloadLength
                            posOut!!.writeVInt((posDelta shl 1) or 1)
                            posOut!!.writeVInt(payloadLength)
                        } else {
                            posOut!!.writeVInt(posDelta shl 1)
                        }

                        if (payloadLength != 0) {
                            posOut!!.writeBytes(payloadBytes!!, payloadBytesReadUpto, payloadLength)
                            payloadBytesReadUpto += payloadLength
                        }
                    } else {
                        posOut!!.writeVInt(posDelta)
                    }

                    if (writeOffsets) {
                        val delta = offsetStartDeltaBuffer!![i]
                        val length = offsetLengthBuffer!![i]
                        if (length == lastOffsetLength) {
                            posOut!!.writeVInt(delta shl 1)
                        } else {
                            posOut!!.writeVInt(delta shl 1 or 1)
                            posOut!!.writeVInt(length)
                            lastOffsetLength = length
                        }
                    }
                }

                if (writePayloads) {
                    require(payloadBytesReadUpto == payloadByteUpto)
                    payloadByteUpto = 0
                }
            }
        } else {
            lastPosBlockOffset = -1
        }

        state.docStartFP = docStartFP
        state.posStartFP = posStartFP
        state.payStartFP = payStartFP
        state.singletonDocID = singletonDocID

        state.lastPosBlockOffset = lastPosBlockOffset
        docBufferUpto = 0
        posBufferUpto = 0
        lastDocID = -1
        docCount = 0
    }

    @Throws(IOException::class)
    override fun encodeTerm(
        out: DataOutput, fieldInfo: FieldInfo, _state: BlockTermState, absolute: Boolean
    ) {
        val state: IntBlockTermState = _state as IntBlockTermState
        if (absolute) {
            lastState = EMPTY_STATE
            require(lastState!!.docStartFP == 0L)
        }

        if (lastState!!.singletonDocID != -1 && state.singletonDocID != -1 && state.docStartFP == lastState!!.docStartFP) {
            // With runs of rare values such as ID fields, the increment of pointers in the docs file is
            // often 0.
            // Furthermore some ID schemes like auto-increment IDs or Flake IDs are monotonic, so we
            // encode the delta
            // between consecutive doc IDs to save space.
            val delta: Long = state.singletonDocID.toLong() - lastState!!.singletonDocID
            out.writeVLong((BitUtil.zigZagEncode(delta) shl 1) or 0x01)
        } else {
            out.writeVLong((state.docStartFP - lastState!!.docStartFP) shl 1)
            if (state.singletonDocID != -1) {
                out.writeVInt(state.singletonDocID)
            }
        }

        if (writePositions) {
            out.writeVLong(state.posStartFP - lastState!!.posStartFP)
            if (writePayloads || writeOffsets) {
                out.writeVLong(state.payStartFP - lastState!!.payStartFP)
            }
        }
        if (writePositions) {
            if (state.lastPosBlockOffset != -1L) {
                out.writeVLong(state.lastPosBlockOffset)
            }
        }
        lastState = state
    }

    @Throws(IOException::class)
    override fun close() {
        // TODO: add a finish() at least to PushBase DV too...
        var success = false
        try {
            if (docOut != null) {
                CodecUtil.writeFooter(docOut!!)
            }
            if (posOut != null) {
                CodecUtil.writeFooter(posOut!!)
            }
            if (payOut != null) {
                CodecUtil.writeFooter(payOut!!)
            }
            if (metaOut != null) {
                metaOut.writeInt(maxNumImpactsAtLevel0)
                metaOut.writeInt(maxImpactNumBytesAtLevel0)
                metaOut.writeInt(maxNumImpactsAtLevel1)
                metaOut.writeInt(maxImpactNumBytesAtLevel1)
                metaOut.writeLong(docOut!!.filePointer)
                if (posOut != null) {
                    metaOut.writeLong(posOut!!.filePointer)
                    if (payOut != null) {
                        metaOut.writeLong(payOut!!.filePointer)
                    }
                }
                CodecUtil.writeFooter(metaOut)
            }
            success = true
        } finally {
            if (success) {
                IOUtils.close(metaOut, docOut, posOut, payOut)
            } else {
                IOUtils.closeWhileHandlingException(metaOut, docOut, posOut, payOut)
            }
            payOut = null
            posOut = payOut
            docOut = posOut
            metaOut = docOut!!
        }
    }

    companion object {
        val EMPTY_STATE: IntBlockTermState = IntBlockTermState()

        /**
         * Special vints that are encoded on 2 bytes if they require 15 bits or less. VInt becomes
         * especially slow when the number of bytes is variable, so this special layout helps in the case
         * when the number likely requires 15 bits or less
         */
        @Throws(IOException::class)
        fun writeVInt15(out: DataOutput, v: Int) {
            require(v >= 0)
            writeVLong15(out, v.toLong())
        }

        /**
         * @see .writeVInt15
         */
        @Throws(IOException::class)
        fun writeVLong15(out: DataOutput, v: Long) {
            require(v >= 0)
            if ((v and 0x7FFFL.inv()) == 0L) {
                out.writeShort(v.toShort())
            } else {
                out.writeShort((0x8000L or (v and 0x7FFFL)).toShort())
                out.writeVLong(v shr 15)
            }
        }

        @Throws(IOException::class)
        fun writeImpacts(impacts: MutableCollection<Impact>, out: DataOutput) {
            var previous = Impact(0, 0)
            for (impact in impacts) {
                require(impact.freq > previous.freq)
                require(Long.compareUnsigned(impact.norm, previous.norm) > 0)
                val freqDelta: Int = impact.freq - previous.freq - 1
                val normDelta: Long = impact.norm - previous.norm - 1
                if (normDelta == 0L) {
                    // most of time, norm only increases by 1, so we can fold everything in a single byte
                    out.writeVInt(freqDelta shl 1)
                } else {
                    out.writeVInt((freqDelta shl 1) or 1)
                    out.writeZLong(normDelta)
                }
                previous = impact
            }
        }
    }
}
