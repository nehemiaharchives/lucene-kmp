package org.gnit.lucenekmp.analysis.cjk

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.util.StemmerUtil

/**
 * A [TokenFilter] that normalizes CJK width differences:
 *
 *
 *  * Folds fullwidth ASCII variants into the equivalent basic latin
 *  * Folds halfwidth Katakana variants into the equivalent kana
 *
 *
 *
 * NOTE: this filter can be viewed as a (practical) subset of NFKC/NFKD Unicode normalization.
 * See the normalization support in the ICU package for full normalization.
 */
class CJKWidthFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute =
        addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val text: CharArray = termAtt.buffer()
            var length: Int = termAtt.length
            var i = 0
            while (i < length) {
                val ch = text[i]
                if (ch.code in 0xFF01..0xFF5E) {
                    // Fullwidth ASCII variants
                    text[i] -= 0xFEE0.toChar().code
                } else if (ch.code in 0xFF65..0xFF9F) {
                    // Halfwidth Katakana variants
                    if ((ch.code == 0xFF9E || ch.code == 0xFF9F) && i > 0 && combine(text, i, ch)) {
                        length =
                            StemmerUtil.delete(text, i--, length)
                    } else {
                        text[i] = KANA_NORM[ch.code - 0xFF65]
                    }
                }
                i++
            }
            termAtt.setLength(length)
            return true
        } else {
            return false
        }
    }

    companion object {
        /* halfwidth kana mappings: 0xFF65-0xFF9D
   *
   * note: 0xFF9C and 0xFF9D are only mapped to 0x3099 and 0x309A
   * as a fallback when they cannot properly combine with a preceding
   * character into a composed form.
   */
        private val KANA_NORM = charArrayOf(
            0x30fb.toChar(),
            0x30f2.toChar(),
            0x30a1.toChar(),
            0x30a3.toChar(),
            0x30a5.toChar(),
            0x30a7.toChar(),
            0x30a9.toChar(),
            0x30e3.toChar(),
            0x30e5.toChar(),
            0x30e7.toChar(),
            0x30c3.toChar(),
            0x30fc.toChar(),
            0x30a2.toChar(),
            0x30a4.toChar(),
            0x30a6.toChar(),
            0x30a8.toChar(),
            0x30aa.toChar(),
            0x30ab.toChar(),
            0x30ad.toChar(),
            0x30af.toChar(),
            0x30b1.toChar(),
            0x30b3.toChar(),
            0x30b5.toChar(),
            0x30b7.toChar(),
            0x30b9.toChar(),
            0x30bb.toChar(),
            0x30bd.toChar(),
            0x30bf.toChar(),
            0x30c1.toChar(),
            0x30c4.toChar(),
            0x30c6.toChar(),
            0x30c8.toChar(),
            0x30ca.toChar(),
            0x30cb.toChar(),
            0x30cc.toChar(),
            0x30cd.toChar(),
            0x30ce.toChar(),
            0x30cf.toChar(),
            0x30d2.toChar(),
            0x30d5.toChar(),
            0x30d8.toChar(),
            0x30db.toChar(),
            0x30de.toChar(),
            0x30df.toChar(),
            0x30e0.toChar(),
            0x30e1.toChar(),
            0x30e2.toChar(),
            0x30e4.toChar(),
            0x30e6.toChar(),
            0x30e8.toChar(),
            0x30e9.toChar(),
            0x30ea.toChar(),
            0x30eb.toChar(),
            0x30ec.toChar(),
            0x30ed.toChar(),
            0x30ef.toChar(),
            0x30f3.toChar(),
            0x3099.toChar(),
            0x309A.toChar()
        )

        /* kana combining diffs: 0x30A6-0x30FD */
        private val KANA_COMBINE_VOICED = byteArrayOf(78, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 8, 8, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        )

        private val KANA_COMBINE_HALF_VOICED = byteArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 2, 0, 0, 2,
            0, 0, 2, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        )

        /** returns true if we successfully combined the voice mark  */
        private fun combine(text: CharArray, pos: Int, ch: Char): Boolean {
            val prev = text[pos - 1]
            if (prev.code in 0x30A6..0x30FD) {
                text[pos - 1] +=
                    Char(
                        (
                                if (ch.code == 0xFF9F)
                                    KANA_COMBINE_HALF_VOICED[prev.code - 0x30A6]
                                else
                                    KANA_COMBINE_VOICED[prev.code - 0x30A6]).toUShort()
                    ).code
                return text[pos - 1] != prev
            }
            return false
        }
    }
}
