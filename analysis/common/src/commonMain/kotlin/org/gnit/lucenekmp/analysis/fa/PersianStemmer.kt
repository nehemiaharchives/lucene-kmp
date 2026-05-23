package org.gnit.lucenekmp.analysis.fa

import org.gnit.lucenekmp.analysis.util.StemmerUtil.deleteN

/**
 * Stemmer for Persian.
 *
 * Stemming is done in-place for efficiency, operating on a termbuffer.
 *
 * Stemming is defined as:
 * - Removal of attached definite article, conjunction, and prepositions.
 * - Stemming of common suffixes.
 */
internal class PersianStemmer {
    /**
     * Stem an input buffer of Persian text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun stem(s: CharArray, len: Int): Int {
        var len = len
        len = stemSuffix(s, len)
        return len
    }

    /**
     * Stem suffix(es) off a Persian word.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return new length of input buffer after stemming
     */
    private fun stemSuffix(s: CharArray, len: Int): Int {
        var len = len
        for (suffix in suffixes) {
            if (endsWithCheckLength(s, len, suffix)) {
                len = deleteN(s, len - suffix.size, len, suffix.size)
            }
        }
        return len
    }

    /**
     * Returns true if the suffix matches and can be stemmed.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @param suffix suffix to check
     * @return true if the suffix matches and can be stemmed
     */
    private fun endsWithCheckLength(s: CharArray, len: Int, suffix: CharArray): Boolean {
        if (len < suffix.size + 2) { // all suffixes require at least 2 characters after stemming
            return false
        }

        var i = 0
        val start = len - suffix.size
        while (i < suffix.size) {
            if (s[start + i] != suffix[i]) {
                return false
            }
            i++
        }
        return true
    }

    private companion object {
        const val ALEF: Char = '\u0627'
        const val HEH: Char = '\u0647'
        const val TEH: Char = '\u062A'
        const val REH: Char = '\u0631'
        const val NOON: Char = '\u0646'
        const val YEH: Char = '\u064A'
        const val ZWNJ: Char = '\u200c' // ZERO WIDTH NON-JOINER character

        val suffixes: Array<CharArray> = arrayOf(
            charArrayOf(ALEF, TEH),
            charArrayOf(ALEF, NOON),
            charArrayOf(TEH, REH, YEH, NOON),
            charArrayOf(TEH, REH),
            charArrayOf(YEH, YEH),
            charArrayOf(YEH),
            charArrayOf(HEH, ALEF),
            charArrayOf(ZWNJ),
        )
    }
}

