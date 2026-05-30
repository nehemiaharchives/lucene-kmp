package org.gnit.lucenekmp.analysis.charfilter

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArrayMap
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.util.OpenStringBuilder
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromCharArray

class HTMLStripCharFilter : BaseCharFilter {

    companion object {
        /** This character denotes the end of file.  */
        private val YYEOF: Int = -1

        /** Initial size of the lookahead buffer.  */
        private val ZZ_BUFFERSIZE: Int = 16384

        /** Lexical states.  */
        private val YYINITIAL: Int = 0
        private val AMPERSAND: Int = 2
        private val NUMERIC_CHARACTER: Int = 4
        private val CHARACTER_REFERENCE_TAIL: Int = 6
        private val LEFT_ANGLE_BRACKET: Int = 8
        private val BANG: Int = 10
        private val COMMENT: Int = 12
        private val SCRIPT: Int = 14
        private val SCRIPT_COMMENT: Int = 16
        private val LEFT_ANGLE_BRACKET_SLASH: Int = 18
        private val LEFT_ANGLE_BRACKET_SPACE: Int = 20
        private val CDATA: Int = 22
        private val SERVER_SIDE_INCLUDE: Int = 24
        private val SINGLE_QUOTED_STRING: Int = 26
        private val DOUBLE_QUOTED_STRING: Int = 28
        private val END_TAG_TAIL_INCLUDE: Int = 30
        private val END_TAG_TAIL_EXCLUDE: Int = 32
        private val END_TAG_TAIL_SUBSTITUTE: Int = 34
        private val START_TAG_TAIL_INCLUDE: Int = 36
        private val START_TAG_TAIL_EXCLUDE: Int = 38
        private val START_TAG_TAIL_SUBSTITUTE: Int = 40
        private val STYLE: Int = 42
        private val STYLE_COMMENT: Int = 44

        /**
         * ZZ_LEXSTATE[l] is the state in the DFA for the lexical state l
         * ZZ_LEXSTATE[l+1] is the state in the DFA for the lexical state l
         * at the beginning of a line
         * l is of the form l = 2*k, k a non negative integer
         */
        private val ZZ_LEXSTATE: IntArray? = intArrayOf(
            0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7,
            8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15,
            16, 16, 17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22
        )

        private val ZZ_CMAP_TOP_PACKED_0: String = "\u0001\u0000\u0001\u0100\u0001\u0200\u0001\u0300\u0001\u0400\u0001\u0500\u0001\u0600\u0001\u0700" +
                "\u0001\u0800\u0001\u0900\u0001\u0a00\u0001\u0b00\u0001\u0c00\u0001\u0d00\u0001\u0e00\u0001\u0f00" +
                "\u0001\u1000\u0001\u0100\u0001\u1100\u0001\u1200\u0001\u1300\u0001\u0100\u0001\u1400\u0001\u1500" +
                "\u0001\u1600\u0001\u1700\u0001\u1800\u0001\u1900\u0001\u1a00\u0001\u1b00\u0001\u0100\u0001\u1c00" +
                "\u0001\u1d00\u0001\u1e00\u000a\u1f00\u0001\u2000\u0001\u2100\u0002\u1f00\u0001\u2200\u0001\u2300" +
                "\u0002\u1f00\u0019\u0100\u0001\u2400\u0051\u0100\u0001\u2500\u0004\u0100\u0001\u2600\u0001\u0100" +
                "\u0001\u2700\u0001\u2800\u0001\u2900\u0001\u2a00\u0001\u2b00\u0001\u2c00\u002b\u0100\u0001\u2d00" +
                "\u0021\u1f00\u0001\u0100\u0001\u2e00\u0001\u2f00\u0001\u0100\u0001\u3000\u0001\u3100\u0001\u3200" +
                "\u0001\u3300\u0001\u3400\u0001\u3500\u0001\u3600\u0001\u3700\u0001\u3800\u0001\u0100\u0001\u3900" +
                "\u0001\u3a00\u0001\u3b00\u0001\u3c00\u0001\u3d00\u0001\u3e00\u0001\u3f00\u0001\u1f00\u0001\u4000" +
                "\u0001\u4100\u0001\u4200\u0001\u4300\u0001\u4400\u0001\u4500\u0001\u4600\u0001\u4700\u0001\u4800" +
                "\u0001\u4900\u0001\u4a00\u0001\u4b00\u0001\u1f00\u0001\u4c00\u0001\u4d00\u0001\u4e00\u0001\u1f00" +
                "\u0003\u0100\u0001\u4f00\u0001\u5000\u0001\u5100\u000a\u1f00\u0004\u0100\u0001\u5200\u000f\u1f00" +
                "\u0002\u0100\u0001\u5300\u0021\u1f00\u0002\u0100\u0001\u5400\u0001\u5500\u0002\u1f00\u0001\u5600" +
                "\u0001\u5700\u0017\u0100\u0001\u5800\u0002\u0100\u0001\u5900\u0025\u1f00\u0001\u0100\u0001\u5a00" +
                "\u0001\u5b00\u0009\u1f00\u0001\u5c00\u0014\u1f00\u0001\u5d00\u0001\u5e00\u0001\u1f00\u0001\u5f00" +
                "\u0001\u6000\u0001\u6100\u0001\u6200\u0002\u1f00\u0001\u6300\u0005\u1f00\u0001\u6400\u0001\u6500" +
                "\u0001\u6600\u0005\u1f00\u0001\u6700\u0001\u6800\u0004\u1f00\u0001\u6900\u0011\u1f00\u00a6\u0100" +
                "\u0001\u6a00\u0010\u0100\u0001\u6b00\u0001\u6c00\u0015\u0100\u0001\u6d00\u001c\u0100\u0001\u6e00" +
                "\u000c\u1f00\u0002\u0100\u0001\u6f00\u0b06\u1f00\u0001\u7000\u02fe\u1f00"

        /**
         * Top-level table for translating characters to character classes
         */
        private val ZZ_CMAP_TOP: IntArray = zzUnpackcmap_top()

        private fun zzUnpackcmap_top(): IntArray {
            val result = IntArray(4352)
            var offset = 0
            offset = zzUnpackcmap_top(ZZ_CMAP_TOP_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackcmap_top(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed.get(i++).code
                val value = packed.get(i++).code
                do result[j++] = value while (--count > 0)
            }
            return j
        }

        /**
         * Second-level tables for translating characters to character classes
         */
        private val ZZ_CMAP_BLOCKS: IntArray = zzUnpackcmap_blocks()

        // internal const val ZZ_CMAP_BLOCKS_PACKED_0: String = // defined in ZZ_CMAP_BLOCKS_PACKED_0.kt

        private fun zzUnpackcmap_blocks(): IntArray {
            val result = IntArray(28928)
            var offset = 0
            offset = zzUnpackcmap_blocks(ZZ_CMAP_BLOCKS_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackcmap_blocks(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed.get(i++).code
                val value = packed.get(i++).code
                do result[j++] = value while (--count > 0)
            }
            return j
        }

        /**
         * Translates DFA states to action switch labels.
         */
        private val ZZ_ACTION: IntArray = zzUnpackAction()

        // internal const val ZZ_ACTION_PACKED_0: String = // defined in ZZ_ACTION_PACKED_0.kt

        private fun zzUnpackAction(): IntArray {
            val result = IntArray(3082)
            var offset = 0
            offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackAction(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed.get(i++).code
                val value = packed.get(i++).code
                do result[j++] = value while (--count > 0)
            }
            return j
        }

        /**
         * Translates a state to a row index in the transition table
         */
        private val ZZ_ROWMAP: IntArray = zzUnpackRowMap()

        // internal const val ZZ_ROWMAP_PACKED_0: String = // defined in ZZ_ROWMAP_PACKED_0.kt

        private fun zzUnpackRowMap(): IntArray {
            val result = IntArray(3082)
            var offset = 0
            offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackRowMap(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                val high = packed.get(i++).code shl 16
                result[j++] = high or packed.get(i++).code
            }
            return j
        }

        /**
         * The transition table of the DFA
         */
        private val ZZ_TRANS: IntArray = zzUnpackTrans()
        // private final String ZZ_TRANS_PACKED_0 = // defined in ZZ_TRANS_PACKED_0.kt

        //  private final String ZZ_TRANS_PACKED_1 = // defined in ZZ_TRANS_PACKED_1.kt

        private fun zzUnpackTrans(): IntArray {
            val result = IntArray(213564)
            var offset = 0
            offset = zzUnpackTrans(ZZ_TRANS_PACKED_0, offset, result)
            offset = zzUnpackTrans(ZZ_TRANS_PACKED_1, offset, result)
            return result
        }

        private fun zzUnpackTrans(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed.get(i++).code
                var value = packed.get(i++).code
                value--
                do result[j++] = value while (--count > 0)
            }
            return j
        }

        /** Error code for "Unknown internal scanner error".  */
        private const val ZZ_UNKNOWN_ERROR: Int = 0
        /** Error code for "could not match input".  */
        private const val ZZ_NO_MATCH: Int = 1
        /** Error code for "pushback value was too large".  */
        private const val ZZ_PUSHBACK_2BIG: Int = 2

        /**
         * Error messages for [.ZZ_UNKNOWN_ERROR], [.ZZ_NO_MATCH], and
         * [.ZZ_PUSHBACK_2BIG] respectively.
         */
        private val ZZ_ERROR_MSG: Array<String> = arrayOf(
            "Unknown internal scanner error",
            "Error: could not match input",
            "Error: pushback value was too large"
        )

        /**
         * ZZ_ATTRIBUTE[aState] contains the attributes of state `aState`
         */
        private val ZZ_ATTRIBUTE: IntArray = zzUnpackAttribute()

        // internal const val ZZ_ATTRIBUTE_PACKED_0: String = // defined in ZZ_ATTRIBUTE_PACKED_0.kt

        private fun zzUnpackAttribute(): IntArray {
            val result = IntArray(3082)
            var offset = 0
            offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackAttribute(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed.get(i++).code
                val value = packed.get(i++).code
                do result[j++] = value while (--count > 0)
            }
            return j
        }
        // line 5075

        // line 5137
        /* user code: */
        private val upperCaseVariantsAccepted = mapOf(
            "quot" to "QUOT",
            "copy" to "COPY",
            "gt" to "GT",
            "lt" to "LT",
            "reg" to "REG",
            "amp" to "AMP",
        )

        private val initValueMapForEntityValues: Map<String, Char> = mapOf(
            "AElig" to 'Æ',
            "Aacute" to 'Á',
            "Acirc" to 'Â',
            "Agrave" to 'À',
            "Alpha" to 'Α',
            "Aring" to 'Å',
            "Atilde" to 'Ã',
            "Auml" to 'Ä',
            "Beta" to 'Β',
            "Ccedil" to 'Ç',
            "Chi" to 'Χ',
            "Dagger" to '‡',
            "Delta" to 'Δ',
            "ETH" to 'Ð',
            "Eacute" to 'É',
            "Ecirc" to 'Ê',
            "Egrave" to 'È',
            "Epsilon" to 'Ε',
            "Eta" to 'Η',
            "Euml" to 'Ë',
            "Gamma" to 'Γ',
            "Iacute" to 'Í',
            "Icirc" to 'Î',
            "Igrave" to 'Ì',
            "Iota" to 'Ι',
            "Iuml" to 'Ï',
            "Kappa" to 'Κ',
            "Lambda" to 'Λ',
            "Mu" to 'Μ',
            "Ntilde" to 'Ñ',
            "Nu" to 'Ν',
            "OElig" to 'Œ',
            "Oacute" to 'Ó',
            "Ocirc" to 'Ô',
            "Ograve" to 'Ò',
            "Omega" to 'Ω',
            "Omicron" to 'Ο',
            "Oslash" to 'Ø',
            "Otilde" to 'Õ',
            "Ouml" to 'Ö',
            "Phi" to 'Φ',
            "Pi" to 'Π',
            "Prime" to '″',
            "Psi" to 'Ψ',
            "Rho" to 'Ρ',
            "Scaron" to 'Š',
            "Sigma" to 'Σ',
            "THORN" to 'Þ',
            "Tau" to 'Τ',
            "Theta" to 'Θ',
            "Uacute" to 'Ú',
            "Ucirc" to 'Û',
            "Ugrave" to 'Ù',
            "Upsilon" to 'Υ',
            "Uuml" to 'Ü',
            "Xi" to 'Ξ',
            "Yacute" to 'Ý',
            "Yuml" to 'Ÿ',
            "Zeta" to 'Ζ',
            "aacute" to 'á',
            "acirc" to 'â',
            "acute" to '´',
            "aelig" to 'æ',
            "agrave" to 'à',
            "alefsym" to 'ℵ',
            "alpha" to 'α',
            "amp" to '&',
            "and" to '∧',
            "ang" to '∠',
            "apos" to '\'',
            "aring" to 'å',
            "asymp" to '≈',
            "atilde" to 'ã',
            "auml" to 'ä',
            "bdquo" to '„',
            "beta" to 'β',
            "brvbar" to '¦',
            "bull" to '•',
            "cap" to '∩',
            "ccedil" to 'ç',
            "cedil" to '¸',
            "cent" to '¢',
            "chi" to 'χ',
            "circ" to 'ˆ',
            "clubs" to '♣',
            "cong" to '≅',
            "copy" to '©',
            "crarr" to '↵',
            "cup" to '∪',
            "curren" to '¤',
            "dArr" to '⇓',
            "dagger" to '†',
            "darr" to '↓',
            "deg" to '°',
            "delta" to 'δ',
            "diams" to '♦',
            "divide" to '÷',
            "eacute" to 'é',
            "ecirc" to 'ê',
            "egrave" to 'è',
            "empty" to '∅',
            "emsp" to ' ',
            "ensp" to ' ',
            "epsilon" to 'ε',
            "equiv" to '≡',
            "eta" to 'η',
            "eth" to 'ð',
            "euml" to 'ë',
            "euro" to '€',
            "exist" to '∃',
            "fnof" to 'ƒ',
            "forall" to '∀',
            "frac12" to '½',
            "frac14" to '¼',
            "frac34" to '¾',
            "frasl" to '⁄',
            "gamma" to 'γ',
            "ge" to '≥',
            "gt" to '>',
            "hArr" to '⇔',
            "harr" to '↔',
            "hearts" to '♥',
            "hellip" to '…',
            "iacute" to 'í',
            "icirc" to 'î',
            "iexcl" to '¡',
            "igrave" to 'ì',
            "image" to 'ℑ',
            "infin" to '∞',
            "int" to '∫',
            "iota" to 'ι',
            "iquest" to '¿',
            "isin" to '∈',
            "iuml" to 'ï',
            "kappa" to 'κ',
            "lArr" to '⇐',
            "lambda" to 'λ',
            "lang" to '〈',
            "laquo" to '«',
            "larr" to '←',
            "lceil" to '⌈',
            "ldquo" to '“',
            "le" to '≤',
            "lfloor" to '⌊',
            "lowast" to '∗',
            "loz" to '◊',
            "lrm" to '‎',
            "lsaquo" to '‹',
            "lsquo" to '‘',
            "lt" to '<',
            "macr" to '¯',
            "mdash" to '—',
            "micro" to 'µ',
            "middot" to '·',
            "minus" to '−',
            "mu" to 'μ',
            "nabla" to '∇',
            "nbsp" to ' ',
            "ndash" to '–',
            "ne" to '≠',
            "ni" to '∋',
            "not" to '¬',
            "notin" to '∉',
            "nsub" to '⊄',
            "ntilde" to 'ñ',
            "nu" to 'ν',
            "oacute" to 'ó',
            "ocirc" to 'ô',
            "oelig" to 'œ',
            "ograve" to 'ò',
            "oline" to '‾',
            "omega" to 'ω',
            "omicron" to 'ο',
            "oplus" to '⊕',
            "or" to '∨',
            "ordf" to 'ª',
            "ordm" to 'º',
            "oslash" to 'ø',
            "otilde" to 'õ',
            "otimes" to '⊗',
            "ouml" to 'ö',
            "para" to '¶',
            "part" to '∂',
            "permil" to '‰',
            "perp" to '⊥',
            "phi" to 'φ',
            "pi" to 'π',
            "piv" to 'ϖ',
            "plusmn" to '±',
            "pound" to '£',
            "prime" to '′',
            "prod" to '∏',
            "prop" to '∝',
            "psi" to 'ψ',
            "quot" to '"',
            "rArr" to '⇒',
            "radic" to '√',
            "rang" to '〉',
            "raquo" to '»',
            "rarr" to '→',
            "rceil" to '⌉',
            "rdquo" to '”',
            "real" to 'ℜ',
            "reg" to '®',
            "rfloor" to '⌋',
            "rho" to 'ρ',
            "rlm" to '‏',
            "rsaquo" to '›',
            "rsquo" to '’',
            "sbquo" to '‚',
            "scaron" to 'š',
            "sdot" to '⋅',
            "sect" to '§',
            "shy" to '­',
            "sigma" to 'σ',
            "sigmaf" to 'ς',
            "sim" to '∼',
            "spades" to '♠',
            "sub" to '⊂',
            "sube" to '⊆',
            "sum" to '∑',
            "sup" to '⊃',
            "sup1" to '¹',
            "sup2" to '²',
            "sup3" to '³',
            "supe" to '⊇',
            "szlig" to 'ß',
            "tau" to 'τ',
            "there4" to '∴',
            "theta" to 'θ',
            "thetasym" to 'ϑ',
            "thinsp" to ' ',
            "thorn" to 'þ',
            "tilde" to '˜',
            "times" to '×',
            "trade" to '™',
            "uArr" to '⇑',
            "uacute" to 'ú',
            "uarr" to '↑',
            "ucirc" to 'û',
            "ugrave" to 'ù',
            "uml" to '¨',
            "upsih" to 'ϒ',
            "upsilon" to 'υ',
            "uuml" to 'ü',
            "weierp" to '℘',
            "xi" to 'ξ',
            "yacute" to 'ý',
            "yen" to '¥',
            "yuml" to 'ÿ',
            "zeta" to 'ζ',
            "zwj" to '‍',
            "zwnj" to '‌',
        )

        private val entityValues: CharArrayMap<Char> = CharArrayMap<Char>(initValueMapForEntityValues.size, false).apply {
            for ((entity, value) in initValueMapForEntityValues) {
                put(entity, value)
                val upperCaseVariant = upperCaseVariantsAccepted[entity]
                if (upperCaseVariant != null) {
                    put(upperCaseVariant, value)
                }
            }
        }

        private const val INITIAL_INPUT_SEGMENT_SIZE: Int = 1024
        private const val BLOCK_LEVEL_START_TAG_REPLACEMENT: Char = '\n'
        private const val BLOCK_LEVEL_END_TAG_REPLACEMENT: Char = '\n'
        private const val BR_START_TAG_REPLACEMENT: Char = '\n'
        private const val BR_END_TAG_REPLACEMENT: Char = '\n'
        private const val SCRIPT_REPLACEMENT: Char = '\n'
        private const val STYLE_REPLACEMENT: Char = '\n'
        private const val REPLACEMENT_CHARACTER: Char = '\uFFFD'

        // line 5241

        // line 5318
        fun getInitialBufferSize(): Int {  // Package private, for testing purposes
            return ZZ_BUFFERSIZE
        }

        private class TextSegment : OpenStringBuilder {
            /** The position from which the next char will be read.  */
            var pos: Int = 0

            /** Wraps the given buffer and sets this.len to the given length.  */
            internal constructor(buffer: CharArray, length: Int) : super(buffer, length)

            /** Allocates an internal buffer of the given size.  */
            internal constructor(size: Int) : super(size)

            /** Sets len = 0 and pos = 0.  */
            fun clear() {
                reset()
                restart()
            }

            /** Sets pos = 0  */
            fun restart() {
                pos = 0
            }

            /** Returns the next char in the segment.  */
            fun nextChar(): Int {
                assert(!this.isRead) { "Attempting to read past the end of a segment." }
                return buf[pos++].code
            }

            val isRead: Boolean
                /** Returns true when all characters in the text segment have been read  */
                get() = pos >= len
        }
        // line 5357

        // line 5373
        /**
         * Translates raw input code points to DFA table row
         */
        private fun zzCMap(input: Int): Int {
            val offset = input and 255
            return if (offset == input) ZZ_CMAP_BLOCKS[offset] else ZZ_CMAP_BLOCKS[ZZ_CMAP_TOP[input shr 8] or offset]
        }

        // line 5560
        /**
         * Reports an error that occurred while scanning.
         *
         *
         * In a well-formed scanner (no or only correct usage of `yypushback(int)` and a
         * match-all fallback rule) this method will only be called with things that
         * "Can't Possibly Happen".
         *
         *
         * If this method is called, something is seriously wrong (e.g. a JFlex bug producing a faulty
         * scanner etc.).
         *
         *
         * Usual syntax/scanner level error handling should be done in error fallback rules.
         *
         * @param errorCode the code of the error message to display.
         */
        private fun zzScanError(errorCode: Int) {
            var message: String?
            try {
                message = ZZ_ERROR_MSG[errorCode]
            } catch (e: /*ArrayIndexOutOfBounds*/Exception) { // TODO this could cause bug based on java-kotlin error parity mismatch
                message = ZZ_ERROR_MSG[ZZ_UNKNOWN_ERROR]
            }

            throw Error(message)
        }
        // line 5583


        //TODO the end of companion object, come back here to add any static methods if needed
    }

    /** Input device.  */
    private lateinit var zzReader: Reader

    /** Current state of the DFA.  */
    private var zzState = 0

    /** Current lexical state.  */
    private var zzLexicalState = YYINITIAL

    /**
     * This buffer contains the current text to be matched and is the source of the [.yytext]
     * string.
     */
    private var zzBuffer: CharArray = CharArray(ZZ_BUFFERSIZE)

    /** Text position at the last accepting state.  */
    private var zzMarkedPos = 0

    /** Current text position in the buffer.  */
    private var zzCurrentPos = 0

    /** Marks the beginning of the [.yytext] string in the buffer.  */
    private var zzStartRead = 0

    /** Marks the last character in the buffer, that has been read from input.  */
    private var zzEndRead = 0

    /**
     * Whether the scanner is at the end of file.
     * @see .yyatEOF
     */
    private var zzAtEOF = false

    /**
     * The number of occupied positions in [.zzBuffer] beyond [.zzEndRead].
     *
     *
     * When a lead/high surrogate has been read from the input stream into the final
     * [.zzBuffer] position, this will have a value of 1; otherwise, it will have a value of 0.
     */
    private var zzFinalHighSurrogate = 0

    /** Number of newlines encountered up to the start of the matched text.  */
    @Suppress("unused")
    private var yyline = 0

    /** Number of characters from the last newline up to the start of the matched text.  */
    @Suppress("unused")
    private var yycolumn = 0

    /** Number of characters up to the start of the matched text.  */
    private var yychar: Long = 0

    /** Whether the scanner is currently at the beginning of a line.  */
    @Suppress("unused")
    private var zzAtBOL = true

    /** Whether the user-EOF-code has already been executed.  */
    private var zzEOFDone = false

    private var escapedTags: CharArraySet? = null
    private var inputStart: Long = 0
    private var cumulativeDiff = 0
    private var escapeBR = false
    private var escapeSCRIPT = false
    private var escapeSTYLE = false
    private var restoreState = 0
    private var previousRestoreState = 0
    private var outputCharCount = 0
    private var eofReturnValue = 0
    private val inputSegment
            : TextSegment = TextSegment(INITIAL_INPUT_SEGMENT_SIZE)
    private var outputSegment: TextSegment = inputSegment
    private val entitySegment: TextSegment = TextSegment(2)

    /**
     * Creates a new HTMLStripCharFilter over the provided Reader
     * with the specified start and end tags.
     * @param in Reader to strip html tags from.
     * @param escapedTags Tags in this set (both start and end tags)
     * will not be filtered out.
     */
    constructor(`in`: Reader, escapedTags: MutableSet<String>?) : this(`in`) {
        if (null != escapedTags) {
            for (tag in escapedTags) {
                if (tag.equals("BR", ignoreCase = true)) {
                    escapeBR = true
                } else if (tag.equals("SCRIPT", ignoreCase = true)) {
                    escapeSCRIPT = true
                } else if (tag.equals("STYLE", ignoreCase = true)) {
                    escapeSTYLE = true
                } else {
                    if (null == this.escapedTags) {
                        this.escapedTags = CharArraySet(16, true)
                    }
                    this.escapedTags!!.add(tag)
                }
            }
        }
    }

    @Throws(IOException::class)
    public override fun read(): Int {
        if (outputSegment.isRead) {
            if (zzAtEOF) {
                return -1
            }
            val ch: Int = nextChar()
            ++outputCharCount
            return ch
        }
        val ch: Int = outputSegment.nextChar()
        ++outputCharCount
        return ch
    }

    @Throws(IOException::class)
    public override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var off = off
        var i = 0
        while (i < len) {
            val ch = read()
            if (ch == -1) break
            cbuf[off++] = ch.toChar()
            ++i
        }
        return if (i > 0) i else (if (len == 0) 0 else -1)
    }

    @Throws(IOException::class)
    public override fun close() {
        yyclose()
    }

    // static int getInitialBufferSize() is defined in the companion object above
    // private static class TextSegment is defined in the companion object above

    /**
     * Creates a new scanner
     *
     * @param   in  the Reader to read input from.
     */
    constructor(`in`: Reader) : super(`in`) {
        this.zzReader = `in`
    }

    // private static int zzCMap(int input) is defined in the generated code below

    /**
     * Refills the input buffer.
     *
     * @return `false` iff there was new input.
     * @exception IOException  if any I/O-Error occurs
     */
    @Throws(IOException::class)
    private fun zzRefill(): Boolean {
        /* first: make room (if you can) */

        if (zzStartRead > 0) {
            zzEndRead += zzFinalHighSurrogate
            zzFinalHighSurrogate = 0
            System.arraycopy(
                zzBuffer, zzStartRead,
                zzBuffer, 0,
                zzEndRead - zzStartRead
            )

            /* translate stored positions */
            zzEndRead -= zzStartRead
            zzCurrentPos -= zzStartRead
            zzMarkedPos -= zzStartRead
            zzStartRead = 0
        }

        /* is the buffer big enough? */
        if (zzCurrentPos >= zzBuffer.size - zzFinalHighSurrogate) {
            /* if not: blow it up */
            val newBuffer = CharArray(zzBuffer.size * 2)
            System.arraycopy(zzBuffer, 0, newBuffer, 0, zzBuffer.size)
            zzBuffer = newBuffer
            zzEndRead += zzFinalHighSurrogate
            zzFinalHighSurrogate = 0
        }

        /* fill the buffer with new input */
        val requested = zzBuffer.size - zzEndRead
        val numRead: Int = zzReader.read(zzBuffer, zzEndRead, requested)

        /* not supposed to occur according to specification of Reader */
        if (numRead == 0) {
            throw IOException(
                "Reader returned 0 characters. See JFlex examples/zero-reader for a workaround."
            )
        }
        if (numRead > 0) {
            zzEndRead += numRead
            if (Character.isHighSurrogate(zzBuffer[zzEndRead - 1])) {
                if (numRead == requested) { // We requested too few chars to encode a full Unicode character
                    --zzEndRead
                    zzFinalHighSurrogate = 1
                } else {                    // There is room in the buffer for at least one more char
                    val c: Int = zzReader.read() // Expecting to read a paired low surrogate char
                    if (c == -1) {
                        return true
                    } else {
                        zzBuffer[zzEndRead++] = c.toChar()
                    }
                }
            }
            /* potentially more input available */
            return false
        }

        /* numRead < 0 ==> end of stream */
        return true
    }

    /**
     * Closes the input reader.
     *
     * @throws IOException if the reader could not be closed.
     */
    @Throws(IOException::class)
    private fun yyclose() {
        zzAtEOF = true // indicate end of file
        zzEndRead = zzStartRead // invalidate buffer

        if (zzReader != null) {
            zzReader.close()
        }
    }

    /**
     * Resets the scanner to read from a new input stream.
     *
     *
     * Does not close the old reader.
     *
     *
     * All internal variables are reset, the old input stream **cannot** be reused (internal
     * buffer is discarded and lost). Lexical state is set to `ZZ_INITIAL`.
     *
     *
     * Internal scan buffer is resized down to its initial length, if it has grown.
     *
     * @param reader The new input stream.
     */
    private fun yyreset(reader: Reader) {
        zzReader = reader
        zzEOFDone = false
        yyResetPosition()
        zzLexicalState = YYINITIAL
        if (zzBuffer.size > ZZ_BUFFERSIZE) {
            zzBuffer = CharArray(ZZ_BUFFERSIZE)
        }
    }

    /**
     * Resets the input position.
     */
    private fun yyResetPosition() {
        zzAtBOL = true
        zzAtEOF = false
        zzCurrentPos = 0
        zzMarkedPos = 0
        zzStartRead = 0
        zzEndRead = 0
        zzFinalHighSurrogate = 0
        yyline = 0
        yycolumn = 0
        yychar = 0L
    }


    /**
     * Returns whether the scanner has reached the end of the reader it reads from.
     *
     * @return whether the scanner has reached EOF.
     */
    private fun yyatEOF(): Boolean {
        return zzAtEOF
    }


    /**
     * Returns the current lexical state.
     *
     * @return the current lexical state.
     */
    private fun yystate(): Int {
        return zzLexicalState
    }

    /**
     * Enters a new lexical state.
     *
     * @param newState the new lexical state
     */
    private fun yybegin(newState: Int) {
        zzLexicalState = newState
    }


    /**
     * Returns the text matched by the current regular expression.
     *
     * @return the matched text.
     */
    private fun yytext(): String {
        return String.fromCharArray(zzBuffer, zzStartRead, zzMarkedPos - zzStartRead)
    }


    /**
     * Returns the character at the given position from the matched text.
     *
     *
     * It is equivalent to `yytext().charAt(pos)`, but faster.
     *
     * @param position the position of the character to fetch. A value from 0 to `yylength()-1`.
     *
     * @return the character at `position`.
     */
    private fun yycharat(position: Int): Char {
        return zzBuffer[zzStartRead + position]
    }


    /**
     * How many characters were matched.
     *
     * @return the length of the matched text region.
     */
    private fun yylength(): Int {
        return zzMarkedPos - zzStartRead
    }

    // private static void zzScanError(int errorCode) is defined in the generated code below

    /**
     * Pushes the specified amount of characters back into the input stream.
     *
     *
     * They will be read again by then next call of the scanning method.
     *
     * @param number the number of characters to be read again. This number must not be greater than
     * [.yylength].
     */
    private fun yypushback(number: Int) {
        if (number > yylength()) zzScanError(ZZ_PUSHBACK_2BIG)

        zzMarkedPos -= number
    }

    /**
     * Contains user EOF-code, which will be executed exactly once,
     * when the end of file is reached
     */
    private fun zzDoEOF() {
        if (!zzEOFDone) {
            zzEOFDone = true

            when (zzLexicalState) {
                SCRIPT, COMMENT, SCRIPT_COMMENT, STYLE, STYLE_COMMENT, SINGLE_QUOTED_STRING, DOUBLE_QUOTED_STRING, END_TAG_TAIL_EXCLUDE, END_TAG_TAIL_SUBSTITUTE, START_TAG_TAIL_EXCLUDE, SERVER_SIDE_INCLUDE, START_TAG_TAIL_SUBSTITUTE -> {
                    // Exclude
                    // add (length of input that won't be output) [ - (substitution length) = 0 ]
                    cumulativeDiff += (yychar - inputStart).toInt()
                    // position the correction at (already output length) [ + (substitution length) = 0 ]
                    addOffCorrectMap(outputCharCount, cumulativeDiff)
                    outputSegment.clear()
                    eofReturnValue = -1
                }

                CHARACTER_REFERENCE_TAIL -> {
                    // Substitute
                    // At end of file, allow char refs without semicolons
                    // add (length of input that won't be output) - (substitution length)
                    cumulativeDiff += inputSegment.length - outputSegment.length
                    // position the correction at (already output length) + (substitution length)
                    addOffCorrectMap(outputCharCount + outputSegment.length, cumulativeDiff)
                    eofReturnValue = if (!outputSegment.isRead) outputSegment.nextChar() else -1
                }

                BANG, CDATA, AMPERSAND, NUMERIC_CHARACTER, END_TAG_TAIL_INCLUDE, START_TAG_TAIL_INCLUDE, LEFT_ANGLE_BRACKET, LEFT_ANGLE_BRACKET_SLASH, LEFT_ANGLE_BRACKET_SPACE -> {
                    // Include
                    outputSegment = inputSegment
                    eofReturnValue = if (!outputSegment.isRead) outputSegment.nextChar() else -1
                }

                else -> {
                    eofReturnValue = -1
                }
            }
        }
    }

    /**
     * Resumes scanning until the next regular expression is matched, the end of input is encountered
     * or an I/O-Error occurs.
     *
     * @return the next token.
     * @exception IOException if any I/O-Error occurs.
     */
    @Throws(IOException::class)
    private fun nextChar(): Int {
        var zzInput: Int
        var zzAction: Int

        // cached fields:
        var zzCurrentPosL: Int
        var zzMarkedPosL: Int
        var zzEndReadL = zzEndRead
        var zzBufferL = zzBuffer

        val zzTransL = ZZ_TRANS
        val zzRowMapL = ZZ_ROWMAP
        val zzAttrL = ZZ_ATTRIBUTE

        while (true) {
            zzMarkedPosL = zzMarkedPos

            yychar += (zzMarkedPosL - zzStartRead).toLong()

            zzAction = -1

            zzStartRead = zzMarkedPosL
            zzCurrentPos = zzStartRead
            zzCurrentPosL = zzCurrentPos

            zzState = ZZ_LEXSTATE!![zzLexicalState]

            // set up zzAction for empty match case:
            var zzAttributes = zzAttrL[zzState]
            if ((zzAttributes and 1) == 1) {
                zzAction = zzState
            }

            zzForAction@ while (true) {
                if (zzCurrentPosL < zzEndReadL) {
                  zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL, zzEndReadL)
                  zzCurrentPosL += Character.charCount(zzInput)
                } else if (zzAtEOF) {
                  zzInput = YYEOF
                  break@zzForAction
                } else {
                  // store back cached positions
                  zzCurrentPos = zzCurrentPosL
                  zzMarkedPos = zzMarkedPosL
                  val eof = zzRefill()
                  // get translated positions and possibly new buffer
                  zzCurrentPosL = zzCurrentPos
                  zzMarkedPosL = zzMarkedPos
                  zzBufferL = zzBuffer
                  zzEndReadL = zzEndRead
                  if (eof) {
                      zzInput = YYEOF
                      break@zzForAction
                  } else {
                      zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL, zzEndReadL)
                      zzCurrentPosL += Character.charCount(zzInput)
                  }
                }

              val zzNext = zzTransL[zzRowMapL[zzState] + zzCMap(zzInput)]
              if (zzNext == -1) break@zzForAction
              zzState = zzNext

              zzAttributes = zzAttrL[zzState]

              if ((zzAttributes and 1) == 1) {
                zzAction = zzState
                zzMarkedPosL = zzCurrentPosL
                if ((zzAttributes and 8) == 8) break@zzForAction
              }
            }

            // store back cached position
            zzMarkedPos = zzMarkedPosL

            if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
                zzAtEOF = true
                zzDoEOF()
                run {
                    return eofReturnValue
                }
            } else {
                when (if (zzAction < 0) zzAction else ZZ_ACTION[zzAction]) {
                    1 -> {
                        if (yylength() == 1) {
                            return zzBuffer[zzStartRead].code
                        } else {
                            outputSegment.append(yytext())
                            return outputSegment.nextChar()
                        }
                    }

                    55 -> {}
                    2 -> {
                        inputStart = yychar
                        inputSegment.clear()
                        inputSegment.append('&')
                        yybegin(AMPERSAND)
                    }

                    56 -> {}
                    3 -> {
                        inputStart = yychar
                        inputSegment.clear()
                        inputSegment.append('<')
                        yybegin(LEFT_ANGLE_BRACKET)
                    }

                    57 -> {}
                    4 -> {
                        yypushback(yylength())
                        outputSegment = inputSegment
                        outputSegment.restart()
                        yybegin(YYINITIAL)
                        return outputSegment.nextChar()
                    }

                    58 -> {}
                    5 -> {
                        inputSegment.append('#')
                        yybegin(NUMERIC_CHARACTER)
                    }

                    59 -> {}
                    6 -> {
                        val matchLength = yylength()
                        inputSegment.write(zzBuffer, zzStartRead, matchLength)
                        if (matchLength <= 7) { // 0x10FFFF = 1114111: max 7 decimal chars
                            val decimalCharRef = yytext()
                            var codePoint = 0
                            try {
                                codePoint = decimalCharRef.toInt()
                            } catch (e: Exception) {
                                assert(false) { "Exception parsing code point '" + decimalCharRef + "'" }
                            }
                            if (codePoint <= 0x10FFFF) {
                                outputSegment = entitySegment
                                outputSegment.clear()
                                if (codePoint >= Character.MIN_SURROGATE.code
                                    && codePoint <= Character.MAX_SURROGATE.code
                                ) {
                                    outputSegment.unsafeWrite(REPLACEMENT_CHARACTER)
                                } else {
                                    outputSegment.setLength(Character.toChars(codePoint, outputSegment.getArray(), 0))
                                }
                                yybegin(CHARACTER_REFERENCE_TAIL)
                            } else {
                                outputSegment = inputSegment
                                yybegin(YYINITIAL)
                                return outputSegment.nextChar()
                            }
                        } else {
                            outputSegment = inputSegment
                            yybegin(YYINITIAL)
                            return outputSegment.nextChar()
                        }
                    }

                    60 -> {}
                    7 -> {
                        // add (previously matched input length) + (this match length) - (substitution length)
                        cumulativeDiff += inputSegment.length + yylength() - outputSegment.length
                        // position the correction at (already output length) + (substitution length)
                        addOffCorrectMap(outputCharCount + outputSegment.length, cumulativeDiff)
                        yybegin(YYINITIAL)
                        return outputSegment.nextChar()
                    }

                    61 -> {}
                    8 -> {
                        inputSegment.write(zzBuffer, zzStartRead, yylength())
                        yybegin(LEFT_ANGLE_BRACKET_SPACE)
                    }

                    62 -> {}
                    9 -> {
                        inputSegment.append('!')
                        yybegin(BANG)
                    }

                    63 -> {}
                    10 -> {
                        inputSegment.append('/')
                        yybegin(LEFT_ANGLE_BRACKET_SLASH)
                    }

                    64 -> {}
                    11 -> {
                        inputSegment.write(zzBuffer, zzStartRead, yylength())
                        if (null != escapedTags
                            && escapedTags!!.contains(zzBuffer, zzStartRead, yylength())
                        ) {
                            yybegin(START_TAG_TAIL_INCLUDE)
                        } else {
                            yybegin(START_TAG_TAIL_SUBSTITUTE)
                        }
                    }

                    65 -> {}
                    12 -> {
                        inputSegment.write(zzBuffer, zzStartRead, yylength())
                        if (null != escapedTags
                            && escapedTags!!.contains(zzBuffer, zzStartRead, yylength())
                        ) {
                            yybegin(START_TAG_TAIL_INCLUDE)
                        } else {
                            yybegin(START_TAG_TAIL_EXCLUDE)
                        }
                    }

                    66 -> {}
                    13 -> {
                        inputSegment.append(yytext())
                    }

                    67 -> {}
                    14 -> {
                        // add (previously matched input length) + (this match length) [ - (substitution length) = 0 ]
                        cumulativeDiff += inputSegment.length + yylength()
                        // position the correction at (already output length) [ + (substitution length) = 0 ]
                        addOffCorrectMap(outputCharCount, cumulativeDiff)
                        inputSegment.clear()
                        yybegin(YYINITIAL)
                    }

                    68 -> {}
                    15 -> {}
                    69 -> {}
                    16 -> {
                        restoreState = SCRIPT_COMMENT
                        yybegin(DOUBLE_QUOTED_STRING)
                    }

                    70 -> {}
                    17 -> {
                        restoreState = SCRIPT_COMMENT
                        yybegin(SINGLE_QUOTED_STRING)
                    }

                    71 -> {}
                    18 -> {
                        inputSegment.write(zzBuffer, zzStartRead, yylength())
                    }

                    72 -> {}
                    19 -> {
                        inputSegment.write(zzBuffer, zzStartRead, yylength())
                        if (null != escapedTags
                            && escapedTags!!.contains(zzBuffer, zzStartRead, yylength())
                        ) {
                            yybegin(END_TAG_TAIL_INCLUDE)
                        } else {
                            yybegin(END_TAG_TAIL_SUBSTITUTE)
                        }
                    }

                    73 -> {}
                    20 -> {
                        inputSegment.write(zzBuffer, zzStartRead, yylength())
                        if (null != escapedTags
                            && escapedTags!!.contains(zzBuffer, zzStartRead, yylength())
                        ) {
                            yybegin(END_TAG_TAIL_INCLUDE)
                        } else {
                            yybegin(END_TAG_TAIL_EXCLUDE)
                        }
                    }

                    74 -> {}
                    21 -> {
                        if (yylength() == 1) {
                            return zzBuffer[zzStartRead].code
                        } else {
                            outputSegment.append(yytext())
                            return outputSegment.nextChar()
                        }
                    }

                    75 -> {}
                    22 -> {
                        previousRestoreState = restoreState
                        restoreState = SERVER_SIDE_INCLUDE
                        yybegin(DOUBLE_QUOTED_STRING)
                    }

                    76 -> {}
                    23 -> {
                        previousRestoreState = restoreState
                        restoreState = SERVER_SIDE_INCLUDE
                        yybegin(SINGLE_QUOTED_STRING)
                    }

                    77 -> {}
                    24 -> {
                        yybegin(restoreState)
                        restoreState = previousRestoreState
                    }

                    78 -> {}
                    25 -> {
                        inputSegment.write(zzBuffer, zzStartRead, yylength())
                        outputSegment = inputSegment
                        yybegin(YYINITIAL)
                        return outputSegment.nextChar()
                    }

                    79 -> {}
                    26 -> {
                        // add (previously matched input length) + (this match length) - (substitution length)
                        cumulativeDiff += inputSegment.length + yylength() - 1
                        // position the correction at (already output length) + (substitution length)
                        addOffCorrectMap(outputCharCount + 1, cumulativeDiff)
                        inputSegment.clear()
                        yybegin(YYINITIAL)
                        return BLOCK_LEVEL_END_TAG_REPLACEMENT.code
                    }

                    80 -> {}
                    27 -> {
                        // add (previously matched input length) + (this match length) [ - (substitution length) = 0 ]
                        cumulativeDiff += inputSegment.length + yylength()
                        // position the correction at (already output length) [ + (substitution length) = 0 ]
                        addOffCorrectMap(outputCharCount, cumulativeDiff)
                        inputSegment.clear()
                        outputSegment = inputSegment
                        yybegin(YYINITIAL)
                    }

                    81 -> {}
                    28 -> {
                        // add (previously matched input length) + (this match length) - (substitution length)
                        cumulativeDiff += inputSegment.length + yylength() - 1
                        // position the correction at (already output length) + (substitution length)
                        addOffCorrectMap(outputCharCount + 1, cumulativeDiff)
                        inputSegment.clear()
                        yybegin(YYINITIAL)
                        return BLOCK_LEVEL_START_TAG_REPLACEMENT.code
                    }

                    82 -> {}
                    29 -> {
                        restoreState = STYLE_COMMENT
                        yybegin(DOUBLE_QUOTED_STRING)
                    }

                    83 -> {}
                    30 -> {
                        restoreState = STYLE_COMMENT
                        yybegin(SINGLE_QUOTED_STRING)
                    }

                    84 -> {}
                    31 -> {
                        val length = yylength()
                        inputSegment.write(zzBuffer, zzStartRead, length)
                        entitySegment.clear()
                        val ch: Char = entityValues.get(zzBuffer, zzStartRead, length)!!
                        entitySegment.append(ch)
                        outputSegment = entitySegment
                        yybegin(CHARACTER_REFERENCE_TAIL)
                    }

                    85 -> {}
                    32 -> {
                        val matchLength = yylength()
                        inputSegment.write(zzBuffer, zzStartRead, matchLength)
                        if (matchLength <= 6) { // 10FFFF: max 6 hex chars
                            val hexCharRef = String.fromCharArray(zzBuffer, zzStartRead + 1, matchLength - 1)
                            var codePoint = 0
                            try {
                                codePoint = hexCharRef.toInt(16)
                            } catch (e: Exception) {
                                assert(false) { "Exception parsing hex code point '$hexCharRef'" }
                            }
                            if (codePoint <= 0x10FFFF) {
                                outputSegment = entitySegment
                                outputSegment.clear()
                                if (codePoint >= Character.MIN_SURROGATE.code
                                    && codePoint <= Character.MAX_SURROGATE.code
                                ) {
                                    outputSegment.unsafeWrite(REPLACEMENT_CHARACTER)
                                } else {
                                    outputSegment.setLength(Character.toChars(codePoint, outputSegment.getArray(), 0))
                                }
                                yybegin(CHARACTER_REFERENCE_TAIL)
                            } else {
                                outputSegment = inputSegment
                                yybegin(YYINITIAL)
                                return outputSegment.nextChar()
                            }
                        } else {
                            outputSegment = inputSegment
                            yybegin(YYINITIAL)
                            return outputSegment.nextChar()
                        }
                    }

                    86 -> {}
                    33 -> {
                        if (inputSegment.length > 2) { // Chars between "<!" and "--" - this is not a comment
                            inputSegment.append(yytext())
                        } else {
                            yybegin(COMMENT)
                        }
                    }

                    87 -> {}
                    34 -> {
                        yybegin(YYINITIAL)
                        if (escapeBR) {
                            inputSegment.write(zzBuffer, zzStartRead, yylength())
                            outputSegment = inputSegment
                            return outputSegment.nextChar()
                        } else {
                            // add (previously matched input length) + (this match length) - (substitution length)
                            cumulativeDiff += inputSegment.length + yylength() - 1
                            // position the correction at (already output length) + (substitution length)
                            addOffCorrectMap(outputCharCount + 1, cumulativeDiff)
                            inputSegment.reset()
                            return BR_START_TAG_REPLACEMENT.code
                        }
                    }

                    88 -> {}
                    35 -> {
                        // add (previously matched input length) + (this match length) [ - (substitution length) = 0]
                        cumulativeDiff += (yychar - inputStart + yylength()).toInt()
                        // position the correction at (already output length) [ + (substitution length) = 0]
                        addOffCorrectMap(outputCharCount, cumulativeDiff)
                        inputSegment.clear()
                        yybegin(YYINITIAL)
                    }

                    89 -> {}
                    36 -> {
                        yybegin(SCRIPT)
                    }

                    90 -> {}
                    37 -> {
                        yybegin(YYINITIAL)
                        if (escapeBR) {
                            inputSegment.write(zzBuffer, zzStartRead, yylength())
                            outputSegment = inputSegment
                            return outputSegment.nextChar()
                        } else {
                            // add (previously matched input length) + (this match length) - (substitution length)
                            cumulativeDiff += inputSegment.length + yylength() - 1
                            // position the correction at (already output length) + (substitution length)
                            addOffCorrectMap(outputCharCount + 1, cumulativeDiff)
                            inputSegment.reset()
                            return BR_END_TAG_REPLACEMENT.code
                        }
                    }

                    91 -> {}
                    38 -> {
                        // add (this match length) [ - (substitution length) = 0 ]
                        cumulativeDiff += yylength()
                        // position the correction at (already output length) [ + (substitution length) = 0 ]
                        addOffCorrectMap(outputCharCount, cumulativeDiff)
                        yybegin(YYINITIAL)
                    }

                    92 -> {}
                    39 -> {
                        yybegin(restoreState)
                    }

                    93 -> {}
                    40 -> {
                        yybegin(STYLE)
                    }

                    94 -> {}
                    41 -> {
                        yybegin(SCRIPT_COMMENT)
                    }

                    95 -> {}
                    42 -> {
                        yybegin(STYLE_COMMENT)
                    }

                    96 -> {}
                    43 -> {
                        restoreState = COMMENT
                        yybegin(SERVER_SIDE_INCLUDE)
                    }

                    97 -> {}
                    44 -> {
                        restoreState = SCRIPT_COMMENT
                        yybegin(SERVER_SIDE_INCLUDE)
                    }

                    98 -> {}
                    45 -> {
                        restoreState = STYLE_COMMENT
                        yybegin(SERVER_SIDE_INCLUDE)
                    }

                    99 -> {}
                    46 -> {
                        yybegin(STYLE)
                        if (escapeSTYLE) {
                            inputSegment.write(zzBuffer, zzStartRead, yylength())
                            outputSegment = inputSegment
                            inputStart += (1 + yylength()).toLong()
                            return outputSegment.nextChar()
                        }
                    }

                    100 -> {}
                    47 -> {
                        yybegin(SCRIPT)
                        if (escapeSCRIPT) {
                            inputSegment.write(zzBuffer, zzStartRead, yylength())
                            outputSegment = inputSegment
                            inputStart += (1 + yylength()).toLong()
                            return outputSegment.nextChar()
                        }
                    }

                    101 -> {}
                    48 -> {
                        if (inputSegment.length > 2) { // Chars between "<!" and "[CDATA[" - this is not a CDATA section
                            inputSegment.append(yytext())
                        } else {
                            // add (previously matched input length) + (this match length) [ - (substitution length) = 0 ]
                            cumulativeDiff += inputSegment.length + yylength()
                            // position the correction at (already output length) [ + (substitution length) = 0 ]
                            addOffCorrectMap(outputCharCount, cumulativeDiff)
                            inputSegment.clear()
                            yybegin(CDATA)
                        }
                    }

                    102 -> {}
                    49 -> {
                        inputSegment.clear()
                        yybegin(YYINITIAL)
                        // add (previously matched input length) -- current match and substitution handled below
                        cumulativeDiff += (yychar - inputStart).toInt()
                        // position the offset correction at (already output length) -- substitution handled below
                        var offsetCorrectionPos = outputCharCount
                        val returnValue: Int
                        if (escapeSTYLE) {
                            inputSegment.write(zzBuffer, zzStartRead, yylength())
                            outputSegment = inputSegment
                            returnValue = outputSegment.nextChar()
                        } else {
                            // add (this match length) - (substitution length)
                            cumulativeDiff += yylength() - 1
                            // add (substitution length)
                            ++offsetCorrectionPos
                            returnValue = STYLE_REPLACEMENT.code
                        }
                        addOffCorrectMap(offsetCorrectionPos, cumulativeDiff)
                        return returnValue
                    }

                    103 -> {}
                    50 -> {
                        inputSegment.clear()
                        yybegin(YYINITIAL)
                        // add (previously matched input length) -- current match and substitution handled below
                        cumulativeDiff += (yychar - inputStart).toInt()
                        // position at (already output length) -- substitution handled below
                        var offsetCorrectionPos = outputCharCount
                        val returnValue: Int
                        if (escapeSCRIPT) {
                            inputSegment.write(zzBuffer, zzStartRead, yylength())
                            outputSegment = inputSegment
                            returnValue = outputSegment.nextChar()
                        } else {
                            // add (this match length) - (substitution length)
                            cumulativeDiff += yylength() - 1
                            // add (substitution length)
                            ++offsetCorrectionPos
                            returnValue = SCRIPT_REPLACEMENT.code
                        }
                        addOffCorrectMap(offsetCorrectionPos, cumulativeDiff)
                        return returnValue
                    }

                    104 -> {}
                    51 -> {
                        // Handle paired UTF-16 surrogates.
                        val surrogatePair = yytext()
                        var highSurrogate = '\u0000'
                        try { // High surrogates are in decimal range [55296, 56319]
                            highSurrogate = surrogatePair.substring(1, 6).toInt().toChar()
                        } catch (e: Exception) { // should never happen
                            assert(false) {
                                ("Exception parsing high surrogate '"
                                        + surrogatePair.substring(1, 6) + "'")
                            }
                        }
                        if (Character.isHighSurrogate(highSurrogate)) {
                            var lowSurrogate = '\u0000'
                            try { // Low surrogates are in decimal range [56320, 57343]
                                lowSurrogate = surrogatePair.substring(9, 14).toInt().toChar()
                            } catch (e: Exception) { // should never happen
                                assert(false) {
                                    ("Exception parsing low surrogate '"
                                            + surrogatePair.substring(9, 14) + "'")
                                }
                            }
                            if (Character.isLowSurrogate(lowSurrogate)) {
                                outputSegment = entitySegment
                                outputSegment.clear()
                                outputSegment.unsafeWrite(lowSurrogate)
                                // add (previously matched input length) + (this match length) - (substitution length)
                                cumulativeDiff += inputSegment.length + yylength() - 2
                                // position the correction at (already output length) + (substitution length)
                                addOffCorrectMap(outputCharCount + 2, cumulativeDiff)
                                inputSegment.clear()
                                yybegin(YYINITIAL)
                                return highSurrogate.code
                            }
                        }
                        yypushback(surrogatePair.length - 1) // Consume only '#'
                        inputSegment.append('#')
                        yybegin(NUMERIC_CHARACTER)
                    }

                    105 -> {}
                    52 -> {
                        // Handle paired UTF-16 surrogates.
                        val surrogatePair = yytext()
                        var highSurrogate = '\u0000'
                        try { // High surrogates are in decimal range [55296, 56319]
                            highSurrogate = surrogatePair.substring(1, 6).toInt().toChar()
                        } catch (e: Exception) { // should never happen
                            assert(false) {
                                ("Exception parsing high surrogate '"
                                        + surrogatePair.substring(1, 6) + "'")
                            }
                        }
                        if (Character.isHighSurrogate(highSurrogate)) {
                            outputSegment = entitySegment
                            outputSegment.clear()
                            try {
                                outputSegment.unsafeWrite(surrogatePair.substring(10, 14).toInt(16).toChar())
                            } catch (e: Exception) { // should never happen
                                assert(false) {
                                    ("Exception parsing low surrogate '"
                                            + surrogatePair.substring(10, 14) + "'")
                                }
                            }
                            // add (previously matched input length) + (this match length) - (substitution length)
                            cumulativeDiff += inputSegment.length + yylength() - 2
                            // position the correction at (already output length) + (substitution length)
                            addOffCorrectMap(outputCharCount + 2, cumulativeDiff)
                            inputSegment.clear()
                            yybegin(YYINITIAL)
                            return highSurrogate.code
                        }
                        yypushback(surrogatePair.length - 1) // Consume only '#'
                        inputSegment.append('#')
                        yybegin(NUMERIC_CHARACTER)
                    }

                    106 -> {}
                    53 -> {
                        // Handle paired UTF-16 surrogates.
                        val surrogatePair = yytext()
                        var highSurrogate = '\u0000'
                        var lowSurrogate = '\u0000'
                        try {
                            highSurrogate = surrogatePair.substring(2, 6).toInt(16).toChar()
                        } catch (e: Exception) { // should never happen
                            assert(false) {
                                ("Exception parsing high surrogate '"
                                        + surrogatePair.substring(2, 6) + "'")
                            }
                        }
                        try { // Low surrogates are in decimal range [56320, 57343]
                            lowSurrogate = surrogatePair.substring(9, 14).toInt().toChar()
                        } catch (e: Exception) { // should never happen
                            assert(false) {
                                ("Exception parsing low surrogate '"
                                        + surrogatePair.substring(9, 14) + "'")
                            }
                        }
                        if (Character.isLowSurrogate(lowSurrogate)) {
                            outputSegment = entitySegment
                            outputSegment.clear()
                            outputSegment.unsafeWrite(lowSurrogate)
                            // add (previously matched input length) + (this match length) - (substitution length)
                            cumulativeDiff += inputSegment.length + yylength() - 2
                            // position the correction at (already output length) + (substitution length)
                            addOffCorrectMap(outputCharCount + 2, cumulativeDiff)
                            inputSegment.clear()
                            yybegin(YYINITIAL)
                            return highSurrogate.code
                        }
                        yypushback(surrogatePair.length - 1) // Consume only '#'
                        inputSegment.append('#')
                        yybegin(NUMERIC_CHARACTER)
                    }

                    107 -> {}
                    54 -> {
                        // Handle paired UTF-16 surrogates.
                        outputSegment = entitySegment
                        outputSegment.clear()
                        val surrogatePair = yytext()
                        var highSurrogate = '\u0000'
                        try {
                            highSurrogate = surrogatePair.substring(2, 6).toInt(16).toChar()
                        } catch (e: Exception) { // should never happen
                            assert(false) {
                                ("Exception parsing high surrogate '"
                                        + surrogatePair.substring(2, 6) + "'")
                            }
                        }
                        try {
                            outputSegment.unsafeWrite(surrogatePair.substring(10, 14).toInt(16).toChar())
                        } catch (e: Exception) { // should never happen
                            assert(false) {
                                ("Exception parsing low surrogate '"
                                        + surrogatePair.substring(10, 14) + "'")
                            }
                        }
                        // add (previously matched input length) + (this match length) - (substitution length)
                        cumulativeDiff += inputSegment.length + yylength() - 2
                        // position the correction at (already output length) + (substitution length)
                        addOffCorrectMap(outputCharCount + 2, cumulativeDiff)
                        inputSegment.clear()
                        yybegin(YYINITIAL)
                        return highSurrogate.code
                    }

                    108 -> {}
                    else -> zzScanError(ZZ_NO_MATCH)
                }
            }
        }
    }

}