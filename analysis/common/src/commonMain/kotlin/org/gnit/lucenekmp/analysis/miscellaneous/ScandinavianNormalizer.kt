package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.util.StemmerUtil

/**
 * This Normalizer does the heavy lifting for a set of Scandinavian normalization filters,
 * normalizing use of the interchangeable Scandinavian characters aeAEaeAEoeOEoeOE and folded variants (aa,
 * ao, ae, oe and oo) by transforming them to aaAAaeAEoeOE.
 */
class ScandinavianNormalizer(
    /**
     * Create the instance, while choosing which foldings to apply. This may differ between Norwegian,
     * Danish and Swedish.
     *
     * @param foldings a Set of Foldings to apply (i.e. AE, OE, AA, AO, OO)
     */
    private val foldings: Set<Foldings>
) {
    /** List of possible foldings that can be used when configuring the filter */
    enum class Foldings {
        AA,
        AO,
        AE,
        OE,
        OO
    }

    /**
     * Takes the original buffer and length as input. Modifies the buffer in-place and returns new
     * length
     *
     * @return new length
     */
    fun processToken(buffer: CharArray, length: Int): Int {
        var len = length
        for (i in 0 until len) {
            if (buffer[i] == ae_se) {
                buffer[i] = ae
            } else if (buffer[i] == AE_se) {
                buffer[i] = AE
            } else if (buffer[i] == oe_se) {
                buffer[i] = oe
            } else if (buffer[i] == OE_se) {
                buffer[i] = OE
            } else if (len - 1 > i) {
                if (buffer[i] == 'a'
                    && (
                        foldings.contains(Foldings.AA) && (buffer[i + 1] == 'a' || buffer[i + 1] == 'A')
                            || foldings.contains(Foldings.AO) && (buffer[i + 1] == 'o' || buffer[i + 1] == 'O')
                        )
                ) {
                    len = StemmerUtil.delete(buffer, i + 1, len)
                    buffer[i] = aa
                } else if (buffer[i] == 'A'
                    && (
                        foldings.contains(Foldings.AA) && (buffer[i + 1] == 'a' || buffer[i + 1] == 'A')
                            || foldings.contains(Foldings.AO) && (buffer[i + 1] == 'o' || buffer[i + 1] == 'O')
                        )
                ) {
                    len = StemmerUtil.delete(buffer, i + 1, len)
                    buffer[i] = AA
                } else if (buffer[i] == 'a'
                    && foldings.contains(Foldings.AE)
                    && (buffer[i + 1] == 'e' || buffer[i + 1] == 'E')
                ) {
                    len = StemmerUtil.delete(buffer, i + 1, len)
                    buffer[i] = ae
                } else if (buffer[i] == 'A'
                    && foldings.contains(Foldings.AE)
                    && (buffer[i + 1] == 'e' || buffer[i + 1] == 'E')
                ) {
                    len = StemmerUtil.delete(buffer, i + 1, len)
                    buffer[i] = AE
                } else if (buffer[i] == 'o'
                    && (
                        foldings.contains(Foldings.OE) && (buffer[i + 1] == 'e' || buffer[i + 1] == 'E')
                            || foldings.contains(Foldings.OO) && (buffer[i + 1] == 'o' || buffer[i + 1] == 'O')
                        )
                ) {
                    len = StemmerUtil.delete(buffer, i + 1, len)
                    buffer[i] = oe
                } else if (buffer[i] == 'O'
                    && (
                        foldings.contains(Foldings.OE) && (buffer[i + 1] == 'e' || buffer[i + 1] == 'E')
                            || foldings.contains(Foldings.OO) && (buffer[i + 1] == 'o' || buffer[i + 1] == 'O')
                        )
                ) {
                    len = StemmerUtil.delete(buffer, i + 1, len)
                    buffer[i] = OE
                }
            }
        }
        return len
    }

    companion object {
        val ALL_FOLDINGS: Set<Foldings> = Foldings.entries.toSet()

        const val AA: Char = '\u00C5' // Å
        const val aa: Char = '\u00E5' // å
        const val AE: Char = '\u00C6' // Æ
        const val ae: Char = '\u00E6' // æ
        const val AE_se: Char = '\u00C4' // Ä
        const val ae_se: Char = '\u00E4' // ä
        const val OE: Char = '\u00D8' // Ø
        const val oe: Char = '\u00F8' // ø
        const val OE_se: Char = '\u00D6' // Ö
        const val oe_se: Char = '\u00F6' // ö
    }
}
