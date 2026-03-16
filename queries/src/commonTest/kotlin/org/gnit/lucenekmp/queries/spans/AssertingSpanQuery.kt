package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode

/** Wraps a span query with asserts */
class AssertingSpanQuery(private val `in`: SpanQuery) : SpanQuery() {
    override fun getField(): String? {
        return `in`.getField()
    }

    override fun toString(field: String?): String {
        return "AssertingSpanQuery(${`in`.toString(field)})"
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        val weight = `in`.createWeight(searcher, scoreMode, boost)
        return AssertingSpanWeight(searcher, weight)
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val q = `in`.rewrite(indexSearcher)
        return if (q === `in`) {
            super.rewrite(indexSearcher)
        } else if (q is SpanQuery) {
            AssertingSpanQuery(q)
        } else {
            q
        }
    }

    override fun visit(visitor: QueryVisitor) {
        `in`.visit(visitor)
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && `in` == (other as AssertingSpanQuery).`in`
    }

    override fun hashCode(): Int {
        return `in`.hashCode()
    }
}
