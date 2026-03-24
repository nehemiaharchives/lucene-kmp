package org.gnit.lucenekmp.search

/** A Query that matches documents matching combinations of subqueries. */
class IndriAndQuery(clauses: MutableList<BooleanClause>) : IndriQuery(clauses) {
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        val query = this
        return IndriAndWeight(query, searcher, ScoreMode.TOP_SCORES, boost)
    }
}
