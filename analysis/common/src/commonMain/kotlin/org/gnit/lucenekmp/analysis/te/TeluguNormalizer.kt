package org.gnit.lucenekmp.analysis.te

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Telugu.
 *
 * Normalizes text to remove some differences in spelling variations.
 */
internal class TeluguNormalizer {
    /**
     * Normalize an input buffer of Telugu text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var length = len
        var i = 0
        while (i < length) {
            when (s[i]) {
                // candrabindu (ఀ and ఁ) -> bindu (ం)
                '\u0C00', '\u0C01' -> s[i] = '\u0C02'
                // delete visarga (ః)
                '\u0C03' -> {
                    length = delete(s, i, length)
                    i--
                }

                // zwj/zwnj -> delete
                '\u200D', '\u200C' -> {
                    length = delete(s, i, length)
                    i--
                }

                // long -> short vowels
                '\u0C14' -> s[i] = '\u0C13'
                '\u0C10' -> s[i] = '\u0C0F'
                '\u0C06' -> s[i] = '\u0C05'
                '\u0C08' -> s[i] = '\u0C07'
                '\u0C0A' -> s[i] = '\u0C09'

                // long -> short vowels matras
                '\u0C40' -> s[i] = '\u0C3F'
                '\u0C42' -> s[i] = '\u0C41'
                '\u0C47' -> s[i] = '\u0C46'
                '\u0C4B' -> s[i] = '\u0C4A'

                // decomposed dipthong (ె + ౖ) -> precomposed diphthong vowel sign (ై)
                '\u0C46' -> {
                    if (i + 1 < length && s[i + 1] == '\u0C56') {
                        s[i] = '\u0C48'
                        length = delete(s, i + 1, length)
                    }
                }

                // composed oo or au -> oo or au
                '\u0C12' -> {
                    if (i + 1 < length && s[i + 1] == '\u0C55') {
                        // (ఒ + ౕ) -> oo (ఓ)
                        s[i] = '\u0C13'
                        length = delete(s, i + 1, length)
                    } else if (i + 1 < length && s[i + 1] == '\u0C4C') {
                        // (ఒ + ౌ) -> au (ఔ)
                        s[i] = '\u0C14'
                        length = delete(s, i + 1, length)
                    }
                }
            }
            i++
        }
        return length
    }
}
