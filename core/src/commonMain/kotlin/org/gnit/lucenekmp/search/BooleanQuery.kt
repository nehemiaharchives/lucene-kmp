package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.IndexSearcher.TooManyClauses
import kotlin.math.max
import kotlin.reflect.cast


/**
 * A Query that matches documents matching boolean combinations of other queries, e.g. [ ]s, [PhraseQuery]s or other BooleanQuerys.
 */
class BooleanQuery private constructor(
    /** Gets the minimum number of the optional BooleanClauses which must be satisfied.  */
    val minimumNumberShouldMatch: Int, clauses: Array<BooleanClause>
) : Query(), Iterable<BooleanClause> {
    /** A builder for boolean queries.  */
    class Builder
    /** Sole constructor.  */
    {
        private var minimumNumberShouldMatch = 0

        private val clauses: MutableList<BooleanClause> = ArrayList()

        /**
         * Specifies a minimum number of the optional BooleanClauses which must be satisfied.
         *
         *
         * By default no optional clauses are necessary for a match (unless there are no required
         * clauses). If this method is used, then the specified number of clauses is required.
         *
         *
         * Use of this method is totally independent of specifying that any specific clauses are
         * required (or prohibited). This number will only be compared against the number of matching
         * optional clauses.
         *
         * @param min the number of optional clauses that must match
         */
        fun setMinimumNumberShouldMatch(min: Int): Builder {
            this.minimumNumberShouldMatch = min
            return this
        }

        /**
         * Add a new clause to this [Builder]. Note that the order in which clauses are added does
         * not have any impact on matching documents or query performance.
         *
         * @throws IndexSearcher.TooManyClauses if the new number of clauses exceeds the maximum clause
         * number
         */
        fun add(clause: BooleanClause): Builder {
            // We do the final deep check for max clauses count limit during
            // <code>IndexSearcher.rewrite</code> but do this check to short
            // circuit in case a single query holds more than numClauses
            //
            // NOTE: this is not just an early check for optimization -- it's
            // neccessary to prevent run-away 'rewriting' of bad queries from
            // creating BQ objects that might eat up all the Heap.
            if (clauses.size >= IndexSearcher.maxClauseCount) {
                throw TooManyClauses()
            }
            clauses.add(clause)
            return this
        }

        /**
         * Add a collection of BooleanClauses to this [Builder]. Note that the order in which
         * clauses are added does not have any impact on matching documents or query performance.
         *
         * @throws IndexSearcher.TooManyClauses if the new number of clauses exceeds the maximum clause
         * number
         */
        fun add(collection: MutableCollection<BooleanClause>): Builder {
            // see #addClause(BooleanClause)
            if ((clauses.size + collection.size) > IndexSearcher.maxClauseCount) {
                throw TooManyClauses()
            }
            clauses.addAll(collection)
            return this
        }

        /**
         * Add a new clause to this [Builder]. Note that the order in which clauses are added does
         * not have any impact on matching documents or query performance.
         *
         * @throws IndexSearcher.TooManyClauses if the new number of clauses exceeds the maximum clause
         * number
         */
        fun add(query: Query, occur: Occur): Builder {
            return add(BooleanClause(query, occur))
        }

        /**
         * Create a new [BooleanQuery] based on the parameters that have been set on this builder.
         */
        fun build(): BooleanQuery {
            return BooleanQuery(minimumNumberShouldMatch, clauses.toTypedArray<BooleanClause>())
        }
    }

    private val clauses: MutableList<BooleanClause> = clauses.toMutableList() // used for toString() and getClauses()

    // WARNING: Do not let clauseSets escape from this class as it breaks immutability:
    private val clauseSets: MutableMap<Occur, MutableCollection<Query>> = mutableMapOf() // used for equals/hashCode

    /** Return a list of the clauses of this [BooleanQuery].  */
    fun clauses(): MutableList<BooleanClause> {
        return clauses
    }

    /** Return the collection of queries for the given [Occur].  */
    fun getClauses(occur: Occur): MutableCollection<Query> {
        // turn this immutable here, because we need to preserve the correct collection types for
        // equals/hashCode!
        return clauseSets[occur]!!.toMutableSet()
    }

    val isPureDisjunction: Boolean
        /**
         * Whether this query is a pure disjunction, ie. it only has SHOULD clauses and it is enough for a
         * single clause to match for this boolean query to match.
         */
        get() = clauses.size == getClauses(Occur.SHOULD).size && minimumNumberShouldMatch <= 1

    val isTwoClausePureDisjunctionWithTerms: Boolean
        /** Whether this query is a two clause disjunction with two term query clauses.  */
        get() = clauses.size == 2 && this.isPureDisjunction
                && clauses[0].query is TermQuery
                && clauses[1].query is TermQuery

    /**
     * Rewrite a single two clause disjunction query with terms to two term queries and a conjunction
     * query using the inclusion–exclusion principle.
     */
    @Throws(IOException::class)
    fun rewriteTwoClauseDisjunctionWithTermsForCount(indexSearcher: IndexSearcher): Array<Query> {
        val newQuery = Builder()
        val queries = kotlin.arrayOfNulls<Query>(3)
        for (i in clauses.indices) {
            var termQuery: TermQuery = clauses[i].query as TermQuery
            // Optimization will count term query several times so use cache to avoid multiple terms
            // dictionary lookups
            if (termQuery.termStates == null) {
                termQuery =
                    TermQuery(
                        termQuery.getTerm(), TermStates.build(indexSearcher, termQuery.getTerm(), false)
                    )
            }
            newQuery.add(termQuery, Occur.MUST)
            queries[i] = termQuery
        }
        queries[2] = newQuery.build()
        return queries as Array<Query>
    }

    /**
     * Returns an iterator on the clauses in this query. It implements the [Iterable] interface
     * to make it possible to do:
     *
     * <pre class="prettyprint">for (BooleanClause clause : booleanQuery) {}</pre>
     */
    override fun iterator(): MutableIterator<BooleanClause> {
        return clauses.iterator()
    }

    // Utility method for rewriting BooleanQuery when scores are not needed.
    // This is called from ConstantScoreQuery#rewrite
    fun rewriteNoScoring(): BooleanQuery {
        var actuallyRewritten = false
        val newQuery =
            Builder().setMinimumNumberShouldMatch(this.minimumNumberShouldMatch)

        val keepShould =
            this.minimumNumberShouldMatch > 0
                    || (clauseSets[Occur.MUST]!!.size + clauseSets[Occur.FILTER]!!.size == 0)

        for (clause in clauses) {
            val query: Query = clause.query
            // NOTE: rewritingNoScoring() should not call rewrite(), otherwise this
            // method could run in exponential time with the depth of the query as
            // every new level would rewrite 2x more than its parent level.
            var rewritten = query
            if (rewritten is BoostQuery) {
                rewritten = rewritten.query
            }
            if (rewritten is ConstantScoreQuery) {
                rewritten = rewritten.query
            }
            if (rewritten is BooleanQuery) {
                rewritten = rewritten.rewriteNoScoring()
            }
            val occur: Occur = clause.occur
            if (occur === Occur.SHOULD && !keepShould) {
                // ignore clause
                actuallyRewritten = true
            } else if (occur === Occur.MUST) {
                // replace MUST clauses with FILTER clauses
                newQuery.add(rewritten, Occur.FILTER)
                actuallyRewritten = true
            } else if (query !== rewritten) {
                newQuery.add(rewritten, occur)
                actuallyRewritten = true
            } else {
                newQuery.add(clause)
            }
        }

        if (!actuallyRewritten) {
            return this
        }

        return newQuery.build()
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return BooleanWeight(this, searcher, scoreMode, boost)
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (clauses.isEmpty()) {
            return MatchNoDocsQuery("empty BooleanQuery")
        }

        // Queries with no positive clauses have no matches
        if (clauses.size == clauseSets[Occur.MUST_NOT]!!.size) {
            return MatchNoDocsQuery("pure negative BooleanQuery")
        }

        // optimize 1-clause queries
        if (clauses.size == 1) {
            val c = clauses[0]
            val query: Query = c.query
            if (minimumNumberShouldMatch == 1 && c.occur === Occur.SHOULD) {
                return query
            } else if (minimumNumberShouldMatch == 0) {
                when (c.occur) {
                    Occur.SHOULD, Occur.MUST -> return query
                    Occur.FILTER ->             // no scoring clauses, so return a score of 0
                        return BoostQuery(ConstantScoreQuery(query), 0f)

                    Occur.MUST_NOT -> throw AssertionError()
                    else -> throw AssertionError()
                }
            }
        }

        // recursively rewrite
        run {
            val builder = Builder()
            builder.setMinimumNumberShouldMatch(this.minimumNumberShouldMatch)
            var actuallyRewritten = false
            for (clause in this) {
                val query: Query = clause.query
                val occur: Occur = clause.occur
                var rewritten: Query
                if (occur === Occur.FILTER || occur === Occur.MUST_NOT) {
                    // Clauses that are not involved in scoring can get some extra simplifications
                    rewritten = ConstantScoreQuery(query).rewrite(indexSearcher)
                    if (rewritten is ConstantScoreQuery) {
                        rewritten = rewritten.query
                    }
                } else {
                    rewritten = query.rewrite(indexSearcher)
                }
                if (rewritten !== query || query is MatchNoDocsQuery) {
                    // rewrite clause
                    actuallyRewritten = true
                    if (rewritten is MatchNoDocsQuery) {
                        when (occur) {
                            Occur.SHOULD, Occur.MUST_NOT -> {}
                            Occur.MUST, Occur.FILTER -> return rewritten
                        }
                    } else {
                        builder.add(rewritten, occur)
                    }
                } else {
                    // leave as-is
                    builder.add(clause)
                }
            }
            if (actuallyRewritten) {
                return builder.build()
            }
        }

        // remove duplicate FILTER and MUST_NOT clauses
        run {
            var clauseCount = 0
            for (queries in clauseSets.values) {
                clauseCount += queries.size
            }
            if (clauseCount != clauses.size) {
                // since clauseSets implicitly deduplicates FILTER and MUST_NOT
                // clauses, this means there were duplicates
                val rewritten = Builder()
                rewritten.setMinimumNumberShouldMatch(minimumNumberShouldMatch)
                for (entry in clauseSets.entries) {
                    val occur: Occur = entry.key
                    for (query in entry.value) {
                        rewritten.add(query, occur)
                    }
                }
                return rewritten.build()
            }
        }

        // Check whether some clauses are both required and excluded
        val mustNotClauses: MutableCollection<Query> = clauseSets[Occur.MUST_NOT]!!
        if (!mustNotClauses.isEmpty()) {
            val mustOrFilter: List<Query> = clauseSets[Occur.MUST]!! + clauseSets[Occur.FILTER]!!
            if (mustNotClauses.any { mustOrFilter.contains(it) }) {
                return MatchNoDocsQuery("FILTER or MUST clause also in MUST_NOT")
            }
            if (mustNotClauses.contains(MatchAllDocsQuery())) {
                return MatchNoDocsQuery("MUST_NOT clause is MatchAllDocsQuery")
            }
        }

        // remove FILTER clauses that are also MUST clauses or that match all documents
        if (clauseSets[Occur.FILTER]!!.isNotEmpty()) {
            val filters: MutableSet<Query> = clauseSets[Occur.FILTER]!!.toMutableSet()
            var modified = false
            if (filters.size > 1 || !clauseSets[Occur.MUST]!!.isEmpty()) {
                modified = filters.remove(MatchAllDocsQuery())
            }
            modified = modified or filters.removeAll(clauseSets[Occur.MUST]!!)
            if (modified) {
                val builder = Builder()
                builder.setMinimumNumberShouldMatch(this.minimumNumberShouldMatch)
                for (clause in clauses) {
                    if (clause.occur !== Occur.FILTER) {
                        builder.add(clause)
                    }
                }
                for (filter in filters) {
                    builder.add(filter, Occur.FILTER)
                }
                return builder.build()
            }
        }

        // convert FILTER clauses that are also SHOULD clauses to MUST clauses
        if (clauseSets[Occur.SHOULD]!!.isNotEmpty() && clauseSets[Occur.FILTER]!!.isNotEmpty()) {
            val filters: MutableCollection<Query> = clauseSets[Occur.FILTER]!!
            val shoulds: MutableCollection<Query> = clauseSets[Occur.SHOULD]!!

            val intersection: MutableSet<Query> = filters.toMutableSet()
            intersection.retainAll(shoulds)

            if (!intersection.isEmpty()) {
                val builder = Builder()
                var minShouldMatch = this.minimumNumberShouldMatch

                for (clause in clauses) {
                    if (intersection.contains(clause.query)) {
                        if (clause.occur === Occur.SHOULD) {
                            builder.add(BooleanClause(clause.query, Occur.MUST))
                            minShouldMatch--
                        }
                    } else {
                        builder.add(clause)
                    }
                }

                builder.setMinimumNumberShouldMatch(max(0, minShouldMatch))
                return builder.build()
            }
        }

        // Deduplicate SHOULD clauses by summing up their boosts
        if (clauseSets[Occur.SHOULD]!!.isNotEmpty() && minimumNumberShouldMatch <= 1) {
            val shouldClauses: MutableMap<Query, Double> = mutableMapOf()
            for (query in clauseSets[Occur.SHOULD]!!) {
                var query: Query = query
                var boost = 1.0
                while (query is BoostQuery) {
                    val bq = query
                    boost *= bq.boost
                    query = bq.query
                }
                shouldClauses.put(query, shouldClauses.getOrElse(query){0.0} + boost)
            }
            if (shouldClauses.size != clauseSets[Occur.SHOULD]!!.size) {
                val builder =
                    Builder().setMinimumNumberShouldMatch(minimumNumberShouldMatch)
                for (entry in shouldClauses.entries) {
                    var query = entry.key
                    val boost = entry.value.toFloat()
                    if (boost != 1f) {
                        query = BoostQuery(query, boost)
                    }
                    builder.add(query, Occur.SHOULD)
                }
                for (clause in clauses) {
                    if (clause.occur !== Occur.SHOULD) {
                        builder.add(clause)
                    }
                }
                return builder.build()
            }
        }

        // Deduplicate MUST clauses by summing up their boosts
        if (clauseSets[Occur.MUST]!!.isNotEmpty()) {
            val mustClauses: MutableMap<Query, Double> = mutableMapOf()
            for (query in clauseSets[Occur.MUST]!!) {
                var query: Query = query
                var boost = 1.0
                while (query is BoostQuery) {
                    val bq = query
                    boost *= bq.boost
                    query = bq.query
                }
                mustClauses.put(query, mustClauses.getOrElse(query ){0.0} + boost)
            }
            if (mustClauses.size != clauseSets[Occur.MUST]!!.size) {
                val builder =
                    Builder().setMinimumNumberShouldMatch(minimumNumberShouldMatch)
                for (entry in mustClauses.entries) {
                    var query = entry.key
                    val boost = entry.value.toFloat()
                    if (boost != 1f) {
                        query = BoostQuery(query, boost)
                    }
                    builder.add(query, Occur.MUST)
                }
                for (clause in clauses) {
                    if (clause.occur !== Occur.MUST) {
                        builder.add(clause)
                    }
                }
                return builder.build()
            }
        }

        // Rewrite queries whose single scoring clause is a MUST clause on a
        // MatchAllDocsQuery to a ConstantScoreQuery
        run {
            val musts: MutableCollection<Query> = clauseSets[Occur.MUST]!!
            val filters: MutableCollection<Query> = clauseSets[Occur.FILTER]!!
            if (musts.size == 1 && filters.isNotEmpty()) {
                var must = musts.iterator().next()
                var boost = 1f
                if (must is BoostQuery) {
                    must = must.query
                    boost = (must as BoostQuery).boost
                }
                if (must is MatchAllDocsQuery) {
                    // our single scoring clause matches everything: rewrite to a CSQ on the filter
                    // ignore SHOULD clause for now
                    var builder = Builder()
                    for (clause in clauses) {
                        when (clause.occur) {
                            Occur.FILTER, Occur.MUST_NOT -> builder.add(clause)
                            Occur.MUST, Occur.SHOULD -> {}
                            else -> {}
                        }
                    }
                    var rewritten: Query = builder.build()
                    rewritten = ConstantScoreQuery(rewritten)
                    if (boost != 1f) {
                        rewritten = BoostQuery(rewritten, boost)
                    }

                    // now add back the SHOULD clauses
                    builder =
                        Builder()
                            .setMinimumNumberShouldMatch(this.minimumNumberShouldMatch)
                            .add(rewritten, Occur.MUST)
                    for (query in clauseSets[Occur.SHOULD]!!) {
                        builder.add(query, Occur.SHOULD)
                    }
                    rewritten = builder.build()
                    return rewritten
                }
            }
        }

        // Flatten nested disjunctions, this is important for block-max WAND to perform well
        if (minimumNumberShouldMatch <= 1) {
            val builder = Builder()
            builder.setMinimumNumberShouldMatch(minimumNumberShouldMatch)
            var actuallyRewritten = false
            for (clause in clauses) {
                if (clause.occur === Occur.SHOULD && clause.query is BooleanQuery) {
                    val innerQuery: BooleanQuery = clause.query
                    if (innerQuery.isPureDisjunction) {
                        actuallyRewritten = true
                        for (innerClause in innerQuery.clauses()) {
                            builder.add(innerClause)
                        }
                    } else {
                        builder.add(clause)
                    }
                } else {
                    builder.add(clause)
                }
            }
            if (actuallyRewritten) {
                return builder.build()
            }
        }

        // Inline required / prohibited clauses. This helps run filtered conjunctive queries more
        // efficiently by providing all clauses to the block-max AND scorer.
        run {
            val builder = Builder()
            builder.setMinimumNumberShouldMatch(minimumNumberShouldMatch)
            var actuallyRewritten = false
            for (outerClause in clauses) {
                if (outerClause.isRequired && outerClause.query is BooleanQuery) {
                    // Inlining prohibited clauses is not legal if the query is a pure negation, since pure
                    // negations have no matches. It works because the inner BooleanQuery would have first
                    // rewritten to a MatchNoDocsQuery if it only had prohibited clauses.
                    val innerQuery: BooleanQuery = outerClause.query
                    require(innerQuery.getClauses(Occur.MUST_NOT).size != innerQuery.clauses().size)
                    if (innerQuery.minimumNumberShouldMatch == 0
                        && innerQuery.getClauses(Occur.SHOULD).isEmpty()
                    ) {
                        actuallyRewritten = true
                        for (innerClause in innerQuery) {
                            val innerOccur: Occur = innerClause.occur
                            if (innerOccur === Occur.FILTER || innerOccur === Occur.MUST_NOT || outerClause.occur === Occur.MUST) {
                                builder.add(innerClause)
                            } else {
                                require(outerClause.occur === Occur.FILTER && innerOccur === Occur.MUST)
                                // In this case we need to change the occur of the inner query from MUST to FILTER.
                                builder.add(innerClause.query, Occur.FILTER)
                            }
                        }
                    } else {
                        builder.add(outerClause)
                    }
                } else {
                    builder.add(outerClause)
                }
            }
            if (actuallyRewritten) {
                return builder.build()
            }
        }

        // SHOULD clause count less than or equal to minimumNumberShouldMatch
        // Important(this can only be processed after nested clauses have been flattened)
        run {
            val shoulds: MutableCollection<Query> = clauseSets[Occur.SHOULD]!!
            if (shoulds.size < minimumNumberShouldMatch) {
                return MatchNoDocsQuery("SHOULD clause count less than minimumNumberShouldMatch")
            }
            if (shoulds.isNotEmpty() && shoulds.size == minimumNumberShouldMatch) {
                val builder = Builder()
                for (clause in clauses) {
                    if (clause.occur === Occur.SHOULD) {
                        builder.add(clause.query, Occur.MUST)
                    } else {
                        builder.add(clause)
                    }
                }

                return builder.build()
            }
        }

        // Inline SHOULD clauses from the only MUST clause
        run {
            val shoulds = clauseSets[Occur.SHOULD]!!
            val musts = clauseSets[Occur.MUST]!!

            if (shoulds.isEmpty() && musts.size == 1) {
                val inner = musts.iterator().next()

                if (inner is BooleanQuery
                    && inner.clauses.size == inner.clauseSets[Occur.SHOULD]!!.size
                ) {
                    val rewritten = Builder()
                    for (clause in clauses) {
                        if (clause.occur !== Occur.MUST) {
                            rewritten.add(clause)
                        }
                    }
                    for (innerClause in inner.clauses()) {
                        rewritten.add(innerClause)
                    }
                    rewritten.setMinimumNumberShouldMatch(max(1, inner.minimumNumberShouldMatch))
                    return rewritten.build()
                }
            }
        }

        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        val sub = visitor.getSubVisitor(Occur.MUST, this)
        for (occur in clauseSets.keys) {
            if (clauseSets[occur]!!.isNotEmpty()) {
                if (occur === Occur.MUST) {
                    for (q in clauseSets[occur]!!) {
                        q.visit(sub)
                    }
                } else {
                    val v: QueryVisitor = sub.getSubVisitor(occur, this)
                    for (q in clauseSets[occur]!!) {
                        q.visit(v)
                    }
                }
            }
        }
    }

    /** Prints a user-readable version of this query.  */
    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        val needParens = this.minimumNumberShouldMatch > 0
        if (needParens) {
            buffer.append("(")
        }

        var i = 0
        for (c in this) {
            buffer.append(c.occur.toString())

            val subQuery: Query = c.query
            if (subQuery is BooleanQuery) { // wrap sub-bools in parens
                buffer.append("(")
                buffer.append(subQuery.toString(field))
                buffer.append(")")
            } else {
                buffer.append(subQuery.toString(field))
            }

            if (i != clauses.size - 1) {
                buffer.append(" ")
            }
            i += 1
        }

        if (needParens) {
            buffer.append(")")
        }

        if (this.minimumNumberShouldMatch > 0) {
            buffer.append('~')
            buffer.append(this.minimumNumberShouldMatch)
        }

        return buffer.toString()
    }

    /**
     * Compares the specified object with this boolean query for equality. Returns true if and only if
     * the provided object
     *
     *
     *  * is also a [BooleanQuery],
     *  * has the same value of [.getMinimumNumberShouldMatch]
     *  * has the same [Occur.SHOULD] clauses, regardless of the order
     *  * has the same [Occur.MUST] clauses, regardless of the order
     *  * has the same set of [Occur.FILTER] clauses, regardless of the order and regardless
     * of duplicates
     *  * has the same set of [Occur.MUST_NOT] clauses, regardless of the order and
     * regardless of duplicates
     *
     */
    override fun equals(o: Any?): Boolean {
        return sameClassAs(o) && equalsTo(this::class.cast(o))
    }

    private fun equalsTo(other: BooleanQuery): Boolean {
        return this.minimumNumberShouldMatch == other.minimumNumberShouldMatch
                && clauseSets == other.clauseSets
    }

    private fun computeHashCode(): Int {
        var hashCode: Int = Objects.hash(minimumNumberShouldMatch, clauseSets)
        if (hashCode == 0) {
            hashCode = 1
        }
        return hashCode
    }

    // cached hash code is ok since boolean queries are immutable
    private var hashCode = 0

    init {
        // duplicates matter for SHOULD and MUST
        clauseSets.put(Occur.SHOULD, Multiset())
        clauseSets.put(Occur.MUST, Multiset())
        // but not for FILTER and MUST_NOT
        clauseSets.put(Occur.FILTER, HashSet<Query>())
        clauseSets.put(Occur.MUST_NOT, HashSet<Query>())
        for (clause in clauses) {
            clauseSets[clause.occur]!!.add(clause.query)
        }
    }

    override fun hashCode(): Int {
        // no need for synchronization, in the worst case we would just compute the hash several times.
        if (hashCode == 0) {
            hashCode = computeHashCode()
            require(hashCode != 0)
        }
        require(hashCode == computeHashCode())
        return hashCode
    }
}
