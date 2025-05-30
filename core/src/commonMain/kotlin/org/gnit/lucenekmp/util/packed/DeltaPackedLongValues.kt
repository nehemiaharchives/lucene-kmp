package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.math.min

class DeltaPackedLongValues(
    pageShift: Int,
    pageMask: Int,
    values: Array<PackedInts.Reader>,
    mins: LongArray,
    size: Long,
    ramBytesUsed: Long
) : PackedLongValues(pageShift, pageMask, values, size, ramBytesUsed) {
    val mins: LongArray

    init {
        assert(values.size == mins.size)
        this.mins = mins
    }

    override fun get(block: Int, element: Int): Long {
        return mins[block] + values[block].get(element)
    }

    override fun decodeBlock(block: Int, dest: LongArray): Int {
        val count: Int = super.decodeBlock(block, dest)
        val min = mins[block]
        for (i in 0..<count) {
            dest[i] += min
        }
        return count
    }

    class Builder(pageSize: Int, acceptableOverheadRatio: Float) :
        PackedLongValues.Builder(pageSize, acceptableOverheadRatio) {
        var mins: LongArray

        init {
            mins = LongArray(values.size)
            ramBytesUsed += RamUsageEstimator.sizeOf(mins)
        }

        override fun baseRamBytesUsed(): Long {
            return BASE_RAM_BYTES_USED
        }

        override fun build(): DeltaPackedLongValues {
            finish()
            pending = LongArray(0) /*null*/ // java lucene try to free up memory by assigning null but in kotlin this value is not nullable
            val values: Array<PackedInts.Reader> =
                ArrayUtil.copyOfSubArray<PackedInts.Reader>(
                    this.values,
                    0,
                    valuesOff
                )
            val mins: LongArray = ArrayUtil.copyOfSubArray(this.mins, 0, valuesOff)
            val ramBytesUsed: Long =
                (DeltaPackedLongValues.Companion.BASE_RAM_BYTES_USED
                        + RamUsageEstimator.sizeOf(values as Array<Accountable>)
                        + RamUsageEstimator.sizeOf(mins))
            return DeltaPackedLongValues(pageShift, pageMask, values, mins, size, ramBytesUsed)
        }

        override fun pack(values: LongArray, numValues: Int, block: Int, acceptableOverheadRatio: Float) {
            var min = values[0]
            for (i in 1..<numValues) {
                min = min(min, values[i])
            }
            for (i in 0..<numValues) {
                values[i] -= min
            }
            super.pack(values, numValues, block, acceptableOverheadRatio)
            mins[block] = min
        }

        override fun grow(newBlockCount: Int) {
            super.grow(newBlockCount)
            ramBytesUsed -= RamUsageEstimator.sizeOf(mins)
            mins = ArrayUtil.growExact(mins, newBlockCount)
            ramBytesUsed += RamUsageEstimator.sizeOf(mins)
        }

        companion object {
            private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(
                Builder::class
            )
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(DeltaPackedLongValues::class)
    }
}
