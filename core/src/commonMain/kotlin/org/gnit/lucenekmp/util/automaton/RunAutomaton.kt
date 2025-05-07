package org.gnit.lucenekmp.util.automaton

import kotlin.math.min
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.RamUsageEstimator


/**
 * Finite-state automaton with fast run operation. The initial state is always 0.
 *
 * @lucene.experimental
 */
abstract class RunAutomaton protected constructor(a: Automaton, alphabetSize: Int) : Accountable {
    val automaton: Automaton
    val alphabetSize: Int

    /** Returns number of states in automaton.  */
    val size: Int
    val accept: FixedBitSet
    val transitions: IntArray // delta(state,c) = transitions[state*points.length +

    // getCharClass(c)]
    val points: IntArray // char interval start points
    val classmap: IntArray // map from char number to class

    /**
     * Constructs a new `RunAutomaton` from a deterministic `Automaton`.
     *
     * @param a an automaton
     * @throws IllegalArgumentException if the automaton is not deterministic
     */
    init {
        this.alphabetSize = alphabetSize
        require(a.isDeterministic) { "Automaton must be deterministic" }
        this.automaton = a
        points = a.getStartPoints()
        size = kotlin.math.max(1, a.numStates)
        accept = FixedBitSet(size)
        transitions = IntArray(size * points.size)
        /*java.util.Arrays.fill(transitions, -1)*/
        transitions.fill(-1)
        val transition = Transition()
        for (n in 0..<size) {
            if (a.isAccept(n)) {
                accept.set(n)
            }
            transition.source = n
            transition.transitionUpto = -1
            for (c in points.indices) {
                val dest = a.next(transition, points[c])
                require(dest == -1 || dest < size)
                transitions[n * points.size + c] = dest
            }
        }

        /*
     * Set alphabet table for optimal run performance.
     */
        classmap = IntArray(min(256, alphabetSize))
        var i = 0
        for (j in classmap.indices) {
            if (i + 1 < points.size && j == points[i + 1]) {
                i++
            }
            classmap[j] = i
        }
    }

    /** Returns a string representation of this automaton.  */
    override fun toString(): String {
        val b = StringBuilder()
        b.append("initial state: 0\n")
        for (i in 0..<size) {
            b.append("state ").append(i)
            if (accept.get(i)) b.append(" [accept]:\n")
            else b.append(" [reject]:\n")
            for (j in points.indices) {
                val k = transitions[i * points.size + j]
                if (k != -1) {
                    val min = points[j]
                    val max: Int
                    if (j + 1 < points.size) max = (points[j + 1] - 1)
                    else max = alphabetSize
                    b.append(" ")
                    Automaton.appendCharString(min, b)
                    if (min != max) {
                        b.append("-")
                        Automaton.appendCharString(max, b)
                    }
                    b.append(" -> ").append(k).append("\n")
                }
            }
        }
        return b.toString()
    }

    /**
     * Returns acceptance status for given state.
     *
     * @param state the state
     * @return whether the state is accepted
     */
    fun isAccept(state: Int): Boolean {
        return accept.get(state)
    }

    val charIntervals: IntArray?
        /**
         * Returns array of codepoint class interval start points. The array should not be modified by the
         * caller.
         */
        get() = points.copyOf()

    /** Gets character class of given codepoint  */
    fun getCharClass(c: Int): Int {
        // binary search

        var a = 0
        var b = points.size
        while (b - a > 1) {
            val d = (a + b) ushr 1
            if (points[d] > c) b = d
            else if (points[d] < c) a = d
            else return d
        }
        return a
    }

    /**
     * Returns the state obtained by reading the given char from the given state. Returns -1 if not
     * obtaining any such state. (If the original `Automaton` had no dead states, -1 is
     * returned here if and only if a dead state is entered in an equivalent automaton with a total
     * transition function.)
     */
    fun step(state: Int, c: Int): Int {
        require(c < alphabetSize)
        if (c >= classmap.size) {
            return transitions[state * points.size + getCharClass(c)]
        } else {
            return transitions[state * points.size + classmap[c]]
        }
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + alphabetSize
        result = prime * result + points.size
        result = prime * result + size
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (this::class != obj::class) return false
        val other = obj as RunAutomaton
        if (alphabetSize != other.alphabetSize) return false
        if (size != other.size) return false
        if (!points.contentEquals(other.points)) return false
        if (!accept.equals(other.accept)) return false
        if (!transitions.contentEquals(other.transitions)) return false
        return true
    }

    public override fun ramBytesUsed(): Long {
        return (BASE_RAM_BYTES
                + accept.ramBytesUsed()
                + RamUsageEstimator.sizeOfObject(automaton)
                + RamUsageEstimator.sizeOfObject(classmap)
                + RamUsageEstimator.sizeOfObject(points)
                + RamUsageEstimator.sizeOfObject(transitions))
    }

    companion object {
        private val BASE_RAM_BYTES: Long = RamUsageEstimator.shallowSizeOfInstance(RunAutomaton::class)
    }
}
