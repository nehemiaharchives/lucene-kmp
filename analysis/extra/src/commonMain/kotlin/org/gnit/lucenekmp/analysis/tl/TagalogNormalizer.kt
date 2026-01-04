package org.gnit.lucenekmp.analysis.tl

/**
 * Normalizer for Tagalog.
 *
 * Applies light normalization for common punctuation variants.
 */
internal class TagalogNormalizer {
    /**
     * Normalize an input buffer of Tagalog text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        if (len == 0) return 0
        var outLen = 0
        var i = 0
        while (i < len) {
            val ch = s[i]
            val normalized = when (ch) {
                '’', '‘', '‛', 'ʹ', 'ʼ' -> '\''
                else -> ch
            }
            s[outLen] = normalized
            outLen += 1
            i += 1
        }
        return outLen
    }
}
