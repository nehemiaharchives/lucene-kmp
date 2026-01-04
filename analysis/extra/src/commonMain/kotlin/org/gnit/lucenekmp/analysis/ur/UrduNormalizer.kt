package org.gnit.lucenekmp.analysis.ur

/**
 * Normalizer for Urdu.
 *
 * Applies Urdu-specific character normalization and removes diacritics.
 */
internal class UrduNormalizer {
    /**
     * Normalize an input buffer of Urdu text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        if (len == 0) return 0
        val sb = StringBuilder(len)
        var i = 0
        while (i < len) {
            val ch = s[i]
            if (i + 1 < len) {
                val next = s[i + 1]
                val combined = when {
                    ch == 'ا' && next == 'ٓ' -> "آ"
                    ch == 'ا' && next == 'ٔ' -> "أ"
                    ch == 'ے' && next == 'ٔ' -> "ۓ"
                    else -> null
                }
                if (combined != null) {
                    appendNormalized(sb, combined)
                    i += 2
                    continue
                }
            }
            val replacement = CHAR_REPLACEMENTS[ch]
            if (replacement != null) {
                if (replacement.isNotEmpty()) {
                    appendNormalized(sb, replacement)
                }
            } else {
                appendNormalized(sb, ch.toString())
            }
            i += 1
        }
        val outLen = sb.length
        var j = 0
        while (j < outLen) {
            s[j] = sb[j]
            j += 1
        }
        return outLen
    }

    private fun appendNormalized(sb: StringBuilder, value: String) {
        for (c in value) {
            if (!DIACRITICS.contains(c)) {
                sb.append(c)
            }
        }
    }

    companion object {
        private val DIACRITICS: Set<Char> = hashSetOf(
            '\u0610', '\u0611', '\u0612', '\u0613', '\u0614', '\u0615', '\u0616', '\u0617', '\u0618', '\u0619', '\u061A',
            '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650', '\u0651', '\u0652', '\u0653', '\u0654', '\u0655',
            '\u0656', '\u0657', '\u0658', '\u0659', '\u065A', '\u065B', '\u065C', '\u065D', '\u065E', '\u065F', '\u0670',
            '\u06D6', '\u06D7', '\u06D8', '\u06D9', '\u06DA', '\u06DB', '\u06DC', '\u06DF', '\u06E0', '\u06E1', '\u06E2',
            '\u06E3', '\u06E4', '\u06E5', '\u06E6', '\u06E7', '\u06E8', '\u06EA', '\u06EB', '\u06EC', '\u06ED'
        )

        private val CHAR_REPLACEMENTS: Map<Char, String> = buildCharMap()

        private fun buildCharMap(): Map<Char, String> {
            val map = mutableMapOf<Char, String>()
            add(map, "آ", "ﺁﺂ")
            add(map, "أ", "ﺃ")
            add(map, "ا", "ﺍﺎ")
            add(map, "ب", "ﺏﺐﺑﺒ")
            add(map, "پ", "ﭖﭘﭙ")
            add(map, "ت", "ﺕﺖﺗﺘ")
            add(map, "ٹ", "ﭦﭧﭨﭩ")
            add(map, "ث", "ﺛﺜﺚ")
            add(map, "ج", "ﺝﺞﺟﺠ")
            add(map, "ح", "ﺡﺣﺤﺢ")
            add(map, "خ", "ﺧﺨﺦ")
            add(map, "د", "ﺩﺪ")
            add(map, "ذ", "ﺬﺫ")
            add(map, "ر", "ﺭﺮ")
            add(map, "ز", "ﺯﺰ")
            add(map, "س", "ﺱﺲﺳﺴ")
            add(map, "ش", "ﺵﺶﺷﺸ")
            add(map, "ص", "ﺹﺺﺻﺼ")
            add(map, "ض", "ﺽﺾﺿﻀ")
            add(map, "ط", "ﻃﻄ")
            add(map, "ظ", "ﻅﻇﻈ")
            add(map, "ع", "ﻉﻊﻋﻌ")
            add(map, "غ", "ﻍﻏﻐ")
            add(map, "ف", "ﻑﻒﻓﻔ")
            add(map, "ق", "ﻕﻖﻗﻘ")
            add(map, "ل", "ﻝﻞﻟﻠ")
            add(map, "م", "ﻡﻢﻣﻤ")
            add(map, "ن", "ﻥﻦﻧﻨ")
            add(map, "چ", "ﭺﭻﭼﭽ")
            add(map, "ڈ", "ﮈﮉ")
            add(map, "ڑ", "ﮍﮌ")
            add(map, "ژ", "ﮋ")
            add(map, "ک", "ﮎﮏﮐﮑﻛك")
            add(map, "گ", "ﮒﮓﮔﮕ")
            add(map, "ں", "ﮞﮟ")
            add(map, "و", "ﻮﻭ")
            add(map, "ؤ", "ﺅ")
            add(map, "ھ", "ﮪﮬﮭﻬﻫﮫ")
            add(map, "ہ", "ﻩﮦﻪﮧﮩﮨه")
            add(map, "ۃ", "ة")
            add(map, "ء", "ﺀ")
            add(map, "ی", "ﯼىﯽﻰﻱﻲﯾﯿي")
            add(map, "ئ", "ﺋﺌ")
            add(map, "ے", "ﮮﮯﻳﻴ")
            add(map, "۰", "٠")
            add(map, "۱", "١")
            add(map, "۲", "٢")
            add(map, "۳", "٣")
            add(map, "۴", "٤")
            add(map, "۵", "٥")
            add(map, "۶", "٦")
            add(map, "۷", "٧")
            add(map, "۸", "٨")
            add(map, "۹", "٩")
            add(map, "لا", "ﻻﻼ")
            map['ـ'] = ""
            return map
        }

        private fun add(map: MutableMap<Char, String>, target: String, variants: String) {
            for (v in variants) {
                map[v] = target
            }
        }
    }
}
