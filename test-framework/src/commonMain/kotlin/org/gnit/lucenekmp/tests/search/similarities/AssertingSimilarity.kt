package org.gnit.lucenekmp.tests.search.similarities

import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.Similarity

/** wraps a similarity with checks for testing  */
class AssertingSimilarity(private val delegate: Similarity) : Similarity() {

    override fun computeNorm(state: FieldInvertState): Long {
        //checkNotNull(state)
        assert(state.length > 0)
        assert(state.position >= 0)
        assert(state.offset >= 0)
        assert(
            state.maxTermFrequency >= 0 // TODO: seems to be 0 for omitTFAP?
        )
        assert(state.maxTermFrequency <= state.length)
        assert(state.numOverlap >= 0)
        assert(state.numOverlap < state.length)
        assert(state.uniqueTermCount > 0)
        assert(state.uniqueTermCount <= state.length)
        val norm: Long = delegate.computeNorm(state)
        assert(norm != 0L)
        return norm
    }

    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        assert(boost >= 0)
        //checkNotNull(collectionStats)
        assert(termStats.isNotEmpty())
        for (term in termStats) {
            checkNotNull(term)
        }
        // TODO: check that TermStats is in bounds with respect to collection? e.g. docFreq <= maxDoc
        val scorer: SimScorer =
            checkNotNull(delegate.scorer(boost, collectionStats, *termStats))
        return AssertingSimScorer(scorer, boost)
    }

    internal class AssertingSimScorer(
        val delegate: SimScorer,
        val boost: Float
    ) : SimScorer() {

        override fun score(freq: Float, norm: Long): Float {
            // freq in bounds
            assert(Float.isFinite(freq))
            assert(freq > 0)
            // result in bounds
            val score: Float = delegate.score(freq, norm)
            assert(Float.isFinite(score))
            assert(score <= delegate.score(freq, 1))
            assert(score >= 0)
            return score
        }

        override fun explain(
            freq: Explanation,
            norm: Long
        ): Explanation {
            // freq in bounds
            //checkNotNull(freq)
            assert(Float.isFinite(freq.value.toFloat()))
            // result in bounds
            val explanation: Explanation =
                checkNotNull(delegate.explain(freq, norm))
            assert(Float.isFinite(explanation.value.toFloat()))
            // result matches score exactly
            assert(
                explanation.value.toFloat()
                        == delegate.score(freq.value.toFloat(), norm)
            )
            return explanation
        }
    }

    override fun toString(): String {
        return "Asserting($delegate)"
    }
}
