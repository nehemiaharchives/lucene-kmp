package org.gnit.lucenekmp.tests.search

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Weight
import kotlin.random.Random

/** Assertion-enabled query. */
class AssertingQuery(
    private val random: Random,
    private val `in`: Query
) : Query() {
    /** Sole constructor. */

    /** Wrap a query if necessary. */
    companion object {
        fun wrap(random: Random, query: Query): Query {
            return if (query is AssertingQuery) query else AssertingQuery(random, query)
        }
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        assert(boost >= 0)
        return AssertingWeight(
            Random(random.nextLong()),
            `in`.createWeight(searcher, scoreMode, boost),
            scoreMode
        )
    }

    override fun toString(field: String?): String {
        return `in`.toString(field)
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && `in` == (other as AssertingQuery).`in`
    }

    override fun hashCode(): Int {
        return -`in`.hashCode()
    }

    fun getRandom(): Random {
        return random
    }

    fun getIn(): Query {
        return `in`
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten = `in`.rewrite(indexSearcher)
        return if (rewritten === `in`) {
            super.rewrite(indexSearcher)
        } else {
            wrap(Random(random.nextLong()), rewritten)
        }
    }

    override fun visit(visitor: QueryVisitor) {
        `in`.visit(visitor)
    }
}
