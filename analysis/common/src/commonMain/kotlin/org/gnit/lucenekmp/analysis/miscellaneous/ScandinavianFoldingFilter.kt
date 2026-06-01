package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.util.StemmerUtil

/**
 * This filter folds Scandinavian characters åÅäæÄÆ-&gt;a and öÖøØ-&gt;o. It also discriminate
 * against use of double vowels aa, ae, ao, oe and oo, leaving just the first one.
 *
 * <p>It's a semantically more destructive solution than [ScandinavianNormalizationFilter] but
 * can in addition help with matching raksmorgas as räksmörgås.
 *
 * <p>blåbærsyltetøj == blåbärsyltetöj == blaabaarsyltetoej == blabarsyltetoj räksmörgås ==
 * ræksmørgås == ræksmörgaos == raeksmoergaas == raksmorgas
 *
 * <p>Background: Swedish åäö are in fact the same letters as Norwegian and Danish åæø and thus
 * interchangeable when used between these languages. They are however folded differently when
 * people type them on a keyboard lacking these characters.
 *
 * <p>In that situation almost all Swedish people use a, a, o instead of å, ä, ö.
 *
 * <p>Norwegians and Danes on the other hand usually type aa, ae and oe instead of å, æ and ø. Some
 * do however use a, a, o, oo, ao and sometimes permutations of everything above.
 *
 * <p>This filter solves that mismatch problem, but might also cause new.
 *
 * @see ScandinavianNormalizationFilter
 */
class ScandinavianFoldingFilter(input: TokenStream) : TokenFilter(input) {
    private val charTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        val buffer = charTermAttribute.buffer()
        var length = charTermAttribute.length

        var i = 0
        while (i < length) {
            if (buffer[i] == aa || buffer[i] == ae_se || buffer[i] == ae) {
                buffer[i] = 'a'
            } else if (buffer[i] == AA || buffer[i] == AE_se || buffer[i] == AE) {
                buffer[i] = 'A'
            } else if (buffer[i] == oe || buffer[i] == oe_se) {
                buffer[i] = 'o'
            } else if (buffer[i] == OE || buffer[i] == OE_se) {
                buffer[i] = 'O'
            } else if (length - 1 > i) {
                if ((buffer[i] == 'a' || buffer[i] == 'A')
                    && (buffer[i + 1] == 'a'
                        || buffer[i + 1] == 'A'
                        || buffer[i + 1] == 'e'
                        || buffer[i + 1] == 'E'
                        || buffer[i + 1] == 'o'
                        || buffer[i + 1] == 'O')
                ) {
                    length = StemmerUtil.delete(buffer, i + 1, length)
                } else if ((buffer[i] == 'o' || buffer[i] == 'O')
                    && (buffer[i + 1] == 'e'
                        || buffer[i + 1] == 'E'
                        || buffer[i + 1] == 'o'
                        || buffer[i + 1] == 'O')
                ) {
                    length = StemmerUtil.delete(buffer, i + 1, length)
                }
            }
            i++
        }

        charTermAttribute.setLength(length)

        return true
    }

    companion object {
        private const val AA = '\u00C5' // Å
        private const val aa = '\u00E5' // å
        private const val AE = '\u00C6' // Æ
        private const val ae = '\u00E6' // æ
        private const val AE_se = '\u00C4' // Ä
        private const val ae_se = '\u00E4' // ä
        private const val OE = '\u00D8' // Ø
        private const val oe = '\u00F8' // ø
        private const val OE_se = '\u00D6' // Ö
        private const val oe_se = '\u00F6' // ö
    }
}
