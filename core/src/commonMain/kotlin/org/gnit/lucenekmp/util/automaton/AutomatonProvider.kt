package org.gnit.lucenekmp.util.automaton

import okio.IOException

/**
 * Automaton provider for `RegExp.` [RegExp.toAutomaton]
 *
 * @lucene.experimental
 */
interface AutomatonProvider {
    /**
     * Returns automaton of the given name.
     *
     * @param name automaton name
     * @return automaton
     * @throws IOException if errors occur
     */
    @Throws(IOException::class)
    fun getAutomaton(name: String): Automaton
}
