package org.gnit.lucenekmp.index


/**
 * Query timeout abstraction that controls whether a query should continue or be stopped. Can be set
 * to the searcher through [org.apache.lucene.search.IndexSearcher.setTimeout],
 * in which case bulk scoring will be time-bound. Can also be used in combination with [ ].
 */
fun interface QueryTimeout {
    /**
     * Called to determine whether to stop processing a query
     *
     * @return true if the query should stop, false otherwise
     */
    fun shouldExit(): Boolean
}
