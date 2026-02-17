package org.gnit.lucenekmp.codecs.lucene90.compressing

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.FLAGS_BITS
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.META_VERSION_START
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.OFFSETS
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.PACKED_BLOCK_SIZE
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.PAYLOADS
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.POSITIONS
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.VECTORS_EXTENSION
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.VECTORS_INDEX_CODEC_NAME
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.VECTORS_INDEX_EXTENSION
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.VECTORS_META_EXTENSION
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.VERSION_CURRENT
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsWriter.Companion.VERSION_START
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.SlowImpactsEnum
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.LongValues
import org.gnit.lucenekmp.util.LongsRef
import org.gnit.lucenekmp.util.packed.BlockPackedReaderIterator
import org.gnit.lucenekmp.util.packed.DirectReader
import org.gnit.lucenekmp.util.packed.DirectWriter
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedInts.ReaderIterator
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import kotlin.experimental.and

/**
 * [TermVectorsReader] for [Lucene90CompressingTermVectorsFormat].
 *
 * @lucene.experimental
 */
class Lucene90CompressingTermVectorsReader : TermVectorsReader {
    private val fieldInfos: FieldInfos?
    val indexReader: FieldsIndex
    val vectorsStream: IndexInput
    val version: Int
    val packedIntsVersion: Int
    val compressionMode: CompressionMode
    private val decompressor: Decompressor
    val chunkSize: Int
    val numDocs: Int
    private var closed = false
    private val reader: BlockPackedReaderIterator
    private val numChunks: Long // number of written blocks
    private val numDirtyChunks: Long // number of incomplete compressed blocks written
    private val numDirtyDocs: Long // cumulative number of docs in incomplete chunks
    val maxPointer: Long // end of the data section
    private var blockState = BlockState(-1, -1, 0)

    // Cache of recently prefetched block IDs. This helps reduce chances of prefetching the same block
    // multiple times, which is otherwise likely due to index sorting or recursive graph bisection
    // clustering similar documents together. NOTE: this cache must be small since it's fully scanned.
    private val prefetchedBlockIDCache: LongArray
    private var prefetchedBlockIDCacheIndex = 0

    // used by clone
    private constructor(reader: Lucene90CompressingTermVectorsReader) {
        this.fieldInfos = reader.fieldInfos
        this.vectorsStream = reader.vectorsStream.clone()
        this.indexReader = reader.indexReader.clone()
        this.packedIntsVersion = reader.packedIntsVersion
        this.compressionMode = reader.compressionMode
        this.decompressor = reader.decompressor.clone()
        this.chunkSize = reader.chunkSize
        this.numDocs = reader.numDocs
        this.reader =
            BlockPackedReaderIterator(vectorsStream, packedIntsVersion, PACKED_BLOCK_SIZE, 0)
        this.version = reader.version
        this.numChunks = reader.numChunks
        this.numDirtyChunks = reader.numDirtyChunks
        this.numDirtyDocs = reader.numDirtyDocs
        this.maxPointer = reader.maxPointer
        this.prefetchedBlockIDCache = LongArray(PREFETCH_CACHE_SIZE)
        Arrays.fill(prefetchedBlockIDCache, -1)
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

        var metaIn: ChecksumIndexInput? = null
        try {
            // Open the data file
            val vectorsStreamFN: String =
                IndexFileNames.segmentFileName(segment, segmentSuffix, VECTORS_EXTENSION)
            vectorsStream = d.openInput(vectorsStreamFN, context.withReadAdvice(ReadAdvice.RANDOM))
            version =
                CodecUtil.checkIndexHeader(
                    vectorsStream, formatName, VERSION_START, VERSION_CURRENT, si.getId(), segmentSuffix
                )
            require(
                CodecUtil.indexHeaderLength(formatName, segmentSuffix).toLong()
                        == vectorsStream.filePointer
            )

            val metaStreamFN: String =
                IndexFileNames.segmentFileName(segment, segmentSuffix, VECTORS_META_EXTENSION)
            metaIn = d.openChecksumInput(metaStreamFN)
            CodecUtil.checkIndexHeader(
                metaIn,
                VECTORS_INDEX_CODEC_NAME + "Meta",
                META_VERSION_START,
                version,
                si.getId(),
                segmentSuffix
            )

            packedIntsVersion = metaIn.readVInt()
            chunkSize = metaIn.readVInt()

            // NOTE: data file is too costly to verify checksum against all the bytes on open,
            // but for now we at least verify proper structure of the checksum footer: which looks
            // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
            // such as file truncation.
            CodecUtil.retrieveChecksum(vectorsStream)

            val fieldsIndexReader =
                FieldsIndexReader(
                    d,
                    si.name,
                    segmentSuffix,
                    VECTORS_INDEX_EXTENSION,
                    VECTORS_INDEX_CODEC_NAME,
                    si.getId(),
                    metaIn,
                    context
                )

            this.indexReader = fieldsIndexReader
            this.maxPointer = fieldsIndexReader.maxPointer

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

            decompressor = compressionMode.newDecompressor()
            this.reader =
                BlockPackedReaderIterator(vectorsStream, packedIntsVersion, PACKED_BLOCK_SIZE, 0)

            CodecUtil.checkFooter(metaIn, null)
            metaIn.close()

            this.prefetchedBlockIDCache = LongArray(PREFETCH_CACHE_SIZE)
            Arrays.fill(prefetchedBlockIDCache, -1)

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
                IOUtils.closeWhileHandlingException(this, metaIn)
            }
        }
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

    /**
     * @throws AlreadyClosedException if this TermVectorsReader is closed
     */
    @Throws(AlreadyClosedException::class)
    private fun ensureOpen() {
        if (closed) {
            throw AlreadyClosedException("this FieldsReader is closed")
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (!closed) {
            IOUtils.close(indexReader, vectorsStream)
            closed = true
        }
    }

    override fun clone(): TermVectorsReader {
        return Lucene90CompressingTermVectorsReader(this)
    }

    override val mergeInstance: TermVectorsReader
        get() = Lucene90CompressingTermVectorsReader(this)

    /** Checks if a given docID was loaded in the current block state.  */
    fun isLoaded(docID: Int): Boolean {
        return blockState.docBase <= docID && docID < blockState.docBase + blockState.chunkDocs
    }

    private data class BlockState(val startPointer: Long, val docBase: Int, val chunkDocs: Int)

    @Throws(IOException::class)
    override fun prefetch(docID: Int) {
        val blockID = indexReader.getBlockID(docID)

        for (prefetchedBlockID in prefetchedBlockIDCache) {
            if (prefetchedBlockID == blockID) {
                return
            }
        }

        val blockStartPointer = indexReader.getBlockStartPointer(blockID)
        val blockLength = indexReader.getBlockLength(blockID)
        vectorsStream.prefetch(blockStartPointer, blockLength)

        prefetchedBlockIDCache[prefetchedBlockIDCacheIndex++ and PREFETCH_CACHE_MASK] = blockID
    }

    @Throws(IOException::class)
    override fun get(doc: Int): Fields? {
        ensureOpen()

        // seek to the right place
        val startPointer: Long
        if (isLoaded(doc)) {
            startPointer = blockState.startPointer // avoid searching the start pointer
        } else {
            startPointer = indexReader.getStartPointer(doc)
        }
        vectorsStream.seek(startPointer)

        // decode
        // - docBase: first doc ID of the chunk
        // - chunkDocs: number of docs of the chunk
        val docBase: Int = vectorsStream.readVInt()
        val chunkDocs: Int = vectorsStream.readVInt() ushr 1
        if (doc < docBase || doc >= docBase + chunkDocs || docBase + chunkDocs > numDocs) {
            throw CorruptIndexException(
                "docBase=" + docBase + ",chunkDocs=" + chunkDocs + ",doc=" + doc, vectorsStream
            )
        }
        this.blockState = BlockState(startPointer, docBase, chunkDocs)

        val skip: Long // number of fields to skip
        val numFields: Long // number of fields of the document we're looking for
        val totalFields: Long // total number of fields of the chunk (sum for all docs)
        if (chunkDocs == 1) {
            skip = 0
            totalFields = vectorsStream.readVInt().toLong()
            numFields = totalFields
        } else {
            reader.reset(vectorsStream, chunkDocs.toLong())
            var sum = 0L
            for (i in docBase..<doc) {
                sum += reader.next()
            }
            skip = sum
            numFields = reader.next()
            sum += numFields
            for (i in doc + 1..<docBase + chunkDocs) {
                sum += reader.next()
            }
            totalFields = sum
        }

        if (numFields == 0L) {
            // no vectors
            return null
        }

        // read field numbers that have term vectors
        val fieldNums: IntArray
        run {
            val token: Int = vectorsStream.readByte().toInt() and 0xFF
            require(
                token != 0 // means no term vectors, cannot happen since we checked for numFields == 0
            )
            val bitsPerFieldNum = token and 0x1F
            var totalDistinctFields = token ushr 5
            if (totalDistinctFields == 0x07) {
                totalDistinctFields += vectorsStream.readVInt()
            }
            ++totalDistinctFields
            val it: ReaderIterator =
                PackedInts.getReaderIteratorNoHeader(
                    vectorsStream,
                    PackedInts.Format.PACKED,
                    packedIntsVersion,
                    totalDistinctFields,
                    bitsPerFieldNum,
                    1
                )
            fieldNums = IntArray(totalDistinctFields)
            for (i in 0..<totalDistinctFields) {
                fieldNums[i] = it.next().toInt()
            }
        }

        // read field numbers and flags
        val fieldNumOffs = LongArray(numFields.toInt())
        val flags: LongValues
        run {
            val bitsPerOff: Int = DirectWriter.bitsRequired((fieldNums.size - 1).toLong())
            val allFieldNumOffs: LongValues = DirectReader.getInstance(slice(vectorsStream), bitsPerOff)
            when (vectorsStream.readVInt()) {
                0 -> {
                    val fieldFlags: LongValues = DirectReader.getInstance(slice(vectorsStream), FLAGS_BITS)
                    val out = ByteBuffersDataOutput()
                    val writer: DirectWriter = DirectWriter.getInstance(out, totalFields, FLAGS_BITS)
                    var i = 0L
                    while (i < totalFields) {
                        val fieldNumOff = allFieldNumOffs.get(i)
                        require(fieldNumOff >= 0 && fieldNumOff < fieldNums.size)
                        writer.add(fieldFlags.get(fieldNumOff))
                        ++i
                    }
                    writer.finish()
                    flags = DirectReader.getInstance(out.toDataInput(), FLAGS_BITS)
                }

                1 -> flags = DirectReader.getInstance(slice(vectorsStream), FLAGS_BITS)
                else -> throw AssertionError()
            }
            for (i in 0..<numFields) {
                fieldNumOffs[i.toInt()] = allFieldNumOffs.get(skip + i)
            }
        }

        // number of terms per field for all fields
        val numTerms: LongValues
        val totalTerms: Long
        run {
            val bitsRequired: Int = vectorsStream.readVInt()
            numTerms = DirectReader.getInstance(slice(vectorsStream), bitsRequired)
            var sum = 0L
            for (i in 0..<totalFields) {
                sum += numTerms.get(i)
            }
            totalTerms = sum
        }

        // term lengths
        var docOff = 0L
        var docLen = 0L
        var totalLen: Long
        val fieldLengths = IntArray(numFields.toInt())
        val prefixLengths: Array<IntArray?> = kotlin.arrayOfNulls(numFields.toInt())
        val suffixLengths: Array<IntArray?> = kotlin.arrayOfNulls(numFields.toInt())
        run {
            reader.reset(vectorsStream, totalTerms)
            // skip
            var toSkip = 0L
            for (i in 0..<skip) {
                toSkip += numTerms.get(i)
            }
            reader.skip(toSkip)
            // read prefix lengths
            for (i in 0..<numFields) {
                val termCount = numTerms.get(skip + i).toInt()
                val fieldPrefixLengths = IntArray(termCount)
                prefixLengths[i.toInt()] = fieldPrefixLengths
                var j = 0
                while (j < termCount) {
                    val next: LongsRef = reader.next(termCount - j)
                    for (k in 0..<next.length) {
                        fieldPrefixLengths[j++] = next.longs[next.offset + k].toInt()
                    }
                }
            }
            reader.skip(totalTerms - reader.ord())

            reader.reset(vectorsStream, totalTerms)
            // skip
            toSkip = 0
            for (i in 0..<skip) {
                for (j in 0..<numTerms.get(i)) {
                    docOff += reader.next()
                }
            }
            for (i in 0..<numFields) {
                val termCount = numTerms.get(skip + i).toInt()
                val fieldSuffixLengths = IntArray(termCount)
                suffixLengths[i.toInt()] = fieldSuffixLengths
                var j = 0
                while (j < termCount) {
                    val next: LongsRef = reader.next(termCount - j)
                    for (k in 0..<next.length) {
                        fieldSuffixLengths[j++] = next.longs[next.offset + k].toInt()
                    }
                }
                fieldLengths[i.toInt()] = sum(suffixLengths[i.toInt()]!!)
                docLen += fieldLengths[i.toInt()]
            }
            totalLen = docOff + docLen
            for (i in skip + numFields..<totalFields) {
                for (j in 0..<numTerms.get(i)) {
                    totalLen += reader.next()
                }
            }
        }

        // term freqs
        val termFreqs = IntArray(totalTerms.toInt())
        run {
            reader.reset(vectorsStream, totalTerms)
            var i = 0
            while (i < totalTerms) {
                val next: LongsRef = reader.next((totalTerms - i).toInt())
                for (k in 0..<next.length) {
                    termFreqs[i++] = 1 + next.longs[next.offset + k].toInt()
                }
            }
        }

        // total number of positions, offsets and payloads
        var totalPositions = 0
        var totalOffsets = 0
        var totalPayloads = 0L
        run {
            var i = 0L
            var termIndex = 0L
            while (i < totalFields) {
                val f = flags.get(i).toInt()
                val termCount = numTerms.get(i).toInt()
                for (j in 0..<termCount) {
                    val freq = termFreqs[(termIndex++).toInt()]
                    if ((f and POSITIONS) != 0) {
                        totalPositions += freq
                    }
                    if ((f and OFFSETS) != 0) {
                        totalOffsets += freq
                    }
                    if ((f and PAYLOADS) != 0) {
                        totalPayloads += freq
                    }
                }
                require(i != totalFields - 1 || termIndex == totalTerms) { "$termIndex $totalTerms" }
                ++i
            }
        }

        val positionIndex = positionIndex(skip.toInt(), numFields.toInt(), numTerms, termFreqs)
        val positions: Array<IntArray?>
        val startOffsets: Array<IntArray?>
        val lengths: Array<IntArray?>
        if (totalPositions > 0) {
            positions =
                readPositions(
                    skip.toInt(),
                    numFields.toInt(),
                    flags,
                    numTerms,
                    termFreqs,
                    POSITIONS,
                    totalPositions,
                    positionIndex
                )
        } else {
            positions = kotlin.arrayOfNulls<IntArray>(numFields.toInt())
        }

        if (totalOffsets > 0) {
            // average number of chars per term
            val charsPerTerm = FloatArray(fieldNums.size)
            for (i in charsPerTerm.indices) {
                charsPerTerm[i] = Float.intBitsToFloat(vectorsStream.readInt())
            }
            startOffsets =
                readPositions(
                    skip.toInt(), numFields.toInt(), flags, numTerms, termFreqs, OFFSETS, totalOffsets, positionIndex
                )
            lengths =
                readPositions(
                    skip.toInt(), numFields.toInt(), flags, numTerms, termFreqs, OFFSETS, totalOffsets, positionIndex
                )

            for (i in 0..<numFields.toInt()) {
                val fStartOffsets: IntArray? = startOffsets[i]
                val fPositions: IntArray? = positions[i]
                // patch offsets from positions
                if (fStartOffsets != null && fPositions != null) {
                    val fieldCharsPerTerm = charsPerTerm[fieldNumOffs[i].toInt()]
                    for (j in fStartOffsets.indices) {
                        fStartOffsets[j] += (fieldCharsPerTerm * fPositions[j]).toInt()
                    }
                }
                if (fStartOffsets != null) {
                    val fPrefixLengths = prefixLengths[i]
                    val fSuffixLengths = suffixLengths[i]
                    val fLengths = lengths[i]
                    if (fLengths == null) {
                        continue
                    }
                    var j = 0
                    val end = numTerms.get(skip + i).toInt()
                    while (j < end) {
                        // delta-decode start offsets and  patch lengths using term lengths
                        val termLength = fPrefixLengths!![j] + fSuffixLengths!![j]
                        fLengths[positionIndex[i][j]] += termLength
                        for (k in positionIndex[i][j] + 1..<positionIndex[i][j + 1]) {
                            fStartOffsets[k] += fStartOffsets[k - 1]
                            fLengths[k] += termLength
                        }
                        ++j
                    }
                }
            }
        } else {
            lengths = kotlin.arrayOfNulls<IntArray>(numFields.toInt())
            startOffsets = lengths
        }
        if (totalPositions > 0) {
            // delta-decode positions
            for (i in 0..<numFields.toInt()) {
                val fPositions: IntArray? = positions[i]
                val fpositionIndex = positionIndex[i]
                if (fPositions != null) {
                    var j = 0
                    val end = numTerms.get(skip + i).toInt()
                    while (j < end) {
                        // delta-decode start offsets
                        for (k in fpositionIndex[j] + 1..<fpositionIndex[j + 1]) {
                            fPositions[k] += fPositions[k - 1]
                        }
                        ++j
                    }
                }
            }
        }

        // payload lengths
        val payloadIndex: Array<IntArray?> = kotlin.arrayOfNulls<IntArray>(numFields.toInt())
        var totalPayloadLength = 0L
        var payloadOff = 0L
        var payloadLen = 0L
        if (totalPayloads > 0) {
            reader.reset(vectorsStream, totalPayloads)
            // skip
            var termIndex = 0
            for (i in 0..<skip) {
                val f = flags.get(i).toInt()
                val termCount = numTerms.get(i).toInt()
                if ((f and PAYLOADS) != 0) {
                    for (j in 0..<termCount) {
                        val freq = termFreqs[termIndex + j]
                        for (k in 0..<freq) {
                            val l = reader.next().toInt()
                            payloadOff += l
                        }
                    }
                }
                termIndex += termCount
            }
            totalPayloadLength = payloadOff
            // read doc payload lengths
            for (i in 0..<numFields.toInt()) {
                val f = flags.get(skip + i).toInt()
                val termCount = numTerms.get(skip + i).toInt()
                if ((f and PAYLOADS) != 0) {
                    val totalFreq = positionIndex[i][termCount]
                    payloadIndex[i] = IntArray(totalFreq + 1)
                    var posIdx = 0
                    payloadIndex[i]!![posIdx] = payloadLen.toInt()
                    for (j in 0..<termCount) {
                        val freq = termFreqs[termIndex + j]
                        for (k in 0..<freq) {
                            val payloadLength = reader.next().toInt()
                            payloadLen += payloadLength
                            payloadIndex[i]!![posIdx + 1] = payloadLen.toInt()
                            ++posIdx
                        }
                    }
                    require(posIdx == totalFreq)
                }
                termIndex += termCount
            }
            totalPayloadLength += payloadLen
            for (i in skip + numFields..<totalFields) {
                val f = flags.get(i).toInt()
                val termCount = numTerms.get(i).toInt()
                if ((f and PAYLOADS) != 0) {
                    for (j in 0..<termCount) {
                        val freq = termFreqs[termIndex + j]
                        for (k in 0..<freq) {
                            totalPayloadLength += reader.next()
                        }
                    }
                }
                termIndex += termCount
            }
            require(termIndex.toLong() == totalTerms) { "$termIndex $totalTerms" }
        }

        // decompress data
        val suffixBytes = BytesRef()
        decompressor.decompress(
            vectorsStream,
            (totalLen + totalPayloadLength).toInt(),
            (docOff + payloadOff).toInt(),
            (docLen + payloadLen).toInt(),
            suffixBytes
        )
        suffixBytes.length = docLen.toInt()
        val payloadBytes =
            BytesRef(suffixBytes.bytes, (suffixBytes.offset + docLen).toInt(), payloadLen.toInt())

        val fieldFlags = IntArray(numFields.toInt())
        for (i in 0..<numFields.toInt()) {
            fieldFlags[i] = flags.get(skip + i).toInt()
        }

        val fieldNumTerms = IntArray(numFields.toInt())
        for (i in 0..<numFields.toInt()) {
            fieldNumTerms[i] = numTerms.get(skip + i).toInt()
        }

        val fieldTermFreqs: Array<IntArray?> = kotlin.arrayOfNulls(numFields.toInt())
        run {
            var termIdx = 0L
            for (i in 0..<skip) {
                termIdx += numTerms.get(i)
            }
            for (i in 0..<numFields.toInt()) {
                val termCount = numTerms.get(skip + i)
                fieldTermFreqs[i] = IntArray(termCount.toInt())
                for (j in 0..<termCount.toInt()) {
                    fieldTermFreqs[i]!![j] = termFreqs[(termIdx++).toInt()]
                }
            }
        }

        require(sum(fieldLengths).toLong() == docLen) { sum(fieldLengths).toString() + " != " + docLen }

        return this.TVFields(
            fieldNums,
            fieldFlags,
            fieldNumOffs,
            fieldNumTerms,
            fieldLengths,
            prefixLengths as Array<IntArray>,
            suffixLengths as Array<IntArray>,
            fieldTermFreqs as Array<IntArray>,
            positionIndex,
            positions as Array<IntArray?>,
            startOffsets,
            lengths as Array<IntArray?>,
            payloadBytes,
            payloadIndex as Array<IntArray?>,
            suffixBytes
        )
    }

    // field -> term index -> position index
    private fun positionIndex(skip: Int, numFields: Int, numTerms: LongValues, termFreqs: IntArray): Array<IntArray> {
        val positionIndex: Array<IntArray?> = kotlin.arrayOfNulls<IntArray>(numFields)
        var termIndex = 0
        for (i in 0..<skip.toLong()) {
            val termCount = numTerms.get(i).toInt()
            termIndex += termCount
        }
        for (i in 0..<numFields) {
            val termCount = numTerms.get(skip + i.toLong()).toInt()
            positionIndex[i] = IntArray(termCount + 1)
            for (j in 0..<termCount) {
                val freq = termFreqs[termIndex + j]
                positionIndex[i]!![j + 1] = positionIndex[i]!![j] + freq
            }
            termIndex += termCount
        }
        return positionIndex as Array<IntArray>
    }

    @Throws(IOException::class)
    private fun readPositions(
        skip: Int,
        numFields: Int,
        flags: LongValues,
        numTerms: LongValues,
        termFreqs: IntArray,
        flag: Int,
        totalPositions: Int,
        positionIndex: Array<IntArray>
    ): Array<IntArray?> {
        val positions: Array<IntArray?> = kotlin.arrayOfNulls<IntArray>(numFields)
        reader.reset(vectorsStream, totalPositions.toLong())
        // skip
        var toSkip = 0L
        var termIndex = 0
        for (i in 0..<skip.toLong()) {
            val f = flags.get(i).toInt()
            val termCount = numTerms.get(i).toInt()
            if ((f and flag) != 0) {
                for (j in 0..<termCount) {
                    val freq = termFreqs[termIndex + j]
                    toSkip += freq
                }
            }
            termIndex += termCount
        }
        reader.skip(toSkip)
        // read doc positions
        for (i in 0..<numFields) {
            val f = flags.get(skip + i.toLong()).toInt()
            val termCount = numTerms.get(skip + i.toLong()).toInt()
            if ((f and flag) != 0) {
                val totalFreq = positionIndex[i][termCount]
                val fieldPositions = IntArray(totalFreq)
                positions[i] = fieldPositions
                var j = 0
                while (j < totalFreq) {
                    val nextPositions: LongsRef = reader.next(totalFreq - j)
                    for (k in 0..<nextPositions.length) {
                        fieldPositions[j++] = nextPositions.longs[nextPositions.offset + k].toInt()
                    }
                }
            }
            termIndex += termCount
        }
        reader.skip(totalPositions - reader.ord())
        return positions
    }

    private inner class TVFields(
        private val fieldNums: IntArray,
        private val fieldFlags: IntArray,
        private val fieldNumOffs: LongArray,
        private val numTerms: IntArray,
        private val fieldLengths: IntArray,
        private val prefixLengths: Array<IntArray>,
        private val suffixLengths: Array<IntArray>,
        private val termFreqs: Array<IntArray>,
        private val positionIndex: Array<IntArray>,
        private val positions: Array<IntArray?>,
        private val startOffsets: Array<IntArray?>,
        private val lengths: Array<IntArray?>,
        payloadBytes: BytesRef,
        payloadIndex: Array<IntArray?>,
        suffixBytes: BytesRef
    ) : Fields() {
        private val payloadIndex: Array<IntArray?>
        private val suffixBytes: BytesRef
        private val payloadBytes: BytesRef

        init {
            this.payloadBytes = payloadBytes
            this.payloadIndex = payloadIndex
            this.suffixBytes = suffixBytes
        }

        override fun iterator(): MutableIterator<String> {
            return object : MutableIterator<String> {
                var i: Int = 0

                override fun hasNext(): Boolean {
                    return i < fieldNumOffs.size
                }

                override fun next(): String {
                    if (!hasNext()) {
                        throw NoSuchElementException()
                    }
                    val fieldNum = fieldNums[fieldNumOffs[i++].toInt()]
                    return fieldInfos!!.fieldInfo(fieldNum)!!.name
                }

                override fun remove() {
                    throw UnsupportedOperationException()
                }
            }
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            val fieldInfo: FieldInfo? = fieldInfos!!.fieldInfo(field!!)
            if (fieldInfo == null) {
                return null
            }
            var idx = -1
            for (i in fieldNumOffs.indices) {
                if (fieldNums[fieldNumOffs[i].toInt()] == fieldInfo.number) {
                    idx = i
                    break
                }
            }

            if (idx == -1 || numTerms[idx] == 0) {
                // no term
                return null
            }
            var fieldOff = 0
            var fieldLen = -1
            for (i in fieldNumOffs.indices) {
                if (i < idx) {
                    fieldOff += fieldLengths[i]
                } else {
                    fieldLen = fieldLengths[i]
                    break
                }
            }
            require(fieldLen >= 0)
            return TVTerms(
                numTerms[idx],
                fieldFlags[idx],
                prefixLengths[idx],
                suffixLengths[idx],
                termFreqs[idx],
                positionIndex[idx],
                positions[idx],
                startOffsets[idx],
                lengths[idx],
                payloadIndex[idx],
                payloadBytes,
                BytesRef(suffixBytes.bytes, suffixBytes.offset + fieldOff, fieldLen)
            )
        }

        override fun size(): Int {
            return fieldNumOffs.size
        }
    }

    private class TVTerms(
        private val numTerms: Int,
        private val flags: Int,
        private val prefixLengths: IntArray,
        private val suffixLengths: IntArray,
        private val termFreqs: IntArray,
        private val positionIndex: IntArray,
        private val positions: IntArray?,
        private val startOffsets: IntArray?,
        private val lengths: IntArray?,
        private val payloadIndex: IntArray?,
        payloadBytes: BytesRef,
        termBytes: BytesRef
    ) : Terms() {
        override val sumTotalTermFreq: Long
        private val termBytes: BytesRef
        private val payloadBytes: BytesRef

        init {
            this.payloadBytes = payloadBytes
            this.termBytes = termBytes
            var ttf: Long = 0
            for (tf in termFreqs) {
                ttf += tf.toLong()
            }
            this.sumTotalTermFreq = ttf
        }

        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            val termsEnum = TVTermsEnum()
            termsEnum.reset(
                numTerms,
                flags,
                prefixLengths,
                suffixLengths,
                termFreqs,
                positionIndex,
                positions,
                startOffsets,
                lengths,
            payloadIndex,
                payloadBytes,
                ByteArrayDataInput(termBytes.bytes, termBytes.offset, termBytes.length)
            )
            return termsEnum
        }

        @Throws(IOException::class)
        override fun size(): Long {
            return numTerms.toLong()
        }

        override val sumDocFreq: Long = numTerms.toLong()

        override val docCount: Int = 1

        override fun hasFreqs(): Boolean {
            return true
        }

        override fun hasOffsets(): Boolean {
            return (flags and OFFSETS) != 0
        }

        override fun hasPositions(): Boolean {
            return (flags and POSITIONS) != 0
        }

        override fun hasPayloads(): Boolean {
            return (flags and PAYLOADS) != 0
        }
    }

    private class TVTermsEnum : BaseTermsEnum() {
        private var numTerms = 0
        private var startPos = 0
        private var ord = 0
        private lateinit var prefixLengths: IntArray
        private lateinit var suffixLengths: IntArray
        private lateinit var termFreqs: IntArray
        private lateinit var positionIndex: IntArray
        private var positions: IntArray? = null
        private var startOffsets: IntArray? = null
        private var lengths: IntArray? = null
        private var payloadIndex: IntArray? = null
        private var `in`: ByteArrayDataInput? = null
        private var payloads: BytesRef? = null
        private val term: BytesRef = BytesRef(16)

        fun reset(
            numTerms: Int,
            flags: Int,
            prefixLengths: IntArray,
            suffixLengths: IntArray,
            termFreqs: IntArray,
            positionIndex: IntArray,
            positions: IntArray?,
            startOffsets: IntArray?,
            lengths: IntArray?,
            payloadIndex: IntArray?,
            payloads: BytesRef,
            `in`: ByteArrayDataInput
        ) {
            this.numTerms = numTerms
            this.prefixLengths = prefixLengths
            this.suffixLengths = suffixLengths
            this.termFreqs = termFreqs
            this.positionIndex = positionIndex
            this.positions = positions
            this.startOffsets = startOffsets
            this.lengths = lengths
            this.payloadIndex = payloadIndex
            this.payloads = payloads
            this.`in` = `in`
            startPos = `in`.position
            reset()
        }

        fun reset() {
            term.length = 0
            `in`!!.position = (startPos)
            ord = -1
        }

        @Throws(IOException::class)
        override fun next(): BytesRef? {
            if (ord == numTerms - 1) {
                return null
            } else {
                require(ord < numTerms)
                ++ord
            }

            // read term
            term.offset = 0
            term.length = prefixLengths[ord] + suffixLengths[ord]
            if (term.length > term.bytes.size) {
                term.bytes = ArrayUtil.grow(term.bytes, term.length)
            }
            `in`!!.readBytes(term.bytes, prefixLengths[ord], suffixLengths[ord])

            return term
        }

        @Throws(IOException::class)
        override fun seekCeil(text: BytesRef): SeekStatus {
            if (ord < numTerms && ord >= 0) {
                val cmp = term().compareTo(text)
                if (cmp == 0) {
                    return SeekStatus.FOUND
                } else if (cmp > 0) {
                    reset()
                }
            }
            // linear scan
            while (true) {
                val term: BytesRef? = next()
                if (term == null) {
                    return SeekStatus.END
                }
                val cmp = term.compareTo(text)
                if (cmp > 0) {
                    return SeekStatus.NOT_FOUND
                } else if (cmp == 0) {
                    return SeekStatus.FOUND
                }
            }
        }

        @Throws(IOException::class)
        override fun seekExact(ord: Long) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun term(): BytesRef {
            return term
        }

        @Throws(IOException::class)
        override fun ord(): Long {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun docFreq(): Int {
            return 1
        }

        @Throws(IOException::class)
        override fun totalTermFreq(): Long {
            return termFreqs[ord].toLong()
        }

        @Throws(IOException::class)
        override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
            val docsEnum: TVPostingsEnum = if (reuse != null && reuse is TVPostingsEnum) {
                reuse
            } else {
                TVPostingsEnum()
            }

            docsEnum.reset(
                termFreqs[ord],
                positionIndex[ord],
                positions,
                startOffsets,
                lengths,
                payloads!!,
                payloadIndex
            )
            return docsEnum
        }

        @Throws(IOException::class)
        override fun impacts(flags: Int): ImpactsEnum {
            val delegate: PostingsEnum = postings(null, PostingsEnum.FREQS.toInt())
            return SlowImpactsEnum(delegate)
        }
    }

    private class TVPostingsEnum : PostingsEnum() {
        private var doc = -1
        private var termFreq = 0
        private var positionIndex = 0
        private var positions: IntArray? = null
        private var startOffsets: IntArray? = null
        private var lengths: IntArray? = null

        private val payloadRef: BytesRef = BytesRef()

        override val payload: BytesRef?
            get() {
                checkPosition()
                return if (payloadRef.length == 0) {
                    null
                } else {
                    payloadRef
                }
            }

        private var payloadIndex: IntArray? = null
        private var basePayloadOffset = 0
        private var i = 0

        fun reset(
            freq: Int,
            positionIndex: Int,
            positions: IntArray?,
            startOffsets: IntArray?,
            lengths: IntArray?,
            payloads: BytesRef,
            payloadIndex: IntArray?
        ) {
            this.termFreq = freq
            this.positionIndex = positionIndex
            this.positions = positions
            this.startOffsets = startOffsets
            this.lengths = lengths
            this.basePayloadOffset = payloads.offset
            this.payloadRef.bytes = payloads.bytes
            payloadRef.length = 0
            payloadRef.offset = 0
            this.payloadIndex = payloadIndex

            i = -1
            doc = i
        }

        fun checkDoc() {
            check(doc != NO_MORE_DOCS) { "DocsEnum exhausted" }
            check(doc != -1) { "DocsEnum not started" }
        }

        fun checkPosition() {
            checkDoc()
            check(i >= 0) { "Position enum not started" }
            check(i < termFreq) { "Read past last position" }
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            check(doc == 0)
            check(i < termFreq - 1) { "Read past last position" }

            ++i

            if (payloadIndex != null) {
                payloadRef.offset = basePayloadOffset + payloadIndex!![positionIndex + i]
                payloadRef.length = payloadIndex!![positionIndex + i + 1] - payloadIndex!![positionIndex + i]
            } else {
                payloadRef.length = 0
            }

            val positionsLocal = positions
            if (positionsLocal == null) {
                return -1
            } else {
                return positionsLocal[positionIndex + i]
            }
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            checkPosition()
            val startOffsetsLocal = startOffsets
            if (startOffsetsLocal == null) {
                return -1
            } else {
                return startOffsetsLocal[positionIndex + i]
            }
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            checkPosition()
            val startOffsetsLocal = startOffsets
            val lengthsLocal = lengths
            if (startOffsetsLocal == null || lengthsLocal == null) {
                return -1
            } else {
                return startOffsetsLocal[positionIndex + i] + lengthsLocal[positionIndex + i]
            }
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            checkDoc()
            return termFreq
        }

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            if (doc == -1) {
                return (0.also { doc = it })
            } else {
                return (NO_MORE_DOCS.also { doc = it })
            }
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return slowAdvance(target)
        }

        override fun cost(): Long {
            return 1
        }
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        indexReader.checkIntegrity()
        CodecUtil.checksumEntireFile(vectorsStream)
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
        private val PREFETCH_CACHE_SIZE = 1 shl 4
        private val PREFETCH_CACHE_MASK = PREFETCH_CACHE_SIZE - 1

        @Throws(IOException::class)
        private fun slice(`in`: IndexInput): RandomAccessInput {
            val length: Int = `in`.readVInt()
            val bytes = ByteArray(length)
            `in`.readBytes(bytes, 0, length)
            return ByteBuffersDataInput(mutableListOf(ByteBuffer.wrap(bytes)))
        }

        private fun sum(arr: IntArray): Int {
            var sum = 0
            for (el in arr) {
                sum += el
            }
            return sum
        }
    }
}
