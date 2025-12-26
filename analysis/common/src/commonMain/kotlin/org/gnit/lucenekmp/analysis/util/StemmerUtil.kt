package org.gnit.lucenekmp.analysis.util

import org.gnit.lucenekmp.jdkport.System

/**
 * Some commonly-used stemming functions.
 */
object StemmerUtil {
    /**
     * Returns true if the character array starts with the prefix.
     */
    fun startsWith(s: CharArray, len: Int, prefix: String): Boolean {
        val prefixLen = prefix.length
        if (prefixLen > len) return false
        for (i in 0 until prefixLen) {
            if (s[i] != prefix[i]) return false
        }
        return true
    }

    /**
     * Returns true if the character array ends with the suffix.
     */
    fun endsWith(s: CharArray, len: Int, suffix: String): Boolean {
        val suffixLen = suffix.length
        if (suffixLen > len) return false
        for (i in suffixLen - 1 downTo 0) {
            if (s[len - (suffixLen - i)] != suffix[i]) return false
        }
        return true
    }

    /**
     * Returns true if the character array ends with the suffix.
     */
    fun endsWith(s: CharArray, len: Int, suffix: CharArray): Boolean {
        val suffixLen = suffix.size
        if (suffixLen > len) return false
        for (i in suffixLen - 1 downTo 0) {
            if (s[len - (suffixLen - i)] != suffix[i]) return false
        }
        return true
    }

    /**
     * Delete a character in-place.
     */
    fun delete(s: CharArray, pos: Int, len: Int): Int {
        if (pos < len - 1) {
            System.arraycopy(s, pos + 1, s, pos, len - pos - 1)
        }
        return len - 1
    }

    /**
     * Delete n characters in-place.
     */
    fun deleteN(s: CharArray, pos: Int, len: Int, nChars: Int): Int {
        if (pos + nChars < len) {
            System.arraycopy(s, pos + nChars, s, pos, len - pos - nChars)
        }
        return len - nChars
    }
}
