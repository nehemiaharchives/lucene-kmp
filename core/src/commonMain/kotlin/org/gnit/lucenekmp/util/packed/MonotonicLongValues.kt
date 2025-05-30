package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator

class MonotonicLongValues(
    pageShift: Int,
    pageMask: Int,
    values: Array<PackedInts.Reader>,
    mins: LongArray,
    averages: FloatArray,
    size: Long,
    ramBytesUsed: Long
) : DeltaPackedLongValues(pageShift, pageMask, values, mins, size, ramBytesUsed) {
    val averages: FloatArray

    init {
        assert(values.size == averages.size)
        this.averages = averages
    }

    override fun get(block: Int, element: Int): Long {
        return MonotonicBlockPackedReader.expected(
            mins[block],
            averages[block],
            element
        ) + values[block].get(element)
    }

    override fun decodeBlock(block: Int, dest: LongArray): Int {
        val count: Int = super.decodeBlock(block, dest)
        val average = averages[block]
        for (i in 0..<count) {
            dest[i] += MonotonicBlockPackedReader.expected(0, average, i)
        }
        return count
    }

    internal class Builder(pageSize: Int, acceptableOverheadRatio: Float) :
        DeltaPackedLongValues.Builder(pageSize, acceptableOverheadRatio) {
        var averages: FloatArray

        init {
            averages = FloatArray(values.size)
            ramBytesUsed += RamUsageEstimator.sizeOf(averages)
        }

        override fun baseRamBytesUsed(): Long {
            return BASE_RAM_BYTES_USED
        }

        override fun build(): MonotonicLongValues {
            finish()
            pending = LongArray(0) /*null*/ // java lucene assigns null to free up memory but it is not nullable value in kmp
            val values: Array<PackedInts.Reader> =
                ArrayUtil.copyOfSubArray<PackedInts.Reader>(
                    this.values,
                    0,
                    valuesOff
                )
            val mins: LongArray = ArrayUtil.copyOfSubArray(this.mins, 0, valuesOff)
            val averages: FloatArray = ArrayUtil.copyOfSubArray(this.averages, 0, valuesOff)
            val ramBytesUsed: Long =
                (MonotonicLongValues.Companion.BASE_RAM_BYTES_USED
                        + RamUsageEstimator.sizeOf(values as Array<Accountable>)
                        + RamUsageEstimator.sizeOf(mins)
                        + RamUsageEstimator.sizeOf(averages))
            return MonotonicLongValues(
                pageShift, pageMask, values, mins, averages, size, ramBytesUsed
            )
        }

        override fun pack(values: LongArray, numValues: Int, block: Int, acceptableOverheadRatio: Float) {
            val average =
                if (numValues == 1) 0f else (values[numValues - 1] - values[0]).toFloat() / (numValues - 1)
            for (i in 0..<numValues) {
                values[i] -= MonotonicBlockPackedReader.expected(0, average, i)
            }
            super.pack(values, numValues, block, acceptableOverheadRatio)
            averages[block] = average
        }

        override fun grow(newBlockCount: Int) {
            super.grow(newBlockCount)
            ramBytesUsed -= RamUsageEstimator.sizeOf(averages)
            averages = ArrayUtil.growExact(averages, newBlockCount)
            ramBytesUsed += RamUsageEstimator.sizeOf(averages)
        }

        companion object {
            private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(
                Builder::class
            )
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(MonotonicLongValues::class)
    }
}
