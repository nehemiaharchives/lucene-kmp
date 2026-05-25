package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.util.AttributeSource

class ASCIIFoldingFilter : TokenFilter {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncAttr: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val preserveOriginal: Boolean
    private var output = CharArray(512)
    private var outputPos = 0
    private var state: AttributeSource.State? = null

    constructor(input: TokenStream) : this(input, false)

    /**
     * Create a new [ASCIIFoldingFilter].
     *
     * @param input TokenStream to filter
     * @param preserveOriginal should the original tokens be kept on the input stream with a 0
     * position increment from the folded tokens?
     */
    constructor(input: TokenStream, preserveOriginal: Boolean) : super(input) {
        this.preserveOriginal = preserveOriginal
    }

    /** Does the filter preserve the original tokens? */
    fun isPreserveOriginal(): Boolean = preserveOriginal

    override fun incrementToken(): Boolean {
        if (state != null) {
            check(preserveOriginal) { "state should only be captured if preserveOriginal is true" }
            restoreState(state)
            posIncAttr.setPositionIncrement(0)
            state = null
            return true
        }
        if (input.incrementToken()) {
            val buffer = termAtt.buffer()
            val length = termAtt.length

            // If no characters actually require rewriting then we
            // just return token as-is:
            for (i in 0 until length) {
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
            output = CharArray(maxSizeNeeded)
        }

        outputPos = foldToASCII(input, 0, output, 0, length)
        if (preserveOriginal && needToPreserve(input, length)) {
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
        for (i in 0 until inputLength) {
            if (input[i] != output[i]) {
                return true
            }
        }
        return false
    }

    companion object {
        fun foldToASCII(input: CharArray, inputPos: Int, output: CharArray, outputPosArg: Int, length: Int): Int {
            val end = inputPos + length
            var outputPos = outputPosArg
            for (pos in inputPos until end) {
                val folded = fold(input[pos])
                for (i in folded.indices) {
                    output[outputPos++] = folded[i]
                }
            }
            return outputPos
        }

        private fun fold(c: Char): String {
            if (c < '\u0080') return c.toString()
            return when (c) {
                '\u00C0', '\u00C1', '\u00C2', '\u00C3', '\u00C4', '\u00C5', '\u0100', '\u0102', '\u0104', '\u018F',
                '\u01CD', '\u01DE', '\u01E0', '\u01FA', '\u0200', '\u0202', '\u0226', '\u023A', '\u1D00', '\u1E00',
                '\u1EA0', '\u1EA2', '\u1EA4', '\u1EA6', '\u1EA8', '\u1EAA', '\u1EAC', '\u1EAE', '\u1EB0', '\u1EB2',
                '\u1EB4', '\u1EB6', '\u24B6', '\uFF21' -> "A"
                '\u00E0', '\u00E1', '\u00E2', '\u00E3', '\u00E4', '\u00E5', '\u0101', '\u0103', '\u0105', '\u01CE',
                '\u01DF', '\u01E1', '\u01FB', '\u0201', '\u0203', '\u0227', '\u0250', '\u0259', '\u1D8F', '\u1D95',
                '\u1E01', '\u1E9A', '\u1EA1', '\u1EA3', '\u1EA5', '\u1EA7', '\u1EA9', '\u1EAB', '\u1EAD', '\u1EAF',
                '\u1EB1', '\u1EB3', '\u1EB5', '\u1EB7', '\u2090', '\u2094', '\u24D0', '\u2C65', '\u2C6F', '\uFF41' -> "a"
                '\uA732' -> "AA"
                '\u00C6', '\u01E2', '\u01FC', '\u1D01' -> "AE"
                '\uA734' -> "AO"
                '\uA736' -> "AU"
                '\uA738', '\uA73A' -> "AV"
                '\uA73C' -> "AY"
                '\u249C' -> "(a)"
                '\uA733' -> "aa"
                '\u00E6', '\u01E3', '\u01FD', '\u1D02' -> "ae"
                '\uA735' -> "ao"
                '\uA737' -> "au"
                '\uA739', '\uA73B' -> "av"
                '\uA73D' -> "ay"
                '\u0181', '\u0182', '\u0243', '\u1D03', '\u1E02', '\u1E04', '\u1E06', '\u24B7', '\uFF22' -> "B"
                '\u0180', '\u0183', '\u0253', '\u1D6C', '\u1D80', '\u1E03', '\u1E05', '\u1E07', '\u24D1', '\uFF42' -> "b"
                '\u249D' -> "(b)"
                '\u00C7', '\u0106', '\u0108', '\u010A', '\u010C', '\u0187', '\u023B', '\u0297', '\u1D04', '\u1E08',
                '\u24B8', '\uA73E', '\uA742', '\uFF23' -> "C"
                '\u00E7', '\u0107', '\u0109', '\u010B', '\u010D', '\u0188', '\u023C', '\u0255', '\u1E09', '\u2184',
                '\u24D2', '\uA73F', '\uA743', '\uFF43' -> "c"
                '\u249E' -> "(c)"
                '\u00D0', '\u010E', '\u0110', '\u0189', '\u018A', '\u018B', '\u1D05', '\u1D06', '\u1E0A', '\u1E0C',
                '\u1E0E', '\u1E10', '\u1E12', '\u24B9', '\uA779', '\uFF24' -> "D"
                '\u00F0', '\u010F', '\u0111', '\u018C', '\u0221', '\u0256', '\u0257', '\u1D6D', '\u1D81', '\u1D91',
                '\u1E0B', '\u1E0D', '\u1E0F', '\u1E11', '\u1E13', '\u24D3', '\uA77A', '\uFF44' -> "d"
                '\u01C4', '\u01F1' -> "DZ"
                '\u01C5', '\u01F2' -> "Dz"
                '\u01C6', '\u01F3' -> "dz"
                '\u249F' -> "(d)"
                '\u00C8', '\u00C9', '\u00CA', '\u00CB', '\u0112', '\u0114', '\u0116', '\u0118', '\u011A', '\u018E',
                '\u0190', '\u0204', '\u0206', '\u0228', '\u0246', '\u1D07', '\u1E14', '\u1E16', '\u1E18', '\u1E1A',
                '\u1E1C', '\u1EB8', '\u1EBA', '\u1EBC', '\u1EBE', '\u1EC0', '\u1EC2', '\u1EC4', '\u1EC6', '\u24BA',
                '\u2C7B', '\uFF25' -> "E"
                '\u00E8', '\u00E9', '\u00EA', '\u00EB', '\u0113', '\u0115', '\u0117', '\u0119', '\u011B', '\u01DD',
                '\u0205', '\u0207', '\u0229', '\u0247', '\u0258', '\u025B', '\u025C', '\u025D', '\u025E', '\u029A',
                '\u1D08', '\u1D92', '\u1D93', '\u1D94', '\u1E15', '\u1E17', '\u1E19', '\u1E1B', '\u1E1D', '\u1EB9',
                '\u1EBB', '\u1EBD', '\u1EBF', '\u1EC1', '\u1EC3', '\u1EC5', '\u1EC7', '\u2091', '\u24D4', '\u2C78',
                '\uFF45' -> "e"
                '\u24A0' -> "(e)"
                '\u0191', '\u1E1E', '\u24BB', '\uA77B', '\uA7FB', '\uFF26' -> "F"
                '\u0192', '\u1D6E', '\u1D82', '\u1E1F', '\u1E9B', '\u24D5', '\uA77C', '\uFF46' -> "f"
                '\uFB00' -> "ff"
                '\uFB03' -> "ffi"
                '\uFB04' -> "ffl"
                '\uFB01' -> "fi"
                '\uFB02' -> "fl"
                '\u24A1' -> "(f)"
                '\u011C', '\u011E', '\u0120', '\u0122', '\u0193', '\u01E4', '\u01E6', '\u01F4', '\u0262', '\u029B',
                '\u1E20', '\u24BC', '\uA77D', '\uA77E', '\uFF27' -> "G"
                '\u011D', '\u011F', '\u0121', '\u0123', '\u01E5', '\u01E7', '\u01F5', '\u0260', '\u0261', '\u1D83',
                '\u1E21', '\u24D6', '\uA77F', '\uFF47' -> "g"
                '\u24A2' -> "(g)"
                '\u0124', '\u0126', '\u021E', '\u029C', '\u1E22', '\u1E24', '\u1E26', '\u1E28', '\u1E2A', '\u24BD',
                '\u2C67', '\u2C75', '\uFF28' -> "H"
                '\u0125', '\u0127', '\u021F', '\u0265', '\u0266', '\u02AE', '\u02AF', '\u1E23', '\u1E25', '\u1E27',
                '\u1E29', '\u1E2B', '\u1E96', '\u24D7', '\u2C68', '\u2C76', '\uFF48' -> "h"
                '\u01F6' -> "HV"
                '\u0195' -> "hv"
                '\u24A3' -> "(h)"
                '\u00CC', '\u00CD', '\u00CE', '\u00CF', '\u0128', '\u012A', '\u012C', '\u012E', '\u0130', '\u0196',
                '\u0197', '\u01CF', '\u0208', '\u020A', '\u026A', '\u1D7B', '\u1E2C', '\u1E2E', '\u1EC8', '\u1ECA',
                '\u24BE', '\uA7FE', '\uFF29' -> "I"
                '\u00EC', '\u00ED', '\u00EE', '\u00EF', '\u0129', '\u012B', '\u012D', '\u012F', '\u0131', '\u01D0',
                '\u0209', '\u020B', '\u0268', '\u1D09', '\u1D62', '\u1D96', '\u1E2D', '\u1E2F', '\u1EC9', '\u1ECB',
                '\u2071', '\u24D8', '\uFF49' -> "i"
                '\u0132' -> "IJ"
                '\u0133' -> "ij"
                '\u24A4' -> "(i)"
                '\u0134', '\u0248', '\u1D0A', '\u24BF', '\uFF2A' -> "J"
                '\u0135', '\u01F0', '\u0237', '\u0249', '\u025F', '\u0284', '\u029D', '\u24D9', '\u2C7C', '\uFF4A' -> "j"
                '\u24A5' -> "(j)"
                '\u0136', '\u0198', '\u01E8', '\u1D0B', '\u1E30', '\u1E32', '\u1E34', '\u24C0', '\u2C69', '\uA740',
                '\uA742', '\uA744', '\uFF2B' -> "K"
                '\u0137', '\u0199', '\u01E9', '\u029E', '\u1D84', '\u1E31', '\u1E33', '\u1E35', '\u24DA', '\u2C6A',
                '\uA741', '\uA743', '\uA745', '\uFF4B' -> "k"
                '\u24A6' -> "(k)"
                '\u0139', '\u013B', '\u013D', '\u013F', '\u0141', '\u023D', '\u029F', '\u1D0C', '\u1E36', '\u1E38',
                '\u1E3A', '\u1E3C', '\u24C1', '\u2C60', '\u2C62', '\uA746', '\uA748', '\uA780', '\uFF2C' -> "L"
                '\u013A', '\u013C', '\u013E', '\u0140', '\u0142', '\u019A', '\u0234', '\u026B', '\u026C', '\u026D',
                '\u1D85', '\u1E37', '\u1E39', '\u1E3B', '\u1E3D', '\u24DB', '\u2C61', '\uA747', '\uA749', '\uA781',
                '\uFF4C' -> "l"
                '\u01C7' -> "LJ"
                '\u01C8' -> "Lj"
                '\u01C9' -> "lj"
                '\u24A7' -> "(l)"
                '\u019C', '\u1D0D', '\u1E3E', '\u1E40', '\u1E42', '\u24C2', '\u2C6E', '\uA7FD', '\uA7FF', '\uFF2D' -> "M"
                '\u026F', '\u0270', '\u0271', '\u1D6F', '\u1D86', '\u1E3F', '\u1E41', '\u1E43', '\u24DC', '\uFF4D' -> "m"
                '\u24A8' -> "(m)"
                '\u00D1', '\u0143', '\u0145', '\u0147', '\u014A', '\u019D', '\u01F8', '\u0220', '\u0274', '\u1D0E',
                '\u1E44', '\u1E46', '\u1E48', '\u1E4A', '\u24C3', '\uFF2E' -> "N"
                '\u00F1', '\u0144', '\u0146', '\u0148', '\u0149', '\u014B', '\u019E', '\u01F9', '\u0235', '\u0272',
                '\u0273', '\u1D70', '\u1D87', '\u1E45', '\u1E47', '\u1E49', '\u1E4B', '\u207F', '\u24DD', '\uFF4E' -> "n"
                '\u01CA' -> "NJ"
                '\u01CB' -> "Nj"
                '\u01CC' -> "nj"
                '\u24A9' -> "(n)"
                '\u00D2', '\u00D3', '\u00D4', '\u00D5', '\u00D6', '\u00D8', '\u014C', '\u014E', '\u0150', '\u0186',
                '\u019F', '\u01A0', '\u01D1', '\u01EA', '\u01EC', '\u01FE', '\u020C', '\u020E', '\u022A', '\u022C',
                '\u022E', '\u0230', '\u1D0F', '\u1D10', '\u1E4C', '\u1E4E', '\u1E50', '\u1E52', '\u1ECC', '\u1ECE',
                '\u1ED0', '\u1ED2', '\u1ED4', '\u1ED6', '\u1ED8', '\u1EDA', '\u1EDC', '\u1EDE', '\u1EE0', '\u1EE2',
                '\u24C4', '\uA74A', '\uA74C', '\uFF2F' -> "O"
                '\u00F2', '\u00F3', '\u00F4', '\u00F5', '\u00F6', '\u00F8', '\u014D', '\u014F', '\u0151', '\u01A1',
                '\u01D2', '\u01EB', '\u01ED', '\u01FF', '\u020D', '\u020F', '\u022B', '\u022D', '\u022F', '\u0231',
                '\u0254', '\u0275', '\u1D16', '\u1D17', '\u1E4D', '\u1E4F', '\u1E51', '\u1E53', '\u1ECD', '\u1ECF',
                '\u1ED1', '\u1ED3', '\u1ED5', '\u1ED7', '\u1ED9', '\u1EDB', '\u1EDD', '\u1EDF', '\u1EE1', '\u1EE3',
                '\u2092', '\u24DE', '\uA74B', '\uA74D', '\uFF4F' -> "o"
                '\u0152', '\u0276' -> "OE"
                '\u0153', '\u1D14' -> "oe"
                '\u0222' -> "OU"
                '\u0223' -> "ou"
                '\u24AA' -> "(o)"
                '\u01A4', '\u1D18', '\u1E54', '\u1E56', '\u24C5', '\u2C63', '\uA750', '\uA752', '\uA754', '\uFF30' -> "P"
                '\u01A5', '\u1D71', '\u1D7D', '\u1D88', '\u1E55', '\u1E57', '\u24DF', '\uA751', '\uA753', '\uA755',
                '\uA7FC', '\uFF50' -> "p"
                '\u24AB' -> "(p)"
                '\u024A', '\u24C6', '\uA756', '\uA758', '\uFF31' -> "Q"
                '\u0138', '\u024B', '\u02A0', '\u24E0', '\uA757', '\uA759', '\uFF51' -> "q"
                '\u24AC' -> "(q)"
                '\u0154', '\u0156', '\u0158', '\u0210', '\u0212', '\u024C', '\u0280', '\u0281', '\u1D19', '\u1D1A',
                '\u1E58', '\u1E5A', '\u1E5C', '\u1E5E', '\u24C7', '\u2C64', '\uA75A', '\uA782', '\uFF32' -> "R"
                '\u0155', '\u0157', '\u0159', '\u0211', '\u0213', '\u024D', '\u027C', '\u027D', '\u027E', '\u027F',
                '\u1D63', '\u1D72', '\u1D73', '\u1D89', '\u1E59', '\u1E5B', '\u1E5D', '\u1E5F', '\u24E1', '\uA75B',
                '\uA783', '\uFF52' -> "r"
                '\u24AD' -> "(r)"
                '\u015A', '\u015C', '\u015E', '\u0160', '\u0218', '\u1E60', '\u1E62', '\u1E64', '\u1E66', '\u1E68',
                '\u24C8', '\uA731', '\uA785', '\uFF33' -> "S"
                '\u00DF' -> "ss"
                '\u015B', '\u015D', '\u015F', '\u0161', '\u017F', '\u0219', '\u023F', '\u0282', '\u1D74', '\u1D8A',
                '\u1E61', '\u1E63', '\u1E65', '\u1E67', '\u1E69', '\u1E9C', '\u1E9D', '\u24E2', '\uA784', '\uFF53' -> "s"
                '\u24AE' -> "(s)"
                '\u0162', '\u0164', '\u0166', '\u01AC', '\u01AE', '\u021A', '\u023E', '\u1D1B', '\u1E6A', '\u1E6C',
                '\u1E6E', '\u1E70', '\u24C9', '\uA786', '\uFF34' -> "T"
                '\u0163', '\u0165', '\u0167', '\u01AD', '\u0288', '\u1D75', '\u1E6B', '\u1E6D', '\u1E6F', '\u1E71',
                '\u1E97', '\u24E3', '\u2C66', '\uFF54' -> "t"
                '\u00DE', '\uA766' -> "TH"
                '\u00FE', '\u1D7A', '\uA767' -> "th"
                '\u24AF' -> "(t)"
                '\u00D9', '\u00DA', '\u00DB', '\u00DC', '\u0168', '\u016A', '\u016C', '\u016E', '\u0170', '\u0172',
                '\u01AF', '\u01D3', '\u01D5', '\u01D7', '\u01D9', '\u01DB', '\u0214', '\u0216', '\u0244', '\u1D1C',
                '\u1D7E', '\u1E72', '\u1E74', '\u1E76', '\u1E78', '\u1E7A', '\u1EE4', '\u1EE6', '\u1EE8', '\u1EEA',
                '\u1EEC', '\u1EEE', '\u1EF0', '\u24CA', '\uFF35' -> "U"
                '\u00F9', '\u00FA', '\u00FB', '\u00FC', '\u0169', '\u016B', '\u016D', '\u016F', '\u0171', '\u0173',
                '\u01B0', '\u01D4', '\u01D6', '\u01D8', '\u01DA', '\u01DC', '\u0215', '\u0217', '\u0289', '\u1D64',
                '\u1D99', '\u1E73', '\u1E75', '\u1E77', '\u1E79', '\u1E7B', '\u1EE5', '\u1EE7', '\u1EE9', '\u1EEB',
                '\u1EED', '\u1EEF', '\u1EF1', '\u24E4', '\uFF55' -> "u"
                '\u24B0' -> "(u)"
                '\u01B2', '\u0245', '\u1D20', '\u1E7C', '\u1E7E', '\u24CB', '\uA75E', '\uFF36' -> "V"
                '\u028B', '\u028C', '\u1D65', '\u1D8C', '\u1E7D', '\u1E7F', '\u24E5', '\u2C71', '\u2C74', '\uA75F',
                '\uFF56' -> "v"
                '\uA760' -> "VY"
                '\uA761' -> "vy"
                '\u24B1' -> "(v)"
                '\u0174', '\u1D21', '\u1E80', '\u1E82', '\u1E84', '\u1E86', '\u1E88', '\u24CC', '\u2C72', '\uFF37' -> "W"
                '\u0175', '\u1E81', '\u1E83', '\u1E85', '\u1E87', '\u1E89', '\u1E98', '\u24E6', '\u2C73', '\uFF57' -> "w"
                '\u24B2' -> "(w)"
                '\u1E8A', '\u1E8C', '\u24CD', '\uFF38' -> "X"
                '\u1D8D', '\u1E8B', '\u1E8D', '\u2093', '\u24E7', '\uFF58' -> "x"
                '\u24B3' -> "(x)"
                '\u00DD', '\u0176', '\u0178', '\u01B3', '\u0232', '\u024E', '\u028F', '\u1E8E', '\u1EF2', '\u1EF4',
                '\u1EF6', '\u1EF8', '\u1EFE', '\u24CE', '\uFF39' -> "Y"
                '\u00FD', '\u00FF', '\u0177', '\u01B4', '\u0233', '\u024F', '\u028E', '\u1E8F', '\u1E99', '\u1EF3',
                '\u1EF5', '\u1EF7', '\u1EF9', '\u1EFF', '\u24E8', '\uFF59' -> "y"
                '\u24B4' -> "(y)"
                '\u0179', '\u017B', '\u017D', '\u01B5', '\u0224', '\u1D22', '\u1E90', '\u1E92', '\u1E94', '\u24CF',
                '\u2C6B', '\uA762', '\uFF3A' -> "Z"
                '\u017A', '\u017C', '\u017E', '\u01B6', '\u0225', '\u0240', '\u0290', '\u0291', '\u1D76', '\u1D8E',
                '\u1E91', '\u1E93', '\u1E95', '\u24E9', '\u2C6C', '\uA763', '\uFF5A' -> "z"
                '\u24B5' -> "(z)"
                '\u2070', '\u2080', '\u24EA', '\u24FF', '\uFF10' -> "0"
                '\u00B9', '\u2081', '\u2460', '\u24F5', '\uFF11' -> "1"
                '\u00B2', '\u2082', '\u2461', '\u24F6', '\uFF12' -> "2"
                '\u00B3', '\u2083', '\u2462', '\u24F7', '\uFF13' -> "3"
                '\u2074', '\u2084', '\u2463', '\u24F8', '\uFF14' -> "4"
                '\u2075', '\u2085', '\u2464', '\u24F9', '\uFF15' -> "5"
                '\u2076', '\u2086', '\u2465', '\u24FA', '\uFF16' -> "6"
                '\u2077', '\u2087', '\u2466', '\u24FB', '\uFF17' -> "7"
                '\u2078', '\u2088', '\u2467', '\u24FC', '\uFF18' -> "8"
                '\u2079', '\u2089', '\u2468', '\u24FD', '\uFF19' -> "9"
                '\u2018', '\u2019', '\u201B', '\u2032', '\u2035', '\uFF07' -> "'"
                '\u201C', '\u201D', '\u201F', '\u2033', '\u2036', '\uFF02' -> "\""
                '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212', '\uFE63', '\uFF0D' -> "-"
                '\u00A0', '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005', '\u2006', '\u2007', '\u2008',
                '\u2009', '\u200A', '\u202F', '\u205F', '\u3000' -> " "
                else -> c.toString()
            }
        }
    }
}
