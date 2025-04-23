package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.math.min

/**
 * Implements [PackedInts.Mutable], but grows the bit count of the underlying packed ints
 * on-demand.
 *
 *
 * Beware that this class will accept to set negative values but in order to do this, it will
 * grow the number of bits per value to 64.
 *
 *
 * @lucene.internal
 */
class GrowableWriter(startBitsPerValue: Int, valueCount: Int, private val acceptableOverheadRatio: Float) :
    PackedInts.Mutable() {
    private var currentMask: Long
    var mutable: PackedInts.Mutable
        private set

    /**
     * @param startBitsPerValue the initial number of bits per value, may grow depending on the data
     * @param valueCount the number of values
     * @param acceptableOverheadRatio an acceptable overhead ratio
     */
    init {
        this.mutable = PackedInts.getMutable(valueCount, startBitsPerValue, this.acceptableOverheadRatio)
        currentMask = mask(mutable.getBitsPerValue())
    }

    override fun get(index: Int): Long {
        return mutable.get(index)
    }

    override fun size(): Int {
        return mutable.size()
    }

    override val bitsPerValue: Int
        get() = mutable.getBitsPerValue()

    override fun getBitsPerValue(): Int {
        return bitsPerValue
    }

    private fun ensureCapacity(value: Long) {
        if ((value and currentMask) == value) {
            return
        }
        val bitsRequired = PackedInts.unsignedBitsRequired(value)
        require(bitsRequired > mutable.getBitsPerValue())
        val valueCount = size()
        val next =
            PackedInts.getMutable(valueCount, bitsRequired, acceptableOverheadRatio)
        PackedInts.copy(this.mutable, 0, next, 0, valueCount, PackedInts.DEFAULT_BUFFER_SIZE)
        this.mutable = next
        currentMask = mask(mutable.getBitsPerValue())
    }

    override fun set(index: Int, value: Long) {
        ensureCapacity(value)
        mutable.set(index, value)
    }

    override fun clear() {
        mutable.clear()
    }

    fun resize(newSize: Int): GrowableWriter {
        val next = GrowableWriter(getBitsPerValue(), newSize, acceptableOverheadRatio)
        val limit = min(size(), newSize)
        PackedInts.copy(this.mutable, 0, next, 0, limit, PackedInts.DEFAULT_BUFFER_SIZE)
        return next
    }

    override fun get(index: Int, arr: LongArray, off: Int, len: Int): Int {
        return mutable.get(index, arr, off, len)
    }

    override fun set(index: Int, arr: LongArray, off: Int, len: Int): Int {
        var max: Long = 0
        var i = off
        val end = off + len
        while (i < end) {
            // bitwise or is nice because either all values are positive and the
            // or-ed result will require as many bits per value as the max of the
            // values, or one of them is negative and the result will be negative,
            // forcing GrowableWriter to use 64 bits per value
            max = max or arr[i]
            ++i
        }
        ensureCapacity(max)
        return mutable.set(index, arr, off, len)
    }

    override fun fill(fromIndex: Int, toIndex: Int, `val`: Long) {
        ensureCapacity(`val`)
        mutable.fill(fromIndex, toIndex, `val`)
    }

    override fun ramBytesUsed(): Long {
        return (RamUsageEstimator.alignObjectSize(
            (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                    + RamUsageEstimator.NUM_BYTES_OBJECT_REF
                    + Long.SIZE_BYTES
                    + Float.SIZE_BYTES).toLong()
        )
                + mutable.ramBytesUsed())
    }

    companion object {
        private fun mask(bitsPerValue: Int): Long {
            return if (bitsPerValue == 64) 0L.inv() else PackedInts.maxValue(bitsPerValue)
        }
    }
}
