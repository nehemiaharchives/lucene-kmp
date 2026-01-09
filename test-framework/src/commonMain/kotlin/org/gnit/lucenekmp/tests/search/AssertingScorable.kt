package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.FilterScorable
import org.gnit.lucenekmp.search.FilterScorer
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.Scorer

/** Wraps another Scorable and asserts that scores are reasonable and only called when positioned */
class AssertingScorable(`in`: Scorable) : FilterScorable(`in`) {

    @Throws(IOException::class)
    override fun score(): Float {
        val score = `in`.score()
        // Note: score >= 0 returns false for NaN
        assert(score >= 0f) { "score=$score for in=$`in`" }
        return score
    }

    override var minCompetitiveScore: Float
        get() = `in`.minCompetitiveScore
        set(minScore) {
            `in`.minCompetitiveScore = minScore
        }

    companion object {
        fun wrap(`in`: Scorable): Scorable {
            if (`in` is AssertingScorable) {
                return `in`
            }
            // If `in` is Scorer, we need to wrap it as a Scorer instead of Scorable because
            // NumericComparator uses the iterator cost of a Scorer in sort optimization.
            return if (`in` is Scorer) {
                WrappedScorer(`in`)
            } else {
                AssertingScorable(`in`)
            }
        }

        fun unwrap(`in`: Scorable): Scorable {
            var current = `in`
            while (true) {
                current =
                    when (current) {
                        is AssertingScorable -> current.`in`
                        is AssertingScorer -> current.`in`
                        is WrappedScorer -> current.unwrap()
                        else -> return current
                    }
            }
        }
    }

    private class WrappedScorer(`in`: Scorer) : FilterScorer(`in`) {
        @Throws(IOException::class)
        override fun score(): Float {
            return AssertingScorable(`in`).score()
        }

        override var minCompetitiveScore: Float
            get() = `in`.minCompetitiveScore
            set(minScore) {
                `in`.minCompetitiveScore = minScore
            }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            return `in`.getMaxScore(upTo)
        }
    }
}
