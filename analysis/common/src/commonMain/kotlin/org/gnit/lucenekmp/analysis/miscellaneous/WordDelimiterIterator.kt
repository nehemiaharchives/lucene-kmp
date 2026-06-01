package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.fromCharArray

/**
 * A BreakIterator-like API for iterating over subwords in text, according to
 * WordDelimiterGraphFilter rules.
 *
 * @lucene.internal
 */
class WordDelimiterIterator(
    private val charTypeTable: ByteArray,
    /**
     * If false, causes case changes to be ignored (subwords will only be generated given
     * SUBWORD_DELIM tokens). (Defaults to true)
     */
    val splitOnCaseChange: Boolean,
    /**
     * If false, causes numeric changes to be ignored (subwords will only be generated given
     * SUBWORD_DELIM tokens). (Defaults to true)
     */
    val splitOnNumerics: Boolean,
    /**
     * If true, causes trailing "'s" to be removed for each subword. (Defaults to true)
     *
     * "O'Neil's" => "O", "Neil"
     */
    val stemEnglishPossessive: Boolean
) {
    companion object {
        const val LOWER = 0x01
        const val UPPER = 0x02
        const val DIGIT = 0x04
        const val SUBWORD_DELIM = 0x08

        // combinations: for testing, not for setting bits
        const val ALPHA = 0x03
        const val ALPHANUM = 0x07

        /** Indicates the end of iteration */
        const val DONE = -1

        val DEFAULT_WORD_DELIM_TABLE: ByteArray

        init {
            val tab = ByteArray(256)
            for (i in 0..255) {
                var code: Byte = 0
                if (Character.isLowerCase(i)) {
                    code = (code.toInt() or LOWER).toByte()
                } else if (Character.getType(i) == Character.UPPERCASE_LETTER.toInt()) {
                    code = (code.toInt() or UPPER).toByte()
                } else if (Character.isDigit(i)) {
                    code = (code.toInt() or DIGIT).toByte()
                }
                if (code.toInt() == 0) {
                    code = SUBWORD_DELIM.toByte()
                }
                tab[i] = code
            }
            DEFAULT_WORD_DELIM_TABLE = tab
        }

        /**
         * Computes the type of the given character
         *
         * @param ch Character whose type is to be determined
         * @return Type of the character
         */
        fun getType(ch: Int): Byte {
            return when (Character.getType(ch)) {
                Character.UPPERCASE_LETTER.toInt() -> UPPER.toByte()
                Character.LOWERCASE_LETTER.toInt() -> LOWER.toByte()
                Character.TITLECASE_LETTER.toInt(),
                Character.MODIFIER_LETTER.toInt(),
                Character.OTHER_LETTER.toInt(),
                Character.NON_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt() -> ALPHA.toByte()
                Character.DECIMAL_DIGIT_NUMBER.toInt(),
                Character.LETTER_NUMBER.toInt(),
                Character.OTHER_NUMBER.toInt() -> DIGIT.toByte()
                Character.SURROGATE.toInt() -> (ALPHA or DIGIT).toByte()
                else -> SUBWORD_DELIM.toByte()
            }
        }

        /**
         * Checks if the given word type includes [ALPHA]
         *
         * @param type Word type to check
         * @return `true` if the type contains ALPHA, `false` otherwise
         */
        fun isAlpha(type: Int): Boolean {
            return (type and ALPHA) != 0
        }

        /**
         * Checks if the given word type includes [DIGIT]
         *
         * @param type Word type to check
         * @return `true` if the type contains DIGIT, `false` otherwise
         */
        fun isDigit(type: Int): Boolean {
            return (type and DIGIT) != 0
        }

        /**
         * Checks if the given word type includes [SUBWORD_DELIM]
         *
         * @param type Word type to check
         * @return `true` if the type contains SUBWORD_DELIM, `false` otherwise
         */
        fun isSubwordDelim(type: Int): Boolean {
            return (type and SUBWORD_DELIM) != 0
        }

        /**
         * Checks if the given word type includes [UPPER]
         *
         * @param type Word type to check
         * @return `true` if the type contains UPPER, `false` otherwise
         */
        fun isUpper(type: Int): Boolean {
            return (type and UPPER) != 0
        }
    }

    var text: CharArray = CharArray(0)
    var length = 0

    /** start position of text, excluding leading delimiters */
    var startBounds = 0

    /** end position of text, excluding trailing delimiters */
    var endBounds = 0

    /** Beginning of subword */
    var current = 0

    /** End of subword */
    var end = 0

    /* does this string end with a possessive such as 's */
    private var hasFinalPossessive = false

    /** if true, need to skip over a possessive found in the last call to next() */
    private var skipPossessive = false

    override fun toString(): String {
        if (end == DONE) {
            return "DONE"
        }
        return String.fromCharArray(text, current, end - current) + " [" + current + "-" + end + "] type=" + type().toString(16)
    }

    /**
     * Advance to the next subword in the string.
     *
     * @return index of the next subword, or [DONE] if all subwords have been returned
     */
    fun next(): Int {
        current = end
        if (current == DONE) {
            return DONE
        }

        if (skipPossessive) {
            current += 2
            skipPossessive = false
        }

        var lastType = 0
        while (current < endBounds && isSubwordDelim(charType(text[current].code).toInt().also { lastType = it })) {
            current++
        }

        if (current >= endBounds) {
            end = DONE
            return DONE
        }

        end = current + 1
        while (end < endBounds) {
            val type = charType(text[end].code)
            if (isBreak(lastType, type.toInt())) {
                break
            }
            lastType = type.toInt()
            end++
        }

        if (end < endBounds - 1 && endsWithPossessive(end + 2)) {
            skipPossessive = true
        }

        return end
    }

    /**
     * Return the type of the current subword. This currently uses the type of the first character in
     * the subword.
     *
     * @return type of the current word
     */
    fun type(): Int {
        if (end == DONE) {
            return 0
        }

        return when (val type = charType(text[current].code).toInt()) {
            LOWER, UPPER -> ALPHA
            else -> type
        }
    }

    /**
     * Reset the text to a new value, and reset all state
     *
     * @param text New text
     * @param length length of the text
     */
    fun setText(text: CharArray, length: Int) {
        this.text = text
        this.length = length
        this.endBounds = length
        current = 0
        startBounds = 0
        end = 0
        skipPossessive = false
        hasFinalPossessive = false
        setBounds()
    }

    /**
     * Determines whether the transition from lastType to type indicates a break
     *
     * @param lastType Last subword type
     * @param type Current subword type
     * @return `true` if the transition indicates a break, `false` otherwise
     */
    private fun isBreak(lastType: Int, type: Int): Boolean {
        if ((type and lastType) != 0) {
            return false
        }

        if (!splitOnCaseChange && isAlpha(lastType) && isAlpha(type)) {
            return false
        } else if (isUpper(lastType) && isAlpha(type)) {
            return false
        } else if (!splitOnNumerics && ((isAlpha(lastType) && isDigit(type)) || (isDigit(lastType) && isAlpha(type)))) {
            return false
        }

        return true
    }

    /**
     * Determines if the current word contains only one subword. Note, it could be potentially
     * surrounded by delimiters
     *
     * @return `true` if the current word contains only one subword, `false` otherwise
     */
    fun isSingleWord(): Boolean {
        return if (hasFinalPossessive) {
            current == startBounds && end == endBounds - 2
        } else {
            current == startBounds && end == endBounds
        }
    }

    /**
     * Set the internal word bounds (remove leading and trailing delimiters). Note, if a possessive is
     * found, don't remove it yet, simply note it.
     */
    private fun setBounds() {
        while (startBounds < length && isSubwordDelim(charType(text[startBounds].code).toInt())) {
            startBounds++
        }

        while (endBounds > startBounds && isSubwordDelim(charType(text[endBounds - 1].code).toInt())) {
            endBounds--
        }
        if (endsWithPossessive(endBounds)) {
            hasFinalPossessive = true
        }
        current = startBounds
    }

    /**
     * Determines if the text at the given position indicates an English possessive which should be
     * removed
     *
     * @param pos Position in the text to check if it indicates an English possessive
     * @return `true` if the text at the position indicates an English possessive, `false`
     *     otherwise
     */
    private fun endsWithPossessive(pos: Int): Boolean {
        return stemEnglishPossessive &&
            pos > 2 &&
            text[pos - 2] == '\'' &&
            (text[pos - 1] == 's' || text[pos - 1] == 'S') &&
            isAlpha(charType(text[pos - 3].code).toInt()) &&
            (pos == endBounds || isSubwordDelim(charType(text[pos].code).toInt()))
    }

    /**
     * Determines the type of the given character
     *
     * @param ch Character whose type is to be determined
     * @return Type of the character
     */
    private fun charType(ch: Int): Byte {
        if (ch < charTypeTable.size) {
            return charTypeTable[ch]
        }
        return getType(ch)
    }
}
