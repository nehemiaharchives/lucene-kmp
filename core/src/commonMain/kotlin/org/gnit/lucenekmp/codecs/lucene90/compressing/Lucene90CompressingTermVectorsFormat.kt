package org.gnit.lucenekmp.codecs.lucene90.compressing

import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import okio.IOException

/**
 * A [TermVectorsFormat] that compresses chunks of documents together in order to improve the
 * compression ratio.
 *
 * @lucene.experimental
 */
open class Lucene90CompressingTermVectorsFormat(
    private val formatName: String,
    private val segmentSuffix: String,
    private val compressionMode: CompressionMode,
    chunkSize: Int,
    maxDocsPerChunk: Int,
    blockSize: Int
) : TermVectorsFormat() {
    private val chunkSize: Int
    private val blockSize: Int
    private val maxDocsPerChunk: Int

    /**
     * Create a new [Lucene90CompressingTermVectorsFormat].
     *
     *
     * `formatName` is the name of the format. This name will be used in the file
     * formats to perform [codec header checks][CodecUtil.checkIndexHeader].
     *
     *
     * The `compressionMode` parameter allows you to choose between compression
     * algorithms that have various compression and decompression speeds so that you can pick the one
     * that best fits your indexing and searching throughput. You should never instantiate two [ ]s that have the same name but different [ ]s.
     *
     *
     * `chunkSize` is the minimum byte size of a chunk of documents. Higher values of
     * `chunkSize` should improve the compression ratio but will require more memory at
     * indexing time and might make document loading a little slower (depending on the size of your OS
     * cache compared to the size of your index).
     *
     * @param formatName the name of the [StoredFieldsFormat]
     * @param segmentSuffix a suffix to append to files created by this format
     * @param compressionMode the [CompressionMode] to use
     * @param chunkSize the minimum number of bytes of a single chunk of stored documents
     * @param maxDocsPerChunk the maximum number of documents in a single chunk
     * @param blockSize the number of chunks to store in an index block.
     * @see CompressionMode
     */
    init {
        require(chunkSize >= 1) { "chunkSize must be >= 1" }
        this.chunkSize = chunkSize
        this.maxDocsPerChunk = maxDocsPerChunk
        require(blockSize >= 1) { "blockSize must be >= 1" }
        this.blockSize = blockSize
    }

    @Throws(IOException::class)
    override fun vectorsReader(
        directory: Directory, segmentInfo: SegmentInfo, fieldInfos: FieldInfos, context: IOContext
    ): TermVectorsReader {
        return Lucene90CompressingTermVectorsReader(
            directory, segmentInfo, segmentSuffix, fieldInfos, context, formatName, compressionMode
        )
    }

    @Throws(IOException::class)
    override fun vectorsWriter(
        directory: Directory, segmentInfo: SegmentInfo, context: IOContext
    ): TermVectorsWriter {
        return Lucene90CompressingTermVectorsWriter(
            directory,
            segmentInfo,
            segmentSuffix,
            context,
            formatName,
            compressionMode,
            chunkSize,
            maxDocsPerChunk,
            blockSize
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
                + ", blockSize="
                + blockSize
                + ")")
    }
}
