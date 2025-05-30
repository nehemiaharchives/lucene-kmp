package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.RamUsageEstimator


/**
 * A [PagedMutable]. This class slices data into fixed-size blocks which have the same number
 * of bits per value. It can be a useful replacement for [PackedInts.Mutable] to store more
 * than 2B values.
 *
 * @lucene.internal
 */
class PagedMutable internal constructor(
    size: Long,
    pageSize: Int,
    bitsPerValue: Int,
    val format: PackedInts.Format
) : AbstractPagedMutable<PagedMutable>(bitsPerValue, size, pageSize) {

    /**
     * Create a new [PagedMutable] instance.
     *
     * @param size the number of values to store.
     * @param pageSize the number of values per page
     * @param bitsPerValue the number of bits per value
     * @param acceptableOverheadRatio an acceptable overhead ratio
     */
    constructor(size: Long, pageSize: Int, bitsPerValue: Int, acceptableOverheadRatio: Float) : this(
        size,
        pageSize,
        PackedInts.fastestFormatAndBits(pageSize, bitsPerValue, acceptableOverheadRatio)
    ) {
        fillPages()
    }

    internal constructor(
        size: Long,
        pageSize: Int,
        formatAndBits: PackedInts.FormatAndBits
    ) : this(size, pageSize, formatAndBits.bitsPerValue, formatAndBits.format)

    override fun newMutable(valueCount: Int, bitsPerValue: Int): PackedInts.Mutable {
        assert(this.bitsPerValue >= bitsPerValue)
        return PackedInts.getMutable(valueCount, this.bitsPerValue, format)
    }

    override fun newUnfilledCopy(newSize: Long): PagedMutable {
        return PagedMutable(newSize, pageSize(), bitsPerValue, format)
    }

    override fun baseRamBytesUsed(): Long {
        return super.baseRamBytesUsed() + RamUsageEstimator.NUM_BYTES_OBJECT_REF
    }
}
