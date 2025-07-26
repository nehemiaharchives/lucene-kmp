package org.gnit.lucenekmp.codecs.lucene90


import okio.IOException
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.StoredFieldsWriter
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsFormat
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import kotlin.jvm.JvmOverloads

/**
 * Lucene 9.0 stored fields format.
 *
 *
 * **Principle**
 *
 *
 * This [StoredFieldsFormat] compresses blocks of documents in order to improve the
 * compression ratio compared to document-level compression. It uses the [LZ4](http://code.google.com/p/lz4/) compression algorithm by default in 8KB blocks and
 * shared dictionaries, which is fast to compress and very fast to decompress data. Although the
 * default compression method that is used ([BEST_SPEED][Mode.BEST_SPEED]) focuses more on
 * speed than on compression ratio, it should provide interesting compression ratios for redundant
 * inputs (such as log files, HTML or plain text). For higher compression, you can choose ([ ][Mode.BEST_COMPRESSION]), which uses the [DEFLATE](http://en.wikipedia.org/wiki/DEFLATE) algorithm with 48KB blocks and shared
 * dictionaries for a better ratio at the expense of slower performance. These two options can be
 * configured like this:
 *
 * <pre class="prettyprint">
 * // the default: for high performance
 * indexWriterConfig.setCodec(new Lucene100Codec(Mode.BEST_SPEED));
 * // instead for higher performance (but slower):
 * // indexWriterConfig.setCodec(new Lucene100Codec(Mode.BEST_COMPRESSION));
</pre> *
 *
 *
 * **File formats**
 *
 *
 * Stored fields are represented by three files:
 *
 *
 *  1. <a id="field_data"></a>
 *
 * A fields data file (extension `.fdt`). This file stores a compact
 * representation of documents in compressed blocks of 8KB or more. When writing a segment,
 * documents are appended to an in-memory `byte[]` buffer. When its size reaches
 * 80KB or more, some metadata about the documents is flushed to disk, immediately followed by
 * a compressed representation of the buffer using the [LZ4](https://github.com/lz4/lz4) [compression
 * format](http://fastcompression.blogspot.fr/2011/05/lz4-explained.html).
 *
 * Notes
 *
 *  * When at least one document in a chunk is large enough so that the chunk is larger
 * than 80KB, the chunk will actually be compressed in several LZ4 blocks of 8KB. This
 * allows [StoredFieldVisitor]s which are only interested in the first fields of a
 * document to not have to decompress 10MB of data if the document is 10MB, but only
 * 8-16KB(may cross the block).
 *  * Given that the original lengths are written in the metadata of the chunk, the
 * decompressor can leverage this information to stop decoding as soon as enough data
 * has been decompressed.
 *  * In case documents are incompressible, the overhead of the compression format is less
 * than 0.5%.
 *
 *  1. <a id="field_index"></a>
 *
 * A fields index file (extension `.fdx`). This file stores two [       ], one for the first doc IDs of each block of
 * compressed documents, and another one for the corresponding offsets on disk. At search
 * time, the array containing doc IDs is binary-searched in order to find the block that
 * contains the expected doc ID, and the associated offset on disk is retrieved from the
 * second array.
 *  1. <a id="field_meta"></a>
 *
 * A fields meta file (extension `.fdm`). This file stores metadata about the
 * monotonic arrays stored in the index file.
 *
 *
 *
 * **Known limitations**
 *
 *
 * This [StoredFieldsFormat] does not support individual documents larger than (`
 * 2<sup>31</sup> - 2<sup>14</sup>`) bytes.
 *
 * @lucene.experimental
 */
class Lucene90StoredFieldsFormat(

    /** Stored fields format with specified mode  */
    /** Stored fields format with default options  */
    var mode: Mode = Mode.BEST_SPEED) : StoredFieldsFormat() {
    /** Configuration option for stored fields.  */
    enum class Mode {
        /** Trade compression ratio for retrieval speed.  */
        BEST_SPEED,

        /** Trade retrieval speed for compression ratio.  */
        BEST_COMPRESSION
    }

    @Throws(IOException::class)
    override fun fieldsReader(
        directory: Directory, si: SegmentInfo, fn: FieldInfos?, context: IOContext
    ): StoredFieldsReader {
        val value: String = si.getAttribute(MODE_KEY)
        checkNotNull(value) { "missing value for " + MODE_KEY + " for segment: " + si.name }
        val mode: Mode = Mode.valueOf(value)
        return impl(mode).fieldsReader(directory, si, fn, context)
    }

    @Throws(IOException::class)
    override fun fieldsWriter(directory: Directory, si: SegmentInfo, context: IOContext): StoredFieldsWriter {
        val previous: String? = si.putAttribute(MODE_KEY, mode.name)
        check(!(previous != null && previous != mode.name)) {
            ("found existing value for "
                    + MODE_KEY
                    + " for segment: "
                    + si.name
                    + "old="
                    + previous
                    + ", new="
                    + mode.name)
        }
        return impl(mode).fieldsWriter(directory, si, context)
    }

    fun impl(mode: Mode): StoredFieldsFormat {
        return when (mode) {
            Mode.BEST_SPEED -> Lucene90CompressingStoredFieldsFormat(
                "Lucene90StoredFieldsFastData", BEST_SPEED_MODE, BEST_SPEED_BLOCK_LENGTH, 1024, 10
            )

            Mode.BEST_COMPRESSION -> Lucene90CompressingStoredFieldsFormat(
                "Lucene90StoredFieldsHighData",
                BEST_COMPRESSION_MODE,
                BEST_COMPRESSION_BLOCK_LENGTH,
                4096,
                10
            )
        }
    }

    init {
        this.mode = mode
    }

    companion object {
        /** Attribute key for compression mode.  */
        val MODE_KEY: String = Lucene90StoredFieldsFormat::class.simpleName + ".mode"

        // Shoot for 10 sub blocks of 48kB each.
        private const val BEST_COMPRESSION_BLOCK_LENGTH = 10 * 48 * 1024

        /** Compression mode for [Mode.BEST_COMPRESSION]  */
        val BEST_COMPRESSION_MODE: CompressionMode = DeflateWithPresetDictCompressionMode()

        // Shoot for 10 sub blocks of 8kB each.
        private const val BEST_SPEED_BLOCK_LENGTH = 10 * 8 * 1024

        /** Compression mode for [Mode.BEST_SPEED]  */
        val BEST_SPEED_MODE: CompressionMode = LZ4WithPresetDictCompressionMode()
    }
}
