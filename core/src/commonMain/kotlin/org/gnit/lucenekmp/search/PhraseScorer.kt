package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import okio.IOException

internal class PhraseScorer(
    val matcher: PhraseMatcher,
    val scoreMode: ScoreMode,
    private val simScorer: SimScorer,
    private val norms: NumericDocValues?
) : Scorer() {
    val approximation: DocIdSetIterator = matcher.approximation()
    val impactsApproximation: ImpactsDISI = matcher.impactsApproximation()
    val maxScoreCache: MaxScoreCache = impactsApproximation.getMaxScoreCache()
    val matchCost: Float = matcher.matchCost

    override var minCompetitiveScore = 0f
    set(minScore) {
        this.minCompetitiveScore = minScore
        impactsApproximation.setMinCompetitiveScore(minScore)
    }

    private var freq = 0f

    override fun twoPhaseIterator(): TwoPhaseIterator {
        return object : TwoPhaseIterator(approximation) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                matcher.reset()
                if (scoreMode == ScoreMode.TOP_SCORES && minCompetitiveScore > 0) {
                    val maxFreq: Float = matcher.maxFreq()
                    var norm = 1L
                    if (norms != null && norms.advanceExact(docID())) {
                        norm = norms.longValue()
                    }
                    if (simScorer.score(maxFreq, norm) < minCompetitiveScore) {
                        // The maximum score we could get is less than the min competitive score
                        return false
                    }
                }
                freq = 0f
                return matcher.nextMatch()
            }

            override fun matchCost(): Float {
                return matchCost
            }
        }
    }

    override fun docID(): Int {
        return approximation.docID()
    }

    @Throws(IOException::class)
    override fun score(): Float {
        if (freq == 0f) {
            freq = matcher.sloppyWeight()
            while (matcher.nextMatch()) {
                freq += matcher.sloppyWeight()
            }
        }
        var norm = 1L
        if (norms != null && norms.advanceExact(docID())) {
            norm = norms.longValue()
        }
        return simScorer.score(freq, norm)
    }

    override fun iterator(): DocIdSetIterator {
        return TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator())
    }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        return maxScoreCache.advanceShallow(target)
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        return maxScoreCache.getMaxScore(upTo)
    }
}
