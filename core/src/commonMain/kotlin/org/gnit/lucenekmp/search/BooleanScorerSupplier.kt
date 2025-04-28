package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.Weight.DefaultBulkScorer
import org.gnit.lucenekmp.util.Bits
import kotlin.math.max
import kotlin.math.min

internal class BooleanScorerSupplier(
    weight: Weight,
    subs: MutableMap<Occur, MutableCollection<ScorerSupplier>>,
    scoreMode: ScoreMode,
    minShouldMatch: Int,
    maxDoc: Int
) : ScorerSupplier() {
    private val subs: MutableMap<Occur, MutableCollection<ScorerSupplier>>
    private val scoreMode: ScoreMode
    private val minShouldMatch: Int
    private val maxDoc: Int
    private var cost: Long = -1
    private var topLevelScoringClause = false

    init {
        require(minShouldMatch >= 0) { "minShouldMatch must be positive, but got: $minShouldMatch" }
        require(!(minShouldMatch != 0 && minShouldMatch >= subs[Occur.SHOULD]!!.size)) { "minShouldMatch must be strictly less than the number of SHOULD clauses" }
        require(
            !(!scoreMode.needsScores() && minShouldMatch == 0 && subs[Occur.SHOULD]!!.isNotEmpty() && subs[Occur.MUST]!!.size + subs[Occur.FILTER]!!.size > 0)
        ) { "Cannot pass purely optional clauses if scores are not needed" }
        require(
            subs[Occur.SHOULD]!!.size + subs[Occur.MUST]!!.size + subs[Occur.FILTER]!!.size
                    != 0
        ) { "There should be at least one positive clause" }
        this.subs = subs
        this.scoreMode = scoreMode
        this.minShouldMatch = minShouldMatch
        this.maxDoc = maxDoc
    }

    private fun computeCost(): Long {
        val mustCosts = subs[Occur.MUST]?.map { it.cost() } ?: emptyList()
        val filterCosts = subs[Occur.FILTER]?.map { it.cost() } ?: emptyList()
        val allRequiredCosts = mustCosts + filterCosts
        val minRequiredCost = allRequiredCosts.minOrNull()

        if (minRequiredCost != null && minShouldMatch == 0) {
            return minRequiredCost
        } else {
            val optionalScorers = subs[Occur.SHOULD] ?: emptyList()
            val shouldCost = ScorerUtil.costWithMinShouldMatch(
                optionalScorers.map { it.cost() }.asSequence(),
                optionalScorers.size,
                minShouldMatch
            )
            return minRequiredCost?.let { min(it, shouldCost) } ?: shouldCost
        }
    }

    override fun setTopLevelScoringClause() {
        topLevelScoringClause = true
        if (subs[Occur.SHOULD]!!.size + subs[Occur.MUST]!!.size == 1) {
            // If there is a single scoring clause, propagate the call.
            for (ss in subs[Occur.SHOULD]!!) {
                ss.setTopLevelScoringClause()
            }
            for (ss in subs[Occur.MUST]!!) {
                ss.setTopLevelScoringClause()
            }
        }
    }

    override fun cost(): Long {
        if (cost == -1L) {
            cost = computeCost()
        }
        return cost
    }

    @Throws(IOException::class)
    override fun get(leadCost: Long): Scorer {
        val scorer = getInternal(leadCost)
        if (scoreMode === ScoreMode.TOP_SCORES && subs[Occur.SHOULD]!!.isEmpty()
            && subs[Occur.MUST]!!.isEmpty()
        ) {
            // no scoring clauses but scores are needed so we wrap the scorer in
            // a constant score in order to allow early termination
            return if (scorer.twoPhaseIterator() != null)
                ConstantScoreScorer(0f, scoreMode, scorer.twoPhaseIterator()!!)
            else
                ConstantScoreScorer(0f, scoreMode, scorer.iterator())
        }
        return scorer
    }

    @Throws(IOException::class)
    private fun getInternal(leadCost: Long): Scorer {
        // three cases: conjunction, disjunction, or mix
        var leadCost = leadCost
        leadCost = min(leadCost, cost())

        // pure conjunction
        if (subs[Occur.SHOULD]!!.isEmpty()) {
            return excl(
                req(subs[Occur.FILTER]!!, subs[Occur.MUST]!!, leadCost, topLevelScoringClause),
                subs[Occur.MUST_NOT]!!,
                leadCost
            )
        }

        // pure disjunction
        if (subs[Occur.FILTER]!!.isEmpty() && subs[Occur.MUST]!!.isEmpty()) {
            return excl(
                opt(subs[Occur.SHOULD]!!, minShouldMatch, scoreMode, leadCost, topLevelScoringClause),
                subs[Occur.MUST_NOT]!!,
                leadCost
            )
        }

        // conjunction-disjunction mix:
        // we create the required and optional pieces, and then
        // combine the two: if minNrShouldMatch > 0, then it's a conjunction: because the
        // optional side must match. otherwise it's required + optional
        if (minShouldMatch > 0) {
            val req =
                excl(
                    req(subs[Occur.FILTER]!!, subs[Occur.MUST]!!, leadCost, false),
                    subs[Occur.MUST_NOT]!!,
                    leadCost
                )
            val opt: Scorer = opt(subs[Occur.SHOULD]!!, minShouldMatch, scoreMode, leadCost, false)
            return ConjunctionScorer(mutableListOf(req, opt), mutableListOf(req, opt))
        } else {
            require(scoreMode.needsScores())
            return ReqOptSumScorer(
                excl(
                    req(subs[Occur.FILTER]!!, subs[Occur.MUST]!!, leadCost, false),
                    subs[Occur.MUST_NOT]!!,
                    leadCost
                ),
                opt(subs[Occur.SHOULD]!!, minShouldMatch, scoreMode, leadCost, false),
                scoreMode
            )
        }
    }

    @Throws(IOException::class)
    override fun bulkScorer(): BulkScorer? {
        val bulkScorer = booleanScorer()
        return if (bulkScorer != null) {
            // bulk scoring is applicable, use it
            bulkScorer
        } else {
            // use a Scorer-based impl (BS2)
            super.bulkScorer()
        }
    }

    @Throws(IOException::class)
    fun booleanScorer(): BulkScorer? {
        val numOptionalClauses = subs[Occur.SHOULD]!!.size
        val numMustClauses = subs[Occur.MUST]!!.size
        val numRequiredClauses = numMustClauses + subs[Occur.FILTER]!!.size

        val positiveScorer: BulkScorer?
        if (numRequiredClauses == 0) {
            // TODO: what is the right heuristic here
            val costThreshold: Long = if (minShouldMatch <= 1) {
                // when all clauses are optional, use BooleanScorer aggressively
                // TODO: is there actually a threshold under which we should rather
                // use the regular scorer
                -1
            } else {
                // when a minimum number of clauses should match, BooleanScorer is
                // going to score all windows that have at least minNrShouldMatch
                // matches in the window. But there is no way to know if there is
                // an intersection (all clauses might match a different doc ID and
                // there will be no matches in the end) so we should only use
                // BooleanScorer if matches are very dense
                (maxDoc / 3).toLong()
            }

            if (cost() < costThreshold) {
                return null
            }

            positiveScorer = optionalBulkScorer()
        } else if (numMustClauses == 0 && numOptionalClauses > 1 && minShouldMatch >= 1) {
            positiveScorer = filteredOptionalBulkScorer()
        } else if (numRequiredClauses > 0 && numOptionalClauses == 0 && minShouldMatch == 0) {
            positiveScorer = requiredBulkScorer()
        } else {
            // TODO: there are some cases where BooleanScorer
            // would handle conjunctions faster than
            // BooleanScorer2...
            return null
        }

        if (positiveScorer == null) {
            return null
        }
        val positiveScorerCost = positiveScorer.cost()

        val prohibited: MutableList<Scorer> = mutableListOf()
        for (ss in subs[Occur.MUST_NOT]!!) {
            prohibited.add(ss.get(positiveScorerCost))
        }

        if (prohibited.isEmpty()) {
            return positiveScorer
        } else {
            val prohibitedScorer =
                if (prohibited.size == 1)
                    prohibited[0]
                else
                    DisjunctionSumScorer(
                        prohibited, ScoreMode.COMPLETE_NO_SCORES, positiveScorerCost
                    )
            return ReqExclBulkScorer(positiveScorer, prohibitedScorer)
        }
    }

    // Return a BulkScorer for the optional clauses only,
    // or null if it is not applicable
    // pkg-private for forcing use of BooleanScorer in tests
    @Throws(IOException::class)
    fun optionalBulkScorer(): BulkScorer? {
        if (subs[Occur.SHOULD]!!.isEmpty()) {
            return null
        } else if (subs[Occur.SHOULD]!!.size == 1 && minShouldMatch <= 1) {
            return subs[Occur.SHOULD]!!.iterator().next().bulkScorer()
        }

        if (scoreMode === ScoreMode.TOP_SCORES && minShouldMatch <= 1) {
            val optionalScorers: MutableList<Scorer> = mutableListOf()
            for (ss in subs[Occur.SHOULD]!!) {
                optionalScorers.add(ss.get(Long.Companion.MAX_VALUE))
            }

            return MaxScoreBulkScorer(maxDoc, optionalScorers, null)
        }

        val optional: MutableList<Scorer> = mutableListOf()
        for (ss in subs[Occur.SHOULD]!!) {
            optional.add(ss.get(Long.Companion.MAX_VALUE))
        }

        return BooleanScorer(optional, max(1, minShouldMatch), scoreMode.needsScores())
    }

    @Throws(IOException::class)
    fun filteredOptionalBulkScorer(): BulkScorer? {
        if (!subs[Occur.MUST]!!.isEmpty() || subs[Occur.FILTER]!!.isEmpty() || (scoreMode.needsScores() && scoreMode !== ScoreMode.TOP_SCORES) || subs[Occur.SHOULD]!!.size <= 1 || minShouldMatch != 1
        ) {
            return null
        }
        val cost = cost()
        val optionalScorers: MutableList<Scorer> = mutableListOf()
        for (ss in subs[Occur.SHOULD]!!) {
            optionalScorers.add(ss.get(cost))
        }
        val filters: MutableList<Scorer> = mutableListOf()
        for (ss in subs[Occur.FILTER]!!) {
            filters.add(ss.get(cost))
        }
        if (scoreMode === ScoreMode.TOP_SCORES) {
            val filterScorer: Scorer = if (filters.size == 1) {
                filters.iterator().next()
            } else {
                ConjunctionScorer(filters, mutableSetOf())
            }
            return MaxScoreBulkScorer(maxDoc, optionalScorers, filterScorer)
        } else {
            // In the beginning of this method, we exited early if the score mode is not either TOP_SCORES
            // or a score mode that doesn't need scores.
            require(!scoreMode.needsScores())
            filters.add(DisjunctionSumScorer(optionalScorers, scoreMode, cost))

            if (filters.map(Scorer::twoPhaseIterator).all { obj: Any? -> obj == null }
                && maxDoc >= DenseConjunctionBulkScorer.WINDOW_SIZE && cost >= maxDoc / DenseConjunctionBulkScorer.DENSITY_THRESHOLD_INVERSE
            ) {
                return DenseConjunctionBulkScorer(
                    filters.map(Scorer::iterator).toMutableList(), maxDoc, 0f
                )
            }

            return DefaultBulkScorer(ConjunctionScorer(filters, mutableListOf()))
        }
    }

    // Return a BulkScorer for the required clauses only
    @Throws(IOException::class)
    private fun requiredBulkScorer(): BulkScorer? {
        if (subs[Occur.MUST]!!.size + subs[Occur.FILTER]!!.size == 0) {
            // No required clauses at all.
            return null
        } else if (subs[Occur.MUST]!!.size + subs[Occur.FILTER]!!.size == 1) {
            var scorer: BulkScorer?
            if (!subs[Occur.MUST]!!.isEmpty()) {
                scorer = subs[Occur.MUST]!!.iterator().next().bulkScorer()
            } else {
                scorer = subs[Occur.FILTER]!!.iterator().next().bulkScorer()
                if (scoreMode.needsScores()) {
                    scorer = disableScoring(scorer!!)
                }
            }
            return scorer
        }

        var leadCost = subs[Occur.MUST]?.minOfOrNull { it.cost() } ?: Long.MAX_VALUE
        leadCost = minOf(leadCost, subs[Occur.FILTER]?.minOfOrNull { it.cost() } ?: leadCost)

        val requiredNoScoring: MutableList<Scorer> = mutableListOf()
        for (ss in subs[Occur.FILTER]!!) {
            requiredNoScoring.add(ss.get(leadCost))
        }
        var requiredScoring: MutableList<Scorer> = mutableListOf()
        val requiredScoringSupplier: MutableCollection<ScorerSupplier> = subs[Occur.MUST]!!
        for (ss in requiredScoringSupplier) {
            if (requiredScoringSupplier.size == 1) {
                ss.setTopLevelScoringClause()
            }
            requiredScoring.add(ss.get(leadCost))
        }
        if (scoreMode === ScoreMode.TOP_SCORES && requiredScoring.size > 1 // Only specialize top-level conjunctions for clauses that don't have a two-phase iterator.
            && requiredNoScoring.map(Scorer::twoPhaseIterator).all { obj: Any? -> obj == null }
            && requiredScoring.map(Scorer::twoPhaseIterator).all { obj: Any? -> obj == null }
        ) {
            // Turn all filters into scoring clauses with a score of zero, so that
            // BlockMaxConjunctionBulkScorer is applicable.
            for (filter in requiredNoScoring) {
                requiredScoring.add(ConstantScoreScorer(0f, ScoreMode.COMPLETE, filter.iterator()))
            }
            return BlockMaxConjunctionBulkScorer(maxDoc, requiredScoring)
        }
        if (scoreMode !== ScoreMode.TOP_SCORES && requiredScoring.size + requiredNoScoring.size >= 2
            && requiredScoring.map(Scorer::twoPhaseIterator).all { obj: Any? -> obj == null }
            && requiredNoScoring.map(Scorer::twoPhaseIterator).all { obj: Any? -> obj == null }
        ) {
            return if (requiredScoring.isEmpty()
                && maxDoc >= DenseConjunctionBulkScorer.WINDOW_SIZE && leadCost >= maxDoc / DenseConjunctionBulkScorer.DENSITY_THRESHOLD_INVERSE
            ) {
                DenseConjunctionBulkScorer(
                    requiredNoScoring.map(Scorer::iterator).toMutableList(), maxDoc, 0f
                )
            } else {
                ConjunctionBulkScorer(requiredScoring, requiredNoScoring)
            }
        }
        if (scoreMode === ScoreMode.TOP_SCORES && requiredScoring.size > 1) {
            requiredScoring = mutableListOf(BlockMaxConjunctionScorer(requiredScoring))
        }
        var conjunctionScorer: Scorer
        if (requiredNoScoring.size + requiredScoring.size == 1) {
            if (requiredScoring.size == 1) {
                conjunctionScorer = requiredScoring[0]
            } else {
                conjunctionScorer = requiredNoScoring[0]
                if (scoreMode.needsScores()) {
                    val inner: Scorer = conjunctionScorer
                    conjunctionScorer =
                        object : FilterScorer(inner) {
                            @Throws(IOException::class)
                            override fun score(): Float {
                                return 0f
                            }

                            @Throws(IOException::class)
                            override fun getMaxScore(upTo: Int): Float {
                                return 0f
                            }
                        }
                }
            }
        } else {
            val required: MutableList<Scorer> = mutableListOf()
            required.addAll(requiredScoring)
            required.addAll(requiredNoScoring)
            conjunctionScorer = ConjunctionScorer(required, requiredScoring)
        }
        return DefaultBulkScorer(conjunctionScorer)
    }

    /**
     * Create a new scorer for the given required clauses. Note that `requiredScoring` is a
     * subset of `required` containing required clauses that should participate in scoring.
     */
    @Throws(IOException::class)
    private fun req(
        requiredNoScoring: MutableCollection<ScorerSupplier>,
        requiredScoring: MutableCollection<ScorerSupplier>,
        leadCost: Long,
        topLevelScoringClause: Boolean
    ): Scorer {
        if (requiredNoScoring.size + requiredScoring.size == 1) {
            val req: Scorer =
                (requiredNoScoring.ifEmpty { requiredScoring })
                    .iterator()
                    .next()
                    .get(leadCost)

            if (!scoreMode.needsScores()) {
                return req
            }

            if (requiredScoring.isEmpty()) {
                // Scores are needed but we only have a filter clause
                // BooleanWeight expects that calling score() is ok so we need to wrap
                // to prevent score() from being propagated
                return object : FilterScorer(req) {
                    @Throws(IOException::class)
                    override fun score(): Float {
                        return 0f
                    }

                    @Throws(IOException::class)
                    override fun getMaxScore(upTo: Int): Float {
                        return 0f
                    }
                }
            }

            return req
        } else {
            val requiredScorers: MutableList<Scorer> = mutableListOf()
            var scoringScorers: MutableList<Scorer> = mutableListOf()
            for (s in requiredNoScoring) {
                requiredScorers.add(s.get(leadCost))
            }
            for (s in requiredScoring) {
                val scorer: Scorer = s.get(leadCost)
                scoringScorers.add(scorer)
            }
            if (scoreMode === ScoreMode.TOP_SCORES && scoringScorers.size > 1 && topLevelScoringClause) {
                val blockMaxScorer: Scorer = BlockMaxConjunctionScorer(scoringScorers)
                if (requiredScorers.isEmpty()) {
                    return blockMaxScorer
                }
                scoringScorers = mutableListOf(blockMaxScorer)
            }
            requiredScorers.addAll(scoringScorers)
            return ConjunctionScorer(requiredScorers, scoringScorers)
        }
    }

    @Throws(IOException::class)
    private fun excl(main: Scorer, prohibited: MutableCollection<ScorerSupplier>, leadCost: Long): Scorer {
        return if (prohibited.isEmpty()) {
            main
        } else {
            ReqExclScorer(
                main, opt(prohibited, 1, ScoreMode.COMPLETE_NO_SCORES, leadCost, false)
            )
        }
    }

    @Throws(IOException::class)
    private fun opt(
        optional: MutableCollection<ScorerSupplier>,
        minShouldMatch: Int,
        scoreMode: ScoreMode,
        leadCost: Long,
        topLevelScoringClause: Boolean
    ): Scorer {
        if (optional.size == 1) {
            return optional.iterator().next().get(leadCost)
        } else {
            val optionalScorers: MutableList<Scorer> = mutableListOf()
            for (scorer in optional) {
                optionalScorers.add(scorer.get(leadCost))
            }

            // Technically speaking, WANDScorer should be able to handle the following 3 conditions now
            // 1. Any ScoreMode (with scoring or not)
            // 2. Any minCompetitiveScore ( >= 0 )
            // 3. Any minShouldMatch ( >= 0 )
            //
            // However, as WANDScorer uses more complex algorithm and data structure, we would like to
            // still use DisjunctionSumScorer to handle exhaustive pure disjunctions, which may be faster
            return if ((scoreMode === ScoreMode.TOP_SCORES && topLevelScoringClause) || minShouldMatch > 1) {
                WANDScorer(optionalScorers, minShouldMatch, scoreMode, leadCost)
            } else {
                DisjunctionSumScorer(optionalScorers, scoreMode, leadCost)
            }
        }
    }

    companion object {
        fun disableScoring(scorer: BulkScorer): BulkScorer {
            //java.util.Objects.requireNonNull<Any>(scorer)
            return object : BulkScorer() {
                @Throws(IOException::class)
                override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
                    val noScoreCollector: LeafCollector =
                        object : LeafCollector {
                            var fake: Score = Score()

                            @Throws(IOException::class)
                            override fun setScorer(scorer: Scorable) {
                                collector.setScorer(fake)
                            }

                            @Throws(IOException::class)
                            override fun collect(doc: Int) {
                                collector.collect(doc)
                            }
                        }
                    return scorer.score(noScoreCollector, acceptDocs, min, max)
                }

                override fun cost(): Long {
                    return scorer.cost()
                }
            }
        }
    }
}
