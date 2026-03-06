package org.gnit.lucenekmp.tests.search

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.*
import org.gnit.lucenekmp.tests.util.RandomNumbers
import kotlin.random.Random
import kotlin.time.TimeSource

private val randomApproximationQueryLogger = KotlinLogging.logger {}
private val randomApproximationWeightLogger = KotlinLogging.logger {}
private val randomApproximationLogger = KotlinLogging.logger {}

/** A [Query] that adds random approximations to its scorers. */
class RandomApproximationQuery(private val query: Query, private val random: Random) : Query() {

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten = query.rewrite(indexSearcher)
        return if (rewritten !== query) {
            RandomApproximationQuery(rewritten, random)
        } else {
            super.rewrite(indexSearcher)
        }
    }

    override fun visit(visitor: QueryVisitor) {
        query.visit(visitor)
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && query == (other as RandomApproximationQuery).query
    }

    override fun hashCode(): Int {
        return 31 * classHash() + query.hashCode()
    }

    override fun toString(field: String?): String {
        return query.toString(field)
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        val totalMark = TimeSource.Monotonic.markNow()
        val innerMark = TimeSource.Monotonic.markNow()
        val weight = query.createWeight(searcher, scoreMode, boost)
        val innerMs = innerMark.elapsedNow().inWholeMilliseconds
        val splitMark = TimeSource.Monotonic.markNow()
        val splitRandom = Random(random.nextLong())
        val splitMs = splitMark.elapsedNow().inWholeMilliseconds
        val totalElapsedMs = totalMark.elapsedNow().inWholeMilliseconds
        // randomApproximationQueryLogger.debug { "phase=randomApproximationQuery.createWeight query=${query::class.simpleName} scoreMode=$scoreMode innerMs=$innerMs splitMs=$splitMs totalMs=$totalElapsedMs" }
        return RandomApproximationWeight(weight, splitRandom)
    }

    private class RandomApproximationWeight(weight: Weight, private val random: Random) :
        FilterWeight(weight) {
        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val totalMark = TimeSource.Monotonic.markNow()
            val scorerSupplier = `in`.scorerSupplier(context) ?: return null
            val innerMs = totalMark.elapsedNow().inWholeMilliseconds
            val getMark = TimeSource.Monotonic.markNow()
            val subScorer = scorerSupplier.get(Long.MAX_VALUE)
            val getMs = getMark.elapsedNow().inWholeMilliseconds
            val splitMark = TimeSource.Monotonic.markNow()
            val splitRandom = Random(random.nextLong())
            val splitMs = splitMark.elapsedNow().inWholeMilliseconds
            val scorer = RandomApproximationScorer(subScorer!!, splitRandom)
            val totalMs = totalMark.elapsedNow().inWholeMilliseconds
            // randomApproximationWeightLogger.debug { "phase=randomApproximationWeight.scorerSupplier innerMs=$innerMs getMs=$getMs splitMs=$splitMs totalMs=$totalMs" }
            return DefaultScorerSupplier(scorer)
        }
    }

    private class RandomApproximationScorer(
        private val scorer: Scorer,
        random: Random
    ) : Scorer() {
        private val twoPhaseView = RandomTwoPhaseView(random, scorer.iterator())

        override fun twoPhaseIterator(): TwoPhaseIterator? {
            return twoPhaseView
        }

        @Throws(IOException::class)
        override fun score(): Float {
            return scorer.score()
        }

        @Throws(IOException::class)
        override fun advanceShallow(target: Int): Int {
            var t = target
            if (scorer.docID() > t && twoPhaseView.approximation.docID() != scorer.docID()) {
                // The random approximation can return doc ids that are not present in the underlying
                // scorer. These additional doc ids are always *before* the next matching doc so we
                // cannot use them to shallow advance the main scorer which is already ahead.
                t = scorer.docID()
            }
            return scorer.advanceShallow(t)
        }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            return scorer.getMaxScore(upTo)
        }

        override fun docID(): Int {
            return twoPhaseView.approximation.docID()
        }

        override fun iterator(): DocIdSetIterator {
            return TwoPhaseIterator.asDocIdSetIterator(twoPhaseView)
        }
    }

    /**
     * A wrapper around a [DocIdSetIterator] that matches the same documents, but introduces
     * false positives that need to be verified via [TwoPhaseIterator.matches].
     */
    class RandomTwoPhaseView(random: Random, private val disi: DocIdSetIterator) :
        TwoPhaseIterator(RandomApproximation(random, disi)) {
        private var lastDoc = -1
        private val randomMatchCost: Float = random.nextFloat() * 200f

        @Throws(IOException::class)
        override fun matches(): Boolean {
            val doc = approximation.docID()
            require(!(doc == -1 || doc == DocIdSetIterator.NO_MORE_DOCS)) {
                "matches() should not be called on doc ID $doc"
            }
            require(lastDoc != doc) { "matches() has been called twice on doc ID $doc" }
            lastDoc = doc
            return doc == disi.docID()
        }

        override fun matchCost(): Float {
            return randomMatchCost
        }
    }

    private class RandomApproximation(
        private val random: Random,
        private val disi: DocIdSetIterator
    ) : DocIdSetIterator() {
        private var doc = -1
        private var advanceCalls = 0
        private var disiAdvanceCalls = 0
        private var randomCalls = 0
        private var disiAdvanceMs = 0L
        private var randomMs = 0L
        private var totalAdvanceMs = 0L
        private var summaryLogged = false

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return advance(doc + 1)
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            advanceCalls++
            val totalMark = TimeSource.Monotonic.markNow()
            if (disi.docID() < target) {
                disiAdvanceCalls++
                val disiMark = TimeSource.Monotonic.markNow()
                disi.advance(target)
                disiAdvanceMs += disiMark.elapsedNow().inWholeMilliseconds
            }
            doc = if (disi.docID() == NO_MORE_DOCS) {
                NO_MORE_DOCS
            } else {
                randomCalls++
                val randomMark = TimeSource.Monotonic.markNow()
                val nextDoc = RandomNumbers.randomIntBetween(random, target, disi.docID())
                randomMs += randomMark.elapsedNow().inWholeMilliseconds
                nextDoc
            }
            totalAdvanceMs += totalMark.elapsedNow().inWholeMilliseconds
            if (doc == NO_MORE_DOCS && summaryLogged == false) {
                summaryLogged = true
                // randomApproximationLogger.debug { "phase=randomApproximation.advanceSummary advanceCalls=$advanceCalls disiAdvanceCalls=$disiAdvanceCalls randomCalls=$randomCalls disiAdvanceMs=$disiAdvanceMs randomMs=$randomMs totalAdvanceMs=$totalAdvanceMs" }
            }
            return doc
        }

        override fun cost(): Long {
            return disi.cost()
        }
    }
}
