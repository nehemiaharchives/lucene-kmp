package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestBoolean2ScorerSupplier : LuceneTestCase() {
    private class FakeWeight : Weight(MatchNoDocsQuery()) {
        @Throws(IOException::class)
        override fun explain(context: LeafReaderContext, doc: Int): Explanation {
            return Explanation.noMatch("")
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            return null
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return false
        }
    }

    private class FakeScorer(cost: Long) : Scorer() {
        private val it: DocIdSetIterator = DocIdSetIterator.all(Math.toIntExact(cost))

        override fun docID(): Int {
            return it.docID()
        }

        @Throws(IOException::class)
        override fun score(): Float {
            return 1f
        }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            return 1f
        }

        override fun iterator(): DocIdSetIterator {
            return it
        }

        override fun toString(): String {
            return "FakeScorer(cost=${it.cost()})"
        }
    }

    private class FakeScorerSupplier : ScorerSupplier {
        private val cost: Long
        private val leadCost: Long?
        var topLevelScoringClause = false

        constructor(cost: Long) {
            this.cost = cost
            this.leadCost = null
        }

        constructor(cost: Long, leadCost: Long) {
            this.cost = cost
            this.leadCost = leadCost
        }

        @Throws(IOException::class)
        override fun get(leadCost: Long): Scorer {
            if (this.leadCost != null) {
                assertEquals(this.leadCost, leadCost, this.toString())
            }
            return FakeScorer(cost)
        }

        override fun cost(): Long {
            return cost
        }

        override fun toString(): String {
            return "FakeLazyScorer(cost=$cost,leadCost=$leadCost)"
        }

        override fun setTopLevelScoringClause() {
            topLevelScoringClause = true
        }
    }

    @Test
    fun testConjunctionCost() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        subs[RandomPicks.randomFrom(random(), arrayOf(Occur.FILTER, Occur.MUST))]!!.add(FakeScorerSupplier(42))
        assertEquals(
            42,
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                0,
                100
            ).cost()
        )

        subs[RandomPicks.randomFrom(random(), arrayOf(Occur.FILTER, Occur.MUST))]!!.add(FakeScorerSupplier(12))
        assertEquals(
            12,
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                0,
                100
            ).cost()
        )

        subs[RandomPicks.randomFrom(random(), arrayOf(Occur.FILTER, Occur.MUST))]!!.add(FakeScorerSupplier(20))
        assertEquals(
            12,
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                0,
                100
            ).cost()
        )
    }

    @Test
    @Throws(IOException::class)
    fun testDisjunctionCost() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(42))
        var s: ScorerSupplier =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                0,
                100
            )
        assertEquals(42, s.cost())
        assertEquals(42, s.get(random().nextInt(100).toLong())!!.iterator().cost())

        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(12))
        s =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                0,
                100
            )
        assertEquals(42 + 12, s.cost())
        assertEquals(42 + 12, s.get(random().nextInt(100).toLong())!!.iterator().cost())

        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(20))
        s =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                0,
                100
            )
        assertEquals(42 + 12 + 20, s.cost())
        assertEquals(42 + 12 + 20, s.get(random().nextInt(100).toLong())!!.iterator().cost())
    }

    @Test
    @Throws(IOException::class)
    fun testDisjunctionWithMinShouldMatchCost() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(42))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(12))
        var s: ScorerSupplier =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                1,
                100
            )
        assertEquals(42 + 12, s.cost())
        assertEquals(42 + 12, s.get(random().nextInt(100).toLong())!!.iterator().cost())

        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(20))
        s =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                1,
                100
            )
        assertEquals(42 + 12 + 20, s.cost())
        assertEquals(42 + 12 + 20, s.get(random().nextInt(100).toLong())!!.iterator().cost())
        s =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                2,
                100
            )
        assertEquals(12 + 20, s.cost())
        assertEquals(12 + 20, s.get(random().nextInt(100).toLong())!!.iterator().cost())

        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(30))
        s =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                1,
                100
            )
        assertEquals(42 + 12 + 20 + 30, s.cost())
        assertEquals(42 + 12 + 20 + 30, s.get(random().nextInt(100).toLong())!!.iterator().cost())
        s =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                2,
                100
            )
        assertEquals(12 + 20 + 30, s.cost())
        assertEquals(12 + 20 + 30, s.get(random().nextInt(100).toLong())!!.iterator().cost())
        s =
            BooleanScorerSupplier(
                FakeWeight(),
                subs,
                RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
                3,
                100
            )
        assertEquals(12 + 20, s.cost())
        assertEquals(12 + 20, s.get(random().nextInt(100).toLong())!!.iterator().cost())
    }

    @Test
    @Throws(Exception::class)
    fun testDuelCost() {
        val iters = atLeast(1000)
        for (iter in 0 until iters) {
            val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
            for (occur in Occur.entries) {
                subs[occur] = ArrayList()
            }
            var numClauses = TestUtil.nextInt(random(), 1, 10)
            var numShoulds = 0
            var numRequired = 0
            for (j in 0 until numClauses) {
                val occur = RandomPicks.randomFrom(random(), Occur.entries.toTypedArray())
                subs[occur]!!.add(FakeScorerSupplier(random().nextInt(100).toLong()))
                if (occur == Occur.SHOULD) {
                    ++numShoulds
                } else if (occur == Occur.FILTER || occur == Occur.MUST) {
                    numRequired++
                }
            }
            val scoreMode = RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray())
            if (!scoreMode.needsScores() && numRequired > 0) {
                numClauses -= numShoulds
                numShoulds = 0
                subs[Occur.SHOULD]!!.clear()
            }
            if (numShoulds + numRequired == 0) {
                continue
            }
            val minShouldMatch = if (numShoulds == 0) 0 else TestUtil.nextInt(random(), 0, numShoulds - 1)
            val supplier = BooleanScorerSupplier(FakeWeight(), subs, scoreMode, minShouldMatch, 100)
            val cost1 = supplier.cost()
            val cost2 = supplier.get(Long.MAX_VALUE)!!.iterator().cost()
            assertEquals(cost1, cost2, "clauses=$subs, minShouldMatch=$minShouldMatch")
        }
    }

    // test the tester...
    @Test
    fun testFakeScorerSupplier() {
        val randomAccessSupplier = FakeScorerSupplier(random().nextInt(100).toLong(), 30)
        expectThrows(AssertionError::class) { randomAccessSupplier.get(70) }
        val sequentialSupplier = FakeScorerSupplier(random().nextInt(100).toLong(), 70)
        expectThrows(AssertionError::class) { sequentialSupplier.get(30) }
    }

    @Test
    @Throws(IOException::class)
    fun testConjunctionLeadCost() {
        var subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        // If the clauses are less costly than the lead cost, the min cost is the new lead cost
        subs[RandomPicks.randomFrom(random(), arrayOf(Occur.FILTER, Occur.MUST))]!!.add(FakeScorerSupplier(42, 12))
        subs[RandomPicks.randomFrom(random(), arrayOf(Occur.FILTER, Occur.MUST))]!!.add(FakeScorerSupplier(12, 12))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            0,
            100
        ).get(Long.MAX_VALUE)

        subs = mutableMapOf()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        // If the lead cost is less that the clauses' cost, then we don't modify it
        subs[RandomPicks.randomFrom(random(), arrayOf(Occur.FILTER, Occur.MUST))]!!.add(FakeScorerSupplier(42, 7))
        subs[RandomPicks.randomFrom(random(), arrayOf(Occur.FILTER, Occur.MUST))]!!.add(FakeScorerSupplier(12, 7))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            0,
            100
        ).get(7)
    }

    @Test
    @Throws(IOException::class)
    fun testDisjunctionLeadCost() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(42, 54))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(12, 54))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            0,
            100
        ).get(100)

        subs[Occur.SHOULD]!!.clear()
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(42, 20))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(12, 20))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            0,
            100
        ).get(20)
    }

    @Test
    @Throws(IOException::class)
    fun testDisjunctionWithMinShouldMatchLeadCost() {
        var subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        // minShouldMatch is 2 so the 2 least costly clauses will lead iteration
        // and their cost will be 30+12=42
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(50, 42))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(12, 42))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(30, 42))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            2,
            100
        ).get(100)

        subs = mutableMapOf()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        // If the leadCost is less than the msm cost, then it wins
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(42, 20))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(12, 20))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(30, 20))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            2,
            100
        ).get(20)

        subs = mutableMapOf()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(42, 62))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(12, 62))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(30, 62))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(20, 62))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            2,
            100
        ).get(100)

        subs = mutableMapOf()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(42, 32))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(12, 32))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(30, 32))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(20, 32))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            3,
            100
        ).get(100)
    }

    @Test
    @Throws(IOException::class)
    fun testProhibitedLeadCost() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        // The MUST_NOT clause is called with the same lead cost as the MUST clause
        subs[Occur.MUST]!!.add(FakeScorerSupplier(42, 42))
        subs[Occur.MUST_NOT]!!.add(FakeScorerSupplier(30, 42))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            0,
            100
        ).get(100)

        subs[Occur.MUST]!!.clear()
        subs[Occur.MUST_NOT]!!.clear()
        subs[Occur.MUST]!!.add(FakeScorerSupplier(42, 42))
        subs[Occur.MUST_NOT]!!.add(FakeScorerSupplier(80, 42))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            0,
            100
        ).get(100)

        subs[Occur.MUST]!!.clear()
        subs[Occur.MUST_NOT]!!.clear()
        subs[Occur.MUST]!!.add(FakeScorerSupplier(42, 20))
        subs[Occur.MUST_NOT]!!.add(FakeScorerSupplier(30, 20))
        BooleanScorerSupplier(
            FakeWeight(),
            subs,
            RandomPicks.randomFrom(random(), ScoreMode.entries.toTypedArray()),
            0,
            100
        ).get(20)
    }

    @Test
    @Throws(IOException::class)
    fun testMixedLeadCost() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        // The SHOULD clause is always called with the same lead cost as the MUST clause
        subs[Occur.MUST]!!.add(FakeScorerSupplier(42, 42))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(30, 42))
        BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.COMPLETE, 0, 100).get(100)

        subs[Occur.MUST]!!.clear()
        subs[Occur.SHOULD]!!.clear()
        subs[Occur.MUST]!!.add(FakeScorerSupplier(42, 42))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(80, 42))
        BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.COMPLETE, 0, 100).get(100)

        subs[Occur.MUST]!!.clear()
        subs[Occur.SHOULD]!!.clear()
        subs[Occur.MUST]!!.add(FakeScorerSupplier(42, 20))
        subs[Occur.SHOULD]!!.add(FakeScorerSupplier(80, 20))
        BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.COMPLETE, 0, 100).get(20)
    }

    @Test
    fun testDisjunctionTopLevelScoringClause() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        val clause1 = FakeScorerSupplier(10, 10)
        subs[Occur.SHOULD]!!.add(clause1)
        val clause2 = FakeScorerSupplier(10, 10)
        subs[Occur.SHOULD]!!.add(clause2)

        BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.TOP_SCORES, 0, 100).setTopLevelScoringClause()
        assertFalse(clause1.topLevelScoringClause)
        assertFalse(clause2.topLevelScoringClause)
    }

    @Test
    fun testConjunctionTopLevelScoringClause() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        val clause1 = FakeScorerSupplier(10, 10)
        subs[Occur.MUST]!!.add(clause1)
        val clause2 = FakeScorerSupplier(10, 10)
        subs[Occur.MUST]!!.add(clause2)

        BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.TOP_SCORES, 0, 100).setTopLevelScoringClause()
        assertFalse(clause1.topLevelScoringClause)
        assertFalse(clause2.topLevelScoringClause)
    }

    @Test
    fun testFilterTopLevelScoringClause() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        val clause1 = FakeScorerSupplier(10, 10)
        subs[Occur.FILTER]!!.add(clause1)
        val clause2 = FakeScorerSupplier(10, 10)
        subs[Occur.FILTER]!!.add(clause2)

        BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.TOP_SCORES, 0, 100).setTopLevelScoringClause()
        assertFalse(clause1.topLevelScoringClause)
        assertFalse(clause2.topLevelScoringClause)
    }

    @Test
    fun testSingleMustScoringClause() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        val clause1 = FakeScorerSupplier(10, 10)
        subs[Occur.MUST]!!.add(clause1)
        val clause2 = FakeScorerSupplier(10, 10)
        subs[Occur.FILTER]!!.add(clause2)

        BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.TOP_SCORES, 0, 100).setTopLevelScoringClause()
        assertTrue(clause1.topLevelScoringClause)
        assertFalse(clause2.topLevelScoringClause)
    }

    @Test
    fun testSingleShouldScoringClause() {
        val subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        val clause1 = FakeScorerSupplier(10, 10)
        subs[Occur.SHOULD]!!.add(clause1)
        val clause2 = FakeScorerSupplier(10, 10)
        subs[Occur.MUST_NOT]!!.add(clause2)

        BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.TOP_SCORES, 0, 100).setTopLevelScoringClause()
        assertTrue(clause1.topLevelScoringClause)
        assertFalse(clause2.topLevelScoringClause)
    }

    @Test
    @Throws(Exception::class)
    fun testMaxScoreNonTopLevelScoringClause() {
        var subs = mutableMapOf<Occur, MutableCollection<ScorerSupplier>>()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        val clause1 = FakeScorerSupplier(10, 10)
        subs[Occur.MUST]!!.add(clause1)
        val clause2 = FakeScorerSupplier(10, 10)
        subs[Occur.MUST]!!.add(clause2)

        var scorer = BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.TOP_SCORES, 0, 100).get(10)
        assertEquals(2.0, scorer!!.getMaxScore(DocIdSetIterator.NO_MORE_DOCS).toDouble(), 0.0)

        subs = mutableMapOf()
        for (occur in Occur.entries) {
            subs[occur] = ArrayList()
        }

        subs[Occur.SHOULD]!!.add(clause1)
        subs[Occur.SHOULD]!!.add(clause2)

        scorer = BooleanScorerSupplier(FakeWeight(), subs, ScoreMode.TOP_SCORES, 0, 100).get(10)
        assertEquals(2.0, scorer!!.getMaxScore(DocIdSetIterator.NO_MORE_DOCS).toDouble(), 0.0)
    }
}
