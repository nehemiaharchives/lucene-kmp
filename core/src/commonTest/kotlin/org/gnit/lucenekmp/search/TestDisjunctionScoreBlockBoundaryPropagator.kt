package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DisjunctionScoreBlockBoundaryPropagator
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDisjunctionScoreBlockBoundaryPropagator : LuceneTestCase() {

    private class FakeScorer(
        private val boundary: Int,
        private val maxScore: Float
    ) : Scorer() {

        override fun docID(): Int {
            return 0
        }

        override fun score(): Float {
            throw UnsupportedOperationException()
        }

        override fun iterator(): DocIdSetIterator {
            throw UnsupportedOperationException()
        }

        override fun getMaxScore(upTo: Int): Float {
            return maxScore
        }

        override fun advanceShallow(target: Int): Int {
            require(target <= boundary)
            return boundary
        }
    }

    @Test
    fun testBasics() {
        val scorer1: Scorer = FakeScorer(20, 0.5f)
        val scorer2: Scorer = FakeScorer(50, 1.5f)
        val scorer3: Scorer = FakeScorer(30, 2f)
        val scorer4: Scorer = FakeScorer(80, 3f)
        val scorers = mutableListOf(scorer1, scorer2, scorer3, scorer4)
        scorers.shuffle(random())
        val propagator = DisjunctionScoreBlockBoundaryPropagator(scorers)
        assertEquals(20, propagator.advanceShallow(0))

        propagator.setMinCompetitiveScore(0.2f)
        assertEquals(20, propagator.advanceShallow(0))

        propagator.setMinCompetitiveScore(0.7f)
        assertEquals(30, propagator.advanceShallow(0))

        propagator.setMinCompetitiveScore(1.2f)
        assertEquals(30, propagator.advanceShallow(0))

        propagator.setMinCompetitiveScore(1.7f)
        assertEquals(30, propagator.advanceShallow(0))

        propagator.setMinCompetitiveScore(2.2f)
        assertEquals(80, propagator.advanceShallow(0))

        propagator.setMinCompetitiveScore(5f)
        assertEquals(80, propagator.advanceShallow(0))
    }
}

