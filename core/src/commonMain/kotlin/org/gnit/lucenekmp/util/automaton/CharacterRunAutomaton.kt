package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.jdkport.Character

/** Automaton representation for matching char[]. */
class CharacterRunAutomaton(a: Automaton) : RunAutomaton(a, Character.MAX_CODE_POINT + 1) {
    /** Returns true if the given string is accepted by this automaton. */
    fun run(s: String): Boolean {
        var p = 0
        var i = 0
        val l = s.length
        while (i < l) {
            val cp = Character.codePointAt(s, i)
            p = step(p, cp)
            if (p == -1) return false
            i += Character.charCount(cp)
        }
        return accept.get(p)
    }

    /** Returns true if the given char array is accepted by this automaton. */
    fun run(s: CharArray, offset: Int, length: Int): Boolean {
        var p = 0
        val end = offset + length
        var i = offset
        while (i < end) {
            val cp = Character.codePointAt(s, i, end)
            p = step(p, cp)
            if (p == -1) return false
            i += Character.charCount(cp)
        }
        return accept.get(p)
    }
}