package org.gnit.lucenekmp.util.automaton

/** A runnable automaton accepting byte array as input  */
interface ByteRunnable {
    /**
     * Returns the state obtained by reading the given char from the given state. Returns -1 if not
     * obtaining any such state.
     *
     * @param state the last state
     * @param c the input codepoint
     * @return the next state, -1 if no such transaction
     */
    fun step(state: Int, c: Int): Int

    /**
     * Returns acceptance status for given state.
     *
     * @param state the state
     * @return whether the state is accepted
     */
    fun isAccept(state: Int): Boolean

    /**
     * Returns number of states this automaton has, note this may not be an accurate number in case of
     * NFA
     *
     * @return number of states
     */
    val size: Int

    /** Returns true if the given byte array is accepted by this automaton  */
    fun run(s: ByteArray, offset: Int, length: Int): Boolean {
        var p = 0
        val l = offset + length
        for (i in offset..<l) {
            p = step(p, s[i].toInt() and 0xFF)
            if (p == -1) return false
        }
        return isAccept(p)
    }
}
