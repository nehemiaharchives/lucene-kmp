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

/*
   Parametric transitions for LEV1.
   ┏━━━━━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┓
   ┃ char vector ┃ State 0 ┃ State 1 ┃ State 2 ┃ State 3 ┃ State 4 ┃
   ┡━━━━━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━┩
   │ (0,0)       │ (2, 0)  │ (-1, 0) │ (-1, 0) │ (-1, 0) │ (-1, 0) │
   │ (0,1)       │ (3, 0)  │ (-1, 0) │ (1, 2)  │ (1, 2)  │ (-1, 0) │
   │ (1,0)       │ (0, 1)  │ (1, 1)  │ (1, 1)  │ (1, 1)  │ (1, 1)  │
   │ (1,1)       │ (0, 1)  │ (1, 1)  │ (2, 1)  │ (2, 1)  │ (1, 1)  │
   └─────────────┴─────────┴─────────┴─────────┴─────────┴─────────┘
   char vector is the characteristic vectors in the paper.
   entry (i,j) in the table means next transitions state is i, next offset is j + currentOffset if we meet the according char vector.
   When i = -1,it means an empty state.
   We store this table in toState and offsetIncr.
   toState = [ i+1  | for entry in entries].
   offsetIncrs = [j | for entry in entries].
*/
/** Parametric description for generating a Levenshtein automaton of degree 1.  */
internal class Lev1ParametricDescription  // state map
//   0 -> [(0, 0)]
//   1 -> [(0, 1)]
//   2 -> [(0, 1), (1, 1)]
//   3 -> [(0, 1), (1, 1), (2, 1)]
//   4 -> [(0, 1), (2, 1)]
    (w: Int) :
    ParametricDescription(w, 1, intArrayOf(0, 1, 0, -1, -1)) {
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
            if (state < 5) {
                val loc = vector * 5 + state
                offset += unpack(offsetIncrs2, loc, 2)
                state = unpack(toStates2, loc, 3) - 1
            }
        } else {
            if (state < 5) {
                val loc = vector * 5 + state
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
        /*
   *  1 vectors; 2 states per vector; array length = 2
   *   Parametric transitions for LEV1  (position = w)
   *  ┏━━━━━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┓
   *  ┃ char vector ┃ State 0 ┃ State 1 ┃
   *  ┡━━━━━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━┩
   *  │ ()          │ (1, 0)  │ (-1, 0) │
   *  └─────────────┴─────────┴─────────┘
   */
        private val toStates0 = longArrayOf(0x2L)
        private val offsetIncrs0 = longArrayOf(0x0L)

        /*
   *   2 vectors; 3 states per vector; array length = 6
   *   Parametric transitions for LEV1 (position = w-1)
   *  ┏━━━━━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┓
   *  ┃ char vector ┃ State 0 ┃ State 1 ┃ State 2 ┃
   *  ┡━━━━━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━┩
   *  │ (0)         │ (2, 0)  │ (-1, 0) │ (-1, 0) │
   *  │ (1)         │ (0, 1)  │ (1, 1)  │ (1, 1)  │
   *  └─────────────┴─────────┴─────────┴─────────┘
   */
        private val toStates1 = longArrayOf(0xa43L)
        private val offsetIncrs1 = longArrayOf(0x38L)

        /*
   *   4 vectors; 5 states per vector; array length = 20
   *   Parametric transitions for LEV1 ( position == w-2 )
   *  ┏━━━━━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┓
   *  ┃ char vector ┃ State 0 ┃ State 1 ┃ State 2 ┃ State 3 ┃ State 4 ┃
   *  ┡━━━━━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━┩
   *  │ (0,0)       │ (2, 0)  │ (-1, 0) │ (-1, 0) │ (-1, 0) │ (-1, 0) │
   *  │ (0,1)       │ (3, 0)  │ (-1, 0) │ (1, 2)  │ (1, 2)  │ (-1, 0) │
   *  │ (1,0)       │ (0, 1)  │ (1, 1)  │ (1, 1)  │ (1, 1)  │ (1, 1)  │
   *  │ (1,1)       │ (0, 1)  │ (1, 1)  │ (2, 1)  │ (2, 1)  │ (1, 1)  │
   *  └─────────────┴─────────┴─────────┴─────────┴─────────┴─────────┘
   */
        private val toStates2 = longArrayOf(0x4da292442420003L)
        private val offsetIncrs2 = longArrayOf(0x5555528000L)

        /*
   *   8 vectors; 5 states per vector; array length = 40
   *   Parametric transitions for LEV1 (0 <= position <= w-3 )
   *  ┏━━━━━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┳━━━━━━━━━┓
   *  ┃ char vector ┃ State 0 ┃ State 1 ┃ State 2 ┃ State 3 ┃ State 4 ┃
   *  ┡━━━━━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━╇━━━━━━━━━┩
   *  │ (0,0,0)     │ (2, 0)  │ (-1, 0) │ (-1, 0) │ (-1, 0) │ (-1, 0) │
   *  │ (0,0,1)     │ (2, 0)  │ (-1, 0) │ (-1, 0) │ (1, 3)  │ (1, 3)  │
   *  │ (0,1,0)     │ (3, 0)  │ (-1, 0) │ (1, 2)  │ (1, 2)  │ (-1, 0) │
   *  │ (0,1,1)     │ (3, 0)  │ (-1, 0) │ (1, 2)  │ (2, 2)  │ (1, 3)  │
   *  │ (1,0,0)     │ (0, 1)  │ (1, 1)  │ (1, 1)  │ (1, 1)  │ (1, 1)  │
   *  │ (1,0,1)     │ (0, 1)  │ (1, 1)  │ (1, 1)  │ (4, 1)  │ (4, 1)  │
   *  │ (1,1,0)     │ (0, 1)  │ (1, 1)  │ (2, 1)  │ (2, 1)  │ (1, 1)  │
   *  │ (1,1,1)     │ (0, 1)  │ (1, 1)  │ (2, 1)  │ (3, 1)  │ (4, 1)  │
   *  └─────────────┴─────────┴─────────┴─────────┴─────────┴─────────┘
   */
        private val toStates3 = longArrayOf(0x14d0812112018003L, 0xb1a29b46d48a49L)
        private val offsetIncrs3 = longArrayOf(0x555555e80a0f0000L, 0x5555L)
    }
}
