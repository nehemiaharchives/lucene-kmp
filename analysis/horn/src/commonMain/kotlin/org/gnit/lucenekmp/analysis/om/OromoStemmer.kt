package org.gnit.lucenekmp.analysis.om

import org.gnit.lucenekmp.analysis.horn.HornLexicons

/**
 * Small Oromo stemmer for indexing/search.
 *
 * It uses compact generated Horn lexicon data plus common suffix reductions for plural,
 * case, TAM, passive, negative, and conjunction forms.
 */
internal class OromoStemmer {
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 2) return len
        val original = s.concatToString(0, len)
        val mapped = LEMMAS[original]
        val stem = mapped ?: HornLexicons.oromoStem(original) ?: lightStem(original)
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
        word = stripSuffix(word)
        word = stripSuffix(word)
        return if (word.length >= 3) word else input
    }

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length > suffix.length + 2 && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        private val LEMMAS: Map<String, String> = mapOf(
            "afeeramaniiru" to "afeeramuu",
            "dubbanne" to "dubbachuu",
            "namoota" to "nama",
            "manaan" to "mana"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "oota",
            "wwan",
            "leen",
            "tti",
            "irra",
            "iin",
            "aan",
            "een",
            "manii",
            "mani",
            "ani",
            "ne",
            "te",
            "tu",
            "ti",
            "ni",
            "n"
        )
    }
}
