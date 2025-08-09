package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Automaton

/**
 * A Query that matches documents containing terms with a specified prefix. A PrefixQuery is built
 * by QueryParser for input like `app*`.
 *
 *
 * This query uses the [MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE] rewrite method.
 */
class PrefixQuery
/**
 * Constructs a query for terms starting with `prefix` using a defined RewriteMethod
 */
/** Constructs a query for terms starting with `prefix`.  */
(
    prefix: Term,
    rewriteMethod: RewriteMethod = CONSTANT_SCORE_BLENDED_REWRITE
) : AutomatonQuery(prefix, toAutomaton(prefix.bytes()), true, rewriteMethod) {
    val prefix: Term
        /** Returns the prefix of this query.  */
        get() = term

    /** Prints a user-readable version of this query.  */
    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        if (field != field) {
            buffer.append(field)
            buffer.append(':')
        }
        buffer.append(term.text())
        buffer.append('*')
        return buffer.toString()
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + term.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }
        if (!super.equals(obj)) {
            return false
        }
        // super.equals() ensures we are the same class
        val other = obj as PrefixQuery
        if (term != other.term) {
            return false
        }
        return true
    }

    companion object {
        /** Build an automaton accepting all terms with the specified prefix.  */
        fun toAutomaton(prefix: BytesRef): Automaton {
            val numStatesAndTransitions: Int = prefix.length + 1
            val automaton =                Automaton(numStatesAndTransitions, numStatesAndTransitions)
            var lastState: Int = automaton.createState()
            for (i in 0..<prefix.length) {
                val state: Int = automaton.createState()
                automaton.addTransition(lastState, state, prefix.bytes[prefix.offset + i].toInt() and 0xff)
                lastState = state
            }
            automaton.setAccept(lastState, true)
            automaton.addTransition(lastState, lastState, 0, 255)
            automaton.finishState()
            assert(automaton.isDeterministic)
            return automaton
        }
    }
}
