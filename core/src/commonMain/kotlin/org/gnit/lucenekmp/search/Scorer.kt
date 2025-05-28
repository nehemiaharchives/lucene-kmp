package org.gnit.lucenekmp.search

import okio.IOException


/**
 * Expert: Common scoring functionality for different types of queries.
 *
 *
 * A `Scorer` exposes an [.iterator] over documents matching a query in
 * increasing order of doc id.
 */
abstract class Scorer : Scorable() {
    /** Returns the doc ID that is currently being scored.  */
    abstract fun docID(): Int

    /**
     * Return a [DocIdSetIterator] over matching documents.
     *
     *
     * The returned iterator will either be positioned on `-1` if no documents have been
     * scored yet, [DocIdSetIterator.NO_MORE_DOCS] if all documents have been scored already, or
     * the last document id that has been scored otherwise.
     *
     *
     * The returned iterator is a view: calling this method several times will return iterators
     * that have the same state.
     */
    abstract fun iterator(): DocIdSetIterator

    /**
     * Optional method: Return a [TwoPhaseIterator] view of this [Scorer]. A return value
     * of `null` indicates that two-phase iteration is not supported.
     *
     *
     * Note that the returned [TwoPhaseIterator]'s [ approximation][TwoPhaseIterator.approximation] must advance synchronously with the [.iterator]: advancing the
     * approximation must advance the iterator and vice-versa.
     *
     *
     * Implementing this method is typically useful on [Scorer]s that have a high
     * per-document overhead in order to confirm matches.
     *
     *
     * The default implementation returns `null`.
     */
    open fun twoPhaseIterator(): TwoPhaseIterator? {
        return null
    }

    /**
     * Advance to the block of documents that contains `target` in order to get scoring
     * information about this block. This method is implicitly called by [ ][DocIdSetIterator.advance] and [DocIdSetIterator.nextDoc] on the returned doc ID.
     * Calling this method doesn't modify the current [DocIdSetIterator.docID]. It returns a
     * number that is greater than or equal to all documents contained in the current block, but less
     * than any doc IDS of the next block. `target` must be &gt;= [.docID] as well as
     * all targets that have been passed to [.advanceShallow] so far.
     */
    @Throws(IOException::class)
    open fun advanceShallow(target: Int): Int {
        return DocIdSetIterator.NO_MORE_DOCS
    }

    /**
     * Return the maximum score that documents between the last `target` that this iterator was
     * [shallow-advanced][.advanceShallow] to included and `upTo` included.
     */
    @Throws(IOException::class)
    abstract fun getMaxScore(upTo: Int): Float
}
