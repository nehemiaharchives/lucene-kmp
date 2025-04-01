package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.hashCode
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.packed.PackedInts

/** Set of longs, optimized for docvalues usage  */
internal class DocValuesLongHashSet(values: LongArray) : Accountable {
    val table: LongArray
    val mask: Int
    val hasMissingValue: Boolean
    val size: Int

    /** minimum value in the set, or Long.MAX_VALUE for an empty set  */
    val minValue: Long

    /** maximum value in the set, or Long.MIN_VALUE for an empty set  */
    val maxValue: Long

    /** Construct a set. Values must be in sorted order.  */
    init {
        var tableSize: Int = Math.toIntExact(values.size * 3L / 2)
        tableSize = 1 shl PackedInts.bitsRequired(tableSize.toLong()) // make it a power of 2
        require(tableSize >= values.size * 3L / 2)
        table = LongArray(tableSize)
        Arrays.fill(table, MISSING)
        mask = tableSize - 1
        var hasMissingValue = false
        var size = 0
        var previousValue = Long.Companion.MIN_VALUE // for assert
        for (value in values) {
            if (value == MISSING) {
                size += if (hasMissingValue) 0 else 1
                hasMissingValue = true
            } else if (add(value)) {
                ++size
            }
            require(value >= previousValue) { "values must be provided in sorted order" }
            previousValue = value
        }
        this.hasMissingValue = hasMissingValue
        this.size = size
        this.minValue = if (values.isEmpty()) Long.Companion.MAX_VALUE else values[0]
        this.maxValue = if (values.isEmpty()) Long.Companion.MIN_VALUE else values[values.size - 1]
    }

    private fun add(l: Long): Boolean {
        require(l != MISSING)
        val slot = Long.hashCode(l) and mask
        var i = slot
        while (true) {
            if (table[i] == MISSING) {
                table[i] = l
                return true
            } else if (table[i] == l) {
                // already added
                return false
            }
            i = (i + 1) and mask
        }
    }

    /**
     * check for membership in the set.
     *
     *
     * You should use [.minValue] and [.maxValue] to guide/terminate iteration before
     * calling this.
     */
    fun contains(l: Long): Boolean {
        if (l == MISSING) {
            return hasMissingValue
        }
        val slot = Long.hashCode(l) and mask
        var i = slot
        while (true) {
            if (table[i] == MISSING) {
                return false
            } else if (table[i] == l) {
                return true
            }
            i = (i + 1) and mask
        }
    }

    /** returns a stream of all values contained in this set  */
    fun stream(): Sequence<Long> {
        val filteredSequence = table.asSequence().filter { it != MISSING }
        return if (hasMissingValue) {
            sequenceOf(MISSING) + filteredSequence
        } else {
            filteredSequence
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(size, minValue, maxValue, mask, hasMissingValue, table.contentHashCode())
    }

    override fun equals(obj: Any?): Boolean {
        if (obj is DocValuesLongHashSet) {
            return size == obj.size && minValue == obj.minValue && maxValue == obj.maxValue && mask == obj.mask && hasMissingValue == obj.hasMissingValue && table.contentEquals(
                obj.table
            )
        }
        return false
    }

    override fun toString(): String {
        return stream()
            .map { it.toString() }
            .joinToString(", ", "[", "]")
    }

    /** number of elements in the set  */
    fun size(): Int {
        return size
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES + RamUsageEstimator.sizeOfObject(table)
    }

    companion object {
        private val BASE_RAM_BYTES: Long = RamUsageEstimator.shallowSizeOfInstance(DocValuesLongHashSet::class)

        private const val MISSING = Long.Companion.MIN_VALUE
    }
}
