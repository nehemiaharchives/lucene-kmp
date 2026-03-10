package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.FilterWeight
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.Weight
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Tests that the [BaseExplanationTestCase] helper code, as well as
 * [CheckHits.checkNoMatchExplanations] are checking what they are suppose to.
 */
class TestBaseExplanationTestCase : BaseExplanationTestCase() {

    @Test
    @Throws(Exception::class)
    fun testQueryNoMatchWhenExpected() {
        assertFailsWith<AssertionError> {
            qtest(TermQuery(Term(FIELD, "BOGUS")), intArrayOf(3 /* none */))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testQueryMatchWhenNotExpected() {
        assertFailsWith<AssertionError> {
            qtest(TermQuery(Term(FIELD, "w1")), intArrayOf(0, 1 /*, 2, 3 */))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIncorrectExplainScores() {
        // sanity check what a real TermQuery matches
        qtest(TermQuery(Term(FIELD, "zz")), intArrayOf(1, 3))

        // ensure when the Explanations are broken, we get an error about those matches
        assertFailsWith<AssertionError> {
            qtest(BrokenExplainTermQuery(Term(FIELD, "zz"), false, true), intArrayOf(1, 3))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIncorrectExplainMatches() {
        // sanity check what a real TermQuery matches
        qtest(TermQuery(Term(FIELD, "zz")), intArrayOf(1, 3))

        // ensure when the Explanations are broken, we get an error about the non matches
        assertFailsWith<AssertionError> {
            CheckHits.checkNoMatchExplanations(
                BrokenExplainTermQuery(Term(FIELD, "zz"), true, false),
                FIELD,
                searcher!!,
                intArrayOf(1, 3),
            )
        }
    }

    class BrokenExplainTermQuery(
        t: Term,
        val toggleExplainMatch: Boolean,
        val breakExplainScores: Boolean,
    ) : TermQuery(t) {

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return BrokenExplainWeight(this, super.createWeight(searcher, scoreMode, boost))
        }
    }

    class BrokenExplainWeight(q: BrokenExplainTermQuery, `in`: Weight) : FilterWeight(q, `in`) {

        @Throws(IOException::class)
        override fun explain(context: LeafReaderContext, doc: Int): Explanation {
            val q = query as BrokenExplainTermQuery
            var result = `in`.explain(context, doc)
            if (result.isMatch) {
                if (q.breakExplainScores) {
                    result =
                        Explanation.match(
                            -1f * result.value.toDouble(),
                            "Broken Explanation Score",
                            result,
                        )
                }
                if (q.toggleExplainMatch) {
                    result = Explanation.noMatch("Broken Explanation Matching", result)
                }
            } else {
                if (q.toggleExplainMatch) {
                    result = Explanation.match(-42.0f, "Broken Explanation Matching", result)
                }
            }
            return result
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val scorer = `in`.scorer(context) ?: return null
            return DefaultScorerSupplier(scorer)
        }
    }
}
