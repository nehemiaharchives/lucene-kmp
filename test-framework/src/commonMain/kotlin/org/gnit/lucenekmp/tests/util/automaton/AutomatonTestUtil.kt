package org.gnit.lucenekmp.tests.util.automaton

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.jdkport.BitSet

object AutomatonTestUtil {
    private const val MAX_RECURSION_LEVEL = 1000

    fun sameLanguage(a1: Automaton, a2: Automaton): Boolean {
        val d1 = Operations.determinize(Operations.removeDeadStates(a1), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val d2 = Operations.determinize(Operations.removeDeadStates(a2), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val diff1 = Operations.minus(d1, d2, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        if (!Operations.isEmpty(diff1)) return false
        val diff2 = Operations.minus(d2, d1, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        return Operations.isEmpty(diff2)
    }

    fun assertCleanDFA(a: Automaton) {
        assertCleanNFA(a)
        assertTrue(a.isDeterministic, "must be deterministic")
    }

    fun assertMinimalDFA(a: Automaton) {
        assertCleanDFA(a)
    }

    fun assertCleanNFA(a: Automaton) {
        assertFalse(Operations.hasDeadStatesFromInitial(a), "has dead states reachable from initial")
        assertFalse(Operations.hasDeadStatesToAccept(a), "has dead states leading to accept")
        assertFalse(Operations.hasDeadStates(a), "has unreachable dead states (ghost states)")
    }

    fun isFinite(a: Automaton): Boolean {
        if (a.numStates == 0) return true
        return isFinite(Transition(), a, 0, BitSet(a.numStates), BitSet(a.numStates), 0)
    }

    private fun isFinite(scratch: Transition, a: Automaton, state: Int, path: BitSet, visited: BitSet, level: Int): Boolean {
        if (level > MAX_RECURSION_LEVEL) {
            throw IllegalArgumentException("input automaton is too large: $level")
        }
        path.set(state)
        val numTransitions = a.initTransition(state, scratch)
        for (i in 0 until numTransitions) {
            a.getTransition(state, i, scratch)
            if (path.get(scratch.dest) || (!visited.get(scratch.dest) && !isFinite(scratch, a, scratch.dest, path, visited, level + 1))) {
                return false
            }
        }
        path.clear(state)
        visited.set(state)
        return true
    }
}
