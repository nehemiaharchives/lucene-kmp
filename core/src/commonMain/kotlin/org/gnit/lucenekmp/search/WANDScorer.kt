package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.search.ScorerUtil.costWithMinShouldMatch
import org.gnit.lucenekmp.util.MathUtil
import org.gnit.lucenekmp.search.DisiPriorityQueueN.Companion.leftNode
import org.gnit.lucenekmp.search.DisiPriorityQueueN.Companion.parentNode
import org.gnit.lucenekmp.search.DisiPriorityQueueN.Companion.rightNode
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Collections
import org.gnit.lucenekmp.jdkport.MIN_EXPONENT
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.jdkport.isInfinite
import org.gnit.lucenekmp.jdkport.isNaN
import kotlin.jvm.JvmName
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * This implements the WAND (Weak AND) algorithm for dynamic pruning described in "Efficient Query
 * Evaluation using a Two-Level Retrieval Process" by Broder, Carmel, Herscovici, Soffer and Zien.
 * Enhanced with techniques described in "Faster Top-k Document Retrieval Using Block-Max Indexes"
 * by Ding and Suel. For scoreMode == [ScoreMode.TOP_SCORES], this scorer maintains a feedback
 * loop with the collector in order to know at any time the minimum score that is required in order
 * for a hit to be competitive.
 *
 *
 * The implementation supports both minCompetitiveScore by enforce that `∑ max_score >=
 * minCompetitiveScore`, and minShouldMatch by enforcing `freq >= minShouldMatch`. It keeps
 * sub scorers in 3 different places: - tail: a heap that contains scorers that are behind the
 * desired doc ID. These scorers are ordered by cost so that we can advance the least costly ones
 * first. - lead: a linked list of scorer that are positioned on the desired doc ID - head: a heap
 * that contains scorers which are beyond the desired doc ID, ordered by doc ID in order to move
 * quickly to the next candidate.
 *
 *
 * When scoreMode == [ScoreMode.TOP_SCORES], it leverages the [ ][Scorer.getMaxScore] from each scorer in order to know when it may call [ ][DocIdSetIterator.advance] rather than [DocIdSetIterator.nextDoc] to move to the next
 * competitive hit. When scoreMode != [ScoreMode.TOP_SCORES], block-max scoring related logic
 * is skipped. Finding the next match consists of first setting the desired doc ID to the least
 * entry in 'head', and then advance 'tail' until there is a match, by meeting the configured `freq >= minShouldMatch` and / or `∑ max_score >= minCompetitiveScore` requirements.
 */
internal class WANDScorer(
    scorers: MutableCollection<Scorer>,
    minShouldMatch: Int,
    scoreMode: ScoreMode,
    leadCost: Long
) : Scorer() {
    private val scalingFactor: Int

    // scaled min competitive score
    override var minCompetitiveScore: Float

    private val allScorers: Array<Scorer>

    // list of scorers which 'lead' the iteration and are currently
    // positioned on 'doc'. This is sometimes called the 'pivot' in
    // some descriptions of WAND (Weak AND).
    var lead: DisiWrapper? = null
    var doc: Int // current doc ID of the leads
    var leadScore: Double = 0.0 // score of the leads

    // priority queue of scorers that are too advanced compared to the current
    // doc. Ordered by doc ID.
    val head: DisiPriorityQueue

    // priority queue of scorers which are behind the current doc.
    // Ordered by maxScore.
    val tail: Array<DisiWrapper?>
    var tailMaxScore: Long = 0 // sum of the max scores of scorers in 'tail'
    var tailSize: Int = 0

    val cost: Long

    var upTo: Int // upper bound for which max scores are valid

    val minShouldMatch: Int
    var freq: Int = 0

    val scoreMode: ScoreMode
    val leadCost: Long

    init {
        require(minShouldMatch < scorers.size) { "minShouldMatch should be < the number of scorers" }

        allScorers = scorers.toTypedArray()
        this.minCompetitiveScore = 0f

        require(minShouldMatch >= 0) { "minShouldMatch should not be negative, but got $minShouldMatch" }
        this.minShouldMatch = minShouldMatch

        this.doc = -1
        this.upTo = -1 // will be computed on the first call to nextDoc/advance

        this.scoreMode = scoreMode

        head = DisiPriorityQueue.ofMaxSize(scorers.size)
        // there can be at most num_scorers - 1 scorers beyond the current position
        tail = kotlin.arrayOfNulls<DisiWrapper>(scorers.size)

        if (this.scoreMode === ScoreMode.TOP_SCORES) {
            // To avoid accuracy issues with floating-point numbers, this scorer operates on scaled longs.
            // How do you choose the scaling factor The thing is that we want to retain as many
            // significant bits as possible, but not too many, otherwise operations on longs would be more
            // precise than the equivalent operations on their unscaled counterparts and we might skip too
            // many hits. So we compute the maximum possible score produced by this scorer, which is the
            // sum of the maximum scores of each clause, and compute a scaling factor that would preserve
            // 24 bits of accuracy - the number of mantissa bits of single-precision floating-point
            // numbers.
            var maxScoreSumDouble = 0.0
            for (scorer in scorers) {
                scorer.advanceShallow(0)
                val maxScore = scorer.getMaxScore(DocIdSetIterator.NO_MORE_DOCS)
                maxScoreSumDouble += maxScore.toDouble()
            }
            val maxScoreSum = MathUtil.sumUpperBound(maxScoreSumDouble, scorers.size).toFloat()
            this.scalingFactor = scalingFactor(maxScoreSum)
        } else {
            this.scalingFactor = 0
        }

        for (scorer in scorers) {
            // Ideally we would pass true when scoreMode == TOP_SCORES and false otherwise, but this would
            // break the optimization as there could then be 3 different impls of DocIdSetIterator
            // (ImpactsEnum, PostingsEnum and <Else>). So we pass true to favor disjunctions sorted by
            // descending score as opposed to non-scoring disjunctions whose minShouldMatch is greater
            // than 1.
            addUnpositionedLead(DisiWrapper(scorer, true))
        }

        this.cost =
            costWithMinShouldMatch(
                scorers.map { it.iterator().cost() }.asSequence(),
                scorers.size,
                minShouldMatch
            )
        this.leadCost = leadCost
    }

    // returns a boolean so that it can be called from assert
    // the return value is useless: it always returns true
    @Throws(IOException::class)
    private fun ensureConsistent(): Boolean {
        if (scoreMode === ScoreMode.TOP_SCORES) {
            var maxScoreSum: Long = 0
            for (i in 0..<tailSize) {
                require(tail[i]!!.doc < doc)
                maxScoreSum = Math.addExact(maxScoreSum, tail[i]!!.scaledMaxScore)
            }
            require(maxScoreSum == tailMaxScore) { "$maxScoreSum $tailMaxScore" }

            val leadScores: MutableList<Float> = ArrayList()
            var w = lead
            while (w != null) {
                require(w.doc == doc)
                leadScores.add(w.scorable!!.score())
                w = w.next
            }
            // Make sure to recompute the sum in the same order to get the same floating point rounding
            // errors.
            Collections.reverse(leadScores)
            var recomputedLeadScore = 0.0
            for (score in leadScores) {
                recomputedLeadScore += score.toDouble()
            }
            require(recomputedLeadScore == leadScore)

            require(minCompetitiveScore == 0.0f || tailMaxScore < minCompetitiveScore || tailSize < minShouldMatch)
            require(doc <= upTo)
        }

        for (w in head) {
            if (lead == null) { // After calling advance() but before matches()
                require(w!!.doc >= doc)
            } else {
                require(w!!.doc > doc)
            }
        }

        return true
    }

    @JvmName("setMinCompetitiveScoreKt")
    @Throws(IOException::class)
    fun setMinCompetitiveScore(minScore: Float) {
        // Let this disjunction know about the new min score so that it can skip
        // over clauses that produce low scores.
        require(
            scoreMode === ScoreMode.TOP_SCORES
        ) { "minCompetitiveScore can only be set for ScoreMode.TOP_SCORES, but got: $scoreMode" }
        require(minScore >= 0)
        val scaledMinScore = scaleMinScore(minScore, scalingFactor)
        require(scaledMinScore >= minCompetitiveScore)
        minCompetitiveScore = scaledMinScore.toFloat()
    }

    @get:Throws(IOException::class)
    override val children: MutableCollection<ChildScorable>
        get() {
            val matchingChildren: MutableList<ChildScorable> = ArrayList()
            advanceAllTail()
            var s = lead
            while (s != null) {
                matchingChildren.add(ChildScorable(s.scorer, "SHOULD"))
                s = s.next
            }
            return matchingChildren
        }

    override fun iterator(): DocIdSetIterator {
        return TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator())
    }

    override fun twoPhaseIterator(): TwoPhaseIterator {
        val approximation: DocIdSetIterator =
            object : DocIdSetIterator() {
                override fun docID(): Int {
                    return doc
                }

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    return advance(doc + 1)
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    // Move 'lead' iterators back to the tail
                    pushBackLeads(target)

                    // Make sure `head` is also on or beyond `target`
                    var headTop = advanceHead(target)

                    if (scoreMode === ScoreMode.TOP_SCORES && (headTop == null || headTop.doc > upTo)) {
                        // Update score bounds if necessary
                        moveToNextBlock(target)
                        require(upTo >= target)
                        headTop = head.top()
                    }

                    return headTop?.doc?.also { doc = it } ?: NO_MORE_DOCS.also { doc = it }
                }

                override fun cost(): Long {
                    return cost
                }
            }
        return object : TwoPhaseIterator(approximation) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                require(lead == null)
                moveToNextCandidate()

                var scaledLeadScore: Long = 0
                if (scoreMode === ScoreMode.TOP_SCORES) {
                    scaledLeadScore =
                        scaleMaxScore(
                            MathUtil.sumUpperBound(leadScore, FLOAT_MANTISSA_BITS).toFloat(), scalingFactor
                        )
                }

                while (scaledLeadScore < minCompetitiveScore || freq < minShouldMatch) {
                    require(ensureConsistent())
                    if (scaledLeadScore + tailMaxScore < minCompetitiveScore
                        || freq + tailSize < minShouldMatch
                    ) {
                        return false
                    } else {
                        // a match on doc is still possible, try to
                        // advance scorers from the tail
                        val prevLead = lead
                        advanceTail()
                        if (scoreMode === ScoreMode.TOP_SCORES && lead !== prevLead) {
                            require(prevLead === lead!!.next)
                            scaledLeadScore =
                                scaleMaxScore(
                                    MathUtil.sumUpperBound(leadScore, FLOAT_MANTISSA_BITS).toFloat(),
                                    scalingFactor
                                )
                        }
                    }
                }

                require(ensureConsistent())
                return true
            }

            override fun matchCost(): Float {
                // maximum number of scorer that matches() might advance
                return tail.size.toFloat()
            }
        }
    }

    /** Add a disi to the linked list of leads.  */
    @Throws(IOException::class)
    private fun addLead(lead: DisiWrapper) {
        lead.next = this.lead
        this.lead = lead
        freq += 1
        if (scoreMode === ScoreMode.TOP_SCORES) {
            leadScore += lead.scorable!!.score()
        }
    }

    /** Add a disi to the linked list of leads.  */
    private fun addUnpositionedLead(lead: DisiWrapper) {
        require(lead.doc == -1)
        lead.next = this.lead
        this.lead = lead
        freq += 1
    }

    /** Move disis that are in 'lead' back to the tail.  */
    @Throws(IOException::class)
    private fun pushBackLeads(target: Int) {
        var s = lead
        while (s != null) {
            val evicted = insertTailWithOverFlow(s)
            if (evicted != null) {
                evicted.doc = evicted.iterator!!.advance(target)
                head.add(evicted)
            }
            s = s.next
        }
        lead = null
    }

    /** Make sure all disis in 'head' are on or after 'target'.  */
    @Throws(IOException::class)
    private fun advanceHead(target: Int): DisiWrapper? {
        var headTop = head.top()
        while (headTop != null && headTop.doc < target) {
            val evicted = insertTailWithOverFlow(headTop)
            if (evicted != null) {
                evicted.doc = evicted.iterator!!.advance(target)
                headTop = head.updateTop(evicted)
            } else {
                head.pop()
                headTop = head.top()
            }
        }
        return headTop
    }

    @Throws(IOException::class)
    private fun advanceTail(disi: DisiWrapper) {
        disi.doc = disi.iterator!!.advance(doc)
        if (disi.doc == doc) {
            addLead(disi)
        } else {
            head.add(disi)
        }
    }

    /**
     * Pop the entry from the 'tail' that has the greatest score contribution, advance it to the
     * current doc and then add it to 'lead' or 'head' depending on whether it matches.
     */
    @Throws(IOException::class)
    private fun advanceTail() {
        val top = popTail()
        advanceTail(top)
    }

    @Throws(IOException::class)
    private fun updateMaxScores(target: Int) {
        var newUpTo = DocIdSetIterator.NO_MORE_DOCS
        // If we have entries in 'head', we treat them all as leads and take the minimum of their next
        // block boundaries as a next boundary.
        // We don't take entries in 'tail' into account on purpose: 'tail' is supposed to contain the
        // least score contributors, and taking them into account might not move the boundary fast
        // enough, so we'll waste CPU re-computing the next boundary all the time.
        // Likewise, we ignore clauses whose cost is greater than the lead cost to avoid recomputing
        // per-window max scores over and over again. In the event when this makes us compute upTo as
        // NO_MORE_DOCS, this scorer will effectively implement WAND rather than block-max WAND.
        for (w in head) {
            if (w!!.doc <= newUpTo && w.cost <= leadCost) {
                newUpTo = min(w.scorer.advanceShallow(w.doc), newUpTo)
            }
        }
        // Only look at the tail if none of the `head` clauses had a block we could reuse and if its
        // cost is less than or equal to the lead cost.
        if (newUpTo == DocIdSetIterator.NO_MORE_DOCS && tailSize > 0 && tail[0]!!.cost <= leadCost) {
            newUpTo = tail[0]!!.scorer.advanceShallow(target)
            // upTo must be on or after the least `head` doc
            val headTop = head.top()
            if (headTop != null) {
                newUpTo = max(newUpTo, headTop.doc)
            }
        }
        upTo = newUpTo

        // Now update the max scores of clauses that are before upTo.
        for (w in head) {
            if (w!!.doc <= upTo) {
                w.scaledMaxScore = scaleMaxScore(w.scorer.getMaxScore(newUpTo), scalingFactor)
            }
        }

        tailMaxScore = 0
        for (i in 0..<tailSize) {
            val w = tail[i]!!
            w.scorer.advanceShallow(target)
            w.scaledMaxScore = scaleMaxScore(w.scorer.getMaxScore(upTo), scalingFactor)
            upHeapMaxScore(tail as Array<DisiWrapper>, i) // the heap might need to be reordered
            tailMaxScore += w.scaledMaxScore
        }

        // We need to make sure that entries in 'tail' alone cannot match
        // a competitive hit.
        while (tailSize > 0 && tailMaxScore >= minCompetitiveScore) {
            val w = popTail()
            w.doc = w.iterator!!.advance(target)
            head.add(w)
        }
    }

    /**
     * Update `upTo` and maximum scores of sub scorers so that `upTo` is greater than or
     * equal to the next candidate after `target`, i.e. the top of `head`.
     */
    @Throws(IOException::class)
    private fun moveToNextBlock(target: Int) {
        var target = target
        require(lead == null)

        while (upTo < DocIdSetIterator.NO_MORE_DOCS) {
            if (head.size() == 0) {
                // All clauses could fit in the tail, which means that the sum of the
                // maximum scores of sub clauses is less than the minimum competitive score.
                // Move to the next block until this condition becomes false.
                target = max(target, upTo + 1)
                updateMaxScores(target)
            } else if (head.top()!!.doc > upTo) {
                // We have a next candidate but it's not in the current block. We need to
                // move to the next block in order to not miss any potential hits between
                // `target` and `head.top().doc`.
                require(head.top()!!.doc >= target)
                updateMaxScores(target)
                break
            } else {
                break
            }
        }

        require(head.size() == 0 || head.top()!!.doc <= upTo)
        require(upTo >= target)
    }

    /**
     * Set 'doc' to the next potential match, and move all disis of 'head' that are on this doc into
     * 'lead'.
     */
    @Throws(IOException::class)
    private fun moveToNextCandidate() {
        // The top of `head` defines the next potential match
        // pop all documents which are on this doc
        lead = head.pop()
        require(doc == lead!!.doc)
        lead!!.next = null
        freq = 1
        if (scoreMode === ScoreMode.TOP_SCORES) {
            leadScore = lead!!.scorable!!.score().toDouble()
        }
        while (head.size() > 0 && head.top()!!.doc == doc) {
            addLead(head.pop()!!)
        }
    }

    /** Advance all entries from the tail to know about all matches on the current doc.  */
    @Throws(IOException::class)
    private fun advanceAllTail() {
        // we return the next doc when the sum of the scores of the potential
        // matching clauses is high enough but some of the clauses in 'tail' might
        // match as well
        // since we are advancing all clauses in tail, we just iterate the array
        // without reorganizing the PQ
        for (i in tailSize - 1 downTo 0) {
            advanceTail(tail[i]!!)
        }
        tailSize = 0
        tailMaxScore = 0
        require(ensureConsistent())
    }

    @Throws(IOException::class)
    override fun score(): Float {
        // we need to know about all matches
        advanceAllTail()

        var leadScore = this.leadScore
        if (scoreMode !== ScoreMode.TOP_SCORES) {
            // With TOP_SCORES, the score was already computed on the fly.
            var s = lead
            while (s != null) {
                leadScore += s.scorable!!.score()
                s = s.next
            }
        }
        return leadScore.toFloat()
    }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        // Propagate to improve score bounds
        for (scorer in allScorers) {
            if (scorer.docID() < target) {
                scorer.advanceShallow(target)
            }
        }
        if (target <= upTo) {
            return upTo
        }
        // TODO: implement
        return DocIdSetIterator.NO_MORE_DOCS
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        var maxScoreSum = 0.0
        for (scorer in allScorers) {
            if (scorer.docID() <= upTo) {
                maxScoreSum += scorer.getMaxScore(upTo)
            }
        }
        return MathUtil.sumUpperBound(maxScoreSum, allScorers.size).toFloat()
    }

    override fun docID(): Int {
        return doc
    }

    /** Insert an entry in 'tail' and evict the least-costly scorer if full.  */
    private fun insertTailWithOverFlow(s: DisiWrapper): DisiWrapper? {
        if (tailMaxScore + s.scaledMaxScore < minCompetitiveScore || tailSize + 1 < minShouldMatch) {
            // we have free room for this new entry
            addTail(s)
            tailMaxScore += s.scaledMaxScore
            return null
        } else if (tailSize == 0) {
            return s
        } else {
            val top = tail[0]
            if (!greaterMaxScore(top!!, s)) {
                return s
            }
            // Swap top and s
            tail[0] = s
            downHeapMaxScore(tail as Array<DisiWrapper>, tailSize)
            tailMaxScore = tailMaxScore - top.scaledMaxScore + s.scaledMaxScore
            return top
        }
    }

    /** Add an entry to 'tail'. Fails if over capacity.  */
    private fun addTail(s: DisiWrapper) {
        tail[tailSize] = s
        upHeapMaxScore(tail as Array<DisiWrapper>, tailSize)
        tailSize += 1
    }

    /** Pop the least-costly scorer from 'tail'.  */
    private fun popTail(): DisiWrapper {
        require(tailSize > 0)
        val result = tail[0]!!
        tail[0] = tail[--tailSize]
        downHeapMaxScore(tail as Array<DisiWrapper>, tailSize)
        tailMaxScore -= result.scaledMaxScore
        return result
    }

    companion object {
        const val FLOAT_MANTISSA_BITS: Int = 24
        private const val MAX_SCALED_SCORE = (1L shl 24) - 1

        /**
         * Return a scaling factor for the given float so that `f x 2^scalingFactor` would be in
         * `[2^23, 2^24[`. Special cases:
         *
         * <pre>
         * scalingFactor(0) = scalingFactor(MIN_VALUE) + 1
         * scalingFactor(+Infty) = scalingFactor(MAX_VALUE) - 1
        </pre> *
         */
        fun scalingFactor(f: Float): Int {
            require(!(f < 0)) { "Scores must be positive or null" }
            if (f == 0f) {
                return scalingFactor(Float.Companion.MIN_VALUE) + 1
            } else if (Float.isInfinite(f)) {
                return scalingFactor(Float.Companion.MAX_VALUE) - 1
            } else {
                val d = f.toDouble()
                // Since doubles have more amplitude than floats for the
                // exponent, the cast produces a normal value.
                require(
                    d == 0.0 || Math.getExponent(d) >= Double.MIN_EXPONENT // normal double
                )
                return FLOAT_MANTISSA_BITS - 1 - Math.getExponent(d)
            }
        }

        /**
         * Scale max scores in an unsigned integer to avoid overflows (only the lower 32 bits of the long
         * are used) as well as floating-point arithmetic errors. Those are rounded up in order to make
         * sure we do not miss any matches.
         */
        fun scaleMaxScore(maxScore: Float, scalingFactor: Int): Long {
            require(!Float.isNaN(maxScore))
            require(maxScore >= 0)

            // NOTE: because doubles have more amplitude than floats for the
            // exponent, the scalb call produces an accurate value.
            val scaled: Double = Math.scalb(maxScore.toDouble(), scalingFactor)

            if (scaled > MAX_SCALED_SCORE) {
                // This happens if one scorer returns +Infty as a max score, or if the scorer returns greater
                // max scores locally than globally - which shouldn't happen with well-behaved scorers
                return MAX_SCALED_SCORE
            }

            return ceil(scaled).toLong() // round up, cast is accurate since value is < 2^24
        }

        /**
         * Scale min competitive scores the same way as max scores but this time by rounding down in order
         * to make sure that we do not miss any matches.
         */
        private fun scaleMinScore(minScore: Float, scalingFactor: Int): Long {
            require(Float.isFinite(minScore))
            require(minScore >= 0)

            // like for scaleMaxScore, this scalb call is accurate
            val scaled: Double = Math.scalb(minScore.toDouble(), scalingFactor)
            // round down, cast might lower the value again if scaled > Long.MAX_VALUE,
            // which is fine
            return floor(scaled).toLong()
        }

        /** Heap helpers  */
        private fun upHeapMaxScore(heap: Array<DisiWrapper>, i: Int) {
            var i = i
            val node = heap[i]
            var j: Int = parentNode(i)
            while (j >= 0 && greaterMaxScore(node, heap[j])) {
                heap[i] = heap[j]
                i = j
                j = parentNode(j)
            }
            heap[i] = node
        }

        private fun downHeapMaxScore(heap: Array<DisiWrapper>, size: Int) {
            var i = 0
            val node = heap[0]
            var j: Int = leftNode(i)
            if (j < size) {
                var k: Int = rightNode(j)
                if (k < size && greaterMaxScore(heap[k], heap[j])) {
                    j = k
                }
                if (greaterMaxScore(heap[j], node)) {
                    do {
                        heap[i] = heap[j]
                        i = j
                        j = leftNode(i)
                        k = rightNode(j)
                        if (k < size && greaterMaxScore(heap[k], heap[j])) {
                            j = k
                        }
                    } while (j < size && greaterMaxScore(heap[j], node))
                    heap[i] = node
                }
            }
        }

        /**
         * In the tail, we want to get first entries that produce the maximum scores and in case of ties
         * (eg. constant-score queries), those that have the least cost so that they are likely to advance
         * further.
         */
        private fun greaterMaxScore(w1: DisiWrapper, w2: DisiWrapper): Boolean {
            return if (w1.scaledMaxScore > w2.scaledMaxScore) {
                true
            } else if (w1.scaledMaxScore < w2.scaledMaxScore) {
                false
            } else {
                w1.cost < w2.cost
            }
        }
    }
}
