package org.gnit.lucenekmp.search

import okio.IOException

/**
 * A manager of collectors. This class is useful to parallelize execution of search requests and has
 * two main methods:
 *
 *
 *  * [.newCollector] which must return a NEW collector which will be used to collect a
 * certain set of leaves.
 *  * [.reduce] which will be used to reduce the results of individual
 * collections into a meaningful result. This method is only called after all leaves have been
 * fully collected.
 *
 *
 *
 * **Note:** Multiple [LeafCollector]s may be requested for the same [ ] via [Collector.getLeafCollector] across the different
 * [Collector]s returned by [.newCollector]. Any computation or logic that needs to
 * happen once per segment requires specific handling in the collector manager implementation,
 * because the collection of an entire segment may be split across threads.
 *
 * @see IndexSearcher.search
 * @lucene.experimental
 */
interface CollectorManager<C : Collector, T> {
    /** Return a new [Collector]. This must return a different instance on each call.  */
    @Throws(IOException::class)
    fun newCollector(): C

    /**
     * Reduce the results of individual collectors into a meaningful result. For instance a [ ] would compute the [top docs][TopDocsCollector.topDocs] of each
     * collector and then merge them using [TopDocs.merge]. This method must be
     * called after collection is finished on all provided collectors.
     */
    @Throws(IOException::class)
    fun reduce(collectors: MutableCollection<C>): T?
}
