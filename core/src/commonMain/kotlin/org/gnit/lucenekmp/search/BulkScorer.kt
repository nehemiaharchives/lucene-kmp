package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.Bits


/**
 * This class is used to score a range of documents at once, and is returned by [ ][Weight.bulkScorer]. Only queries that have a more optimized means of scoring across a range of
 * documents need to override this. Otherwise, a default implementation is wrapped around the [ ] returned by [Weight.scorer].
 */
abstract class BulkScorer {
    /**
     * Collects matching documents in a range and return an estimation of the next matching document
     * which is on or after `max`.
     *
     *
     * The return value must be:
     *
     *
     *  * &gt;= `max`,
     *  * [DocIdSetIterator.NO_MORE_DOCS] if there are no more matches,
     *  * &lt;= the first matching document that is &gt;= `max` otherwise.
     *
     *
     *
     * `min` is the minimum document to be considered for matching. All documents strictly
     * before this value must be ignored.
     *
     *
     * Although `max` would be a legal return value for this method, higher values might help
     * callers skip more efficiently over non-matching portions of the docID space.
     *
     *
     * For instance, a [Scorer]-based implementation could look like below:
     *
     * <pre class="prettyprint">
     * private final Scorer scorer; // set via constructor
     *
     * public int score(LeafCollector collector, Bits acceptDocs, int min, int max) throws IOException {
     * collector.setScorer(scorer);
     * int doc = scorer.docID();
     * if (doc &lt; min) {
     * doc = scorer.advance(min);
     * }
     * while (doc &lt; max) {
     * if (acceptDocs == null || acceptDocs.get(doc)) {
     * collector.collect(doc);
     * }
     * doc = scorer.nextDoc();
     * }
     * return doc;
     * }
    </pre> *
     *
     * @param collector The collector to which all matching documents are passed.
     * @param acceptDocs [Bits] that represents the allowed documents to match, or `null`
     * if they are all allowed to match.
     * @param min Score starting at, including, this document
     * @param max Score up to, but not including, this doc
     * @return an under-estimation of the next matching doc after max
     */
    @Throws(IOException::class)
    abstract fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int

    /** Same as [DocIdSetIterator.cost] for bulk scorers.  */
    abstract fun cost(): Long
}
