package org.gnit.lucenekmp.search.comparators

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.longBitsToDouble
import org.gnit.lucenekmp.search.LeafFieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.util.NumericUtils


/**
 * Comparator based on [Double.compare] for `numHits`. This comparator provides a
 * skipping functionality - an iterator that can skip over non-competitive documents.
 */
class DoubleComparator(numHits: Int, field: String, missingValue: Double, reverse: Boolean, pruning: Pruning) :
    NumericComparator<Double>(
        field, if (missingValue != null) missingValue else 0.0, reverse, pruning, Double.SIZE_BYTES
    ) {
    private val values: DoubleArray
    protected var topValue: Double = 0.0
    protected var bottom: Double = 0.0

    init {
        values = DoubleArray(numHits)
    }

    override fun compare(slot1: Int, slot2: Int): Int {
        return Double.compare(values[slot1], values[slot2])
    }

    override fun setTopValue(value: Double) {
        super.setTopValue(value)
        topValue = value
    }

    override fun value(slot: Int): Double {
        return values[slot]
    }

    override fun missingValueAsComparableLong(): Long {
        return NumericUtils.doubleToSortableLong(missingValue)
    }

    override fun sortableBytesToLong(bytes: ByteArray): Long {
        return NumericUtils.sortableBytesToLong(bytes, 0)
    }

    @Throws(IOException::class)
    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
        return this.DoubleLeafComparator(context)
    }

    /** Leaf comparator for [DoubleComparator] that provides skipping functionality  */
    inner class DoubleLeafComparator(context: LeafReaderContext) : NumericLeafComparator(context) {
        @Throws(IOException::class)
        private fun getValueForDoc(doc: Int): Double {
            if (docValues.advanceExact(doc)) {
                return Double.longBitsToDouble(docValues.longValue())
            } else {
                return missingValue
            }
        }

        @Throws(IOException::class)
        override fun setBottom(slot: Int) {
            bottom = values[slot]
            super.setBottom(slot)
        }

        @Throws(IOException::class)
        override fun compareBottom(doc: Int): Int {
            return Double.compare(bottom, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun compareTop(doc: Int): Int {
            return Double.compare(topValue, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            values[slot] = getValueForDoc(doc)
            super.copy(slot, doc)
        }

        override fun bottomAsComparableLong(): Long {
            return NumericUtils.doubleToSortableLong(bottom)
        }

        override fun topAsComparableLong(): Long {
            return NumericUtils.doubleToSortableLong(topValue)
        }
    }
}
