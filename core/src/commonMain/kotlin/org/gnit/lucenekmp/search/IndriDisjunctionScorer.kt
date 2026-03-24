package org.gnit.lucenekmp.search

/**
 * The Indri implemenation of a disjunction scorer which stores the subscorers for the child
 * queries. The score and smoothingScore methods use the list of all subscorers and not just the
 * matches so that a smoothingScore can be calculated if there is not an exact match.
 */
abstract class IndriDisjunctionScorer protected constructor(
    private val subScorersList: MutableList<Scorer>,
    scoreMode: ScoreMode,
    boost: Float
) : IndriScorer(boost) {
    private val approximation: DocIdSetIterator

    init {
        val wrappers = ArrayList<DisiWrapper>()
        for (scorer in subScorersList) {
            val w = DisiWrapper(scorer, false)
            wrappers.add(w)
        }
        this.approximation = DisjunctionDISIApproximation(wrappers, Long.MAX_VALUE)
    }

    override fun iterator(): DocIdSetIterator {
        return approximation
    }

    override fun getMaxScore(upTo: Int): Float {
        return 0f
    }

    fun getSubMatches(): MutableList<Scorer> {
        return subScorersList
    }

    abstract fun score(subScorers: MutableList<Scorer>): Float

    abstract fun smoothingScore(subScorers: MutableList<Scorer>, docId: Int): Float

    override fun score(): Float {
        return score(getSubMatches())
    }

    override fun smoothingScore(docId: Int): Float {
        return smoothingScore(getSubMatches(), docId)
    }

    override fun docID(): Int {
        return approximation.docID()
    }
}
