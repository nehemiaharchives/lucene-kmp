package org.gnit.lucenekmp.util


/**
 * Used for parsing Version strings so we don't have to use overkill String.split nor
 * StringTokenizer (which silently skips empty tokens).
 */
internal class StrictStringTokenizer(private val s: String, private val delimiter: Char) {
    fun nextToken(): String {
        check(pos >= 0) { "no more tokens" }

        val pos1 = s.indexOf(delimiter, pos)
        val s1: String
        if (pos1 >= 0) {
            s1 = s.substring(pos, pos1)
            pos = pos1 + 1
        } else {
            s1 = s.substring(pos)
            pos = -1
        }

        return s1
    }

    fun hasMoreTokens(): Boolean {
        return pos >= 0
    }

    private var pos = 0
}
