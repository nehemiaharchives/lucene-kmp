package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.LongValues
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.packed.PackedInts.checkBlockSize
import kotlin.math.max
import kotlin.math.min


/** Utility class to compress integers into a [LongValues] instance.  */
open class PackedLongValues internal constructor(
    val pageShift: Int,
    val pageMask: Int,
    values: Array<PackedInts.Reader>,
    size: Long,
    ramBytesUsed: Long
) : LongValues(), Accountable {
    val values: Array<PackedInts.Reader>
    private val size: Long
    private val ramBytesUsed: Long

    init {
        this.values = values
        this.size = size
        this.ramBytesUsed = ramBytesUsed
    }

    /** Get the number of values in this array.  */
    fun size(): Long {
        return size
    }

    open fun decodeBlock(block: Int, dest: LongArray): Int {
        val vals: PackedInts.Reader = values[block]
        val size: Int = vals.size()
        var k = 0
        while (k < size) {
            k += vals.get(k, dest, k, size - k)
        }
        return size
    }

    open fun get(block: Int, element: Int): Long {
        return values[block].get(element)
    }

    override fun get(index: Long): Long {
        require(index >= 0 && index < size())
        val block = (index shr pageShift).toInt()
        val element = (index and pageMask.toLong()).toInt()
        return get(block, element)
    }

    override fun ramBytesUsed(): Long {
        return ramBytesUsed
    }

    /** Return an iterator over the values of this array.  */
    fun iterator(): Iterator {
        return this.Iterator()
    }

    /** An iterator over long values.  */
    inner class Iterator internal constructor() {
        val currentValues: LongArray = LongArray(min(size.toInt(), pageMask + 1))
        var vOff: Int
        var pOff: Int = 0
        var currentCount: Int = 0 // number of entries of the current page

        init {
            vOff = pOff
            fillBlock()
        }

        private fun fillBlock() {
            if (vOff == values.size) {
                currentCount = 0
            } else {
                currentCount = decodeBlock(vOff, currentValues)
                require(currentCount > 0)
            }
        }

        /** Whether or not there are remaining values.  */
        fun hasNext(): Boolean {
            return pOff < currentCount
        }

        /** Return the next long in the buffer.  */
        fun next(): Long {
            require(hasNext())
            val result = currentValues[pOff++]
            if (pOff == currentCount) {
                vOff += 1
                pOff = 0
                fillBlock()
            }
            return result
        }
    }

    /** A Builder for a [PackedLongValues] instance.  */
    open class Builder internal constructor(pageSize: Int, acceptableOverheadRatio: Float) : Accountable {
        val pageShift: Int = checkBlockSize(pageSize, MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        val pageMask: Int = pageSize - 1
        val acceptableOverheadRatio: Float = acceptableOverheadRatio
        var pending: LongArray
        var size: Long

        var values: Array<PackedInts.Reader>
        var ramBytesUsed: Long
        var valuesOff: Int
        var pendingOff: Int

        init {
            values = kotlin.arrayOfNulls<PackedInts.Reader>(INITIAL_PAGE_COUNT) as Array<PackedInts.Reader>
            pending = LongArray(pageSize)
            valuesOff = 0
            pendingOff = 0
            size = 0
            ramBytesUsed =
                (baseRamBytesUsed()
                        + RamUsageEstimator.sizeOf(pending)
                        + RamUsageEstimator.shallowSizeOf(values))
        }

        /**
         * Build a [PackedLongValues] instance that contains values that have been added to this
         * builder. This operation is destructive.
         */
        open fun build(): PackedLongValues {
            finish()
            pending = LongArray(0)
            val values: Array<PackedInts.Reader> = ArrayUtil.copyOfSubArray(this.values, 0, valuesOff)
            val ramBytesUsed: Long =
                PackedLongValues.Companion.BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(values as Array<Accountable?>)
            return PackedLongValues(pageShift, pageMask, values, size, ramBytesUsed)
        }

        open fun baseRamBytesUsed(): Long {
            return BASE_RAM_BYTES_USED
        }

        override fun ramBytesUsed(): Long {
            return ramBytesUsed
        }

        /** Return the number of elements that have been added to this builder.  */
        fun size(): Long {
            return size
        }

        /** Add a new element to this builder.  */
        fun add(l: Long): Builder {
            checkNotNull(pending) { "Cannot be reused after build()" }
            if (pendingOff == pending.size) {
                // check size
                if (values.size == valuesOff) {
                    val newLength: Int = ArrayUtil.oversize(valuesOff + 1, 8)
                    grow(newLength)
                }
                pack()
            }
            pending[pendingOff++] = l
            size += 1
            return this
        }

        fun finish() {
            if (pendingOff > 0) {
                if (values.size == valuesOff) {
                    grow(valuesOff + 1)
                }
                pack()
            }
        }

        private fun pack() {
            pack(pending, pendingOff, valuesOff, acceptableOverheadRatio)
            ramBytesUsed += values[valuesOff].ramBytesUsed()
            valuesOff += 1
            // reset pending buffer
            pendingOff = 0
        }

        open fun pack(values: LongArray, numValues: Int, block: Int, acceptableOverheadRatio: Float) {
            require(numValues > 0)
            // compute max delta
            var minValue = values[0]
            var maxValue = values[0]
            for (i in 1..<numValues) {
                minValue = min(minValue, values[i])
                maxValue = max(maxValue, values[i])
            }

            // build a new packed reader
            if (minValue == 0L && maxValue == 0L) {
                this.values[block] = PackedInts.NullReader.forCount(numValues)
            } else {
                val bitsRequired = if (minValue < 0) 64 else PackedInts.bitsRequired(maxValue)
                val mutable: PackedInts.Mutable =
                    PackedInts.getMutable(numValues, bitsRequired, acceptableOverheadRatio)
                var i = 0
                while (i < numValues) {
                    i += mutable.set(i, values, i, numValues - i)
                }
                this.values[block] = mutable
            }
        }

        open fun grow(newBlockCount: Int) {
            ramBytesUsed -= RamUsageEstimator.shallowSizeOf(values)
            values = ArrayUtil.growExact(values, newBlockCount)
            ramBytesUsed += RamUsageEstimator.shallowSizeOf(values)
        }

        companion object {
            private const val INITIAL_PAGE_COUNT = 16
            private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(Builder::class)
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(PackedLongValues::class)

        const val DEFAULT_PAGE_SIZE: Int = 256
        const val MIN_PAGE_SIZE: Int = 64

        // More than 1M doesn't really makes sense with these appending buffers
        // since their goal is to try to have small numbers of bits per value
        const val MAX_PAGE_SIZE: Int = 1 shl 20

        /** Return a new [Builder] that will compress efficiently positive integers.  */
        fun packedBuilder(
            pageSize: Int, acceptableOverheadRatio: Float
        ): Builder {
            return Builder(pageSize, acceptableOverheadRatio)
        }

        /**
         * @see .packedBuilder
         */
        fun packedBuilder(acceptableOverheadRatio: Float): Builder {
            return packedBuilder(DEFAULT_PAGE_SIZE, acceptableOverheadRatio)
        }

        /**
         * Return a new [Builder] that will compress efficiently integers that are close to each
         * other.
         */
        fun deltaPackedBuilder(
            pageSize: Int, acceptableOverheadRatio: Float
        ): Builder {
            return Builder(pageSize, acceptableOverheadRatio)
        }

        /**
         * @see .deltaPackedBuilder
         */
        fun deltaPackedBuilder(acceptableOverheadRatio: Float): Builder {
            return deltaPackedBuilder(DEFAULT_PAGE_SIZE, acceptableOverheadRatio)
        }

        /**
         * Return a new [Builder] that will compress efficiently integers that would be a monotonic
         * function of their index.
         */
        fun monotonicBuilder(
            pageSize: Int, acceptableOverheadRatio: Float
        ): Builder {
            return Builder(pageSize, acceptableOverheadRatio)
        }

        /**
         * @see .monotonicBuilder
         */
        fun monotonicBuilder(acceptableOverheadRatio: Float): Builder {
            return monotonicBuilder(DEFAULT_PAGE_SIZE, acceptableOverheadRatio)
        }
    }
}
