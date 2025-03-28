package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SlowImpactsEnum
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer


/**
 * Expert: A `Scorer` for documents matching a `Term`.
 *
 * @lucene.internal
 */
class TermScorer : Scorer {
    private val postingsEnum: PostingsEnum
    private val iterator: DocIdSetIterator
    private val scorer: SimScorer
    private val norms: NumericDocValues
    private val impactsDisi: ImpactsDISI?
    private val maxScoreCache: MaxScoreCache

    /** Construct a [TermScorer] that will iterate all documents.  */
    constructor(postingsEnum: PostingsEnum, scorer: SimScorer, norms: NumericDocValues) {
        this.postingsEnum = postingsEnum
        iterator = this.postingsEnum
        val impactsEnum: ImpactsEnum = SlowImpactsEnum(postingsEnum)
        maxScoreCache = MaxScoreCache(impactsEnum, scorer)
        impactsDisi = null
        this.scorer = scorer
        this.norms = norms
    }

    /**
     * Construct a [TermScorer] that will use impacts to skip blocks of non-competitive
     * documents.
     */
    constructor(
        impactsEnum: ImpactsEnum,
        scorer: SimScorer,
        norms: NumericDocValues,
        topLevelScoringClause: Boolean
    ) {
        postingsEnum = impactsEnum
        maxScoreCache = MaxScoreCache(impactsEnum, scorer)
        if (topLevelScoringClause) {
            impactsDisi = ImpactsDISI(impactsEnum, maxScoreCache)
            iterator = impactsDisi
        } else {
            impactsDisi = null
            iterator = impactsEnum
        }
        this.scorer = scorer
        this.norms = norms
    }

    override fun docID(): Int {
        return postingsEnum.docID()
    }

    /** Returns term frequency in the current document.  */
    @Throws(IOException::class)
    fun freq(): Int {
        return postingsEnum.freq()
    }

    override fun iterator(): DocIdSetIterator {
        return iterator
    }

    @Throws(IOException::class)
    override fun score(): Float {
        val postingsEnum: PostingsEnum = this.postingsEnum
        val norms: NumericDocValues? = this.norms

        var norm = 1L
        if (norms != null && norms.advanceExact(postingsEnum.docID())) {
            norm = norms.longValue()
        }
        return scorer.score(postingsEnum.freq().toFloat(), norm)
    }

    @Throws(IOException::class)
    override fun smoothingScore(docId: Int): Float {
        var norm = 1L
        if (norms != null && norms.advanceExact(docId)) {
            norm = norms.longValue()
        }
        return scorer.score(0f, norm)
    }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        return maxScoreCache.advanceShallow(target)
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        return maxScoreCache.getMaxScore(upTo)
    }

    override fun setMinCompetitiveScore(minScore: Float) {
        if (impactsDisi != null) {
            impactsDisi.setMinCompetitiveScore(minScore)
        }
    }
}
