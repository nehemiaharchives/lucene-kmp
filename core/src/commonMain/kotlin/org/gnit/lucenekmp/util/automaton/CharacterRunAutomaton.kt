package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.jdkport.Character

/** Automaton representation for matching [String]s. */
class CharacterRunAutomaton(a: Automaton) : RunAutomaton(a, Character.MAX_CODE_POINT + 1) {
    /** Returns true if the given string is accepted by this automaton. */
    fun run(s: String): Boolean {
        var state = 0
        var i = 0
        val length = s.length
        while (i < length) {
            val cp = Character.codePointAt(s, i)
            state = step(state, cp)
            if (state == -1) return false
            i += Character.charCount(cp)
        }
        return accept.get(state)
    }

    /** Returns true if the given char array range is accepted by this automaton. */
    fun run(s: CharArray, offset: Int, length: Int): Boolean {
        var state = 0
        val end = offset + length
        var i = offset
        while (i < end) {
            val cp = Character.codePointAt(s, i, end)
            state = step(state, cp)
            if (state == -1) return false
            i += Character.charCount(cp)
        }
        return accept.get(state)
    }
}
