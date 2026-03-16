package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScoringRewrite
import org.gnit.lucenekmp.search.TopTermsRewrite

/**
 * Wraps any [MultiTermQuery] as a [SpanQuery], so it can be nested within other
 * SpanQuery classes.
 *
 * <p>The query is rewritten by default to a [SpanOrQuery] containing the expanded terms, but
 * this can be customized.
 *
 * <p>Example:
 *
 * <blockquote>
 *
 * <pre class="prettyprint">{@code
 * WildcardQuery wildcard = new WildcardQuery(new Term("field", "bro?n"));
 * SpanQuery spanWildcard = new SpanMultiTermQueryWrapper<WildcardQuery>(wildcard);
 * // do something with spanWildcard, such as use it in a SpanFirstQuery
 * }</pre>
 *
 * </blockquote>
 */
class SpanMultiTermQueryWrapper<Q : MultiTermQuery>(
    protected val query: Q,
) : SpanQuery() {
    private var rewriteMethod: SpanRewriteMethod = selectRewriteMethod(query)

    private fun selectRewriteMethod(query: MultiTermQuery): SpanRewriteMethod {
        val method = query.rewriteMethod
        return if (method is TopTermsRewrite<*>) {
            val pqsize = method.size
            TopTermsSpanBooleanQueryRewrite(pqsize)
        } else {
            SCORING_SPAN_QUERY_REWRITE
        }
    }

    /** Expert: returns the rewriteMethod */
    fun getRewriteMethod(): SpanRewriteMethod {
        return rewriteMethod
    }

    /** Expert: sets the rewrite method. This only makes sense to be a span rewrite method. */
    fun setRewriteMethod(rewriteMethod: SpanRewriteMethod) {
        this.rewriteMethod = rewriteMethod
    }

    override fun getField(): String {
        return query.field
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        throw IllegalArgumentException("Rewrite first!")
    }

    /** Returns the wrapped query */
    fun getWrappedQuery(): Query {
        return query
    }

    override fun toString(field: String?): String {
        val builder = StringBuilder()
        builder.append("SpanMultiTermQueryWrapper(")
        // NOTE: query.toString must be placed in a temp local to avoid compile errors on Java 8u20
        // see
        // https://bugs.openjdk.java.net/browse/JDK-8056984?page=com.atlassian.streams.streams-jira-plugin:activity-stream-issue-tab
        val queryStr = query.toString(field)
        builder.append(queryStr)
        builder.append(")")
        return builder.toString()
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        return rewriteMethod.rewrite(indexSearcher, query)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(query.field)) {
            query.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this))
        }
    }

    override fun hashCode(): Int {
        return classHash() * 31 + query.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && query == (other as SpanMultiTermQueryWrapper<*>).query
    }

    /** Abstract class that defines how the query is rewritten. */
    abstract class SpanRewriteMethod : MultiTermQuery.RewriteMethod() {
        @Throws(IOException::class)
        abstract override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): SpanQuery
    }

    /**
     * A rewrite method that first translates each term into a SpanTermQuery in a [BooleanClause.Occur.SHOULD]
     * clause in a BooleanQuery, and keeps the scores as computed by the query.
     *
     * @see setRewriteMethod
     */
    companion object {
        val SCORING_SPAN_QUERY_REWRITE: SpanRewriteMethod =
            object : SpanRewriteMethod() {
                private val delegate =
                    object : ScoringRewrite<MutableList<SpanQuery>>() {
                        override val topLevelBuilder: MutableList<SpanQuery>
                            get() = mutableListOf()

                        override fun build(builder: MutableList<SpanQuery>): Query {
                            return SpanOrQuery(*builder.toTypedArray())
                        }

                        override fun checkMaxClauseCount(count: Int) {
                            // we accept all terms as SpanOrQuery has no limits
                        }

                        override fun addClause(
                            topLevel: MutableList<SpanQuery>,
                            term: Term,
                            docCount: Int,
                            boost: Float,
                            states: TermStates?,
                        ) {
                            val q = SpanTermQuery(term, states)
                            topLevel.add(q)
                        }
                    }

                @Throws(IOException::class)
                override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): SpanQuery {
                    return delegate.rewrite(indexSearcher, query) as SpanQuery
                }
            }
    }

    /**
     * A rewrite method that first translates each term into a SpanTermQuery in a [BooleanClause.Occur.SHOULD]
     * clause in a BooleanQuery, and keeps the scores as computed by the query.
     *
     * <p>This rewrite method only uses the top scoring terms so it will not overflow the boolean max
     * clause count.
     *
     * @see setRewriteMethod
     */
    class TopTermsSpanBooleanQueryRewrite(size: Int) : SpanRewriteMethod() {
        private val delegate =
            object : TopTermsRewrite<MutableList<SpanQuery>>(size) {
                override val maxSize: Int
                    get() = Int.MAX_VALUE

                override val topLevelBuilder: MutableList<SpanQuery>
                    get() = mutableListOf()

                override fun build(builder: MutableList<SpanQuery>): Query {
                    return SpanOrQuery(*builder.toTypedArray())
                }

                override fun addClause(
                    topLevel: MutableList<SpanQuery>,
                    term: Term,
                    docFreq: Int,
                    boost: Float,
                    states: TermStates?,
                ) {
                    val q = SpanTermQuery(term, states)
                    topLevel.add(q)
                }
            }

        /** return the maximum priority queue size */
        fun getSize(): Int {
            return delegate.size
        }

        @Throws(IOException::class)
        override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): SpanQuery {
            return delegate.rewrite(indexSearcher, query) as SpanQuery
        }

        override fun hashCode(): Int {
            return 31 * delegate.hashCode()
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) return true
            if (obj == null) return false
            if (this::class != obj::class) return false
            val other = obj as TopTermsSpanBooleanQueryRewrite
            return delegate == other.delegate
        }
    }
}
