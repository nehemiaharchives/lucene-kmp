package org.gnit.lucenekmp.search.comparators

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.LeafFieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.util.NumericUtils


/**
 * Comparator based on [Integer.compare] for `numHits`. This comparator provides a
 * skipping functionality – an iterator that can skip over non-competitive documents.
 */
class IntComparator(numHits: Int, field: String, missingValue: Int, reverse: Boolean, pruning: Pruning) :
    NumericComparator<Int>(
        field,
        if (missingValue != null) missingValue else 0,
        reverse,
        pruning,
        Int.SIZE_BYTES
    ) {
    private val values: IntArray
    protected var topValue: Int = 0
    protected var bottom: Int = 0

    init {
        values = IntArray(numHits)
    }

    override fun compare(slot1: Int, slot2: Int): Int {
        return Int.compare(values[slot1], values[slot2])
    }

    override fun setTopValue(value: Int) {
        super.setTopValue(value)
        topValue = value
    }

    override fun value(slot: Int): Int {
        return values[slot]
    }

    protected override fun missingValueAsComparableLong(): Long {
        return missingValue.toLong()
    }

    protected override fun sortableBytesToLong(bytes: ByteArray): Long {
        return NumericUtils.sortableBytesToInt(bytes, 0).toLong()
    }

    @Throws(IOException::class)
    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
        return this.IntLeafComparator(context)
    }

    /** Leaf comparator for [IntComparator] that provides skipping functionality  */
    inner class IntLeafComparator(context: LeafReaderContext) : NumericLeafComparator(context) {
        @Throws(IOException::class)
        private fun getValueForDoc(doc: Int): Int {
            if (docValues.advanceExact(doc)) {
                return docValues.longValue() as Int
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
            return Int.compare(bottom, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun compareTop(doc: Int): Int {
            return Int.compare(topValue, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            values[slot] = getValueForDoc(doc)
            super.copy(slot, doc)
        }

        override fun bottomAsComparableLong(): Long {
            return bottom.toLong()
        }

        override fun topAsComparableLong(): Long {
            return topValue.toLong()
        }
    }
}
