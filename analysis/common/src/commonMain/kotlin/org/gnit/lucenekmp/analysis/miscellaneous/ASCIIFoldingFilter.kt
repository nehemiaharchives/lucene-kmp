package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.ArrayUtil

/**
 * This class converts alphabetic, numeric, and symbolic Unicode characters which are not in the
 * first 127 ASCII characters (the "Basic Latin" Unicode block) into their ASCII equivalents, if one
 * exists.
 *
 *
 * Characters from the following Unicode blocks are converted; however, only those characters
 * with reasonable ASCII alternatives are converted:
 *
 *
 *  * C1 Controls and Latin-1 Supplement: [http://www.unicode.org/charts/PDF/U0080.pdf](http://www.unicode.org/charts/PDF/U0080.pdf)
 *  * Latin Extended-A: [http://www.unicode.org/charts/PDF/U0100.pdf](http://www.unicode.org/charts/PDF/U0100.pdf)
 *  * Latin Extended-B: [http://www.unicode.org/charts/PDF/U0180.pdf](http://www.unicode.org/charts/PDF/U0180.pdf)
 *  * Latin Extended Additional: [http://www.unicode.org/charts/PDF/U1E00.pdf](http://www.unicode.org/charts/PDF/U1E00.pdf)
 *  * Latin Extended-C: [http://www.unicode.org/charts/PDF/U2C60.pdf](http://www.unicode.org/charts/PDF/U2C60.pdf)
 *  * Latin Extended-D: [http://www.unicode.org/charts/PDF/UA720.pdf](http://www.unicode.org/charts/PDF/UA720.pdf)
 *  * IPA Extensions: [http://www.unicode.org/charts/PDF/U0250.pdf](http://www.unicode.org/charts/PDF/U0250.pdf)
 *  * Phonetic Extensions: [http://www.unicode.org/charts/PDF/U1D00.pdf](http://www.unicode.org/charts/PDF/U1D00.pdf)
 *  * Phonetic Extensions Supplement: [http://www.unicode.org/charts/PDF/U1D80.pdf](http://www.unicode.org/charts/PDF/U1D80.pdf)
 *  * General Punctuation: [http://www.unicode.org/charts/PDF/U2000.pdf](http://www.unicode.org/charts/PDF/U2000.pdf)
 *  * Superscripts and Subscripts: [http://www.unicode.org/charts/PDF/U2070.pdf](http://www.unicode.org/charts/PDF/U2070.pdf)
 *  * Enclosed Alphanumerics: [http://www.unicode.org/charts/PDF/U2460.pdf](http://www.unicode.org/charts/PDF/U2460.pdf)
 *  * Dingbats: [http://www.unicode.org/charts/PDF/U2700.pdf](http://www.unicode.org/charts/PDF/U2700.pdf)
 *  * Supplemental Punctuation: [http://www.unicode.org/charts/PDF/U2E00.pdf](http://www.unicode.org/charts/PDF/U2E00.pdf)
 *  * Alphabetic Presentation Forms: [http://www.unicode.org/charts/PDF/UFB00.pdf](http://www.unicode.org/charts/PDF/UFB00.pdf)
 *  * Halfwidth and Fullwidth Forms: [http://www.unicode.org/charts/PDF/UFF00.pdf](http://www.unicode.org/charts/PDF/UFF00.pdf)
 *
 *
 * See: [http://en.wikipedia.org/wiki/Latin_characters_in_Unicode](http://en.wikipedia.org/wiki/Latin_characters_in_Unicode)
 *
 *
 * For example, '' will be replaced by 'a'.
 */
class ASCIIFoldingFilter
/**
 * Create a new [ASCIIFoldingFilter].
 *
 * @param input TokenStream to filter
 * @param isPreserveOriginal should the original tokens be kept on the input stream with a 0
 * position increment from the folded tokens?
 */ constructor(
    input: TokenStream,
    /** Does the filter preserve the original tokens?  */
    val isPreserveOriginal: Boolean = false
) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncAttr: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private var output = CharArray(512)
    private var outputPos = 0
    private var state: State? = null

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (state != null) {
            assert(this.isPreserveOriginal) { "state should only be captured if preserveOriginal is true" }
            restoreState(state)
            posIncAttr.setPositionIncrement(0)
            state = null
            return true
        }
        if (input.incrementToken()) {
            val buffer: CharArray = termAtt.buffer()
            val length: Int = termAtt.length

            // If no characters actually require rewriting then we
            // just return token as-is:
            for (i in 0..<length) {
                val c = buffer[i]
                if (c >= '\u0080') {
                    foldToASCII(buffer, length)
                    termAtt.copyBuffer(output, 0, outputPos)
                    break
                }
            }
            return true
        } else {
            return false
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        state = null
    }

    /**
     * Converts characters above ASCII to their ASCII equivalents. For example, accents are removed
     * from accented characters.
     *
     * @param input The string to fold
     * @param length The number of characters in the input string
     */
    fun foldToASCII(input: CharArray, length: Int) {
        // Worst-case length required:
        val maxSizeNeeded = 4 * length
        if (output.size < maxSizeNeeded) {
            output = CharArray(ArrayUtil.oversize(maxSizeNeeded, Character.BYTES))
        }

        outputPos = foldToASCII(input, 0, output, 0, length)
        if (this.isPreserveOriginal && needToPreserve(input, length)) {
            state = captureState()
        }
    }

    /**
     * Check if foldToASCII generated a different token.
     *
     * @param input original term
     * @param inputLength length of the original term
     * @return true if foldToASCII generated a different token
     */
    private fun needToPreserve(input: CharArray, inputLength: Int): Boolean {
        if (inputLength != outputPos) {
            return true
        }
        for (i in 0..<inputLength) {
            if (input[i] != output[i]) {
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * Converts characters above ASCII to their ASCII equivalents. For example, accents are removed
         * from accented characters.
         *
         * @param input The characters to fold
         * @param inputPos Index of the first character to fold
         * @param output The result of the folding. Should be of size &gt;= `length * 4`.
         * @param outputPos Index of output where to put the result of the folding
         * @param length The number of characters to fold
         * @return length of output
         * @lucene.internal
         */
        fun foldToASCII(
            input: CharArray, inputPos: Int, output: CharArray, outputPos: Int, length: Int
        ): Int {
            var outputPos = outputPos
            val end = inputPos + length
            for (pos in inputPos..<end) {
                val c = input[pos]

                // Quick test: if it's not in range then just keep current character
                if (c < '\u0080') {
                    output[outputPos++] = c
                } else {
                    when (c) {
                        '\u00C0', '\u00C1', '\u00C2', '\u00C3', '\u00C4', '\u00C5', '\u0100', '\u0102', '\u0104', '\u018F', '\u01CD', '\u01DE', '\u01E0', '\u01FA', '\u0200', '\u0202', '\u0226', '\u023A', '\u1D00', '\u1E00', '\u1EA0', '\u1EA2', '\u1EA4', '\u1EA6', '\u1EA8', '\u1EAA', '\u1EAC', '\u1EAE', '\u1EB0', '\u1EB2', '\u1EB4', '\u1EB6', '\u24B6', '\uFF21' -> output[outputPos++] =
                            'A'

                        '\u00E0', '\u00E1', '\u00E2', '\u00E3', '\u00E4', '\u00E5', '\u0101', '\u0103', '\u0105', '\u01CE', '\u01DF', '\u01E1', '\u01FB', '\u0201', '\u0203', '\u0227', '\u0250', '\u0259', '\u025A', '\u1D8F', '\u1D95', '\u1E01', '\u1E9A', '\u1EA1', '\u1EA3', '\u1EA5', '\u1EA7', '\u1EA9', '\u1EAB', '\u1EAD', '\u1EAF', '\u1EB1', '\u1EB3', '\u1EB5', '\u1EB7', '\u2090', '\u2094', '\u24D0', '\u2C65', '\u2C6F', '\uFF41' -> output[outputPos++] =
                            'a'

                        '\uA732' -> {
                            output[outputPos++] = 'A'
                            output[outputPos++] = 'A'
                        }

                        '\u00C6', '\u01E2', '\u01FC', '\u1D01' -> {
                            output[outputPos++] = 'A'
                            output[outputPos++] = 'E'
                        }

                        '\uA734' -> {
                            output[outputPos++] = 'A'
                            output[outputPos++] = 'O'
                        }

                        '\uA736' -> {
                            output[outputPos++] = 'A'
                            output[outputPos++] = 'U'
                        }

                        '\uA738', '\uA73A' -> {
                            output[outputPos++] = 'A'
                            output[outputPos++] = 'V'
                        }

                        '\uA73C' -> {
                            output[outputPos++] = 'A'
                            output[outputPos++] = 'Y'
                        }

                        '\u249C' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'a'
                            output[outputPos++] = ')'
                        }

                        '\uA733' -> {
                            output[outputPos++] = 'a'
                            output[outputPos++] = 'a'
                        }

                        '\u00E6', '\u01E3', '\u01FD', '\u1D02' -> {
                            output[outputPos++] = 'a'
                            output[outputPos++] = 'e'
                        }

                        '\uA735' -> {
                            output[outputPos++] = 'a'
                            output[outputPos++] = 'o'
                        }

                        '\uA737' -> {
                            output[outputPos++] = 'a'
                            output[outputPos++] = 'u'
                        }

                        '\uA739', '\uA73B' -> {
                            output[outputPos++] = 'a'
                            output[outputPos++] = 'v'
                        }

                        '\uA73D' -> {
                            output[outputPos++] = 'a'
                            output[outputPos++] = 'y'
                        }

                        '\u0181', '\u0182', '\u0243', '\u0299', '\u1D03', '\u1E02', '\u1E04', '\u1E06', '\u24B7', '\uFF22' -> output[outputPos++] = 'B'
                        '\u0180', '\u0183', '\u0253', '\u1D6C', '\u1D80', '\u1E03', '\u1E05', '\u1E07', '\u24D1', '\uFF42' -> output[outputPos++] = 'b'
                        '\u249D' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'b'
                            output[outputPos++] = ')'
                        }

                        '\u00C7', '\u0106', '\u0108', '\u010A', '\u010C', '\u0187', '\u023B', '\u0297', '\u1D04', '\u1E08', '\u24B8', '\uFF23' -> output[outputPos++] = 'C'
                        '\u00E7', '\u0107', '\u0109', '\u010B', '\u010D', '\u0188', '\u023C', '\u0255', '\u1E09', '\u2184', '\u24D2', '\uA73E', '\uA73F', '\uFF43' -> output[outputPos++] = 'c'
                        '\u249E' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'c'
                            output[outputPos++] = ')'
                        }

                        '\u00D0', '\u010E', '\u0110', '\u0189', '\u018A', '\u018B', '\u1D05', '\u1D06', '\u1E0A', '\u1E0C', '\u1E0E', '\u1E10', '\u1E12', '\u24B9', '\uA779', '\uFF24' -> output[outputPos++] = 'D'
                        '\u00F0', '\u010F', '\u0111', '\u018C', '\u0221', '\u0256', '\u0257', '\u1D6D', '\u1D81', '\u1D91', '\u1E0B', '\u1E0D', '\u1E0F', '\u1E11', '\u1E13', '\u24D3', '\uA77A', '\uFF44' -> output[outputPos++] = 'd'
                        '\u01C4', '\u01F1' -> {
                            output[outputPos++] = 'D'
                            output[outputPos++] = 'Z'
                        }

                        '\u01C5', '\u01F2' -> {
                            output[outputPos++] = 'D'
                            output[outputPos++] = 'z'
                        }

                        '\u249F' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'd'
                            output[outputPos++] = ')'
                        }

                        '\u0238' -> {
                            output[outputPos++] = 'd'
                            output[outputPos++] = 'b'
                        }

                        '\u01C6', '\u01F3', '\u02A3', '\u02A5' -> {
                            output[outputPos++] = 'd'
                            output[outputPos++] = 'z'
                        }

                        '\u00C8', '\u00C9', '\u00CA', '\u00CB', '\u0112', '\u0114', '\u0116', '\u0118', '\u011A', '\u018E', '\u0190', '\u0204', '\u0206', '\u0228', '\u0246', '\u1D07', '\u1E14', '\u1E16', '\u1E18', '\u1E1A', '\u1E1C', '\u1EB8', '\u1EBA', '\u1EBC', '\u1EBE', '\u1EC0', '\u1EC2', '\u1EC4', '\u1EC6', '\u24BA', '\u2C7B', '\uFF25' -> output[outputPos++] =
                            'E'

                        '\u00E8', '\u00E9', '\u00EA', '\u00EB', '\u0113', '\u0115', '\u0117', '\u0119', '\u011B', '\u01DD', '\u0205', '\u0207', '\u0229', '\u0247', '\u0258', '\u025B', '\u025C', '\u025D', '\u025E', '\u029A', '\u1D08', '\u1D92', '\u1D93', '\u1D94', '\u1E15', '\u1E17', '\u1E19', '\u1E1B', '\u1E1D', '\u1EB9', '\u1EBB', '\u1EBD', '\u1EBF', '\u1EC1', '\u1EC3', '\u1EC5', '\u1EC7', '\u2091', '\u24D4', '\u2C78', '\uFF45' -> output[outputPos++] =
                            'e'

                        '\u24A0' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'e'
                            output[outputPos++] = ')'
                        }

                        '\u0191', '\u1E1E', '\u24BB', '\uA730', '\uA77B', '\uA7FB', '\uFF26' -> output[outputPos++] = 'F'
                        '\u0192', '\u1D6E', '\u1D82', '\u1E1F', '\u1E9B', '\u24D5', '\uA77C', '\uFF46' -> output[outputPos++] = 'f'
                        '\u24A1' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'f'
                            output[outputPos++] = ')'
                        }

                        '\uFB00' -> {
                            output[outputPos++] = 'f'
                            output[outputPos++] = 'f'
                        }

                        '\uFB03' -> {
                            output[outputPos++] = 'f'
                            output[outputPos++] = 'f'
                            output[outputPos++] = 'i'
                        }

                        '\uFB04' -> {
                            output[outputPos++] = 'f'
                            output[outputPos++] = 'f'
                            output[outputPos++] = 'l'
                        }

                        '\uFB01' -> {
                            output[outputPos++] = 'f'
                            output[outputPos++] = 'i'
                        }

                        '\uFB02' -> {
                            output[outputPos++] = 'f'
                            output[outputPos++] = 'l'
                        }

                        '\u011C', '\u011E', '\u0120', '\u0122', '\u0193', '\u01E4', '\u01E5', '\u01E6', '\u01E7', '\u01F4', '\u0262', '\u029B', '\u1E20', '\u24BC', '\uA77D', '\uA77E', '\uFF27' -> output[outputPos++] = 'G'
                        '\u011D', '\u011F', '\u0121', '\u0123', '\u01F5', '\u0260', '\u0261', '\u1D77', '\u1D79', '\u1D83', '\u1E21', '\u24D6', '\uA77F', '\uFF47' -> output[outputPos++] = 'g'
                        '\u24A2' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'g'
                            output[outputPos++] = ')'
                        }

                        '\u0124', '\u0126', '\u021E', '\u029C', '\u1E22', '\u1E24', '\u1E26', '\u1E28', '\u1E2A', '\u24BD', '\u2C67', '\u2C75', '\uFF28' -> output[outputPos++] = 'H'
                        '\u0125', '\u0127', '\u021F', '\u0265', '\u0266', '\u02AE', '\u02AF', '\u1E23', '\u1E25', '\u1E27', '\u1E29', '\u1E2B', '\u1E96', '\u24D7', '\u2C68', '\u2C76', '\uFF48' -> output[outputPos++] = 'h'
                        '\u01F6' -> {
                            output[outputPos++] = 'H'
                            output[outputPos++] = 'V'
                        }

                        '\u24A3' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'h'
                            output[outputPos++] = ')'
                        }

                        '\u0195' -> {
                            output[outputPos++] = 'h'
                            output[outputPos++] = 'v'
                        }

                        '\u00CC', '\u00CD', '\u00CE', '\u00CF', '\u0128', '\u012A', '\u012C', '\u012E', '\u0130', '\u0196', '\u0197', '\u01CF', '\u0208', '\u020A', '\u026A', '\u1D7B', '\u1E2C', '\u1E2E', '\u1EC8', '\u1ECA', '\u24BE', '\uA7FE', '\uFF29' -> output[outputPos++] = 'I'
                        '\u00EC', '\u00ED', '\u00EE', '\u00EF', '\u0129', '\u012B', '\u012D', '\u012F', '\u0131', '\u01D0', '\u0209', '\u020B', '\u0268', '\u1D09', '\u1D62', '\u1D7C', '\u1D96', '\u1E2D', '\u1E2F', '\u1EC9', '\u1ECB', '\u2071', '\u24D8', '\uFF49' -> output[outputPos++] = 'i'
                        '\u0132' -> {
                            output[outputPos++] = 'I'
                            output[outputPos++] = 'J'
                        }

                        '\u24A4' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'i'
                            output[outputPos++] = ')'
                        }

                        '\u0133' -> {
                            output[outputPos++] = 'i'
                            output[outputPos++] = 'j'
                        }

                        '\u0134', '\u0248', '\u1D0A', '\u24BF', '\uFF2A' -> output[outputPos++] = 'J'
                        '\u0135', '\u01F0', '\u0237', '\u0249', '\u025F', '\u0284', '\u029D', '\u24D9', '\u2C7C', '\uFF4A' -> output[outputPos++] = 'j'
                        '\u24A5' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'j'
                            output[outputPos++] = ')'
                        }

                        '\u0136', '\u0198', '\u01E8', '\u1D0B', '\u1E30', '\u1E32', '\u1E34', '\u24C0', '\u2C69', '\uA740', '\uA742', '\uA744', '\uFF2B' -> output[outputPos++] = 'K'
                        '\u0137', '\u0199', '\u01E9', '\u029E', '\u1D84', '\u1E31', '\u1E33', '\u1E35', '\u24DA', '\u2C6A', '\uA741', '\uA743', '\uA745', '\uFF4B' -> output[outputPos++] = 'k'
                        '\u24A6' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'k'
                            output[outputPos++] = ')'
                        }

                        '\u0139', '\u013B', '\u013D', '\u013F', '\u0141', '\u023D', '\u029F', '\u1D0C', '\u1E36', '\u1E38', '\u1E3A', '\u1E3C', '\u24C1', '\u2C60', '\u2C62', '\uA746', '\uA748', '\uA780', '\uFF2C' -> output[outputPos++] = 'L'
                        '\u013A', '\u013C', '\u013E', '\u0140', '\u0142', '\u019A', '\u0234', '\u026B', '\u026C', '\u026D', '\u1D85', '\u1E37', '\u1E39', '\u1E3B', '\u1E3D', '\u24DB', '\u2C61', '\uA747', '\uA749', '\uA781', '\uFF4C' -> output[outputPos++] = 'l'
                        '\u01C7' -> {
                            output[outputPos++] = 'L'
                            output[outputPos++] = 'J'
                        }

                        '\u1EFA' -> {
                            output[outputPos++] = 'L'
                            output[outputPos++] = 'L'
                        }

                        '\u01C8' -> {
                            output[outputPos++] = 'L'
                            output[outputPos++] = 'j'
                        }

                        '\u24A7' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'l'
                            output[outputPos++] = ')'
                        }

                        '\u01C9' -> {
                            output[outputPos++] = 'l'
                            output[outputPos++] = 'j'
                        }

                        '\u1EFB' -> {
                            output[outputPos++] = 'l'
                            output[outputPos++] = 'l'
                        }

                        '\u02AA' -> {
                            output[outputPos++] = 'l'
                            output[outputPos++] = 's'
                        }

                        '\u02AB' -> {
                            output[outputPos++] = 'l'
                            output[outputPos++] = 'z'
                        }

                        '\u019C', '\u1D0D', '\u1E3E', '\u1E40', '\u1E42', '\u24C2', '\u2C6E', '\uA7FD', '\uA7FF', '\uFF2D' -> output[outputPos++] = 'M'
                        '\u026F', '\u0270', '\u0271', '\u1D6F', '\u1D86', '\u1E3F', '\u1E41', '\u1E43', '\u24DC', '\uFF4D' -> output[outputPos++] = 'm'
                        '\u24A8' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'm'
                            output[outputPos++] = ')'
                        }

                        '\u00D1', '\u0143', '\u0145', '\u0147', '\u014A', '\u019D', '\u01F8', '\u0220', '\u0274', '\u1D0E', '\u1E44', '\u1E46', '\u1E48', '\u1E4A', '\u24C3', '\uFF2E' -> output[outputPos++] = 'N'
                        '\u00F1', '\u0144', '\u0146', '\u0148', '\u0149', '\u014B', '\u019E', '\u01F9', '\u0235', '\u0272', '\u0273', '\u1D70', '\u1D87', '\u1E45', '\u1E47', '\u1E49', '\u1E4B', '\u207F', '\u24DD', '\uFF4E' -> output[outputPos++] = 'n'
                        '\u01CA' -> {
                            output[outputPos++] = 'N'
                            output[outputPos++] = 'J'
                        }

                        '\u01CB' -> {
                            output[outputPos++] = 'N'
                            output[outputPos++] = 'j'
                        }

                        '\u24A9' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'n'
                            output[outputPos++] = ')'
                        }

                        '\u01CC' -> {
                            output[outputPos++] = 'n'
                            output[outputPos++] = 'j'
                        }

                        '\u00D2', '\u00D3', '\u00D4', '\u00D5', '\u00D6', '\u00D8', '\u014C', '\u014E', '\u0150', '\u0186', '\u019F', '\u01A0', '\u01D1', '\u01EA', '\u01EC', '\u01FE', '\u020C', '\u020E', '\u022A', '\u022C', '\u022E', '\u0230', '\u1D0F', '\u1D10', '\u1E4C', '\u1E4E', '\u1E50', '\u1E52', '\u1ECC', '\u1ECE', '\u1ED0', '\u1ED2', '\u1ED4', '\u1ED6', '\u1ED8', '\u1EDA', '\u1EDC', '\u1EDE', '\u1EE0', '\u1EE2', '\u24C4', '\uA74A', '\uA74C', '\uFF2F' -> output[outputPos++] =
                            'O'

                        '\u00F2', '\u00F3', '\u00F4', '\u00F5', '\u00F6', '\u00F8', '\u014D', '\u014F', '\u0151', '\u01A1', '\u01D2', '\u01EB', '\u01ED', '\u01FF', '\u020D', '\u020F', '\u022B', '\u022D', '\u022F', '\u0231', '\u0254', '\u0275', '\u1D16', '\u1D17', '\u1D97', '\u1E4D', '\u1E4F', '\u1E51', '\u1E53', '\u1ECD', '\u1ECF', '\u1ED1', '\u1ED3', '\u1ED5', '\u1ED7', '\u1ED9', '\u1EDB', '\u1EDD', '\u1EDF', '\u1EE1', '\u1EE3', '\u2092', '\u24DE', '\u2C7A', '\uA74B', '\uA74D', '\uFF4F' -> output[outputPos++] =
                            'o'

                        '\u0152', '\u0276' -> {
                            output[outputPos++] = 'O'
                            output[outputPos++] = 'E'
                        }

                        '\uA74E' -> {
                            output[outputPos++] = 'O'
                            output[outputPos++] = 'O'
                        }

                        '\u0222', '\u1D15' -> {
                            output[outputPos++] = 'O'
                            output[outputPos++] = 'U'
                        }

                        '\u24AA' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'o'
                            output[outputPos++] = ')'
                        }

                        '\u0153', '\u1D14' -> {
                            output[outputPos++] = 'o'
                            output[outputPos++] = 'e'
                        }

                        '\uA74F' -> {
                            output[outputPos++] = 'o'
                            output[outputPos++] = 'o'
                        }

                        '\u0223' -> {
                            output[outputPos++] = 'o'
                            output[outputPos++] = 'u'
                        }

                        '\u01A4', '\u1D18', '\u1E54', '\u1E56', '\u24C5', '\u2C63', '\uA750', '\uA752', '\uA754', '\uFF30' -> output[outputPos++] = 'P'
                        '\u01A5', '\u1D71', '\u1D7D', '\u1D88', '\u1E55', '\u1E57', '\u24DF', '\uA751', '\uA753', '\uA755', '\uA7FC', '\uFF50' -> output[outputPos++] = 'p'
                        '\u24AB' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'p'
                            output[outputPos++] = ')'
                        }

                        '\u024A', '\u24C6', '\uA756', '\uA758', '\uFF31' -> output[outputPos++] = 'Q'
                        '\u0138', '\u024B', '\u02A0', '\u24E0', '\uA757', '\uA759', '\uFF51' -> output[outputPos++] = 'q'
                        '\u24AC' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'q'
                            output[outputPos++] = ')'
                        }

                        '\u0239' -> {
                            output[outputPos++] = 'q'
                            output[outputPos++] = 'p'
                        }

                        '\u0154', '\u0156', '\u0158', '\u0210', '\u0212', '\u024C', '\u0280', '\u0281', '\u1D19', '\u1D1A', '\u1E58', '\u1E5A', '\u1E5C', '\u1E5E', '\u24C7', '\u2C64', '\uA75A', '\uA782', '\uFF32' -> output[outputPos++] = 'R'
                        '\u0155', '\u0157', '\u0159', '\u0211', '\u0213', '\u024D', '\u027C', '\u027D', '\u027E', '\u027F', '\u1D63', '\u1D72', '\u1D73', '\u1D89', '\u1E59', '\u1E5B', '\u1E5D', '\u1E5F', '\u24E1', '\uA75B', '\uA783', '\uFF52' -> output[outputPos++] = 'r'
                        '\u24AD' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'r'
                            output[outputPos++] = ')'
                        }

                        '\u015A', '\u015C', '\u015E', '\u0160', '\u0218', '\u1E60', '\u1E62', '\u1E64', '\u1E66', '\u1E68', '\u24C8', '\uA731', '\uA785', '\uFF33' -> output[outputPos++] = 'S'
                        '\u015B', '\u015D', '\u015F', '\u0161', '\u017F', '\u0219', '\u023F', '\u0282', '\u1D74', '\u1D8A', '\u1E61', '\u1E63', '\u1E65', '\u1E67', '\u1E69', '\u1E9C', '\u1E9D', '\u24E2', '\uA784', '\uFF53' -> output[outputPos++] = 's'
                        '\u1E9E' -> {
                            output[outputPos++] = 'S'
                            output[outputPos++] = 'S'
                        }

                        '\u24AE' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 's'
                            output[outputPos++] = ')'
                        }

                        '\u00DF' -> {
                            output[outputPos++] = 's'
                            output[outputPos++] = 's'
                        }

                        '\uFB06' -> {
                            output[outputPos++] = 's'
                            output[outputPos++] = 't'
                        }

                        '\u0162', '\u0164', '\u0166', '\u01AC', '\u01AE', '\u021A', '\u023E', '\u1D1B', '\u1E6A', '\u1E6C', '\u1E6E', '\u1E70', '\u24C9', '\uA786', '\uFF34' -> output[outputPos++] = 'T'
                        '\u0163', '\u0165', '\u0167', '\u01AB', '\u01AD', '\u021B', '\u0236', '\u0287', '\u0288', '\u1D75', '\u1E6B', '\u1E6D', '\u1E6F', '\u1E71', '\u1E97', '\u24E3', '\u2C66', '\uFF54' -> output[outputPos++] = 't'
                        '\u00DE', '\uA766' -> {
                            output[outputPos++] = 'T'
                            output[outputPos++] = 'H'
                        }

                        '\uA728' -> {
                            output[outputPos++] = 'T'
                            output[outputPos++] = 'Z'
                        }

                        '\u24AF' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 't'
                            output[outputPos++] = ')'
                        }

                        '\u02A8' -> {
                            output[outputPos++] = 't'
                            output[outputPos++] = 'c'
                        }

                        '\u00FE', '\u1D7A', '\uA767' -> {
                            output[outputPos++] = 't'
                            output[outputPos++] = 'h'
                        }

                        '\u02A6' -> {
                            output[outputPos++] = 't'
                            output[outputPos++] = 's'
                        }

                        '\uA729' -> {
                            output[outputPos++] = 't'
                            output[outputPos++] = 'z'
                        }

                        '\u00D9', '\u00DA', '\u00DB', '\u00DC', '\u0168', '\u016A', '\u016C', '\u016E', '\u0170', '\u0172', '\u01AF', '\u01D3', '\u01D5', '\u01D7', '\u01D9', '\u01DB', '\u0214', '\u0216', '\u0244', '\u1D1C', '\u1D7E', '\u1E72', '\u1E74', '\u1E76', '\u1E78', '\u1E7A', '\u1EE4', '\u1EE6', '\u1EE8', '\u1EEA', '\u1EEC', '\u1EEE', '\u1EF0', '\u24CA', '\uFF35' -> output[outputPos++] =
                            'U'

                        '\u00F9', '\u00FA', '\u00FB', '\u00FC', '\u0169', '\u016B', '\u016D', '\u016F', '\u0171', '\u0173', '\u01B0', '\u01D4', '\u01D6', '\u01D8', '\u01DA', '\u01DC', '\u0215', '\u0217', '\u0289', '\u1D64', '\u1D99', '\u1E73', '\u1E75', '\u1E77', '\u1E79', '\u1E7B', '\u1EE5', '\u1EE7', '\u1EE9', '\u1EEB', '\u1EED', '\u1EEF', '\u1EF1', '\u24E4', '\uFF55' -> output[outputPos++] =
                            'u'

                        '\u24B0' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'u'
                            output[outputPos++] = ')'
                        }

                        '\u1D6B' -> {
                            output[outputPos++] = 'u'
                            output[outputPos++] = 'e'
                        }

                        '\u01B2', '\u0245', '\u1D20', '\u1E7C', '\u1E7E', '\u1EFC', '\u24CB', '\uA75E', '\uA768', '\uFF36' -> output[outputPos++] = 'V'
                        '\u028B', '\u028C', '\u1D65', '\u1D8C', '\u1E7D', '\u1E7F', '\u24E5', '\u2C71', '\u2C74', '\uA75F', '\uFF56' -> output[outputPos++] = 'v'
                        '\uA760' -> {
                            output[outputPos++] = 'V'
                            output[outputPos++] = 'Y'
                        }

                        '\u24B1' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'v'
                            output[outputPos++] = ')'
                        }

                        '\uA761' -> {
                            output[outputPos++] = 'v'
                            output[outputPos++] = 'y'
                        }

                        '\u0174', '\u01F7', '\u1D21', '\u1E80', '\u1E82', '\u1E84', '\u1E86', '\u1E88', '\u24CC', '\u2C72', '\uFF37' -> output[outputPos++] = 'W'
                        '\u0175', '\u01BF', '\u028D', '\u1E81', '\u1E83', '\u1E85', '\u1E87', '\u1E89', '\u1E98', '\u24E6', '\u2C73', '\uFF57' -> output[outputPos++] = 'w'
                        '\u24B2' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'w'
                            output[outputPos++] = ')'
                        }

                        '\u1E8A', '\u1E8C', '\u24CD', '\uFF38' -> output[outputPos++] = 'X'
                        '\u1D8D', '\u1E8B', '\u1E8D', '\u2093', '\u24E7', '\uFF58' -> output[outputPos++] = 'x'
                        '\u24B3' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'x'
                            output[outputPos++] = ')'
                        }

                        '\u00DD', '\u0176', '\u0178', '\u01B3', '\u0232', '\u024E', '\u028F', '\u1E8E', '\u1EF2', '\u1EF4', '\u1EF6', '\u1EF8', '\u1EFE', '\u24CE', '\uFF39' -> output[outputPos++] = 'Y'
                        '\u00FD', '\u00FF', '\u0177', '\u01B4', '\u0233', '\u024F', '\u028E', '\u1E8F', '\u1E99', '\u1EF3', '\u1EF5', '\u1EF7', '\u1EF9', '\u1EFF', '\u24E8', '\uFF59' -> output[outputPos++] = 'y'
                        '\u24B4' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'y'
                            output[outputPos++] = ')'
                        }

                        '\u0179', '\u017B', '\u017D', '\u01B5', '\u021C', '\u0224', '\u1D22', '\u1E90', '\u1E92', '\u1E94', '\u24CF', '\u2C6B', '\uA762', '\uFF3A' -> output[outputPos++] = 'Z'
                        '\u017A', '\u017C', '\u017E', '\u01B6', '\u021D', '\u0225', '\u0240', '\u0290', '\u0291', '\u1D76', '\u1D8E', '\u1E91', '\u1E93', '\u1E95', '\u24E9', '\u2C6C', '\uA763', '\uFF5A' -> output[outputPos++] = 'z'
                        '\u24B5' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = 'z'
                            output[outputPos++] = ')'
                        }

                        '\u2070', '\u2080', '\u24EA', '\u24FF', '\uFF10' -> output[outputPos++] = '0'
                        '\u00B9', '\u2081', '\u2460', '\u24F5', '\u2776', '\u2780', '\u278A', '\uFF11' -> output[outputPos++] = '1'
                        '\u2488' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '.'
                        }

                        '\u2474' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = ')'
                        }

                        '\u00B2', '\u2082', '\u2461', '\u24F6', '\u2777', '\u2781', '\u278B', '\uFF12' -> output[outputPos++] = '2'
                        '\u2489' -> {
                            output[outputPos++] = '2'
                            output[outputPos++] = '.'
                        }

                        '\u2475' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '2'
                            output[outputPos++] = ')'
                        }

                        '\u00B3', '\u2083', '\u2462', '\u24F7', '\u2778', '\u2782', '\u278C', '\uFF13' -> output[outputPos++] = '3'
                        '\u248A' -> {
                            output[outputPos++] = '3'
                            output[outputPos++] = '.'
                        }

                        '\u2476' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '3'
                            output[outputPos++] = ')'
                        }

                        '\u2074', '\u2084', '\u2463', '\u24F8', '\u2779', '\u2783', '\u278D', '\uFF14' -> output[outputPos++] = '4'
                        '\u248B' -> {
                            output[outputPos++] = '4'
                            output[outputPos++] = '.'
                        }

                        '\u2477' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '4'
                            output[outputPos++] = ')'
                        }

                        '\u2075', '\u2085', '\u2464', '\u24F9', '\u277A', '\u2784', '\u278E', '\uFF15' -> output[outputPos++] = '5'
                        '\u248C' -> {
                            output[outputPos++] = '5'
                            output[outputPos++] = '.'
                        }

                        '\u2478' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '5'
                            output[outputPos++] = ')'
                        }

                        '\u2076', '\u2086', '\u2465', '\u24FA', '\u277B', '\u2785', '\u278F', '\uFF16' -> output[outputPos++] = '6'
                        '\u248D' -> {
                            output[outputPos++] = '6'
                            output[outputPos++] = '.'
                        }

                        '\u2479' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '6'
                            output[outputPos++] = ')'
                        }

                        '\u2077', '\u2087', '\u2466', '\u24FB', '\u277C', '\u2786', '\u2790', '\uFF17' -> output[outputPos++] = '7'
                        '\u248E' -> {
                            output[outputPos++] = '7'
                            output[outputPos++] = '.'
                        }

                        '\u247A' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '7'
                            output[outputPos++] = ')'
                        }

                        '\u2078', '\u2088', '\u2467', '\u24FC', '\u277D', '\u2787', '\u2791', '\uFF18' -> output[outputPos++] = '8'
                        '\u248F' -> {
                            output[outputPos++] = '8'
                            output[outputPos++] = '.'
                        }

                        '\u247B' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '8'
                            output[outputPos++] = ')'
                        }

                        '\u2079', '\u2089', '\u2468', '\u24FD', '\u277E', '\u2788', '\u2792', '\uFF19' -> output[outputPos++] = '9'
                        '\u2490' -> {
                            output[outputPos++] = '9'
                            output[outputPos++] = '.'
                        }

                        '\u247C' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '9'
                            output[outputPos++] = ')'
                        }

                        '\u2469', '\u24FE', '\u277F', '\u2789', '\u2793' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '0'
                        }

                        '\u2491' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '0'
                            output[outputPos++] = '.'
                        }

                        '\u247D' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '0'
                            output[outputPos++] = ')'
                        }

                        '\u246A', '\u24EB' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '1'
                        }

                        '\u2492' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '1'
                            output[outputPos++] = '.'
                        }

                        '\u247E' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '1'
                            output[outputPos++] = ')'
                        }

                        '\u246B', '\u24EC' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '2'
                        }

                        '\u2493' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '2'
                            output[outputPos++] = '.'
                        }

                        '\u247F' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '2'
                            output[outputPos++] = ')'
                        }

                        '\u246C', '\u24ED' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '3'
                        }

                        '\u2494' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '3'
                            output[outputPos++] = '.'
                        }

                        '\u2480' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '3'
                            output[outputPos++] = ')'
                        }

                        '\u246D', '\u24EE' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '4'
                        }

                        '\u2495' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '4'
                            output[outputPos++] = '.'
                        }

                        '\u2481' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '4'
                            output[outputPos++] = ')'
                        }

                        '\u246E', '\u24EF' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '5'
                        }

                        '\u2496' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '5'
                            output[outputPos++] = '.'
                        }

                        '\u2482' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '5'
                            output[outputPos++] = ')'
                        }

                        '\u246F', '\u24F0' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '6'
                        }

                        '\u2497' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '6'
                            output[outputPos++] = '.'
                        }

                        '\u2483' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '6'
                            output[outputPos++] = ')'
                        }

                        '\u2470', '\u24F1' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '7'
                        }

                        '\u2498' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '7'
                            output[outputPos++] = '.'
                        }

                        '\u2484' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '7'
                            output[outputPos++] = ')'
                        }

                        '\u2471', '\u24F2' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '8'
                        }

                        '\u2499' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '8'
                            output[outputPos++] = '.'
                        }

                        '\u2485' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '8'
                            output[outputPos++] = ')'
                        }

                        '\u2472', '\u24F3' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '9'
                        }

                        '\u249A' -> {
                            output[outputPos++] = '1'
                            output[outputPos++] = '9'
                            output[outputPos++] = '.'
                        }

                        '\u2486' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '1'
                            output[outputPos++] = '9'
                            output[outputPos++] = ')'
                        }

                        '\u2473', '\u24F4' -> {
                            output[outputPos++] = '2'
                            output[outputPos++] = '0'
                        }

                        '\u249B' -> {
                            output[outputPos++] = '2'
                            output[outputPos++] = '0'
                            output[outputPos++] = '.'
                        }

                        '\u2487' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '2'
                            output[outputPos++] = '0'
                            output[outputPos++] = ')'
                        }

                        '\u00AB', '\u00BB', '\u201C', '\u201D', '\u201E', '\u2033', '\u2036', '\u275D', '\u275E', '\u276E', '\u276F', '\uFF02' -> output[outputPos++] = '"'
                        '\u2018', '\u2019', '\u201A', '\u201B', '\u2032', '\u2035', '\u2039', '\u203A', '\u275B', '\u275C', '\uFF07' -> output[outputPos++] = '\''
                        '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u207B', '\u208B', '\uFF0D' -> output[outputPos++] = '-'
                        '\u2045', '\u2772', '\uFF3B' -> output[outputPos++] = '['
                        '\u2046', '\u2773', '\uFF3D' -> output[outputPos++] = ']'
                        '\u207D', '\u208D', '\u2768', '\u276A', '\uFF08' -> output[outputPos++] = '('
                        '\u2E28' -> {
                            output[outputPos++] = '('
                            output[outputPos++] = '('
                        }

                        '\u207E', '\u208E', '\u2769', '\u276B', '\uFF09' -> output[outputPos++] = ')'
                        '\u2E29' -> {
                            output[outputPos++] = ')'
                            output[outputPos++] = ')'
                        }

                        '\u276C', '\u2770', '\uFF1C' -> output[outputPos++] = '<'
                        '\u276D', '\u2771', '\uFF1E' -> output[outputPos++] = '>'
                        '\u2774', '\uFF5B' -> output[outputPos++] = '{'
                        '\u2775', '\uFF5D' -> output[outputPos++] = '}'
                        '\u207A', '\u208A', '\uFF0B' -> output[outputPos++] = '+'
                        '\u207C', '\u208C', '\uFF1D' -> output[outputPos++] = '='
                        '\uFF01' -> output[outputPos++] = '!'
                        '\u203C' -> {
                            output[outputPos++] = '!'
                            output[outputPos++] = '!'
                        }

                        '\u2049' -> {
                            output[outputPos++] = '!'
                            output[outputPos++] = '?'
                        }

                        '\uFF03' -> output[outputPos++] = '#'
                        '\uFF04' -> output[outputPos++] = '$'
                        '\u2052', '\uFF05' -> output[outputPos++] = '%'
                        '\uFF06' -> output[outputPos++] = '&'
                        '\u204E', '\uFF0A' -> output[outputPos++] = '*'
                        '\uFF0C' -> output[outputPos++] = ','
                        '\uFF0E' -> output[outputPos++] = '.'
                        '\u2044', '\uFF0F' -> output[outputPos++] = '/'
                        '\uFF1A' -> output[outputPos++] = ':'
                        '\u204F', '\uFF1B' -> output[outputPos++] = ';'
                        '\uFF1F' -> output[outputPos++] = '?'
                        '\u2047' -> {
                            output[outputPos++] = '?'
                            output[outputPos++] = '?'
                        }

                        '\u2048' -> {
                            output[outputPos++] = '?'
                            output[outputPos++] = '!'
                        }

                        '\uFF20' -> output[outputPos++] = '@'
                        '\uFF3C' -> output[outputPos++] = '\\'
                        '\u2038', '\uFF3E' -> output[outputPos++] = '^'
                        '\uFF3F' -> output[outputPos++] = '_'
                        '\u2053', '\uFF5E' -> output[outputPos++] = '~'
                        else -> output[outputPos++] = c
                    }
                }
            }
            return outputPos
        }
    }
}
