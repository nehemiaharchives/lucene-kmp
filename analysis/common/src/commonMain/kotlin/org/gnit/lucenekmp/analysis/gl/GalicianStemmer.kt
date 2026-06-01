package org.gnit.lucenekmp.analysis.gl

import org.gnit.lucenekmp.analysis.pt.RSLPStemmerBase
import org.gnit.lucenekmp.jdkport.assert

/**
 * Galician stemmer implementing "Regras do lematizador para o galego".
 *
 * @see RSLPStemmerBase
 * @see [Description of rules](http://bvg.udc.es/recursos_lingua/stemming.jsp)
 */
internal class GalicianStemmer : RSLPStemmerBase() {
    private val plural: Step
    private val unification: Step
    private val adverb: Step
    private val augmentative: Step
    private val noun: Step
    private val verb: Step
    private val vowel: Step

    init {
        val steps = parse("galician.rslp")
        plural = steps["Plural"]!!
        unification = steps["Unification"]!!
        adverb = steps["Adverb"]!!
        augmentative = steps["Augmentative"]!!
        noun = steps["Noun"]!!
        verb = steps["Verb"]!!
        vowel = steps["Vowel"]!!
    }

    /**
     * @param s buffer, oversized to at least `len+1`
     * @param len initial valid length of buffer
     * @return new valid length, stemmed
     */
    fun stem(s: CharArray, len: Int): Int {
        var length = len
        assert(s.size >= length + 1) { "this stemmer requires an oversized array of at least 1" }

        length = plural.apply(s, length)
        length = unification.apply(s, length)
        length = adverb.apply(s, length)

        var oldlen: Int
        do {
            oldlen = length
            length = augmentative.apply(s, length)
        } while (length != oldlen)

        oldlen = length
        length = noun.apply(s, length)
        if (length == oldlen) {
            /* suffix not removed */
            length = verb.apply(s, length)
        }

        length = vowel.apply(s, length)

        // RSLG accent removal
        for (i in 0..<length) {
            when (s[i]) {
                'á' -> s[i] = 'a'
                'é', 'ê' -> s[i] = 'e'
                'í' -> s[i] = 'i'
                'ó' -> s[i] = 'o'
                'ú' -> s[i] = 'u'
            }
        }

        return length
    }
}
