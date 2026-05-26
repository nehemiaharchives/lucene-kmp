package org.gnit.lucenekmp.analysis.km

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * A [TokenFilter] that reorders Khmer characters within each token to a canonical order,
 * and applies various regex-based normalizations for split vowels and coeng sequences.
 */
class CharReorderFilter(input: TokenStream) : TokenFilter(input) {

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    companion object {
        const val CAT_OTHER: Char = 0.toChar()
        const val CAT_BASE: Char = 1.toChar()
        const val CAT_ROBAT: Char = 2.toChar()
        const val CAT_COENG: Char = 3.toChar()
        const val CAT_Z: Char = 4.toChar()
        const val CAT_SHIFT: Char = 5.toChar()
        const val CAT_VOWEL: Char = 6.toChar()
        const val CAT_MS: Char = 7.toChar()
        const val CAT_MF: Char = 8.toChar()

        val categories: CharArray = CharArray(94)

        init {
            for (i in 0..'\u17B3'.code - '\u1780'.code)
                categories[i] = CAT_BASE
            for (i in '\u17B4'.code - '\u1780'.code..'\u17C5'.code - '\u1780'.code)
                categories[i] = CAT_VOWEL
            categories['\u17C6'.code - '\u1780'.code] = CAT_MS
            categories['\u17C7'.code - '\u1780'.code] = CAT_MF
            categories['\u17C8'.code - '\u1780'.code] = CAT_MF
            categories['\u17C9'.code - '\u1780'.code] = CAT_SHIFT
            categories['\u17CA'.code - '\u1780'.code] = CAT_SHIFT
            categories['\u17CB'.code - '\u1780'.code] = CAT_MS
            categories['\u17CC'.code - '\u1780'.code] = CAT_ROBAT
            for (i in '\u17CD'.code - '\u1780'.code..'\u17D1'.code - '\u1780'.code)
                categories[i] = CAT_MS
            categories['\u17D2'.code - '\u1780'.code] = CAT_COENG
            categories['\u17D3'.code - '\u1780'.code] = CAT_MS
            for (i in '\u17D4'.code - '\u1780'.code..'\u17DC'.code - '\u1780'.code)
                categories[i] = CAT_OTHER
            categories['\u17DD'.code - '\u1780'.code] = CAT_MS
        }

        fun charcat(c: Char): Char {
            if ('\u1780' <= c && c <= '\u17DD')
                return categories[c.code - '\u1780'.code]
            if (c == '\u200C' || c == '\u200D')
                return CAT_Z
            return CAT_OTHER
        }

        private const val BNB = "[\u1780-\u1793\u1795-\u17A2]"
        private const val SF = "[\u179E-\u17A0\u17A2]"
        private const val SNF = "[\u1780-\u179D\u17A1]"
        private const val SS = "[\u1784\u1789\u1793\u1794\u1798-\u179D]"
        private const val VA = "[\u17B7-\u17BA\u17BE\u17D0\u17DD]|\u17B6\u17C6"

        private val triisapR = Regex(
            "($SF(?:\u17D2$BNB){0,2}|$BNB(?:\u17D2$SF(?:\u17D2$BNB)?|\u17D2$BNB\u17D2$SF))\u17BB($VA)"
        )
        private val muusikatoanR = Regex(
            "($SS(?:\u17D2$SNF){0,2}|$SNF(?:\u17D2$SS(?:\u17D2$SNF)?|\u17D2$SNF\u17D2$SS))\u17BB($VA)"
        )

        private val selectedCorrectCharacterReplacements =
            arrayOf(
                "ប្តី" to "ប្ដី",
                "ផម្តើ" to "ផ្ដើម",
                "ផ្តើម" to "ផ្ដើម",
                "ផ្តល់" to "ផ្ដល់",
                "ម្តង" to "ម្ដង",
                "កណល្តា" to "កណ្ដាល",
                "កណ្តាល" to "កណ្ដាល",
            )
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        val buffer = termAtt.buffer()
        val len = termAtt.length

        if (len < 2 || len > 30)
            return true
        // if token doesn't start with a base, don't reorder
        if (charcat(buffer[0]) != CAT_BASE)
            return true

        val cats = CharArray(len)

        for (i in 0 until len) {
            var cat = charcat(buffer[i])
            // Recategorise base → coeng after coeng char
            if (i > 0 && cat == CAT_BASE && cats[i - 1] == CAT_COENG)
                cat = CAT_COENG
            cats[i] = cat
        }

        val indexes = Array(len) { it }
        indexes.sortWith { a, b -> cats[a].code - cats[b].code }

        val reordered = CharArray(len) { i -> buffer[indexes[i]] }

        var res = reordered.concatToString()
        res = res.replace(Regex("([\u200C\u200D])[\u200C\u200D]+"), "$1") // remove multiple ZW(N)J
        res = res.replace(Regex("\u17D2\u17D2+"), "\u17D2")               // remove multiple coeng (not in document)
        res = res.replace(Regex("\u17C1(\u17BB?)\u17B8"), "$1\u17BE")     // compose split vowels
        res = res.replace(Regex("\u17C1(\u17BB?)\u17B6"), "$1\u17C4")
        res = res.replace(Regex("\u17B8(\u17BB?)\u17C1"), "$1\u17BE")
        res = res.replace(Regex("\u17B6(\u17BB?)\u17C1"), "$1\u17C4")
        res = res.replace(Regex("([\u17B7-\u17BA\u17BE\u17D0\u17DD]|\u17B6\u17C6)(\u17BB)"), "$2$1") // reorder u before VA
        res = triisapR.replace(res, "$1\u17CA$2")      // Upshifting triisap
        res = muusikatoanR.replace(res, "$1\u17C9$2")  // Upshifting muusikatoan
        res = res.replace(Regex("(\u17D2\u179A)(\u17D2[\u1780-\u17B3])"), "$2$1") // coeng ro 2nd
        res = res.replace(Regex("(\u17D2)\u178A"), "$1\u178F")            // coeng da → ta
        for ((incorrect, correct) in selectedCorrectCharacterReplacements) {
            res = res.replace(incorrect, correct)
        }

        val newlen = res.length
        if (newlen != len)
            termAtt.setLength(newlen)

        // almost magical
        res.toCharArray(buffer, 0, 0, newlen)

        return true
    }
}
