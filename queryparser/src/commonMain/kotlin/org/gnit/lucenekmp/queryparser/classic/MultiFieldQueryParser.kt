package org.gnit.lucenekmp.queryparser.classic

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query

/** A QueryParser which constructs queries to search multiple fields. */
open class MultiFieldQueryParser : QueryParser {
    protected var fields: Array<String>
    protected var boosts: Map<String, Float>? = null

    /**
     * Creates a MultiFieldQueryParser. Allows passing of a map with term to Boost, and the boost to
     * apply to each term.
     *
     * It will, when parse(String query) is called, construct a query like this (assuming the query
     * consists of two terms and you specify the two fields `title` and `body`):
     * ` (title:term1 body:term1) (title:term2 body:term2) `
     *
     * When setDefaultOperator(AND_OPERATOR) is set, the result will be: `
     * +(title:term1 body:term1) +(title:term2 body:term2) `
     *
     * When you pass a boost (title=>5 body=>10) you can get `
     * +(title:term1^5.0 body:term1^10.0) +(title:term2^5.0 body:term2^10.0) `
     *
     * In other words, all the query's terms must appear, but it doesn't matter in what fields they
     * appear.
     */
    constructor(fields: Array<String>, analyzer: Analyzer, boosts: Map<String, Float>) : this(fields, analyzer) {
        this.boosts = boosts
    }

    /**
     * Creates a MultiFieldQueryParser.
     *
     * It will, when parse(String query) is called, construct a query like this (assuming the query
     * consists of two terms and you specify the two fields `title` and `body`):
     * ` (title:term1 body:term1) (title:term2 body:term2) `
     *
     * When setDefaultOperator(AND_OPERATOR) is set, the result will be: `
     * +(title:term1 body:term1) +(title:term2 body:term2) `
     *
     * In other words, all the query's terms must appear, but it doesn't matter in what fields they
     * appear.
     */
    constructor(fields: Array<String>, analyzer: Analyzer) : super(NULL_FIELD, analyzer) {
        this.fields = fields
    }

    @Throws(ParseException::class)
    override fun getFieldQuery(field: String, queryText: String, slop: Int): Query? {
        if (field == NULL_FIELD) {
            val clauses = ArrayList<Query>()
            for (i in fields.indices) {
                var q = super.getFieldQuery(fields[i], queryText, true)
                if (q != null) {
                    if (boosts != null) {
                        val boost = boosts!![fields[i]]
                        if (boost != null) {
                            q = BoostQuery(q, boost)
                        }
                    }
                    val query = requireNotNull(applySlop(q, slop))
                    clauses.add(query)
                }
            }
            if (clauses.size == 0) {
                return null
            }
            return getMultiFieldQuery(clauses)
        }
        var q = super.getFieldQuery(field, queryText, true)
        q = applySlop(q, slop)
        return q
    }

    private fun applySlop(q: Query?, slop: Int): Query? {
        var query = q
        if (query is PhraseQuery) {
            val builder = PhraseQuery.Builder()
            builder.setSlop(slop)
            val terms = query.terms
            val positions = query.positions
            for (i in terms.indices) {
                builder.add(terms[i], positions[i])
            }
            query = builder.build()
        } else if (query is MultiPhraseQuery) {
            if (slop != query.slop) {
                query = MultiPhraseQuery.Builder(query).setSlop(slop).build()
            }
        } else if (query is BoostQuery) {
            val subQuery = requireNotNull(applySlop(query.query, slop))
            query = BoostQuery(subQuery, query.boost)
        }
        return query
    }

    private fun applyBoost(q: Query, field: String): Query {
        var query = q
        if (boosts != null) {
            val boost = boosts!![field]
            if (boost != null) {
                query = BoostQuery(query, boost)
            }
        }
        return query
    }

    @Throws(ParseException::class)
    override fun getFieldQuery(field: String, queryText: String, quoted: Boolean): Query? {
        if (field == NULL_FIELD) {
            val clauses = ArrayList<Query>()
            val fieldQueries = arrayOfNulls<Query>(fields.size)
            var maxTerms = 0
            for (i in fields.indices) {
                val q = super.getFieldQuery(fields[i], queryText, quoted)
                if (q != null) {
                    if (q is BooleanQuery) {
                        maxTerms = kotlin.math.max(maxTerms, q.clauses().size)
                    } else {
                        maxTerms = kotlin.math.max(1, maxTerms)
                    }
                    fieldQueries[i] = q
                }
            }
            for (termNum in 0 until maxTerms) {
                val termClauses = ArrayList<Query>()
                for (i in fields.indices) {
                    if (fieldQueries[i] != null) {
                        var q: Query? = null
                        if (fieldQueries[i] is BooleanQuery) {
                            val nestedClauses = (fieldQueries[i] as BooleanQuery).clauses()
                            if (termNum < nestedClauses.size) {
                                q = nestedClauses[termNum].query
                            }
                        } else if (termNum == 0) {
                            q = fieldQueries[i]
                        }
                        if (q != null) {
                            if (boosts != null) {
                                val boost = boosts!![fields[i]]
                                if (boost != null) {
                                    q = BoostQuery(q, boost)
                                }
                            }
                            termClauses.add(q)
                        }
                    }
                }
                if (maxTerms > 1) {
                    if (termClauses.size > 0) {
                        val builder = newBooleanQuery()
                        for (termClause in termClauses) {
                            builder.add(termClause, BooleanClause.Occur.SHOULD)
                        }
                        clauses.add(builder.build())
                    }
                } else {
                    clauses.addAll(termClauses)
                }
            }
            if (clauses.size == 0) {
                return null
            }
            return getMultiFieldQuery(clauses)
        }
        return super.getFieldQuery(field, queryText, quoted)
    }

    @Throws(ParseException::class)
    override fun getFuzzyQuery(field: String, termStr: String, minSimilarity: Float): Query {
        if (field == NULL_FIELD) {
            val clauses = ArrayList<Query>()
            for (i in fields.indices) {
                clauses.add(getFuzzyQuery(fields[i], termStr, minSimilarity))
            }
            return requireNotNull(getMultiFieldQuery(clauses))
        }
        val q = super.getFuzzyQuery(field, termStr, minSimilarity)
        return applyBoost(q, field)
    }

    @Throws(ParseException::class)
    override fun getPrefixQuery(field: String, termStr: String): Query {
        if (field == NULL_FIELD) {
            val clauses = ArrayList<Query>()
            for (i in fields.indices) {
                clauses.add(getPrefixQuery(fields[i], termStr))
            }
            return requireNotNull(getMultiFieldQuery(clauses))
        }
        val q = super.getPrefixQuery(field, termStr)
        return applyBoost(q, field)
    }

    @Throws(ParseException::class)
    override fun getWildcardQuery(field: String, termStr: String): Query {
        if (field == NULL_FIELD) {
            val clauses = ArrayList<Query>()
            for (i in fields.indices) {
                clauses.add(getWildcardQuery(fields[i], termStr))
            }
            return requireNotNull(getMultiFieldQuery(clauses))
        }
        val q = super.getWildcardQuery(field, termStr)
        return applyBoost(q, field)
    }

    @Throws(ParseException::class)
    override fun getRangeQuery(
        field: String,
        part1: String?,
        part2: String?,
        startInclusive: Boolean,
        endInclusive: Boolean
    ): Query {
        if (field == NULL_FIELD) {
            val clauses = ArrayList<Query>()
            for (i in fields.indices) {
                clauses.add(getRangeQuery(fields[i], part1, part2, startInclusive, endInclusive))
            }
            return requireNotNull(getMultiFieldQuery(clauses))
        }
        val q = super.getRangeQuery(field, part1, part2, startInclusive, endInclusive)
        return applyBoost(q, field)
    }

    @Throws(ParseException::class)
    override fun getRegexpQuery(field: String, termStr: String): Query {
        if (field == NULL_FIELD) {
            val clauses = ArrayList<Query>()
            for (i in fields.indices) {
                clauses.add(getRegexpQuery(fields[i], termStr))
            }
            return requireNotNull(getMultiFieldQuery(clauses))
        }
        val q = super.getRegexpQuery(field, termStr)
        return applyBoost(q, field)
    }

    /** Creates a multifield query */
    // TODO: investigate more general approach by default, e.g. DisjunctionMaxQuery?
    @Throws(ParseException::class)
    protected open fun getMultiFieldQuery(queries: List<Query>): Query? {
        if (queries.isEmpty()) {
            return null
        }
        val query = newBooleanQuery()
        for (sub in queries) {
            query.add(sub, BooleanClause.Occur.SHOULD)
        }
        return query.build()
    }

    companion object {
        private const val NULL_FIELD: String = ""

        /**
         * Parses a query which searches on the fields specified.
         *
         * If x fields are specified, this effectively constructs:
         *
         * <pre>
         * ` (field1:query1) (field2:query2) (field3:query3)...(fieldx:queryx) `
         </pre>
         *
         * @param queries Queries strings to parse
         * @param fields Fields to search on
         * @param analyzer Analyzer to use
         * @throws ParseException if query parsing fails
         * @throws IllegalArgumentException if the length of the queries array differs from the length of
         * fields array
         */
        @Throws(ParseException::class)
        fun parse(queries: Array<String>, fields: Array<String>, analyzer: Analyzer): Query {
            require(queries.size == fields.size) { "queries.length != fields.length" }
            val bQuery = BooleanQuery.Builder()
            for (i in fields.indices) {
                val qp = QueryParser(fields[i], analyzer)
                val q = qp.parse(queries[i])
                if (q != null && (q !is BooleanQuery || q.clauses().size > 0)) {
                    bQuery.add(q, BooleanClause.Occur.SHOULD)
                }
            }
            return bQuery.build()
        }

        /**
         * Parses a query, searching on the fields specified. Use this if you need to specify certain
         * fields as required, and others as prohibited.
         *
         * Usage:
         *
         * <pre class="prettyprint">
         * ` String[] fields = {"filename", "contents", "description"};
         * BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD,
         *                BooleanClause.Occur.MUST,
         *                BooleanClause.Occur.MUST_NOT};
         * MultiFieldQueryParser.parse("query", fields, flags, analyzer); `
         </pre>
         *
         * The code above would construct a query:
         *
         * <pre>
         * ` (filename:query) +(contents:query) -(description:query) `
         </pre>
         *
         * @param query Query string to parse
         * @param fields Fields to search on
         * @param flags Flags describing the fields
         * @param analyzer Analyzer to use
         * @throws ParseException if query parsing fails
         * @throws IllegalArgumentException if the length of the fields array differs from the length of
         * flags array
         */
        @Throws(ParseException::class)
        fun parse(
            query: String,
            fields: Array<String>,
            flags: Array<BooleanClause.Occur>,
            analyzer: Analyzer
        ): Query {
            require(fields.size == flags.size) { "fields.length != flags.length" }
            val bQuery = BooleanQuery.Builder()
            for (i in fields.indices) {
                val qp = QueryParser(fields[i], analyzer)
                val q = qp.parse(query)
                if (q != null && (q !is BooleanQuery || q.clauses().size > 0)) {
                    bQuery.add(q, flags[i])
                }
            }
            return bQuery.build()
        }

        /**
         * Parses a query, searching on the fields specified. Use this if you need to specify certain
         * fields as required, and others as prohibited.
         *
         * Usage:
         *
         * <pre class="prettyprint">
         * ` String[] query = {"query1", "query2", "query3"};
         * String[] fields = {"filename", "contents", "description"};
         * BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD,
         *                BooleanClause.Occur.MUST,
         *                BooleanClause.Occur.MUST_NOT};
         * MultiFieldQueryParser.parse(query, fields, flags, analyzer); `
         </pre>
         *
         * The code above would construct a query:
         *
         * <pre>
         * ` (filename:query1) +(contents:query2) -(description:query3) `
         </pre>
         *
         * @param queries Queries string to parse
         * @param fields Fields to search on
         * @param flags Flags describing the fields
         * @param analyzer Analyzer to use
         * @throws ParseException if query parsing fails
         * @throws IllegalArgumentException if the length of the queries, fields, and flags array differ
         */
        @Throws(ParseException::class)
        fun parse(
            queries: Array<String>,
            fields: Array<String>,
            flags: Array<BooleanClause.Occur>,
            analyzer: Analyzer
        ): Query {
            require(queries.size == fields.size && queries.size == flags.size) {
                "queries, fields, and flags array have have different length"
            }
            val bQuery = BooleanQuery.Builder()
            for (i in fields.indices) {
                val qp = QueryParser(fields[i], analyzer)
                val q = qp.parse(queries[i])
                if (q != null && (q !is BooleanQuery || q.clauses().size > 0)) {
                    bQuery.add(q, flags[i])
                }
            }
            return bQuery.build()
        }
    }
}
