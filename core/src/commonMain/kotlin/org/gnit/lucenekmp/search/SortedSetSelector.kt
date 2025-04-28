package org.gnit.lucenekmp.search


import kotlinx.io.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.util.BytesRef

/** Selects a value from the document's set to use as the representative value  */
object SortedSetSelector {
    /**
     * Wraps a multi-valued SortedSetDocValues as a single-valued view, using the specified selector
     */
    fun wrap(sortedSet: SortedSetDocValues, selector: Type): SortedDocValues {
        if (sortedSet.valueCount >= Int.Companion.MAX_VALUE) {
            throw UnsupportedOperationException(
                ("fields containing more than "
                        + (Int.Companion.MAX_VALUE - 1)
                        + " unique terms are unsupported")
            )
        }

        val singleton: SortedDocValues? = DocValues.unwrapSingleton(sortedSet)
        if (singleton != null) {
            // it's actually single-valued in practice, but indexed as multi-valued,
            // so just sort on the underlying single-valued dv directly.
            // regardless of selector type, this optimization is safe!
            return singleton
        } else {
            when (selector) {
                Type.MIN -> return MinValue(sortedSet)
                Type.MAX -> return MaxValue(sortedSet)
                Type.MIDDLE_MIN -> return MiddleMinValue(sortedSet)
                Type.MIDDLE_MAX -> return MiddleMaxValue(sortedSet)
            }
        }
    }

    /**
     * Type of selection to perform.
     *
     *
     * Limitations:
     *
     *
     *  * Fields containing [Integer.MAX_VALUE] or more unique values are unsupported.
     *  * Selectors other than ([Type.MIN]) require optional codec support. However several
     * codecs provided by Lucene, including the current default codec, support this.
     *
     */
    enum class Type {
        /** Selects the minimum value in the set  */
        MIN,

        /** Selects the maximum value in the set  */
        MAX,

        /**
         * Selects the middle value in the set.
         *
         *
         * If the set has an even number of values, the lower of the middle two is chosen.
         */
        MIDDLE_MIN,

        /**
         * Selects the middle value in the set.
         *
         *
         * If the set has an even number of values, the higher of the middle two is chosen
         */
        MIDDLE_MAX
    }

    /** Wraps a SortedSetDocValues and returns the first ordinal (min)  */
    internal class MinValue(val `in`: SortedSetDocValues) : SortedDocValues() {
        private var ord = 0

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            `in`.nextDoc()
            setOrd()
            return docID()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            `in`.advance(target)
            setOrd()
            return docID()
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            if (`in`.advanceExact(target)) {
                setOrd()
                return true
            }
            return false
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        override fun ordValue(): Int {
            return ord
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Int): BytesRef? {
            return `in`.lookupOrd(ord.toLong())
        }

        override val valueCount: Int
            get() = `in`.valueCount.toInt()

        @Throws(IOException::class)
        override fun lookupTerm(key: BytesRef): Int {
            return `in`.lookupTerm(key).toInt()
        }

        @Throws(IOException::class)
        private fun setOrd() {
            if (docID() != NO_MORE_DOCS) {
                ord = `in`.nextOrd().toInt()
            }
        }
    }

    /** Wraps a SortedSetDocValues and returns the last ordinal (max)  */
    internal class MaxValue(val `in`: SortedSetDocValues) : SortedDocValues() {
        private var ord = 0

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            `in`.nextDoc()
            setOrd()
            return docID()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            `in`.advance(target)
            setOrd()
            return docID()
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            if (`in`.advanceExact(target)) {
                setOrd()
                return true
            }
            return false
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        override fun ordValue(): Int {
            return ord
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Int): BytesRef? {
            return `in`.lookupOrd(ord.toLong())
        }

        override val valueCount: Int
            get() = `in`.valueCount.toInt()

        @Throws(IOException::class)
        override fun lookupTerm(key: BytesRef): Int {
            return `in`.lookupTerm(key).toInt()
        }

        @Throws(IOException::class)
        private fun setOrd() {
            if (docID() != NO_MORE_DOCS) {
                val docValueCount: Int = `in`.docValueCount()
                for (i in 0..<docValueCount - 1) {
                    `in`.nextOrd()
                }
                ord = `in`.nextOrd().toInt()
            }
        }
    }

    /** Wraps a SortedSetDocValues and returns the middle ordinal (or min of the two)  */
    internal class MiddleMinValue(val `in`: SortedSetDocValues) : SortedDocValues() {
        private var ord = 0

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            `in`.nextDoc()
            setOrd()
            return docID()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            `in`.advance(target)
            setOrd()
            return docID()
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            if (`in`.advanceExact(target)) {
                setOrd()
                return true
            }
            return false
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        override fun ordValue(): Int {
            return ord
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Int): BytesRef? {
            return `in`.lookupOrd(ord.toLong())
        }

        override val valueCount: Int
            get() = `in`.valueCount.toInt()

        @Throws(IOException::class)
        override fun lookupTerm(key: BytesRef): Int {
            return `in`.lookupTerm(key).toInt()
        }

        @Throws(IOException::class)
        private fun setOrd() {
            if (docID() != NO_MORE_DOCS) {
                val docValueCount: Int = `in`.docValueCount()
                val targetIdx = (docValueCount - 1) ushr 1
                for (i in 0..<targetIdx) {
                    `in`.nextOrd()
                }
                ord = `in`.nextOrd().toInt()
            }
        }
    }

    /** Wraps a SortedSetDocValues and returns the middle ordinal (or max of the two)  */
    internal class MiddleMaxValue(val `in`: SortedSetDocValues) : SortedDocValues() {
        private var ord = 0

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            `in`.nextDoc()
            setOrd()
            return docID()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            `in`.advance(target)
            setOrd()
            return docID()
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            if (`in`.advanceExact(target)) {
                setOrd()
                return true
            }
            return false
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        override fun ordValue(): Int {
            return ord
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Int): BytesRef? {
            return `in`.lookupOrd(ord.toLong())
        }

        override val valueCount: Int
            get() = `in`.valueCount.toInt()

        @Throws(IOException::class)
        override fun lookupTerm(key: BytesRef): Int {
            return `in`.lookupTerm(key).toInt()
        }

        @Throws(IOException::class)
        private fun setOrd() {
            if (docID() != NO_MORE_DOCS) {
                val docValueCount: Int = `in`.docValueCount()
                val targetIdx = docValueCount ushr 1
                for (i in 0..<targetIdx) {
                    `in`.nextOrd()
                }
                ord = `in`.nextOrd().toInt()
            }
        }
    }
}
