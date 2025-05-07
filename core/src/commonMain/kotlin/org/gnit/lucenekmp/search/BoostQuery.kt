package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.isFinite
import kotlin.reflect.cast


/**
 * A [Query] wrapper that allows to give a boost to the wrapped query. Boost values that are
 * less than one will give less importance to this query compared to other ones while values that
 * are greater than one will give more importance to the scores returned by this query.
 *
 *
 * More complex boosts can be applied by using FunctionScoreQuery in the lucene-queries module
 */
class BoostQuery(query: Query, boost: Float) : Query() {
    /** Return the wrapped [Query].  */
    val query: Query

    /** Return the applied boost.  */
    val boost: Float

    /**
     * Sole constructor: wrap `query` in such a way that the produced scores will be boosted by
     * `boost`.
     */
    init {
        this.query = requireNotNull<Query>(query)
        require(
            !(Float.isFinite(boost) == false || Float.compare(
                boost,
                0f
            ) < 0)
        ) { "boost must be a positive float, got $boost" }
        this.boost = boost
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: BoostQuery): Boolean {
        return query == other.query
                && Float.floatToIntBits(boost) == Float.floatToIntBits(other.boost)
    }

    override fun hashCode(): Int {
        var h = classHash()
        h = 31 * h + query.hashCode()
        h = 31 * h + Float.floatToIntBits(boost)
        return h
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten = query.rewrite(indexSearcher)

        if (boost == 1f) {
            return rewritten
        }

        if (rewritten is BoostQuery) {
            val `in` = rewritten
            return BoostQuery(`in`.query, boost * `in`.boost)
        }

        if (rewritten is MatchNoDocsQuery) {
            // bubble up MatchNoDocsQuery
            return rewritten
        }

        if (boost == 0f && rewritten::class != ConstantScoreQuery::class) {
            // so that we pass needScores=false
            return BoostQuery(ConstantScoreQuery(rewritten), 0f)
        }

        if (query !== rewritten) {
            return BoostQuery(rewritten, boost)
        }

        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        query.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this))
    }

    override fun toString(field: String?): String {
        val builder = StringBuilder()
        builder.append("(")
        builder.append(query.toString(field))
        builder.append(")")
        builder.append("^")
        builder.append(boost)
        return builder.toString()
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return query.createWeight(searcher, scoreMode, this@BoostQuery.boost * boost)
    }
}
