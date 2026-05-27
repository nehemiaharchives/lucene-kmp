package org.gnit.lucenekmp.analysis.be

/** Normalizer for Belarusian Cyrillic text. */
internal class BelarusianNormalizer {
    /**
     * Normalize an input buffer of Belarusian text.
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
            val next = if (i + 1 < len) s[i + 1] else 0.toChar()
            val normalized = when {
                ch == 'у' && next == '\u0306' -> {
                    i += 1
                    'ў'
                }
                ch == 'У' && next == '\u0306' -> {
                    i += 1
                    'Ў'
                }
                ch == 'е' && next == '\u0308' -> {
                    i += 1
                    'ё'
                }
                ch == 'Е' && next == '\u0308' -> {
                    i += 1
                    'Ё'
                }
                ch == 'и' -> 'і'
                ch == 'И' -> 'І'
                ch == '’' || ch == '‘' || ch == '‛' || ch == 'ʹ' || ch == 'ʼ' || ch == '`' || ch == '´' -> '\''
                ch == '‐' || ch == '‑' || ch == '‒' || ch == '–' || ch == '—' || ch == '―' -> '-'
                else -> ch
            }
            s[outLen] = normalized
            outLen += 1
            i += 1
        }
        return outLen
    }
}
