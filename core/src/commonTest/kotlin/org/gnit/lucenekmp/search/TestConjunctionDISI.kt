package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitDocIdSet
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestConjunctionDISI : LuceneTestCase() {
    private fun approximation(
        iterator: DocIdSetIterator,
        confirmed: FixedBitSet
    ): TwoPhaseIterator {
        val approximation =
            if (random().nextBoolean()) {
                anonymizeIterator(iterator)
            } else {
                iterator
            }
        return object : TwoPhaseIterator(approximation) {
            override fun matches(): Boolean {
                return confirmed.get(approximation.docID())
            }

            override fun matchCost(): Float {
                return 5f // #operations in FixedBitSet#get()
            }
        }
    }

    /**
     * Return an anonym class so that ConjunctionDISI cannot optimize it like it does eg. for
     * BitSetIterators.
     */
    private fun anonymizeIterator(it: DocIdSetIterator): DocIdSetIterator {
        return object : DocIdSetIterator() {
            @Throws(IOException::class)
            override fun nextDoc(): Int {
                return it.nextDoc()
            }

            override fun docID(): Int {
                return it.docID()
            }

            override fun cost(): Long {
                return it.docID().toLong()
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                return it.advance(target)
            }
        }
    }

    private fun scorer(twoPhaseIterator: TwoPhaseIterator): Scorer {
        return scorer(TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator), twoPhaseIterator)
    }

    /**
     * Create a [Scorer] that wraps the given [DocIdSetIterator]. It also accepts a [TwoPhaseIterator]
     * view, which is exposed in [Scorer.twoPhaseIterator]. When the two-phase view is not null, then
     * [DocIdSetIterator.nextDoc] and [DocIdSetIterator.advance] will raise an exception in order to
     * make sure that [ConjunctionDISI] takes advantage of the [TwoPhaseIterator] view.
     */
    private fun scorer(it: DocIdSetIterator, twoPhaseIterator: TwoPhaseIterator?): Scorer {
        return object : Scorer() {
            override fun iterator(): DocIdSetIterator {
                return object : DocIdSetIterator() {
                    override fun docID(): Int {
                        return it.docID()
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        if (twoPhaseIterator != null) {
                            throw UnsupportedOperationException(
                                "ConjunctionDISI should call the two-phase iterator"
                            )
                        }
                        return it.nextDoc()
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        if (twoPhaseIterator != null) {
                            throw UnsupportedOperationException(
                                "ConjunctionDISI should call the two-phase iterator"
                            )
                        }
                        return it.advance(target)
                    }

                    override fun cost(): Long {
                        if (twoPhaseIterator != null) {
                            throw UnsupportedOperationException(
                                "ConjunctionDISI should call the two-phase iterator"
                            )
                        }
                        return it.cost()
                    }
                }
            }

            override fun twoPhaseIterator(): TwoPhaseIterator? {
                return twoPhaseIterator
            }

            override fun docID(): Int {
                if (twoPhaseIterator != null) {
                    throw UnsupportedOperationException(
                        "ConjunctionDISI should call the two-phase iterator"
                    )
                }
                return it.docID()
            }

            @Throws(IOException::class)
            override fun score(): Float {
                return 0f
            }

            @Throws(IOException::class)
            override fun getMaxScore(upTo: Int): Float {
                return 0f
            }
        }
    }

    private fun randomSet(maxDoc: Int): FixedBitSet {
        val step = TestUtil.nextInt(random(), 1, 10)
        val set = FixedBitSet(maxDoc)
        var doc = random().nextInt(step)
        while (doc < maxDoc) {
            set.set(doc)
            doc += TestUtil.nextInt(random(), 1, step)
        }
        return set
    }

    private fun clearRandomBits(other: FixedBitSet): FixedBitSet {
        val set = FixedBitSet(other.length())
        set.or(other)
        for (i in 0 until set.length()) {
            if (random().nextBoolean()) {
                set.clear(i)
            }
        }
        return set
    }

    private fun intersect(bitSets: Array<FixedBitSet>): FixedBitSet {
        val intersection = FixedBitSet(bitSets[0].length())
        intersection.or(bitSets[0])
        for (i in 1 until bitSets.size) {
            intersection.and(bitSets[i])
        }
        return intersection
    }

    @Throws(IOException::class)
    private fun toBitSet(maxDoc: Int, iterator: DocIdSetIterator): FixedBitSet {
        val set = FixedBitSet(maxDoc)
        var doc = iterator.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            set.set(doc)
            doc = iterator.nextDoc()
        }
        return set
    }

    // Test that the conjunction iterator is correct
    @Test
    @Throws(IOException::class)
    fun testConjunction() {
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val maxDoc = TestUtil.nextInt(random(), 100, 10000)
            val numIterators = TestUtil.nextInt(random(), 2, 5)
            val sets = Array(numIterators) { FixedBitSet(maxDoc) }
            val iterators = arrayOfNulls<Scorer>(numIterators)
            for (i in iterators.indices) {
                val set = randomSet(maxDoc)
                when (random().nextInt(3)) {
                    0 -> {
                        // simple iterator
                        sets[i] = set
                        iterators[i] =
                            ConstantScoreScorer(
                                0f,
                                ScoreMode.TOP_SCORES,
                                anonymizeIterator(BitDocIdSet(set).iterator())
                            )
                    }

                    1 -> {
                        // bitSet iterator
                        sets[i] = set
                        iterators[i] =
                            ConstantScoreScorer(0f, ScoreMode.TOP_SCORES, BitDocIdSet(set).iterator())
                    }

                    else -> {
                        // scorer with approximation
                        val confirmed = clearRandomBits(set)
                        sets[i] = confirmed
                        val approximation = approximation(BitDocIdSet(set).iterator(), confirmed)
                        iterators[i] = scorer(approximation)
                    }
                }
            }

            val conjunction =
                ConjunctionUtils.intersectScorers(iterators.map { it!! }.toMutableList())
            assertEquals(intersect(sets), toBitSet(maxDoc, conjunction))
        }
    }

    // Test that the conjunction approximation is correct
    @Test
    @Throws(IOException::class)
    fun testConjunctionApproximation() {
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val maxDoc = TestUtil.nextInt(random(), 100, 10000)
            val numIterators = TestUtil.nextInt(random(), 2, 5)
            val sets = Array(numIterators) { FixedBitSet(maxDoc) }
            val iterators = arrayOfNulls<Scorer>(numIterators)
            var hasApproximation = false
            for (i in iterators.indices) {
                val set = randomSet(maxDoc)
                if (random().nextBoolean()) {
                    // simple iterator
                    sets[i] = set
                    iterators[i] =
                        ConstantScoreScorer(
                            0f,
                            ScoreMode.COMPLETE_NO_SCORES,
                            BitDocIdSet(set).iterator()
                        )
                } else {
                    // scorer with approximation
                    val confirmed = clearRandomBits(set)
                    sets[i] = confirmed
                    val approximation = approximation(BitDocIdSet(set).iterator(), confirmed)
                    iterators[i] = scorer(approximation)
                    hasApproximation = true
                }
            }

            val conjunction =
                ConjunctionUtils.intersectScorers(iterators.map { it!! }.toMutableList())
            val twoPhaseIterator = TwoPhaseIterator.unwrap(conjunction)
            assertEquals(hasApproximation, twoPhaseIterator != null)
            if (hasApproximation) {
                assertNotNull(twoPhaseIterator)
                assertEquals(
                    intersect(sets),
                    toBitSet(maxDoc, TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator))
                )
            }
        }
    }

    // This test makes sure that when nesting scorers with ConjunctionDISI, confirmations are pushed
    // to the root.
    @Test
    @Throws(IOException::class)
    fun testRecursiveConjunctionApproximation() {
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val maxDoc = TestUtil.nextInt(random(), 100, 10000)
            val numIterators = TestUtil.nextInt(random(), 2, 5)
            val sets = Array(numIterators) { FixedBitSet(maxDoc) }
            var conjunction: Scorer? = null
            var hasApproximation = false
            for (i in 0 until numIterators) {
                val set = randomSet(maxDoc)
                val newIterator: Scorer =
                    when (random().nextInt(3)) {
                        0 -> {
                            // simple iterator
                            sets[i] = set
                            ConstantScoreScorer(
                                0f,
                                ScoreMode.TOP_SCORES,
                                anonymizeIterator(BitDocIdSet(set).iterator())
                            )
                        }

                        1 -> {
                            // bitSet iterator
                            sets[i] = set
                            ConstantScoreScorer(0f, ScoreMode.TOP_SCORES, BitDocIdSet(set).iterator())
                        }

                        else -> {
                            // scorer with approximation
                            val confirmed = clearRandomBits(set)
                            sets[i] = confirmed
                            val approximation = approximation(BitDocIdSet(set).iterator(), confirmed)
                            hasApproximation = true
                            scorer(approximation)
                        }
                    }
                if (conjunction == null) {
                    conjunction = newIterator
                } else {
                    val conj =
                        ConjunctionUtils.intersectScorers(mutableListOf(conjunction, newIterator))
                    conjunction = scorer(conj, TwoPhaseIterator.unwrap(conj))
                }
            }

            val twoPhaseIterator = conjunction!!.twoPhaseIterator()
            assertEquals(hasApproximation, twoPhaseIterator != null)
            if (hasApproximation) {
                assertNotNull(twoPhaseIterator)
                assertEquals(
                    intersect(sets),
                    toBitSet(maxDoc, TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator))
                )
            } else {
                assertEquals(intersect(sets), toBitSet(maxDoc, conjunction.iterator()))
            }
        }
    }

    @Throws(IOException::class)
    fun testCollapseSubConjunctions(wrapWithScorer: Boolean) {
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val maxDoc = TestUtil.nextInt(random(), 100, 10000)
            val numIterators = TestUtil.nextInt(random(), 5, 10)
            val sets = Array(numIterators) { FixedBitSet(maxDoc) }
            val scorers = mutableListOf<Scorer>()
            for (i in 0 until numIterators) {
                val set = randomSet(maxDoc)
                if (random().nextBoolean()) {
                    // simple iterator
                    sets[i] = set
                    scorers.add(
                        ConstantScoreScorer(0f, ScoreMode.TOP_SCORES, BitDocIdSet(set).iterator())
                    )
                } else {
                    // scorer with approximation
                    val confirmed = clearRandomBits(set)
                    sets[i] = confirmed
                    val approximation = approximation(BitDocIdSet(set).iterator(), confirmed)
                    scorers.add(scorer(approximation))
                }
            }

            // make some sub sequences into sub conjunctions
            val subIters = atLeast(3)
            for (subIter in 0 until subIters) {
                if (scorers.size <= 3) {
                    break
                }
                val subSeqStart = TestUtil.nextInt(random(), 0, scorers.size - 2)
                val subSeqEnd = TestUtil.nextInt(random(), subSeqStart + 2, scorers.size)
                val subIterators = scorers.subList(subSeqStart, subSeqEnd)
                val subConjunction =
                    if (wrapWithScorer) {
                        ConjunctionScorer(subIterators, mutableListOf())
                    } else {
                        ConstantScoreScorer(
                            0f,
                            ScoreMode.TOP_SCORES,
                            ConjunctionUtils.intersectScorers(subIterators)
                        )
                    }
                scorers[subSeqStart] = subConjunction
                var toRemove = subSeqEnd - subSeqStart - 1
                while (toRemove-- > 0) {
                    scorers.removeAt(subSeqStart + 1)
                }
            }
            if (scorers.size == 1) {
                // ConjunctionDISI needs two iterators
                scorers.add(ConstantScoreScorer(0f, ScoreMode.TOP_SCORES, DocIdSetIterator.all(maxDoc)))
            }

            val conjunction = ConjunctionUtils.intersectScorers(scorers)
            assertEquals(intersect(sets), toBitSet(maxDoc, conjunction))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCollapseSubConjunctionDISIs() {
        testCollapseSubConjunctions(false)
    }

    @Test
    @Throws(IOException::class)
    fun testCollapseSubConjunctionScorers() {
        testCollapseSubConjunctions(true)
    }

    @Test
    @Throws(IOException::class)
    fun testIllegalAdvancementOfSubIteratorsTripsAssertion() {
        val maxDoc = 100
        val numIterators = TestUtil.nextInt(random(), 2, 5)
        val set = randomSet(maxDoc)

        val iterators = arrayOfNulls<DocIdSetIterator>(numIterators)
        for (i in iterators.indices) {
            iterators[i] = BitDocIdSet(set).iterator()
        }
        val conjunction =
            ConjunctionUtils.intersectIterators(iterators.map { it!! }.toMutableList())
        val idx = TestUtil.nextInt(random(), 0, iterators.size - 1)
        iterators[idx]!!
            .nextDoc() // illegally advancing one of the sub-iterators outside of the conjunction
        // iterator
        val ex = expectThrows(AssertionError::class) { conjunction.nextDoc() }
        assertEquals(
            "Sub-iterators of ConjunctionDISI are not on the same document!",
            ex.message
        )
    }

    @Test
    @Throws(IOException::class)
    fun testBitSetConjunctionDISIDocIDOnExhaust() {
        val numBitSetIterators = TestUtil.nextInt(random(), 2, 5)
        val iterators = arrayOfNulls<DocIdSetIterator>(numBitSetIterators + 1)

        // Create sparse DocIdSetIterator with a single match that is greater than lengths of bitset
        // iterators
        val maxBitSetLength = 1000
        val minBitSetLength = 2
        val leadMaxDoc = maxBitSetLength + 1
        iterators[iterators.size - 1] = DocIdSetIterator.range(leadMaxDoc, leadMaxDoc + 1)

        for (i in 0 until numBitSetIterators) {
            val bitSetLength = TestUtil.nextInt(random(), minBitSetLength, maxBitSetLength)
            val bitSet = FixedBitSet(bitSetLength)
            bitSet.set(0, bitSetLength - 1)
            iterators[i] = BitDocIdSet(bitSet).iterator()
        }

        val conjunction =
            ConjunctionUtils.intersectIterators(iterators.map { it!! }.toMutableList())

        assertEquals(DocIdSetIterator.NO_MORE_DOCS, conjunction.nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, conjunction.docID())
    }
}
