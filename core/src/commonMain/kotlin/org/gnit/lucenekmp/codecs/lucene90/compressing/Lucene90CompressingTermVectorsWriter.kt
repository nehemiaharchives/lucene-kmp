package org.gnit.lucenekmp.codecs.lucene90.compressing

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.codecs.compressing.MatchingReaders
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.DocIDMerger
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.packed.BlockPackedWriter
import org.gnit.lucenekmp.util.packed.DirectWriter
import org.gnit.lucenekmp.util.packed.PackedInts
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.descendingIterator
import org.gnit.lucenekmp.jdkport.floatToRawIntBits
import org.gnit.lucenekmp.jdkport.getFirst
import org.gnit.lucenekmp.jdkport.getLast
import kotlin.math.min

/**
 * [TermVectorsWriter] for [Lucene90CompressingTermVectorsFormat].
 *
 * @lucene.experimental
 */
class Lucene90CompressingTermVectorsWriter internal constructor(
    directory: Directory,
    si: SegmentInfo,
    segmentSuffix: String,
    context: IOContext,
    formatName: String,
    compressionMode: CompressionMode,
    chunkSize: Int,
    maxDocsPerChunk: Int,
    blockShift: Int
) : TermVectorsWriter() {
    private val segment: String
    private var indexWriter: FieldsIndexWriter? = null
    private var metaStream: IndexOutput? = null
    private var vectorsStream: IndexOutput? = null

    private val compressionMode: CompressionMode
    private val compressor: Compressor
    private val chunkSize: Int

    private var numChunks: Long = 0 // number of chunks
    private var numDirtyChunks: Long = 0 // number of incomplete compressed blocks written
    private var numDirtyDocs: Long = 0 // cumulative number of docs in incomplete chunks

    /** a pending doc  */
    private inner class DocData(val numFields: Int, val posStart: Int, val offStart: Int, val payStart: Int) {
        val fields: ArrayDeque<FieldData> = ArrayDeque(numFields)

        fun addField(
            fieldNum: Int, numTerms: Int, positions: Boolean, offsets: Boolean, payloads: Boolean
        ): FieldData {
            val field: FieldData
            if (fields.isEmpty()) {
                field =
                    this@Lucene90CompressingTermVectorsWriter.FieldData(
                        fieldNum, numTerms, positions, offsets, payloads, posStart, offStart, payStart
                    )
            } else {
                val last: FieldData = fields.getLast()
                val posStart = last.posStart + (if (last.hasPositions) last.totalPositions else 0)
                val offStart = last.offStart + (if (last.hasOffsets) last.totalPositions else 0)
                val payStart = last.payStart + (if (last.hasPayloads) last.totalPositions else 0)
                field =
                    this@Lucene90CompressingTermVectorsWriter.FieldData(
                        fieldNum, numTerms, positions, offsets, payloads, posStart, offStart, payStart
                    )
            }
            fields.add(field)
            return field
        }
    }

    private fun addDocData(numVectorFields: Int): DocData {
        var last: FieldData? = null
        val it: MutableIterator<DocData> = pendingDocs.descendingIterator()
        while (it.hasNext()) {
            val doc = it.next()
            if (!doc.fields.isEmpty()) {
                last = doc.fields.getLast()
                break
            }
        }
        val doc: DocData
        if (last == null) {
            doc = this.DocData(numVectorFields, 0, 0, 0)
        } else {
            val posStart = last.posStart + (if (last.hasPositions) last.totalPositions else 0)
            val offStart = last.offStart + (if (last.hasOffsets) last.totalPositions else 0)
            val payStart = last.payStart + (if (last.hasPayloads) last.totalPositions else 0)
            doc = this.DocData(numVectorFields, posStart, offStart, payStart)
        }
        pendingDocs.add(doc)
        return doc
    }

    /** a pending field  */
    private inner class FieldData(
        val fieldNum: Int,
        val numTerms: Int,
        val hasPositions: Boolean,
        val hasOffsets: Boolean,
        val hasPayloads: Boolean,
        val posStart: Int,
        val offStart: Int,
        val payStart: Int
    ) {
        val flags: Int = (if (hasPositions) POSITIONS else 0) or (if (hasOffsets) OFFSETS else 0) or (if (hasPayloads) PAYLOADS else 0)
        val freqs: IntArray = IntArray(numTerms)
        val prefixLengths: IntArray = IntArray(numTerms)
        val suffixLengths: IntArray = IntArray(numTerms)
        var totalPositions: Int = 0
        var ord: Int = 0

        fun addTerm(freq: Int, prefixLength: Int, suffixLength: Int) {
            freqs[ord] = freq
            prefixLengths[ord] = prefixLength
            suffixLengths[ord] = suffixLength
            ++ord
        }

        fun addPosition(position: Int, startOffset: Int, length: Int, payloadLength: Int) {
            if (hasPositions) {
                if (posStart + totalPositions == positionsBuf.size) {
                    positionsBuf = ArrayUtil.grow(positionsBuf)
                }
                positionsBuf[posStart + totalPositions] = position
            }
            if (hasOffsets) {
                if (offStart + totalPositions == startOffsetsBuf.size) {
                    val newLength: Int = ArrayUtil.oversize(offStart + totalPositions, 4)
                    startOffsetsBuf = ArrayUtil.growExact(startOffsetsBuf, newLength)
                    lengthsBuf = ArrayUtil.growExact(lengthsBuf, newLength)
                }
                startOffsetsBuf[offStart + totalPositions] = startOffset
                lengthsBuf[offStart + totalPositions] = length
            }
            if (hasPayloads) {
                if (payStart + totalPositions == payloadLengthsBuf.size) {
                    payloadLengthsBuf = ArrayUtil.grow(payloadLengthsBuf)
                }
                payloadLengthsBuf[payStart + totalPositions] = payloadLength
            }
            ++totalPositions
        }
    }

    private var numDocs: Int // total number of docs seen
    private val pendingDocs: ArrayDeque<DocData> // pending docs
    private var curDoc: DocData? = null // current document
    private var curField: FieldData? = null // current field
    private val lastTerm: BytesRef
    private var positionsBuf: IntArray
    private var startOffsetsBuf: IntArray
    private var lengthsBuf: IntArray
    private var payloadLengthsBuf: IntArray
    private val termSuffixes: ByteBuffersDataOutput // buffered term suffixes
    private val payloadBytes: ByteBuffersDataOutput // buffered term payloads
    private val writer: BlockPackedWriter
    private val maxDocsPerChunk: Int // hard limit on number of docs per chunk
    private val scratchBuffer: ByteBuffersDataOutput = ByteBuffersDataOutput.newResettableInstance()

    override fun close() {
        try {
            IOUtils.close(metaStream!!, vectorsStream!!, indexWriter!!)
        } finally {
            metaStream = null
            vectorsStream = null
            indexWriter = null
        }
    }

    @Throws(IOException::class)
    override fun startDocument(numVectorFields: Int) {
        curDoc = addDocData(numVectorFields)
    }

    @Throws(IOException::class)
    override fun finishDocument() {
        // append the payload bytes of the doc after its terms
        payloadBytes.copyTo(termSuffixes)
        payloadBytes.reset()
        ++numDocs
        if (triggerFlush()) {
            flush(false)
        }
        curDoc = null
    }

    @Throws(IOException::class)
    override fun startField(
        info: FieldInfo?, numTerms: Int, positions: Boolean, offsets: Boolean, payloads: Boolean
    ) {
        curField = curDoc!!.addField(info!!.number, numTerms, positions, offsets, payloads)
        lastTerm.length = 0
    }

    @Throws(IOException::class)
    override fun finishField() {
        curField = null
    }

    @Throws(IOException::class)
    override fun startTerm(term: BytesRef?, freq: Int) {
        require(freq >= 1)
        val prefix: Int = if (lastTerm.length == 0) {
            // no previous term: no bytes to write
            0
        } else {
            StringHelper.bytesDifference(lastTerm, term)
        }
        curField!!.addTerm(freq, prefix, term!!.length - prefix)
        termSuffixes.writeBytes(term.bytes, term.offset + prefix, term.length - prefix)
        // copy last term
        if (lastTerm.bytes.size < term.length) {
            lastTerm.bytes = ByteArray(ArrayUtil.oversize(term.length, 1))
        }
        lastTerm.offset = 0
        lastTerm.length = term.length
        System.arraycopy(term.bytes, term.offset, lastTerm.bytes, 0, term.length)
    }

    @Throws(IOException::class)
    override fun addPosition(position: Int, startOffset: Int, endOffset: Int, payload: BytesRef?) {
        require(curField!!.flags != 0)
        curField!!.addPosition(
            position, startOffset, endOffset - startOffset, payload?.length ?: 0
        )
        if (curField!!.hasPayloads && payload != null) {
            payloadBytes.writeBytes(payload.bytes, payload.offset, payload.length)
        }
    }

    private fun triggerFlush(): Boolean {
        return termSuffixes.size() >= chunkSize || pendingDocs.size >= maxDocsPerChunk
    }

    @Throws(IOException::class)
    private fun flush(force: Boolean) {
        require(force != triggerFlush())
        val chunkDocs: Int = pendingDocs.size
        require(chunkDocs > 0) { chunkDocs }
        numChunks++
        if (force) {
            numDirtyChunks++ // incomplete: we had to force this flush
            numDirtyDocs += pendingDocs.size.toLong()
        }
        // write the index file
        indexWriter!!.writeIndex(chunkDocs, vectorsStream!!.filePointer)

        val docBase = numDocs - chunkDocs
        vectorsStream!!.writeVInt(docBase)
        val dirtyBit = if (force) 1 else 0
        vectorsStream!!.writeVInt((chunkDocs shl 1) or dirtyBit)

        // total number of fields of the chunk
        val totalFields = flushNumFields(chunkDocs)

        if (totalFields > 0) {
            // unique field numbers (sorted)
            val fieldNums = flushFieldNums()
            // offsets in the array of unique field numbers
            flushFields(totalFields, fieldNums)
            // flags (does the field have positions, offsets, payloads)
            flushFlags(totalFields, fieldNums)
            // number of terms of each field
            flushNumTerms(totalFields)
            // prefix and suffix lengths for each field
            flushTermLengths()
            // term freqs - 1 (because termFreq is always >=1) for each term
            flushTermFreqs()
            // positions for all terms, when enabled
            flushPositions()
            // offsets for all terms, when enabled
            flushOffsets(fieldNums)
            // payload lengths for all terms, when enabled
            flushPayloadLengths()

            // compress terms and payloads and write them to the output
            // using ByteBuffersDataInput reduce memory copy
            val content: ByteBuffersDataInput = termSuffixes.toDataInput()
            compressor.compress(content, vectorsStream!!)
        }

        // reset
        pendingDocs.clear()
        curDoc = null
        curField = null
        termSuffixes.reset()
    }

    @Throws(IOException::class)
    private fun flushNumFields(chunkDocs: Int): Int {
        if (chunkDocs == 1) {
            val numFields: Int = pendingDocs.getFirst().numFields
            vectorsStream!!.writeVInt(numFields)
            return numFields
        } else {
            writer.reset(vectorsStream!!)
            var totalFields = 0
            for (dd in pendingDocs) {
                writer.add(dd.numFields.toLong())
                totalFields += dd.numFields
            }
            writer.finish()
            return totalFields
        }
    }

    /** Returns a sorted array containing unique field numbers  */
    @Throws(IOException::class)
    private fun flushFieldNums(): IntArray {
        val fieldNumsSet = IntHashSet()
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                fieldNumsSet.add(fd.fieldNum)
            }
        }
        val fieldNums: IntArray = fieldNumsSet.toArray()
        Arrays.sort(fieldNums)

        val numDistinctFields = fieldNums.size
        require(numDistinctFields > 0)
        val bitsRequired: Int = PackedInts.bitsRequired(fieldNums[numDistinctFields - 1].toLong())
        val token = (min(numDistinctFields - 1, 0x07) shl 5) or bitsRequired
        vectorsStream!!.writeByte(token.toByte())
        if (numDistinctFields - 1 >= 0x07) {
            vectorsStream!!.writeVInt(numDistinctFields - 1 - 0x07)
        }
        val writer: PackedInts.Writer =
            PackedInts.getWriterNoHeader(
                vectorsStream!!, PackedInts.Format.PACKED, numDistinctFields, bitsRequired, 1
            )
        for (fieldNum in fieldNums) {
            writer.add(fieldNum.toLong())
        }
        writer.finish()

        return fieldNums
    }

    @Throws(IOException::class)
    private fun flushFields(totalFields: Int, fieldNums: IntArray) {
        scratchBuffer.reset()
        val writer: DirectWriter =
            DirectWriter.getInstance(
                scratchBuffer, totalFields.toLong(), DirectWriter.bitsRequired(fieldNums.size.toLong() - 1L)
            )
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                val fieldNumIndex = Arrays.binarySearch(fieldNums, fd.fieldNum)
                require(fieldNumIndex >= 0)
                writer.add(fieldNumIndex.toLong())
            }
        }
        writer.finish()
        vectorsStream!!.writeVLong(scratchBuffer.size())
        scratchBuffer.copyTo(vectorsStream!!)
    }

    @Throws(IOException::class)
    private fun flushFlags(totalFields: Int, fieldNums: IntArray) {
        // check if fields always have the same flags
        var nonChangingFlags = true
        val fieldFlags = IntArray(fieldNums.size)
        Arrays.fill(fieldFlags, -1)
        outer@ for (dd in pendingDocs) {
            for (fd in dd.fields) {
                val fieldNumOff = Arrays.binarySearch(fieldNums, fd.fieldNum)
                require(fieldNumOff >= 0)
                if (fieldFlags[fieldNumOff] == -1) {
                    fieldFlags[fieldNumOff] = fd.flags
                } else if (fieldFlags[fieldNumOff] != fd.flags) {
                    nonChangingFlags = false
                    break@outer
                }
            }
        }

        if (nonChangingFlags) {
            // write one flag per field num
            vectorsStream!!.writeVInt(0)
            scratchBuffer.reset()
            val writer: DirectWriter =
                DirectWriter.getInstance(scratchBuffer, fieldFlags.size.toLong(), FLAGS_BITS)
            for (flags in fieldFlags) {
                require(flags >= 0)
                writer.add(flags.toLong())
            }
            writer.finish()
            vectorsStream!!.writeVInt(Math.toIntExact(scratchBuffer.size()))
            scratchBuffer.copyTo(vectorsStream!!)
        } else {
            // write one flag for every field instance
            vectorsStream!!.writeVInt(1)
            scratchBuffer.reset()
            val writer: DirectWriter = DirectWriter.getInstance(scratchBuffer, totalFields.toLong(), FLAGS_BITS)
            for (dd in pendingDocs) {
                for (fd in dd.fields) {
                    writer.add(fd.flags.toLong())
                }
            }
            writer.finish()
            vectorsStream!!.writeVInt(Math.toIntExact(scratchBuffer.size()))
            scratchBuffer.copyTo(vectorsStream!!)
        }
    }

    @Throws(IOException::class)
    private fun flushNumTerms(totalFields: Int) {
        var maxNumTerms = 0
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                maxNumTerms = maxNumTerms or fd.numTerms
            }
        }
        val bitsRequired: Int = DirectWriter.bitsRequired(maxNumTerms.toLong())
        vectorsStream!!.writeVInt(bitsRequired)
        scratchBuffer.reset()
        val writer: DirectWriter = DirectWriter.getInstance(scratchBuffer, totalFields.toLong(), bitsRequired)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                writer.add(fd.numTerms.toLong())
            }
        }
        writer.finish()
        vectorsStream!!.writeVInt(Math.toIntExact(scratchBuffer.size()))
        scratchBuffer.copyTo(vectorsStream!!)
    }

    @Throws(IOException::class)
    private fun flushTermLengths() {
        writer.reset(vectorsStream!!)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                for (i in 0..<fd.numTerms) {
                    writer.add(fd.prefixLengths[i].toLong())
                }
            }
        }
        writer.finish()
        writer.reset(vectorsStream!!)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                for (i in 0..<fd.numTerms) {
                    writer.add(fd.suffixLengths[i].toLong())
                }
            }
        }
        writer.finish()
    }

    @Throws(IOException::class)
    private fun flushTermFreqs() {
        writer.reset(vectorsStream!!)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                for (i in 0..<fd.numTerms) {
                    writer.add(fd.freqs[i].toLong() - 1L)
                }
            }
        }
        writer.finish()
    }

    @Throws(IOException::class)
    private fun flushPositions() {
        writer.reset(vectorsStream!!)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                if (fd.hasPositions) {
                    var pos = 0
                    for (i in 0..<fd.numTerms) {
                        var previousPosition = 0
                        for (j in 0..<fd.freqs[i]) {
                            val position = positionsBuf[fd.posStart + pos++]
                            writer.add((position - previousPosition).toLong())
                            previousPosition = position
                        }
                    }
                    require(pos == fd.totalPositions)
                }
            }
        }
        writer.finish()
    }

    @Throws(IOException::class)
    private fun flushOffsets(fieldNums: IntArray) {
        var hasOffsets = false
        val sumPos = LongArray(fieldNums.size)
        val sumOffsets = LongArray(fieldNums.size)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                hasOffsets = hasOffsets or fd.hasOffsets
                if (fd.hasOffsets && fd.hasPositions) {
                    val fieldNumOff = Arrays.binarySearch(fieldNums, fd.fieldNum)
                    var pos = 0
                    for (i in 0..<fd.numTerms) {
                        sumPos[fieldNumOff] += positionsBuf[fd.posStart + fd.freqs[i] - 1 + pos].toLong()
                        sumOffsets[fieldNumOff] += startOffsetsBuf[fd.offStart + fd.freqs[i] - 1 + pos].toLong()
                        pos += fd.freqs[i]
                    }
                    require(pos == fd.totalPositions)
                }
            }
        }

        if (!hasOffsets) {
            // nothing to do
            return
        }

        val charsPerTerm = FloatArray(fieldNums.size)
        for (i in fieldNums.indices) {
            charsPerTerm[i] =
                if (sumPos[i] <= 0 || sumOffsets[i] <= 0) 0f else (sumOffsets[i].toDouble() / sumPos[i]).toFloat()
        }

        // start offsets
        for (i in fieldNums.indices) {
            vectorsStream!!.writeInt(Float.floatToRawIntBits(charsPerTerm[i]))
        }

        writer.reset(vectorsStream!!)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                if ((fd.flags and OFFSETS) != 0) {
                    val fieldNumOff = Arrays.binarySearch(fieldNums, fd.fieldNum)
                    val cpt = charsPerTerm[fieldNumOff]
                    var pos = 0
                    for (i in 0..<fd.numTerms) {
                        var previousPos = 0
                        var previousOff = 0
                        for (j in 0..<fd.freqs[i]) {
                            val position = if (fd.hasPositions) positionsBuf[fd.posStart + pos] else 0
                            val startOffset = startOffsetsBuf[fd.offStart + pos]
                            writer.add((startOffset - previousOff - (cpt * (position - previousPos)).toInt()).toLong())
                            previousPos = position
                            previousOff = startOffset
                            ++pos
                        }
                    }
                }
            }
        }
        writer.finish()

        // lengths
        writer.reset(vectorsStream!!)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                if ((fd.flags and OFFSETS) != 0) {
                    var pos = 0
                    for (i in 0..<fd.numTerms) {
                        for (j in 0..<fd.freqs[i]) {
                            writer.add(
                                (lengthsBuf[fd.offStart + pos++] - fd.prefixLengths[i] - fd.suffixLengths[i]).toLong()
                            )
                        }
                    }
                    require(pos == fd.totalPositions)
                }
            }
        }
        writer.finish()
    }

    @Throws(IOException::class)
    private fun flushPayloadLengths() {
        writer.reset(vectorsStream!!)
        for (dd in pendingDocs) {
            for (fd in dd.fields) {
                if (fd.hasPayloads) {
                    for (i in 0..<fd.totalPositions) {
                        writer.add(payloadLengthsBuf[fd.payStart + i].toLong())
                    }
                }
            }
        }
        writer.finish()
    }

    @Throws(IOException::class)
    override fun finish(numDocs: Int) {
        if (!pendingDocs.isEmpty()) {
            flush(true)
        }
        if (numDocs != this.numDocs) {
            throw RuntimeException(
                "Wrote " + this.numDocs + " docs, finish called with numDocs=" + numDocs
            )
        }
        indexWriter!!.finish(numDocs, vectorsStream!!.filePointer, metaStream!!)
        metaStream!!.writeVLong(numChunks)
        metaStream!!.writeVLong(numDirtyChunks)
        metaStream!!.writeVLong(numDirtyDocs)
        CodecUtil.writeFooter(metaStream!!)
        CodecUtil.writeFooter(vectorsStream!!)
    }

    @Throws(IOException::class)
    override fun addProx(numProx: Int, positions: DataInput?, offsets: DataInput?) {
        require((curField!!.hasPositions) == (positions != null))
        require((curField!!.hasOffsets) == (offsets != null))

        if (curField!!.hasPositions) {
            val posStart = curField!!.posStart + curField!!.totalPositions
            if (posStart + numProx > positionsBuf.size) {
                positionsBuf = ArrayUtil.grow(positionsBuf, posStart + numProx)
            }
            var position = 0
            if (curField!!.hasPayloads) {
                val payStart = curField!!.payStart + curField!!.totalPositions
                if (payStart + numProx > payloadLengthsBuf.size) {
                    payloadLengthsBuf = ArrayUtil.grow(payloadLengthsBuf, payStart + numProx)
                }
                for (i in 0..<numProx) {
                    val code: Int = positions!!.readVInt()
                    if ((code and 1) != 0) {
                        // This position has a payload
                        val payloadLength: Int = positions.readVInt()
                        payloadLengthsBuf[payStart + i] = payloadLength
                        payloadBytes.copyBytes(positions, payloadLength.toLong())
                    } else {
                        payloadLengthsBuf[payStart + i] = 0
                    }
                    position += code ushr 1
                    positionsBuf[posStart + i] = position
                }
            } else {
                for (i in 0..<numProx) {
                    position += (positions!!.readVInt() ushr 1)
                    positionsBuf[posStart + i] = position
                }
            }
        }

        if (curField!!.hasOffsets) {
            val offStart = curField!!.offStart + curField!!.totalPositions
            if (offStart + numProx > startOffsetsBuf.size) {
                val newLength: Int = ArrayUtil.oversize(offStart + numProx, 4)
                startOffsetsBuf = ArrayUtil.growExact(startOffsetsBuf, newLength)
                lengthsBuf = ArrayUtil.growExact(lengthsBuf, newLength)
            }
            var lastOffset = 0
            var startOffset: Int
            var endOffset: Int
            for (i in 0..<numProx) {
                startOffset = lastOffset + offsets!!.readVInt()
                endOffset = startOffset + offsets.readVInt()
                lastOffset = endOffset
                startOffsetsBuf[offStart + i] = startOffset
                lengthsBuf[offStart + i] = endOffset - startOffset
            }
        }

        curField!!.totalPositions += numProx
    }

    /** Sole constructor.  */
    init {
        checkNotNull(directory)
        this.segment = si.name
        this.compressionMode = compressionMode
        this.compressor = compressionMode.newCompressor()
        this.chunkSize = chunkSize
        this.maxDocsPerChunk = maxDocsPerChunk

        numDocs = 0
        pendingDocs = ArrayDeque<DocData>()
        termSuffixes = ByteBuffersDataOutput.newResettableInstance()
        payloadBytes = ByteBuffersDataOutput.newResettableInstance()
        lastTerm = BytesRef(ArrayUtil.oversize(30, 1))

        var success = false
        try {
            metaStream =
                directory.createOutput(
                    IndexFileNames.segmentFileName(segment, segmentSuffix, VECTORS_META_EXTENSION),
                    context
                )
            CodecUtil.writeIndexHeader(
                metaStream!!,
                VECTORS_INDEX_CODEC_NAME + "Meta",
                VERSION_CURRENT,
                si.getId(),
                segmentSuffix
            )
            require(
                CodecUtil.indexHeaderLength(VECTORS_INDEX_CODEC_NAME + "Meta", segmentSuffix).toLong()
                        == metaStream!!.filePointer
            )

            vectorsStream =
                directory.createOutput(
                    IndexFileNames.segmentFileName(segment, segmentSuffix, VECTORS_EXTENSION), context
                )
            CodecUtil.writeIndexHeader(
                vectorsStream!!, formatName, VERSION_CURRENT, si.getId(), segmentSuffix
            )
            require(
                CodecUtil.indexHeaderLength(formatName, segmentSuffix).toLong()
                        == vectorsStream!!.filePointer
            )

            indexWriter =
                FieldsIndexWriter(
                    directory,
                    segment,
                    segmentSuffix,
                    VECTORS_INDEX_EXTENSION,
                    VECTORS_INDEX_CODEC_NAME,
                    si.getId(),
                    blockShift,
                    context
                )

            metaStream!!.writeVInt(PackedInts.VERSION_CURRENT)
            metaStream!!.writeVInt(chunkSize)
            writer = BlockPackedWriter(vectorsStream!!, PACKED_BLOCK_SIZE)

            positionsBuf = IntArray(1024)
            startOffsetsBuf = IntArray(1024)
            lengthsBuf = IntArray(1024)
            payloadLengthsBuf = IntArray(1024)

            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(metaStream!!, vectorsStream!!, indexWriter!!, indexWriter!!)
            }
        }
    }

    @Throws(IOException::class)
    private fun copyChunks(
        mergeState: MergeState,
        sub: CompressingTermVectorsSub,
        fromDocID: Int,
        toDocID: Int
    ) {
        val reader: Lucene90CompressingTermVectorsReader =
            mergeState.termVectorsReaders[sub.readerIndex] as Lucene90CompressingTermVectorsReader
        require(reader.version == VERSION_CURRENT)
        require(reader.chunkSize == chunkSize)
        require(reader.compressionMode === compressionMode)
        require(!tooDirty(reader))
        require(mergeState.liveDocs[sub.readerIndex] == null)

        var docID = fromDocID
        val index: FieldsIndex = reader.indexReader

        // copy docs that belong to the previous chunk
        while (docID < toDocID && reader.isLoaded(docID)) {
            addAllDocVectors(reader.get(docID++), mergeState)
        }

        if (docID >= toDocID) {
            return
        }
        // copy chunks
        var fromPointer = index.getStartPointer(docID)
        val toPointer =
            if (toDocID == sub.maxDoc) reader.maxPointer else index.getStartPointer(toDocID)
        if (fromPointer < toPointer) {
            // flush any pending chunks
            if (!pendingDocs.isEmpty()) {
                flush(true)
            }
            val rawDocs: IndexInput = reader.vectorsStream
            rawDocs.seek(fromPointer)
            do {
                // iterate over each chunk. we use the vectors index to find chunk boundaries,
                // read the docstart + doccount from the chunk header (we write a new header, since doc
                // numbers will change),
                // and just copy the bytes directly.
                // read header
                val base: Int = rawDocs.readVInt()
                if (base != docID) {
                    throw CorruptIndexException(
                        "invalid state: base=$base, docID=$docID", rawDocs
                    )
                }

                val code: Int = rawDocs.readVInt()
                val bufferedDocs = code ushr 1

                // write a new index entry and new header for this chunk.
                indexWriter!!.writeIndex(bufferedDocs, vectorsStream!!.filePointer)
                vectorsStream!!.writeVInt(numDocs) // rebase
                vectorsStream!!.writeVInt(code)
                docID += bufferedDocs
                numDocs += bufferedDocs
                if (docID > toDocID) {
                    throw CorruptIndexException(
                        "invalid state: base=$base, count=$bufferedDocs, toDocID=$toDocID",
                        rawDocs
                    )
                }

                // copy bytes until the next chunk boundary (or end of chunk data).
                // using the stored fields index for this isn't the most efficient, but fast enough
                // and is a source of redundancy for detecting bad things.
                val end: Long = if (docID == sub.maxDoc) {
                    reader.maxPointer
                } else {
                    index.getStartPointer(docID)
                }
                vectorsStream!!.copyBytes(rawDocs, end - rawDocs.filePointer)
                ++numChunks
                val dirtyChunk = (code and 1) != 0
                if (dirtyChunk) {
                    numDirtyChunks++
                    numDirtyDocs += bufferedDocs.toLong()
                }
                fromPointer = end
            } while (fromPointer < toPointer)
        }
        // copy leftover docs that don't form a complete chunk
        require(!reader.isLoaded(docID))
        while (docID < toDocID) {
            addAllDocVectors(reader.get(docID++), mergeState)
        }
    }

    @Throws(IOException::class)
    override fun merge(mergeState: MergeState): Int {
        val numReaders: Int = mergeState.termVectorsReaders.size
        val matchingReaders = MatchingReaders(mergeState)
        val subs: MutableList<CompressingTermVectorsSub> = ArrayList(numReaders)
        for (i in 0..<numReaders) {
            val reader: TermVectorsReader? = mergeState.termVectorsReaders[i]
            reader?.checkIntegrity()
            val bulkMerge = canPerformBulkMerge(mergeState, matchingReaders, i)
            subs.add(CompressingTermVectorsSub(mergeState, bulkMerge, i))
        }
        var docCount = 0
        val docIDMerger: DocIDMerger<CompressingTermVectorsSub> =
            DocIDMerger.of(subs, mergeState.needsIndexSort)
        var sub: CompressingTermVectorsSub? = docIDMerger.next()
        while (sub != null) {
            require(sub.mappedDocID == docCount) {  "${sub.mappedDocID} != $docCount" }
            if (sub.canPerformBulkMerge) {
                val fromDocID = sub.docID
                var toDocID = fromDocID
                val current: CompressingTermVectorsSub? = sub
                while ((docIDMerger.next().also { sub = it }) === current) {
                    ++toDocID
                    require(sub!!.docID == toDocID)
                }
                ++toDocID // exclusive bound
                copyChunks(mergeState, current!!, fromDocID, toDocID)
                docCount += toDocID - fromDocID
            } else {
                val reader: TermVectorsReader? = mergeState.termVectorsReaders[sub.readerIndex]
                val vectors: Fields? = reader?.get(sub.docID)
                addAllDocVectors(vectors, mergeState)
                ++docCount
                sub = docIDMerger.next()
            }
        }
        finish(docCount)
        return docCount
    }

    /**
     * Returns true if we should recompress this reader, even though we could bulk merge compressed
     * data
     *
     *
     * The last chunk written for a segment is typically incomplete, so without recompressing, in
     * some worst-case situations (e.g. frequent reopen with tiny flushes), over time the compression
     * ratio can degrade. This is a safety switch.
     */
    fun tooDirty(candidate: Lucene90CompressingTermVectorsReader): Boolean {
        // A segment is considered dirty only if it has enough dirty docs to make a full block
        // AND more than 1% blocks are dirty.
        return candidate.getNumDirtyDocs() > maxDocsPerChunk
                && candidate.getNumDirtyChunks() * 100 > candidate.getNumChunks()
    }

    private fun canPerformBulkMerge(
        mergeState: MergeState, matchingReaders: MatchingReaders, readerIndex: Int
    ): Boolean {
        if (mergeState.termVectorsReaders[readerIndex]
                    is Lucene90CompressingTermVectorsReader
        ) {
            val reader: Lucene90CompressingTermVectorsReader =
                mergeState.termVectorsReaders[readerIndex] as Lucene90CompressingTermVectorsReader
            return BULK_MERGE_ENABLED
                    && matchingReaders.matchingReaders[readerIndex]
                    && reader.compressionMode === compressionMode && reader.chunkSize == chunkSize && reader.version == VERSION_CURRENT && reader.packedIntsVersion == PackedInts.VERSION_CURRENT && mergeState.liveDocs[readerIndex] == null && !tooDirty(
                reader
            )
        }
        return false
    }

    private class CompressingTermVectorsSub(mergeState: MergeState, val canPerformBulkMerge: Boolean, val readerIndex: Int) :
        DocIDMerger.Sub(mergeState.docMaps!![readerIndex]) {
        val maxDoc: Int = mergeState.maxDocs[readerIndex]
        var docID: Int = -1

        override fun nextDoc(): Int {
            docID++
            return if (docID == maxDoc) {
                NO_MORE_DOCS
            } else {
                docID
            }
        }
    }

    override fun ramBytesUsed(): Long {
        return (positionsBuf.size
                + startOffsetsBuf.size
                + lengthsBuf.size
                + payloadLengthsBuf.size
                + termSuffixes.ramBytesUsed()
                + payloadBytes.ramBytesUsed()
                + lastTerm.bytes.size
                + scratchBuffer.ramBytesUsed())
    }

    override val childResources: MutableCollection<Accountable>
        get() = mutableListOf(termSuffixes, payloadBytes)

    companion object {
        const val VECTORS_EXTENSION: String = "tvd"
        const val VECTORS_INDEX_EXTENSION: String = "tvx"
        const val VECTORS_META_EXTENSION: String = "tvm"
        const val VECTORS_INDEX_CODEC_NAME: String = "Lucene90TermVectorsIndex"

        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = VERSION_START
        const val META_VERSION_START: Int = 0

        const val PACKED_BLOCK_SIZE: Int = 64

        const val POSITIONS: Int = 0x01
        const val OFFSETS: Int = 0x02
        const val PAYLOADS: Int = 0x04
        val FLAGS_BITS: Int = DirectWriter.bitsRequired((POSITIONS or OFFSETS or PAYLOADS).toLong())

        // bulk merge is scary: its caused corruption bugs in the past.
        // we try to be extra safe with this impl, but add an escape hatch to
        // have a workaround for undiscovered bugs.
        val BULK_MERGE_ENABLED_SYSPROP: String =
            Lucene90CompressingTermVectorsWriter::class.qualifiedName + ".enableBulkMerge"
        val BULK_MERGE_ENABLED: Boolean

        init {
            var v = true
            try {
                v = System.getProperty(BULK_MERGE_ENABLED_SYSPROP, "true").toBoolean()
            } catch (ignored: /*java.lang.Security*/Exception) {
            }
            BULK_MERGE_ENABLED = v
        }
    }
}
