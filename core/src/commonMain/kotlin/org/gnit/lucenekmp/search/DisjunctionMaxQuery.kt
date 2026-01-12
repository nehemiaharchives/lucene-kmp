package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.floatToIntBits
import kotlin.reflect.cast


/**
 * A query that generates the union of documents produced by its subqueries, and that scores each
 * document with the maximum score for that document as produced by any subquery, plus a tie
 * breaking increment for any additional matching subqueries. This is useful when searching for a
 * word in multiple fields with different boost factors (so that the fields cannot be combined
 * equivalently into a single search field). We want the primary score to be the one associated with
 * the highest boost, not the sum of the field scores (as BooleanQuery would give). If the query is
 * "albino elephant" this ensures that "albino" matching one field and "elephant" matching another
 * gets a higher score than "albino" matching both fields. To get this result, use both BooleanQuery
 * and DisjunctionMaxQuery: for each term a DisjunctionMaxQuery searches for it in each field, while
 * the set of these DisjunctionMaxQuery's is combined into a BooleanQuery. The tie breaker
 * capability allows results that include the same term in multiple fields to be judged better than
 * results that include this term in only the best of those multiple fields, without confusing this
 * with the better case of two different terms in the multiple fields.
 */
open class DisjunctionMaxQuery(disjuncts: MutableCollection<out Query>, tieBreakerMultiplier: Float) : Query(),
    Iterable<Query> {
    /* The subqueries */
    private val disjuncts: Multiset<Query> = Multiset()
    private val orderedQueries: MutableList<Query> // used for toString()

    /**
     * @return tie breaker value for multiple matches.
     */
    /* Multiple of the non-max disjunct scores added into our final score.  Non-zero values support tie-breaking. */
    val tieBreakerMultiplier: Float

    /**
     * Creates a new DisjunctionMaxQuery
     *
     * @param disjuncts a collection of all the disjunct queries to add
     * @param tieBreakerMultiplier the score of each non-maximum disjunct for a document is multiplied
     * by this weight and added into the final score. If non-zero, the value should be small, on
     * the order of 0.1, which says that 10 occurrences of word in a lower-scored field that is
     * also in a higher scored field is just as good as a unique word in the lower scored field
     * (i.e., one that is not in any higher scored field.
     */
    init {
        requireNotNull(disjuncts) {"Collection of Querys must not be null"}
        require(!(tieBreakerMultiplier < 0 || tieBreakerMultiplier > 1)) { "tieBreakerMultiplier must be in [0, 1]" }
        this.tieBreakerMultiplier = tieBreakerMultiplier
        this.disjuncts.addAll(disjuncts)
        this.orderedQueries = ArrayList<Query>(disjuncts) // order from the caller
    }

    /**
     * @return An `Iterator<Query>` over the disjuncts
     */
    override fun iterator(): MutableIterator<Query> {
        return getDisjuncts().iterator()
    }

    /**
     * @return the disjuncts.
     */
    fun getDisjuncts(): MutableCollection<Query> {
        return disjuncts.toMutableList()
    }

    /**
     * Expert: the Weight for DisjunctionMaxQuery, used to normalize, score and explain these queries.
     *
     *
     * NOTE: this API and implementation is subject to change suddenly in the next release.
     */
    protected open inner class DisjunctionMaxWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float) :
        Weight(this@DisjunctionMaxQuery) {
        /** The Weights for our subqueries, in 1-1 correspondence with disjuncts  */
        protected val weights: ArrayList<Weight> =
            ArrayList() // The Weight's for our subqueries, in 1-1 correspondence with disjuncts

        private val scoreMode: ScoreMode

        /**
         * Construct the Weight for this Query searched by searcher. Recursively construct subquery
         * weights.
         */
        init {
            for (disjunctQuery in disjuncts) {
                weights.add(searcher.createWeight(disjunctQuery, scoreMode, boost))
            }
            this.scoreMode = scoreMode
        }

        @Throws(IOException::class)
        override fun matches(context: LeafReaderContext, doc: Int): Matches {
            val mis: MutableList<Matches> = ArrayList()
            for (weight in weights) {
                val mi: Matches? = weight.matches(context, doc)
                if (mi != null) {
                    mis.add(mi)
                }
            }
            return MatchesUtils.fromSubMatches(mis)!!
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val scorerSuppliers: MutableList<ScorerSupplier> = ArrayList()
            for (w in weights) {
                val ss: ScorerSupplier? = w.scorerSupplier(context)
                if (ss != null) {
                    scorerSuppliers.add(ss)
                }
            }

            if (scorerSuppliers.isEmpty()) {
                return null
            } else if (scorerSuppliers.size == 1) {
                return scorerSuppliers[0]
            } else {
                return object : ScorerSupplier() {
                    private var cost: Long = -1

                    @Throws(IOException::class)
                    override fun get(leadCost: Long): Scorer {
                        val scorers: MutableList<Scorer> = ArrayList()
                        for (ss in scorerSuppliers) {
                            scorers.add(ss.get(leadCost)!!)
                        }
                        return DisjunctionMaxScorer(tieBreakerMultiplier, scorers, scoreMode, leadCost)
                    }

                    @Throws(IOException::class)
                    override fun bulkScorer(): BulkScorer? {
                        if (tieBreakerMultiplier == 0f && scoreMode == ScoreMode.TOP_SCORES) {
                            val scorers: MutableList<BulkScorer> = ArrayList()
                            for (ss in scorerSuppliers) {
                                scorers.add(ss.bulkScorer()!!)
                            }
                            return DisjunctionMaxBulkScorer(scorers)
                        }
                        return super.bulkScorer()
                    }

                    override fun cost(): Long {
                        if (cost == -1L) {
                            var cost: Long = 0
                            for (ss in scorerSuppliers) {
                                cost += ss.cost()
                            }
                            this.cost = cost
                        }
                        return cost
                    }

                    override fun setTopLevelScoringClause() {
                        if (tieBreakerMultiplier == 0f) {
                            for (ss in scorerSuppliers) {
                                // sub scorers need to be able to skip too as calls to setMinCompetitiveScore get
                                // propagated
                                ss.setTopLevelScoringClause()
                            }
                        }
                    }
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            if (weights.size
                > AbstractMultiTermQueryConstantScoreWrapper.BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD
            ) {
                // Disallow caching large dismax queries to not encourage users
                // to build large dismax queries as a workaround to the fact that
                // we disallow caching large TermInSetQueries.
                return false
            }
            for (w in weights) {
                if (!w.isCacheable(ctx)) return false
            }
            return true
        }

        /** Explain the score we computed for doc  */
        @Throws(IOException::class)
        override fun explain(context: LeafReaderContext, doc: Int): Explanation {
            var match = false
            var max = 0.0
            var otherSum = 0.0
            val subsOnMatch: MutableList<Explanation> = ArrayList()
            val subsOnNoMatch: MutableList<Explanation> = ArrayList()
            for (wt in weights) {
                val e: Explanation = wt.explain(context, doc)
                if (e.isMatch) {
                    match = true
                    subsOnMatch.add(e)
                    val score: Double = e.value.toDouble()
                    if (score >= max) {
                        otherSum += max
                        max = score
                    } else {
                        otherSum += score
                    }
                } else if (!match) {
                    subsOnNoMatch.add(e)
                }
            }
            if (match) {
                val score = (max + otherSum * tieBreakerMultiplier).toFloat()
                val desc =
                    if (tieBreakerMultiplier == 0.0f)
                        "max of:"
                    else
                        "max plus $tieBreakerMultiplier times others of:"
                return Explanation.match(score, desc, subsOnMatch)
            } else {
                return Explanation.noMatch("No matching clause", subsOnNoMatch)
            }
        }
    } // end of DisjunctionMaxWeight inner class


    /** Create the Weight used to score us  */
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return this.DisjunctionMaxWeight(searcher, scoreMode, boost)
    }

    /**
     * Optimize our representation and our subqueries representations
     *
     * @return an optimized copy of us (which may not be a copy if there is nothing to optimize)
     */
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (disjuncts.isEmpty()) {
            return MatchNoDocsQuery("empty DisjunctionMaxQuery")
        }

        if (disjuncts.size == 1) {
            return disjuncts.iterator().next()
        }

        if (tieBreakerMultiplier == 1.0f) {
            val builder: BooleanQuery.Builder = BooleanQuery.Builder()
            for (sub in disjuncts) {
                builder.add(sub, BooleanClause.Occur.SHOULD)
            }
            return builder.build()
        }

        var actuallyRewritten = false
        val rewrittenDisjuncts: MutableList<Query> = ArrayList()
        for (sub in disjuncts) {
            val rewrittenSub: Query = sub.rewrite(indexSearcher)
            actuallyRewritten = actuallyRewritten or (rewrittenSub != sub)
            rewrittenDisjuncts.add(rewrittenSub)
        }

        if (actuallyRewritten) {
            return DisjunctionMaxQuery(rewrittenDisjuncts, tieBreakerMultiplier)
        }

        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        val v: QueryVisitor = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this)
        for (q in disjuncts) {
            q.visit(v)
        }
    }

override fun toString(field: String?): String {
    val queryStrings = this.orderedQueries.map { subquery: Query ->
        if (subquery is BooleanQuery) { // wrap sub-bools in parens
            "(" + subquery.toString(field) + ")"
        } else {
            subquery.toString(field)
        }
    }

    val tieBreaker = if (tieBreakerMultiplier != 0.0f) "~$tieBreakerMultiplier" else ""
    return "(" + queryStrings.joinToString(" | ") + ")$tieBreaker"
}

    /**
     * Return true if we represent the same query as other
     *
     * @param other another object
     * @return true if other is a DisjunctionMaxQuery with the same boost and the same subqueries, in
     * the same order, as us
     */
    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: DisjunctionMaxQuery): Boolean {
        return tieBreakerMultiplier == other.tieBreakerMultiplier
                && disjuncts == other.disjuncts
    }

    /**
     * Compute a hash code for hashing us
     *
     * @return the hash code
     */
    override fun hashCode(): Int {
        var h = classHash()
        h = 31 * h + Float.floatToIntBits(tieBreakerMultiplier)
        h = 31 * h + Objects.hashCode(disjuncts)
        return h
    }
}
