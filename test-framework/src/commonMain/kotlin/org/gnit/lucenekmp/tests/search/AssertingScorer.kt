package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.random.Random

/** Wraps a Scorer with additional checks */
class AssertingScorer private constructor(
    private val random: Random,
    internal val `in`: Scorer,
    private val scoreMode: ScoreMode,
    private val canCallMinCompetitiveScore: Boolean
) : Scorer() {

    private enum class IteratorState {
        APPROXIMATING,
        ITERATING,
        SHALLOW_ADVANCING,
        FINISHED
    }

    private var state: IteratorState = IteratorState.ITERATING
    private var doc: Int = `in`.docID()
    private var minCompetitiveScoreValue = 0f
    private var lastShallowTarget = -1

    companion object {
        fun wrap(
            random: Random,
            other: Scorer?,
            scoreMode: ScoreMode,
            canCallMinCompetitiveScore: Boolean
        ): Scorer? {
            if (other == null) {
                return null
            }
            return AssertingScorer(random, other, scoreMode, canCallMinCompetitiveScore)
        }
    }

    fun getIn(): Scorer {
        return `in`
    }

    private fun iterating(): Boolean {
        // we cannot assert that state == ITERATING because of CachingScorerWrapper
        return when (docID()) {
            -1, DocIdSetIterator.NO_MORE_DOCS -> false
            else -> state == IteratorState.ITERATING
        }
    }

    override var minCompetitiveScore: Float
        get() = minCompetitiveScoreValue
        @Throws(IOException::class)
        set(score) {
            assert(scoreMode == ScoreMode.TOP_SCORES)
            assert(canCallMinCompetitiveScore)
            assert(score.isNaN().not())
            assert(score >= minCompetitiveScoreValue)
            `in`.minCompetitiveScore = score
            minCompetitiveScoreValue = score
        }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        assert(scoreMode.needsScores())
        assert(target >= lastShallowTarget) {
            "called on decreasing targets: target = $target < last target = $lastShallowTarget"
        }
        assert(target >= docID()) { "target = $target < docID = ${docID()}" }
        val upTo = `in`.advanceShallow(target)
        assert(upTo >= target) { "upTo = $upTo < target = $target" }
        lastShallowTarget = target
        state = if (target != doc) IteratorState.SHALLOW_ADVANCING else state
        return upTo
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        assert(scoreMode.needsScores())
        assert(upTo >= lastShallowTarget) { "uTo = $upTo < last target = $lastShallowTarget" }
        assert(docID() >= 0 || lastShallowTarget >= 0) {
            "Cannot get max scores until the iterator is positioned or advanceShallow has been called"
        }
        return `in`.getMaxScore(upTo)
    }

    @Throws(IOException::class)
    override fun score(): Float {
        assert(scoreMode.needsScores())
        assert(iterating()) { state }
        val score = `in`.score()
        assert(score.isNaN().not()) { "NaN score for in=$`in`" }
        assert(lastShallowTarget == -1 || score <= getMaxScore(docID()))
        assert(score >= 0f) { score }
        return score
    }

    override val children: MutableCollection<ChildScorable>
        get() = mutableListOf(ChildScorable(`in`, "SHOULD"))

    override fun docID(): Int {
        return `in`.docID()
    }

    override fun toString(): String {
        return "AssertingScorer($`in`)"
    }

    override fun iterator(): DocIdSetIterator {
        val inIter = `in`.iterator()
        return object : DocIdSetIterator() {
            override fun docID(): Int {
                assert(this@AssertingScorer.`in`.docID() == inIter.docID())
                return inIter.docID()
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                assert(state != IteratorState.FINISHED) { "nextDoc() called after NO_MORE_DOCS" }
                assert(docID() + 1 >= lastShallowTarget)
                val nextDoc = inIter.nextDoc()
                assert(nextDoc > doc) { "backwards nextDoc from $doc to $nextDoc $inIter" }
                state = if (nextDoc == DocIdSetIterator.NO_MORE_DOCS) {
                    IteratorState.FINISHED
                } else {
                    IteratorState.ITERATING
                }
                assert(inIter.docID() == nextDoc)
                assert(this@AssertingScorer.`in`.docID() == inIter.docID())
                doc = nextDoc
                return doc
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                assert(state != IteratorState.FINISHED) { "advance() called after NO_MORE_DOCS" }
                assert(target > doc) { "target must be > docID(), got $target <= $doc" }
                assert(target >= lastShallowTarget)
                val advanced = inIter.advance(target)
                assert(advanced >= target) { "backwards advance from: $target to: $advanced" }
                state = if (advanced == DocIdSetIterator.NO_MORE_DOCS) {
                    IteratorState.FINISHED
                } else {
                    IteratorState.ITERATING
                }
                assert(inIter.docID() == advanced) { "${inIter.docID()} != $advanced in $inIter" }
                assert(this@AssertingScorer.`in`.docID() == inIter.docID())
                doc = advanced
                return doc
            }

            override fun cost(): Long {
                return inIter.cost()
            }

            @Throws(IOException::class)
            override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
                assert(docID() != -1)
                assert(offset <= docID())
                inIter.intoBitSet(upTo, bitSet, offset)
                assert(docID() >= upTo)
            }
        }
    }

    override fun twoPhaseIterator(): TwoPhaseIterator? {
        val inTwoPhase = `in`.twoPhaseIterator() ?: return null
        val inApproximation = inTwoPhase.approximation
        assert(inApproximation.docID() == doc)
        val assertingApproximation = object : DocIdSetIterator() {
            override fun docID(): Int {
                return inApproximation.docID()
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                assert(state != IteratorState.FINISHED) { "advance() called after NO_MORE_DOCS" }
                assert(docID() + 1 >= lastShallowTarget)
                val nextDoc = inApproximation.nextDoc()
                assert(nextDoc > doc) { "backwards advance from: $doc to: $nextDoc" }
                state = if (nextDoc == NO_MORE_DOCS) {
                    IteratorState.FINISHED
                } else {
                    IteratorState.APPROXIMATING
                }
                assert(inApproximation.docID() == nextDoc)
                doc = nextDoc
                return doc
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                assert(state != IteratorState.FINISHED) { "advance() called after NO_MORE_DOCS" }
                assert(target > doc) { "target must be > docID(), got $target <= $doc" }
                assert(target >= lastShallowTarget)
                val advanced = inApproximation.advance(target)
                assert(advanced >= target) { "backwards advance from: $target to: $advanced" }
                state = if (advanced == NO_MORE_DOCS) {
                    IteratorState.FINISHED
                } else {
                    IteratorState.APPROXIMATING
                }
                assert(inApproximation.docID() == advanced)
                doc = advanced
                return doc
            }

            override fun cost(): Long {
                return inApproximation.cost()
            }
        }
        return object : TwoPhaseIterator(assertingApproximation) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                assert(state == IteratorState.APPROXIMATING) { state }
                val matches = inTwoPhase.matches()
                if (matches) {
                    assert(this@AssertingScorer.`in`.iterator().docID() == inApproximation.docID()) {
                        "Approximation and scorer don't advance synchronously"
                    }
                    doc = inApproximation.docID()
                    state = IteratorState.ITERATING
                }
                return matches
            }

            override fun matchCost(): Float {
                val matchCost = inTwoPhase.matchCost()
                assert(matchCost.isNaN().not())
                assert(matchCost >= 0f)
                return matchCost
            }

            override fun toString(): String {
                return "AssertingScorer@asTwoPhaseIterator($inTwoPhase)"
            }
        }
    }
}
