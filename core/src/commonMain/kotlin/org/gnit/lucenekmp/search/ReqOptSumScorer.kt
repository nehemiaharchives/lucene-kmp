package org.gnit.lucenekmp.search


import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import okio.IOException
import kotlin.jvm.JvmName
import kotlin.math.min

/**
 * A Scorer for queries with a required part and an optional part. Delays skipTo() on the optional
 * part until a score() is needed.
 */
internal class ReqOptSumScorer(reqScorer: Scorer, optScorer: Scorer, scoreMode: ScoreMode) : Scorer() {
    private val reqScorer: Scorer
    private val optScorer: Scorer
    private val reqApproximation: DocIdSetIterator
    private val optApproximation: DocIdSetIterator
    private val optTwoPhase: TwoPhaseIterator?
    private val approximation: DocIdSetIterator
    private val twoPhase: TwoPhaseIterator?

    private var minScore = 0f
    private val reqMaxScore: Float
    private var optIsRequired = false

    /**
     * Construct a `ReqOptScorer`.
     *
     * @param reqScorer The required scorer. This must match.
     * @param optScorer The optional scorer. This is used for scoring only.
     * @param scoreMode How the produced scorers will be consumed.
     */
    init {
        checkNotNull(reqScorer)
        checkNotNull(optScorer)
        this.reqScorer = reqScorer
        this.optScorer = optScorer

        val reqTwoPhase = reqScorer.twoPhaseIterator()
        this.optTwoPhase = optScorer.twoPhaseIterator()
        reqApproximation = reqTwoPhase?.approximation() ?: reqScorer.iterator()
        optApproximation = optTwoPhase?.approximation() ?: optScorer.iterator()
        if (scoreMode !== ScoreMode.TOP_SCORES) {
            approximation = reqApproximation
            this.reqMaxScore = Float.Companion.POSITIVE_INFINITY
        } else {
            reqScorer.advanceShallow(0)
            optScorer.advanceShallow(0)
            this.reqMaxScore = reqScorer.getMaxScore(NO_MORE_DOCS)
            this.approximation =
                object : DocIdSetIterator() {
                    var upTo: Int = -1
                    var maxScore: Float = 0f

                    @Throws(IOException::class)
                    private fun moveToNextBlock(target: Int) {
                        upTo = advanceShallow(target)
                        val reqMaxScoreBlock = reqScorer.getMaxScore(upTo)
                        maxScore = getMaxScore(upTo)

                        // Potentially move to a conjunction
                        optIsRequired = reqMaxScoreBlock < minScore
                    }

                    @Throws(IOException::class)
                    private fun advanceImpacts(target: Int): Int {
                        var target = target
                        if (target > upTo) {
                            moveToNextBlock(target)
                        }

                        while (true) {
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
                        return advanceInternal(reqApproximation.docID() + 1)
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        return advanceInternal(target)
                    }

                    @Throws(IOException::class)
                    private fun advanceInternal(target: Int): Int {
                        if (target == NO_MORE_DOCS) {
                            reqApproximation.advance(target)
                            return NO_MORE_DOCS
                        }
                        var reqDoc = target
                        advanceHead@ while (true) {
                            if (minScore != 0f) {
                                reqDoc = advanceImpacts(reqDoc)
                            }
                            if (reqApproximation.docID() < reqDoc) {
                                reqDoc = reqApproximation.advance(reqDoc)
                            }
                            if (reqDoc == NO_MORE_DOCS || !optIsRequired) {
                                return reqDoc
                            }

                            val upperBound = if (reqMaxScore < minScore) NO_MORE_DOCS else upTo
                            if (reqDoc > upperBound) {
                                continue
                            }

                            // Find the next common doc within the current block
                            while (true) {
                                // invariant: reqDoc >= optDoc
                                var optDoc = optApproximation.docID()
                                if (optDoc < reqDoc) {
                                    optDoc = optApproximation.advance(reqDoc)
                                }
                                if (optDoc > upperBound) {
                                    reqDoc = upperBound + 1
                                    continue@advanceHead
                                }

                                if (optDoc != reqDoc) {
                                    reqDoc = reqApproximation.advance(optDoc)
                                    if (reqDoc > upperBound) {
                                        continue@advanceHead
                                    }
                                }

                                if (reqDoc == NO_MORE_DOCS || optDoc == reqDoc) {
                                    return reqDoc
                                }
                            }
                        }
                    }

                    override fun docID(): Int {
                        return reqApproximation.docID()
                    }

                    override fun cost(): Long {
                        return reqApproximation.cost()
                    }
                }
        }

        if (reqTwoPhase == null && optTwoPhase == null) {
            this.twoPhase = null
        } else {
            this.twoPhase =
                object : TwoPhaseIterator(approximation) {
                    @Throws(IOException::class)
                    override fun matches(): Boolean {
                        if (reqTwoPhase != null && !reqTwoPhase.matches()) {
                            return false
                        }
                        if (optTwoPhase != null) {
                            if (optIsRequired) {
                                // The below condition is rare and can only happen if we transitioned to
                                // optIsRequired=true
                                // after the opt approximation was advanced and before it was confirmed.
                                if (reqScorer.docID() != optApproximation.docID()) {
                                    if (optApproximation.docID() < reqScorer.docID()) {
                                        optApproximation.advance(reqScorer.docID())
                                    }
                                    if (reqScorer.docID() != optApproximation.docID()) {
                                        return false
                                    }
                                }
                                if (!optTwoPhase.matches()) {
                                    // Advance the iterator to make it clear it doesn't match the current doc id
                                    optApproximation.nextDoc()
                                    return false
                                }
                            } else if (optApproximation.docID() == reqScorer.docID() && !optTwoPhase.matches()
                            ) {
                                // Advance the iterator to make it clear it doesn't match the current doc id
                                optApproximation.nextDoc()
                            }
                        }
                        return true
                    }

                    override fun matchCost(): Float {
                        var matchCost = 1f
                        if (reqTwoPhase != null) {
                            matchCost += reqTwoPhase.matchCost()
                        }
                        if (optTwoPhase != null) {
                            matchCost += optTwoPhase.matchCost()
                        }
                        return matchCost
                    }
                }
        }
    }

    override fun twoPhaseIterator(): TwoPhaseIterator? {
        return twoPhase
    }

    override fun iterator(): DocIdSetIterator {
        return if (twoPhase == null) {
            approximation
        } else {
            TwoPhaseIterator.asDocIdSetIterator(twoPhase)
        }
    }

    override fun docID(): Int {
        return reqScorer.docID()
    }

    @Throws(IOException::class)
    override fun score(): Float {
        // TODO: sum into a double and cast to float if we ever send required clauses to BS1
        val curDoc = reqScorer.docID()
        var score = reqScorer.score()

        var optScorerDoc = optApproximation.docID()
        if (optScorerDoc < curDoc) {
            optScorerDoc = optApproximation.advance(curDoc)
            if (optTwoPhase != null && optScorerDoc == curDoc && !optTwoPhase.matches()) {
                optScorerDoc = optApproximation.nextDoc()
            }
        }
        if (optScorerDoc == curDoc) {
            score += optScorer.score()
        }

        return score
    }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        var upTo = reqScorer.advanceShallow(target)
        if (optScorer.docID() <= target) {
            upTo = min(upTo, optScorer.advanceShallow(target))
        } else if (optScorer.docID() != NO_MORE_DOCS) {
            upTo = min(upTo, optScorer.docID() - 1)
        }
        return upTo
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        var maxScore = reqScorer.getMaxScore(upTo)
        if (optScorer.docID() <= upTo) {
            maxScore += optScorer.getMaxScore(upTo)
        }
        return maxScore
    }

    @JvmName("setMinCompetitiveScoreKt")
    @Throws(IOException::class)
    fun setMinCompetitiveScore(minScore: Float) {
        this.minScore = minScore
        // Potentially move to a conjunction
        if (reqMaxScore < minScore) {
            optIsRequired = true
            if (reqMaxScore == 0f) {
                // If the required clause doesn't contribute scores, we can propagate the minimum
                // competitive score to the optional clause. This happens when the required clause is a
                // FILTER clause.
                // In theory we could generalize this and set minScore - reqMaxScore as a minimum
                // competitive score, but it's unlikely to help in practice unless reqMaxScore is much
                // smaller than typical scores of the optional clause.
                optScorer.minCompetitiveScore = minScore
            }
        }
    }

    override val children: MutableCollection<ChildScorable>
        get() {
            val children: ArrayList<ChildScorable> = ArrayList(2)
            children.add(ChildScorable(reqScorer, "MUST"))
            children.add(ChildScorable(optScorer, "SHOULD"))
            return children
        }
}
