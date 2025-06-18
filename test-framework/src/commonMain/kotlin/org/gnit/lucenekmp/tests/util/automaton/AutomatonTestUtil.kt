package org.gnit.lucenekmp.tests.util.automaton

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.random.Random
import kotlin.experimental.ExperimentalNativeApi

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

@OptIn(ExperimentalNativeApi::class)
class RandomAcceptedStrings(private val a: Automaton) {
    private val leadsToAccept = mutableMapOf<Transition, Boolean>()
    private val transitions: Array<Array<Transition>> = a.sortedTransitions

    init {
        if (a.numStates == 0) {
            throw IllegalArgumentException("this automaton accepts nothing")
        }
        val allArriving = mutableMapOf<Int, MutableList<ArrivingTransition>>()
        val q = ArrayDeque<Int>()
        val seen = HashSet<Int>()
        val numStates = a.numStates
        for (s in 0 until numStates) {
            for (t in transitions[s]) {
                val tl = allArriving.getOrPut(t.dest) { mutableListOf() }
                tl.add(ArrivingTransition(s, t))
            }
            if (a.isAccept(s)) {
                q.add(s)
                seen.add(s)
            }
        }
        while (q.isNotEmpty()) {
            val s = q.removeFirst()
            val arriving = allArriving[s]
            if (arriving != null) {
                for (at in arriving) {
                    val from = at.from
                    if (!seen.contains(from)) {
                        q.add(from)
                        seen.add(from)
                        leadsToAccept[at.t] = true
                    }
                }
            }
        }
    }

    fun getRandomAcceptedString(r: Random): IntArray {
        var codePoints = IntArray(0)
        var codepointCount = 0
        var s = 0
        while (true) {
            if (a.isAccept(s)) {
                if (a.getNumTransitions(s) == 0) {
                    break
                } else if (r.nextBoolean()) {
                    break
                }
            }
            if (a.getNumTransitions(s) == 0) {
                throw RuntimeException("this automaton has dead states")
            }
            val cheat = r.nextBoolean()
            val t: Transition = if (cheat) {
                val toAccept = mutableListOf<Transition>()
                for (t0 in transitions[s]) {
                    if (leadsToAccept.containsKey(t0)) {
                        toAccept.add(t0)
                    }
                }
                if (toAccept.isEmpty()) {
                    transitions[s][r.nextInt(transitions[s].size)]
                } else {
                    toAccept[r.nextInt(toAccept.size)]
                }
            } else {
                transitions[s][r.nextInt(transitions[s].size)]
            }
            codePoints = ArrayUtil.grow(codePoints, codepointCount + 1)
            codePoints[codepointCount++] = getRandomCodePoint(r, t.min, t.max)
            s = t.dest
        }
        return ArrayUtil.copyOfSubArray(codePoints, 0, codepointCount)
    }

    private fun getRandomCodePoint(r: Random, min: Int, max: Int): Int {
        val code: Int
        if (max < UnicodeUtil.UNI_SUR_HIGH_START || min > UnicodeUtil.UNI_SUR_HIGH_END) {
            code = min + r.nextInt(max - min + 1)
        } else if (min >= UnicodeUtil.UNI_SUR_HIGH_START) {
            if (max > UnicodeUtil.UNI_SUR_LOW_END) {
                code = 1 + UnicodeUtil.UNI_SUR_LOW_END + r.nextInt(max - UnicodeUtil.UNI_SUR_LOW_END)
            } else {
                throw IllegalArgumentException("transition accepts only surrogates: min=$min max=$max")
            }
        } else if (max <= UnicodeUtil.UNI_SUR_LOW_END) {
            if (min < UnicodeUtil.UNI_SUR_HIGH_START) {
                code = min + r.nextInt(UnicodeUtil.UNI_SUR_HIGH_START - min)
            } else {
                throw IllegalArgumentException("transition accepts only surrogates: min=$min max=$max")
            }
        } else {
            val gap1 = UnicodeUtil.UNI_SUR_HIGH_START - min
            val gap2 = max - UnicodeUtil.UNI_SUR_LOW_END
            val c = r.nextInt(gap1 + gap2)
            code = if (c < gap1) {
                min + c
            } else {
                UnicodeUtil.UNI_SUR_LOW_END + c - gap1 + 1
            }
        }
        assert(code >= min && code <= max && (code < UnicodeUtil.UNI_SUR_HIGH_START || code > UnicodeUtil.UNI_SUR_LOW_END))
        return code
    }

    private class ArrivingTransition(val from: Int, val t: Transition)
}
