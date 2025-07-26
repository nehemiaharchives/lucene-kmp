package org.gnit.lucenekmp.codecs.lucene90.compressing

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.BYTE_ARR
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.DAY
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.DAY_ENCODING
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.FIELDS_EXTENSION
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.HOUR
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.HOUR_ENCODING
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.INDEX_CODEC_NAME
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.INDEX_EXTENSION
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.META_EXTENSION
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.META_VERSION_START
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.NUMERIC_DOUBLE
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.NUMERIC_FLOAT
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.NUMERIC_INT
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.NUMERIC_LONG
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.SECOND
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.SECOND_ENCODING
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.STRING
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.TYPE_BITS
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.TYPE_MASK
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.VERSION_CURRENT
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsWriter.Companion.VERSION_START
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.LongsRef
import okio.EOFException
import okio.IOException
import org.gnit.lucenekmp.index.StoredFieldVisitor.*
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.longBitsToDouble
import org.gnit.lucenekmp.jdkport.toHexString
import kotlin.experimental.and
import kotlin.math.min

/**
 * [StoredFieldsReader] impl for [Lucene90CompressingStoredFieldsFormat].
 *
 * @lucene.experimental
 */
class Lucene90CompressingStoredFieldsReader : StoredFieldsReader {
    val version: Int
    private val fieldInfos: FieldInfos?
    private val indexReader: FieldsIndex
    val maxPointer: Long
    private val fieldsStream: IndexInput
    val chunkSize: Int
    private val compressionMode: CompressionMode
    private val decompressor: Decompressor
    val numDocs: Int
    private val merging: Boolean
    private val state: BlockState
    private val numChunks: Long // number of written blocks
    private val numDirtyChunks: Long // number of incomplete compressed blocks written
    private val numDirtyDocs: Long // cumulative number of docs in incomplete chunks

    // Cache of recently prefetched block IDs. This helps reduce chances of prefetching the same block
    // multiple times, which is otherwise likely due to index sorting or recursive graph bisection
    // clustering similar documents together. NOTE: this cache must be small since it's fully scanned.
    private val prefetchedBlockIDCache: LongArray
    private var prefetchedBlockIDCacheIndex = 0
    private var closed = false

    // used by clone
    private constructor(reader: Lucene90CompressingStoredFieldsReader, merging: Boolean) {
        this.version = reader.version
        this.fieldInfos = reader.fieldInfos
        this.fieldsStream = reader.fieldsStream.clone()
        this.indexReader = reader.indexReader.clone()
        this.maxPointer = reader.maxPointer
        this.chunkSize = reader.chunkSize
        this.compressionMode = reader.compressionMode
        this.decompressor = reader.decompressor.clone()
        this.numDocs = reader.numDocs
        this.numChunks = reader.numChunks
        this.numDirtyChunks = reader.numDirtyChunks
        this.numDirtyDocs = reader.numDirtyDocs
        this.prefetchedBlockIDCache = LongArray(PREFETCH_CACHE_SIZE)
        Arrays.fill(prefetchedBlockIDCache, -1)
        this.merging = merging
        this.state = this.BlockState()
        this.closed = false
    }

    /** Sole constructor.  */
    constructor(
        d: Directory,
        si: SegmentInfo,
        segmentSuffix: String,
        fn: FieldInfos?,
        context: IOContext,
        formatName: String,
        compressionMode: CompressionMode
    ) {
        this.compressionMode = compressionMode
        val segment: String = si.name
        var success = false
        fieldInfos = fn
        numDocs = si.maxDoc()

        val fieldsStreamFN: String =
            IndexFileNames.segmentFileName(segment, segmentSuffix, FIELDS_EXTENSION)
        var metaIn: ChecksumIndexInput? = null
        try {
            // Open the data file
            fieldsStream = d.openInput(fieldsStreamFN, context.withReadAdvice(ReadAdvice.RANDOM))
            version =
                CodecUtil.checkIndexHeader(
                    fieldsStream, formatName, VERSION_START, VERSION_CURRENT, si.getId(), segmentSuffix
                )
            require(
                CodecUtil.indexHeaderLength(formatName, segmentSuffix).toLong()
                        == fieldsStream.filePointer
            )

            val metaStreamFN: String =
                IndexFileNames.segmentFileName(segment, segmentSuffix, META_EXTENSION)
            metaIn = d.openChecksumInput(metaStreamFN)
            CodecUtil.checkIndexHeader(
                metaIn,
                INDEX_CODEC_NAME + "Meta",
                META_VERSION_START,
                version,
                si.getId(),
                segmentSuffix
            )

            chunkSize = metaIn.readVInt()

            decompressor = compressionMode.newDecompressor()
            this.prefetchedBlockIDCache = LongArray(PREFETCH_CACHE_SIZE)
            Arrays.fill(prefetchedBlockIDCache, -1)
            this.merging = false
            this.state = this.BlockState()

            // NOTE: data file is too costly to verify checksum against all the bytes on open,
            // but for now we at least verify proper structure of the checksum footer: which looks
            // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
            // such as file truncation.
            CodecUtil.retrieveChecksum(fieldsStream)

            var maxPointer: Long = -1
            var indexReader: FieldsIndex? = null

            val fieldsIndexReader =
                FieldsIndexReader(
                    d,
                    si.name,
                    segmentSuffix,
                    INDEX_EXTENSION,
                    INDEX_CODEC_NAME,
                    si.getId(),
                    metaIn,
                    context
                )
            indexReader = fieldsIndexReader
            maxPointer = fieldsIndexReader.maxPointer

            this.maxPointer = maxPointer
            this.indexReader = indexReader

            numChunks = metaIn.readVLong()
            numDirtyChunks = metaIn.readVLong()
            numDirtyDocs = metaIn.readVLong()

            if (numChunks < numDirtyChunks) {
                throw CorruptIndexException(
                    ("Cannot have more dirty chunks than chunks: numChunks="
                            + numChunks
                            + ", numDirtyChunks="
                            + numDirtyChunks),
                    metaIn
                )
            }
            if ((numDirtyChunks == 0L) != (numDirtyDocs == 0L)) {
                throw CorruptIndexException(
                    ("Cannot have dirty chunks without dirty docs or vice-versa: numDirtyChunks="
                            + numDirtyChunks
                            + ", numDirtyDocs="
                            + numDirtyDocs),
                    metaIn
                )
            }
            if (numDirtyDocs < numDirtyChunks) {
                throw CorruptIndexException(
                    ("Cannot have more dirty chunks than documents within dirty chunks: numDirtyChunks="
                            + numDirtyChunks
                            + ", numDirtyDocs="
                            + numDirtyDocs),
                    metaIn
                )
            }

            CodecUtil.checkFooter(metaIn, null)
            metaIn.close()

            success = true
        } catch (t: Throwable) {
            if (metaIn != null) {
                CodecUtil.checkFooter(metaIn, t)
                throw AssertionError("unreachable")
            } else {
                throw t
            }
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this, metaIn!!)
            }
        }
    }

    /**
     * @throws AlreadyClosedException if this FieldsReader is closed
     */
    @Throws(AlreadyClosedException::class)
    private fun ensureOpen() {
        if (closed) {
            throw AlreadyClosedException("this FieldsReader is closed")
        }
    }

    /** Close the underlying [IndexInput]s.  */
    @Throws(IOException::class)
    override fun close() {
        if (!closed) {
            IOUtils.close(indexReader, fieldsStream)
            closed = true
        }
    }

    /**
     * A serialized document, you need to decode its input in order to get an actual [Document].
     */
    class SerializedDocument(// the serialized data
        val `in`: DataInput, // the number of bytes on which the document is encoded
        val length: Int, // the number of stored fields
        val numStoredFields: Int
    )

    /** Keeps state about the current block of documents.  */
    private inner class BlockState {
        private var docBase = 0
        private var chunkDocs = 0

        // whether the block has been sliced, this happens for large documents
        private var sliced = false

        private var offsets: LongArray = LongsRef.EMPTY_LONGS
        private var numStoredFields: LongArray = LongsRef.EMPTY_LONGS

        // the start pointer at which you can read the compressed documents
        private var startPointer: Long = 0

        private val spare: BytesRef?
        private val bytes: BytesRef?

        init {
            if (merging) {
                spare = BytesRef()
                bytes = BytesRef()
            } else {
                bytes = null
                spare = bytes
            }
        }

        fun contains(docID: Int): Boolean {
            return docID >= docBase && docID < docBase + chunkDocs
        }

        /** Reset this block so that it stores state for the block that contains the given doc id.  */
        @Throws(IOException::class)
        fun reset(docID: Int) {
            var success = false
            try {
                doReset(docID)
                success = true
            } finally {
                if (!success) {
                    // if the read failed, set chunkDocs to 0 so that it does not
                    // contain any docs anymore and is not reused. This should help
                    // get consistent exceptions when trying to get several
                    // documents which are in the same corrupted block since it will
                    // force the header to be decoded again
                    chunkDocs = 0
                }
            }
        }

        @Throws(IOException::class)
        fun doReset(docID: Int) {
            docBase = fieldsStream.readVInt()
            val token: Int = fieldsStream.readVInt()
            chunkDocs = token ushr 2
            if (!contains(docID) || docBase + chunkDocs > numDocs) {
                throw CorruptIndexException(
                    ("Corrupted: docID="
                            + docID
                            + ", docBase="
                            + docBase
                            + ", chunkDocs="
                            + chunkDocs
                            + ", numDocs="
                            + numDocs),
                    fieldsStream
                )
            }

            sliced = (token and 1) != 0

            offsets = ArrayUtil.growNoCopy(offsets, chunkDocs + 1)
            numStoredFields = ArrayUtil.growNoCopy(numStoredFields, chunkDocs)

            if (chunkDocs == 1) {
                numStoredFields[0] = fieldsStream.readVInt().toLong()
                offsets[1] = fieldsStream.readVInt().toLong()
            } else {
                // Number of stored fields per document
                StoredFieldsInts.readInts(fieldsStream, chunkDocs, numStoredFields, 0)
                // The stream encodes the length of each document and we decode
                // it into a list of monotonically increasing offsets
                StoredFieldsInts.readInts(fieldsStream, chunkDocs, offsets, 1)
                for (i in 0..<chunkDocs) {
                    offsets[i + 1] += offsets[i]
                }

                // Additional validation: only the empty document has a serialized length of 0
                for (i in 0..<chunkDocs) {
                    val len = offsets[i + 1] - offsets[i]
                    val storedFields = numStoredFields[i]
                    if ((len == 0L) != (storedFields == 0L)) {
                        throw CorruptIndexException(
                            "length=$len, numStoredFields=$storedFields", fieldsStream
                        )
                    }
                }
            }

            startPointer = fieldsStream.filePointer

            if (merging) {
                val totalLength: Int = Math.toIntExact(offsets[chunkDocs])
                // decompress eagerly
                if (sliced) {
                    bytes!!.length = 0
                    bytes.offset = bytes.length
                    var decompressed = 0
                    while (decompressed < totalLength) {
                        val toDecompress = min(totalLength - decompressed, chunkSize)
                        decompressor.decompress(fieldsStream, toDecompress, 0, toDecompress, spare!!)
                        bytes.bytes = ArrayUtil.grow(bytes.bytes, bytes.length + spare.length)
                        System.arraycopy(spare.bytes, spare.offset, bytes.bytes, bytes.length, spare.length)
                        bytes.length += spare.length
                        decompressed += toDecompress
                    }
                } else {
                    decompressor.decompress(fieldsStream, totalLength, 0, totalLength, bytes!!)
                }
                if (bytes.length != totalLength) {
                    throw CorruptIndexException(
                        "Corrupted: expected chunk size = " + totalLength + ", got " + bytes.length,
                        fieldsStream
                    )
                }
            }
        }

        /**
         * Get the serialized representation of the given docID. This docID has to be contained in the
         * current block.
         */
        @Throws(IOException::class)
        fun document(docID: Int): SerializedDocument {
            require(contains(docID))

            val index = docID - docBase
            val offset: Int = Math.toIntExact(offsets[index])
            val length: Int = Math.toIntExact(offsets[index + 1]) - offset
            val totalLength: Int = Math.toIntExact(offsets[chunkDocs])
            val numStoredFields: Int = Math.toIntExact(this.numStoredFields[index])
            val bytes: BytesRef = if (merging) {
                this.bytes!!
            } else {
                BytesRef()
            }

            val documentInput: DataInput
            if (length == 0) {
                // empty
                documentInput = ByteArrayDataInput()
            } else if (merging) {
                // already decompressed
                documentInput = ByteArrayDataInput(bytes.bytes, bytes.offset + offset, length)
            } else if (sliced) {
                fieldsStream.seek(startPointer)
                decompressor.decompress(
                    fieldsStream, chunkSize, offset, min(length, chunkSize - offset), bytes
                )
                documentInput =
                    object : DataInput() {
                        var decompressed: Int = bytes.length

                        @Throws(IOException::class)
                        fun fillBuffer() {
                            require(decompressed <= length)
                            if (decompressed == length) {
                                throw EOFException()
                            }
                            val toDecompress = min(length - decompressed, chunkSize)
                            decompressor.decompress(fieldsStream, toDecompress, 0, toDecompress, bytes)
                            decompressed += toDecompress
                        }

                        @Throws(IOException::class)
                        override fun readByte(): Byte {
                            if (bytes.length == 0) {
                                fillBuffer()
                            }
                            --bytes.length
                            return bytes.bytes[bytes.offset++]
                        }

                        @Throws(IOException::class)
                        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
                            var offset = offset
                            var len = len
                            while (len > bytes.length) {
                                System.arraycopy(bytes.bytes, bytes.offset, b, offset, bytes.length)
                                len -= bytes.length
                                offset += bytes.length
                                fillBuffer()
                            }
                            System.arraycopy(bytes.bytes, bytes.offset, b, offset, len)
                            bytes.offset += len
                            bytes.length -= len
                        }

                        @Throws(IOException::class)
                        override fun skipBytes(numBytes: Long) {
                            var numBytes = numBytes
                            require(numBytes >= 0) { "numBytes must be >= 0, got $numBytes" }
                            while (numBytes > bytes.length) {
                                numBytes -= bytes.length
                                fillBuffer()
                            }
                            bytes.offset += numBytes.toInt()
                            bytes.length -= numBytes.toInt()
                        }
                    }
            } else {
                fieldsStream.seek(startPointer)
                decompressor.decompress(fieldsStream, totalLength, offset, length, bytes)
                require(bytes.length == length)
                documentInput = ByteArrayDataInput(bytes.bytes, bytes.offset, bytes.length)
            }

            return SerializedDocument(documentInput, length, numStoredFields)
        }
    }

    @Throws(IOException::class)
    override fun prefetch(docID: Int) {
        val blockID: Long = indexReader.getBlockID(docID)

        for (prefetchedBlockID in prefetchedBlockIDCache) {
            if (prefetchedBlockID == blockID) {
                return
            }
        }

        val blockStartPointer: Long = indexReader.getBlockStartPointer(blockID)
        val blockLength: Long = indexReader.getBlockLength(blockID)
        fieldsStream.prefetch(blockStartPointer, blockLength)

        prefetchedBlockIDCache[prefetchedBlockIDCacheIndex++ and PREFETCH_CACHE_MASK] = blockID
    }

    @Throws(IOException::class)
    fun serializedDocument(docID: Int): SerializedDocument {
        if (!state.contains(docID)) {
            fieldsStream.seek(indexReader.getStartPointer(docID))
            state.reset(docID)
        }
        require(state.contains(docID))
        return state.document(docID)
    }

    /** Checks if a given docID was loaded in the current block state.  */
    fun isLoaded(docID: Int): Boolean {
        check(merging) { "isLoaded should only ever get called on a merge instance" }
        check(version == VERSION_CURRENT) { "isLoaded should only ever get called when the reader is on the current version" }
        return state.contains(docID)
    }

    @Throws(IOException::class)
    override fun document(docID: Int, visitor: StoredFieldVisitor) {
        val doc = serializedDocument(docID)

        for (fieldIDX in 0..<doc.numStoredFields) {
            val infoAndBits: Long = doc.`in`.readVLong()
            val fieldNumber = (infoAndBits ushr TYPE_BITS).toInt()
            val fieldInfo: FieldInfo = fieldInfos!!.fieldInfo(fieldNumber)!!

            val bits = (infoAndBits and TYPE_MASK.toLong()).toInt()
            require(bits <= NUMERIC_DOUBLE) { "bits=" + Int.toHexString(bits) }

            when (visitor.needsField(fieldInfo)) {
                Status.YES -> readField(doc.`in`, visitor, fieldInfo, bits)
                Status.NO -> {
                    if (fieldIDX
                        == doc.numStoredFields - 1
                    ) { // don't skipField on last field value; treat like STOP
                        return
                    }
                    skipField(doc.`in`, bits)
                }

                Status.STOP -> return
                null -> UnsupportedOperationException(
                    "StoredFieldVisitor returned null for field: " + fieldInfo.name
                )
            }
        }
    }

    override fun clone(): StoredFieldsReader {
        ensureOpen()
        return Lucene90CompressingStoredFieldsReader(this, false)
    }

    override val mergeInstance: StoredFieldsReader
        get() {
            ensureOpen()
            return Lucene90CompressingStoredFieldsReader(this, true)
        }

    fun getCompressionMode(): CompressionMode {
        return compressionMode
    }

    fun getIndexReader(): FieldsIndex {
        return indexReader
    }

    fun getFieldsStream(): IndexInput {
        return fieldsStream
    }

    fun getNumDirtyDocs(): Long {
        check(version == VERSION_CURRENT) { "getNumDirtyDocs should only ever get called when the reader is on the current version" }
        require(numDirtyDocs >= 0)
        return numDirtyDocs
    }

    fun getNumDirtyChunks(): Long {
        check(version == VERSION_CURRENT) { "getNumDirtyChunks should only ever get called when the reader is on the current version" }
        require(numDirtyChunks >= 0)
        return numDirtyChunks
    }

    fun getNumChunks(): Long {
        check(version == VERSION_CURRENT) { "getNumChunks should only ever get called when the reader is on the current version" }
        require(numChunks >= 0)
        return numChunks
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        indexReader.checkIntegrity()
        CodecUtil.checksumEntireFile(fieldsStream)
    }

    override fun toString(): String {
        return (this::class.simpleName
                + "(mode="
                + compressionMode
                + ",chunksize="
                + chunkSize
                + ")")
    }

    companion object {
        private const val PREFETCH_CACHE_SIZE = 1 shl 4
        private const val PREFETCH_CACHE_MASK = PREFETCH_CACHE_SIZE - 1

        @Throws(IOException::class)
        private fun readField(`in`: DataInput, visitor: StoredFieldVisitor, info: FieldInfo, bits: Int) {
            when (bits and TYPE_MASK) {
                BYTE_ARR -> {
                    val length: Int = `in`.readVInt()
                    visitor.binaryField(info, StoredFieldDataInput(`in`, length))
                }

                STRING -> visitor.stringField(info, `in`.readString())
                NUMERIC_INT -> visitor.intField(info, `in`.readZInt())
                NUMERIC_FLOAT -> visitor.floatField(info, readZFloat(`in`))
                NUMERIC_LONG -> visitor.longField(info, readTLong(`in`))
                NUMERIC_DOUBLE -> visitor.doubleField(info, readZDouble(`in`))
                else -> throw AssertionError("Unknown type flag: " + Int.toHexString(bits))
            }
        }

        @Throws(IOException::class)
        private fun skipField(`in`: DataInput, bits: Int) {
            when (bits and TYPE_MASK) {
                BYTE_ARR, STRING -> {
                    val length: Int = `in`.readVInt()
                    `in`.skipBytes(length.toLong())
                }

                NUMERIC_INT -> `in`.readZInt()
                NUMERIC_FLOAT -> readZFloat(`in`)
                NUMERIC_LONG -> readTLong(`in`)
                NUMERIC_DOUBLE -> readZDouble(`in`)
                else -> throw AssertionError("Unknown type flag: " + Int.toHexString(bits))
            }
        }

        /**
         * Reads a float in a variable-length format. Reads between one and five bytes. Small integral
         * values typically take fewer bytes.
         */
        @Throws(IOException::class)
        fun readZFloat(`in`: DataInput): Float {
            val b: Int = (`in`.readByte() and 0xFF.toByte()).toInt()
            if (b == 0xFF) {
                // negative value
                return Float.intBitsToFloat(`in`.readInt())
            } else if ((b and 0x80) != 0) {
                // small integer [-1..125]
                return ((b and 0x7f) - 1).toFloat()
            } else {
                // positive float
                val bits = b shl 24 or ((`in`.readShort() and 0xFFFF.toShort()).toInt() shl 8) or (`in`.readByte() and 0xFF.toByte()).toInt()
                return Float.intBitsToFloat(bits)
            }
        }

        /**
         * Reads a double in a variable-length format. Reads between one and nine bytes. Small integral
         * values typically take fewer bytes.
         */
        @Throws(IOException::class)
        fun readZDouble(`in`: DataInput): Double {
            val b: Int = (`in`.readByte() and 0xFF.toByte()).toInt()
            if (b == 0xFF) {
                // negative value
                return Double.longBitsToDouble(`in`.readLong())
            } else if (b == 0xFE) {
                // float
                return Float.intBitsToFloat(`in`.readInt()).toDouble()
            } else if ((b and 0x80) != 0) {
                // small integer [-1..124]
                return ((b and 0x7f) - 1).toDouble()
            } else {
                // positive double
                val bits =
                    ((b.toLong()) shl 56 or ((`in`.readInt() and 0xFFFFFFFFL.toInt()) shl 24).toLong()
                            or ((`in`.readShort() and 0xFFFFL.toShort()).toInt() shl 8).toLong()
                            or (`in`.readByte() and 0xFFL.toByte()).toLong())
                return Double.longBitsToDouble(bits)
            }
        }

        /**
         * Reads a long in a variable-length format. Reads between one andCorePropLo nine bytes. Small
         * values typically take fewer bytes.
         */
        @Throws(IOException::class)
        fun readTLong(`in`: DataInput): Long {
            val header: Int = (`in`.readByte() and 0xFF.toByte()).toInt()

            var bits = (header and 0x1F).toLong()
            if ((header and 0x20) != 0) {
                // continuation bit
                bits = bits or (`in`.readVLong() shl 5)
            }

            var l: Long = BitUtil.zigZagDecode(bits)

            when (header and DAY_ENCODING) {
                SECOND_ENCODING -> l *= SECOND
                HOUR_ENCODING -> l *= HOUR
                DAY_ENCODING -> l *= DAY
                0 -> {}
                else -> throw AssertionError()
            }

            return l
        }
    }
}
