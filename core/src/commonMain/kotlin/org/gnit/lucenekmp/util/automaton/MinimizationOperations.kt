package org.gnit.lucenekmp.util.automaton

/**
 * Simplified minimization utilities. These only determinize and remove dead
 * states, which is enough for basic correctness checks used by tests.
 */
object MinimizationOperations {
    fun minimize(a: Automaton, determinizeWorkLimit: Int): Automaton {
        var result = Operations.determinize(a, determinizeWorkLimit)
        result = Operations.removeDeadStates(result)
        return result
    }
}
