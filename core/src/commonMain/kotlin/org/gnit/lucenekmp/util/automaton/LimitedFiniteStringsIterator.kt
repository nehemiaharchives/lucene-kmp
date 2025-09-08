package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.jdkport.BitSet

/**
 * [FiniteStringsIterator] which limits the number of iterated accepted strings. If more than
 * `limit` strings are accepted, the first `limit` strings found are returned.
 *
 *
 * If the [Automaton] has cycles then this iterator may throw an `IllegalArgumentException`, but this is not guaranteed!
 *
 *
 * Be aware that the iteration order is implementation dependent and may change across releases.
 *
 * @lucene.experimental
 */
class LimitedFiniteStringsIterator(a: Automaton, limit: Int) :
    FiniteStringsIterator(a) {
    /** Maximum number of finite strings to create.  */
    private val limit: Int

    /** Number of generated finite strings.  */
    private var count = 0

    /**
     * Constructor.
     *
     * @param a Automaton to create finite string from.
     * @param limit Maximum number of finite strings to create, or -1 for infinite.
     */
    init {
        require(!(limit != -1 && limit <= 0)) { "limit must be -1 (which means no limit), or > 0; got: $limit" }

        // Fail fast if the automaton has a cycle reachable from the initial state.
        // FiniteStringsIterator would eventually throw when hitting the cycle; we detect it upfront
        // to avoid rare long iterations on cyclic automata.
        if (hasReachableCycle(a)) {
            throw IllegalArgumentException("automaton has cycles")
        }

        this.limit = if (limit > 0) limit else Int.Companion.MAX_VALUE
    }

    override fun next(): IntsRef? {
        if (count >= limit) {
            // Abort on limit.
            return null
        }

        val result: IntsRef? = super.next()
        if (result != null) {
            count++
        }

        return result
    }

    /** Number of iterated finite strings.  */
    fun size(): Int {
        return count
    }

    /**
     * Detects whether there is any cycle reachable from the initial state (state 0).
     * This matches the runtime behavior of FiniteStringsIterator which throws on cycles
     * encountered along a path during enumeration.
     */
    private fun hasReachableCycle(a: Automaton): Boolean {
        if (a.numStates == 0) return false
        val onPath = BitSet(a.numStates)
        val visited = BitSet(a.numStates)
        // stack of pairs (state, nextTransitionIndex)
        data class Frame(var state: Int, var nextIdx: Int, var num: Int)
        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(0, 0, a.getNumTransitions(0)))
        onPath.set(0)
        val t = Transition()
        while (stack.isNotEmpty()) {
            val f = stack.last()
            if (f.nextIdx < f.num) {
                a.getTransition(f.state, f.nextIdx, t)
                f.nextIdx++
                val dest = t.dest
                if (onPath.get(dest)) {
                    return true
                }
                if (!visited.get(dest)) {
                    onPath.set(dest)
                    stack.addLast(Frame(dest, 0, a.getNumTransitions(dest)))
                }
            } else {
                visited.set(f.state)
                onPath.clear(f.state)
                stack.removeLast()
            }
        }
        return false
    }
}