package org.gnit.lucenekmp.analysis.gu

/**
 * Normalizer for Gujarati.
 *
 * Applies light Gujarati-specific normalization:
 * - Replace Gujarati danda characters with generic Indic danda.
 * - Replace ':' with visarga if it follows a Gujarati script character.
 */
internal class GujaratiNormalizer {
    /**
     * Normalize an input buffer of Gujarati text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        if (len == 0) return 0
        var outLen = 0
        var i = 0
        var prevWasGujarati = false
        while (i < len) {
            val ch = s[i]
            val normalized = when {
                ch == '\u0AE4' -> '\u0964' // Gujarati danda -> generic danda
                ch == '\u0AE5' -> '\u0965' // Gujarati double danda -> generic double danda
                ch == ':' && prevWasGujarati -> '\u0A83' // visarga
                else -> ch
            }
            s[outLen] = normalized
            outLen += 1
            prevWasGujarati = isGujaratiChar(ch)
            i += 1
        }
        return outLen
    }

    private fun isGujaratiChar(ch: Char): Boolean {
        return ch in '\u0A80'..'\u0AFF'
    }
}
