package org.gnit.lucenekmp.analysis.gl

import org.gnit.lucenekmp.analysis.pt.RSLPStemmerBase

/**
 * Minimal Stemmer for Galician
 *
 * This follows the "RSLP-S" algorithm, but modified for Galician. Hence this stemmer only
 * applies the plural reduction step of: "Regras do lematizador para o galego"
 *
 * @see RSLPStemmerBase
 */
internal class GalicianMinimalStemmer : RSLPStemmerBase() {
    private val pluralStep: Step = parse("galician.rslp")["Plural"]!!

    fun stem(s: CharArray, len: Int): Int {
        return pluralStep.apply(s, len)
    }
}
