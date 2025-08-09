package org.gnit.lucenekmp.util.automaton

// following comment is copy of java lucene's comment:
// The following code was generated with the moman/finenight pkg
// This package is available under the MIT License, see NOTICE.txt
// for more details.
// This source file is auto-generated, Please do not modify it directly.
// You should modify the gradle/generation/moman/createAutomata.py instead.

// this kotlin code is ported from java lucene using Intellij's automatic java-to-kotlin conversion tool

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.automaton.LevenshteinAutomata.ParametricDescription

/**
 * Parametric description for generating a Levenshtein automaton of degree 1, with transpositions as
 * primitive edits. The comment in Lev1ParametricDescription may be helpful for you to understand
 * this class.
 *
 * @see Lev1ParametricDescription
 */
internal class Lev1TParametricDescription  // state map
//   0 -> [(0, 0)]
//   1 -> [(0, 1)]
//   2 -> [(0, 1), (1, 1)]
//   3 -> [(0, 1), (1, 1), (2, 1)]
//   4 -> [(0, 1), (2, 1)]
//   5 -> [t(0, 1), (0, 1), (1, 1), (2, 1)]
    (w: Int) : ParametricDescription(w, 1, intArrayOf(0, 1, 0, -1, -1, -1)) {
    override fun transition(absState: Int, position: Int, vector: Int): Int {
        // null absState should never be passed in
        assert(absState != -1)

        // decode absState -> state, offset
        var state: Int = absState / (w + 1)
        var offset: Int = absState % (w + 1)
        assert(offset >= 0)

        if (position == w) {
            if (state < 2) {
                val loc = vector * 2 + state
                offset += unpack(offsetIncrs0, loc, 1)
                state = unpack(toStates0, loc, 2) - 1
            }
        } else if (position == w - 1) {
            if (state < 3) {
                val loc = vector * 3 + state
                offset += unpack(offsetIncrs1, loc, 1)
                state = unpack(toStates1, loc, 2) - 1
            }
        } else if (position == w - 2) {
            if (state < 6) {
                val loc = vector * 6 + state
                offset += unpack(offsetIncrs2, loc, 2)
                state = unpack(toStates2, loc, 3) - 1
            }
        } else {
            if (state < 6) {
                val loc = vector * 6 + state
                offset += unpack(offsetIncrs3, loc, 2)
                state = unpack(toStates3, loc, 3) - 1
            }
        }

        if (state == -1) {
            // null state
            return -1
        } else {
            // translate back to abs
            return state * (w + 1) + offset
        }
    }

    companion object {
        // 1 vectors; 2 states per vector; array length = 2
        private val toStates0 = longArrayOf(0x2L)
        private val offsetIncrs0 = longArrayOf(0x0L)

        // 2 vectors; 3 states per vector; array length = 6
        private val toStates1 = longArrayOf(0xa43L)
        private val offsetIncrs1 = longArrayOf(0x38L)

        // 4 vectors; 6 states per vector; array length = 24
        private val toStates2 = longArrayOf(-0x4ba5b6ebede7fffdL, 0x69L)
        private val offsetIncrs2 = longArrayOf(0x5555558a0000L)

        // 8 vectors; 6 states per vector; array length = 48
        private val toStates3 = longArrayOf(-0x5e6fb79b6ff3fffdL, 0x5a6d196a45a49169L, 0x9634L)
        private val offsetIncrs3 = longArrayOf(0x5555ba08a0fc0000L, 0x55555555L)
    }
}
