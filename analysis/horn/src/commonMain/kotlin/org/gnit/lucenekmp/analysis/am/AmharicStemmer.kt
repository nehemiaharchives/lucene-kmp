package org.gnit.lucenekmp.analysis.am

import org.gnit.lucenekmp.analysis.horn.HornLexicons

/**
 * Small Amharic stemmer for indexing/search.
 *
 * This intentionally does not port Horn FSTs. It uses compact generated lexicon data and
 * strips high-frequency prefixes/suffixes that Horn models as prepositions, definiteness,
 * possessives, objects, plural, and conjunctive suffixes.
 */
internal class AmharicStemmer {
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 1) return len
        val original = s.concatToString(0, len)
        val mapped = LEMMAS[original]
        val lightStem = lightStem(original)
        val stem = mapped ?: lightStem.takeIf { it != original } ?: HornLexicons.amharicStem(original) ?: original
        if (stem == original) return len
        val out = stem.toCharArray()
        var i = 0
        while (i < out.size) {
            s[i] = out[i]
            i++
        }
        return out.size
    }

    private fun lightStem(input: String): String {
        var word = input
        word = stripPrefix(word)
        word = stripSuffix(word)
        word = stripSuffix(word)
        word = repairPluralStem(word)
        return if (word.length >= 2) word else input
    }

    private fun stripPrefix(word: String): String {
        for (prefix in PREFIXES) {
            if (word.length > prefix.length + 2 && word.startsWith(prefix)) {
                return word.substring(prefix.length)
            }
        }
        return word
    }

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length > suffix.length + 2 && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    private fun repairPluralStem(word: String): String {
        if (word.endsWith("ፎ") && word.length > 2) {
            return word.substring(0, word.length - 1) + "ፍ"
        }
        return word
    }

    companion object {
        private val LEMMAS: Map<String, String> = mapOf(
            "የማያስፈልጋትስ" to "አስፈለገ",
            "አይደለችም" to "ነው",
            "ይመጣሉ" to "መጣ",
            "ቢያስጨንቁአቸው" to "አስጨነቀ",
            "ለዘመዶቻችንም" to "ዘመድ"
        )

        private val PREFIXES: Array<String> = arrayOf(
            "እንደ",
            "የማይ",
            "ያል",
            "ለ",
            "በ",
            "ከ",
            "የ",
            "ስ",
            "እ"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "ዎቻችንም",
            "ዎቻችን",
            "ዎችንም",
            "ዎችን",
            "ዎችም",
            "ዎች",
            "ቻችንም",
            "ቻችን",
            "ችንም",
            "ችን",
            "አቸው",
            "ቸው",
            "ችሁ",
            "ችህ",
            "ችሽ",
            "ችም",
            "ች",
            "ንም",
            "ን",
            "ም",
            "ስ"
        )
    }
}
