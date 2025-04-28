package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.MathUtil
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min

internal class MaxScoreBulkScorer(private val maxDoc: Int, scorers: MutableList<Scorer>, filter: Scorer?) :
    BulkScorer() {
    // All scorers, sorted by increasing max score.
    val allScorers: Array<DisiWrapper?> = kotlin.arrayOfNulls(scorers.size)
    private val scratch: Array<DisiWrapper?> = kotlin.arrayOfNulls(allScorers.size)

    // These are the last scorers from `allScorers` that are "essential", ie. required for a match to
    // have a competitive score.
    private val essentialQueue: DisiPriorityQueue

    // Index of the first essential scorer, ie. essentialQueue contains all scorers from
    // allScorers[firstEssentialScorer:]. All scorers below this index are non-essential.
    var firstEssentialScorer: Int = 0

    // Index of the first scorer that is required, this scorer and all following scorers are required
    // for a document to match.
    var firstRequiredScorer: Int = 0

    // The minimum value of minCompetitiveScore that would produce a more favorable partitioning.
    var nextMinCompetitiveScore: Float = 0f
    private val cost: Long
    var minCompetitiveScore: Float = 0f
    private val scorable: Score = this.Score()
    val maxScoreSums: DoubleArray
    private val filter: DisiWrapper? = if (filter == null) null else DisiWrapper(filter, false)

    private val windowMatches = LongArray(FixedBitSet.bits2words(INNER_WINDOW_SIZE))
    private val windowScores = DoubleArray(INNER_WINDOW_SIZE)

    // Number of outer windows that have been evaluated
    private var numOuterWindows = 0

    // Number of candidate matches so far
    private var numCandidates = 0

    // Minimum window size. See #computeOuterWindowMax where we have heuristics that adjust the
    // minimum window size based on the average number of candidate matches per outer window, to keep
    // the per-window overhead under control.
    private var minWindowSize = 1

    init {
        var i = 0
        var cost: Long = 0
        for (scorer in scorers) {
            val w = DisiWrapper(scorer, true)
            cost += w.cost
            allScorers[i++] = w
        }
        this.cost = cost
        essentialQueue = DisiPriorityQueue.ofMaxSize(allScorers.size)
        maxScoreSums = DoubleArray(allScorers.size)
    }

    @Throws(IOException::class)
    override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
        collector.setScorer(scorable)

        // This scorer computes outer windows based on impacts that are stored in the index. These outer
        // windows should be small enough to provide good upper bounds of scores, and big enough to make
        // sure we spend more time collecting docs than recomputing windows.
        // Then within these outer windows, it creates inner windows of size WINDOW_SIZE that help
        // collect matches into a bitset and save the overhead of rebalancing the priority queue on
        // every match.
        var outerWindowMin = min
        outer@ while (outerWindowMin < max) {
            var outerWindowMax = computeOuterWindowMax(outerWindowMin)
            outerWindowMax = min(outerWindowMax, max)

            while (true) {
                updateMaxWindowScores(outerWindowMin, outerWindowMax)
                if (!partitionScorers()) {
                    // No matches in this window
                    outerWindowMin = outerWindowMax
                    continue@outer
                }

                // There is a dependency between windows and maximum scores, as we compute windows based on
                // maximum scores and maximum scores based on windows.
                // So the approach consists of starting by computing a window based on the set of essential
                // scorers from the _previous_ window and then iteratively recompute maximum scores and
                // windows as long as the window size decreases.
                // In general the set of essential scorers is rather stable over time so this would exit
                // after a single iteration, but there is a change that some scorers got swapped between the
                // set of essential and non-essential scorers, in which case there may be multiple
                // iterations of this loop.
                val newOuterWindowMax = computeOuterWindowMax(outerWindowMin)
                if (newOuterWindowMax >= outerWindowMax) {
                    break
                }
                outerWindowMax = newOuterWindowMax
            }

            var top: DisiWrapper = essentialQueue.top()!!
            while (top.doc < outerWindowMin) {
                top.doc = top.iterator!!.advance(outerWindowMin)
                top = essentialQueue.updateTop()
            }

            while (top.doc < outerWindowMax) {
                scoreInnerWindow(collector, acceptDocs, outerWindowMax, filter)
                top = essentialQueue.top()!!
                if (minCompetitiveScore >= nextMinCompetitiveScore) {
                    // The minimum competitive score increased substantially, so we can now partition scorers
                    // in a more favorable way.
                    break
                }
            }

            outerWindowMin = min(top.doc, outerWindowMax)
            ++numOuterWindows
        }

        return nextCandidate(max)
    }

    @Throws(IOException::class)
    private fun scoreInnerWindow(
        collector: LeafCollector, acceptDocs: Bits?, max: Int, filter: DisiWrapper?
    ) {
        if (filter != null) {
            scoreInnerWindowWithFilter(collector, acceptDocs, max, filter)
        } else if (allScorers.size - firstRequiredScorer >= 2) {
            scoreInnerWindowAsConjunction(collector, acceptDocs, max)
        } else {
            val top: DisiWrapper = essentialQueue.top()!!
            val top2 = essentialQueue.top2()
            if (top2 == null) {
                scoreInnerWindowSingleEssentialClause(collector, acceptDocs, max)
            } else if (top2.doc - INNER_WINDOW_SIZE / 2 >= top.doc) {
                // The first half of the window would match a single clause. Let's collect this single
                // clause until the next doc ID of the next clause.
                scoreInnerWindowSingleEssentialClause(collector, acceptDocs, min(max, top2.doc))
            } else {
                scoreInnerWindowMultipleEssentialClauses(collector, acceptDocs, max)
            }
        }
    }

    @Throws(IOException::class)
    private fun scoreInnerWindowWithFilter(
        collector: LeafCollector, acceptDocs: Bits?, max: Int, filter: DisiWrapper
    ) {
        // TODO: Sometimes load the filter into a bitset and use the more optimized execution paths with
        // this bitset as `acceptDocs`

        var top: DisiWrapper = essentialQueue.top()!!
        require(top.doc < max)
        while (top.doc < filter.doc) {
            top.doc = top.approximation!!.advance(filter.doc)
            top = essentialQueue.updateTop()
        }

        // Only score an inner window, after that we'll check if the min competitive score has increased
        // enough for a more favorable partitioning to be used.
        val innerWindowMin = top.doc
        val innerWindowMax = min(max.toLong(), innerWindowMin.toLong() + INNER_WINDOW_SIZE).toInt()

        while (top.doc < innerWindowMax) {
            require(
                filter.doc <= top.doc // invariant
            )
            if (filter.doc < top.doc) {
                filter.doc = filter.approximation!!.advance(top.doc)
            }

            if (filter.doc != top.doc) {
                do {
                    top.doc = top.iterator!!.advance(filter.doc)
                    top = essentialQueue.updateTop()
                } while (top.doc < filter.doc)
            } else {
                val doc = top.doc
                val match =
                    (acceptDocs == null || acceptDocs.get(doc))
                            && (filter.twoPhaseView == null || filter.twoPhaseView.matches())
                var score = 0.0
                do {
                    if (match) {
                        score += top.scorer.score()
                    }
                    top.doc = top.iterator!!.nextDoc()
                    top = essentialQueue.updateTop()
                } while (top.doc == doc)

                if (match) {
                    scoreNonEssentialClauses(collector, doc, score, firstEssentialScorer)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun scoreInnerWindowSingleEssentialClause(
        collector: LeafCollector, acceptDocs: Bits?, upTo: Int
    ) {
        val top: DisiWrapper = essentialQueue.top()!!

        // single essential clause in this window, we can iterate it directly and skip the bitset.
        // this is a common case for 2-clauses queries
        var doc = top.doc
        while (doc < upTo) {
            if (acceptDocs != null && !acceptDocs.get(doc)) {
                doc = top.iterator!!.nextDoc()
                continue
            }
            scoreNonEssentialClauses(collector, doc, top.scorable!!.score().toDouble(), firstEssentialScorer)
            doc = top.iterator!!.nextDoc()
        }
        top.doc = top.iterator!!.docID()
        essentialQueue.updateTop()
    }

    @Throws(IOException::class)
    private fun scoreInnerWindowAsConjunction(collector: LeafCollector, acceptDocs: Bits?, max: Int) {
        require(firstEssentialScorer == allScorers.size - 1)
        require(firstRequiredScorer <= allScorers.size - 2)
        val lead1 = allScorers[allScorers.size - 1]!!
        require(essentialQueue.size() == 1)
        require(lead1 === essentialQueue.top())
        val lead2 = allScorers[allScorers.size - 2]!!
        if (lead1.doc < lead2.doc) {
            lead1.doc = lead1.iterator!!.advance(min(lead2.doc, max))
        }
        // maximum score contribution of all scorers but the lead
        val maxScoreSumAtLead2 = maxScoreSums[allScorers.size - 2]

        outer@ while (lead1.doc < max) {
            if (acceptDocs != null && !acceptDocs.get(lead1.doc)) {
                lead1.doc = lead1.iterator!!.nextDoc()
                continue
            }

            var score: Double = lead1.scorable!!.score().toDouble()

            // We specialize handling the second best scorer, which seems to help a bit with performance.
            // But this is the exact same logic as in the below for loop.
            if ((MathUtil.sumUpperBound(score + maxScoreSumAtLead2, allScorers.size).toFloat())
                < minCompetitiveScore
                    ) {
                // a competitive match is not possible according to max scores, skip to the next candidate
                lead1.doc = lead1.iterator!!.nextDoc()
                continue
            }

            if (lead2.doc < lead1.doc) {
                lead2.doc = lead2.iterator!!.advance(lead1.doc)
            }
            if (lead2.doc != lead1.doc) {
                lead1.doc = lead1.iterator!!.advance(min(lead2.doc, max))
                continue
            }

            score += lead2.scorable!!.score()

            for (i in allScorers.size - 3 downTo firstRequiredScorer) {
                if ((MathUtil.sumUpperBound(score + maxScoreSums[i], allScorers.size).toFloat())
                    < minCompetitiveScore
                        ) {
                    // a competitive match is not possible according to max scores, skip to the next candidate
                    lead1.doc = lead1.iterator!!.nextDoc()
                    continue@outer
                }

                val w = allScorers[i]!!
                if (w.doc < lead1.doc) {
                    w.doc = w.iterator!!.advance(lead1.doc)
                }
                if (w.doc != lead1.doc) {
                    lead1.doc = lead1.iterator!!.advance(min(w.doc, max))
                    continue@outer
                }
                score += w.scorable!!.score()
            }

            scoreNonEssentialClauses(collector, lead1.doc, score, firstRequiredScorer)
            lead1.doc = lead1.iterator!!.nextDoc()
        }
    }

    @Throws(IOException::class)
    private fun scoreInnerWindowMultipleEssentialClauses(
        collector: LeafCollector, acceptDocs: Bits?, max: Int
    ) {
        var top: DisiWrapper = essentialQueue.top()!!

        val innerWindowMin = top.doc
        val innerWindowMax = min(max.toLong(), innerWindowMin.toLong() + INNER_WINDOW_SIZE).toInt()

        // Collect matches of essential clauses into a bitset
        do {
            var doc = top.doc
            while (doc < innerWindowMax) {
                if (acceptDocs == null || acceptDocs.get(doc)) {
                    val i = doc - innerWindowMin
                    windowMatches[i ushr 6] = windowMatches[i ushr 6] or (1L shl i)
                    windowScores[i] += top.scorable!!.score()
                }
                doc = top.iterator!!.nextDoc()
            }
            top.doc = top.iterator!!.docID()
            top = essentialQueue.updateTop()
        } while (top.doc < innerWindowMax)

        for (wordIndex in windowMatches.indices) {
            var bits = windowMatches[wordIndex]
            windowMatches[wordIndex] = 0L
            while (bits != 0L) {
                val ntz: Int = Long.numberOfTrailingZeros(bits)
                bits = bits xor (1L shl ntz)
                val index = wordIndex shl 6 or ntz
                val doc = innerWindowMin + index
                val score = windowScores[index]
                windowScores[index] = 0.0

                scoreNonEssentialClauses(collector, doc, score, firstEssentialScorer)
            }
        }
    }

    @Throws(IOException::class)
    private fun computeOuterWindowMax(windowMin: Int): Int {
        // Only use essential scorers to compute the window's max doc ID, in order to avoid constantly
        // recomputing max scores over small windows
        val firstWindowLead = min(firstEssentialScorer, allScorers.size - 1)
        var windowMax = DocIdSetIterator.NO_MORE_DOCS
        for (i in firstWindowLead..<allScorers.size) {
            val scorer = allScorers[i]!!
            if (filter == null || scorer.cost >= filter.cost) {
                val upTo = scorer.scorer.advanceShallow(max(scorer.doc, windowMin))
                windowMax = min(windowMax.toLong(), upTo + 1L).toInt() // upTo is inclusive
            }
        }

        if (allScorers.size - firstWindowLead > 1) {
            // The more clauses we consider to compute outer windows, the higher chances that one of these
            // clauses has a block boundary in the next few doc IDs. This situation can result in more
            // time spent computing maximum scores per outer window than evaluating hits. To avoid such
            // situations, we target at least 32 candidate matches per clause per outer window on average,
            // to make sure we amortize the cost of computing maximum scores.
            val threshold = numOuterWindows * 32L * allScorers.size
            minWindowSize = if (numCandidates < threshold) {
                min(minWindowSize shl 1, INNER_WINDOW_SIZE)
            } else {
                1
            }

            val minWindowMax = min(Int.Companion.MAX_VALUE.toLong(), windowMin.toLong() + minWindowSize).toInt()
            windowMax = max(windowMax, minWindowMax)
        }

        return windowMax
    }

    @Throws(IOException::class)
    fun updateMaxWindowScores(windowMin: Int, windowMax: Int) {
        for (scorer in allScorers) {
            if (scorer!!.doc < windowMax) {
                if (scorer.doc < windowMin) {
                    // Make sure to advance shallow if necessary to get as good score upper bounds as
                    // possible.
                    scorer.scorer.advanceShallow(windowMin)
                }
                scorer.maxWindowScore = scorer.scorer.getMaxScore(windowMax - 1)
            } else {
                // This scorer has no documents in the considered window.
                scorer.maxWindowScore = 0F
            }
        }
    }

    @Throws(IOException::class)
    private fun scoreNonEssentialClauses(
        collector: LeafCollector, doc: Int, essentialScore: Double, numNonEssentialClauses: Int
    ) {
        ++numCandidates

        var score = essentialScore
        for (i in numNonEssentialClauses - 1 downTo 0) {
            val maxPossibleScore =
                MathUtil.sumUpperBound(score + maxScoreSums[i], allScorers.size).toFloat()
            if (maxPossibleScore < minCompetitiveScore) {
                // Hit is not competitive.
                return
            }

            val scorer = allScorers[i]
            if (scorer!!.doc < doc) {
                scorer.doc = scorer.iterator!!.advance(doc)
            }
            if (scorer.doc == doc) {
                score += scorer.scorable!!.score()
            }
        }

        scorable.score = score.toFloat()
        collector.collect(doc)
    }

    fun partitionScorers(): Boolean {
        // Partitioning scorers is an optimization problem: the optimal set of non-essential scorers is
        // the subset of scorers whose sum of max window scores is less than the minimum competitive
        // score that maximizes the sum of costs.
        // Computing the optimal solution to this problem would take O(2^num_clauses). As a first
        // approximation, we take the first scorers sorted by max_window_score / cost whose sum of max
        // scores is less than the minimum competitive scores. In the common case, maximum scores are
        // inversely correlated with document frequency so this is the same as only sorting by maximum
        // score, as described in the MAXSCORE paper and gives the optimal solution. However, this can
        // make a difference when using custom scores (like FuzzyQuery), high query-time boosts, or
        // scoring based on wacky weights.
        System.arraycopy(allScorers, 0, scratch, 0, allScorers.size)
        // Do not use Comparator#comparingDouble below, it might cause unnecessary allocations
        Arrays.sort(
            scratch as Array<DisiWrapper>
        ) { scorer1: DisiWrapper, scorer2: DisiWrapper ->
            Double.compare(
                scorer1.maxWindowScore.toDouble() / max(1L, scorer1.cost),
                scorer2.maxWindowScore.toDouble() / max(1L, scorer2.cost)
            )
        }
        var maxScoreSum = 0.0
        firstEssentialScorer = 0
        nextMinCompetitiveScore = Float.Companion.POSITIVE_INFINITY
        for (i in allScorers.indices) {
            val w = scratch[i]
            val newMaxScoreSum = maxScoreSum + w.maxWindowScore
            val maxScoreSumFloat =
                MathUtil.sumUpperBound(newMaxScoreSum, firstEssentialScorer + 1).toFloat()
            if (maxScoreSumFloat < minCompetitiveScore) {
                maxScoreSum = newMaxScoreSum
                allScorers[firstEssentialScorer] = w
                maxScoreSums[firstEssentialScorer] = maxScoreSum
                firstEssentialScorer++
            } else {
                allScorers[allScorers.size - 1 - (i - firstEssentialScorer)] = w
                nextMinCompetitiveScore = min(maxScoreSumFloat, nextMinCompetitiveScore)
            }
        }

        firstRequiredScorer = allScorers.size

        if (firstEssentialScorer == allScorers.size) {
            return false
        }

        essentialQueue.clear()
        for (i in firstEssentialScorer..<allScorers.size) {
            essentialQueue.add(allScorers[i]!!)
        }

        if (firstEssentialScorer == allScorers.size - 1) { // single essential clause
            // If there is a single essential clause and matching it plus all non-essential clauses but
            // the best one is not enough to yield a competitive match, the we know that hits must match
            // both the essential clause and the best non-essential clause. Here are some examples when
            // this optimization would kick in:
            //   `quick fox`  when maxscore(quick) = 1, maxscore(fox) = 1, minCompetitiveScore = 1.5
            //   `the quick fox` when maxscore (the) = 0.1, maxscore(quick) = 1, maxscore(fox) = 1,
            //       minCompetitiveScore = 1.5
            firstRequiredScorer = allScorers.size - 1
            var maxRequiredScore: Double = allScorers[firstEssentialScorer]!!.maxWindowScore.toDouble()

            while (firstRequiredScorer > 0) {
                var maxPossibleScoreWithoutPreviousClause = maxRequiredScore
                if (firstRequiredScorer > 1) {
                    maxPossibleScoreWithoutPreviousClause += maxScoreSums[firstRequiredScorer - 2]
                }
                if (maxPossibleScoreWithoutPreviousClause.toFloat() >= minCompetitiveScore) {
                    break
                }
                // The sum of maximum scores ignoring the previous clause is less than the minimum
                // competitive
                --firstRequiredScorer
                maxRequiredScore += allScorers[firstRequiredScorer]!!.maxWindowScore
            }
        }

        return true
    }

    /** Return the next candidate on or after `rangeEnd`.  */
    private fun nextCandidate(rangeEnd: Int): Int {
        if (rangeEnd >= maxDoc) {
            return DocIdSetIterator.NO_MORE_DOCS
        }

        var next = DocIdSetIterator.NO_MORE_DOCS
        for (scorer in allScorers) {
            if (scorer!!.doc < rangeEnd) {
                return rangeEnd
            } else {
                next = min(next, scorer.doc)
            }
        }
        return next
    }

    override fun cost(): Long {
        return cost
    }

    private inner class Score : Scorable() {
        var score: Float = 0f

        override fun score(): Float {
            return score
        }

        @JvmName("setMinCompetitiveScoreKt")
        @Throws(IOException::class)
        fun setMinCompetitiveScore(minScore: Float) {
            this@MaxScoreBulkScorer.minCompetitiveScore = minScore
        }
    }

    companion object {
        const val INNER_WINDOW_SIZE: Int = 1 shl 12
    }
}
