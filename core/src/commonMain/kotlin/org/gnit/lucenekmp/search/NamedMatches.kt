package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext

/**
 * Utility class to help extract the set of sub queries that have matched from a larger query.
 *
 * <p>Individual subqueries may be wrapped using [wrapQuery], and the matching queries for a
 * particular document can then be pulled from the parent [Query]'s [Matches] object by calling
 * [findNamedMatches].
 */
class NamedMatches(
    /** Returns the name of this [Matches] */
    val name: String,
    private val `in`: Matches,
) : Matches {
    override fun getMatches(field: String): MatchesIterator? {
        return `in`.getMatches(field)
    }

    override val subMatches: MutableCollection<Matches>
        get() = mutableListOf(`in`)

    override fun iterator(): Iterator<String> {
        return `in`.iterator()
    }

    companion object {
        /** Wrap a Query so that it associates a name with its [Matches] */
        fun wrapQuery(name: String, `in`: Query): Query {
            return NamedQuery(name, `in`)
        }

        /** Finds all [NamedMatches] in a [Matches] tree */
        fun findNamedMatches(matches: Matches): List<NamedMatches> {
            val nm = mutableListOf<NamedMatches>()
            val toProcess = ArrayDeque<Matches>()
            toProcess.add(matches)
            while (toProcess.isNotEmpty()) {
                val next = toProcess.removeFirst()
                if (next is NamedMatches) {
                    nm.add(next)
                }
                toProcess.addAll(next.subMatches)
            }
            return nm
        }
    }

    private class NamedQuery(
        private val name: String,
        private val `in`: Query,
    ) : Query() {
        @Throws(Exception::class)
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            val w = `in`.createWeight(searcher, scoreMode, boost)
            return object : FilterWeight(w) {
                @Throws(IOException::class)
                override fun matches(context: LeafReaderContext, doc: Int): Matches? {
                    val m = `in`.matches(context, doc)
                    if (m == null) {
                        return null
                    }
                    return NamedMatches(name, m)
                }
            }
        }

        @Throws(Exception::class)
        override fun rewrite(indexSearcher: IndexSearcher): Query {
            val rewritten = `in`.rewrite(indexSearcher)
            if (rewritten != `in`) {
                return NamedQuery(name, rewritten)
            }
            return this
        }

        override fun toString(field: String?): String {
            return "NamedQuery($name,${`in`.toString(field)})"
        }

        override fun visit(visitor: QueryVisitor) {
            val sub = visitor.getSubVisitor(BooleanClause.Occur.MUST, this)
            `in`.visit(sub)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as NamedQuery
            return name == other.name && `in` == other.`in`
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + `in`.hashCode()
            return result
        }
    }
}
