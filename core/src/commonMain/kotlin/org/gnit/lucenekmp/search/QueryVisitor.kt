package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.automaton.ByteRunAutomaton
import org.gnit.lucenekmp.index.Term

/**
 * Allows recursion through a query tree
 *
 * @see Query.visit
 */
abstract class QueryVisitor {
    /**
     * Called by leaf queries that match on specific terms
     *
     * @param query the leaf query
     * @param terms the terms the query will match on
     */
    open fun consumeTerms(query: Query, vararg terms: Term) {}

    /**
     * Called by leaf queries that match on a class of terms.
     *
     * @param query the leaf query.
     * @param field the field queried against.
     * @param automaton a supplier (lambda) for an automaton defining which terms match.
     * @lucene.experimental
     */
    fun consumeTermsMatching(query: Query, field: String, automaton: () -> ByteRunAutomaton) {
        visitLeaf(query) // default implementation for backward compatibility
    }

    /**
     * Called by leaf queries that do not match on terms
     *
     * @param query the query
     */
    fun visitLeaf(query: Query?) {}

    /**
     * Whether or not this field is of interest to the visitor
     *
     *
     * Implement this to avoid collecting terms from heavy queries such as [TermInSetQuery]
     * that are not running on fields of interest
     */
    fun acceptField(field: String?): Boolean {
        return true
    }

    /**
     * Pulls a visitor instance for visiting child clauses of a query
     *
     *
     * The default implementation returns `this`, unless `occur` is equal to [ ][BooleanClause.Occur.MUST_NOT] in which case it returns [.EMPTY_VISITOR]
     *
     * @param occur the relationship between the parent and its children
     * @param parent the query visited
     */
    fun getSubVisitor(occur: BooleanClause.Occur, parent: Query): QueryVisitor {
        if (occur === BooleanClause.Occur.MUST_NOT) {
            return EMPTY_VISITOR
        }
        return this
    }

    companion object {
        /**
         * Builds a `QueryVisitor` instance that collects all terms that may match a query
         *
         * @param termSet a `Set` to add collected terms to
         */
        fun termCollector(termSet: MutableSet<Term?>): QueryVisitor {
            return object : QueryVisitor() {
                override fun consumeTerms(query: Query, vararg terms: Term) {
                    termSet.addAll(listOf(*terms))
                }
            }
        }

        /** A QueryVisitor implementation that does nothing  */
        val EMPTY_VISITOR: QueryVisitor = object : QueryVisitor() {}
    }
}
