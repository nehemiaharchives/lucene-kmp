package org.gnit.lucenekmp.search

import okio.IOException


/**
 * Expert: comparator that gets instantiated on each leaf from a top-level [FieldComparator]
 * instance.
 *
 *
 * A leaf comparator must define these functions:
 *
 *
 *  * [.setBottom] This method is called by [FieldValueHitQueue] to notify the
 * FieldComparator of the current weakest ("bottom") slot. Note that this slot may not hold
 * the weakest value according to your comparator, in cases where your comparator is not the
 * primary one (ie, is only used to break ties from the comparators before it).
 *  * [.compareBottom] Compare a new hit (docID) against the "weakest" (bottom) entry in
 * the queue.
 *  * [.compareTop] Compare a new hit (docID) against the top value previously set by a
 * call to [FieldComparator.setTopValue].
 *  * [.copy] Installs a new hit into the priority queue. The [FieldValueHitQueue]
 * calls this method when a new hit is competitive.
 *
 *
 * @see FieldComparator
 *
 * @lucene.experimental
 */
interface LeafFieldComparator {
    /**
     * Set the bottom slot, ie the "weakest" (sorted last) entry in the queue. When [ ][.compareBottom] is called, you should compare against this slot. This will always be called
     * before [.compareBottom].
     *
     * @param slot the currently weakest (sorted last) slot in the queue
     */
    @Throws(IOException::class)
    fun setBottom(slot: Int)

    /**
     * Compare the bottom of the queue with this doc. This will only invoked after setBottom has been
     * called. This should return the same result as [FieldComparator.compare]} as if
     * bottom were slot1 and the new document were slot 2.
     *
     *
     * For a search that hits many results, this method will be the hotspot (invoked by far the
     * most frequently).
     *
     * @param doc that was hit
     * @return any `N < 0` if the doc's value is sorted after the bottom entry (not
     * competitive), any `N > 0` if the doc's value is sorted before the bottom entry and
     * `0` if they are equal.
     */
    @Throws(IOException::class)
    fun compareBottom(doc: Int): Int

    /**
     * Compare the top value with this doc. This will only invoked after setTopValue has been called.
     * This should return the same result as [FieldComparator.compare]} as if topValue
     * were slot1 and the new document were slot 2. This is only called for searches that use
     * searchAfter (deep paging).
     *
     * @param doc that was hit
     * @return any `N < 0` if the doc's value is sorted after the top entry (not competitive),
     * any `N > 0` if the doc's value is sorted before the top entry and `0` if they
     * are equal.
     */
    @Throws(IOException::class)
    fun compareTop(doc: Int): Int

    /**
     * This method is called when a new hit is competitive. You should copy any state associated with
     * this document that will be required for future comparisons, into the specified slot.
     *
     * @param slot which slot to copy the hit to
     * @param doc docID relative to current reader
     */
    @Throws(IOException::class)
    fun copy(slot: Int, doc: Int)

    /**
     * Sets the Scorer to use in case a document's score is needed.
     *
     * @param scorer Scorer instance that you should use to obtain the current hit's score, if
     * necessary.
     */
    @Throws(IOException::class)
    fun setScorer(scorer: Scorable)

    /**
     * Returns a competitive iterator
     *
     * @return an iterator over competitive docs that are stronger than already collected docs or
     * `null` if such an iterator is not available for the current comparator or segment.
     */
    @Throws(IOException::class)
    fun competitiveIterator(): DocIdSetIterator? {
        return null
    }

    /**
     * Informs this leaf comparator that hits threshold is reached. This method is called from a
     * collector when hits threshold is reached.
     */
    @Throws(IOException::class)
    fun setHitsThresholdReached() {
    }
}
