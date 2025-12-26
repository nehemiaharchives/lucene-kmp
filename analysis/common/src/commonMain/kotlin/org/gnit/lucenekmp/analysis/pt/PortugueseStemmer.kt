package org.gnit.lucenekmp.analysis.pt

/**
 * Portuguese stemmer implementing the RSLP algorithm.
 */
class PortugueseStemmer : RSLPStemmerBase() {
    private val plural: Step
    private val feminine: Step
    private val adverb: Step
    private val augmentative: Step
    private val noun: Step
    private val verb: Step
    private val vowel: Step

    init {
        val steps = parse("portuguese.rslp")
        plural = steps["Plural"] ?: throw RuntimeException("Missing Plural step in portuguese.rslp")
        feminine = steps["Feminine"] ?: throw RuntimeException("Missing Feminine step in portuguese.rslp")
        adverb = steps["Adverb"] ?: throw RuntimeException("Missing Adverb step in portuguese.rslp")
        augmentative = steps["Augmentative"] ?: throw RuntimeException("Missing Augmentative step in portuguese.rslp")
        noun = steps["Noun"] ?: throw RuntimeException("Missing Noun step in portuguese.rslp")
        verb = steps["Verb"] ?: throw RuntimeException("Missing Verb step in portuguese.rslp")
        vowel = steps["Vowel"] ?: throw RuntimeException("Missing Vowel step in portuguese.rslp")
    }

    fun stem(s: CharArray, len: Int): Int {
        require(s.size >= len + 1) { "this stemmer requires an oversized array of at least 1" }

        var newLen = plural.apply(s, len)
        newLen = adverb.apply(s, newLen)
        newLen = feminine.apply(s, newLen)
        newLen = augmentative.apply(s, newLen)

        var oldLen = newLen
        newLen = noun.apply(s, newLen)

        if (newLen == oldLen) {
            oldLen = newLen
            newLen = verb.apply(s, newLen)
            if (newLen == oldLen) {
                newLen = vowel.apply(s, newLen)
            }
        }

        for (i in 0 until newLen) {
            when (s[i]) {
                'à', 'á', 'â', 'ã', 'ä', 'å' -> s[i] = 'a'
                'ç' -> s[i] = 'c'
                'è', 'é', 'ê', 'ë' -> s[i] = 'e'
                'ì', 'í', 'î', 'ï' -> s[i] = 'i'
                'ñ' -> s[i] = 'n'
                'ò', 'ó', 'ô', 'õ', 'ö' -> s[i] = 'o'
                'ù', 'ú', 'û', 'ü' -> s[i] = 'u'
                'ý', 'ÿ' -> s[i] = 'y'
            }
        }

        return newLen
    }
}
