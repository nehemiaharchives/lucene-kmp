package org.gnit.lucenekmp.search.comparators

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.search.LeafFieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.jvm.JvmName


/**
 * Comparator based on [Float.compare] for `numHits`. This comparator provides a
 * skipping functionality – an iterator that can skip over non-competitive documents.
 */
class FloatComparator(numHits: Int, field: String, missingValue: Float?, reverse: Boolean, pruning: Pruning) :
    NumericComparator<Float>(
        field, if (missingValue != null) missingValue else 0.0f, reverse, pruning, Float.SIZE_BYTES
    ) {
    private val values: FloatArray
    protected var topValue: Float = 0f
    protected var bottom: Float = 0f

    init {
        values = FloatArray(numHits)
    }

    override fun compare(slot1: Int, slot2: Int): Int {
        return Float.compare(values[slot1], values[slot2])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("setTopValueKt")
    override fun setTopValue(value: Float) {
        super.setTopValue(value)
        topValue = value
    }

    override fun value(slot: Int): Float {
        return values[slot]
    }

    override fun missingValueAsComparableLong(): Long {
        return NumericUtils.floatToSortableInt(missingValue).toLong()
    }

    override fun sortableBytesToLong(bytes: ByteArray): Long {
        return NumericUtils.sortableBytesToInt(bytes, 0).toLong()
    }

    @Throws(IOException::class)
    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
        return this.FloatLeafComparator(context)
    }

    /** Leaf comparator for [FloatComparator] that provides skipping functionality  */
    inner class FloatLeafComparator(context: LeafReaderContext) : NumericLeafComparator(context) {
        @Throws(IOException::class)
        private fun getValueForDoc(doc: Int): Float {
            if (docValues.advanceExact(doc)) {
                return Float.intBitsToFloat(docValues.longValue().toInt())
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
            return Float.compare(bottom, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun compareTop(doc: Int): Int {
            return Float.compare(topValue, getValueForDoc(doc))
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            values[slot] = getValueForDoc(doc)
            super.copy(slot, doc)
        }

        override fun bottomAsComparableLong(): Long {
            return NumericUtils.floatToSortableInt(bottom).toLong()
        }

        override fun topAsComparableLong(): Long {
            return NumericUtils.floatToSortableInt(topValue).toLong()
        }
    }
}
