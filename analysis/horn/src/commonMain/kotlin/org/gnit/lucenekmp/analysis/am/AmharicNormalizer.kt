package org.gnit.lucenekmp.analysis.am

/** Conservative Ethiopic normalization based on Horn's Amharic simplification intent. */
internal class AmharicNormalizer {
    fun normalize(s: CharArray, len: Int): Int {
        var i = 0
        while (i < len) {
            s[i] = normalizeChar(s[i])
            i++
        }
        return len
    }

    private fun normalizeChar(ch: Char): Char {
        return when (ch) {
            'ሃ', 'ሐ', 'ሓ', 'ኃ' -> 'ሀ'
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
            'ዐ' -> 'አ'
            'ዑ' -> 'ኡ'
            'ዒ' -> 'ኢ'
            'ዓ' -> 'ኣ'
            'ዔ' -> 'ኤ'
            'ዕ' -> 'እ'
            'ዖ' -> 'ኦ'
            'ጸ' -> 'ፀ'
            'ጹ' -> 'ፁ'
            'ጺ' -> 'ፂ'
            'ጻ' -> 'ፃ'
            'ጼ' -> 'ፄ'
            'ጽ' -> 'ፅ'
            'ጾ' -> 'ፆ'
            else -> ch
        }
    }
}
