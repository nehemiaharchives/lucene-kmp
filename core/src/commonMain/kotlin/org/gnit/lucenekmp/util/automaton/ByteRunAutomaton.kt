package org.gnit.lucenekmp.util.automaton

/** Automaton representation for matching UTF-8 byte[].  */
class ByteRunAutomaton
/**
 * expert: if isBinary is true, the input is already byte-based
 *
 * @throws IllegalArgumentException if the automaton is not deterministic
 */
    (a: Automaton, isBinary: Boolean) : RunAutomaton(if (isBinary) a else convert(a), 256), ByteRunnable {
    /**
     * Converts incoming automaton to byte-based (UTF32ToUTF8) first
     *
     * @throws IllegalArgumentException if the automaton is not deterministic
     */
    constructor(a: Automaton) : this(a, false)

    companion object {
        fun convert(a: Automaton): Automaton {
            require(a.isDeterministic) { "Automaton must be deterministic" }
            // we checked the input is a DFA, according to mike this determinization is contained :)
            return Operations.determinize(UTF32ToUTF8().convert(a), Int.Companion.MAX_VALUE)
        }
    }
}
