package org.gnit.lucenekmp.codecs.lucene90.compressing


import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.StoredFieldsWriter
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.packed.DirectMonotonicWriter
import okio.IOException

/**
 * A [StoredFieldsFormat] that compresses documents in chunks in order to improve the
 * compression ratio.
 *
 *
 * For a chunk size of <var>chunkSize</var> bytes, this [StoredFieldsFormat] does not
 * support documents larger than (`2<sup>31</sup> - chunkSize`) bytes.
 *
 *
 * For optimal performance, you should use a [MergePolicy] that returns segments that have
 * the biggest byte size first.
 *
 * @lucene.experimental
 */
class Lucene90CompressingStoredFieldsFormat(
    private val formatName: String,
    private val segmentSuffix: String,
    private val compressionMode: CompressionMode,
    chunkSize: Int,
    maxDocsPerChunk: Int,
    blockShift: Int
) : StoredFieldsFormat() {
    private val chunkSize: Int
    private val maxDocsPerChunk: Int
    private val blockShift: Int

    /**
     * Create a new [Lucene90CompressingStoredFieldsFormat] with an empty segment suffix.
     *
     * @see Lucene90CompressingStoredFieldsFormat.Lucene90CompressingStoredFieldsFormat
     */
    constructor(
        formatName: String,
        compressionMode: CompressionMode,
        chunkSize: Int,
        maxDocsPerChunk: Int,
        blockShift: Int
    ) : this(formatName, "", compressionMode, chunkSize, maxDocsPerChunk, blockShift)

    /**
     * Create a new [Lucene90CompressingStoredFieldsFormat].
     *
     *
     * `formatName` is the name of the format. This name will be used in the file
     * formats to perform [codec header checks][CodecUtil.checkIndexHeader].
     *
     *
     * `segmentSuffix` is the segment suffix. This suffix is added to the result file
     * name only if it's not the empty string.
     *
     *
     * The `compressionMode` parameter allows you to choose between compression
     * algorithms that have various compression and decompression speeds so that you can pick the one
     * that best fits your indexing and searching throughput. You should never instantiate two [ ]s that have the same name but different [ ]s.
     *
     *
     * `chunkSize` is the minimum byte size of a chunk of documents. A value of `1
    ` *  can make sense if there is redundancy across fields. `maxDocsPerChunk` is an
     * upperbound on how many docs may be stored in a single chunk. This is to bound the cpu costs for
     * highly compressible data.
     *
     *
     * Higher values of `chunkSize` should improve the compression ratio but will
     * require more memory at indexing time and might make document loading a little slower (depending
     * on the size of your OS cache compared to the size of your index).
     *
     * @param formatName the name of the [StoredFieldsFormat]
     * @param compressionMode the [CompressionMode] to use
     * @param chunkSize the minimum number of bytes of a single chunk of stored documents
     * @param maxDocsPerChunk the maximum number of documents in a single chunk
     * @param blockShift the log in base 2 of number of chunks to store in an index block
     * @see CompressionMode
     */
    init {
        require(chunkSize >= 1) { "chunkSize must be >= 1" }
        this.chunkSize = chunkSize
        require(maxDocsPerChunk >= 1) { "maxDocsPerChunk must be >= 1" }
        this.maxDocsPerChunk = maxDocsPerChunk
        require(
            !(blockShift < DirectMonotonicWriter.MIN_BLOCK_SHIFT
                    || blockShift > DirectMonotonicWriter.MAX_BLOCK_SHIFT)
        ) {
            ("blockSize must be in "
                    + DirectMonotonicWriter.MIN_BLOCK_SHIFT
                    + "-"
                    + DirectMonotonicWriter.MAX_BLOCK_SHIFT
                    + ", got "
                    + blockShift)
        }
        this.blockShift = blockShift
    }

    @Throws(IOException::class)
    override fun fieldsReader(
        directory: Directory, si: SegmentInfo, fn: FieldInfos, context: IOContext
    ): StoredFieldsReader {
        return Lucene90CompressingStoredFieldsReader(
            directory, si, segmentSuffix, fn, context, formatName, compressionMode
        )
    }

    @Throws(IOException::class)
    override fun fieldsWriter(
        directory: Directory,
        si: SegmentInfo,
        context: IOContext
    ): StoredFieldsWriter {
        return Lucene90CompressingStoredFieldsWriter(
            directory,
            si,
            segmentSuffix,
            context,
            formatName,
            compressionMode,
            chunkSize,
            maxDocsPerChunk,
            blockShift
        )
    }

    override fun toString(): String {
        return (this::class.simpleName
                + "(compressionMode="
                + compressionMode
                + ", chunkSize="
                + chunkSize
                + ", maxDocsPerChunk="
                + maxDocsPerChunk
                + ", blockShift="
                + blockShift
                + ")")
    }
}
