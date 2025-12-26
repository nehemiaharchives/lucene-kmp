package org.gnit.lucenekmp.analysis.pt

/**
 * Minimal Stemmer for Portuguese.
 */
class PortugueseMinimalStemmer : RSLPStemmerBase() {
    private val pluralStep: Step =
        parse("portuguese.rslp")["Plural"]
            ?: throw RuntimeException("Missing Plural step in portuguese.rslp")

    fun stem(s: CharArray, len: Int): Int {
        return pluralStep.apply(s, len)
    }
}
