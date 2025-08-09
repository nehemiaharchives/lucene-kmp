package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.codePointCount
import kotlin.math.min

/**
 * Class to construct DFAs that match a word within some edit distance.
 *
 *
 * Implements the algorithm described in: Schulz and Mihov: Fast String Correction with
 * Levenshtein Automata
 *
 * @lucene.experimental
 */
class LevenshteinAutomata(/* input word */val word: IntArray, /* the maximum symbol in the alphabet (e.g. 255 for UTF-8 or 10FFFF for UTF-32) */
                          val alphaMax: Int, withTranspositions: Boolean
) {
    /* the automata alphabet. */
    val alphabet: IntArray

    /* the ranges outside of alphabet */
    val rangeLower: IntArray
    val rangeUpper: IntArray
    var numRanges: Int = 0

    var descriptions: Array<ParametricDescription?>

    /**
     * Create a new LevenshteinAutomata for some input String. Optionally count transpositions as a
     * primitive edit.
     */
    constructor(input: String, withTranspositions: Boolean) : this(
        codePoints(input),
        Character.MAX_CODE_POINT,
        withTranspositions
    )

    /**
     * Expert: specify a custom maximum possible symbol (alphaMax); default is
     * Character.MAX_CODE_POINT.
     */
    init {
        // calculate the alphabet
        val set = IntHashSet()
        for (i in word.indices) {
            val v = word[i]
            require(v <= alphaMax) { "alphaMax exceeded by symbol $v in word" }
            set.add(v)
        }
        alphabet = set.toArray()
        Arrays.sort(alphabet)

        rangeLower = IntArray(alphabet.size + 2)
        rangeUpper = IntArray(alphabet.size + 2)
        // calculate the unicode range intervals that exclude the alphabet
        // these are the ranges for all unicode characters not in the alphabet
        var lower = 0
        for (i in alphabet.indices) {
            val higher = alphabet[i]
            if (higher > lower) {
                rangeLower[numRanges] = lower
                rangeUpper[numRanges] = higher - 1
                numRanges++
            }
            lower = higher + 1
        }
        /* add the final endpoint */
        if (lower <= alphaMax) {
            rangeLower[numRanges] = lower
            rangeUpper[numRanges] = alphaMax
            numRanges++
        }

        descriptions =
            arrayOf<ParametricDescription?>(
                null,  /* for n=0, we do not need to go through the trouble */
                if (withTranspositions)
                    Lev1TParametricDescription(word.size)
                else
                    Lev1ParametricDescription(word.size),
                if (withTranspositions)
                    Lev2TParametricDescription(word.size)
                else
                    Lev2ParametricDescription(word.size),
            )
    }

    /**
     * Compute a DFA that accepts all strings within an edit distance of `n`.
     *
     *
     * All automata have the following properties:
     *
     *
     *  * They are deterministic (DFA).
     *  * There are no transitions to dead states.
     *  * They are not minimal (some transitions could be combined).
     *
     */
    fun toAutomaton(n: Int): Automaton? {
        return toAutomaton(n, "")
    }

    /**
     * Compute a DFA that accepts all strings within an edit distance of `n`, matching the
     * specified exact prefix.
     *
     *
     * All automata have the following properties:
     *
     *
     *  * They are deterministic (DFA).
     *  * There are no transitions to dead states.
     *  * They are not minimal (some transitions could be combined).
     *
     */
    fun toAutomaton(n: Int, prefix: String): Automaton? {
        //checkNotNull(prefix)
        if (n == 0) {
            return Automata.makeString(
                prefix + UnicodeUtil.newString(
                    word,
                    0,
                    word.size
                )
            )
        }

        if (n >= descriptions.size) return null

        val range = 2 * n + 1
        val description = descriptions[n]!!
        // the number of states is based on the length of the word and n
        val numStates = description.size()
        val numTransitions = numStates * min(1 + 2 * n, alphabet.size)
        val prefixStates = if (prefix != null) prefix.codePointCount(0, prefix.length) else 0

        val a = Automaton(numStates + prefixStates, numTransitions)
        var lastState: Int
        if (prefix != null) {
            // Insert prefix
            lastState = a.createState()
            var i = 0
            var cp = 0
            while (i < prefix.length) {
                val state: Int = a.createState()
                cp = prefix.codePointAt(i)
                a.addTransition(lastState, state, cp, cp)
                lastState = state
                i += Character.charCount(cp)
            }
        } else {
            lastState = a.createState()
        }

        val stateOffset = lastState
        a.setAccept(lastState, description.isAccept(0))

        // create all states, and mark as accept states if appropriate
        for (i in 1..<numStates) {
            val state: Int = a.createState()
            a.setAccept(state, description.isAccept(i))
        }

        // TODO: this creates bogus states/transitions (states are final, have self loops, and can't be
        // reached from an init state)

        // create transitions from state to state
        for (k in 0..<numStates) {
            val xpos = description.getPosition(k)
            if (xpos < 0) continue
            val end = xpos + min(word.size - xpos, range)

            for (x in alphabet.indices) {
                val ch = alphabet[x]
                // get the characteristic vector at this position wrt ch
                val cvec = getVector(ch, xpos, end)
                val dest = description.transition(k, xpos, cvec)
                if (dest >= 0) {
                    a.addTransition(stateOffset + k, stateOffset + dest, ch)
                }
            }
            // add transitions for all other chars in unicode
            // by definition, their characteristic vectors are always 0,
            // because they do not exist in the input string.
            val dest = description.transition(k, xpos, 0) // by definition
            if (dest >= 0) {
                for (r in 0..<numRanges) {
                    a.addTransition(stateOffset + k, stateOffset + dest, rangeLower[r], rangeUpper[r])
                }
            }
        }

        a.finishState()
        val automaton: Automaton =
            Operations.removeDeadStates(a)
        assert(automaton.isDeterministic)
        return automaton
    }

    /**
     * Get the characteristic vector `X(x, V)` where V is `substring(pos, end)`
     */
    fun getVector(x: Int, pos: Int, end: Int): Int {
        var vector = 0
        for (i in pos..<end) {
            vector = vector shl 1
            if (word[i] == x) vector = vector or 1
        }
        return vector
    }

    /**
     * A ParametricDescription describes the structure of a Levenshtein DFA for some degree n.
     *
     *
     * There are four components of a parametric description, all parameterized on the length of
     * the word `w`:
     *
     *
     *  1. The number of states: [.size]
     *  1. The set of final states: [.isAccept]
     *  1. The transition function: [.transition]
     *  1. Minimal boundary function: [.getPosition]
     *
     */
    abstract class ParametricDescription(
        protected val w: Int,
        protected val n: Int,
        private val minErrors: IntArray
    ) {
        /** Return the number of states needed to compute a Levenshtein DFA  */
        fun size(): Int {
            return minErrors.size * (w + 1)
        }

        /**
         * Returns true if the `state` in any Levenshtein DFA is an accept state (final
         * state).
         */
        fun isAccept(absState: Int): Boolean {
            // decode absState -> state, offset
            val state = absState / (w + 1)
            val offset = absState % (w + 1)
            assert(offset >= 0)
            return w - offset + minErrors[state] <= n
        }

        /**
         * Returns the position in the input word for a given `state`. This is the minimal
         * boundary for the state.
         */
        fun getPosition(absState: Int): Int {
            return absState % (w + 1)
        }

        /**
         * Returns the state number for a transition from the given `state`, assuming `
         * position` and characteristic vector `vector`
         */
        abstract fun transition(state: Int, position: Int, vector: Int): Int

        protected fun unpack(data: LongArray, index: Int, bitsPerValue: Int): Int {
            val bitLoc = bitsPerValue * index.toLong()
            val dataLoc = (bitLoc shr 6).toInt()
            val bitStart = (bitLoc and 63L).toInt()
            // System.out.println("index=" + index + " dataLoc=" + dataLoc + " bitStart=" + bitStart + "
            // bitsPerV=" + bitsPerValue);
            if (bitStart + bitsPerValue <= 64) {
                // not split
                return ((data[dataLoc] shr bitStart) and MASKS[bitsPerValue - 1]).toInt()
            } else {
                // split
                val part = 64 - bitStart
                return (((data[dataLoc] shr bitStart) and MASKS[part - 1])
                        + ((data[1 + dataLoc] and MASKS[bitsPerValue - part - 1]) shl part)).toInt()
            }
        }

        companion object {
            private val MASKS = longArrayOf(
                0x1,
                0x3,
                0x7,
                0xf,
                0x1f,
                0x3f,
                0x7f,
                0xff,
                0x1ff,
                0x3ff,
                0x7ff,
                0xfff,
                0x1fff,
                0x3fff,
                0x7fff,
                0xffff,
                0x1ffff,
                0x3ffff,
                0x7ffff,
                0xfffff,
                0x1fffff,
                0x3fffff,
                0x7fffff,
                0xffffff,
                0x1ffffff,
                0x3ffffff,
                0x7ffffff,
                0xfffffff,
                0x1fffffff,
                0x3fffffff,
                0x7fffffffL,
                0xffffffffL,
                0x1ffffffffL,
                0x3ffffffffL,
                0x7ffffffffL,
                0xfffffffffL,
                0x1fffffffffL,
                0x3fffffffffL,
                0x7fffffffffL,
                0xffffffffffL,
                0x1ffffffffffL,
                0x3ffffffffffL,
                0x7ffffffffffL,
                0xfffffffffffL,
                0x1fffffffffffL,
                0x3fffffffffffL,
                0x7fffffffffffL,
                0xffffffffffffL,
                0x1ffffffffffffL,
                0x3ffffffffffffL,
                0x7ffffffffffffL,
                0xfffffffffffffL,
                0x1fffffffffffffL,
                0x3fffffffffffffL,
                0x7fffffffffffffL,
                0xffffffffffffffL,
                0x1ffffffffffffffL,
                0x3ffffffffffffffL,
                0x7ffffffffffffffL,
                0xfffffffffffffffL,
                0x1fffffffffffffffL,
                0x3fffffffffffffffL,
                0x7fffffffffffffffL
            )
        }
    }

    companion object {
        /**
         * Maximum edit distance this class can generate an automaton for.
         *
         * @lucene.internal
         */
        const val MAXIMUM_SUPPORTED_DISTANCE: Int = 2

        private fun codePoints(input: String): IntArray {
            val length: Int = input.codePointCount(0, input.length)
            val word = IntArray(length)
            var i = 0
            var j = 0
            var cp = 0
            while (i < input.length) {
                cp = input.codePointAt(i)
                word[j++] = cp
                i += Character.charCount(cp)
            }
            return word
        }
    }
}
