package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.Operations

/**
 * Implements the wildcard search query. Supported wildcards are `*`, which matches any
 * character sequence (including the empty one), and ``, which matches any single
 * character. '\' is the escape character.
 *
 *
 * Note this query can be slow, as it needs to iterate over many terms. In order to prevent
 * extremely slow WildcardQueries, a Wildcard term should not start with the wildcard `*`
 *
 *
 * This query uses the [MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE] rewrite method.
 *
 * @see AutomatonQuery
 */
class WildcardQuery
/**
 * Constructs a query for terms matching `term`.
 *
 * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
 * wildcard. Set higher to allow more complex queries and lower to prevent memory exhaustion.
 * Use [Operations.DEFAULT_DETERMINIZE_WORK_LIMIT] as a decent default if you don't
 * otherwise know what to specify.
 * @param rewriteMethod the rewrite method to use when building the final query
 */
/**
 * Constructs a query for terms matching `term`.
 *
 * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
 * wildcard. Set higher to allow more complex queries and lower to prevent memory exhaustion.
 * Use [Operations.DEFAULT_DETERMINIZE_WORK_LIMIT] as a decent default if you don't
 * otherwise know what to specify.
 */
/** Constructs a query for terms matching `term`.  */
(
    override val term: Term,
    determinizeWorkLimit: Int = Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
    rewriteMethod: RewriteMethod = CONSTANT_SCORE_BLENDED_REWRITE
) : AutomatonQuery(term, toAutomaton(term, determinizeWorkLimit), false, rewriteMethod) {

    /** Prints a user-readable version of this query.  */
    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        if (this.field != field) {
            buffer.append(this.field)
            buffer.append(":")
        }
        buffer.append(term.text())
        return buffer.toString()
    }

    companion object {
        /** String equality with support for wildcards  */
        const val WILDCARD_STRING: Char = '*'

        /** Char equality with support for wildcards  */
        const val WILDCARD_CHAR: Char = '?'

        /** Escape character  */
        const val WILDCARD_ESCAPE: Char = '\\'

        /**
         * Convert Lucene wildcard syntax into an automaton.
         *
         * @lucene.internal
         */
        fun toAutomaton(
            wildcardquery: Term,
            determinizeWorkLimit: Int
        ): Automaton {
            val automata: MutableList<Automaton> = mutableListOf()

            val wildcardText: String = wildcardquery.text()

            var i = 0
            while (i < wildcardText.length) {
                val c: Int = wildcardText.codePointAt(i)
                var length: Int = Character.charCount(c)
                when (c.toChar()) {
                    WILDCARD_STRING -> automata.add(Automata.makeAnyString())
                    WILDCARD_CHAR -> automata.add(Automata.makeAnyChar())
                    WILDCARD_ESCAPE -> {
                        // add the next codepoint instead, if it exists
                        if (i + length < wildcardText.length) {
                            val nextChar: Int = wildcardText.codePointAt(i + length)
                            length += Character.charCount(nextChar)
                            automata.add(Automata.makeChar(nextChar))
                            break
                        } // else fallthru, lenient parsing with a trailing \

                        automata.add(Automata.makeChar(c))
                    }

                    else -> automata.add(Automata.makeChar(c))
                }
                i += length
            }

            return Operations.determinize(
                Operations.concatenate(
                    automata
                ), determinizeWorkLimit
            )
        }
    }
}
