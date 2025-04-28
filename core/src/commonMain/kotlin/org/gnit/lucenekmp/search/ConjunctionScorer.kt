package org.gnit.lucenekmp.search

import kotlinx.io.IOException


/** Scorer for conjunctions, sets of queries, all of which are required.  */
internal class ConjunctionScorer(required: MutableCollection<Scorer>, scorers: MutableCollection<Scorer>) : Scorer() {
    val disi: DocIdSetIterator
    val scorers: Array<Scorer>
    val required: MutableCollection<Scorer>

    /**
     * Create a new [ConjunctionScorer], note that `scorers` must be a subset of `required`.
     */
    init {
        require(required.containsAll(scorers))
        this.disi = ConjunctionUtils.intersectScorers(required)
        this.scorers = scorers.toTypedArray<Scorer>()
        this.required = required
    }

    override fun twoPhaseIterator(): TwoPhaseIterator {
        return TwoPhaseIterator.unwrap(disi)!!
    }

    override fun iterator(): DocIdSetIterator {
        return disi
    }

    override fun docID(): Int {
        return disi.docID()
    }

    @Throws(IOException::class)
    override fun score(): Float {
        var sum = 0.0
        for (scorer in scorers) {
            sum += scorer.score()
        }
        return sum.toFloat()
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        var maxScore = 0.0
        for (s in scorers) {
            if (s.docID() <= upTo) {
                maxScore += s.getMaxScore(upTo)
            }
        }
        return maxScore.toFloat()
    }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        if (scorers.size == 1) {
            return scorers[0].advanceShallow(target)
        }
        for (scorer in scorers) {
            scorer.advanceShallow(target)
        }
        return super.advanceShallow(target)
    }

    @get:Throws(IOException::class)
    override var minCompetitiveScore: Float
        get() {
            // This scorer is only used for TOP_SCORES when there is a single scoring clause
            return if (scorers.size == 1) {
                scorers[0].minCompetitiveScore
            } else {
                0f
            }
        }
        set(minScore: Float) {
        // This scorer is only used for TOP_SCORES when there is a single scoring clause
        if (scorers.size == 1) {
            scorers[0].minCompetitiveScore = minScore
        }
    }

    override val children: MutableCollection<ChildScorable>
        get() {
            val children: ArrayList<ChildScorable> = ArrayList()
            for (scorer in required) {
                children.add(ChildScorable(scorer, "MUST"))
            }
            return children
        }

    internal class DocsAndFreqs(val iterator: DocIdSetIterator) {
        val cost: Long = iterator.cost()
        var doc: Int = -1
    }
}
