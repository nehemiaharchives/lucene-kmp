package org.gnit.lucenekmp.analysis.ar

import org.gnit.lucenekmp.analysis.util.StemmerUtil.deleteN

/**
 * Stemmer for Arabic.
 *
 * Stemming is done in-place for efficiency, operating on a termbuffer.
 *
 * Stemming is defined as:
 * - Removal of attached definite article, conjunction, and prepositions.
 * - Stemming of common suffixes.
 */
internal class ArabicStemmer {
    /**
     * Stem an input buffer of Arabic text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun stem(s: CharArray, len: Int): Int {
        var len = len
        len = stemPrefix(s, len)
        len = stemSuffix(s, len)
        return len
    }

    /**
     * Stem a prefix off an Arabic word.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return new length of input buffer after stemming.
     */
    fun stemPrefix(s: CharArray, len: Int): Int {
        for (i in prefixes.indices) {
            if (startsWithCheckLength(s, len, prefixes[i])) {
                return deleteN(s, 0, len, prefixes[i].size)
            }
        }
        return len
    }

    /**
     * Stem suffix(es) off an Arabic word.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return new length of input buffer after stemming
     */
    fun stemSuffix(s: CharArray, len: Int): Int {
        var len = len
        for (i in suffixes.indices) {
            if (endsWithCheckLength(s, len, suffixes[i])) {
                len = deleteN(s, len - suffixes[i].size, len, suffixes[i].size)
            }
        }
        return len
    }

    /**
     * Returns true if the prefix matches and can be stemmed.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @param prefix prefix to check
     * @return true if the prefix matches and can be stemmed
     */
    fun startsWithCheckLength(s: CharArray, len: Int, prefix: CharArray): Boolean {
        if (prefix.size == 1 && len < 4) { // wa- prefix requires at least 3 characters
            return false
        }
        if (len < prefix.size + 2) { // other prefixes require only 2.
            return false
        }
        for (i in prefix.indices) {
            if (s[i] != prefix[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Returns true if the suffix matches and can be stemmed.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @param suffix suffix to check
     * @return true if the suffix matches and can be stemmed
     */
    fun endsWithCheckLength(s: CharArray, len: Int, suffix: CharArray): Boolean {
        if (len < suffix.size + 2) { // all suffixes require at least 2 characters after stemming
            return false
        }
        for (i in suffix.indices) {
            if (s[len - suffix.size + i] != suffix[i]) {
                return false
            }
        }
        return true
    }

    private companion object {
        const val ALEF: Char = '\u0627'
        const val BEH: Char = '\u0628'
        const val TEH_MARBUTA: Char = '\u0629'
        const val TEH: Char = '\u062A'
        const val FEH: Char = '\u0641'
        const val KAF: Char = '\u0643'
        const val LAM: Char = '\u0644'
        const val NOON: Char = '\u0646'
        const val HEH: Char = '\u0647'
        const val WAW: Char = '\u0648'
        const val YEH: Char = '\u064A'

        val prefixes: Array<CharArray> = arrayOf(
            charArrayOf(ALEF, LAM),
            charArrayOf(WAW, ALEF, LAM),
            charArrayOf(BEH, ALEF, LAM),
            charArrayOf(KAF, ALEF, LAM),
            charArrayOf(FEH, ALEF, LAM),
            charArrayOf(LAM, LAM),
            charArrayOf(WAW),
        )

        val suffixes: Array<CharArray> = arrayOf(
            charArrayOf(HEH, ALEF),
            charArrayOf(ALEF, NOON),
            charArrayOf(ALEF, TEH),
            charArrayOf(WAW, NOON),
            charArrayOf(YEH, NOON),
            charArrayOf(YEH, HEH),
            charArrayOf(YEH, TEH_MARBUTA),
            charArrayOf(HEH),
            charArrayOf(TEH_MARBUTA),
            charArrayOf(YEH),
        )
    }
}

