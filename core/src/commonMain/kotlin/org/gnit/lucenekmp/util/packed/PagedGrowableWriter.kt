package org.gnit.lucenekmp.util.packed


import org.gnit.lucenekmp.util.packed.PackedInts.Mutable

/**
 * A [PagedGrowableWriter]. This class slices data into fixed-size blocks which have
 * independent numbers of bits per value and grow on-demand.
 *
 *
 * You should use this class instead of the [PackedLongValues] related ones only when you
 * need random write-access. Otherwise this class will likely be slower and less memory-efficient.
 *
 * @lucene.internal
 */
class PagedGrowableWriter internal constructor(
    size: Long,
    pageSize: Int,
    startBitsPerValue: Int,
    val acceptableOverheadRatio: Float,
    fillPages: Boolean
) : AbstractPagedMutable<PagedGrowableWriter>(startBitsPerValue, size, pageSize) {
    /**
     * Create a new [PagedGrowableWriter] instance.
     *
     * @param size the number of values to store.
     * @param pageSize the number of values per page
     * @param startBitsPerValue the initial number of bits per value
     * @param acceptableOverheadRatio an acceptable overhead ratio
     */
    constructor(size: Long, pageSize: Int, startBitsPerValue: Int, acceptableOverheadRatio: Float) : this(
        size,
        pageSize,
        startBitsPerValue,
        acceptableOverheadRatio,
        true
    )

    init {
        if (fillPages) {
            fillPages()
        }
    }

    override fun newMutable(valueCount: Int, bitsPerValue: Int): Mutable {
        return GrowableWriter(bitsPerValue, valueCount, acceptableOverheadRatio)
    }

    override fun newUnfilledCopy(newSize: Long): PagedGrowableWriter {
        return PagedGrowableWriter(
            newSize, pageSize(), bitsPerValue, acceptableOverheadRatio, false
        )
    }

    override fun baseRamBytesUsed(): Long {
        return super.baseRamBytesUsed() + Float.SIZE_BYTES
    }
}
