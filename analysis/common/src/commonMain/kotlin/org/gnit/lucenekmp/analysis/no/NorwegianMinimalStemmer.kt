package org.gnit.lucenekmp.analysis.no

import org.gnit.lucenekmp.analysis.no.NorwegianLightStemmer.Companion.BOKMAAL
import org.gnit.lucenekmp.analysis.no.NorwegianLightStemmer.Companion.NYNORSK
import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/**
 * Minimal Stemmer for Norwegian Bokmål (no-nb) and Nynorsk (no-nn)
 *
 * Stems known plural forms for Norwegian nouns only, together with genitiv -s
 */
internal class NorwegianMinimalStemmer(flags: Int) {
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
        // Remove genitiv s
        if (length > 4 && s[length - 1] == 's') {
            length--
        }

        if (length > 5
            && (
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
                    (endsWith(s, length, "ar") && useNynorsk)
                )
        ) { // masc pl indefinite
            return length - 2
        }

        if (length > 3) {
            when (s[length - 1]) {
                'a', // fem definite
                'e' // to get correct stem for nouns ending in -e (kake -> kak, kaker -> kak)
                -> return length - 1
            }
        }

        return length
    }
}

