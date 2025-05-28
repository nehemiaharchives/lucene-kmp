package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator


/**
 * Skipper for [DocValues].
 *
 *
 * A skipper has a position that can only be advanced via [.advance]. The next advance
 * position must be greater than [.maxDocID] at level 0. A skipper's position, along with
 * a `level`, determines the interval at which the skipper is currently situated.
 */
abstract class DocValuesSkipper {
    /**
     * Advance this skipper so that all levels contain the next document on or after `target`.
     *
     *
     * **NOTE**: The behavior is undefined if `target` is less than or equal to `maxDocID(0)`.
     *
     *
     * **NOTE**: `minDocID(0)` may return a doc ID that is greater than `target` if
     * the target document doesn't have a value.
     */
    @Throws(IOException::class)
    abstract fun advance(target: Int)

    /** Return the number of levels. This number may change when moving to a different interval.  */
    abstract fun numLevels(): Int

    /**
     * Return the minimum doc ID of the interval on the given level, inclusive. This returns `-1` if [.advance] has not been called yet and [DocIdSetIterator.NO_MORE_DOCS]
     * if the iterator is exhausted. This method is non-increasing when `level` increases. Said
     * otherwise `minDocID(level+1) <= minDocId(level)`.
     */
    abstract fun minDocID(level: Int): Int

    /**
     * Return the maximum doc ID of the interval on the given level, inclusive. This returns `-1` if [.advance] has not been called yet and [DocIdSetIterator.NO_MORE_DOCS]
     * if the iterator is exhausted. This method is non-decreasing when `level` decreases. Said
     * otherwise `maxDocID(level+1) >= maxDocId(level)`.
     */
    abstract fun maxDocID(level: Int): Int

    /**
     * Return the minimum value of the interval at the given level, inclusive.
     *
     *
     * **NOTE**: It is only guaranteed that values in this interval are greater than or equal
     * the returned value. There is no guarantee that one document actually has this value.
     */
    abstract fun minValue(level: Int): Long

    /**
     * Return the maximum value of the interval at the given level, inclusive.
     *
     *
     * **NOTE**: It is only guaranteed that values in this interval are less than or equal the
     * returned value. There is no guarantee that one document actually has this value.
     */
    abstract fun maxValue(level: Int): Long

    /**
     * Return the number of documents that have a value in the interval associated with the given
     * level.
     */
    abstract fun docCount(level: Int): Int

    /**
     * Return the global minimum value.
     *
     *
     * **NOTE**: It is only guaranteed that values are greater than or equal the returned value.
     * There is no guarantee that one document actually has this value.
     */
    abstract fun minValue(): Long

    /**
     * Return the global maximum value.
     *
     *
     * **NOTE**: It is only guaranteed that values are less than or equal the returned value.
     * There is no guarantee that one document actually has this value.
     */
    abstract fun maxValue(): Long

    /** Return the global number of documents with a value for the field.  */
    abstract fun docCount(): Int

    /**
     * Advance this skipper so that all levels intersects the range given by `minValue` and
     * `maxValue`. If there are no intersecting levels, the skipper is exhausted.
     */
    @Throws(IOException::class)
    fun advance(minValue: Long, maxValue: Long) {
        if (minDocID(0) == -1) {
            // #advance has not been called yet
            advance(0)
        }
        // check if the current interval intersects the provided range
        while (minDocID(0) != DocIdSetIterator.NO_MORE_DOCS
            && ((minValue(0) > maxValue || maxValue(0) < minValue))
        ) {
            var maxDocID = maxDocID(0)
            var nextLevel = 1
            // check if the next levels intersects to skip as many docs as possible
            while (nextLevel < numLevels()
                && (minValue(nextLevel) > maxValue || maxValue(nextLevel) < minValue)
            ) {
                maxDocID = maxDocID(nextLevel)
                nextLevel++
            }
            advance(maxDocID + 1)
        }
    }
}
