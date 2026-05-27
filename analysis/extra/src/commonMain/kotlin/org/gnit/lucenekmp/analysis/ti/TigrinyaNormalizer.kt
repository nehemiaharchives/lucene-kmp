package org.gnit.lucenekmp.analysis.ti

/** Normalizer for Tigrinya Ethiopic text. */
internal class TigrinyaNormalizer {
    /**
     * Normalize an input buffer of Tigrinya text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var i = 0
        while (i < len) {
            s[i] = normalizeChar(s[i])
            i += 1
        }
        return len
    }

    private fun normalizeChar(ch: Char): Char {
        return when (ch) {
            'ሃ', 'ሐ', 'ሓ', 'ኀ', 'ኃ', 'ኻ' -> 'ሀ'
            'ሑ', 'ኁ' -> 'ሁ'
            'ሒ', 'ኂ' -> 'ሂ'
            'ሔ', 'ኄ' -> 'ሄ'
            'ሕ', 'ኅ' -> 'ህ'
            'ሖ', 'ኆ' -> 'ሆ'
            'ሠ' -> 'ሰ'
            'ሡ' -> 'ሱ'
            'ሢ' -> 'ሲ'
            'ሣ' -> 'ሳ'
            'ሤ' -> 'ሴ'
            'ሥ' -> 'ስ'
            'ሦ' -> 'ሶ'
            'ዉ' -> 'ው'
            'ዎ' -> 'ወ'
            'ዐ', 'ዓ' -> 'አ'
            'ዑ' -> 'ኡ'
            'ዒ' -> 'ኢ'
            'ዔ' -> 'ኤ'
            'ዕ' -> 'እ'
            'ዖ' -> 'ኦ'
            'ፀ' -> 'ጸ'
            'ፁ' -> 'ጹ'
            'ፂ' -> 'ጺ'
            'ፃ' -> 'ጻ'
            'ፄ' -> 'ጼ'
            'ፅ' -> 'ጽ'
            'ፆ' -> 'ጾ'
            '’', '‘', '‛', 'ʹ', 'ʼ', '`', '´' -> '\''
            '‐', '‑', '‒', '–', '—', '―' -> '-'
            else -> ch
        }
    }
}
