package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays

/**
 * Scorer for conjunctions that checks the maximum scores of each clause in order to potentially
 * skip over blocks that can't have competitive matches.
 */
internal class BlockMaxConjunctionScorer(scorersList: MutableCollection<Scorer>) : Scorer() {
    val scorers: Array<Scorer> = scorersList.toTypedArray<Scorer>()
    val scorables: Array<Scorable>
    val approximations: Array<DocIdSetIterator?>
    val twoPhases: Array<TwoPhaseIterator>
    var minScore: Float = 0f

    /** Create a new [BlockMaxConjunctionScorer] from scoring clauses.  */
    init {
        // Sort scorer by cost
        this.scorers.sortBy { it.iterator().cost() }
        this.scorables =
            scorers.map(ScorerUtil::likelyTermScorer).toTypedArray<Scorable>()

        this.approximations = kotlin.arrayOfNulls<DocIdSetIterator>(scorers.size)
        val twoPhaseList: MutableList<TwoPhaseIterator> = ArrayList()
        for (i in scorers.indices) {
            val scorer = scorers[i]
            val twoPhase = scorer.twoPhaseIterator()
            if (twoPhase != null) {
                twoPhaseList.add(twoPhase)
                approximations[i] = twoPhase.approximation()
            } else {
                approximations[i] = scorer.iterator()
            }
            approximations[i] = ScorerUtil.likelyImpactsEnum(approximations[i]!!)
            scorer.advanceShallow(0)
        }
        this.twoPhases = twoPhaseList.toTypedArray<TwoPhaseIterator>()
        Arrays.sort<TwoPhaseIterator>(
            this.twoPhases,
            /*Comparator.comparingDouble<Any>(TwoPhaseIterator::matchCost)*/
            compareBy<TwoPhaseIterator> { it.matchCost() }
        )
    }

    override fun twoPhaseIterator(): TwoPhaseIterator? {
        if (twoPhases.isEmpty()) {
            return null
        }
        val matchCost = twoPhases.sumOf { it.matchCost().toDouble() }.toFloat()
        val approx = approximation()
        return object : TwoPhaseIterator(approx) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                for (twoPhase in twoPhases) {
                    require(twoPhase.approximation().docID() == docID())
                    if (!twoPhase.matches()) {
                        return false
                    }
                }
                return true
            }

            override fun matchCost(): Float {
                return matchCost
            }
        }
    }

    override fun iterator(): DocIdSetIterator {
        return if (twoPhases.isEmpty())
            approximation()
        else
            TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator()!!)
    }

    private fun approximation(): DocIdSetIterator {
        val lead = approximations[0]!!

        return object : DocIdSetIterator() {
            var maxScore: Float = 0f
            var upTo: Int = -1

            override fun docID(): Int {
                return lead.docID()
            }

            override fun cost(): Long {
                return lead.cost()
            }

            @Throws(IOException::class)
            fun moveToNextBlock(target: Int) {
                if (minScore == 0f) {
                    upTo = target
                    maxScore = Float.Companion.POSITIVE_INFINITY
                } else {
                    upTo = advanceShallow(target)
                    maxScore = getMaxScore(upTo)
                }
            }

            @Throws(IOException::class)
            fun advanceTarget(target: Int): Int {
                var target = target
                if (target > upTo) {
                    moveToNextBlock(target)
                }

                while (true) {
                    require(upTo >= target)

                    if (maxScore >= minScore) {
                        return target
                    }

                    if (upTo == NO_MORE_DOCS) {
                        return NO_MORE_DOCS
                    }

                    target = upTo + 1

                    moveToNextBlock(target)
                }
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                return advance(docID() + 1)
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                return doNext(lead.advance(advanceTarget(target)))
            }

            @Throws(IOException::class)
            fun doNext(doc: Int): Int {
                var doc = doc
                advanceHead@ while (true) {
                    require(doc == lead.docID())

                    if (doc == NO_MORE_DOCS) {
                        return NO_MORE_DOCS
                    }

                    if (doc > upTo) {
                        // This check is useful when scorers return information about blocks
                        // that do not actually have any matches. Otherwise `doc` will always
                        // be in the current block already since it is always the result of
                        // lead.advance(advanceTarget(some_doc_id))
                        val nextTarget = advanceTarget(doc)
                        if (nextTarget != doc) {
                            doc = lead.advance(nextTarget)
                            continue
                        }
                    }

                    require(doc <= upTo)

                    // then find agreement with other iterators
                    for (i in 1..<approximations.size) {
                        val other = approximations[i]!!
                        // other.doc may already be equal to doc if we "continued advanceHead"
                        // on the previous iteration and the advance on the lead scorer exactly matched.
                        if (other.docID() < doc) {
                            val next = other.advance(doc)

                            if (next > doc) {
                                // iterator beyond the current doc - advance lead and continue to the new highest
                                // doc.
                                doc = lead.advance(advanceTarget(next))
                                continue@advanceHead
                            }
                        }

                        require(other.docID() == doc)
                    }

                    // success - all iterators are on the same doc and the score is competitive
                    return doc
                }
            }
        }
    }

    override fun docID(): Int {
        return scorers[0].docID()
    }

    @Throws(IOException::class)
    override fun score(): Float {
        var score = 0.0
        for (scorer in scorables) {
            score += scorer.score()
        }
        return score.toFloat()
    }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        // We use block boundaries of the lead scorer.
        // It is tempting to fold in other clauses as well to have better bounds of
        // the score, but then there is a risk of not progressing fast enough.
        val result = scorers[0].advanceShallow(target)
        // But we still need to shallow-advance other clauses, in order to have
        // better score upper bounds
        for (i in 1..<scorers.size) {
            scorers[i].advanceShallow(target)
        }
        return result
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        var sum = 0.0
        for (scorer in scorers) {
            sum += scorer.getMaxScore(upTo)
        }
        return sum.toFloat()
    }

    override var minCompetitiveScore: Float
        get() = minScore
        set(minScore) {
            this.minScore = minScore
        }

    override val children: MutableCollection<ChildScorable>
        get() {
            val children: ArrayList<ChildScorable> = ArrayList()
            for (scorer in scorers) {
                children.add(ChildScorable(scorer, "MUST"))
            }
            return children
        }
}
