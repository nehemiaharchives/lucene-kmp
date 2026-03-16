package org.gnit.lucenekmp.queryparser.complexPhrase

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queries.spans.SpanNearQuery
import org.gnit.lucenekmp.queries.spans.SpanNotQuery
import org.gnit.lucenekmp.queries.spans.SpanOrQuery
import org.gnit.lucenekmp.queries.spans.SpanQuery
import org.gnit.lucenekmp.queries.spans.SpanTermQuery
import org.gnit.lucenekmp.queryparser.classic.ParseException
import org.gnit.lucenekmp.queryparser.classic.QueryParser
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.SynonymQuery
import org.gnit.lucenekmp.search.TermQuery

/**
 * QueryParser which permits complex phrase query syntax eg "(john jon jonathan~) peters*".
 */
open class ComplexPhraseQueryParser(f: String, a: Analyzer) : QueryParser(f, a) {
    private var complexPhrases: ArrayList<ComplexPhraseQuery>? = null
    private var isPass2ResolvingPhrases = false
    private var inOrder = true
    private var currentPhraseQuery: ComplexPhraseQuery? = null

    /**
     * When `inOrder` is true, the search terms must exists in the documents as the same
     * order as in query.
     */
    fun setInOrder(inOrder: Boolean) {
        this.inOrder = inOrder
    }

    override fun getFieldQuery(field: String, queryText: String, slop: Int): Query {
        val cpq = ComplexPhraseQuery(field, queryText, slop, inOrder)
        complexPhrases!!.add(cpq)
        return cpq
    }

    @Throws(ParseException::class)
    override fun parse(query: String): Query? {
        if (isPass2ResolvingPhrases) {
            val oldMethod = multiTermRewriteMethod
            try {
                multiTermRewriteMethod = MultiTermQuery.SCORING_BOOLEAN_REWRITE
                return super.parse(query)
            } finally {
                multiTermRewriteMethod = oldMethod
            }
        }

        complexPhrases = ArrayList()
        val q = super.parse(query)
        isPass2ResolvingPhrases = true
        try {
            val iterator = complexPhrases!!.iterator()
            while (iterator.hasNext()) {
                currentPhraseQuery = iterator.next()
                currentPhraseQuery!!.parsePhraseElements(this)
            }
        } finally {
            isPass2ResolvingPhrases = false
        }
        return q
    }

    override fun newTermQuery(term: Term, boost: Float): Query {
        if (isPass2ResolvingPhrases) {
            try {
                checkPhraseClauseIsForSameField(term.field())
            } catch (pe: ParseException) {
                throw RuntimeException("Error parsing complex phrase", pe)
            }
        }
        return super.newTermQuery(term, boost)
    }

    @Throws(ParseException::class)
    private fun checkPhraseClauseIsForSameField(field: String) {
        if (field != currentPhraseQuery!!.field) {
            throw ParseException("Cannot have clause for field \"$field\" nested in phrase  for field \"${currentPhraseQuery!!.field}\"")
        }
    }

    @Throws(ParseException::class)
    override fun getWildcardQuery(field: String, termStr: String): Query {
        if (isPass2ResolvingPhrases) {
            checkPhraseClauseIsForSameField(field)
        }
        return super.getWildcardQuery(field, termStr)
    }

    @Throws(ParseException::class)
    override fun getRangeQuery(
        field: String,
        part1: String?,
        part2: String?,
        startInclusive: Boolean,
        endInclusive: Boolean,
    ): Query {
        if (isPass2ResolvingPhrases) {
            checkPhraseClauseIsForSameField(field)
        }
        val originalRewriteMethod = multiTermRewriteMethod
        try {
            if (isPass2ResolvingPhrases) {
                multiTermRewriteMethod = MultiTermQuery.SCORING_BOOLEAN_REWRITE
            }
            return super.getRangeQuery(field, part1, part2, startInclusive, endInclusive)
        } finally {
            multiTermRewriteMethod = originalRewriteMethod
        }
    }

    @Throws(ParseException::class)
    override fun getFuzzyQuery(field: String, termStr: String, minSimilarity: Float): Query {
        if (isPass2ResolvingPhrases) {
            checkPhraseClauseIsForSameField(field)
        }
        return super.getFuzzyQuery(field, termStr, minSimilarity)
    }

    /*
     * Used to handle the query content in between quotes and produced Span-based
     * interpretations of the clauses.
     */
    class ComplexPhraseQuery(
        val field: String,
        val phrasedQueryStringContents: String,
        val slopFactor: Int,
        private val inOrder: Boolean,
    ) : Query() {
        private val contents = arrayOfNulls<Query>(1)

        @Throws(ParseException::class)
        fun parsePhraseElements(qp: ComplexPhraseQueryParser) {
            val oldDefaultParserField = qp.field
            try {
                qp.field = this.field
                contents[0] = qp.parse(phrasedQueryStringContents)
            } finally {
                qp.field = oldDefaultParserField
            }
        }

        override fun visit(visitor: QueryVisitor) {
            visitor.visitLeaf(this)
        }

        override fun rewrite(indexSearcher: IndexSearcher): Query {
            val contents = this.contents[0]!!
            if (contents is TermQuery || contents is MultiTermQuery || contents is SynonymQuery) {
                return contents
            }
            var numNegatives = 0
            if (contents !is BooleanQuery) {
                throw IllegalArgumentException(
                    "Unknown query type \"${contents::class.qualifiedName}\" found in phrase query string \"$phrasedQueryStringContents\""
                )
            }
            val bq = contents
            val allSpanClauses = arrayOfNulls<SpanQuery>(bq.clauses().size)
            var i = 0
            for (clause in bq) {
                var qc = clause.query
                qc = indexSearcher.rewrite(qc)
                if (clause.occur == BooleanClause.Occur.MUST_NOT) {
                    numNegatives++
                }
                while (qc is BoostQuery) {
                    qc = qc.query
                }

                if (qc is BooleanQuery || qc is SynonymQuery) {
                    val sc = ArrayList<SpanQuery>()
                    val booleanClause = if (qc is BooleanQuery) qc else convert(qc as SynonymQuery)
                    addComplexPhraseClause(sc, booleanClause)
                    allSpanClauses[i] = if (sc.isNotEmpty()) {
                        sc[0]
                    } else {
                        SpanTermQuery(Term(field, "Dummy clause because no terms found - must match nothing"))
                    }
                } else if (qc is MatchNoDocsQuery) {
                    allSpanClauses[i] = SpanTermQuery(Term(field, "Dummy clause because no terms found - must match nothing"))
                } else {
                    if (qc is TermQuery) {
                        allSpanClauses[i] = SpanTermQuery(qc.getTerm())
                    } else {
                        throw IllegalArgumentException(
                            "Unknown query type \"${qc::class.qualifiedName}\" found in phrase query string \"$phrasedQueryStringContents\""
                        )
                    }
                }
                i += 1
            }

            if (numNegatives == 0) {
                return SpanNearQuery(allSpanClauses.map { it!! }.toTypedArray(), slopFactor, inOrder)
            }

            val positiveClauses = ArrayList<SpanQuery>()
            i = 0
            for (clause in bq) {
                if (clause.occur != BooleanClause.Occur.MUST_NOT) {
                    positiveClauses.add(allSpanClauses[i]!!)
                }
                i += 1
            }

            val includeClauses = positiveClauses.toTypedArray()
            val include =
                if (includeClauses.size == 1) includeClauses[0]
                else SpanNearQuery(includeClauses, slopFactor + numNegatives, inOrder)
            val exclude = SpanNearQuery(allSpanClauses.map { it!! }.toTypedArray(), slopFactor, inOrder)
            return SpanNotQuery(include, exclude)
        }

        private fun convert(qc: SynonymQuery): BooleanQuery {
            val bqb = BooleanQuery.Builder()
            for (t in qc.getTerms()) {
                bqb.add(BooleanClause(TermQuery(t), Occur.SHOULD))
            }
            return bqb.build()
        }

        private fun addComplexPhraseClause(spanClauses: MutableList<SpanQuery>, qc: BooleanQuery) {
            val ors = ArrayList<SpanQuery>()
            val nots = ArrayList<SpanQuery>()

            for (clause in qc) {
                var childQuery = clause.query
                while (childQuery is BoostQuery) {
                    childQuery = childQuery.query
                }

                var chosenList = ors
                if (clause.occur == BooleanClause.Occur.MUST_NOT) {
                    chosenList = nots
                }

                if (childQuery is TermQuery) {
                    chosenList.add(SpanTermQuery(childQuery.getTerm()))
                } else if (childQuery is BooleanQuery) {
                    addComplexPhraseClause(chosenList, childQuery)
                } else if (childQuery is MatchNoDocsQuery) {
                    chosenList.add(SpanTermQuery(Term(field, "Dummy clause because no terms found - must match nothing")))
                } else {
                    throw IllegalArgumentException("Unknown query type:${childQuery::class.qualifiedName}")
                }
            }
            if (ors.isEmpty()) {
                return
            }
            val soq = SpanOrQuery(*ors.toTypedArray())
            if (nots.isEmpty()) {
                spanClauses.add(soq)
            } else {
                val snqs = SpanOrQuery(*nots.toTypedArray())
                val snq = SpanNotQuery(soq, snqs)
                spanClauses.add(snq)
            }
        }

        override fun toString(field: String?): String {
            val sb = StringBuilder()
            if (this.field != field) {
                sb.append(this.field).append(":")
            }
            sb.append("\"").append(phrasedQueryStringContents).append("\"")
            if (slopFactor != 0) {
                sb.append("~").append(slopFactor)
            }
            return sb.toString()
        }

        override fun hashCode(): Int {
            var result = classHash()
            result = 31 * result + field.hashCode()
            result = 31 * result + phrasedQueryStringContents.hashCode()
            result = 31 * result + slopFactor
            result = 31 * result + if (inOrder) 1 else 0
            return result
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other)
                && field == (other as ComplexPhraseQuery).field
                && phrasedQueryStringContents == other.phrasedQueryStringContents
                && slopFactor == other.slopFactor
                && inOrder == other.inOrder
        }
    }
}
