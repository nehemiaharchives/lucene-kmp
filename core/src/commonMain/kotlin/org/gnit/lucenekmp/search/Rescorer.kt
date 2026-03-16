package org.gnit.lucenekmp.search

import okio.IOException

/**
 * Re-scores the topN results ([TopDocs]) from an original query. See [QueryRescorer]
 * for an actual implementation. Typically, you run a low-cost first-pass query across the entire
 * index, collecting the top few hundred hits perhaps, and then use this class to mix in a more
 * costly second pass scoring.
 *
 *
 * See [QueryRescorer.rescore] for a simple static
 * method to call to rescore using a 2nd pass [Query].
 *
 * @lucene.experimental
 */
abstract class Rescorer {
    /**
     * Rescore an initial first-pass [TopDocs].
     *
     * @param searcher [IndexSearcher] used to produce the first pass topDocs
     * @param firstPassTopDocs Hits from the first pass search. It's very important that these hits
     * were produced by the provided searcher; otherwise the doc IDs will not match!
     * @param topN How many re-scored hits to return
     */
    @Throws(IOException::class)
    abstract fun rescore(searcher: IndexSearcher, firstPassTopDocs: TopDocs, topN: Int): TopDocs

    /** Explains how the score for the specified document was computed. */
    @Throws(IOException::class)
    abstract fun explain(searcher: IndexSearcher, firstPassExplanation: Explanation, docID: Int): Explanation
}
