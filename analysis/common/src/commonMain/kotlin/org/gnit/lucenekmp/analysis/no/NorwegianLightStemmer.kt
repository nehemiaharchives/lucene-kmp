package org.gnit.lucenekmp.analysis.no

import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/**
 * Light Stemmer for Norwegian.
 *
 * Parts of this stemmer is adapted from SwedishLightStemFilter, except that while the Swedish
 * one has a pre-defined rule set and a corresponding corpus to validate against whereas the
 * Norwegian one is hand crafted.
 */
internal class NorwegianLightStemmer(flags: Int) {
    val useBokmaal: Boolean
    val useNynorsk: Boolean

    init {
        if (flags <= 0 || flags > BOKMAAL + NYNORSK) {
            throw IllegalArgumentException("invalid flags")
        }
        useBokmaal = flags and BOKMAAL != 0
        useNynorsk = flags and NYNORSK != 0
    }

    fun stem(s: CharArray, len: Int): Int {
        var length = len
        // Remove posessive -s (bilens -> bilen) and continue checking
        if (length > 4 && s[length - 1] == 's') {
            length--
        }

        // Remove common endings, single-pass
        if (length > 7
            && (
                (endsWith(s, length, "heter") && useBokmaal) || // general ending (hemmelig-heter -> hemmelig)
                    (endsWith(s, length, "heten") && useBokmaal) || // general ending (hemmelig-heten -> hemmelig)
                    (endsWith(s, length, "heita") && useNynorsk)
                )
        ) { // general ending (hemmeleg-heita -> hemmeleg)
            return length - 5
        }

        // Remove Nynorsk common endings, single-pass
        if (length > 8 && useNynorsk
            && (
                endsWith(s, length, "heiter") || // general ending (hemmeleg-heiter -> hemmeleg)
                    endsWith(s, length, "leiken") || // general ending (trygg-leiken -> trygg)
                    endsWith(s, length, "leikar")
                )
        ) { // general ending (trygg-leikar -> trygg)
            return length - 6
        }

        if (length > 5
            && (
                endsWith(s, length, "dom") || // general ending (kristen-dom -> kristen)
                    (endsWith(s, length, "het") && useBokmaal)
                )
        ) { // general ending (hemmelig-het -> hemmelig)
            return length - 3
        }

        if (length > 6 && useNynorsk
            && (
                endsWith(s, length, "heit") || // general ending (hemmeleg-heit -> hemmeleg)
                    endsWith(s, length, "semd") || // general ending (verk-semd -> verk)
                    endsWith(s, length, "leik")
                )
        ) { // general ending (trygg-leik -> trygg)
            return length - 4
        }

        if (length > 7
            && (
                endsWith(s, length, "elser") || // general ending (føl-elser -> føl)
                    endsWith(s, length, "elsen")
                )
        ) { // general ending (føl-elsen -> føl)
            return length - 5
        }

        if (length > 6
            && (
                (endsWith(s, length, "ende") && useBokmaal) || // (sov-ende -> sov)
                    (endsWith(s, length, "ande") && useNynorsk) || // (sov-ande -> sov)
                    endsWith(s, length, "else") || // general ending (føl-else -> føl)
                    (endsWith(s, length, "este") && useBokmaal) || // adj (fin-este -> fin)
                    (endsWith(s, length, "aste") && useNynorsk) || // adj (fin-aste -> fin)
                    (endsWith(s, length, "eren") && useBokmaal) || // masc
                    (endsWith(s, length, "aren") && useNynorsk)
                )
        ) { // masc
            return length - 4
        }

        if (length > 5
            && (
                (endsWith(s, length, "ere") && useBokmaal) || // adj (fin-ere -> fin)
                    (endsWith(s, length, "are") && useNynorsk) || // adj (fin-are -> fin)
                    (endsWith(s, length, "est") && useBokmaal) || // adj (fin-est -> fin)
                    (endsWith(s, length, "ast") && useNynorsk) || // adj (fin-ast -> fin)
                    endsWith(s, length, "ene") || // masc/fem/neutr pl definite (hus-ene)
                    (endsWith(s, length, "ane") && useNynorsk)
                )
        ) { // masc pl definite (gut-ane)
            return length - 3
        }

        if (length > 4
            && (
                endsWith(s, length, "er") || // masc/fem indefinite
                    endsWith(s, length, "en") || // masc/fem definite
                    endsWith(s, length, "et") || // neutr definite
                    (endsWith(s, length, "ar") && useNynorsk) || // masc pl indefinite
                    (endsWith(s, length, "st") && useBokmaal) || // adj (billig-st -> billig)
                    endsWith(s, length, "te")
                )
        ) {
            return length - 2
        }

        if (length > 3) {
            when (s[length - 1]) {
                'a', // fem definite
                'e', // to get correct stem for nouns ending in -e (kake -> kak, kaker -> kak)
                'n' -> return length - 1
            }
        }

        return length
    }

    companion object {
        /** Constant to remove Bokmål-specific endings */
        const val BOKMAAL: Int = 1

        /** Constant to remove Nynorsk-specific endings */
        const val NYNORSK: Int = 2
    }
}
