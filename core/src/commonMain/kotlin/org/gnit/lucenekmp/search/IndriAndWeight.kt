package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.assert

/** The Weight for IndriAndQuery, used to normalize, score and explain these queries. */
class IndriAndWeight(
    private val indriAndQuery: IndriAndQuery,
    searcher: IndexSearcher,
    private val scoreMode: ScoreMode,
    private val boost: Float
) : Weight(indriAndQuery) {
    private val weights: ArrayList<Weight> = ArrayList()

    init {
        for (c in indriAndQuery) {
            val w = searcher.createWeight(c.query, scoreMode, 1.0f)
            weights.add(w)
        }
    }

    private fun getScorer(context: LeafReaderContext): Scorer? {
        val subScorers = ArrayList<Scorer>()

        for (w in weights) {
            val scorer = w.scorer(context)
            if (scorer != null) {
                subScorers.add(scorer)
            }
        }

        if (subScorers.isEmpty()) {
            return null
        }
        var scorer: Scorer = subScorers[0]
        if (subScorers.size > 1) {
            scorer = IndriAndScorer(subScorers, scoreMode, boost)
        }
        return scorer
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
        for (w in weights) {
            if (w.isCacheable(ctx) == false) return false
        }
        return true
    }

    override fun explain(context: LeafReaderContext, doc: Int): Explanation {
        val subs = ArrayList<Explanation>()
        var fail = false
        val cIter = indriAndQuery.iterator()
        for (w in weights) {
            val c = cIter.next()
            val e = w.explain(context, doc)
            if (e.isMatch) {
                subs.add(e)
            } else if (c.isRequired) {
                subs.add(
                    Explanation.noMatch("no match on required clause (${c.query.toString()})", e)
                )
                fail = true
            }
        }
        if (fail) {
            return Explanation.noMatch(
                "Failure to meet condition(s) of required/prohibited clause(s)",
                subs
            )
        } else {
            val scorer = scorer(context)
            if (scorer != null) {
                val advanced = scorer.iterator().advance(doc)
                assert(advanced == doc)
                return Explanation.match(scorer.score(), "sum of:", subs)
            } else {
                return Explanation.noMatch(
                    "Failure to meet condition(s) of required/prohibited clause(s)",
                    subs
                )
            }
        }
    }

    override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        val scorer = getScorer(context) ?: return null
        return DefaultScorerSupplier(scorer)
    }
}
