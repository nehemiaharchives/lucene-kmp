package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import kotlin.jvm.JvmOverloads

/**
 * Lucene 9.0 DocValues format.
 *
 *
 * Documents that have a value for the field are encoded in a way that it is always possible to
 * know the ordinal of the current document in the set of documents that have a value. For instance,
 * say the set of documents that have a value for the field is `{1, 5, 6, 11}`. When the
 * iterator is on `6`, it knows that this is the 3rd item of the set. This way, values
 * can be stored densely and accessed based on their index at search time. If all documents in a
 * segment have a value for the field, the index is the same as the doc ID, so this case is encoded
 * implicitly and is very fast at query time. On the other hand if some documents are missing a
 * value for the field then the set of documents that have a value is encoded into blocks. All doc
 * IDs that share the same upper 16 bits are encoded into the same block with the following
 * strategies:
 *
 *
 *  * SPARSE: This strategy is used when a block contains at most 4095 documents. The lower 16
 * bits of doc IDs are stored as [shorts][DataOutput.writeShort] while the upper
 * 16 bits are given by the block ID.
 *  * DENSE: This strategy is used when a block contains between 4096 and 65535 documents. The
 * lower bits of doc IDs are stored in a bit set. Advancing &lt; 512 documents is performed
 * using [ntz][Long.numberOfTrailingZeros] operations while the index is computed
 * by accumulating the [bit counts][Long.bitCount] of the visited longs. Advancing
 * &gt;= 512 documents is performed by skipping to the start of the needed 512 document
 * sub-block and iterating to the specific document within that block. The index for the
 * sub-block that is skipped to is retrieved from a rank-table positioned before the bit set.
 * The rank-table holds the origo index numbers for all 512 documents sub-blocks, represented
 * as an unsigned short for each 128 blocks.
 *  * ALL: This strategy is used when a block contains exactly 65536 documents, meaning that the
 * block is full. In that case doc IDs do not need to be stored explicitly. This is typically
 * faster than both SPARSE and DENSE which is a reason why it is preferable to have all
 * documents that have a value for a field using contiguous doc IDs, for instance by using
 * [index sorting][IndexWriterConfig.setIndexSort].
 *
 *
 *
 * Skipping blocks to arrive at a wanted document is either done on an iterative basis or by
 * using the jump-table stored at the end of the chain of blocks. The jump-table holds the offset as
 * well as the index for all blocks, packed in a single long per block.
 *
 *
 * Then the five per-document value types (Numeric,Binary,Sorted,SortedSet,SortedNumeric) are
 * encoded using the following strategies:
 *
 *
 * [NUMERIC][DocValuesType.NUMERIC]:
 *
 *
 *  * Delta-compressed: per-document integers written as deltas from the minimum value,
 * compressed with bitpacking. For more information, see [DirectWriter].
 *  * Table-compressed: when the number of unique values is very small (&lt; 256), and when there
 * are unused "gaps" in the range of values used (such as [SmallFloat]), a lookup table
 * is written instead. Each per-document entry is instead the ordinal to this table, and those
 * ordinals are compressed with bitpacking ([DirectWriter]).
 *  * GCD-compressed: when all numbers share a common divisor, such as dates, the greatest common
 * denominator (GCD) is computed, and quotients are stored using Delta-compressed Numerics.
 *  * Monotonic-compressed: when all numbers are monotonically increasing offsets, they are
 * written as blocks of bitpacked integers, encoding the deviation from the expected delta.
 *  * Const-compressed: when there is only one possible value, no per-document data is needed and
 * this value is encoded alone.
 *
 *
 *
 * Depending on calculated gains, the numbers might be split into blocks of 16384 values. In that
 * case, a jump-table with block offsets is appended to the blocks for O(1) access to the needed
 * block.
 *
 *
 * [BINARY][DocValuesType.BINARY]:
 *
 *
 *  * Fixed-width Binary: one large concatenated byte[] is written, along with the fixed length.
 * Each document's value can be addressed directly with multiplication (`docID *
 * length`).
 *  * Variable-width Binary: one large concatenated byte[] is written, along with end addresses
 * for each document. The addresses are written as Monotonic-compressed numerics.
 *  * Prefix-compressed Binary: values are written in chunks of 16, with the first value written
 * completely and other values sharing prefixes. chunk addresses are written as
 * Monotonic-compressed numerics. A reverse lookup index is written from a portion of every
 * 1024th term.
 *
 *
 *
 * [SORTED][DocValuesType.SORTED]:
 *
 *
 *  * Sorted: a mapping of ordinals to deduplicated terms is written as Prefix-compressed Binary,
 * along with the per-document ordinals written using one of the numeric strategies above.
 *
 *
 *
 * [SORTED_SET][DocValuesType.SORTED_SET]:
 *
 *
 *  * Single: if all documents have 0 or 1 value, then data are written like SORTED.
 *  * SortedSet: a mapping of ordinals to deduplicated terms is written as Binary, an ordinal
 * list and per-document index into this list are written using the numeric strategies above.
 *
 *
 *
 * [SORTED_NUMERIC][DocValuesType.SORTED_NUMERIC]:
 *
 *
 *  * Single: if all documents have 0 or 1 value, then data are written like NUMERIC.
 *  * SortedNumeric: a value list and per-document index into this list are written using the
 * numeric strategies above.
 *
 *
 *
 * Files:
 *
 *
 *  1. `.dvd`: DocValues data
 *  1. `.dvm`: DocValues metadata
 *
 *
 * @lucene.experimental
 */
class Lucene90DocValuesFormat @JvmOverloads constructor(skipIndexIntervalSize: Int = DEFAULT_SKIP_INDEX_INTERVAL_SIZE) :
    DocValuesFormat("Lucene90") {
    private val skipIndexIntervalSize: Int

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): DocValuesConsumer {
        return Lucene90DocValuesConsumer(
            state, skipIndexIntervalSize, DATA_CODEC, DATA_EXTENSION, META_CODEC, META_EXTENSION
        )
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): DocValuesProducer {
        return Lucene90DocValuesProducer(
            state, DATA_CODEC, DATA_EXTENSION, META_CODEC, META_EXTENSION
        )
    }

    /** Doc values fields format with specified skipIndexIntervalSize.  */
    /** Default constructor.  */
    init {
        require(skipIndexIntervalSize >= 2) { "skipIndexIntervalSize must be > 1, got [$skipIndexIntervalSize]" }
        this.skipIndexIntervalSize = skipIndexIntervalSize
    }

    companion object {
        const val DATA_CODEC: String = "Lucene90DocValuesData"
        const val DATA_EXTENSION: String = "dvd"
        const val META_CODEC: String = "Lucene90DocValuesMetadata"
        const val META_EXTENSION: String = "dvm"
        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = VERSION_START

        // indicates docvalues type
        const val NUMERIC: Byte = 0
        const val BINARY: Byte = 1
        const val SORTED: Byte = 2
        const val SORTED_SET: Byte = 3
        const val SORTED_NUMERIC: Byte = 4

        const val DIRECT_MONOTONIC_BLOCK_SHIFT: Int = 16

        const val NUMERIC_BLOCK_SHIFT: Int = 14
        const val NUMERIC_BLOCK_SIZE: Int = 1 shl NUMERIC_BLOCK_SHIFT

        const val TERMS_DICT_BLOCK_LZ4_SHIFT: Int = 6
        const val TERMS_DICT_BLOCK_LZ4_SIZE: Int = 1 shl TERMS_DICT_BLOCK_LZ4_SHIFT
        const val TERMS_DICT_BLOCK_LZ4_MASK: Int = TERMS_DICT_BLOCK_LZ4_SIZE - 1

        const val TERMS_DICT_REVERSE_INDEX_SHIFT: Int = 10
        const val TERMS_DICT_REVERSE_INDEX_SIZE: Int = 1 shl TERMS_DICT_REVERSE_INDEX_SHIFT
        const val TERMS_DICT_REVERSE_INDEX_MASK: Int = TERMS_DICT_REVERSE_INDEX_SIZE - 1

        // number of documents in an interval
        private const val DEFAULT_SKIP_INDEX_INTERVAL_SIZE = 4096

        // bytes on an interval:
        //   * 1 byte : number of levels
        //   * 16 bytes: min / max value,
        //   * 8 bytes:  min / max docID
        //   * 4 bytes: number of documents
        private const val SKIP_INDEX_INTERVAL_BYTES = 29L

        // number of intervals represented as a shift to create a new level, this is 1 << 3 == 8
        // intervals.
        const val SKIP_INDEX_LEVEL_SHIFT: Int = 3

        // max number of levels
        // Increasing this number, it increases how much heap we need at index time.
        // we currently need (1 * 8 * 8 * 8)  = 512 accumulators on heap
        const val SKIP_INDEX_MAX_LEVEL: Int = 4

        // number of bytes to skip when skipping a level. It does not take into account the
        // current interval that is being read.
        val SKIP_INDEX_JUMP_LENGTH_PER_LEVEL: LongArray = LongArray(SKIP_INDEX_MAX_LEVEL)

        init {
            // Size of the interval minus read bytes (1 byte for level and 4 bytes for maxDocID)
            SKIP_INDEX_JUMP_LENGTH_PER_LEVEL[0] = SKIP_INDEX_INTERVAL_BYTES - 5L
            for (level in 1..<SKIP_INDEX_MAX_LEVEL) {
                // jump from previous level
                SKIP_INDEX_JUMP_LENGTH_PER_LEVEL[level] = SKIP_INDEX_JUMP_LENGTH_PER_LEVEL[level - 1]
                // nodes added by new level
                SKIP_INDEX_JUMP_LENGTH_PER_LEVEL[level] +=
                    (1 shl (level * SKIP_INDEX_LEVEL_SHIFT)) * SKIP_INDEX_INTERVAL_BYTES
                // remove the byte levels added in the previous level
                SKIP_INDEX_JUMP_LENGTH_PER_LEVEL[level] -= (1 shl ((level - 1) * SKIP_INDEX_LEVEL_SHIFT)).toLong()
            }
        }
    }
}
