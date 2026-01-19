package org.gnit.lucenekmp.search.comparators

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.LeafFieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.jvm.JvmName


/**
 * Comparator based on [Long.compare] for `numHits`. This comparator provides a skipping
 * functionality – an iterator that can skip over non-competitive documents.
 */
open class LongComparator(numHits: Int, field: String, missingValue: Long, reverse: Boolean, pruning: Pruning) :
    NumericComparator<Long>(
        field, if (missingValue != null) missingValue else 0L, reverse, pruning, Long.SIZE_BYTES
    ) {
    private val values: LongArray
    protected var topValue: Long = 0
    protected var bottom: Long = 0

    init {
        values = LongArray(numHits)
    }

    override fun compare(slot1: Int, slot2: Int): Int {
        return Long.compare(values[slot1], values[slot2])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("setTopValueKt")
    override fun setTopValue(value: Long) {
        super.setTopValue(value)
        topValue = value
    }

    override fun value(slot: Int): Long {
        return values[slot]
    }

    override fun missingValueAsComparableLong(): Long {
        return missingValue!!
    }

    override fun sortableBytesToLong(bytes: ByteArray): Long {
        return NumericUtils.sortableBytesToLong(bytes, 0)
    }

    @Throws(IOException::class)
    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
        return this.LongLeafComparator(context)
    }

    /** Leaf comparator for [LongComparator] that provides skipping functionality  */
    open inner class LongLeafComparator(context: LeafReaderContext) : NumericLeafComparator(context) {
        @Throws(IOException::class)
        private fun getValueForDoc(doc: Int): Long {
            if (docValues.advanceExact(doc)) {
                return docValues.longValue()
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
            return Long.compare(bottom, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun compareTop(doc: Int): Int {
            return Long.compare(topValue, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            values[slot] = getValueForDoc(doc)
            super.copy(slot, doc)
        }

        override fun bottomAsComparableLong(): Long {
            return bottom
        }

        override fun topAsComparableLong(): Long {
            return topValue
        }
    }
}
