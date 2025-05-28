package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder


/**
 * Expert: a FieldComparator compares hits so as to determine their sort order when collecting the
 * top results with [TopFieldCollector]. The concrete public FieldComparator classes here
 * correspond to the SortField types.
 *
 *
 * The document IDs passed to these methods must only move forwards, since they are using doc
 * values iterators to retrieve sort values.
 *
 *
 * This API is designed to achieve high performance sorting, by exposing a tight interaction with
 * [FieldValueHitQueue] as it visits hits. Whenever a hit is competitive, it's enrolled into a
 * virtual slot, which is an int ranging from 0 to numHits-1. Segment transitions are handled by
 * creating a dedicated per-segment [LeafFieldComparator] which also needs to interact with
 * the [FieldValueHitQueue] but can optimize based on the segment to collect.
 *
 *
 * The following functions need to be implemented
 *
 *
 *  * [.compare] Compare a hit at 'slot a' with hit 'slot b'.
 *  * [.setTopValue] This method is called by [TopFieldCollector] to notify the
 * FieldComparator of the top most value, which is used by future calls to [       ][LeafFieldComparator.compareTop].
 *  * [.getLeafComparator] Invoked when the
 * search is switching to the next segment. You may need to update internal state of the
 * comparator, for example retrieving new values from DocValues.
 *  * [.value] Return the sort value stored in the specified slot. This is only called at
 * the end of the search, in order to populate [FieldDoc.fields] when returning the top
 * results.
 *
 *
 * @see LeafFieldComparator
 *
 * @lucene.experimental
 */
abstract class FieldComparator<T> {
    /**
     * Compare hit at slot1 with hit at slot2.
     *
     * @param slot1 first slot to compare
     * @param slot2 second slot to compare
     * @return any `N < 0` if slot2's value is sorted after slot1, any `N > 0` if the
     * slot2's value is sorted before slot1 and `0` if they are equal
     */
    abstract fun compare(slot1: Int, slot2: Int): Int

    /**
     * Record the top value, for future calls to [LeafFieldComparator.compareTop]. This is only
     * called for searches that use searchAfter (deep paging), and is called before any calls to
     * [.getLeafComparator].
     */
    abstract fun setTopValue(value: T)

    /**
     * Return the actual value in the slot.
     *
     * @param slot the value
     * @return value in this slot
     */
    abstract fun value(slot: Int): T

    /**
     * Get a per-segment [LeafFieldComparator] to collect the given [ ]. All docIDs supplied to this [ ] are relative to the current reader (you must add docBase if you need to
     * map it to a top-level docID).
     *
     * @param context current reader context
     * @return the comparator to use for this segment
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    abstract fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator

    /**
     * Returns a negative integer if first is less than second, 0 if they are equal and a positive
     * integer otherwise. Default impl to assume the type implements Comparable and invoke .compareTo;
     * be sure to override this method if your FieldComparator's type isn't a Comparable or if your
     * values may sometimes be null
     */
    open fun compareValues(first: T, second: T): Int {
        if (first == null) {
            if (second == null) {
                return 0
            } else {
                return -1
            }
        } else if (second == null) {
            return 1
        } else {
            return (first as Comparable<T>).compareTo(second)
        }
    }

    /**
     * Informs the comparator that sort is done on this single field. This is useful to enable some
     * optimizations for skipping non-competitive documents.
     */
    open fun setSingleSort() {}

    /**
     * Informs the comparator that the skipping of documents should be disabled. This function is
     * called by TopFieldCollector in cases when the skipping functionality should not be applied or
     * not necessary. An example could be when search sort is a part of the index sort, and can be
     * already efficiently handled by TopFieldCollector, and doing extra work for skipping in the
     * comparator is redundant.
     */
    open fun disableSkipping() {}

    /**
     * Sorts by descending relevance. NOTE: if you are sorting only by descending relevance and then
     * secondarily by ascending docID, performance is faster using [TopScoreDocCollector]
     * directly (which [IndexSearcher.search] uses when no [Sort] is
     * specified).
     */
    class RelevanceComparator(numHits: Int) : FieldComparator<Float>(), LeafFieldComparator {
        private val scores: FloatArray = FloatArray(numHits)
        private var bottom = 0f
        private lateinit var scorer: Scorable
        private var topValue = 0f

        override fun compare(slot1: Int, slot2: Int): Int {
            return Float.compare(scores[slot2], scores[slot1])
        }

        @Throws(IOException::class)
        override fun compareBottom(doc: Int): Int {
            val score: Float = scorer.score()
            require(!Float.isNaN(score))
            return Float.compare(score, bottom)
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            scores[slot] = scorer.score()
            require(!Float.isNaN(scores[slot]))
        }

        override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
            return this
        }

        override fun setBottom(bottom: Int) {
            this.bottom = scores[bottom]
        }

        override fun setTopValue(value: Float) {
            topValue = value
        }

        override fun setScorer(scorer: Scorable) {
            this.scorer = scorer
        }

        override fun value(slot: Int): Float {
            return scores[slot]
        }

        // Override because we sort reverse of natural Float order:
        override fun compareValues(first: Float, second: Float): Int {
            // Reversed intentionally because relevance by default
            // sorts descending:
            return second.compareTo(first)
        }

        @Throws(IOException::class)
        override fun compareTop(doc: Int): Int {
            val docValue: Float = scorer.score()
            require(!Float.isNaN(docValue))
            return Float.compare(docValue, topValue)
        }
    }

    /**
     * Sorts by field's natural Term sort order. All comparisons are done using BytesRef.compareTo,
     * which is slow for medium to large result sets but possibly very fast for very small results
     * sets.
     */
    class TermValComparator(numHits: Int, private val field: String, sortMissingLast: Boolean) :
        FieldComparator<BytesRef>(), LeafFieldComparator {
        private val values: Array<BytesRef?> = kotlin.arrayOfNulls<BytesRef>(numHits)
        private val tempBRs: Array<BytesRefBuilder?> = kotlin.arrayOfNulls<BytesRefBuilder>(numHits)
        private var docTerms: BinaryDocValues? = null
        private var bottom: BytesRef? = null
        private var topValue: BytesRef? = null
        private val missingSortCmp: Int = if (sortMissingLast) 1 else -1

        @Throws(IOException::class)
        private fun getValueForDoc(doc: Int): BytesRef? {
            if (docTerms!!.advanceExact(doc)) {
                return docTerms!!.binaryValue()
            } else {
                return null
            }
        }

        override fun compare(slot1: Int, slot2: Int): Int {
            val val1: BytesRef = values[slot1]!!
            val val2: BytesRef = values[slot2]!!
            return compareValues(val1, val2)
        }

        @Throws(IOException::class)
        override fun compareBottom(doc: Int): Int {
            val comparableBytes: BytesRef? = getValueForDoc(doc)
            return compareValues(bottom, comparableBytes)
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            val comparableBytes: BytesRef? = getValueForDoc(doc)
            if (comparableBytes == null) {
                values[slot] = null
            } else {
                if (tempBRs[slot] == null) {
                    tempBRs[slot] = BytesRefBuilder()
                }
                tempBRs[slot]!!.copyBytes(comparableBytes)
                values[slot] = tempBRs[slot]!!.get()
            }
        }

        /** Retrieves the BinaryDocValues for the field in this segment  */
        @Throws(IOException::class)
        protected fun getBinaryDocValues(context: LeafReaderContext, field: String): BinaryDocValues {
            return DocValues.getBinary(context.reader(), field)
        }

        @Throws(IOException::class)
        override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
            docTerms = getBinaryDocValues(context, field)
            return this
        }

        override fun setBottom(bottom: Int) {
            this.bottom = values[bottom]!!
        }

        override fun setTopValue(value: BytesRef) {
            // null is fine: it means the last doc of the prior
            // search was missing this value
            topValue = value
        }

        override fun value(slot: Int): BytesRef {
            return values[slot]!!
        }

        override fun compareValues(val1: BytesRef, val2: BytesRef): Int {
            // missing always sorts first:
            if (val1 == null) {
                if (val2 == null) {
                    return 0
                }
                return missingSortCmp
            } else if (val2 == null) {
                return -missingSortCmp
            }
            return val1.compareTo(val2)
        }

        @Throws(IOException::class)
        override fun compareTop(doc: Int): Int {
            return compareValues(topValue, getValueForDoc(doc))
        }

        override fun setScorer(scorer: Scorable) {}
    }
}
