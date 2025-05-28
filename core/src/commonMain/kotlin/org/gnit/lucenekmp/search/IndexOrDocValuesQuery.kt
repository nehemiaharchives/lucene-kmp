package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * A query that uses either an index structure (points or terms) or doc values in order to run a
 * query, depending which one is more efficient. This is typically useful for range queries, whose
 * [Weight.scorer] is costly to create since it usually needs to sort large lists of doc ids.
 * For instance, for a field that both indexed [LongPoint]s and [ ]s with the same values, an efficient range query could be created by
 * doing:
 *
 * <pre class="prettyprint">
 * String field;
 * long minValue, maxValue;
 * Query pointQuery = LongPoint.newRangeQuery(field, minValue, maxValue);
 * Query dvQuery = SortedNumericDocValuesField.newSlowRangeQuery(field, minValue, maxValue);
 * Query query = new IndexOrDocValuesQuery(pointQuery, dvQuery);
</pre> *
 *
 * The above query will be efficient as it will use points in the case that they perform better, ie.
 * when we need a good lead iterator that will be almost entirely consumed; and doc values
 * otherwise, ie. in the case that another part of the query is already leading iteration but we
 * still need the ability to verify that some documents match.
 *
 *
 * Some field types that work well with [IndexOrDocValuesQuery] are [ ], [org.apache.lucene.document.LongField], [ ], [org.apache.lucene.document.DoubleField], and
 * [org.apache.lucene.document.KeywordField]. These fields provide both an indexed structure
 * and doc values.
 *
 *
 * **NOTE**This query currently only works well with point range/exact queries and their
 * equivalent doc values queries.
 *
 * @lucene.experimental
 */
class IndexOrDocValuesQuery
/**
 * Create an [IndexOrDocValuesQuery]. Both provided queries must match the same documents
 * and give the same scores.
 *
 * @param indexQuery a query that has a good iterator but whose scorer may be costly to create
 * @param randomAccessQuery a query whose scorer is cheap to create that can quickly check whether a given
 * document matches
 */(
    /** Return the wrapped query that may be costly to initialize but has a good iterator.  */
    val indexQuery: Query,
    /**
     * Return the wrapped query that may be slow at identifying all matching documents, but which is
     * cheap to initialize and can efficiently verify that some documents match.
     */
    val randomAccessQuery: Query
) : Query() {
    override fun toString(field: String?): String {
        return ("IndexOrDocValuesQuery(indexQuery="
                + indexQuery.toString(field)
                + ", dvQuery="
                + randomAccessQuery.toString(field)
                + ")")
    }

    override fun equals(obj: Any?): Boolean {
        if (!sameClassAs(obj)) {
            return false
        }
        val that = obj as IndexOrDocValuesQuery
        return indexQuery == that.indexQuery && randomAccessQuery == that.randomAccessQuery
    }

    override fun hashCode(): Int {
        var h = classHash()
        h = 31 * h + indexQuery.hashCode()
        h = 31 * h + randomAccessQuery.hashCode()
        return h
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val indexRewrite = indexQuery.rewrite(indexSearcher)
        val dvRewrite = randomAccessQuery.rewrite(indexSearcher)
        if (indexRewrite is MatchAllDocsQuery || dvRewrite is MatchAllDocsQuery) {
            return MatchAllDocsQuery()
        }
        if (indexQuery !== indexRewrite || this.randomAccessQuery !== dvRewrite) {
            return IndexOrDocValuesQuery(indexRewrite, dvRewrite)
        }
        return this
    }

    override fun visit(visitor: QueryVisitor) {
        val v: QueryVisitor = visitor.getSubVisitor(BooleanClause.Occur.MUST, this)
        indexQuery.visit(v)
        randomAccessQuery.visit(v)
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        val indexWeight = indexQuery.createWeight(searcher, scoreMode, boost)
        val dvWeight = randomAccessQuery.createWeight(searcher, scoreMode, boost)
        return object : Weight(this) {
            @Throws(IOException::class)
            override fun matches(context: LeafReaderContext, doc: Int): Matches {
                // We need to check a single doc, so the dv query should perform better
                return dvWeight.matches(context, doc)!!
            }

            @Throws(IOException::class)
            override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                // We need to check a single doc, so the dv query should perform better
                return dvWeight.explain(context, doc)
            }

            @Throws(IOException::class)
            override fun count(context: LeafReaderContext): Int {
                val count = indexWeight.count(context)
                if (count != -1) {
                    return count
                }
                return dvWeight.count(context)
            }

            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val indexScorerSupplier = indexWeight.scorerSupplier(context)
                val dvScorerSupplier = dvWeight.scorerSupplier(context)
                if (indexScorerSupplier == null || dvScorerSupplier == null) {
                    return null
                }
                return object : ScorerSupplier() {
                    @Throws(IOException::class)
                    override fun get(leadCost: Long): Scorer {
                        // At equal costs, doc values tend to be worse than points since they
                        // still need to perform one comparison per document while points can
                        // do much better than that given how values are organized. So we give
                        // an arbitrary 8x penalty to doc values.
                        val threshold = cost() ushr 3
                        return if (threshold <= leadCost) {
                            indexScorerSupplier.get(leadCost)
                        } else {
                            dvScorerSupplier.get(leadCost)
                        }
                    }

                    @Throws(IOException::class)
                    override fun bulkScorer(): BulkScorer? {
                        // Bulk scorers need to consume the entire set of docs, so using an
                        // index structure should perform better
                        return indexScorerSupplier.bulkScorer()
                    }

                    override fun cost(): Long {
                        return indexScorerSupplier.cost()
                    }
                }
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                // Both index and dv query should return the same values, so we can use
                // the index query's cachehelper here
                return indexWeight.isCacheable(ctx)
            }
        }
    }
}
