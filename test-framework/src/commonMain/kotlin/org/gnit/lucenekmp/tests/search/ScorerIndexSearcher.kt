package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.search.BulkScorer
import org.gnit.lucenekmp.search.Collector
import org.gnit.lucenekmp.search.FilterWeight
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight

/** An [IndexSearcher] that always uses the [Scorer] API, never [BulkScorer]. */
class ScorerIndexSearcher : IndexSearcher {

    /**
     * Creates a searcher searching the provided index. Search on individual segments will be run in
     * the provided [Executor].
     *
     * @see IndexSearcher.IndexSearcher
     */
    constructor(r: IndexReader, executor: Executor) : super(r, executor)

    /**
     * Creates a searcher searching the provided index.
     *
     * @see IndexSearcher.IndexSearcher
     */
    constructor(r: IndexReader) : super(r)

    @Throws(IOException::class)
    override fun searchLeaf(
        ctx: LeafReaderContext,
        minDocId: Int,
        maxDocId: Int,
        weight: Weight,
        collector: Collector
    ) {
        val filterWeight: Weight =
            object : FilterWeight(weight) {
                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    val `in`: ScorerSupplier = super.scorerSupplier(context) ?: return null
                    return object : ScorerSupplier() {
                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer? {
                            return `in`.get(leadCost)
                        }

                        @Throws(IOException::class)
                        override fun bulkScorer(): BulkScorer? {
                            // Don't delegate to `in` to make sure we get a DefaultBulkScorer
                            return super.bulkScorer()
                        }

                        override fun cost(): Long {
                            return `in`.cost()
                        }
                    }
                }
            }

        super.searchLeaf(ctx, minDocId, maxDocId, filterWeight, collector)
    }
}
