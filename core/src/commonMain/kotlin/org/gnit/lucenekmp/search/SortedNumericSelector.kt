package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.FilterNumericDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Selects a value from the document's list to use as the representative value
 *
 *
 * This provides a NumericDocValues view over the SortedNumeric, for use with sorting,
 * expressions, function queries, etc.
 */
object SortedNumericSelector {
    /**
     * Wraps a multi-valued SortedNumericDocValues as a single-valued view, using the specified
     * selector and numericType.
     */
    fun wrap(
        sortedNumeric: SortedNumericDocValues,
        selector: Type,
        numericType: SortField.Type
    ): NumericDocValues {
        require(!(numericType != SortField.Type.INT && numericType != SortField.Type.LONG && numericType != SortField.Type.FLOAT && numericType != SortField.Type.DOUBLE)) { "numericType must be a numeric type" }
        val view: NumericDocValues
        val singleton: NumericDocValues? = DocValues.unwrapSingleton(sortedNumeric)
        if (singleton != null) {
            // it's actually single-valued in practice, but indexed as multi-valued,
            // so just sort on the underlying single-valued dv directly.
            // regardless of selector type, this optimization is safe!
            view = singleton
        } else {
            when (selector) {
                Type.MIN -> view = MinValue(sortedNumeric)
                Type.MAX -> view = MaxValue(sortedNumeric)
            }
        }
        // undo the numericutils sortability
        when (numericType) {
            SortField.Type.FLOAT -> return object :
                FilterNumericDocValues(view) {
                @Throws(IOException::class)
                override fun longValue(): Long {
                    return NumericUtils.sortableFloatBits(
                        `in`.longValue().toInt()
                    ).toLong()
                }
            }

            SortField.Type.DOUBLE -> return object :
                FilterNumericDocValues(view) {
                @Throws(IOException::class)
                override fun longValue(): Long {
                    return NumericUtils.sortableDoubleBits(`in`.longValue())
                }
            }

            SortField.Type.INT, SortField.Type.LONG, SortField.Type.CUSTOM, SortField.Type.DOC, SortField.Type.REWRITEABLE, SortField.Type.STRING, SortField.Type.STRING_VAL, SortField.Type.SCORE -> return view
            /*else -> return view*/
        }
    }

    /** Type of selection to perform.  */
    enum class Type {
        /** Selects the minimum value in the set  */
        MIN,

        /** Selects the maximum value in the set  */
        MAX,  // TODO: we could do MEDIAN in constant time (at most 2 lookups)
    }

    /** Wraps a SortedNumericDocValues and returns the first value (min)  */
    internal class MinValue(val `in`: SortedNumericDocValues) :
        NumericDocValues() {
        private var value: Long = 0

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val docID: Int = `in`.nextDoc()
            if (docID != NO_MORE_DOCS) {
                value = `in`.nextValue()
            }
            return docID
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            val docID: Int = `in`.advance(target)
            if (docID != NO_MORE_DOCS) {
                value = `in`.nextValue()
            }
            return docID
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            if (`in`.advanceExact(target)) {
                value = `in`.nextValue()
                return true
            }
            return false
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        override fun longValue(): Long {
            return value
        }
    }

    /** Wraps a SortedNumericDocValues and returns the last value (max)  */
    internal class MaxValue(val `in`: SortedNumericDocValues) : NumericDocValues() {
        private var value: Long = 0

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        private fun setValue() {
            val count: Int = `in`.docValueCount()
            for (i in 0..<count) {
                value = `in`.nextValue()
            }
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val docID: Int = `in`.nextDoc()
            if (docID != NO_MORE_DOCS) {
                setValue()
            }
            return docID
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            val docID: Int = `in`.advance(target)
            if (docID != NO_MORE_DOCS) {
                setValue()
            }
            return docID
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            if (`in`.advanceExact(target)) {
                setValue()
                return true
            }
            return false
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        override fun longValue(): Long {
            return value
        }
    }
}
