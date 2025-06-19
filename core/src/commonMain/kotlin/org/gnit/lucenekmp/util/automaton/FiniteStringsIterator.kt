package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder

/**
 * Iterates all accepted strings.
 *
 * If the [Automaton] has cycles then this iterator may throw an [IllegalArgumentException], but this
 * is not guaranteed!
 *
 * Be aware that the iteration order is implementation dependent and may change across releases.
 *
 * If the automaton is not determinized then it's possible this iterator will return duplicates.
 *
 * @lucene.experimental
 */
open class FiniteStringsIterator(
    /** Automaton to create finite string from. */
    private val a: Automaton,
    startState: Int,
    /** The state where each path should stop or -1 if only accepted states should be final. */
    private val endState: Int
) {

    /** Tracks which states are in the current path, for cycle detection. */
    private val pathStates: BitSet

    /** Builder for current finite string. */
    private val string: IntsRefBuilder

    /** Stack to hold our current state in the recursion/iteration. */
    private var nodes: Array<PathNode>

    /** Emit empty string?. */
    private var emitEmptyString: Boolean

    /**
     * Constructor.
     *
     * @param a Automaton to create finite string from.
     */
    constructor(a: Automaton) : this(a, 0, -1)

    init {
        nodes = Array(16) { PathNode() }
        string = IntsRefBuilder()
        pathStates = BitSet(a.numStates)
        string.setLength(0)
        emitEmptyString = a.isAccept(0)

        // Start iteration with node startState.
        if (a.numStates > startState && a.getNumTransitions(startState) > 0) {
            pathStates.set(startState)
            nodes[0].resetState(a, startState)
            string.append(startState)
        }
    }

    /**
     * Generate next finite string. The return value is just valid until the next call of this method!
     *
     * @return Finite string or null, if no more finite strings are available.
     */
    open fun next(): IntsRef? {
        // Special case the empty string, as usual:
        if (emitEmptyString) {
            emitEmptyString = false
            return EMPTY
        }

        var depth = string.length()
        while (depth > 0) {
            val node = nodes[depth - 1]

            // Get next label leaving the current node:
            val label = node.nextLabel(a)
            if (label != -1) {
                string.setIntAt(depth - 1, label)
                val to = node.to
                if (a.getNumTransitions(to) != 0 && to != endState) {
                    // Now recurse: the destination of this transition has outgoing transitions:
                    if (pathStates[to]) {
                        throw IllegalArgumentException("automaton has cycles")
                    }
                    pathStates.set(to)

                    // Push node onto stack:
                    growStack(depth)
                    nodes[depth].resetState(a, to)
                    depth++
                    string.setLength(depth)
                    string.grow(depth)
                } else if (endState == to || a.isAccept(to)) {
                    // This transition leads to an accept state, so we save the current string:
                    return string.get()
                }
            } else {
                // No more transitions leaving this state, pop/return back to previous state:
                val state = node.state
                check(pathStates[state])
                pathStates.clear(state)
                depth--
                string.setLength(depth)
                if (a.isAccept(state)) {
                    // This transition leads to an accept state, so we save the current string:
                    return string.get()
                }
            }
        }

        // Finished iteration.
        return null
    }

    /** Grow path stack, if required. */
    private fun growStack(depth: Int) {
        if (nodes.size == depth) {
            val newSize = ArrayUtil.oversize(nodes.size + 1, 4)
            val newNodes = Array(newSize) { PathNode() }
            nodes.copyInto(destination = newNodes, endIndex = nodes.size)
            nodes = newNodes
        }
    }

    /** Nodes for path stack. */
    private class PathNode {
        /** Which state the path node ends on, whose transitions we are enumerating. */
        var state = 0

        /** Which state the current transition leads to. */
        var to = 0

        /** Which transition we are on. */
        var transition = 0

        /** Which label we are on, in the min-max range of the current Transition */
        var label = 0
        private val t = Transition()

        fun resetState(a: Automaton, state: Int) {
            check(a.getNumTransitions(state) != 0)
            this.state = state
            transition = 0
            a.getTransition(state, 0, t)
            label = t.min
            to = t.dest
        }

        /**
         * Returns next label of current transition, or advances to next transition and returns its
         * first label, if current one is exhausted. If there are no more transitions, returns -1.
         */
        fun nextLabel(a: Automaton): Int {
            if (label > t.max) {
                // We've exhaused the current transition's labels;
                // move to next transitions:
                transition++
                if (transition >= a.getNumTransitions(state)) {
                    // We're done iterating transitions leaving this state
                    label = -1
                    return -1
                }
                a.getTransition(state, transition, t)
                label = t.min
                to = t.dest
            }
            return label++
        }
    }

    companion object {
        /** Empty string. */
        private val EMPTY = IntsRef()
    }
}