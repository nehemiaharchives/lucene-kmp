package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.jdkport.Objects

/**
 * A Basic abstract query that all IndriQueries can extend to implement toString, equals,
 * getClauses, and iterator.
 */
abstract class IndriQuery(private val clauses: MutableList<BooleanClause>) : Query(),
    Iterable<BooleanClause> {
    override abstract fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight

    override fun toString(field: String?): String {
        val buffer = StringBuilder()

        var i = 0
        for (c in this) {
            buffer.append(c.occur.toString())

            val subQuery = c.query
            if (subQuery is BooleanQuery) {
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

        return buffer.toString()
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(other as IndriQuery)
    }

    override fun visit(visitor: QueryVisitor) {
        visitor.visitLeaf(this)
    }

    private fun equalsTo(other: IndriQuery): Boolean {
        return clauses == other.clauses
    }

    override fun hashCode(): Int {
        var hashCode = Objects.hash(clauses)
        if (hashCode == 0) {
            hashCode = 1
        }
        return hashCode
    }

    override fun iterator(): Iterator<BooleanClause> {
        return clauses.iterator()
    }

    fun getClauses(): MutableList<BooleanClause> {
        return this.clauses
    }
}
