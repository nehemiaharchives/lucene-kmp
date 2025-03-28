package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext


/**
 * Expert: Collectors are primarily meant to be used to gather raw results from a search, and
 * implement sorting or custom result filtering, collation, etc.
 *
 *
 * Lucene's core collectors are derived from [Collector] and [SimpleCollector].
 * Likely your application can use one of these classes, or subclass [TopDocsCollector],
 * instead of implementing Collector directly:
 *
 *
 *  * [TopDocsCollector] is an abstract base class that assumes you will retrieve the top N
 * docs, according to some criteria, after collection is done.
 *  * [TopScoreDocCollector] is a concrete subclass [TopDocsCollector] and sorts
 * according to score + docID. This is used internally by the [IndexSearcher] search
 * methods that do not take an explicit [Sort]. It is likely the most frequently used
 * collector.
 *  * [TopFieldCollector] subclasses [TopDocsCollector] and sorts according to a
 * specified [Sort] object (sort by field). This is used internally by the [       ] search methods that take an explicit [Sort].
 *  * [PositiveScoresOnlyCollector] wraps any other Collector and prevents collection of
 * hits whose score is &lt;= 0.0
 *
 *
 * @lucene.experimental
 */
interface Collector {
    /**
     * Create a new [collector][LeafCollector] to collect the given context.
     *
     * @param context next atomic reader context
     */
    @Throws(IOException::class)
    fun getLeafCollector(context: LeafReaderContext): LeafCollector

    /** Indicates what features are required from the scorer.  */
    fun scoreMode(): ScoreMode

    /**
     * Set the [Weight] that will be used to produce scorers that will feed [ ]s. This is typically useful to have access to [Weight.count] from [ ][Collector.getLeafCollector].
     */
    fun setWeight(weight: Weight) {}
}
