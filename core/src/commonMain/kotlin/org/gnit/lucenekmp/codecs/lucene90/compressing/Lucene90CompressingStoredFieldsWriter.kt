package org.gnit.lucenekmp.codecs.lucene90.compressing

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.StoredFieldsWriter
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.codecs.compressing.MatchingReaders
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsReader.SerializedDocument
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.DocIDMerger
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.floatToIntBits
import kotlin.math.min

/**
 * [StoredFieldsWriter] impl for [Lucene90CompressingStoredFieldsFormat].
 *
 * @lucene.experimental
 */
class Lucene90CompressingStoredFieldsWriter internal constructor(
    directory: Directory,
    si: SegmentInfo,
    segmentSuffix: String,
    context: IOContext,
    formatName: String,
    compressionMode: CompressionMode,
    chunkSize: Int,
    maxDocsPerChunk: Int,
    blockShift: Int
) : StoredFieldsWriter() {
    private val segment: String
    private var indexWriter: FieldsIndexWriter? = null
    private var metaStream: IndexOutput? = null
    private var fieldsStream: IndexOutput? = null

    private var compressor: Compressor?
    private val compressionMode: CompressionMode
    private val chunkSize: Int
    private val maxDocsPerChunk: Int

    private val bufferedDocs: ByteBuffersDataOutput
    private var numStoredFields: IntArray // number of stored fields
    private var endOffsets: IntArray // end offsets in bufferedDocs
    private var docBase: Int // doc ID at the beginning of the chunk
    private var numBufferedDocs: Int // docBase + numBufferedDocs == current doc ID

    private var numChunks: Long = 0
    private var numDirtyChunks: Long = 0 // number of incomplete compressed blocks written
    private var numDirtyDocs: Long = 0 // cumulative number of missing docs in incomplete chunks

    @Throws(IOException::class)
    override fun close() {
        try {
            IOUtils.close(metaStream!!, fieldsStream!!, indexWriter!!, compressor!!)
        } finally {
            metaStream = null
            fieldsStream = null
            indexWriter = null
            compressor = null
        }
    }

    private var numStoredFieldsInDoc = 0

    @Throws(IOException::class)
    override fun startDocument() {
    }

    @Throws(IOException::class)
    override fun finishDocument() {
        if (numBufferedDocs == this.numStoredFields.size) {
            val newLength: Int = ArrayUtil.oversize(numBufferedDocs + 1, 4)
            this.numStoredFields = ArrayUtil.growExact(this.numStoredFields, newLength)
            endOffsets = ArrayUtil.growExact(endOffsets, newLength)
        }
        this.numStoredFields[numBufferedDocs] = numStoredFieldsInDoc
        numStoredFieldsInDoc = 0
        endOffsets[numBufferedDocs] = Math.toIntExact(bufferedDocs.size())
        ++numBufferedDocs
        if (triggerFlush()) {
            flush(false)
        }
    }

    @Throws(IOException::class)
    private fun writeHeader(
        docBase: Int,
        numBufferedDocs: Int,
        numStoredFields: IntArray,
        lengths: IntArray,
        sliced: Boolean,
        dirtyChunk: Boolean
    ) {
        val slicedBit = if (sliced) 1 else 0
        val dirtyBit = if (dirtyChunk) 2 else 0
        // save docBase and numBufferedDocs
        fieldsStream!!.writeVInt(docBase)
        fieldsStream!!.writeVInt((numBufferedDocs shl 2) or dirtyBit or slicedBit)

        // save numStoredFields
        saveInts(numStoredFields, numBufferedDocs, fieldsStream!!)

        // save lengths
        saveInts(lengths, numBufferedDocs, fieldsStream!!)
    }

    private fun triggerFlush(): Boolean {
        return bufferedDocs.size() >= chunkSize
                ||  // chunks of at least chunkSize bytes
                numBufferedDocs >= maxDocsPerChunk
    }

    @Throws(IOException::class)
    private fun flush(force: Boolean) {
        require(triggerFlush() != force)
        numChunks++
        if (force) {
            numDirtyChunks++ // incomplete: we had to force this flush
            numDirtyDocs += numBufferedDocs.toLong()
        }
        indexWriter!!.writeIndex(numBufferedDocs, fieldsStream!!.getFilePointer())

        // transform end offsets into lengths
        val lengths = endOffsets
        for (i in numBufferedDocs - 1 downTo 1) {
            lengths[i] = endOffsets[i] - endOffsets[i - 1]
            require(lengths[i] >= 0)
        }
        val sliced = bufferedDocs.size() >= 2L * chunkSize
        val dirtyChunk = force
        writeHeader(docBase, numBufferedDocs, numStoredFields, lengths, sliced, dirtyChunk)
        val bytebuffers: ByteBuffersDataInput = bufferedDocs.toDataInput()
        // compress stored fields to fieldsStream!!.
        if (sliced) {
            // big chunk, slice it, using ByteBuffersDataInput ignore memory copy
            val capacity = bytebuffers.length().toInt()
            var compressed = 0
            while (compressed < capacity) {
                val l = min(chunkSize, capacity - compressed)
                val bbdi: ByteBuffersDataInput = bytebuffers.slice(compressed.toLong(), l.toLong())
                compressor!!.compress(bbdi, fieldsStream!!)
                compressed += chunkSize
            }
        } else {
            compressor!!.compress(bytebuffers, fieldsStream!!)
        }

        // reset
        docBase += numBufferedDocs
        numBufferedDocs = 0
        bufferedDocs.reset()
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: Int) {
        ++numStoredFieldsInDoc
        val infoAndBits = ((info!!.number.toLong()) shl TYPE_BITS) or NUMERIC_INT.toLong()
        bufferedDocs.writeVLong(infoAndBits)
        bufferedDocs.writeZInt(value)
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: Long) {
        ++numStoredFieldsInDoc
        val infoAndBits = ((info!!.number.toLong()) shl TYPE_BITS) or NUMERIC_LONG.toLong()
        bufferedDocs.writeVLong(infoAndBits)
        writeTLong(bufferedDocs, value)
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: Float) {
        ++numStoredFieldsInDoc
        val infoAndBits = ((info!!.number.toLong()) shl TYPE_BITS) or NUMERIC_FLOAT.toLong()
        bufferedDocs.writeVLong(infoAndBits)
        writeZFloat(bufferedDocs, value)
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: Double) {
        ++numStoredFieldsInDoc
        val infoAndBits = ((info!!.number.toLong()) shl TYPE_BITS) or NUMERIC_DOUBLE.toLong()
        bufferedDocs.writeVLong(infoAndBits)
        writeZDouble(bufferedDocs, value)
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: BytesRef) {
        ++numStoredFieldsInDoc
        val infoAndBits = ((info!!.number.toLong()) shl TYPE_BITS) or BYTE_ARR.toLong()
        bufferedDocs.writeVLong(infoAndBits)
        bufferedDocs.writeVInt(value.length)
        bufferedDocs.writeBytes(value.bytes, value.offset, value.length)
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: StoredFieldDataInput) {
        val length: Int = value.getLength()
        ++numStoredFieldsInDoc
        val infoAndBits = ((info!!.number.toLong()) shl TYPE_BITS) or BYTE_ARR.toLong()
        bufferedDocs.writeVLong(infoAndBits)
        bufferedDocs.writeVInt(length)
        bufferedDocs.copyBytes(value.getDataInput(), length.toLong())
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: String) {
        ++numStoredFieldsInDoc
        val infoAndBits = ((info!!.number.toLong()) shl TYPE_BITS) or STRING.toLong()
        bufferedDocs.writeVLong(infoAndBits)
        bufferedDocs.writeString(value)
    }

    @Throws(IOException::class)
    override fun finish(numDocs: Int) {
        if (numBufferedDocs > 0) {
            flush(true)
        } else {
            require(bufferedDocs.size() == 0L)
        }
        if (docBase != numDocs) {
            throw RuntimeException(
                "Wrote $docBase docs, finish called with numDocs=$numDocs"
            )
        }
        indexWriter!!.finish(numDocs, fieldsStream!!.getFilePointer(), metaStream!!)
        metaStream!!.writeVLong(numChunks)
        metaStream!!.writeVLong(numDirtyChunks)
        metaStream!!.writeVLong(numDirtyDocs)
        CodecUtil.writeFooter(metaStream!!)
        CodecUtil.writeFooter(fieldsStream!!)
        require(bufferedDocs.size() == 0L)
    }

    /** Sole constructor.  */
    init {
        checkNotNull(directory)
        this.segment = si.name
        this.compressionMode = compressionMode
        this.compressor = compressionMode.newCompressor()
        this.chunkSize = chunkSize
        this.maxDocsPerChunk = maxDocsPerChunk
        this.docBase = 0
        this.bufferedDocs = ByteBuffersDataOutput.newResettableInstance()
        this.numStoredFields = IntArray(16)
        this.endOffsets = IntArray(16)
        this.numBufferedDocs = 0

        var success = false
        try {
            metaStream =
                directory.createOutput(
                    IndexFileNames.segmentFileName(segment, segmentSuffix, META_EXTENSION), context
                )
            CodecUtil.writeIndexHeader(
                metaStream!!, INDEX_CODEC_NAME + "Meta", VERSION_CURRENT, si.getId(), segmentSuffix
            )
            require(
                CodecUtil.indexHeaderLength(INDEX_CODEC_NAME + "Meta", segmentSuffix).toLong()
                        == metaStream!!.getFilePointer()
            )

            fieldsStream =
                directory.createOutput(
                    IndexFileNames.segmentFileName(segment, segmentSuffix, FIELDS_EXTENSION), context
                )
            CodecUtil.writeIndexHeader(
                fieldsStream!!, formatName, VERSION_CURRENT, si.getId(), segmentSuffix
            )
            require(
                CodecUtil.indexHeaderLength(formatName, segmentSuffix).toLong()
                        == fieldsStream!!.getFilePointer()
            )

            indexWriter =
                FieldsIndexWriter(
                    directory,
                    segment,
                    segmentSuffix,
                    INDEX_EXTENSION,
                    INDEX_CODEC_NAME,
                    si.getId(),
                    blockShift,
                    context
                )

            metaStream!!.writeVInt(chunkSize)

            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(metaStream!!, fieldsStream!!, indexWriter!!)
            }
        }
    }

    @Throws(IOException::class)
    private fun copyOneDoc(reader: Lucene90CompressingStoredFieldsReader, docID: Int) {
        require(reader.getVersion() == VERSION_CURRENT)
        val doc: SerializedDocument = reader.serializedDocument(docID)
        startDocument()
        bufferedDocs.copyBytes(doc.`in`, doc.length.toLong())
        numStoredFieldsInDoc = doc.numStoredFields
        finishDocument()
    }

    @Throws(IOException::class)
    private fun copyChunks(
        mergeState: MergeState,
        sub: CompressingStoredFieldsMergeSub,
        fromDocID: Int,
        toDocID: Int
    ) {
        val reader: Lucene90CompressingStoredFieldsReader =
            mergeState.storedFieldsReaders[sub.readerIndex] as Lucene90CompressingStoredFieldsReader
        require(reader.getVersion() == VERSION_CURRENT)
        require(reader.getChunkSize() == chunkSize)
        require(reader.getCompressionMode() === compressionMode)
        require(!tooDirty(reader))
        require(mergeState.liveDocs[sub.readerIndex] == null)

        var docID = fromDocID
        val index: FieldsIndex = reader.getIndexReader()

        // copy docs that belong to the previous chunk
        while (docID < toDocID && reader.isLoaded(docID)) {
            copyOneDoc(reader, docID++)
        }
        if (docID >= toDocID) {
            return
        }
        // copy chunks
        var fromPointer: Long = index.getStartPointer(docID)
        val toPointer: Long =
            if (toDocID == sub.maxDoc) reader.getMaxPointer() else index.getStartPointer(toDocID)
        if (fromPointer < toPointer) {
            if (numBufferedDocs > 0) {
                flush(true)
            }
            val rawDocs: IndexInput = reader.getFieldsStream()
            rawDocs.seek(fromPointer)
            do {
                val base: Int = rawDocs.readVInt()
                val code: Int = rawDocs.readVInt()
                val bufferedDocs = code ushr 2
                if (base != docID) {
                    throw CorruptIndexException(
                        "invalid state: base=$base, docID=$docID", rawDocs
                    )
                }
                // write a new index entry and new header for this chunk.
                indexWriter!!.writeIndex(bufferedDocs, fieldsStream!!.getFilePointer())
                fieldsStream!!.writeVInt(docBase) // rebase
                fieldsStream!!.writeVInt(code)
                docID += bufferedDocs
                docBase += bufferedDocs
                if (docID > toDocID) {
                    throw CorruptIndexException(
                        "invalid state: base=$base, count=$bufferedDocs, toDocID=$toDocID",
                        rawDocs
                    )
                }
                // copy bytes until the next chunk boundary (or end of chunk data).
                // using the stored fields index for this isn't the most efficient, but fast enough
                // and is a source of redundancy for detecting bad things.
                val endChunkPointer: Long = if (docID == sub.maxDoc) {
                    reader.getMaxPointer()
                } else {
                    index.getStartPointer(docID)
                }
                fieldsStream!!.copyBytes(rawDocs, endChunkPointer - rawDocs.getFilePointer())
                ++numChunks
                val dirtyChunk = (code and 2) != 0
                if (dirtyChunk) {
                    require(bufferedDocs < maxDocsPerChunk)
                    ++numDirtyChunks
                    numDirtyDocs += bufferedDocs.toLong()
                }
                fromPointer = endChunkPointer
            } while (fromPointer < toPointer)
        }

        // copy leftover docs that don't form a complete chunk
        require(!reader.isLoaded(docID))
        while (docID < toDocID) {
            copyOneDoc(reader, docID++)
        }
    }

    @Throws(IOException::class)
    override fun merge(mergeState: MergeState): Int {
        val matchingReaders = MatchingReaders(mergeState)
        val visitors: Array<MergeVisitor?> = kotlin.arrayOfNulls(mergeState.storedFieldsReaders.size)
        val subs: MutableList<CompressingStoredFieldsMergeSub> =
            ArrayList(mergeState.storedFieldsReaders.size)
        for (i in 0..<mergeState.storedFieldsReaders.size) {
            val reader: StoredFieldsReader = mergeState.storedFieldsReaders[i]!!
            reader.checkIntegrity()
            val mergeStrategy = getMergeStrategy(mergeState, matchingReaders, i)
            if (mergeStrategy == MergeStrategy.VISITOR) {
                visitors[i] = MergeVisitor(mergeState, i)
            }
            subs.add(CompressingStoredFieldsMergeSub(mergeState, mergeStrategy, i))
        }
        var docCount = 0
        val docIDMerger: DocIDMerger<CompressingStoredFieldsMergeSub> =
            DocIDMerger.of(subs, mergeState.needsIndexSort)
        var sub: CompressingStoredFieldsMergeSub? = docIDMerger.next()
        while (sub != null) {
            require(sub.mappedDocID == docCount) {  "${sub.mappedDocID} != $docCount" }
            val reader: StoredFieldsReader = mergeState.storedFieldsReaders[sub.readerIndex]!!
            if (sub.mergeStrategy == MergeStrategy.BULK) {
                val fromDocID = sub.docID
                var toDocID = fromDocID
                val current: CompressingStoredFieldsMergeSub = sub
                while ((docIDMerger.next().also { sub = it }) === current) {
                    ++toDocID
                    require(sub!!.docID == toDocID)
                }
                ++toDocID // exclusive bound
                copyChunks(mergeState, current, fromDocID, toDocID)
                docCount += (toDocID - fromDocID)
            } else if (sub.mergeStrategy == MergeStrategy.DOC) {
                copyOneDoc(reader as Lucene90CompressingStoredFieldsReader, sub.docID)
                ++docCount
                sub = docIDMerger.next()
            } else if (sub.mergeStrategy == MergeStrategy.VISITOR) {
                checkNotNull(visitors[sub.readerIndex])
                startDocument()
                reader.document(sub.docID, visitors[sub.readerIndex]!!)
                finishDocument()
                ++docCount
                sub = docIDMerger.next()
            } else {
                throw AssertionError("Unknown merge strategy [" + sub.mergeStrategy + "]")
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
    fun tooDirty(candidate: Lucene90CompressingStoredFieldsReader): Boolean {
        // A segment is considered dirty only if it has enough dirty docs to make a full block
        // AND more than 1% blocks are dirty.
        return candidate.getNumDirtyDocs() > maxDocsPerChunk
                && candidate.getNumDirtyChunks() * 100 > candidate.getNumChunks()
    }

    private enum class MergeStrategy {
        /** Copy chunk by chunk in a compressed format  */
        BULK,

        /** Copy document by document in a decompressed format  */
        DOC,

        /** Copy field by field of decompressed documents  */
        VISITOR
    }

    private fun getMergeStrategy(
        mergeState: MergeState, matchingReaders: MatchingReaders, readerIndex: Int
    ): MergeStrategy {
        val candidate: StoredFieldsReader = mergeState.storedFieldsReaders[readerIndex]!!
        if (!matchingReaders.matchingReaders[readerIndex] || candidate !is Lucene90CompressingStoredFieldsReader || candidate.getVersion() != VERSION_CURRENT) {
            return MergeStrategy.VISITOR
        }
        val reader: Lucene90CompressingStoredFieldsReader =
            candidate
        return if (BULK_MERGE_ENABLED
            && reader.getCompressionMode() === compressionMode && reader.getChunkSize() == chunkSize // its not worth fine-graining this if there are deletions.
            && mergeState.liveDocs[readerIndex] == null && !tooDirty(reader)
        ) {
            MergeStrategy.BULK
        } else {
            MergeStrategy.DOC
        }
    }

    private class CompressingStoredFieldsMergeSub(
        mergeState: MergeState,
        val mergeStrategy: MergeStrategy,
        val readerIndex: Int
    ) : DocIDMerger.Sub(mergeState.docMaps!![readerIndex]) {
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
        return (bufferedDocs.ramBytesUsed()
                + numStoredFields.size * Int.SIZE_BYTES.toLong() + endOffsets.size * Int.SIZE_BYTES.toLong())
    }

    companion object {
        /** Extension of stored fields file  */
        const val FIELDS_EXTENSION: String = "fdt"

        /** Extension of stored fields index  */
        const val INDEX_EXTENSION: String = "fdx"

        /** Extension of stored fields meta  */
        const val META_EXTENSION: String = "fdm"

        /** Codec name for the index.  */
        const val INDEX_CODEC_NAME: String = "Lucene90FieldsIndex"

        const val STRING: Int = 0x00
        const val BYTE_ARR: Int = 0x01
        const val NUMERIC_INT: Int = 0x02
        const val NUMERIC_FLOAT: Int = 0x03
        const val NUMERIC_LONG: Int = 0x04
        const val NUMERIC_DOUBLE: Int = 0x05

        val TYPE_BITS: Int = PackedInts.bitsRequired(NUMERIC_DOUBLE.toLong())
        val TYPE_MASK: Int = PackedInts.maxValue(TYPE_BITS).toInt()

        const val VERSION_START: Int = 1
        const val VERSION_CURRENT: Int = VERSION_START
        const val META_VERSION_START: Int = 0

        @Throws(IOException::class)
        private fun saveInts(values: IntArray, length: Int, out: DataOutput) {
            if (length == 1) {
                out.writeVInt(values[0])
            } else {
                StoredFieldsInts.writeInts(values, 0, length, out)
            }
        }

        // -0 isn't compressed.
        val NEGATIVE_ZERO_FLOAT: Int = Float.floatToIntBits(-0f)
        val NEGATIVE_ZERO_DOUBLE: Long = Double.doubleToLongBits(-0.0)

        // for compression of timestamps
        const val SECOND: Long = 1000L
        const val HOUR: Long = 60 * 60 * SECOND
        const val DAY: Long = 24 * HOUR
        const val SECOND_ENCODING: Int = 0x40
        const val HOUR_ENCODING: Int = 0x80
        const val DAY_ENCODING: Int = 0xC0

        /**
         * Writes a float in a variable-length format. Writes between one and five bytes. Small integral
         * values typically take fewer bytes.
         *
         *
         * ZFloat --&gt; Header, Bytes*
         *
         *
         *  * Header --&gt; [Uint8][DataOutput.writeByte]. When it is equal to 0xFF then the value
         * is negative and stored in the next 4 bytes. Otherwise if the first bit is set then the
         * other bits in the header encode the value plus one and no other bytes are read.
         * Otherwise, the value is a positive float value whose first byte is the header, and 3
         * bytes need to be read to complete it.
         *  * Bytes --&gt; Potential additional bytes to read depending on the header.
         *
         */
        @Throws(IOException::class)
        fun writeZFloat(out: DataOutput, f: Float) {
            val intVal = f.toInt()
            val floatBits: Int = Float.floatToIntBits(f)

            if (f == intVal.toFloat() && intVal >= -1 && intVal <= 0x7D && floatBits != NEGATIVE_ZERO_FLOAT) {
                // small integer value [-1..125]: single byte
                out.writeByte((0x80 or (1 + intVal)).toByte())
            } else if ((floatBits ushr 31) == 0) {
                // other positive floats: 4 bytes
                out.writeByte((floatBits shr 24).toByte())
                out.writeShort((floatBits ushr 8).toShort())
                out.writeByte(floatBits.toByte())
            } else {
                // other negative float: 5 bytes
                out.writeByte(0xFF.toByte())
                out.writeInt(floatBits)
            }
        }

        /**
         * Writes a float in a variable-length format. Writes between one and five bytes. Small integral
         * values typically take fewer bytes.
         *
         *
         * ZFloat --&gt; Header, Bytes*
         *
         *
         *  * Header --&gt; [Uint8][DataOutput.writeByte]. When it is equal to 0xFF then the value
         * is negative and stored in the next 8 bytes. When it is equal to 0xFE then the value is
         * stored as a float in the next 4 bytes. Otherwise if the first bit is set then the other
         * bits in the header encode the value plus one and no other bytes are read. Otherwise, the
         * value is a positive float value whose first byte is the header, and 7 bytes need to be
         * read to complete it.
         *  * Bytes --&gt; Potential additional bytes to read depending on the header.
         *
         */
        @Throws(IOException::class)
        fun writeZDouble(out: DataOutput, d: Double) {
            val intVal = d.toInt()
            val doubleBits: Long = Double.doubleToLongBits(d)

            if (d == intVal.toDouble() && intVal >= -1 && intVal <= 0x7C && doubleBits != NEGATIVE_ZERO_DOUBLE) {
                // small integer value [-1..124]: single byte
                out.writeByte((0x80 or (intVal + 1)).toByte())
                return
            } else if (d == d.toFloat().toDouble()) {
                // d has an accurate float representation: 5 bytes
                out.writeByte(0xFE.toByte())
                out.writeInt(Float.floatToIntBits(d.toFloat()))
            } else if ((doubleBits ushr 63) == 0L) {
                // other positive doubles: 8 bytes
                out.writeByte((doubleBits shr 56).toByte())
                out.writeInt((doubleBits ushr 24).toInt())
                out.writeShort((doubleBits ushr 8).toShort())
                out.writeByte((doubleBits).toByte())
            } else {
                // other negative doubles: 9 bytes
                out.writeByte(0xFF.toByte())
                out.writeLong(doubleBits)
            }
        }

        /**
         * Writes a long in a variable-length format. Writes between one and ten bytes. Small values or
         * values representing timestamps with day, hour or second precision typically require fewer
         * bytes.
         *
         *
         * ZLong --&gt; Header, Bytes*
         *
         *
         *  * Header --&gt; The first two bits indicate the compression scheme:
         *
         *  * 00 - uncompressed
         *  * 01 - multiple of 1000 (second)
         *  * 10 - multiple of 3600000 (hour)
         *  * 11 - multiple of 86400000 (day)
         *
         * Then the next bit is a continuation bit, indicating whether more bytes need to be read,
         * and the last 5 bits are the lower bits of the encoded value. In order to reconstruct the
         * value, you need to combine the 5 lower bits of the header with a vLong in the next bytes
         * (if the continuation bit is set to 1). Then [       zigzag-decode][BitUtil.zigZagDecode] it and finally multiply by the multiple corresponding to the compression
         * scheme.
         *  * Bytes --&gt; Potential additional bytes to read depending on the header.
         *
         */
        // T for "timestamp"
        @Throws(IOException::class)
        fun writeTLong(out: DataOutput, l: Long) {
            var l = l
            var header: Int
            if (l % SECOND != 0L) {
                header = 0
            } else if (l % DAY == 0L) {
                // timestamp with day precision
                header = DAY_ENCODING
                l /= DAY
            } else if (l % HOUR == 0L) {
                // timestamp with hour precision, or day precision with a timezone
                header = HOUR_ENCODING
                l /= HOUR
            } else {
                // timestamp with second precision
                header = SECOND_ENCODING
                l /= SECOND
            }

            val zigZagL: Long = BitUtil.zigZagEncode(l)
            header = header or (zigZagL and 0x1FL).toInt() // last 5 bits
            val upperBits = zigZagL ushr 5
            if (upperBits != 0L) {
                header = header or 0x20
            }
            out.writeByte(header.toByte())
            if (upperBits != 0L) {
                out.writeVLong(upperBits)
            }
        }

        // bulk merge is scary: its caused corruption bugs in the past.
        // we try to be extra safe with this impl, but add an escape hatch to
        // have a workaround for undiscovered bugs.
        val BULK_MERGE_ENABLED_SYSPROP: String =
            Lucene90CompressingStoredFieldsWriter::class.qualifiedName + ".enableBulkMerge"
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
