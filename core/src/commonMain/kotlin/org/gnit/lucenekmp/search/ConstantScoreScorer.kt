package org.gnit.lucenekmp.search

import okio.IOException


/**
 * A constant-scoring [Scorer].
 *
 * @lucene.internal
 */
class ConstantScoreScorer : Scorer {
    private inner class DocIdSetIteratorWrapper(var delegate: DocIdSetIterator) : DocIdSetIterator() {
        var doc: Int = -1

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return delegate.nextDoc().also { doc = it }
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return delegate.advance(target).also { doc = it }
        }

        override fun cost(): Long {
            return delegate.cost()
        }
    }

    private val score: Float
    private val scoreMode: ScoreMode?
    private val approximation: DocIdSetIterator
    private val twoPhaseIterator: TwoPhaseIterator?
    private val disi: DocIdSetIterator

    /**
     * Constructor based on a [DocIdSetIterator] which will be used to drive iteration. Two
     * phase iteration will not be supported.
     *
     * @param score the score to return on each document
     * @param scoreMode the score mode
     * @param disi the iterator that defines matching documents
     */
    constructor(score: Float, scoreMode: ScoreMode?, disi: DocIdSetIterator) {
        this.score = score
        this.scoreMode = scoreMode
        // TODO: Only wrap when it is the top-level scoring clause? See
        // ScorerSupplier#setTopLevelScoringClause
        this.approximation =
            if (scoreMode === ScoreMode.TOP_SCORES) this.DocIdSetIteratorWrapper(disi) else disi
        this.twoPhaseIterator = null
        this.disi = this.approximation
    }

    /**
     * Constructor based on a [TwoPhaseIterator]. In that case the [Scorer] will support
     * two-phase iteration.
     *
     * @param score the score to return on each document
     * @param scoreMode the score mode
     * @param twoPhaseIterator the iterator that defines matching documents
     */
    constructor(score: Float, scoreMode: ScoreMode?, twoPhaseIterator: TwoPhaseIterator) {
        this.score = score
        this.scoreMode = scoreMode
        if (scoreMode === ScoreMode.TOP_SCORES) {
            // TODO: Only wrap when it is the top-level scoring clause? See
            // ScorerSupplier#setTopLevelScoringClause
            val docIdSetIteratorWrapper = this.DocIdSetIteratorWrapper(twoPhaseIterator.approximation())
            this.approximation = docIdSetIteratorWrapper
            this.twoPhaseIterator =
                object : TwoPhaseIterator(docIdSetIteratorWrapper) {
                    @Throws(IOException::class)
                    override fun matches(): Boolean {
                        return twoPhaseIterator.matches()
                    }

                    override fun matchCost(): Float {
                        return twoPhaseIterator.matchCost()
                    }
                }
        } else {
            this.approximation = twoPhaseIterator.approximation()
            this.twoPhaseIterator = twoPhaseIterator
        }
        this.disi = TwoPhaseIterator.asDocIdSetIterator(this.twoPhaseIterator)
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        return score
    }

    override var minCompetitiveScore: Float
        get() {
            return if (scoreMode === ScoreMode.TOP_SCORES) {
                score
            } else {
                0f
            }
        }
        set(minScore: Float) {
        if (scoreMode === ScoreMode.TOP_SCORES && minScore > score) {
            (approximation as DocIdSetIteratorWrapper).delegate = DocIdSetIterator.empty()
        }
    }

    override fun iterator(): DocIdSetIterator {
        return disi
    }

    override fun twoPhaseIterator(): TwoPhaseIterator? {
        return twoPhaseIterator
    }

    override fun docID(): Int {
        return disi.docID()
    }

    @Throws(IOException::class)
    override fun score(): Float {
        return score
    }
}
