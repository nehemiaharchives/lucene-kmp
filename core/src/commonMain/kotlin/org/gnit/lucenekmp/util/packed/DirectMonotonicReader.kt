package org.gnit.lucenekmp.util.packed


import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.util.LongValues

/**
 * Retrieves an instance previously written by [DirectMonotonicWriter].
 *
 * @see DirectMonotonicWriter
 */
class DirectMonotonicReader private constructor(
    private val blockShift: Int,
    private val readers: Array<LongValues>,
    mins: LongArray,
    avgs: FloatArray,
    bpvs: ByteArray
) : LongValues() {
    /**
     * In-memory metadata that needs to be kept around for [DirectMonotonicReader] to read data
     * from disk.
     */
    class Meta internal constructor(numValues: Long, internal val blockShift: Int) {
        internal val numBlocks: Int
        internal val mins: LongArray
        internal val avgs: FloatArray
        internal val bpvs: ByteArray
        internal val offsets: LongArray

        init {
            var numBlocks = numValues ushr blockShift
            if ((numBlocks shl blockShift) < numValues) {
                numBlocks += 1
            }
            this.numBlocks = numBlocks.toInt()
            this.mins = LongArray(this.numBlocks)
            this.avgs = FloatArray(this.numBlocks)
            this.bpvs = ByteArray(this.numBlocks)
            this.offsets = LongArray(this.numBlocks)
        }

        companion object {
            // Use a shift of 63 so that there would be a single block regardless of the number of values.
            internal val SINGLE_ZERO_BLOCK = Meta(1L, 63)
        }
    }

    private val blockMask: Long = (1L shl blockShift) - 1
    private val mins: LongArray
    private val avgs: FloatArray
    private val bpvs: ByteArray

    init {
        this.mins = mins
        this.avgs = avgs
        this.bpvs = bpvs
        require(!(readers.size != mins.size || readers.size != avgs.size || readers.size != bpvs.size))
    }

    override fun get(index: Long): Long {
        val block = (index ushr blockShift).toInt()
        val blockIndex = index and blockMask
        val delta: Long = readers[block].get(blockIndex)
        return mins[block] + (avgs[block] * blockIndex).toLong() + delta
    }

    /** Get lower/upper bounds for the value at a given index without hitting the direct reader.  */
    private fun getBounds(index: Long): LongArray {
        val block: Int = Math.toIntExact(index ushr blockShift)
        val blockIndex = index and blockMask
        val lowerBound = mins[block] + (avgs[block] * blockIndex).toLong()
        val upperBound = lowerBound + (1L shl bpvs[block].toInt()) - 1
        return if (bpvs[block].toInt() == 64 || upperBound < lowerBound) { // overflow
            longArrayOf(Long.Companion.MIN_VALUE, Long.Companion.MAX_VALUE)
        } else {
            longArrayOf(lowerBound, upperBound)
        }
    }

    /**
     * Return the index of a key if it exists, or its insertion point otherwise like [ ][Arrays.binarySearch].
     *
     * @see Arrays.binarySearch
     */
    fun binarySearch(fromIndex: Long, toIndex: Long, key: Long): Long {
        require(!(fromIndex < 0 || fromIndex > toIndex)) { "fromIndex=$fromIndex,toIndex=$toIndex" }
        var lo = fromIndex
        var hi = toIndex - 1

        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            // Try to run as many iterations of the binary search as possible without
            // hitting the direct readers, since they might hit a page fault.
            val bounds = getBounds(mid)
            if (bounds[1] < key) {
                lo = mid + 1
            } else if (bounds[0] > key) {
                hi = mid - 1
            } else {
                val midVal = get(mid)
                if (midVal < key) {
                    lo = mid + 1
                } else if (midVal > key) {
                    hi = mid - 1
                } else {
                    return mid
                }
            }
        }

        return -1 - lo
    }

    companion object {
        /**
         * Load metadata from the given [IndexInput].
         *
         * @see DirectMonotonicReader.getInstance
         */
        @Throws(IOException::class)
        fun loadMeta(metaIn: IndexInput, numValues: Long, blockShift: Int): Meta {
            var allValuesZero = true
            val meta = Meta(numValues, blockShift)
            for (i in 0..<meta.numBlocks) {
                val min: Long = metaIn.readLong()
                meta.mins[i] = min
                val avgInt: Int = metaIn.readInt()
                meta.avgs[i] = Float.intBitsToFloat(avgInt)
                meta.offsets[i] = metaIn.readLong()
                val bpvs: Byte = metaIn.readByte()
                meta.bpvs[i] = bpvs
                allValuesZero = allValuesZero && min == 0L && avgInt == 0 && bpvs.toInt() == 0
            }
            // save heap in case all values are zero
            return if (allValuesZero) Meta.Companion.SINGLE_ZERO_BLOCK else meta
        }

        /** Retrieves a non-merging instance from the specified slice.  */
        @Throws(IOException::class)
        fun getInstance(meta: Meta, data: RandomAccessInput): DirectMonotonicReader {
            return getInstance(meta, data, false)
        }

        /** Retrieves an instance from the specified slice.  */
        @Throws(IOException::class)
        fun getInstance(
            meta: Meta, data: RandomAccessInput, merging: Boolean
        ): DirectMonotonicReader {
            val readers: Array<LongValues?> = kotlin.arrayOfNulls<LongValues>(meta.numBlocks)
            for (i in 0..<meta.numBlocks) {
                if (meta.bpvs[i].toInt() == 0) {
                    readers[i] = ZEROES
                } else if (merging
                    && i < meta.numBlocks - 1 // we only know the number of values for the last block
                    && meta.blockShift >= DirectReader.MERGE_BUFFER_SHIFT
                ) {
                    readers[i] =
                        DirectReader.getMergeInstance(
                            data, meta.bpvs[i].toInt(), meta.offsets[i], 1L shl meta.blockShift
                        )
                } else {
                    readers[i] = DirectReader.getInstance(data, meta.bpvs[i].toInt(), meta.offsets[i])
                }
            }

            return DirectMonotonicReader(meta.blockShift, readers as Array<LongValues>, meta.mins, meta.avgs, meta.bpvs)
        }
    }
}
