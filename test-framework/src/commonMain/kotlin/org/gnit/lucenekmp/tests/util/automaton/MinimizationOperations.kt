package org.gnit.lucenekmp.tests.util.automaton

import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.jdkport.Character

/**
 * Operations for minimizing automata using Hopcroft's algorithm.
 * Ported from Lucene's MinimizationOperations (test utility).
 */
object MinimizationOperations {
    fun minimize(aInput: Automaton, determinizeWorkLimit: Int): Automaton {
        if (aInput.numStates == 0 || (!aInput.isAccept(0) && aInput.getNumTransitions(0) == 0)) {
            return Automaton()
        }
        var a = Operations.determinize(aInput, determinizeWorkLimit)
        if (a.getNumTransitions(0) == 1) {
            val tCheck = Transition()
            a.getTransition(0, 0, tCheck)
            if (tCheck.dest == 0 &&
                tCheck.min == Character.MIN_CODE_POINT &&
                tCheck.max == Character.MAX_CODE_POINT
            ) {
                return a
            }
        }
        a = Operations.totalize(a)

        val sigma = a.getStartPoints()
        val sigmaLen = sigma.size
        val statesLen = a.numStates

        val reverse = Array(statesLen) { Array<IntArrayList?>(sigmaLen) { null } }
        val partition = Array(statesLen) { IntHashSet() }
        val splitblock = Array(statesLen) { IntArrayList() }
        val block = IntArray(statesLen)
        val active = Array(statesLen) { Array(sigmaLen) { StateList.EMPTY } }
        val active2 = Array(statesLen) { Array<StateListNode?>(sigmaLen) { null } }
        val pending = ArrayDeque<IntPair>()
        val pending2 = BitSet(sigmaLen * statesLen)
        val split = BitSet(statesLen)
        val refine = BitSet(statesLen)
        val refine2 = BitSet(statesLen)
        val transition = Transition()
        for (q in 0 until statesLen) {
            splitblock[q] = IntArrayList()
            partition[q] = IntHashSet()
            for (x in 0 until sigmaLen) {
                active[q][x] = StateList.EMPTY
            }
        }
        for (q in 0 until statesLen) {
            val j = if (a.isAccept(q)) 0 else 1
            partition[j].add(q)
            block[q] = j
            transition.source = q
            transition.transitionUpto = -1
            for (x in 0 until sigmaLen) {
                val dest = a.next(transition, sigma[x])
                val r = reverse[dest]
                if (r[x] == null) {
                    r[x] = IntArrayList()
                }
                r[x]!!.add(q)
            }
        }
        for (j in 0..1) {
            for (x in 0 until sigmaLen) {
                for (qCursor in partition[j]) {
                    val q = qCursor!!.value
                    if (reverse[q][x] != null) {
                        var stateList = active[j][x]
                        if (stateList === StateList.EMPTY) {
                            stateList = StateList()
                            active[j][x] = stateList
                        }
                        active2[q][x] = stateList.add(q)
                    }
                }
            }
        }
        for (x in 0 until sigmaLen) {
            val j = if (active[0][x].size <= active[1][x].size) 0 else 1
            pending.addLast(IntPair(j, x))
            pending2.set(x * statesLen + j)
        }
        var k = 2
        while (pending.isNotEmpty()) {
            val ip = pending.removeFirst()
            val p = ip.n1
            val x = ip.n2
            pending2.clear(x * statesLen + p)
            var m = active[p][x].first
            while (m != null) {
                val r = reverse[m.q][x]
                if (r != null) {
                    for (iCursor in r) {
                        val i = iCursor.value
                        if (!split.get(i)) {
                            split.set(i)
                            val j = block[i]
                            splitblock[j].add(i)
                            if (!refine2.get(j)) {
                                refine2.set(j)
                                refine.set(j)
                            }
                        }
                    }
                }
                m = m.next
            }
            var j = refine.nextSetBit(0)
            while (j != -1) {
                val sb = splitblock[j]
                if (sb.size() < partition[j].size()) {
                    val b1 = partition[j]
                    val b2 = partition[k]
                    for (iCursor in sb) {
                        val s = iCursor.value
                        b1.remove(s)
                        b2.add(s)
                        block[s] = k
                        for (c in 0 until sigmaLen) {
                            val sn = active2[s][c]
                            if (sn != null && sn.sl === active[j][c]) {
                                sn.remove()
                                var stateList = active[k][c]
                                if (stateList === StateList.EMPTY) {
                                    stateList = StateList()
                                    active[k][c] = stateList
                                }
                                active2[s][c] = stateList.add(s)
                            }
                        }
                    }
                    for (c in 0 until sigmaLen) {
                        val aj = active[j][c].size
                        val ak = active[k][c].size
                        val ofs = c * statesLen
                        if (!pending2.get(ofs + j) && aj > 0 && aj <= ak) {
                            pending2.set(ofs + j)
                            pending.addLast(IntPair(j, c))
                        } else {
                            pending2.set(ofs + k)
                            pending.addLast(IntPair(k, c))
                        }
                    }
                    k++
                }
                refine2.clear(j)
                for (iCursor in sb) {
                    val s = iCursor.value
                    split.clear(s)
                }
                sb.clear()
                j = refine.nextSetBit(j + 1)
            }
            refine.clear()
        }
        val result = Automaton()
        val t = Transition()
        val stateMap = IntArray(statesLen)
        val stateRep = IntArray(k)
        result.createState()
        for (n in 0 until k) {
            val isInitial = partition[n].contains(0)
            val newState = if (isInitial) 0 else result.createState()
            for (qCursor in partition[n]) {
                val q = qCursor!!.value
                stateMap[q] = newState
                result.setAccept(newState, a.isAccept(q))
                stateRep[newState] = q
            }
        }
        for (n in 0 until k) {
            val numTransitions = a.initTransition(stateRep[n], t)
            for (i in 0 until numTransitions) {
                a.getNextTransition(t)
                result.addTransition(n, stateMap[t.dest], t.min, t.max)
            }
        }
        result.finishState()
        return Operations.removeDeadStates(result)
    }

    data class IntPair(val n1: Int, val n2: Int)

    private class StateList {
        var size: Int = 0
        var first: StateListNode? = null
        var last: StateListNode? = null
        fun add(q: Int): StateListNode {
            check(this !== EMPTY)
            return StateListNode(q, this)
        }
        companion object {
            val EMPTY = StateList()
        }
    }

    private class StateListNode(val q: Int, val sl: StateList) {
        var next: StateListNode? = null
        var prev: StateListNode? = null
        init {
            if (sl.size++ == 0) sl.first = this.also { sl.last = it } else {
                sl.last!!.next = this
                prev = sl.last
                sl.last = this
            }
        }
        fun remove() {
            sl.size--
            if (sl.first === this) sl.first = next else prev!!.next = next
            if (sl.last === this) sl.last = prev else next!!.prev = prev
        }
    }
}

