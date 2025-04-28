package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.util.Bits


/**
 * A query that wraps another query and simply returns a constant score equal to 1 for every
 * document that matches the query. It therefore simply strips of all scores and always returns 1.
 */
open class ConstantScoreQuery(val query: Query) : Query() {
    /** Returns the encapsulated query.  */

    @Throws(IOException::class)
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        var rewritten = query.rewrite(indexSearcher)

        // Do some extra simplifications that are legal since scores are not needed on the wrapped
        // query.
        if (rewritten is BoostQuery) {
            rewritten = rewritten.query
        } else if (rewritten is ConstantScoreQuery) {
            rewritten = rewritten.query
        } else if (rewritten is BooleanQuery) {
            rewritten = rewritten.rewriteNoScoring()
        }

        if (rewritten::class == MatchNoDocsQuery::class) {
            // bubble up MatchNoDocsQuery
            return rewritten
        }

        if (rewritten !== query) {
            return ConstantScoreQuery(rewritten)
        }

        if (rewritten::class == ConstantScoreQuery::class) {
            return rewritten
        }

        if (rewritten::class == BoostQuery::class) {
            return ConstantScoreQuery((rewritten as BoostQuery).query)
        }

        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        query.visit(visitor.getSubVisitor(BooleanClause.Occur.FILTER, this))
    }

    /**
     * We return this as our [BulkScorer] so that if the CSQ wraps a query with its own
     * optimized top-level scorer (e.g. BooleanScorer) we can use that top-level scorer.
     */
    protected class ConstantBulkScorer(val bulkScorer: BulkScorer, val weight: Weight, val theScore: Float) : BulkScorer() {

        @Throws(IOException::class)
        override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
            return bulkScorer.score(wrapCollector(collector), acceptDocs, min, max)
        }

        private fun wrapCollector(collector: LeafCollector): LeafCollector {
            return object : FilterLeafCollector(collector) {
                @Throws(IOException::class)
                override fun setScorer(scorer: Scorable) {
                    // we must wrap again here, but using the scorer passed in as parameter:
                    `in`.setScorer(
                        object : FilterScorable(scorer) {
                            override fun score(): Float {
                                return theScore
                            }
                        })
                }
            }
        }

        override fun cost(): Long {
            return bulkScorer.cost()
        }
    }

    @Throws(IOException::class)
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        // If the score mode is exhaustive then pass COMPLETE_NO_SCORES, otherwise pass TOP_DOCS to make
        // sure to not disable any of the dynamic pruning optimizations for queries sorted by field or
        // top scores.
        val innerScoreMode: ScoreMode
        if (scoreMode.isExhaustive()) {
            innerScoreMode = ScoreMode.COMPLETE_NO_SCORES
        } else {
            innerScoreMode = ScoreMode.TOP_DOCS
        }
        val innerWeight = searcher.createWeight(query, innerScoreMode, 1f)
        if (scoreMode.needsScores()) {
            return object : ConstantScoreWeight(this, boost) {
                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    val innerScorerSupplier: ScorerSupplier? = innerWeight.scorerSupplier(context)
                    if (innerScorerSupplier == null) {
                        return null
                    }
                    return object : ScorerSupplier() {
                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            val innerScorer: Scorer = innerScorerSupplier.get(leadCost)
                            val twoPhaseIterator = innerScorer.twoPhaseIterator()
                            if (twoPhaseIterator == null) {
                                return ConstantScoreScorer(score(), scoreMode, innerScorer.iterator())
                            } else {
                                return ConstantScoreScorer(score(), scoreMode, twoPhaseIterator)
                            }
                        }

                        @Throws(IOException::class)
                        override fun bulkScorer(): BulkScorer? {
                            if (!scoreMode.isExhaustive()) {
                                return super.bulkScorer()
                            }
                            val innerScorer: BulkScorer? = innerScorerSupplier.bulkScorer()
                            if (innerScorer == null) {
                                return null
                            }
                            return ConstantBulkScorer(innerScorer, innerWeight, score())
                        }

                        override fun cost(): Long {
                            return innerScorerSupplier.cost()
                        }
                    }
                }

                @Throws(IOException::class)
                override fun matches(context: LeafReaderContext, doc: Int): Matches? {
                    return innerWeight.matches(context, doc)
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return innerWeight.isCacheable(ctx)
                }

                @Throws(IOException::class)
                override fun count(context: LeafReaderContext): Int {
                    return innerWeight.count(context)
                }
            }
        } else {
            return innerWeight
        }
    }

    override fun toString(field: String?): String {
        return "ConstantScore(" + query.toString(field) + ')'
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && query == (other as ConstantScoreQuery).query
    }

    override fun hashCode(): Int {
        return 31 * classHash() + query.hashCode()
    }
}
