package org.gnit.lucenekmp.jdkport

/**
 * port of [java.lang.Character](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/Character.html)
 */
class Character {

    companion object {

        /**
         * The minimum radix available for conversion to and from strings.
         * The constant value of this field is the smallest value permitted
         * for the radix argument in radix-conversion methods such as the
         * `digit` method, the `forDigit` method, and the
         * `toString` method of class `Integer`.
         *
         * @see Character.digit
         * @see Character.forDigit
         * @see Integer.toString
         * @see Integer.valueOf
         */
        const val MIN_RADIX: Int = 2

        /**
         * The maximum radix available for conversion to and from strings.
         * The constant value of this field is the largest value permitted
         * for the radix argument in radix-conversion methods such as the
         * `digit` method, the `forDigit` method, and the
         * `toString` method of class `Integer`.
         *
         * @see Character.digit
         * @see Character.forDigit
         * @see Integer.toString
         * @see Integer.valueOf
         */
        const val MAX_RADIX: Int = 36


        /**
         * The constant value of this field is the smallest value of type
         * `char`, `'\u005Cu0000'`.
         *
         * @since   1.0.2
         */
        const val MIN_VALUE: Char = '\u0000'

        /**
         * The constant value of this field is the largest value of type
         * `char`, `'\u005CuFFFF'`.
         *
         * @since   1.0.2
         */
        const val MAX_VALUE: Char = '\uFFFF'


        const val MIN_CODE_POINT: Int = 0x000000
        const val MAX_CODE_POINT: Int = 0X10FFFF
        const val MIN_SUPPLEMENTARY_CODE_POINT: Int = 0x010000

        /**
         * General category "Cn" in the Unicode specification.
         * @since   1.1
         */
        const val UNASSIGNED: Byte = 0

        /**
         * General category "Lu" in the Unicode specification.
         * @since   1.1
         */
        const val UPPERCASE_LETTER: Byte = 1

        /**
         * General category "Ll" in the Unicode specification.
         * @since   1.1
         */
        const val LOWERCASE_LETTER: Byte = 2

        /**
         * General category "Lt" in the Unicode specification.
         * @since   1.1
         */
        const val TITLECASE_LETTER: Byte = 3

        /**
         * General category "Lm" in the Unicode specification.
         * @since   1.1
         */
        const val MODIFIER_LETTER: Byte = 4

        /**
         * General category "Lo" in the Unicode specification.
         * @since   1.1
         */
        const val OTHER_LETTER: Byte = 5

        /**
         * General category "Mn" in the Unicode specification.
         * @since   1.1
         */
        const val NON_SPACING_MARK: Byte = 6

        /**
         * General category "Me" in the Unicode specification.
         * @since   1.1
         */
        const val ENCLOSING_MARK: Byte = 7

        /**
         * General category "Mc" in the Unicode specification.
         * @since   1.1
         */
        const val COMBINING_SPACING_MARK: Byte = 8

        /**
         * General category "Nd" in the Unicode specification.
         * @since   1.1
         */
        const val DECIMAL_DIGIT_NUMBER: Byte = 9

        /**
         * General category "Nl" in the Unicode specification.
         * @since   1.1
         */
        const val LETTER_NUMBER: Byte = 10

        /**
         * General category "No" in the Unicode specification.
         * @since   1.1
         */
        const val OTHER_NUMBER: Byte = 11

        /**
         * General category "Zs" in the Unicode specification.
         * @since   1.1
         */
        const val SPACE_SEPARATOR: Byte = 12

        /**
         * General category "Zl" in the Unicode specification.
         * @since   1.1
         */
        const val LINE_SEPARATOR: Byte = 13

        /**
         * General category "Zp" in the Unicode specification.
         * @since   1.1
         */
        const val PARAGRAPH_SEPARATOR: Byte = 14

        /**
         * General category "Cc" in the Unicode specification.
         * @since   1.1
         */
        const val CONTROL: Byte = 15

        /**
         * General category "Cf" in the Unicode specification.
         * @since   1.1
         */
        const val FORMAT: Byte = 16

        /**
         * General category "Co" in the Unicode specification.
         * @since   1.1
         */
        const val PRIVATE_USE: Byte = 18

        /**
         * General category "Cs" in the Unicode specification.
         * @since   1.1
         */
        const val SURROGATE: Byte = 19

        /**
         * General category "Pd" in the Unicode specification.
         * @since   1.1
         */
        const val DASH_PUNCTUATION: Byte = 20

        /**
         * General category "Ps" in the Unicode specification.
         * @since   1.1
         */
        const val START_PUNCTUATION: Byte = 21

        /**
         * General category "Pe" in the Unicode specification.
         * @since   1.1
         */
        const val END_PUNCTUATION: Byte = 22

        /**
         * General category "Pc" in the Unicode specification.
         * @since   1.1
         */
        const val CONNECTOR_PUNCTUATION: Byte = 23

        /**
         * General category "Po" in the Unicode specification.
         * @since   1.1
         */
        const val OTHER_PUNCTUATION: Byte = 24

        /**
         * General category "Sm" in the Unicode specification.
         * @since   1.1
         */
        const val MATH_SYMBOL: Byte = 25

        /**
         * General category "Sc" in the Unicode specification.
         * @since   1.1
         */
        const val CURRENCY_SYMBOL: Byte = 26

        /**
         * General category "Sk" in the Unicode specification.
         * @since   1.1
         */
        const val MODIFIER_SYMBOL: Byte = 27

        /**
         * General category "So" in the Unicode specification.
         * @since   1.1
         */
        const val OTHER_SYMBOL: Byte = 28

        /**
         * General category "Pi" in the Unicode specification.
         * @since   1.4
         */
        const val INITIAL_QUOTE_PUNCTUATION: Byte = 29

        /**
         * General category "Pf" in the Unicode specification.
         * @since   1.4
         */
        const val FINAL_QUOTE_PUNCTUATION: Byte = 30

        /**
         * Error flag. Use int (code point) to avoid confusion with U+FFFF.
         */
        const val ERROR: Int = -0x1

        /**
         * Undefined bidirectional character type. Undefined {@code char}
         * values have undefined directionality in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_UNDEFINED: Byte = -1

        /**
         * Strong bidirectional character type "L" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_LEFT_TO_RIGHT: Byte = 0

        /**
         * Strong bidirectional character type "R" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT: Byte = 1

        /**
         * Strong bidirectional character type "AL" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC: Byte = 2

        /**
         * Weak bidirectional character type "EN" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_EUROPEAN_NUMBER: Byte = 3

        /**
         * Weak bidirectional character type "ES" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR: Byte = 4

        /**
         * Weak bidirectional character type "ET" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR: Byte = 5

        /**
         * Weak bidirectional character type "AN" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_ARABIC_NUMBER: Byte = 6

        /**
         * Weak bidirectional character type "CS" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_COMMON_NUMBER_SEPARATOR: Byte = 7

        /**
         * Weak bidirectional character type "NSM" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_NONSPACING_MARK: Byte = 8

        /**
         * Weak bidirectional character type "BN" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_BOUNDARY_NEUTRAL: Byte = 9

        /**
         * Neutral bidirectional character type "B" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_PARAGRAPH_SEPARATOR: Byte = 10

        /**
         * Neutral bidirectional character type "S" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_SEGMENT_SEPARATOR: Byte = 11

        /**
         * Neutral bidirectional character type "WS" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_WHITESPACE: Byte = 12

        /**
         * Neutral bidirectional character type "ON" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_OTHER_NEUTRALS: Byte = 13

        /**
         * Strong bidirectional character type "LRE" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING: Byte = 14

        /**
         * Strong bidirectional character type "LRO" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE: Byte = 15

        /**
         * Strong bidirectional character type "RLE" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING: Byte = 16

        /**
         * Strong bidirectional character type "RLO" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE: Byte = 17

        /**
         * Weak bidirectional character type "PDF" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_POP_DIRECTIONAL_FORMAT: Byte = 18

        /**
         * Weak bidirectional character type "LRI" in the Unicode specification.
         * @since 9
         */
        const val DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE: Byte = 19

        /**
         * Weak bidirectional character type "RLI" in the Unicode specification.
         * @since 9
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE: Byte = 20

        /**
         * Weak bidirectional character type "FSI" in the Unicode specification.
         * @since 9
         */
        const val DIRECTIONALITY_FIRST_STRONG_ISOLATE: Byte = 21

        /**
         * Weak bidirectional character type "PDI" in the Unicode specification.
         * @since 9
         */
        const val DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE: Byte = 22


        const val SIZE: Int = 16

        /**
         * The number of bytes used to represent a `char` value in unsigned
         * binary form.
         *
         * @since 1.8
         */
        const val BYTES: Int = SIZE / Byte.SIZE_BITS //2

        /**
         * The minimum value of a
         * [Unicode high-surrogate code unit](http://www.unicode.org/glossary/#high_surrogate_code_unit)
         * in the UTF-16 encoding, constant `'\u005CuD800'`.
         * A high-surrogate is also known as a *leading-surrogate*.
         *
         * @since 1.5
         */
        const val MIN_HIGH_SURROGATE: Char = '\uD800'

        /**
         * The maximum value of a
         * [Unicode high-surrogate code unit](http://www.unicode.org/glossary/#high_surrogate_code_unit)
         * in the UTF-16 encoding, constant `'\u005CuDBFF'`.
         * A high-surrogate is also known as a *leading-surrogate*.
         *
         * @since 1.5
         */
        const val MAX_HIGH_SURROGATE: Char = '\uDBFF'

        /**
         * The minimum value of a
         * [Unicode low-surrogate code unit](http://www.unicode.org/glossary/#low_surrogate_code_unit)
         * in the UTF-16 encoding, constant `'\u005CuDC00'`.
         * A low-surrogate is also known as a *trailing-surrogate*.
         *
         * @since 1.5
         */
        const val MIN_LOW_SURROGATE: Char = '\uDC00'


        /**
         * The maximum value of a
         * [Unicode low-surrogate code unit](http://www.unicode.org/glossary/#low_surrogate_code_unit)
         * in the UTF-16 encoding, constant `'\u005CuDFFF'`.
         * A low-surrogate is also known as a *trailing-surrogate*.
         *
         * @since 1.5
         */
        const val MAX_LOW_SURROGATE: Char = '\uDFFF'

        /**
         * The minimum value of a Unicode surrogate code unit in the
         * UTF-16 encoding, constant `'\u005CuD800'`.
         *
         * @since 1.5
         */
        const val MIN_SURROGATE: Char = MIN_HIGH_SURROGATE

        /**
         * The maximum value of a Unicode surrogate code unit in the
         * UTF-16 encoding, constant `'\u005CuDFFF'`.
         *
         * @since 1.5
         */
        const val MAX_SURROGATE: Char = MAX_LOW_SURROGATE

        fun charCount(codePoint: Int): Int {
            return if (codePoint >= MIN_SUPPLEMENTARY_CODE_POINT) 2 else 1
        }


        /**
         * Converts the specified surrogate pair to its supplementary code
         * point value. This method does not validate the specified
         * surrogate pair. The caller must validate it using [ ][.isSurrogatePair] if necessary.
         *
         * @param  high the high-surrogate code unit
         * @param  low the low-surrogate code unit
         * @return the supplementary code point composed from the
         * specified surrogate pair.
         * @since  1.5
         */
        fun toCodePoint(high: Char, low: Char): Int {
            // Optimized form of:
            // return ((high - MIN_HIGH_SURROGATE) << 10)
            //         + (low - MIN_LOW_SURROGATE)
            //         + MIN_SUPPLEMENTARY_CODE_POINT;
            return ((high.code shl 10) + low.code) + ((MIN_SUPPLEMENTARY_CODE_POINT
                    - (MIN_HIGH_SURROGATE.code shl 10)
                    - MIN_LOW_SURROGATE.code))
        }

        /**
         * Returns the code point at the given index of the
         * `CharSequence`. If the `char` value at
         * the given index in the `CharSequence` is in the
         * high-surrogate range, the following index is less than the
         * length of the `CharSequence`, and the
         * `char` value at the following index is in the
         * low-surrogate range, then the supplementary code point
         * corresponding to this surrogate pair is returned. Otherwise,
         * the `char` value at the given index is returned.
         *
         * @param seq a sequence of `char` values (Unicode code
         * units)
         * @param index the index to the `char` values (Unicode
         * code units) in `seq` to be converted
         * @return the Unicode code point at the given index
         * @throws NullPointerException if `seq` is null.
         * @throws IndexOutOfBoundsException if the value
         * `index` is negative or not less than
         * [seq.length()][CharSequence.length].
         * @since  1.5
         */
        fun codePointAt(seq: CharSequence, index: Int): Int {
            var index = index
            val c1 = seq[index]
            if (isHighSurrogate(c1) && ++index < seq.length) {
                val c2 = seq[index]
                if (c2.isLowSurrogate()) {
                    return toCodePoint(c1, c2)
                }
            }
            return c1.code
        }

        /**
         * Returns the code point at the given index of the
         * `char` array, where only array elements with
         * `index` less than `limit` can be used. If
         * the `char` value at the given index in the
         * `char` array is in the high-surrogate range, the
         * following index is less than the `limit`, and the
         * `char` value at the following index is in the
         * low-surrogate range, then the supplementary code point
         * corresponding to this surrogate pair is returned. Otherwise,
         * the `char` value at the given index is returned.
         *
         * @param a the `char` array
         * @param index the index to the `char` values (Unicode
         * code units) in the `char` array to be converted
         * @param limit the index after the last array element that
         * can be used in the `char` array
         * @return the Unicode code point at the given index
         * @throws NullPointerException if `a` is null.
         * @throws IndexOutOfBoundsException if the `index`
         * argument is negative or not less than the `limit`
         * argument, or if the `limit` argument is negative or
         * greater than the length of the `char` array.
         * @since  1.5
         */
        fun codePointAt(a: CharArray, index: Int, limit: Int): Int {
            if (index !in 0..<limit || limit > a.size) {
                throw IndexOutOfBoundsException()
            }
            return codePointAtImpl(a, index, limit)
        }

        // throws ArrayIndexOutOfBoundsException if index out of bounds
        fun codePointAtImpl(a: CharArray, index: Int, limit: Int): Int {
            var index = index
            val c1 = a[index]
            if (isHighSurrogate(c1) && ++index < limit) {
                val c2 = a[index]
                if (c2.isLowSurrogate()) {
                    return toCodePoint(c1, c2)
                }
            }
            return c1.code
        }

        /**
         * Determines if the specified character (Unicode code point) is a
         * lowercase character.
         *
         *
         * A character is lowercase if its general category type, provided
         * by [getType(codePoint)][Character.getType], is
         * `LOWERCASE_LETTER`, or it has contributory property
         * Other_Lowercase as defined by the Unicode Standard.
         *
         *
         * The following are examples of lowercase characters:
         * <blockquote><pre>
         * a b c d e f g h i j k l m n o p q r s t u v w x y z
         * '&#92;u00DF' '&#92;u00E0' '&#92;u00E1' '&#92;u00E2' '&#92;u00E3' '&#92;u00E4' '&#92;u00E5' '&#92;u00E6'
         * '&#92;u00E7' '&#92;u00E8' '&#92;u00E9' '&#92;u00EA' '&#92;u00EB' '&#92;u00EC' '&#92;u00ED' '&#92;u00EE'
         * '&#92;u00EF' '&#92;u00F0' '&#92;u00F1' '&#92;u00F2' '&#92;u00F3' '&#92;u00F4' '&#92;u00F5' '&#92;u00F6'
         * '&#92;u00F8' '&#92;u00F9' '&#92;u00FA' '&#92;u00FB' '&#92;u00FC' '&#92;u00FD' '&#92;u00FE' '&#92;u00FF'
        </pre></blockquote> *
         *
         *  Many other Unicode characters are lowercase too.
         *
         * @param   codePoint the character (Unicode code point) to be tested.
         * @return  `true` if the character is lowercase;
         * `false` otherwise.
         * @see Character.isLowerCase
         * @see Character.isTitleCase
         * @see Character.toLowerCase
         * @see Character.getType
         * @since   1.5
         */
        fun isLowerCase(codePoint: Int): Boolean {
            return CharacterData.of(codePoint).isLowerCase(codePoint)
        }

        /**
         * Determines if the given `char` value is a
         * [Unicode high-surrogate code unit](http://www.unicode.org/glossary/#high_surrogate_code_unit)
         * (also known as *leading-surrogate code unit*).
         *
         *
         * Such values do not represent characters by themselves,
         * but are used in the representation of
         * [supplementary characters](#supplementary)
         * in the UTF-16 encoding.
         *
         * @param  ch the `char` value to be tested.
         * @return `true` if the `char` value is between
         * [.MIN_HIGH_SURROGATE] and
         * [.MAX_HIGH_SURROGATE] inclusive;
         * `false` otherwise.
         * @see Character.isLowSurrogate
         * @see Character.UnicodeBlock.of
         * @since  1.5
         */
        fun isHighSurrogate(ch: Char): Boolean {
            // Help VM constant-fold; MAX_HIGH_SURROGATE + 1 == MIN_LOW_SURROGATE
            return ch >= MIN_HIGH_SURROGATE && ch.code < (MAX_HIGH_SURROGATE.code + 1)
        }

        /**
         * Converts the character (Unicode code point) argument to
         * uppercase using case mapping information from the UnicodeData
         * file.
         *
         *
         * Note that
         * `Character.isUpperCase(Character.toUpperCase(codePoint))`
         * does not always return `true` for some ranges of
         * characters, particularly those that are symbols or ideographs.
         *
         *
         * In general, [String.toUpperCase] should be used to map
         * characters to uppercase. `String` case mapping methods
         * have several benefits over `Character` case mapping methods.
         * `String` case mapping methods can perform locale-sensitive
         * mappings, context-sensitive mappings, and 1:M character mappings, whereas
         * the `Character` case mapping methods cannot.
         *
         * @param   codePoint   the character (Unicode code point) to be converted.
         * @return  the uppercase equivalent of the character, if any;
         * otherwise, the character itself.
         * @see Character.isUpperCase
         * @see String.toUpperCase
         * @since   1.5
         */
        fun toUpperCase(codePoint: Int): Int {
            return CharacterData.of(codePoint).toUpperCase(codePoint)
        }

        /**
         * Converts the character (Unicode code point) argument to
         * lowercase using case mapping information from the UnicodeData
         * file.
         *
         *
         *  Note that
         * `Character.isLowerCase(Character.toLowerCase(codePoint))`
         * does not always return `true` for some ranges of
         * characters, particularly those that are symbols or ideographs.
         *
         *
         * In general, [String.toLowerCase] should be used to map
         * characters to lowercase. `String` case mapping methods
         * have several benefits over `Character` case mapping methods.
         * `String` case mapping methods can perform locale-sensitive
         * mappings, context-sensitive mappings, and 1:M character mappings, whereas
         * the `Character` case mapping methods cannot.
         *
         * @param   codePoint   the character (Unicode code point) to be converted.
         * @return  the lowercase equivalent of the character (Unicode code
         * point), if any; otherwise, the character itself.
         * @see Character.isLowerCase
         * @see String.toLowerCase
         * @since   1.5
         */
        fun toLowerCase(codePoint: Int): Int {
            return CharacterData.of(codePoint).toLowerCase(codePoint)
        }

        /**
         * Compares two `char` values numerically.
         * The value returned is identical to what would be returned by:
         * <pre>
         * Character.valueOf(x).compareTo(Character.valueOf(y))
        </pre> *
         *
         * @param  x the first `char` to compare
         * @param  y the second `char` to compare
         * @return the value `0` if `x == y`;
         * a value less than `0` if `x < y`; and
         * a value greater than `0` if `x > y`
         * @since 1.7
         */
        fun compare(x: Char, y: Char): Int {
            return x.code - y.code
        }

        /**
         * Converts the specified character (Unicode code point) to its
         * UTF-16 representation. If the specified code point is a BMP
         * (Basic Multilingual Plane or Plane 0) value, the same value is
         * stored in `dst[dstIndex]`, and 1 is returned. If the
         * specified code point is a supplementary character, its
         * surrogate values are stored in `dst[dstIndex]`
         * (high-surrogate) and `dst[dstIndex+1]`
         * (low-surrogate), and 2 is returned.
         *
         * @param  codePoint the character (Unicode code point) to be converted.
         * @param  dst an array of `char` in which the
         * `codePoint`'s UTF-16 value is stored.
         * @param dstIndex the start index into the `dst`
         * array where the converted value is stored.
         * @return 1 if the code point is a BMP code point, 2 if the
         * code point is a supplementary code point.
         * @throws IllegalArgumentException if the specified
         * `codePoint` is not a valid Unicode code point.
         * @throws NullPointerException if the specified `dst` is null.
         * @throws IndexOutOfBoundsException if `dstIndex`
         * is negative or not less than `dst.length`, or if
         * `dst` at `dstIndex` doesn't have enough
         * array element(s) to store the resulting `char`
         * value(s). (If `dstIndex` is equal to
         * `dst.length-1` and the specified
         * `codePoint` is a supplementary character, the
         * high-surrogate value is not stored in
         * `dst[dstIndex]`.)
         * @since  1.5
         */
        fun toChars(codePoint: Int, dst: CharArray, dstIndex: Int): Int {
            if (isBmpCodePoint(codePoint)) {
                dst[dstIndex] = codePoint.toChar()
                return 1
            } else if (isValidCodePoint(codePoint)) {
                toSurrogates(codePoint, dst, dstIndex)
                return 2
            } else {
                throw IllegalArgumentException(
                    "Not a valid Unicode code point: 0x$codePoint"
                )
            }
        }

        fun toSurrogates(codePoint: Int, dst: CharArray, index: Int) {
            // We write elements "backwards" to guarantee all-or-nothing
            dst[index + 1] = lowSurrogate(codePoint)
            dst[index] = highSurrogate(codePoint)
        }

        /**
         * Returns the trailing surrogate (a
         * [low surrogate code unit](http://www.unicode.org/glossary/#low_surrogate_code_unit)) of the
         * [surrogate pair](http://www.unicode.org/glossary/#surrogate_pair)
         * representing the specified supplementary character (Unicode
         * code point) in the UTF-16 encoding.  If the specified character
         * is not a
         * [supplementary character](Character.html#supplementary),
         * an unspecified `char` is returned.
         *
         *
         * If
         * [isSupplementaryCodePoint(x)][.isSupplementaryCodePoint]
         * is `true`, then
         * [isLowSurrogate][.isLowSurrogate]`(lowSurrogate(x))` and
         * [toCodePoint][.toCodePoint]`(`[highSurrogate][.highSurrogate]`(x), lowSurrogate(x)) == x`
         * are also always `true`.
         *
         * @param   codePoint a supplementary character (Unicode code point)
         * @return  the trailing surrogate code unit used to represent the
         * character in the UTF-16 encoding
         * @since   1.7
         */
        fun lowSurrogate(codePoint: Int): Char {
            return ((codePoint and 0x3ff) + MIN_LOW_SURROGATE.code).toChar()
        }

        /**
         * Returns the leading surrogate (a
         * [high surrogate code unit](http://www.unicode.org/glossary/#high_surrogate_code_unit)) of the
         * [surrogate pair](http://www.unicode.org/glossary/#surrogate_pair)
         * representing the specified supplementary character (Unicode
         * code point) in the UTF-16 encoding.  If the specified character
         * is not a
         * [supplementary character](Character.html#supplementary),
         * an unspecified `char` is returned.
         *
         *
         * If
         * [isSupplementaryCodePoint(x)][.isSupplementaryCodePoint]
         * is `true`, then
         * [isHighSurrogate][.isHighSurrogate]`(highSurrogate(x))` and
         * [toCodePoint][.toCodePoint]`(highSurrogate(x), `[lowSurrogate][.lowSurrogate]`(x)) == x`
         * are also always `true`.
         *
         * @param   codePoint a supplementary character (Unicode code point)
         * @return  the leading surrogate code unit used to represent the
         * character in the UTF-16 encoding
         * @since   1.7
         */
        fun highSurrogate(codePoint: Int): Char {
            return ((codePoint ushr 10)
                    + (MIN_HIGH_SURROGATE.code - (MIN_SUPPLEMENTARY_CODE_POINT ushr 10))).toChar()
        }

        /**
         * Determines whether the specified character (Unicode code point)
         * is in the [Basic Multilingual Plane (BMP)](#BMP).
         * Such code points can be represented using a single `char`.
         *
         * @param  codePoint the character (Unicode code point) to be tested
         * @return `true` if the specified code point is between
         * [.MIN_VALUE] and [.MAX_VALUE] inclusive;
         * `false` otherwise.
         * @since  1.7
         */
        fun isBmpCodePoint(codePoint: Int): Boolean {
            return codePoint ushr 16 == 0
            // Optimized form of:
            //     codePoint >= MIN_VALUE && codePoint <= MAX_VALUE
            // We consistently use logical shift (>>>) to facilitate
            // additional runtime optimizations.
        }

        /**
         * Determines whether the specified code point is a valid
         * [Unicode code point value](http://www.unicode.org/glossary/#code_point).
         *
         * @param  codePoint the Unicode code point to be tested
         * @return `true` if the specified code point value is between
         * [.MIN_CODE_POINT] and
         * [.MAX_CODE_POINT] inclusive;
         * `false` otherwise.
         * @since  1.5
         */
        fun isValidCodePoint(codePoint: Int): Boolean {
            // Optimized form of:
            //     codePoint >= MIN_CODE_POINT && codePoint <= MAX_CODE_POINT
            val plane = codePoint ushr 16
            return plane < ((MAX_CODE_POINT + 1) ushr 16)
        }

        /**
         * Determines whether the specified character (Unicode code point)
         * is in the [supplementary character](#supplementary) range.
         *
         * @param  codePoint the character (Unicode code point) to be tested
         * @return `true` if the specified code point is between
         * [.MIN_SUPPLEMENTARY_CODE_POINT] and
         * [.MAX_CODE_POINT] inclusive;
         * `false` otherwise.
         * @since  1.5
         */
        fun isSupplementaryCodePoint(codePoint: Int): Boolean {
            return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT
                    && codePoint < MAX_CODE_POINT + 1
        }

        /**
         * Determines if the specified character (Unicode code point) is
         * an Extended Pictographic.
         *
         *
         * A character is considered to be an Extended Pictographic if and only if it has
         * the `Extended_Pictographic` property, defined in
         * [
         * Unicode Emoji (Technical Standard #51)](https://unicode.org/reports/tr51/#Emoji_Properties_and_Data_Files).
         *
         * @param   codePoint the character (Unicode code point) to be tested.
         * @return  `true` if the character is an Extended Pictographic;
         * `false` otherwise.
         * @since   21
         */
        fun isExtendedPictographic(codePoint: Int): Boolean {
            return CharacterData.of(codePoint).isExtendedPictographic(codePoint)
        }


        /**
         * Returns a value indicating a character's general category.
         *
         * @param   codePoint the character (Unicode code point) to be tested.
         * @return  a value of type `int` representing the
         * character's general category.
         * @see Character.COMBINING_SPACING_MARK COMBINING_SPACING_MARK
         *
         * @see Character.CONNECTOR_PUNCTUATION CONNECTOR_PUNCTUATION
         *
         * @see Character.CONTROL CONTROL
         *
         * @see Character.CURRENCY_SYMBOL CURRENCY_SYMBOL
         *
         * @see Character.DASH_PUNCTUATION DASH_PUNCTUATION
         *
         * @see Character.DECIMAL_DIGIT_NUMBER DECIMAL_DIGIT_NUMBER
         *
         * @see Character.ENCLOSING_MARK ENCLOSING_MARK
         *
         * @see Character.END_PUNCTUATION END_PUNCTUATION
         *
         * @see Character.FINAL_QUOTE_PUNCTUATION FINAL_QUOTE_PUNCTUATION
         *
         * @see Character.FORMAT FORMAT
         *
         * @see Character.INITIAL_QUOTE_PUNCTUATION INITIAL_QUOTE_PUNCTUATION
         *
         * @see Character.LETTER_NUMBER LETTER_NUMBER
         *
         * @see Character.LINE_SEPARATOR LINE_SEPARATOR
         *
         * @see Character.LOWERCASE_LETTER LOWERCASE_LETTER
         *
         * @see Character.MATH_SYMBOL MATH_SYMBOL
         *
         * @see Character.MODIFIER_LETTER MODIFIER_LETTER
         *
         * @see Character.MODIFIER_SYMBOL MODIFIER_SYMBOL
         *
         * @see Character.NON_SPACING_MARK NON_SPACING_MARK
         *
         * @see Character.OTHER_LETTER OTHER_LETTER
         *
         * @see Character.OTHER_NUMBER OTHER_NUMBER
         *
         * @see Character.OTHER_PUNCTUATION OTHER_PUNCTUATION
         *
         * @see Character.OTHER_SYMBOL OTHER_SYMBOL
         *
         * @see Character.PARAGRAPH_SEPARATOR PARAGRAPH_SEPARATOR
         *
         * @see Character.PRIVATE_USE PRIVATE_USE
         *
         * @see Character.SPACE_SEPARATOR SPACE_SEPARATOR
         *
         * @see Character.START_PUNCTUATION START_PUNCTUATION
         *
         * @see Character.SURROGATE SURROGATE
         *
         * @see Character.TITLECASE_LETTER TITLECASE_LETTER
         *
         * @see Character.UNASSIGNED UNASSIGNED
         *
         * @see Character.UPPERCASE_LETTER UPPERCASE_LETTER
         *
         * @since   1.5
         */
        fun getType(codePoint: Int): Int {
            return CharacterData.of(codePoint).getType(codePoint)
        }

        /**
         * Determines if the specified character (Unicode code point) is a digit.
         */
        fun isDigit(codePoint: Int): Boolean {
            return CharacterData.of(codePoint).isDigit(codePoint)
        }

        /**
         * Returns the numeric value of the specified character (Unicode code point).
         */
        fun getNumericValue(codePoint: Int): Int {
            return CharacterData.of(codePoint).getNumericValue(codePoint)
        }


        /**
         * A family of character subsets representing the character scripts
         * defined in the [
         * *Unicode Standard Annex #24: Script Names*](http://www.unicode.org/reports/tr24/). Every Unicode
         * character is assigned to a single Unicode script, either a specific
         * script, such as [Latin][Character.UnicodeScript.LATIN], or
         * one of the following three special values,
         * [Inherited][Character.UnicodeScript.INHERITED],
         * [Common][Character.UnicodeScript.COMMON] or
         * [Unknown][Character.UnicodeScript.UNKNOWN].
         *
         * @spec https://www.unicode.org/reports/tr24 Unicode Script Property
         * @since 1.7
         */
        enum class UnicodeScript {
            /**
             * Unicode script "Common".
             */
            COMMON,

            /**
             * Unicode script "Latin".
             */
            LATIN,

            /**
             * Unicode script "Greek".
             */
            GREEK,

            /**
             * Unicode script "Cyrillic".
             */
            CYRILLIC,

            /**
             * Unicode script "Armenian".
             */
            ARMENIAN,

            /**
             * Unicode script "Hebrew".
             */
            HEBREW,

            /**
             * Unicode script "Arabic".
             */
            ARABIC,

            /**
             * Unicode script "Syriac".
             */
            SYRIAC,

            /**
             * Unicode script "Thaana".
             */
            THAANA,

            /**
             * Unicode script "Devanagari".
             */
            DEVANAGARI,

            /**
             * Unicode script "Bengali".
             */
            BENGALI,

            /**
             * Unicode script "Gurmukhi".
             */
            GURMUKHI,

            /**
             * Unicode script "Gujarati".
             */
            GUJARATI,

            /**
             * Unicode script "Oriya".
             */
            ORIYA,

            /**
             * Unicode script "Tamil".
             */
            TAMIL,

            /**
             * Unicode script "Telugu".
             */
            TELUGU,

            /**
             * Unicode script "Kannada".
             */
            KANNADA,

            /**
             * Unicode script "Malayalam".
             */
            MALAYALAM,

            /**
             * Unicode script "Sinhala".
             */
            SINHALA,

            /**
             * Unicode script "Thai".
             */
            THAI,

            /**
             * Unicode script "Lao".
             */
            LAO,

            /**
             * Unicode script "Tibetan".
             */
            TIBETAN,

            /**
             * Unicode script "Myanmar".
             */
            MYANMAR,

            /**
             * Unicode script "Georgian".
             */
            GEORGIAN,

            /**
             * Unicode script "Hangul".
             */
            HANGUL,

            /**
             * Unicode script "Ethiopic".
             */
            ETHIOPIC,

            /**
             * Unicode script "Cherokee".
             */
            CHEROKEE,

            /**
             * Unicode script "Canadian_Aboriginal".
             */
            CANADIAN_ABORIGINAL,

            /**
             * Unicode script "Ogham".
             */
            OGHAM,

            /**
             * Unicode script "Runic".
             */
            RUNIC,

            /**
             * Unicode script "Khmer".
             */
            KHMER,

            /**
             * Unicode script "Mongolian".
             */
            MONGOLIAN,

            /**
             * Unicode script "Hiragana".
             */
            HIRAGANA,

            /**
             * Unicode script "Katakana".
             */
            KATAKANA,

            /**
             * Unicode script "Bopomofo".
             */
            BOPOMOFO,

            /**
             * Unicode script "Han".
             */
            HAN,

            /**
             * Unicode script "Yi".
             */
            YI,

            /**
             * Unicode script "Old_Italic".
             */
            OLD_ITALIC,

            /**
             * Unicode script "Gothic".
             */
            GOTHIC,

            /**
             * Unicode script "Deseret".
             */
            DESERET,

            /**
             * Unicode script "Inherited".
             */
            INHERITED,

            /**
             * Unicode script "Tagalog".
             */
            TAGALOG,

            /**
             * Unicode script "Hanunoo".
             */
            HANUNOO,

            /**
             * Unicode script "Buhid".
             */
            BUHID,

            /**
             * Unicode script "Tagbanwa".
             */
            TAGBANWA,

            /**
             * Unicode script "Limbu".
             */
            LIMBU,

            /**
             * Unicode script "Tai_Le".
             */
            TAI_LE,

            /**
             * Unicode script "Linear_B".
             */
            LINEAR_B,

            /**
             * Unicode script "Ugaritic".
             */
            UGARITIC,

            /**
             * Unicode script "Shavian".
             */
            SHAVIAN,

            /**
             * Unicode script "Osmanya".
             */
            OSMANYA,

            /**
             * Unicode script "Cypriot".
             */
            CYPRIOT,

            /**
             * Unicode script "Braille".
             */
            BRAILLE,

            /**
             * Unicode script "Buginese".
             */
            BUGINESE,

            /**
             * Unicode script "Coptic".
             */
            COPTIC,

            /**
             * Unicode script "New_Tai_Lue".
             */
            NEW_TAI_LUE,

            /**
             * Unicode script "Glagolitic".
             */
            GLAGOLITIC,

            /**
             * Unicode script "Tifinagh".
             */
            TIFINAGH,

            /**
             * Unicode script "Syloti_Nagri".
             */
            SYLOTI_NAGRI,

            /**
             * Unicode script "Old_Persian".
             */
            OLD_PERSIAN,

            /**
             * Unicode script "Kharoshthi".
             */
            KHAROSHTHI,

            /**
             * Unicode script "Balinese".
             */
            BALINESE,

            /**
             * Unicode script "Cuneiform".
             */
            CUNEIFORM,

            /**
             * Unicode script "Phoenician".
             */
            PHOENICIAN,

            /**
             * Unicode script "Phags_Pa".
             */
            PHAGS_PA,

            /**
             * Unicode script "Nko".
             */
            NKO,

            /**
             * Unicode script "Sundanese".
             */
            SUNDANESE,

            /**
             * Unicode script "Batak".
             */
            BATAK,

            /**
             * Unicode script "Lepcha".
             */
            LEPCHA,

            /**
             * Unicode script "Ol_Chiki".
             */
            OL_CHIKI,

            /**
             * Unicode script "Vai".
             */
            VAI,

            /**
             * Unicode script "Saurashtra".
             */
            SAURASHTRA,

            /**
             * Unicode script "Kayah_Li".
             */
            KAYAH_LI,

            /**
             * Unicode script "Rejang".
             */
            REJANG,

            /**
             * Unicode script "Lycian".
             */
            LYCIAN,

            /**
             * Unicode script "Carian".
             */
            CARIAN,

            /**
             * Unicode script "Lydian".
             */
            LYDIAN,

            /**
             * Unicode script "Cham".
             */
            CHAM,

            /**
             * Unicode script "Tai_Tham".
             */
            TAI_THAM,

            /**
             * Unicode script "Tai_Viet".
             */
            TAI_VIET,

            /**
             * Unicode script "Avestan".
             */
            AVESTAN,

            /**
             * Unicode script "Egyptian_Hieroglyphs".
             */
            EGYPTIAN_HIEROGLYPHS,

            /**
             * Unicode script "Samaritan".
             */
            SAMARITAN,

            /**
             * Unicode script "Mandaic".
             */
            MANDAIC,

            /**
             * Unicode script "Lisu".
             */
            LISU,

            /**
             * Unicode script "Bamum".
             */
            BAMUM,

            /**
             * Unicode script "Javanese".
             */
            JAVANESE,

            /**
             * Unicode script "Meetei_Mayek".
             */
            MEETEI_MAYEK,

            /**
             * Unicode script "Imperial_Aramaic".
             */
            IMPERIAL_ARAMAIC,

            /**
             * Unicode script "Old_South_Arabian".
             */
            OLD_SOUTH_ARABIAN,

            /**
             * Unicode script "Inscriptional_Parthian".
             */
            INSCRIPTIONAL_PARTHIAN,

            /**
             * Unicode script "Inscriptional_Pahlavi".
             */
            INSCRIPTIONAL_PAHLAVI,

            /**
             * Unicode script "Old_Turkic".
             */
            OLD_TURKIC,

            /**
             * Unicode script "Brahmi".
             */
            BRAHMI,

            /**
             * Unicode script "Kaithi".
             */
            KAITHI,

            /**
             * Unicode script "Meroitic Hieroglyphs".
             * @since 1.8
             */
            MEROITIC_HIEROGLYPHS,

            /**
             * Unicode script "Meroitic Cursive".
             * @since 1.8
             */
            MEROITIC_CURSIVE,

            /**
             * Unicode script "Sora Sompeng".
             * @since 1.8
             */
            SORA_SOMPENG,

            /**
             * Unicode script "Chakma".
             * @since 1.8
             */
            CHAKMA,

            /**
             * Unicode script "Sharada".
             * @since 1.8
             */
            SHARADA,

            /**
             * Unicode script "Takri".
             * @since 1.8
             */
            TAKRI,

            /**
             * Unicode script "Miao".
             * @since 1.8
             */
            MIAO,

            /**
             * Unicode script "Caucasian Albanian".
             * @since 9
             */
            CAUCASIAN_ALBANIAN,

            /**
             * Unicode script "Bassa Vah".
             * @since 9
             */
            BASSA_VAH,

            /**
             * Unicode script "Duployan".
             * @since 9
             */
            DUPLOYAN,

            /**
             * Unicode script "Elbasan".
             * @since 9
             */
            ELBASAN,

            /**
             * Unicode script "Grantha".
             * @since 9
             */
            GRANTHA,

            /**
             * Unicode script "Pahawh Hmong".
             * @since 9
             */
            PAHAWH_HMONG,

            /**
             * Unicode script "Khojki".
             * @since 9
             */
            KHOJKI,

            /**
             * Unicode script "Linear A".
             * @since 9
             */
            LINEAR_A,

            /**
             * Unicode script "Mahajani".
             * @since 9
             */
            MAHAJANI,

            /**
             * Unicode script "Manichaean".
             * @since 9
             */
            MANICHAEAN,

            /**
             * Unicode script "Mende Kikakui".
             * @since 9
             */
            MENDE_KIKAKUI,

            /**
             * Unicode script "Modi".
             * @since 9
             */
            MODI,

            /**
             * Unicode script "Mro".
             * @since 9
             */
            MRO,

            /**
             * Unicode script "Old North Arabian".
             * @since 9
             */
            OLD_NORTH_ARABIAN,

            /**
             * Unicode script "Nabataean".
             * @since 9
             */
            NABATAEAN,

            /**
             * Unicode script "Palmyrene".
             * @since 9
             */
            PALMYRENE,

            /**
             * Unicode script "Pau Cin Hau".
             * @since 9
             */
            PAU_CIN_HAU,

            /**
             * Unicode script "Old Permic".
             * @since 9
             */
            OLD_PERMIC,

            /**
             * Unicode script "Psalter Pahlavi".
             * @since 9
             */
            PSALTER_PAHLAVI,

            /**
             * Unicode script "Siddham".
             * @since 9
             */
            SIDDHAM,

            /**
             * Unicode script "Khudawadi".
             * @since 9
             */
            KHUDAWADI,

            /**
             * Unicode script "Tirhuta".
             * @since 9
             */
            TIRHUTA,

            /**
             * Unicode script "Warang Citi".
             * @since 9
             */
            WARANG_CITI,

            /**
             * Unicode script "Ahom".
             * @since 9
             */
            AHOM,

            /**
             * Unicode script "Anatolian Hieroglyphs".
             * @since 9
             */
            ANATOLIAN_HIEROGLYPHS,

            /**
             * Unicode script "Hatran".
             * @since 9
             */
            HATRAN,

            /**
             * Unicode script "Multani".
             * @since 9
             */
            MULTANI,

            /**
             * Unicode script "Old Hungarian".
             * @since 9
             */
            OLD_HUNGARIAN,

            /**
             * Unicode script "SignWriting".
             * @since 9
             */
            SIGNWRITING,

            /**
             * Unicode script "Adlam".
             * @since 11
             */
            ADLAM,

            /**
             * Unicode script "Bhaiksuki".
             * @since 11
             */
            BHAIKSUKI,

            /**
             * Unicode script "Marchen".
             * @since 11
             */
            MARCHEN,

            /**
             * Unicode script "Newa".
             * @since 11
             */
            NEWA,

            /**
             * Unicode script "Osage".
             * @since 11
             */
            OSAGE,

            /**
             * Unicode script "Tangut".
             * @since 11
             */
            TANGUT,

            /**
             * Unicode script "Masaram Gondi".
             * @since 11
             */
            MASARAM_GONDI,

            /**
             * Unicode script "Nushu".
             * @since 11
             */
            NUSHU,

            /**
             * Unicode script "Soyombo".
             * @since 11
             */
            SOYOMBO,

            /**
             * Unicode script "Zanabazar Square".
             * @since 11
             */
            ZANABAZAR_SQUARE,

            /**
             * Unicode script "Hanifi Rohingya".
             * @since 12
             */
            HANIFI_ROHINGYA,

            /**
             * Unicode script "Old Sogdian".
             * @since 12
             */
            OLD_SOGDIAN,

            /**
             * Unicode script "Sogdian".
             * @since 12
             */
            SOGDIAN,

            /**
             * Unicode script "Dogra".
             * @since 12
             */
            DOGRA,

            /**
             * Unicode script "Gunjala Gondi".
             * @since 12
             */
            GUNJALA_GONDI,

            /**
             * Unicode script "Makasar".
             * @since 12
             */
            MAKASAR,

            /**
             * Unicode script "Medefaidrin".
             * @since 12
             */
            MEDEFAIDRIN,

            /**
             * Unicode script "Elymaic".
             * @since 13
             */
            ELYMAIC,

            /**
             * Unicode script "Nandinagari".
             * @since 13
             */
            NANDINAGARI,

            /**
             * Unicode script "Nyiakeng Puachue Hmong".
             * @since 13
             */
            NYIAKENG_PUACHUE_HMONG,

            /**
             * Unicode script "Wancho".
             * @since 13
             */
            WANCHO,

            /**
             * Unicode script "Yezidi".
             * @since 15
             */
            YEZIDI,

            /**
             * Unicode script "Chorasmian".
             * @since 15
             */
            CHORASMIAN,

            /**
             * Unicode script "Dives Akuru".
             * @since 15
             */
            DIVES_AKURU,

            /**
             * Unicode script "Khitan Small Script".
             * @since 15
             */
            KHITAN_SMALL_SCRIPT,

            /**
             * Unicode script "Vithkuqi".
             * @since 19
             */
            VITHKUQI,

            /**
             * Unicode script "Old Uyghur".
             * @since 19
             */
            OLD_UYGHUR,

            /**
             * Unicode script "Cypro Minoan".
             * @since 19
             */
            CYPRO_MINOAN,

            /**
             * Unicode script "Tangsa".
             * @since 19
             */
            TANGSA,

            /**
             * Unicode script "Toto".
             * @since 19
             */
            TOTO,

            /**
             * Unicode script "Kawi".
             * @since 20
             */
            KAWI,

            /**
             * Unicode script "Nag Mundari".
             * @since 20
             */
            NAG_MUNDARI,

            /**
             * Unicode script "Todhri".
             * @since 24
             */
            TODHRI,

            /**
             * Unicode script "Garay".
             * @since 24
             */
            GARAY,

            /**
             * Unicode script "Tulu Tigalari".
             * @since 24
             */
            TULU_TIGALARI,

            /**
             * Unicode script "Sunuwar".
             * @since 24
             */
            SUNUWAR,

            /**
             * Unicode script "Gurung Khema".
             * @since 24
             */
            GURUNG_KHEMA,

            /**
             * Unicode script "Kirat Rai".
             * @since 24
             */
            KIRAT_RAI,

            /**
             * Unicode script "Ol Onal".
             * @since 24
             */
            OL_ONAL,

            /**
             * Unicode script "Unknown".
             */
            UNKNOWN; // must be the last enum constant for calculating the size of "aliases" hash map.

            companion object {
                private val scriptStarts = intArrayOf(
                    0x0000,  // 0000..0040; COMMON
                    0x0041,  // 0041..005A; LATIN
                    0x005B,  // 005B..0060; COMMON
                    0x0061,  // 0061..007A; LATIN
                    0x007B,  // 007B..00A9; COMMON
                    0x00AA,  // 00AA      ; LATIN
                    0x00AB,  // 00AB..00B9; COMMON
                    0x00BA,  // 00BA      ; LATIN
                    0x00BB,  // 00BB..00BF; COMMON
                    0x00C0,  // 00C0..00D6; LATIN
                    0x00D7,  // 00D7      ; COMMON
                    0x00D8,  // 00D8..00F6; LATIN
                    0x00F7,  // 00F7      ; COMMON
                    0x00F8,  // 00F8..02B8; LATIN
                    0x02B9,  // 02B9..02DF; COMMON
                    0x02E0,  // 02E0..02E4; LATIN
                    0x02E5,  // 02E5..02E9; COMMON
                    0x02EA,  // 02EA..02EB; BOPOMOFO
                    0x02EC,  // 02EC..02FF; COMMON
                    0x0300,  // 0300..036F; INHERITED
                    0x0370,  // 0370..0373; GREEK
                    0x0374,  // 0374      ; COMMON
                    0x0375,  // 0375..0377; GREEK
                    0x0378,  // 0378..0379; UNKNOWN
                    0x037A,  // 037A..037D; GREEK
                    0x037E,  // 037E      ; COMMON
                    0x037F,  // 037F      ; GREEK
                    0x0380,  // 0380..0383; UNKNOWN
                    0x0384,  // 0384      ; GREEK
                    0x0385,  // 0385      ; COMMON
                    0x0386,  // 0386      ; GREEK
                    0x0387,  // 0387      ; COMMON
                    0x0388,  // 0388..038A; GREEK
                    0x038B,  // 038B      ; UNKNOWN
                    0x038C,  // 038C      ; GREEK
                    0x038D,  // 038D      ; UNKNOWN
                    0x038E,  // 038E..03A1; GREEK
                    0x03A2,  // 03A2      ; UNKNOWN
                    0x03A3,  // 03A3..03E1; GREEK
                    0x03E2,  // 03E2..03EF; COPTIC
                    0x03F0,  // 03F0..03FF; GREEK
                    0x0400,  // 0400..0484; CYRILLIC
                    0x0485,  // 0485..0486; INHERITED
                    0x0487,  // 0487..052F; CYRILLIC
                    0x0530,  // 0530      ; UNKNOWN
                    0x0531,  // 0531..0556; ARMENIAN
                    0x0557,  // 0557..0558; UNKNOWN
                    0x0559,  // 0559..058A; ARMENIAN
                    0x058B,  // 058B..058C; UNKNOWN
                    0x058D,  // 058D..058F; ARMENIAN
                    0x0590,  // 0590      ; UNKNOWN
                    0x0591,  // 0591..05C7; HEBREW
                    0x05C8,  // 05C8..05CF; UNKNOWN
                    0x05D0,  // 05D0..05EA; HEBREW
                    0x05EB,  // 05EB..05EE; UNKNOWN
                    0x05EF,  // 05EF..05F4; HEBREW
                    0x05F5,  // 05F5..05FF; UNKNOWN
                    0x0600,  // 0600..0604; ARABIC
                    0x0605,  // 0605      ; COMMON
                    0x0606,  // 0606..060B; ARABIC
                    0x060C,  // 060C      ; COMMON
                    0x060D,  // 060D..061A; ARABIC
                    0x061B,  // 061B      ; COMMON
                    0x061C,  // 061C..061E; ARABIC
                    0x061F,  // 061F      ; COMMON
                    0x0620,  // 0620..063F; ARABIC
                    0x0640,  // 0640      ; COMMON
                    0x0641,  // 0641..064A; ARABIC
                    0x064B,  // 064B..0655; INHERITED
                    0x0656,  // 0656..066F; ARABIC
                    0x0670,  // 0670      ; INHERITED
                    0x0671,  // 0671..06DC; ARABIC
                    0x06DD,  // 06DD      ; COMMON
                    0x06DE,  // 06DE..06FF; ARABIC
                    0x0700,  // 0700..070D; SYRIAC
                    0x070E,  // 070E      ; UNKNOWN
                    0x070F,  // 070F..074A; SYRIAC
                    0x074B,  // 074B..074C; UNKNOWN
                    0x074D,  // 074D..074F; SYRIAC
                    0x0750,  // 0750..077F; ARABIC
                    0x0780,  // 0780..07B1; THAANA
                    0x07B2,  // 07B2..07BF; UNKNOWN
                    0x07C0,  // 07C0..07FA; NKO
                    0x07FB,  // 07FB..07FC; UNKNOWN
                    0x07FD,  // 07FD..07FF; NKO
                    0x0800,  // 0800..082D; SAMARITAN
                    0x082E,  // 082E..082F; UNKNOWN
                    0x0830,  // 0830..083E; SAMARITAN
                    0x083F,  // 083F      ; UNKNOWN
                    0x0840,  // 0840..085B; MANDAIC
                    0x085C,  // 085C..085D; UNKNOWN
                    0x085E,  // 085E      ; MANDAIC
                    0x085F,  // 085F      ; UNKNOWN
                    0x0860,  // 0860..086A; SYRIAC
                    0x086B,  // 086B..086F; UNKNOWN
                    0x0870,  // 0870..088E; ARABIC
                    0x088F,  // 088F      ; UNKNOWN
                    0x0890,  // 0890..0891; ARABIC
                    0x0892,  // 0892..0896; UNKNOWN
                    0x0897,  // 0897..08E1; ARABIC
                    0x08E2,  // 08E2      ; COMMON
                    0x08E3,  // 08E3..08FF; ARABIC
                    0x0900,  // 0900..0950; DEVANAGARI
                    0x0951,  // 0951..0954; INHERITED
                    0x0955,  // 0955..0963; DEVANAGARI
                    0x0964,  // 0964..0965; COMMON
                    0x0966,  // 0966..097F; DEVANAGARI
                    0x0980,  // 0980..0983; BENGALI
                    0x0984,  // 0984      ; UNKNOWN
                    0x0985,  // 0985..098C; BENGALI
                    0x098D,  // 098D..098E; UNKNOWN
                    0x098F,  // 098F..0990; BENGALI
                    0x0991,  // 0991..0992; UNKNOWN
                    0x0993,  // 0993..09A8; BENGALI
                    0x09A9,  // 09A9      ; UNKNOWN
                    0x09AA,  // 09AA..09B0; BENGALI
                    0x09B1,  // 09B1      ; UNKNOWN
                    0x09B2,  // 09B2      ; BENGALI
                    0x09B3,  // 09B3..09B5; UNKNOWN
                    0x09B6,  // 09B6..09B9; BENGALI
                    0x09BA,  // 09BA..09BB; UNKNOWN
                    0x09BC,  // 09BC..09C4; BENGALI
                    0x09C5,  // 09C5..09C6; UNKNOWN
                    0x09C7,  // 09C7..09C8; BENGALI
                    0x09C9,  // 09C9..09CA; UNKNOWN
                    0x09CB,  // 09CB..09CE; BENGALI
                    0x09CF,  // 09CF..09D6; UNKNOWN
                    0x09D7,  // 09D7      ; BENGALI
                    0x09D8,  // 09D8..09DB; UNKNOWN
                    0x09DC,  // 09DC..09DD; BENGALI
                    0x09DE,  // 09DE      ; UNKNOWN
                    0x09DF,  // 09DF..09E3; BENGALI
                    0x09E4,  // 09E4..09E5; UNKNOWN
                    0x09E6,  // 09E6..09FE; BENGALI
                    0x09FF,  // 09FF..0A00; UNKNOWN
                    0x0A01,  // 0A01..0A03; GURMUKHI
                    0x0A04,  // 0A04      ; UNKNOWN
                    0x0A05,  // 0A05..0A0A; GURMUKHI
                    0x0A0B,  // 0A0B..0A0E; UNKNOWN
                    0x0A0F,  // 0A0F..0A10; GURMUKHI
                    0x0A11,  // 0A11..0A12; UNKNOWN
                    0x0A13,  // 0A13..0A28; GURMUKHI
                    0x0A29,  // 0A29      ; UNKNOWN
                    0x0A2A,  // 0A2A..0A30; GURMUKHI
                    0x0A31,  // 0A31      ; UNKNOWN
                    0x0A32,  // 0A32..0A33; GURMUKHI
                    0x0A34,  // 0A34      ; UNKNOWN
                    0x0A35,  // 0A35..0A36; GURMUKHI
                    0x0A37,  // 0A37      ; UNKNOWN
                    0x0A38,  // 0A38..0A39; GURMUKHI
                    0x0A3A,  // 0A3A..0A3B; UNKNOWN
                    0x0A3C,  // 0A3C      ; GURMUKHI
                    0x0A3D,  // 0A3D      ; UNKNOWN
                    0x0A3E,  // 0A3E..0A42; GURMUKHI
                    0x0A43,  // 0A43..0A46; UNKNOWN
                    0x0A47,  // 0A47..0A48; GURMUKHI
                    0x0A49,  // 0A49..0A4A; UNKNOWN
                    0x0A4B,  // 0A4B..0A4D; GURMUKHI
                    0x0A4E,  // 0A4E..0A50; UNKNOWN
                    0x0A51,  // 0A51      ; GURMUKHI
                    0x0A52,  // 0A52..0A58; UNKNOWN
                    0x0A59,  // 0A59..0A5C; GURMUKHI
                    0x0A5D,  // 0A5D      ; UNKNOWN
                    0x0A5E,  // 0A5E      ; GURMUKHI
                    0x0A5F,  // 0A5F..0A65; UNKNOWN
                    0x0A66,  // 0A66..0A76; GURMUKHI
                    0x0A77,  // 0A77..0A80; UNKNOWN
                    0x0A81,  // 0A81..0A83; GUJARATI
                    0x0A84,  // 0A84      ; UNKNOWN
                    0x0A85,  // 0A85..0A8D; GUJARATI
                    0x0A8E,  // 0A8E      ; UNKNOWN
                    0x0A8F,  // 0A8F..0A91; GUJARATI
                    0x0A92,  // 0A92      ; UNKNOWN
                    0x0A93,  // 0A93..0AA8; GUJARATI
                    0x0AA9,  // 0AA9      ; UNKNOWN
                    0x0AAA,  // 0AAA..0AB0; GUJARATI
                    0x0AB1,  // 0AB1      ; UNKNOWN
                    0x0AB2,  // 0AB2..0AB3; GUJARATI
                    0x0AB4,  // 0AB4      ; UNKNOWN
                    0x0AB5,  // 0AB5..0AB9; GUJARATI
                    0x0ABA,  // 0ABA..0ABB; UNKNOWN
                    0x0ABC,  // 0ABC..0AC5; GUJARATI
                    0x0AC6,  // 0AC6      ; UNKNOWN
                    0x0AC7,  // 0AC7..0AC9; GUJARATI
                    0x0ACA,  // 0ACA      ; UNKNOWN
                    0x0ACB,  // 0ACB..0ACD; GUJARATI
                    0x0ACE,  // 0ACE..0ACF; UNKNOWN
                    0x0AD0,  // 0AD0      ; GUJARATI
                    0x0AD1,  // 0AD1..0ADF; UNKNOWN
                    0x0AE0,  // 0AE0..0AE3; GUJARATI
                    0x0AE4,  // 0AE4..0AE5; UNKNOWN
                    0x0AE6,  // 0AE6..0AF1; GUJARATI
                    0x0AF2,  // 0AF2..0AF8; UNKNOWN
                    0x0AF9,  // 0AF9..0AFF; GUJARATI
                    0x0B00,  // 0B00      ; UNKNOWN
                    0x0B01,  // 0B01..0B03; ORIYA
                    0x0B04,  // 0B04      ; UNKNOWN
                    0x0B05,  // 0B05..0B0C; ORIYA
                    0x0B0D,  // 0B0D..0B0E; UNKNOWN
                    0x0B0F,  // 0B0F..0B10; ORIYA
                    0x0B11,  // 0B11..0B12; UNKNOWN
                    0x0B13,  // 0B13..0B28; ORIYA
                    0x0B29,  // 0B29      ; UNKNOWN
                    0x0B2A,  // 0B2A..0B30; ORIYA
                    0x0B31,  // 0B31      ; UNKNOWN
                    0x0B32,  // 0B32..0B33; ORIYA
                    0x0B34,  // 0B34      ; UNKNOWN
                    0x0B35,  // 0B35..0B39; ORIYA
                    0x0B3A,  // 0B3A..0B3B; UNKNOWN
                    0x0B3C,  // 0B3C..0B44; ORIYA
                    0x0B45,  // 0B45..0B46; UNKNOWN
                    0x0B47,  // 0B47..0B48; ORIYA
                    0x0B49,  // 0B49..0B4A; UNKNOWN
                    0x0B4B,  // 0B4B..0B4D; ORIYA
                    0x0B4E,  // 0B4E..0B54; UNKNOWN
                    0x0B55,  // 0B55..0B57; ORIYA
                    0x0B58,  // 0B58..0B5B; UNKNOWN
                    0x0B5C,  // 0B5C..0B5D; ORIYA
                    0x0B5E,  // 0B5E      ; UNKNOWN
                    0x0B5F,  // 0B5F..0B63; ORIYA
                    0x0B64,  // 0B64..0B65; UNKNOWN
                    0x0B66,  // 0B66..0B77; ORIYA
                    0x0B78,  // 0B78..0B81; UNKNOWN
                    0x0B82,  // 0B82..0B83; TAMIL
                    0x0B84,  // 0B84      ; UNKNOWN
                    0x0B85,  // 0B85..0B8A; TAMIL
                    0x0B8B,  // 0B8B..0B8D; UNKNOWN
                    0x0B8E,  // 0B8E..0B90; TAMIL
                    0x0B91,  // 0B91      ; UNKNOWN
                    0x0B92,  // 0B92..0B95; TAMIL
                    0x0B96,  // 0B96..0B98; UNKNOWN
                    0x0B99,  // 0B99..0B9A; TAMIL
                    0x0B9B,  // 0B9B      ; UNKNOWN
                    0x0B9C,  // 0B9C      ; TAMIL
                    0x0B9D,  // 0B9D      ; UNKNOWN
                    0x0B9E,  // 0B9E..0B9F; TAMIL
                    0x0BA0,  // 0BA0..0BA2; UNKNOWN
                    0x0BA3,  // 0BA3..0BA4; TAMIL
                    0x0BA5,  // 0BA5..0BA7; UNKNOWN
                    0x0BA8,  // 0BA8..0BAA; TAMIL
                    0x0BAB,  // 0BAB..0BAD; UNKNOWN
                    0x0BAE,  // 0BAE..0BB9; TAMIL
                    0x0BBA,  // 0BBA..0BBD; UNKNOWN
                    0x0BBE,  // 0BBE..0BC2; TAMIL
                    0x0BC3,  // 0BC3..0BC5; UNKNOWN
                    0x0BC6,  // 0BC6..0BC8; TAMIL
                    0x0BC9,  // 0BC9      ; UNKNOWN
                    0x0BCA,  // 0BCA..0BCD; TAMIL
                    0x0BCE,  // 0BCE..0BCF; UNKNOWN
                    0x0BD0,  // 0BD0      ; TAMIL
                    0x0BD1,  // 0BD1..0BD6; UNKNOWN
                    0x0BD7,  // 0BD7      ; TAMIL
                    0x0BD8,  // 0BD8..0BE5; UNKNOWN
                    0x0BE6,  // 0BE6..0BFA; TAMIL
                    0x0BFB,  // 0BFB..0BFF; UNKNOWN
                    0x0C00,  // 0C00..0C0C; TELUGU
                    0x0C0D,  // 0C0D      ; UNKNOWN
                    0x0C0E,  // 0C0E..0C10; TELUGU
                    0x0C11,  // 0C11      ; UNKNOWN
                    0x0C12,  // 0C12..0C28; TELUGU
                    0x0C29,  // 0C29      ; UNKNOWN
                    0x0C2A,  // 0C2A..0C39; TELUGU
                    0x0C3A,  // 0C3A..0C3B; UNKNOWN
                    0x0C3C,  // 0C3C..0C44; TELUGU
                    0x0C45,  // 0C45      ; UNKNOWN
                    0x0C46,  // 0C46..0C48; TELUGU
                    0x0C49,  // 0C49      ; UNKNOWN
                    0x0C4A,  // 0C4A..0C4D; TELUGU
                    0x0C4E,  // 0C4E..0C54; UNKNOWN
                    0x0C55,  // 0C55..0C56; TELUGU
                    0x0C57,  // 0C57      ; UNKNOWN
                    0x0C58,  // 0C58..0C5A; TELUGU
                    0x0C5B,  // 0C5B..0C5C; UNKNOWN
                    0x0C5D,  // 0C5D      ; TELUGU
                    0x0C5E,  // 0C5E..0C5F; UNKNOWN
                    0x0C60,  // 0C60..0C63; TELUGU
                    0x0C64,  // 0C64..0C65; UNKNOWN
                    0x0C66,  // 0C66..0C6F; TELUGU
                    0x0C70,  // 0C70..0C76; UNKNOWN
                    0x0C77,  // 0C77..0C7F; TELUGU
                    0x0C80,  // 0C80..0C8C; KANNADA
                    0x0C8D,  // 0C8D      ; UNKNOWN
                    0x0C8E,  // 0C8E..0C90; KANNADA
                    0x0C91,  // 0C91      ; UNKNOWN
                    0x0C92,  // 0C92..0CA8; KANNADA
                    0x0CA9,  // 0CA9      ; UNKNOWN
                    0x0CAA,  // 0CAA..0CB3; KANNADA
                    0x0CB4,  // 0CB4      ; UNKNOWN
                    0x0CB5,  // 0CB5..0CB9; KANNADA
                    0x0CBA,  // 0CBA..0CBB; UNKNOWN
                    0x0CBC,  // 0CBC..0CC4; KANNADA
                    0x0CC5,  // 0CC5      ; UNKNOWN
                    0x0CC6,  // 0CC6..0CC8; KANNADA
                    0x0CC9,  // 0CC9      ; UNKNOWN
                    0x0CCA,  // 0CCA..0CCD; KANNADA
                    0x0CCE,  // 0CCE..0CD4; UNKNOWN
                    0x0CD5,  // 0CD5..0CD6; KANNADA
                    0x0CD7,  // 0CD7..0CDC; UNKNOWN
                    0x0CDD,  // 0CDD..0CDE; KANNADA
                    0x0CDF,  // 0CDF      ; UNKNOWN
                    0x0CE0,  // 0CE0..0CE3; KANNADA
                    0x0CE4,  // 0CE4..0CE5; UNKNOWN
                    0x0CE6,  // 0CE6..0CEF; KANNADA
                    0x0CF0,  // 0CF0      ; UNKNOWN
                    0x0CF1,  // 0CF1..0CF3; KANNADA
                    0x0CF4,  // 0CF4..0CFF; UNKNOWN
                    0x0D00,  // 0D00..0D0C; MALAYALAM
                    0x0D0D,  // 0D0D      ; UNKNOWN
                    0x0D0E,  // 0D0E..0D10; MALAYALAM
                    0x0D11,  // 0D11      ; UNKNOWN
                    0x0D12,  // 0D12..0D44; MALAYALAM
                    0x0D45,  // 0D45      ; UNKNOWN
                    0x0D46,  // 0D46..0D48; MALAYALAM
                    0x0D49,  // 0D49      ; UNKNOWN
                    0x0D4A,  // 0D4A..0D4F; MALAYALAM
                    0x0D50,  // 0D50..0D53; UNKNOWN
                    0x0D54,  // 0D54..0D63; MALAYALAM
                    0x0D64,  // 0D64..0D65; UNKNOWN
                    0x0D66,  // 0D66..0D7F; MALAYALAM
                    0x0D80,  // 0D80      ; UNKNOWN
                    0x0D81,  // 0D81..0D83; SINHALA
                    0x0D84,  // 0D84      ; UNKNOWN
                    0x0D85,  // 0D85..0D96; SINHALA
                    0x0D97,  // 0D97..0D99; UNKNOWN
                    0x0D9A,  // 0D9A..0DB1; SINHALA
                    0x0DB2,  // 0DB2      ; UNKNOWN
                    0x0DB3,  // 0DB3..0DBB; SINHALA
                    0x0DBC,  // 0DBC      ; UNKNOWN
                    0x0DBD,  // 0DBD      ; SINHALA
                    0x0DBE,  // 0DBE..0DBF; UNKNOWN
                    0x0DC0,  // 0DC0..0DC6; SINHALA
                    0x0DC7,  // 0DC7..0DC9; UNKNOWN
                    0x0DCA,  // 0DCA      ; SINHALA
                    0x0DCB,  // 0DCB..0DCE; UNKNOWN
                    0x0DCF,  // 0DCF..0DD4; SINHALA
                    0x0DD5,  // 0DD5      ; UNKNOWN
                    0x0DD6,  // 0DD6      ; SINHALA
                    0x0DD7,  // 0DD7      ; UNKNOWN
                    0x0DD8,  // 0DD8..0DDF; SINHALA
                    0x0DE0,  // 0DE0..0DE5; UNKNOWN
                    0x0DE6,  // 0DE6..0DEF; SINHALA
                    0x0DF0,  // 0DF0..0DF1; UNKNOWN
                    0x0DF2,  // 0DF2..0DF4; SINHALA
                    0x0DF5,  // 0DF5..0E00; UNKNOWN
                    0x0E01,  // 0E01..0E3A; THAI
                    0x0E3B,  // 0E3B..0E3E; UNKNOWN
                    0x0E3F,  // 0E3F      ; COMMON
                    0x0E40,  // 0E40..0E5B; THAI
                    0x0E5C,  // 0E5C..0E80; UNKNOWN
                    0x0E81,  // 0E81..0E82; LAO
                    0x0E83,  // 0E83      ; UNKNOWN
                    0x0E84,  // 0E84      ; LAO
                    0x0E85,  // 0E85      ; UNKNOWN
                    0x0E86,  // 0E86..0E8A; LAO
                    0x0E8B,  // 0E8B      ; UNKNOWN
                    0x0E8C,  // 0E8C..0EA3; LAO
                    0x0EA4,  // 0EA4      ; UNKNOWN
                    0x0EA5,  // 0EA5      ; LAO
                    0x0EA6,  // 0EA6      ; UNKNOWN
                    0x0EA7,  // 0EA7..0EBD; LAO
                    0x0EBE,  // 0EBE..0EBF; UNKNOWN
                    0x0EC0,  // 0EC0..0EC4; LAO
                    0x0EC5,  // 0EC5      ; UNKNOWN
                    0x0EC6,  // 0EC6      ; LAO
                    0x0EC7,  // 0EC7      ; UNKNOWN
                    0x0EC8,  // 0EC8..0ECE; LAO
                    0x0ECF,  // 0ECF      ; UNKNOWN
                    0x0ED0,  // 0ED0..0ED9; LAO
                    0x0EDA,  // 0EDA..0EDB; UNKNOWN
                    0x0EDC,  // 0EDC..0EDF; LAO
                    0x0EE0,  // 0EE0..0EFF; UNKNOWN
                    0x0F00,  // 0F00..0F47; TIBETAN
                    0x0F48,  // 0F48      ; UNKNOWN
                    0x0F49,  // 0F49..0F6C; TIBETAN
                    0x0F6D,  // 0F6D..0F70; UNKNOWN
                    0x0F71,  // 0F71..0F97; TIBETAN
                    0x0F98,  // 0F98      ; UNKNOWN
                    0x0F99,  // 0F99..0FBC; TIBETAN
                    0x0FBD,  // 0FBD      ; UNKNOWN
                    0x0FBE,  // 0FBE..0FCC; TIBETAN
                    0x0FCD,  // 0FCD      ; UNKNOWN
                    0x0FCE,  // 0FCE..0FD4; TIBETAN
                    0x0FD5,  // 0FD5..0FD8; COMMON
                    0x0FD9,  // 0FD9..0FDA; TIBETAN
                    0x0FDB,  // 0FDB..0FFF; UNKNOWN
                    0x1000,  // 1000..109F; MYANMAR
                    0x10A0,  // 10A0..10C5; GEORGIAN
                    0x10C6,  // 10C6      ; UNKNOWN
                    0x10C7,  // 10C7      ; GEORGIAN
                    0x10C8,  // 10C8..10CC; UNKNOWN
                    0x10CD,  // 10CD      ; GEORGIAN
                    0x10CE,  // 10CE..10CF; UNKNOWN
                    0x10D0,  // 10D0..10FA; GEORGIAN
                    0x10FB,  // 10FB      ; COMMON
                    0x10FC,  // 10FC..10FF; GEORGIAN
                    0x1100,  // 1100..11FF; HANGUL
                    0x1200,  // 1200..1248; ETHIOPIC
                    0x1249,  // 1249      ; UNKNOWN
                    0x124A,  // 124A..124D; ETHIOPIC
                    0x124E,  // 124E..124F; UNKNOWN
                    0x1250,  // 1250..1256; ETHIOPIC
                    0x1257,  // 1257      ; UNKNOWN
                    0x1258,  // 1258      ; ETHIOPIC
                    0x1259,  // 1259      ; UNKNOWN
                    0x125A,  // 125A..125D; ETHIOPIC
                    0x125E,  // 125E..125F; UNKNOWN
                    0x1260,  // 1260..1288; ETHIOPIC
                    0x1289,  // 1289      ; UNKNOWN
                    0x128A,  // 128A..128D; ETHIOPIC
                    0x128E,  // 128E..128F; UNKNOWN
                    0x1290,  // 1290..12B0; ETHIOPIC
                    0x12B1,  // 12B1      ; UNKNOWN
                    0x12B2,  // 12B2..12B5; ETHIOPIC
                    0x12B6,  // 12B6..12B7; UNKNOWN
                    0x12B8,  // 12B8..12BE; ETHIOPIC
                    0x12BF,  // 12BF      ; UNKNOWN
                    0x12C0,  // 12C0      ; ETHIOPIC
                    0x12C1,  // 12C1      ; UNKNOWN
                    0x12C2,  // 12C2..12C5; ETHIOPIC
                    0x12C6,  // 12C6..12C7; UNKNOWN
                    0x12C8,  // 12C8..12D6; ETHIOPIC
                    0x12D7,  // 12D7      ; UNKNOWN
                    0x12D8,  // 12D8..1310; ETHIOPIC
                    0x1311,  // 1311      ; UNKNOWN
                    0x1312,  // 1312..1315; ETHIOPIC
                    0x1316,  // 1316..1317; UNKNOWN
                    0x1318,  // 1318..135A; ETHIOPIC
                    0x135B,  // 135B..135C; UNKNOWN
                    0x135D,  // 135D..137C; ETHIOPIC
                    0x137D,  // 137D..137F; UNKNOWN
                    0x1380,  // 1380..1399; ETHIOPIC
                    0x139A,  // 139A..139F; UNKNOWN
                    0x13A0,  // 13A0..13F5; CHEROKEE
                    0x13F6,  // 13F6..13F7; UNKNOWN
                    0x13F8,  // 13F8..13FD; CHEROKEE
                    0x13FE,  // 13FE..13FF; UNKNOWN
                    0x1400,  // 1400..167F; CANADIAN_ABORIGINAL
                    0x1680,  // 1680..169C; OGHAM
                    0x169D,  // 169D..169F; UNKNOWN
                    0x16A0,  // 16A0..16EA; RUNIC
                    0x16EB,  // 16EB..16ED; COMMON
                    0x16EE,  // 16EE..16F8; RUNIC
                    0x16F9,  // 16F9..16FF; UNKNOWN
                    0x1700,  // 1700..1715; TAGALOG
                    0x1716,  // 1716..171E; UNKNOWN
                    0x171F,  // 171F      ; TAGALOG
                    0x1720,  // 1720..1734; HANUNOO
                    0x1735,  // 1735..1736; COMMON
                    0x1737,  // 1737..173F; UNKNOWN
                    0x1740,  // 1740..1753; BUHID
                    0x1754,  // 1754..175F; UNKNOWN
                    0x1760,  // 1760..176C; TAGBANWA
                    0x176D,  // 176D      ; UNKNOWN
                    0x176E,  // 176E..1770; TAGBANWA
                    0x1771,  // 1771      ; UNKNOWN
                    0x1772,  // 1772..1773; TAGBANWA
                    0x1774,  // 1774..177F; UNKNOWN
                    0x1780,  // 1780..17DD; KHMER
                    0x17DE,  // 17DE..17DF; UNKNOWN
                    0x17E0,  // 17E0..17E9; KHMER
                    0x17EA,  // 17EA..17EF; UNKNOWN
                    0x17F0,  // 17F0..17F9; KHMER
                    0x17FA,  // 17FA..17FF; UNKNOWN
                    0x1800,  // 1800..1801; MONGOLIAN
                    0x1802,  // 1802..1803; COMMON
                    0x1804,  // 1804      ; MONGOLIAN
                    0x1805,  // 1805      ; COMMON
                    0x1806,  // 1806..1819; MONGOLIAN
                    0x181A,  // 181A..181F; UNKNOWN
                    0x1820,  // 1820..1878; MONGOLIAN
                    0x1879,  // 1879..187F; UNKNOWN
                    0x1880,  // 1880..18AA; MONGOLIAN
                    0x18AB,  // 18AB..18AF; UNKNOWN
                    0x18B0,  // 18B0..18F5; CANADIAN_ABORIGINAL
                    0x18F6,  // 18F6..18FF; UNKNOWN
                    0x1900,  // 1900..191E; LIMBU
                    0x191F,  // 191F      ; UNKNOWN
                    0x1920,  // 1920..192B; LIMBU
                    0x192C,  // 192C..192F; UNKNOWN
                    0x1930,  // 1930..193B; LIMBU
                    0x193C,  // 193C..193F; UNKNOWN
                    0x1940,  // 1940      ; LIMBU
                    0x1941,  // 1941..1943; UNKNOWN
                    0x1944,  // 1944..194F; LIMBU
                    0x1950,  // 1950..196D; TAI_LE
                    0x196E,  // 196E..196F; UNKNOWN
                    0x1970,  // 1970..1974; TAI_LE
                    0x1975,  // 1975..197F; UNKNOWN
                    0x1980,  // 1980..19AB; NEW_TAI_LUE
                    0x19AC,  // 19AC..19AF; UNKNOWN
                    0x19B0,  // 19B0..19C9; NEW_TAI_LUE
                    0x19CA,  // 19CA..19CF; UNKNOWN
                    0x19D0,  // 19D0..19DA; NEW_TAI_LUE
                    0x19DB,  // 19DB..19DD; UNKNOWN
                    0x19DE,  // 19DE..19DF; NEW_TAI_LUE
                    0x19E0,  // 19E0..19FF; KHMER
                    0x1A00,  // 1A00..1A1B; BUGINESE
                    0x1A1C,  // 1A1C..1A1D; UNKNOWN
                    0x1A1E,  // 1A1E..1A1F; BUGINESE
                    0x1A20,  // 1A20..1A5E; TAI_THAM
                    0x1A5F,  // 1A5F      ; UNKNOWN
                    0x1A60,  // 1A60..1A7C; TAI_THAM
                    0x1A7D,  // 1A7D..1A7E; UNKNOWN
                    0x1A7F,  // 1A7F..1A89; TAI_THAM
                    0x1A8A,  // 1A8A..1A8F; UNKNOWN
                    0x1A90,  // 1A90..1A99; TAI_THAM
                    0x1A9A,  // 1A9A..1A9F; UNKNOWN
                    0x1AA0,  // 1AA0..1AAD; TAI_THAM
                    0x1AAE,  // 1AAE..1AAF; UNKNOWN
                    0x1AB0,  // 1AB0..1ACE; INHERITED
                    0x1ACF,  // 1ACF..1AFF; UNKNOWN
                    0x1B00,  // 1B00..1B4C; BALINESE
                    0x1B4D,  // 1B4D      ; UNKNOWN
                    0x1B4E,  // 1B4E..1B7F; BALINESE
                    0x1B80,  // 1B80..1BBF; SUNDANESE
                    0x1BC0,  // 1BC0..1BF3; BATAK
                    0x1BF4,  // 1BF4..1BFB; UNKNOWN
                    0x1BFC,  // 1BFC..1BFF; BATAK
                    0x1C00,  // 1C00..1C37; LEPCHA
                    0x1C38,  // 1C38..1C3A; UNKNOWN
                    0x1C3B,  // 1C3B..1C49; LEPCHA
                    0x1C4A,  // 1C4A..1C4C; UNKNOWN
                    0x1C4D,  // 1C4D..1C4F; LEPCHA
                    0x1C50,  // 1C50..1C7F; OL_CHIKI
                    0x1C80,  // 1C80..1C8A; CYRILLIC
                    0x1C8B,  // 1C8B..1C8F; UNKNOWN
                    0x1C90,  // 1C90..1CBA; GEORGIAN
                    0x1CBB,  // 1CBB..1CBC; UNKNOWN
                    0x1CBD,  // 1CBD..1CBF; GEORGIAN
                    0x1CC0,  // 1CC0..1CC7; SUNDANESE
                    0x1CC8,  // 1CC8..1CCF; UNKNOWN
                    0x1CD0,  // 1CD0..1CD2; INHERITED
                    0x1CD3,  // 1CD3      ; COMMON
                    0x1CD4,  // 1CD4..1CE0; INHERITED
                    0x1CE1,  // 1CE1      ; COMMON
                    0x1CE2,  // 1CE2..1CE8; INHERITED
                    0x1CE9,  // 1CE9..1CEC; COMMON
                    0x1CED,  // 1CED      ; INHERITED
                    0x1CEE,  // 1CEE..1CF3; COMMON
                    0x1CF4,  // 1CF4      ; INHERITED
                    0x1CF5,  // 1CF5..1CF7; COMMON
                    0x1CF8,  // 1CF8..1CF9; INHERITED
                    0x1CFA,  // 1CFA      ; COMMON
                    0x1CFB,  // 1CFB..1CFF; UNKNOWN
                    0x1D00,  // 1D00..1D25; LATIN
                    0x1D26,  // 1D26..1D2A; GREEK
                    0x1D2B,  // 1D2B      ; CYRILLIC
                    0x1D2C,  // 1D2C..1D5C; LATIN
                    0x1D5D,  // 1D5D..1D61; GREEK
                    0x1D62,  // 1D62..1D65; LATIN
                    0x1D66,  // 1D66..1D6A; GREEK
                    0x1D6B,  // 1D6B..1D77; LATIN
                    0x1D78,  // 1D78      ; CYRILLIC
                    0x1D79,  // 1D79..1DBE; LATIN
                    0x1DBF,  // 1DBF      ; GREEK
                    0x1DC0,  // 1DC0..1DFF; INHERITED
                    0x1E00,  // 1E00..1EFF; LATIN
                    0x1F00,  // 1F00..1F15; GREEK
                    0x1F16,  // 1F16..1F17; UNKNOWN
                    0x1F18,  // 1F18..1F1D; GREEK
                    0x1F1E,  // 1F1E..1F1F; UNKNOWN
                    0x1F20,  // 1F20..1F45; GREEK
                    0x1F46,  // 1F46..1F47; UNKNOWN
                    0x1F48,  // 1F48..1F4D; GREEK
                    0x1F4E,  // 1F4E..1F4F; UNKNOWN
                    0x1F50,  // 1F50..1F57; GREEK
                    0x1F58,  // 1F58      ; UNKNOWN
                    0x1F59,  // 1F59      ; GREEK
                    0x1F5A,  // 1F5A      ; UNKNOWN
                    0x1F5B,  // 1F5B      ; GREEK
                    0x1F5C,  // 1F5C      ; UNKNOWN
                    0x1F5D,  // 1F5D      ; GREEK
                    0x1F5E,  // 1F5E      ; UNKNOWN
                    0x1F5F,  // 1F5F..1F7D; GREEK
                    0x1F7E,  // 1F7E..1F7F; UNKNOWN
                    0x1F80,  // 1F80..1FB4; GREEK
                    0x1FB5,  // 1FB5      ; UNKNOWN
                    0x1FB6,  // 1FB6..1FC4; GREEK
                    0x1FC5,  // 1FC5      ; UNKNOWN
                    0x1FC6,  // 1FC6..1FD3; GREEK
                    0x1FD4,  // 1FD4..1FD5; UNKNOWN
                    0x1FD6,  // 1FD6..1FDB; GREEK
                    0x1FDC,  // 1FDC      ; UNKNOWN
                    0x1FDD,  // 1FDD..1FEF; GREEK
                    0x1FF0,  // 1FF0..1FF1; UNKNOWN
                    0x1FF2,  // 1FF2..1FF4; GREEK
                    0x1FF5,  // 1FF5      ; UNKNOWN
                    0x1FF6,  // 1FF6..1FFE; GREEK
                    0x1FFF,  // 1FFF      ; UNKNOWN
                    0x2000,  // 2000..200B; COMMON
                    0x200C,  // 200C..200D; INHERITED
                    0x200E,  // 200E..2064; COMMON
                    0x2065,  // 2065      ; UNKNOWN
                    0x2066,  // 2066..2070; COMMON
                    0x2071,  // 2071      ; LATIN
                    0x2072,  // 2072..2073; UNKNOWN
                    0x2074,  // 2074..207E; COMMON
                    0x207F,  // 207F      ; LATIN
                    0x2080,  // 2080..208E; COMMON
                    0x208F,  // 208F      ; UNKNOWN
                    0x2090,  // 2090..209C; LATIN
                    0x209D,  // 209D..209F; UNKNOWN
                    0x20A0,  // 20A0..20C0; COMMON
                    0x20C1,  // 20C1..20CF; UNKNOWN
                    0x20D0,  // 20D0..20F0; INHERITED
                    0x20F1,  // 20F1..20FF; UNKNOWN
                    0x2100,  // 2100..2125; COMMON
                    0x2126,  // 2126      ; GREEK
                    0x2127,  // 2127..2129; COMMON
                    0x212A,  // 212A..212B; LATIN
                    0x212C,  // 212C..2131; COMMON
                    0x2132,  // 2132      ; LATIN
                    0x2133,  // 2133..214D; COMMON
                    0x214E,  // 214E      ; LATIN
                    0x214F,  // 214F..215F; COMMON
                    0x2160,  // 2160..2188; LATIN
                    0x2189,  // 2189..218B; COMMON
                    0x218C,  // 218C..218F; UNKNOWN
                    0x2190,  // 2190..2429; COMMON
                    0x242A,  // 242A..243F; UNKNOWN
                    0x2440,  // 2440..244A; COMMON
                    0x244B,  // 244B..245F; UNKNOWN
                    0x2460,  // 2460..27FF; COMMON
                    0x2800,  // 2800..28FF; BRAILLE
                    0x2900,  // 2900..2B73; COMMON
                    0x2B74,  // 2B74..2B75; UNKNOWN
                    0x2B76,  // 2B76..2B95; COMMON
                    0x2B96,  // 2B96      ; UNKNOWN
                    0x2B97,  // 2B97..2BFF; COMMON
                    0x2C00,  // 2C00..2C5F; GLAGOLITIC
                    0x2C60,  // 2C60..2C7F; LATIN
                    0x2C80,  // 2C80..2CF3; COPTIC
                    0x2CF4,  // 2CF4..2CF8; UNKNOWN
                    0x2CF9,  // 2CF9..2CFF; COPTIC
                    0x2D00,  // 2D00..2D25; GEORGIAN
                    0x2D26,  // 2D26      ; UNKNOWN
                    0x2D27,  // 2D27      ; GEORGIAN
                    0x2D28,  // 2D28..2D2C; UNKNOWN
                    0x2D2D,  // 2D2D      ; GEORGIAN
                    0x2D2E,  // 2D2E..2D2F; UNKNOWN
                    0x2D30,  // 2D30..2D67; TIFINAGH
                    0x2D68,  // 2D68..2D6E; UNKNOWN
                    0x2D6F,  // 2D6F..2D70; TIFINAGH
                    0x2D71,  // 2D71..2D7E; UNKNOWN
                    0x2D7F,  // 2D7F      ; TIFINAGH
                    0x2D80,  // 2D80..2D96; ETHIOPIC
                    0x2D97,  // 2D97..2D9F; UNKNOWN
                    0x2DA0,  // 2DA0..2DA6; ETHIOPIC
                    0x2DA7,  // 2DA7      ; UNKNOWN
                    0x2DA8,  // 2DA8..2DAE; ETHIOPIC
                    0x2DAF,  // 2DAF      ; UNKNOWN
                    0x2DB0,  // 2DB0..2DB6; ETHIOPIC
                    0x2DB7,  // 2DB7      ; UNKNOWN
                    0x2DB8,  // 2DB8..2DBE; ETHIOPIC
                    0x2DBF,  // 2DBF      ; UNKNOWN
                    0x2DC0,  // 2DC0..2DC6; ETHIOPIC
                    0x2DC7,  // 2DC7      ; UNKNOWN
                    0x2DC8,  // 2DC8..2DCE; ETHIOPIC
                    0x2DCF,  // 2DCF      ; UNKNOWN
                    0x2DD0,  // 2DD0..2DD6; ETHIOPIC
                    0x2DD7,  // 2DD7      ; UNKNOWN
                    0x2DD8,  // 2DD8..2DDE; ETHIOPIC
                    0x2DDF,  // 2DDF      ; UNKNOWN
                    0x2DE0,  // 2DE0..2DFF; CYRILLIC
                    0x2E00,  // 2E00..2E5D; COMMON
                    0x2E5E,  // 2E5E..2E7F; UNKNOWN
                    0x2E80,  // 2E80..2E99; HAN
                    0x2E9A,  // 2E9A      ; UNKNOWN
                    0x2E9B,  // 2E9B..2EF3; HAN
                    0x2EF4,  // 2EF4..2EFF; UNKNOWN
                    0x2F00,  // 2F00..2FD5; HAN
                    0x2FD6,  // 2FD6..2FEF; UNKNOWN
                    0x2FF0,  // 2FF0..3004; COMMON
                    0x3005,  // 3005      ; HAN
                    0x3006,  // 3006      ; COMMON
                    0x3007,  // 3007      ; HAN
                    0x3008,  // 3008..3020; COMMON
                    0x3021,  // 3021..3029; HAN
                    0x302A,  // 302A..302D; INHERITED
                    0x302E,  // 302E..302F; HANGUL
                    0x3030,  // 3030..3037; COMMON
                    0x3038,  // 3038..303B; HAN
                    0x303C,  // 303C..303F; COMMON
                    0x3040,  // 3040      ; UNKNOWN
                    0x3041,  // 3041..3096; HIRAGANA
                    0x3097,  // 3097..3098; UNKNOWN
                    0x3099,  // 3099..309A; INHERITED
                    0x309B,  // 309B..309C; COMMON
                    0x309D,  // 309D..309F; HIRAGANA
                    0x30A0,  // 30A0      ; COMMON
                    0x30A1,  // 30A1..30FA; KATAKANA
                    0x30FB,  // 30FB..30FC; COMMON
                    0x30FD,  // 30FD..30FF; KATAKANA
                    0x3100,  // 3100..3104; UNKNOWN
                    0x3105,  // 3105..312F; BOPOMOFO
                    0x3130,  // 3130      ; UNKNOWN
                    0x3131,  // 3131..318E; HANGUL
                    0x318F,  // 318F      ; UNKNOWN
                    0x3190,  // 3190..319F; COMMON
                    0x31A0,  // 31A0..31BF; BOPOMOFO
                    0x31C0,  // 31C0..31E5; COMMON
                    0x31E6,  // 31E6..31EE; UNKNOWN
                    0x31EF,  // 31EF      ; COMMON
                    0x31F0,  // 31F0..31FF; KATAKANA
                    0x3200,  // 3200..321E; HANGUL
                    0x321F,  // 321F      ; UNKNOWN
                    0x3220,  // 3220..325F; COMMON
                    0x3260,  // 3260..327E; HANGUL
                    0x327F,  // 327F..32CF; COMMON
                    0x32D0,  // 32D0..32FE; KATAKANA
                    0x32FF,  // 32FF      ; COMMON
                    0x3300,  // 3300..3357; KATAKANA
                    0x3358,  // 3358..33FF; COMMON
                    0x3400,  // 3400..4DBF; HAN
                    0x4DC0,  // 4DC0..4DFF; COMMON
                    0x4E00,  // 4E00..9FFF; HAN
                    0xA000,  // A000..A48C; YI
                    0xA48D,  // A48D..A48F; UNKNOWN
                    0xA490,  // A490..A4C6; YI
                    0xA4C7,  // A4C7..A4CF; UNKNOWN
                    0xA4D0,  // A4D0..A4FF; LISU
                    0xA500,  // A500..A62B; VAI
                    0xA62C,  // A62C..A63F; UNKNOWN
                    0xA640,  // A640..A69F; CYRILLIC
                    0xA6A0,  // A6A0..A6F7; BAMUM
                    0xA6F8,  // A6F8..A6FF; UNKNOWN
                    0xA700,  // A700..A721; COMMON
                    0xA722,  // A722..A787; LATIN
                    0xA788,  // A788..A78A; COMMON
                    0xA78B,  // A78B..A7CD; LATIN
                    0xA7CE,  // A7CE..A7CF; UNKNOWN
                    0xA7D0,  // A7D0..A7D1; LATIN
                    0xA7D2,  // A7D2      ; UNKNOWN
                    0xA7D3,  // A7D3      ; LATIN
                    0xA7D4,  // A7D4      ; UNKNOWN
                    0xA7D5,  // A7D5..A7DC; LATIN
                    0xA7DD,  // A7DD..A7F1; UNKNOWN
                    0xA7F2,  // A7F2..A7FF; LATIN
                    0xA800,  // A800..A82C; SYLOTI_NAGRI
                    0xA82D,  // A82D..A82F; UNKNOWN
                    0xA830,  // A830..A839; COMMON
                    0xA83A,  // A83A..A83F; UNKNOWN
                    0xA840,  // A840..A877; PHAGS_PA
                    0xA878,  // A878..A87F; UNKNOWN
                    0xA880,  // A880..A8C5; SAURASHTRA
                    0xA8C6,  // A8C6..A8CD; UNKNOWN
                    0xA8CE,  // A8CE..A8D9; SAURASHTRA
                    0xA8DA,  // A8DA..A8DF; UNKNOWN
                    0xA8E0,  // A8E0..A8FF; DEVANAGARI
                    0xA900,  // A900..A92D; KAYAH_LI
                    0xA92E,  // A92E      ; COMMON
                    0xA92F,  // A92F      ; KAYAH_LI
                    0xA930,  // A930..A953; REJANG
                    0xA954,  // A954..A95E; UNKNOWN
                    0xA95F,  // A95F      ; REJANG
                    0xA960,  // A960..A97C; HANGUL
                    0xA97D,  // A97D..A97F; UNKNOWN
                    0xA980,  // A980..A9CD; JAVANESE
                    0xA9CE,  // A9CE      ; UNKNOWN
                    0xA9CF,  // A9CF      ; COMMON
                    0xA9D0,  // A9D0..A9D9; JAVANESE
                    0xA9DA,  // A9DA..A9DD; UNKNOWN
                    0xA9DE,  // A9DE..A9DF; JAVANESE
                    0xA9E0,  // A9E0..A9FE; MYANMAR
                    0xA9FF,  // A9FF      ; UNKNOWN
                    0xAA00,  // AA00..AA36; CHAM
                    0xAA37,  // AA37..AA3F; UNKNOWN
                    0xAA40,  // AA40..AA4D; CHAM
                    0xAA4E,  // AA4E..AA4F; UNKNOWN
                    0xAA50,  // AA50..AA59; CHAM
                    0xAA5A,  // AA5A..AA5B; UNKNOWN
                    0xAA5C,  // AA5C..AA5F; CHAM
                    0xAA60,  // AA60..AA7F; MYANMAR
                    0xAA80,  // AA80..AAC2; TAI_VIET
                    0xAAC3,  // AAC3..AADA; UNKNOWN
                    0xAADB,  // AADB..AADF; TAI_VIET
                    0xAAE0,  // AAE0..AAF6; MEETEI_MAYEK
                    0xAAF7,  // AAF7..AB00; UNKNOWN
                    0xAB01,  // AB01..AB06; ETHIOPIC
                    0xAB07,  // AB07..AB08; UNKNOWN
                    0xAB09,  // AB09..AB0E; ETHIOPIC
                    0xAB0F,  // AB0F..AB10; UNKNOWN
                    0xAB11,  // AB11..AB16; ETHIOPIC
                    0xAB17,  // AB17..AB1F; UNKNOWN
                    0xAB20,  // AB20..AB26; ETHIOPIC
                    0xAB27,  // AB27      ; UNKNOWN
                    0xAB28,  // AB28..AB2E; ETHIOPIC
                    0xAB2F,  // AB2F      ; UNKNOWN
                    0xAB30,  // AB30..AB5A; LATIN
                    0xAB5B,  // AB5B      ; COMMON
                    0xAB5C,  // AB5C..AB64; LATIN
                    0xAB65,  // AB65      ; GREEK
                    0xAB66,  // AB66..AB69; LATIN
                    0xAB6A,  // AB6A..AB6B; COMMON
                    0xAB6C,  // AB6C..AB6F; UNKNOWN
                    0xAB70,  // AB70..ABBF; CHEROKEE
                    0xABC0,  // ABC0..ABED; MEETEI_MAYEK
                    0xABEE,  // ABEE..ABEF; UNKNOWN
                    0xABF0,  // ABF0..ABF9; MEETEI_MAYEK
                    0xABFA,  // ABFA..ABFF; UNKNOWN
                    0xAC00,  // AC00..D7A3; HANGUL
                    0xD7A4,  // D7A4..D7AF; UNKNOWN
                    0xD7B0,  // D7B0..D7C6; HANGUL
                    0xD7C7,  // D7C7..D7CA; UNKNOWN
                    0xD7CB,  // D7CB..D7FB; HANGUL
                    0xD7FC,  // D7FC..F8FF; UNKNOWN
                    0xF900,  // F900..FA6D; HAN
                    0xFA6E,  // FA6E..FA6F; UNKNOWN
                    0xFA70,  // FA70..FAD9; HAN
                    0xFADA,  // FADA..FAFF; UNKNOWN
                    0xFB00,  // FB00..FB06; LATIN
                    0xFB07,  // FB07..FB12; UNKNOWN
                    0xFB13,  // FB13..FB17; ARMENIAN
                    0xFB18,  // FB18..FB1C; UNKNOWN
                    0xFB1D,  // FB1D..FB36; HEBREW
                    0xFB37,  // FB37      ; UNKNOWN
                    0xFB38,  // FB38..FB3C; HEBREW
                    0xFB3D,  // FB3D      ; UNKNOWN
                    0xFB3E,  // FB3E      ; HEBREW
                    0xFB3F,  // FB3F      ; UNKNOWN
                    0xFB40,  // FB40..FB41; HEBREW
                    0xFB42,  // FB42      ; UNKNOWN
                    0xFB43,  // FB43..FB44; HEBREW
                    0xFB45,  // FB45      ; UNKNOWN
                    0xFB46,  // FB46..FB4F; HEBREW
                    0xFB50,  // FB50..FBC2; ARABIC
                    0xFBC3,  // FBC3..FBD2; UNKNOWN
                    0xFBD3,  // FBD3..FD3D; ARABIC
                    0xFD3E,  // FD3E..FD3F; COMMON
                    0xFD40,  // FD40..FD8F; ARABIC
                    0xFD90,  // FD90..FD91; UNKNOWN
                    0xFD92,  // FD92..FDC7; ARABIC
                    0xFDC8,  // FDC8..FDCE; UNKNOWN
                    0xFDCF,  // FDCF      ; ARABIC
                    0xFDD0,  // FDD0..FDEF; UNKNOWN
                    0xFDF0,  // FDF0..FDFF; ARABIC
                    0xFE00,  // FE00..FE0F; INHERITED
                    0xFE10,  // FE10..FE19; COMMON
                    0xFE1A,  // FE1A..FE1F; UNKNOWN
                    0xFE20,  // FE20..FE2D; INHERITED
                    0xFE2E,  // FE2E..FE2F; CYRILLIC
                    0xFE30,  // FE30..FE52; COMMON
                    0xFE53,  // FE53      ; UNKNOWN
                    0xFE54,  // FE54..FE66; COMMON
                    0xFE67,  // FE67      ; UNKNOWN
                    0xFE68,  // FE68..FE6B; COMMON
                    0xFE6C,  // FE6C..FE6F; UNKNOWN
                    0xFE70,  // FE70..FE74; ARABIC
                    0xFE75,  // FE75      ; UNKNOWN
                    0xFE76,  // FE76..FEFC; ARABIC
                    0xFEFD,  // FEFD..FEFE; UNKNOWN
                    0xFEFF,  // FEFF      ; COMMON
                    0xFF00,  // FF00      ; UNKNOWN
                    0xFF01,  // FF01..FF20; COMMON
                    0xFF21,  // FF21..FF3A; LATIN
                    0xFF3B,  // FF3B..FF40; COMMON
                    0xFF41,  // FF41..FF5A; LATIN
                    0xFF5B,  // FF5B..FF65; COMMON
                    0xFF66,  // FF66..FF6F; KATAKANA
                    0xFF70,  // FF70      ; COMMON
                    0xFF71,  // FF71..FF9D; KATAKANA
                    0xFF9E,  // FF9E..FF9F; COMMON
                    0xFFA0,  // FFA0..FFBE; HANGUL
                    0xFFBF,  // FFBF..FFC1; UNKNOWN
                    0xFFC2,  // FFC2..FFC7; HANGUL
                    0xFFC8,  // FFC8..FFC9; UNKNOWN
                    0xFFCA,  // FFCA..FFCF; HANGUL
                    0xFFD0,  // FFD0..FFD1; UNKNOWN
                    0xFFD2,  // FFD2..FFD7; HANGUL
                    0xFFD8,  // FFD8..FFD9; UNKNOWN
                    0xFFDA,  // FFDA..FFDC; HANGUL
                    0xFFDD,  // FFDD..FFDF; UNKNOWN
                    0xFFE0,  // FFE0..FFE6; COMMON
                    0xFFE7,  // FFE7      ; UNKNOWN
                    0xFFE8,  // FFE8..FFEE; COMMON
                    0xFFEF,  // FFEF..FFF8; UNKNOWN
                    0xFFF9,  // FFF9..FFFD; COMMON
                    0xFFFE,  // FFFE..FFFF; UNKNOWN
                    0x10000,  // 10000..1000B; LINEAR_B
                    0x1000C,  // 1000C       ; UNKNOWN
                    0x1000D,  // 1000D..10026; LINEAR_B
                    0x10027,  // 10027       ; UNKNOWN
                    0x10028,  // 10028..1003A; LINEAR_B
                    0x1003B,  // 1003B       ; UNKNOWN
                    0x1003C,  // 1003C..1003D; LINEAR_B
                    0x1003E,  // 1003E       ; UNKNOWN
                    0x1003F,  // 1003F..1004D; LINEAR_B
                    0x1004E,  // 1004E..1004F; UNKNOWN
                    0x10050,  // 10050..1005D; LINEAR_B
                    0x1005E,  // 1005E..1007F; UNKNOWN
                    0x10080,  // 10080..100FA; LINEAR_B
                    0x100FB,  // 100FB..100FF; UNKNOWN
                    0x10100,  // 10100..10102; COMMON
                    0x10103,  // 10103..10106; UNKNOWN
                    0x10107,  // 10107..10133; COMMON
                    0x10134,  // 10134..10136; UNKNOWN
                    0x10137,  // 10137..1013F; COMMON
                    0x10140,  // 10140..1018E; GREEK
                    0x1018F,  // 1018F       ; UNKNOWN
                    0x10190,  // 10190..1019C; COMMON
                    0x1019D,  // 1019D..1019F; UNKNOWN
                    0x101A0,  // 101A0       ; GREEK
                    0x101A1,  // 101A1..101CF; UNKNOWN
                    0x101D0,  // 101D0..101FC; COMMON
                    0x101FD,  // 101FD       ; INHERITED
                    0x101FE,  // 101FE..1027F; UNKNOWN
                    0x10280,  // 10280..1029C; LYCIAN
                    0x1029D,  // 1029D..1029F; UNKNOWN
                    0x102A0,  // 102A0..102D0; CARIAN
                    0x102D1,  // 102D1..102DF; UNKNOWN
                    0x102E0,  // 102E0       ; INHERITED
                    0x102E1,  // 102E1..102FB; COMMON
                    0x102FC,  // 102FC..102FF; UNKNOWN
                    0x10300,  // 10300..10323; OLD_ITALIC
                    0x10324,  // 10324..1032C; UNKNOWN
                    0x1032D,  // 1032D..1032F; OLD_ITALIC
                    0x10330,  // 10330..1034A; GOTHIC
                    0x1034B,  // 1034B..1034F; UNKNOWN
                    0x10350,  // 10350..1037A; OLD_PERMIC
                    0x1037B,  // 1037B..1037F; UNKNOWN
                    0x10380,  // 10380..1039D; UGARITIC
                    0x1039E,  // 1039E       ; UNKNOWN
                    0x1039F,  // 1039F       ; UGARITIC
                    0x103A0,  // 103A0..103C3; OLD_PERSIAN
                    0x103C4,  // 103C4..103C7; UNKNOWN
                    0x103C8,  // 103C8..103D5; OLD_PERSIAN
                    0x103D6,  // 103D6..103FF; UNKNOWN
                    0x10400,  // 10400..1044F; DESERET
                    0x10450,  // 10450..1047F; SHAVIAN
                    0x10480,  // 10480..1049D; OSMANYA
                    0x1049E,  // 1049E..1049F; UNKNOWN
                    0x104A0,  // 104A0..104A9; OSMANYA
                    0x104AA,  // 104AA..104AF; UNKNOWN
                    0x104B0,  // 104B0..104D3; OSAGE
                    0x104D4,  // 104D4..104D7; UNKNOWN
                    0x104D8,  // 104D8..104FB; OSAGE
                    0x104FC,  // 104FC..104FF; UNKNOWN
                    0x10500,  // 10500..10527; ELBASAN
                    0x10528,  // 10528..1052F; UNKNOWN
                    0x10530,  // 10530..10563; CAUCASIAN_ALBANIAN
                    0x10564,  // 10564..1056E; UNKNOWN
                    0x1056F,  // 1056F       ; CAUCASIAN_ALBANIAN
                    0x10570,  // 10570..1057A; VITHKUQI
                    0x1057B,  // 1057B       ; UNKNOWN
                    0x1057C,  // 1057C..1058A; VITHKUQI
                    0x1058B,  // 1058B       ; UNKNOWN
                    0x1058C,  // 1058C..10592; VITHKUQI
                    0x10593,  // 10593       ; UNKNOWN
                    0x10594,  // 10594..10595; VITHKUQI
                    0x10596,  // 10596       ; UNKNOWN
                    0x10597,  // 10597..105A1; VITHKUQI
                    0x105A2,  // 105A2       ; UNKNOWN
                    0x105A3,  // 105A3..105B1; VITHKUQI
                    0x105B2,  // 105B2       ; UNKNOWN
                    0x105B3,  // 105B3..105B9; VITHKUQI
                    0x105BA,  // 105BA       ; UNKNOWN
                    0x105BB,  // 105BB..105BC; VITHKUQI
                    0x105BD,  // 105BD..105BF; UNKNOWN
                    0x105C0,  // 105C0..105F3; TODHRI
                    0x105F4,  // 105F4..105FF; UNKNOWN
                    0x10600,  // 10600..10736; LINEAR_A
                    0x10737,  // 10737..1073F; UNKNOWN
                    0x10740,  // 10740..10755; LINEAR_A
                    0x10756,  // 10756..1075F; UNKNOWN
                    0x10760,  // 10760..10767; LINEAR_A
                    0x10768,  // 10768..1077F; UNKNOWN
                    0x10780,  // 10780..10785; LATIN
                    0x10786,  // 10786       ; UNKNOWN
                    0x10787,  // 10787..107B0; LATIN
                    0x107B1,  // 107B1       ; UNKNOWN
                    0x107B2,  // 107B2..107BA; LATIN
                    0x107BB,  // 107BB..107FF; UNKNOWN
                    0x10800,  // 10800..10805; CYPRIOT
                    0x10806,  // 10806..10807; UNKNOWN
                    0x10808,  // 10808       ; CYPRIOT
                    0x10809,  // 10809       ; UNKNOWN
                    0x1080A,  // 1080A..10835; CYPRIOT
                    0x10836,  // 10836       ; UNKNOWN
                    0x10837,  // 10837..10838; CYPRIOT
                    0x10839,  // 10839..1083B; UNKNOWN
                    0x1083C,  // 1083C       ; CYPRIOT
                    0x1083D,  // 1083D..1083E; UNKNOWN
                    0x1083F,  // 1083F       ; CYPRIOT
                    0x10840,  // 10840..10855; IMPERIAL_ARAMAIC
                    0x10856,  // 10856       ; UNKNOWN
                    0x10857,  // 10857..1085F; IMPERIAL_ARAMAIC
                    0x10860,  // 10860..1087F; PALMYRENE
                    0x10880,  // 10880..1089E; NABATAEAN
                    0x1089F,  // 1089F..108A6; UNKNOWN
                    0x108A7,  // 108A7..108AF; NABATAEAN
                    0x108B0,  // 108B0..108DF; UNKNOWN
                    0x108E0,  // 108E0..108F2; HATRAN
                    0x108F3,  // 108F3       ; UNKNOWN
                    0x108F4,  // 108F4..108F5; HATRAN
                    0x108F6,  // 108F6..108FA; UNKNOWN
                    0x108FB,  // 108FB..108FF; HATRAN
                    0x10900,  // 10900..1091B; PHOENICIAN
                    0x1091C,  // 1091C..1091E; UNKNOWN
                    0x1091F,  // 1091F       ; PHOENICIAN
                    0x10920,  // 10920..10939; LYDIAN
                    0x1093A,  // 1093A..1093E; UNKNOWN
                    0x1093F,  // 1093F       ; LYDIAN
                    0x10940,  // 10940..1097F; UNKNOWN
                    0x10980,  // 10980..1099F; MEROITIC_HIEROGLYPHS
                    0x109A0,  // 109A0..109B7; MEROITIC_CURSIVE
                    0x109B8,  // 109B8..109BB; UNKNOWN
                    0x109BC,  // 109BC..109CF; MEROITIC_CURSIVE
                    0x109D0,  // 109D0..109D1; UNKNOWN
                    0x109D2,  // 109D2..109FF; MEROITIC_CURSIVE
                    0x10A00,  // 10A00..10A03; KHAROSHTHI
                    0x10A04,  // 10A04       ; UNKNOWN
                    0x10A05,  // 10A05..10A06; KHAROSHTHI
                    0x10A07,  // 10A07..10A0B; UNKNOWN
                    0x10A0C,  // 10A0C..10A13; KHAROSHTHI
                    0x10A14,  // 10A14       ; UNKNOWN
                    0x10A15,  // 10A15..10A17; KHAROSHTHI
                    0x10A18,  // 10A18       ; UNKNOWN
                    0x10A19,  // 10A19..10A35; KHAROSHTHI
                    0x10A36,  // 10A36..10A37; UNKNOWN
                    0x10A38,  // 10A38..10A3A; KHAROSHTHI
                    0x10A3B,  // 10A3B..10A3E; UNKNOWN
                    0x10A3F,  // 10A3F..10A48; KHAROSHTHI
                    0x10A49,  // 10A49..10A4F; UNKNOWN
                    0x10A50,  // 10A50..10A58; KHAROSHTHI
                    0x10A59,  // 10A59..10A5F; UNKNOWN
                    0x10A60,  // 10A60..10A7F; OLD_SOUTH_ARABIAN
                    0x10A80,  // 10A80..10A9F; OLD_NORTH_ARABIAN
                    0x10AA0,  // 10AA0..10ABF; UNKNOWN
                    0x10AC0,  // 10AC0..10AE6; MANICHAEAN
                    0x10AE7,  // 10AE7..10AEA; UNKNOWN
                    0x10AEB,  // 10AEB..10AF6; MANICHAEAN
                    0x10AF7,  // 10AF7..10AFF; UNKNOWN
                    0x10B00,  // 10B00..10B35; AVESTAN
                    0x10B36,  // 10B36..10B38; UNKNOWN
                    0x10B39,  // 10B39..10B3F; AVESTAN
                    0x10B40,  // 10B40..10B55; INSCRIPTIONAL_PARTHIAN
                    0x10B56,  // 10B56..10B57; UNKNOWN
                    0x10B58,  // 10B58..10B5F; INSCRIPTIONAL_PARTHIAN
                    0x10B60,  // 10B60..10B72; INSCRIPTIONAL_PAHLAVI
                    0x10B73,  // 10B73..10B77; UNKNOWN
                    0x10B78,  // 10B78..10B7F; INSCRIPTIONAL_PAHLAVI
                    0x10B80,  // 10B80..10B91; PSALTER_PAHLAVI
                    0x10B92,  // 10B92..10B98; UNKNOWN
                    0x10B99,  // 10B99..10B9C; PSALTER_PAHLAVI
                    0x10B9D,  // 10B9D..10BA8; UNKNOWN
                    0x10BA9,  // 10BA9..10BAF; PSALTER_PAHLAVI
                    0x10BB0,  // 10BB0..10BFF; UNKNOWN
                    0x10C00,  // 10C00..10C48; OLD_TURKIC
                    0x10C49,  // 10C49..10C7F; UNKNOWN
                    0x10C80,  // 10C80..10CB2; OLD_HUNGARIAN
                    0x10CB3,  // 10CB3..10CBF; UNKNOWN
                    0x10CC0,  // 10CC0..10CF2; OLD_HUNGARIAN
                    0x10CF3,  // 10CF3..10CF9; UNKNOWN
                    0x10CFA,  // 10CFA..10CFF; OLD_HUNGARIAN
                    0x10D00,  // 10D00..10D27; HANIFI_ROHINGYA
                    0x10D28,  // 10D28..10D2F; UNKNOWN
                    0x10D30,  // 10D30..10D39; HANIFI_ROHINGYA
                    0x10D3A,  // 10D3A..10D3F; UNKNOWN
                    0x10D40,  // 10D40..10D65; GARAY
                    0x10D66,  // 10D66..10D68; UNKNOWN
                    0x10D69,  // 10D69..10D85; GARAY
                    0x10D86,  // 10D86..10D8D; UNKNOWN
                    0x10D8E,  // 10D8E..10D8F; GARAY
                    0x10D90,  // 10D90..10E5F; UNKNOWN
                    0x10E60,  // 10E60..10E7E; ARABIC
                    0x10E7F,  // 10E7F       ; UNKNOWN
                    0x10E80,  // 10E80..10EA9; YEZIDI
                    0x10EAA,  // 10EAA       ; UNKNOWN
                    0x10EAB,  // 10EAB..10EAD; YEZIDI
                    0x10EAE,  // 10EAE..10EAF; UNKNOWN
                    0x10EB0,  // 10EB0..10EB1; YEZIDI
                    0x10EB2,  // 10EB2..10EC1; UNKNOWN
                    0x10EC2,  // 10EC2..10EC4; ARABIC
                    0x10EC5,  // 10EC5..10EFB; UNKNOWN
                    0x10EFC,  // 10EFC..10EFF; ARABIC
                    0x10F00,  // 10F00..10F27; OLD_SOGDIAN
                    0x10F28,  // 10F28..10F2F; UNKNOWN
                    0x10F30,  // 10F30..10F59; SOGDIAN
                    0x10F5A,  // 10F5A..10F6F; UNKNOWN
                    0x10F70,  // 10F70..10F89; OLD_UYGHUR
                    0x10F8A,  // 10F8A..10FAF; UNKNOWN
                    0x10FB0,  // 10FB0..10FCB; CHORASMIAN
                    0x10FCC,  // 10FCC..10FDF; UNKNOWN
                    0x10FE0,  // 10FE0..10FF6; ELYMAIC
                    0x10FF7,  // 10FF7..10FFF; UNKNOWN
                    0x11000,  // 11000..1104D; BRAHMI
                    0x1104E,  // 1104E..11051; UNKNOWN
                    0x11052,  // 11052..11075; BRAHMI
                    0x11076,  // 11076..1107E; UNKNOWN
                    0x1107F,  // 1107F       ; BRAHMI
                    0x11080,  // 11080..110C2; KAITHI
                    0x110C3,  // 110C3..110CC; UNKNOWN
                    0x110CD,  // 110CD       ; KAITHI
                    0x110CE,  // 110CE..110CF; UNKNOWN
                    0x110D0,  // 110D0..110E8; SORA_SOMPENG
                    0x110E9,  // 110E9..110EF; UNKNOWN
                    0x110F0,  // 110F0..110F9; SORA_SOMPENG
                    0x110FA,  // 110FA..110FF; UNKNOWN
                    0x11100,  // 11100..11134; CHAKMA
                    0x11135,  // 11135       ; UNKNOWN
                    0x11136,  // 11136..11147; CHAKMA
                    0x11148,  // 11148..1114F; UNKNOWN
                    0x11150,  // 11150..11176; MAHAJANI
                    0x11177,  // 11177..1117F; UNKNOWN
                    0x11180,  // 11180..111DF; SHARADA
                    0x111E0,  // 111E0       ; UNKNOWN
                    0x111E1,  // 111E1..111F4; SINHALA
                    0x111F5,  // 111F5..111FF; UNKNOWN
                    0x11200,  // 11200..11211; KHOJKI
                    0x11212,  // 11212       ; UNKNOWN
                    0x11213,  // 11213..11241; KHOJKI
                    0x11242,  // 11242..1127F; UNKNOWN
                    0x11280,  // 11280..11286; MULTANI
                    0x11287,  // 11287       ; UNKNOWN
                    0x11288,  // 11288       ; MULTANI
                    0x11289,  // 11289       ; UNKNOWN
                    0x1128A,  // 1128A..1128D; MULTANI
                    0x1128E,  // 1128E       ; UNKNOWN
                    0x1128F,  // 1128F..1129D; MULTANI
                    0x1129E,  // 1129E       ; UNKNOWN
                    0x1129F,  // 1129F..112A9; MULTANI
                    0x112AA,  // 112AA..112AF; UNKNOWN
                    0x112B0,  // 112B0..112EA; KHUDAWADI
                    0x112EB,  // 112EB..112EF; UNKNOWN
                    0x112F0,  // 112F0..112F9; KHUDAWADI
                    0x112FA,  // 112FA..112FF; UNKNOWN
                    0x11300,  // 11300..11303; GRANTHA
                    0x11304,  // 11304       ; UNKNOWN
                    0x11305,  // 11305..1130C; GRANTHA
                    0x1130D,  // 1130D..1130E; UNKNOWN
                    0x1130F,  // 1130F..11310; GRANTHA
                    0x11311,  // 11311..11312; UNKNOWN
                    0x11313,  // 11313..11328; GRANTHA
                    0x11329,  // 11329       ; UNKNOWN
                    0x1132A,  // 1132A..11330; GRANTHA
                    0x11331,  // 11331       ; UNKNOWN
                    0x11332,  // 11332..11333; GRANTHA
                    0x11334,  // 11334       ; UNKNOWN
                    0x11335,  // 11335..11339; GRANTHA
                    0x1133A,  // 1133A       ; UNKNOWN
                    0x1133B,  // 1133B       ; INHERITED
                    0x1133C,  // 1133C..11344; GRANTHA
                    0x11345,  // 11345..11346; UNKNOWN
                    0x11347,  // 11347..11348; GRANTHA
                    0x11349,  // 11349..1134A; UNKNOWN
                    0x1134B,  // 1134B..1134D; GRANTHA
                    0x1134E,  // 1134E..1134F; UNKNOWN
                    0x11350,  // 11350       ; GRANTHA
                    0x11351,  // 11351..11356; UNKNOWN
                    0x11357,  // 11357       ; GRANTHA
                    0x11358,  // 11358..1135C; UNKNOWN
                    0x1135D,  // 1135D..11363; GRANTHA
                    0x11364,  // 11364..11365; UNKNOWN
                    0x11366,  // 11366..1136C; GRANTHA
                    0x1136D,  // 1136D..1136F; UNKNOWN
                    0x11370,  // 11370..11374; GRANTHA
                    0x11375,  // 11375..1137F; UNKNOWN
                    0x11380,  // 11380..11389; TULU_TIGALARI
                    0x1138A,  // 1138A       ; UNKNOWN
                    0x1138B,  // 1138B       ; TULU_TIGALARI
                    0x1138C,  // 1138C..1138D; UNKNOWN
                    0x1138E,  // 1138E       ; TULU_TIGALARI
                    0x1138F,  // 1138F       ; UNKNOWN
                    0x11390,  // 11390..113B5; TULU_TIGALARI
                    0x113B6,  // 113B6       ; UNKNOWN
                    0x113B7,  // 113B7..113C0; TULU_TIGALARI
                    0x113C1,  // 113C1       ; UNKNOWN
                    0x113C2,  // 113C2       ; TULU_TIGALARI
                    0x113C3,  // 113C3..113C4; UNKNOWN
                    0x113C5,  // 113C5       ; TULU_TIGALARI
                    0x113C6,  // 113C6       ; UNKNOWN
                    0x113C7,  // 113C7..113CA; TULU_TIGALARI
                    0x113CB,  // 113CB       ; UNKNOWN
                    0x113CC,  // 113CC..113D5; TULU_TIGALARI
                    0x113D6,  // 113D6       ; UNKNOWN
                    0x113D7,  // 113D7..113D8; TULU_TIGALARI
                    0x113D9,  // 113D9..113E0; UNKNOWN
                    0x113E1,  // 113E1..113E2; TULU_TIGALARI
                    0x113E3,  // 113E3..113FF; UNKNOWN
                    0x11400,  // 11400..1145B; NEWA
                    0x1145C,  // 1145C       ; UNKNOWN
                    0x1145D,  // 1145D..11461; NEWA
                    0x11462,  // 11462..1147F; UNKNOWN
                    0x11480,  // 11480..114C7; TIRHUTA
                    0x114C8,  // 114C8..114CF; UNKNOWN
                    0x114D0,  // 114D0..114D9; TIRHUTA
                    0x114DA,  // 114DA..1157F; UNKNOWN
                    0x11580,  // 11580..115B5; SIDDHAM
                    0x115B6,  // 115B6..115B7; UNKNOWN
                    0x115B8,  // 115B8..115DD; SIDDHAM
                    0x115DE,  // 115DE..115FF; UNKNOWN
                    0x11600,  // 11600..11644; MODI
                    0x11645,  // 11645..1164F; UNKNOWN
                    0x11650,  // 11650..11659; MODI
                    0x1165A,  // 1165A..1165F; UNKNOWN
                    0x11660,  // 11660..1166C; MONGOLIAN
                    0x1166D,  // 1166D..1167F; UNKNOWN
                    0x11680,  // 11680..116B9; TAKRI
                    0x116BA,  // 116BA..116BF; UNKNOWN
                    0x116C0,  // 116C0..116C9; TAKRI
                    0x116CA,  // 116CA..116CF; UNKNOWN
                    0x116D0,  // 116D0..116E3; MYANMAR
                    0x116E4,  // 116E4..116FF; UNKNOWN
                    0x11700,  // 11700..1171A; AHOM
                    0x1171B,  // 1171B..1171C; UNKNOWN
                    0x1171D,  // 1171D..1172B; AHOM
                    0x1172C,  // 1172C..1172F; UNKNOWN
                    0x11730,  // 11730..11746; AHOM
                    0x11747,  // 11747..117FF; UNKNOWN
                    0x11800,  // 11800..1183B; DOGRA
                    0x1183C,  // 1183C..1189F; UNKNOWN
                    0x118A0,  // 118A0..118F2; WARANG_CITI
                    0x118F3,  // 118F3..118FE; UNKNOWN
                    0x118FF,  // 118FF       ; WARANG_CITI
                    0x11900,  // 11900..11906; DIVES_AKURU
                    0x11907,  // 11907..11908; UNKNOWN
                    0x11909,  // 11909       ; DIVES_AKURU
                    0x1190A,  // 1190A..1190B; UNKNOWN
                    0x1190C,  // 1190C..11913; DIVES_AKURU
                    0x11914,  // 11914       ; UNKNOWN
                    0x11915,  // 11915..11916; DIVES_AKURU
                    0x11917,  // 11917       ; UNKNOWN
                    0x11918,  // 11918..11935; DIVES_AKURU
                    0x11936,  // 11936       ; UNKNOWN
                    0x11937,  // 11937..11938; DIVES_AKURU
                    0x11939,  // 11939..1193A; UNKNOWN
                    0x1193B,  // 1193B..11946; DIVES_AKURU
                    0x11947,  // 11947..1194F; UNKNOWN
                    0x11950,  // 11950..11959; DIVES_AKURU
                    0x1195A,  // 1195A..1199F; UNKNOWN
                    0x119A0,  // 119A0..119A7; NANDINAGARI
                    0x119A8,  // 119A8..119A9; UNKNOWN
                    0x119AA,  // 119AA..119D7; NANDINAGARI
                    0x119D8,  // 119D8..119D9; UNKNOWN
                    0x119DA,  // 119DA..119E4; NANDINAGARI
                    0x119E5,  // 119E5..119FF; UNKNOWN
                    0x11A00,  // 11A00..11A47; ZANABAZAR_SQUARE
                    0x11A48,  // 11A48..11A4F; UNKNOWN
                    0x11A50,  // 11A50..11AA2; SOYOMBO
                    0x11AA3,  // 11AA3..11AAF; UNKNOWN
                    0x11AB0,  // 11AB0..11ABF; CANADIAN_ABORIGINAL
                    0x11AC0,  // 11AC0..11AF8; PAU_CIN_HAU
                    0x11AF9,  // 11AF9..11AFF; UNKNOWN
                    0x11B00,  // 11B00..11B09; DEVANAGARI
                    0x11B0A,  // 11B0A..11BBF; UNKNOWN
                    0x11BC0,  // 11BC0..11BE1; SUNUWAR
                    0x11BE2,  // 11BE2..11BEF; UNKNOWN
                    0x11BF0,  // 11BF0..11BF9; SUNUWAR
                    0x11BFA,  // 11BFA..11BFF; UNKNOWN
                    0x11C00,  // 11C00..11C08; BHAIKSUKI
                    0x11C09,  // 11C09       ; UNKNOWN
                    0x11C0A,  // 11C0A..11C36; BHAIKSUKI
                    0x11C37,  // 11C37       ; UNKNOWN
                    0x11C38,  // 11C38..11C45; BHAIKSUKI
                    0x11C46,  // 11C46..11C4F; UNKNOWN
                    0x11C50,  // 11C50..11C6C; BHAIKSUKI
                    0x11C6D,  // 11C6D..11C6F; UNKNOWN
                    0x11C70,  // 11C70..11C8F; MARCHEN
                    0x11C90,  // 11C90..11C91; UNKNOWN
                    0x11C92,  // 11C92..11CA7; MARCHEN
                    0x11CA8,  // 11CA8       ; UNKNOWN
                    0x11CA9,  // 11CA9..11CB6; MARCHEN
                    0x11CB7,  // 11CB7..11CFF; UNKNOWN
                    0x11D00,  // 11D00..11D06; MASARAM_GONDI
                    0x11D07,  // 11D07       ; UNKNOWN
                    0x11D08,  // 11D08..11D09; MASARAM_GONDI
                    0x11D0A,  // 11D0A       ; UNKNOWN
                    0x11D0B,  // 11D0B..11D36; MASARAM_GONDI
                    0x11D37,  // 11D37..11D39; UNKNOWN
                    0x11D3A,  // 11D3A       ; MASARAM_GONDI
                    0x11D3B,  // 11D3B       ; UNKNOWN
                    0x11D3C,  // 11D3C..11D3D; MASARAM_GONDI
                    0x11D3E,  // 11D3E       ; UNKNOWN
                    0x11D3F,  // 11D3F..11D47; MASARAM_GONDI
                    0x11D48,  // 11D48..11D4F; UNKNOWN
                    0x11D50,  // 11D50..11D59; MASARAM_GONDI
                    0x11D5A,  // 11D5A..11D5F; UNKNOWN
                    0x11D60,  // 11D60..11D65; GUNJALA_GONDI
                    0x11D66,  // 11D66       ; UNKNOWN
                    0x11D67,  // 11D67..11D68; GUNJALA_GONDI
                    0x11D69,  // 11D69       ; UNKNOWN
                    0x11D6A,  // 11D6A..11D8E; GUNJALA_GONDI
                    0x11D8F,  // 11D8F       ; UNKNOWN
                    0x11D90,  // 11D90..11D91; GUNJALA_GONDI
                    0x11D92,  // 11D92       ; UNKNOWN
                    0x11D93,  // 11D93..11D98; GUNJALA_GONDI
                    0x11D99,  // 11D99..11D9F; UNKNOWN
                    0x11DA0,  // 11DA0..11DA9; GUNJALA_GONDI
                    0x11DAA,  // 11DAA..11EDF; UNKNOWN
                    0x11EE0,  // 11EE0..11EF8; MAKASAR
                    0x11EF9,  // 11EF9..11EFF; UNKNOWN
                    0x11F00,  // 11F00..11F10; KAWI
                    0x11F11,  // 11F11       ; UNKNOWN
                    0x11F12,  // 11F12..11F3A; KAWI
                    0x11F3B,  // 11F3B..11F3D; UNKNOWN
                    0x11F3E,  // 11F3E..11F5A; KAWI
                    0x11F5B,  // 11F5B..11FAF; UNKNOWN
                    0x11FB0,  // 11FB0       ; LISU
                    0x11FB1,  // 11FB1..11FBF; UNKNOWN
                    0x11FC0,  // 11FC0..11FF1; TAMIL
                    0x11FF2,  // 11FF2..11FFE; UNKNOWN
                    0x11FFF,  // 11FFF       ; TAMIL
                    0x12000,  // 12000..12399; CUNEIFORM
                    0x1239A,  // 1239A..123FF; UNKNOWN
                    0x12400,  // 12400..1246E; CUNEIFORM
                    0x1246F,  // 1246F       ; UNKNOWN
                    0x12470,  // 12470..12474; CUNEIFORM
                    0x12475,  // 12475..1247F; UNKNOWN
                    0x12480,  // 12480..12543; CUNEIFORM
                    0x12544,  // 12544..12F8F; UNKNOWN
                    0x12F90,  // 12F90..12FF2; CYPRO_MINOAN
                    0x12FF3,  // 12FF3..12FFF; UNKNOWN
                    0x13000,  // 13000..13455; EGYPTIAN_HIEROGLYPHS
                    0x13456,  // 13456..1345F; UNKNOWN
                    0x13460,  // 13460..143FA; EGYPTIAN_HIEROGLYPHS
                    0x143FB,  // 143FB..143FF; UNKNOWN
                    0x14400,  // 14400..14646; ANATOLIAN_HIEROGLYPHS
                    0x14647,  // 14647..160FF; UNKNOWN
                    0x16100,  // 16100..16139; GURUNG_KHEMA
                    0x1613A,  // 1613A..167FF; UNKNOWN
                    0x16800,  // 16800..16A38; BAMUM
                    0x16A39,  // 16A39..16A3F; UNKNOWN
                    0x16A40,  // 16A40..16A5E; MRO
                    0x16A5F,  // 16A5F       ; UNKNOWN
                    0x16A60,  // 16A60..16A69; MRO
                    0x16A6A,  // 16A6A..16A6D; UNKNOWN
                    0x16A6E,  // 16A6E..16A6F; MRO
                    0x16A70,  // 16A70..16ABE; TANGSA
                    0x16ABF,  // 16ABF       ; UNKNOWN
                    0x16AC0,  // 16AC0..16AC9; TANGSA
                    0x16ACA,  // 16ACA..16ACF; UNKNOWN
                    0x16AD0,  // 16AD0..16AED; BASSA_VAH
                    0x16AEE,  // 16AEE..16AEF; UNKNOWN
                    0x16AF0,  // 16AF0..16AF5; BASSA_VAH
                    0x16AF6,  // 16AF6..16AFF; UNKNOWN
                    0x16B00,  // 16B00..16B45; PAHAWH_HMONG
                    0x16B46,  // 16B46..16B4F; UNKNOWN
                    0x16B50,  // 16B50..16B59; PAHAWH_HMONG
                    0x16B5A,  // 16B5A       ; UNKNOWN
                    0x16B5B,  // 16B5B..16B61; PAHAWH_HMONG
                    0x16B62,  // 16B62       ; UNKNOWN
                    0x16B63,  // 16B63..16B77; PAHAWH_HMONG
                    0x16B78,  // 16B78..16B7C; UNKNOWN
                    0x16B7D,  // 16B7D..16B8F; PAHAWH_HMONG
                    0x16B90,  // 16B90..16D3F; UNKNOWN
                    0x16D40,  // 16D40..16D79; KIRAT_RAI
                    0x16D7A,  // 16D7A..16E3F; UNKNOWN
                    0x16E40,  // 16E40..16E9A; MEDEFAIDRIN
                    0x16E9B,  // 16E9B..16EFF; UNKNOWN
                    0x16F00,  // 16F00..16F4A; MIAO
                    0x16F4B,  // 16F4B..16F4E; UNKNOWN
                    0x16F4F,  // 16F4F..16F87; MIAO
                    0x16F88,  // 16F88..16F8E; UNKNOWN
                    0x16F8F,  // 16F8F..16F9F; MIAO
                    0x16FA0,  // 16FA0..16FDF; UNKNOWN
                    0x16FE0,  // 16FE0       ; TANGUT
                    0x16FE1,  // 16FE1       ; NUSHU
                    0x16FE2,  // 16FE2..16FE3; HAN
                    0x16FE4,  // 16FE4       ; KHITAN_SMALL_SCRIPT
                    0x16FE5,  // 16FE5..16FEF; UNKNOWN
                    0x16FF0,  // 16FF0..16FF1; HAN
                    0x16FF2,  // 16FF2..16FFF; UNKNOWN
                    0x17000,  // 17000..187F7; TANGUT
                    0x187F8,  // 187F8..187FF; UNKNOWN
                    0x18800,  // 18800..18AFF; TANGUT
                    0x18B00,  // 18B00..18CD5; KHITAN_SMALL_SCRIPT
                    0x18CD6,  // 18CD6..18CFE; UNKNOWN
                    0x18CFF,  // 18CFF       ; KHITAN_SMALL_SCRIPT
                    0x18D00,  // 18D00..18D08; TANGUT
                    0x18D09,  // 18D09..1AFEF; UNKNOWN
                    0x1AFF0,  // 1AFF0..1AFF3; KATAKANA
                    0x1AFF4,  // 1AFF4       ; UNKNOWN
                    0x1AFF5,  // 1AFF5..1AFFB; KATAKANA
                    0x1AFFC,  // 1AFFC       ; UNKNOWN
                    0x1AFFD,  // 1AFFD..1AFFE; KATAKANA
                    0x1AFFF,  // 1AFFF       ; UNKNOWN
                    0x1B000,  // 1B000       ; KATAKANA
                    0x1B001,  // 1B001..1B11F; HIRAGANA
                    0x1B120,  // 1B120..1B122; KATAKANA
                    0x1B123,  // 1B123..1B131; UNKNOWN
                    0x1B132,  // 1B132       ; HIRAGANA
                    0x1B133,  // 1B133..1B14F; UNKNOWN
                    0x1B150,  // 1B150..1B152; HIRAGANA
                    0x1B153,  // 1B153..1B154; UNKNOWN
                    0x1B155,  // 1B155       ; KATAKANA
                    0x1B156,  // 1B156..1B163; UNKNOWN
                    0x1B164,  // 1B164..1B167; KATAKANA
                    0x1B168,  // 1B168..1B16F; UNKNOWN
                    0x1B170,  // 1B170..1B2FB; NUSHU
                    0x1B2FC,  // 1B2FC..1BBFF; UNKNOWN
                    0x1BC00,  // 1BC00..1BC6A; DUPLOYAN
                    0x1BC6B,  // 1BC6B..1BC6F; UNKNOWN
                    0x1BC70,  // 1BC70..1BC7C; DUPLOYAN
                    0x1BC7D,  // 1BC7D..1BC7F; UNKNOWN
                    0x1BC80,  // 1BC80..1BC88; DUPLOYAN
                    0x1BC89,  // 1BC89..1BC8F; UNKNOWN
                    0x1BC90,  // 1BC90..1BC99; DUPLOYAN
                    0x1BC9A,  // 1BC9A..1BC9B; UNKNOWN
                    0x1BC9C,  // 1BC9C..1BC9F; DUPLOYAN
                    0x1BCA0,  // 1BCA0..1BCA3; COMMON
                    0x1BCA4,  // 1BCA4..1CBFF; UNKNOWN
                    0x1CC00,  // 1CC00..1CCF9; COMMON
                    0x1CCFA,  // 1CCFA..1CCFF; UNKNOWN
                    0x1CD00,  // 1CD00..1CEB3; COMMON
                    0x1CEB4,  // 1CEB4..1CEFF; UNKNOWN
                    0x1CF00,  // 1CF00..1CF2D; INHERITED
                    0x1CF2E,  // 1CF2E..1CF2F; UNKNOWN
                    0x1CF30,  // 1CF30..1CF46; INHERITED
                    0x1CF47,  // 1CF47..1CF4F; UNKNOWN
                    0x1CF50,  // 1CF50..1CFC3; COMMON
                    0x1CFC4,  // 1CFC4..1CFFF; UNKNOWN
                    0x1D000,  // 1D000..1D0F5; COMMON
                    0x1D0F6,  // 1D0F6..1D0FF; UNKNOWN
                    0x1D100,  // 1D100..1D126; COMMON
                    0x1D127,  // 1D127..1D128; UNKNOWN
                    0x1D129,  // 1D129..1D166; COMMON
                    0x1D167,  // 1D167..1D169; INHERITED
                    0x1D16A,  // 1D16A..1D17A; COMMON
                    0x1D17B,  // 1D17B..1D182; INHERITED
                    0x1D183,  // 1D183..1D184; COMMON
                    0x1D185,  // 1D185..1D18B; INHERITED
                    0x1D18C,  // 1D18C..1D1A9; COMMON
                    0x1D1AA,  // 1D1AA..1D1AD; INHERITED
                    0x1D1AE,  // 1D1AE..1D1EA; COMMON
                    0x1D1EB,  // 1D1EB..1D1FF; UNKNOWN
                    0x1D200,  // 1D200..1D245; GREEK
                    0x1D246,  // 1D246..1D2BF; UNKNOWN
                    0x1D2C0,  // 1D2C0..1D2D3; COMMON
                    0x1D2D4,  // 1D2D4..1D2DF; UNKNOWN
                    0x1D2E0,  // 1D2E0..1D2F3; COMMON
                    0x1D2F4,  // 1D2F4..1D2FF; UNKNOWN
                    0x1D300,  // 1D300..1D356; COMMON
                    0x1D357,  // 1D357..1D35F; UNKNOWN
                    0x1D360,  // 1D360..1D378; COMMON
                    0x1D379,  // 1D379..1D3FF; UNKNOWN
                    0x1D400,  // 1D400..1D454; COMMON
                    0x1D455,  // 1D455       ; UNKNOWN
                    0x1D456,  // 1D456..1D49C; COMMON
                    0x1D49D,  // 1D49D       ; UNKNOWN
                    0x1D49E,  // 1D49E..1D49F; COMMON
                    0x1D4A0,  // 1D4A0..1D4A1; UNKNOWN
                    0x1D4A2,  // 1D4A2       ; COMMON
                    0x1D4A3,  // 1D4A3..1D4A4; UNKNOWN
                    0x1D4A5,  // 1D4A5..1D4A6; COMMON
                    0x1D4A7,  // 1D4A7..1D4A8; UNKNOWN
                    0x1D4A9,  // 1D4A9..1D4AC; COMMON
                    0x1D4AD,  // 1D4AD       ; UNKNOWN
                    0x1D4AE,  // 1D4AE..1D4B9; COMMON
                    0x1D4BA,  // 1D4BA       ; UNKNOWN
                    0x1D4BB,  // 1D4BB       ; COMMON
                    0x1D4BC,  // 1D4BC       ; UNKNOWN
                    0x1D4BD,  // 1D4BD..1D4C3; COMMON
                    0x1D4C4,  // 1D4C4       ; UNKNOWN
                    0x1D4C5,  // 1D4C5..1D505; COMMON
                    0x1D506,  // 1D506       ; UNKNOWN
                    0x1D507,  // 1D507..1D50A; COMMON
                    0x1D50B,  // 1D50B..1D50C; UNKNOWN
                    0x1D50D,  // 1D50D..1D514; COMMON
                    0x1D515,  // 1D515       ; UNKNOWN
                    0x1D516,  // 1D516..1D51C; COMMON
                    0x1D51D,  // 1D51D       ; UNKNOWN
                    0x1D51E,  // 1D51E..1D539; COMMON
                    0x1D53A,  // 1D53A       ; UNKNOWN
                    0x1D53B,  // 1D53B..1D53E; COMMON
                    0x1D53F,  // 1D53F       ; UNKNOWN
                    0x1D540,  // 1D540..1D544; COMMON
                    0x1D545,  // 1D545       ; UNKNOWN
                    0x1D546,  // 1D546       ; COMMON
                    0x1D547,  // 1D547..1D549; UNKNOWN
                    0x1D54A,  // 1D54A..1D550; COMMON
                    0x1D551,  // 1D551       ; UNKNOWN
                    0x1D552,  // 1D552..1D6A5; COMMON
                    0x1D6A6,  // 1D6A6..1D6A7; UNKNOWN
                    0x1D6A8,  // 1D6A8..1D7CB; COMMON
                    0x1D7CC,  // 1D7CC..1D7CD; UNKNOWN
                    0x1D7CE,  // 1D7CE..1D7FF; COMMON
                    0x1D800,  // 1D800..1DA8B; SIGNWRITING
                    0x1DA8C,  // 1DA8C..1DA9A; UNKNOWN
                    0x1DA9B,  // 1DA9B..1DA9F; SIGNWRITING
                    0x1DAA0,  // 1DAA0       ; UNKNOWN
                    0x1DAA1,  // 1DAA1..1DAAF; SIGNWRITING
                    0x1DAB0,  // 1DAB0..1DEFF; UNKNOWN
                    0x1DF00,  // 1DF00..1DF1E; LATIN
                    0x1DF1F,  // 1DF1F..1DF24; UNKNOWN
                    0x1DF25,  // 1DF25..1DF2A; LATIN
                    0x1DF2B,  // 1DF2B..1DFFF; UNKNOWN
                    0x1E000,  // 1E000..1E006; GLAGOLITIC
                    0x1E007,  // 1E007       ; UNKNOWN
                    0x1E008,  // 1E008..1E018; GLAGOLITIC
                    0x1E019,  // 1E019..1E01A; UNKNOWN
                    0x1E01B,  // 1E01B..1E021; GLAGOLITIC
                    0x1E022,  // 1E022       ; UNKNOWN
                    0x1E023,  // 1E023..1E024; GLAGOLITIC
                    0x1E025,  // 1E025       ; UNKNOWN
                    0x1E026,  // 1E026..1E02A; GLAGOLITIC
                    0x1E02B,  // 1E02B..1E02F; UNKNOWN
                    0x1E030,  // 1E030..1E06D; CYRILLIC
                    0x1E06E,  // 1E06E..1E08E; UNKNOWN
                    0x1E08F,  // 1E08F       ; CYRILLIC
                    0x1E090,  // 1E090..1E0FF; UNKNOWN
                    0x1E100,  // 1E100..1E12C; NYIAKENG_PUACHUE_HMONG
                    0x1E12D,  // 1E12D..1E12F; UNKNOWN
                    0x1E130,  // 1E130..1E13D; NYIAKENG_PUACHUE_HMONG
                    0x1E13E,  // 1E13E..1E13F; UNKNOWN
                    0x1E140,  // 1E140..1E149; NYIAKENG_PUACHUE_HMONG
                    0x1E14A,  // 1E14A..1E14D; UNKNOWN
                    0x1E14E,  // 1E14E..1E14F; NYIAKENG_PUACHUE_HMONG
                    0x1E150,  // 1E150..1E28F; UNKNOWN
                    0x1E290,  // 1E290..1E2AE; TOTO
                    0x1E2AF,  // 1E2AF..1E2BF; UNKNOWN
                    0x1E2C0,  // 1E2C0..1E2F9; WANCHO
                    0x1E2FA,  // 1E2FA..1E2FE; UNKNOWN
                    0x1E2FF,  // 1E2FF       ; WANCHO
                    0x1E300,  // 1E300..1E4CF; UNKNOWN
                    0x1E4D0,  // 1E4D0..1E4F9; NAG_MUNDARI
                    0x1E4FA,  // 1E4FA..1E5CF; UNKNOWN
                    0x1E5D0,  // 1E5D0..1E5FA; OL_ONAL
                    0x1E5FB,  // 1E5FB..1E5FE; UNKNOWN
                    0x1E5FF,  // 1E5FF       ; OL_ONAL
                    0x1E600,  // 1E600..1E7DF; UNKNOWN
                    0x1E7E0,  // 1E7E0..1E7E6; ETHIOPIC
                    0x1E7E7,  // 1E7E7       ; UNKNOWN
                    0x1E7E8,  // 1E7E8..1E7EB; ETHIOPIC
                    0x1E7EC,  // 1E7EC       ; UNKNOWN
                    0x1E7ED,  // 1E7ED..1E7EE; ETHIOPIC
                    0x1E7EF,  // 1E7EF       ; UNKNOWN
                    0x1E7F0,  // 1E7F0..1E7FE; ETHIOPIC
                    0x1E7FF,  // 1E7FF       ; UNKNOWN
                    0x1E800,  // 1E800..1E8C4; MENDE_KIKAKUI
                    0x1E8C5,  // 1E8C5..1E8C6; UNKNOWN
                    0x1E8C7,  // 1E8C7..1E8D6; MENDE_KIKAKUI
                    0x1E8D7,  // 1E8D7..1E8FF; UNKNOWN
                    0x1E900,  // 1E900..1E94B; ADLAM
                    0x1E94C,  // 1E94C..1E94F; UNKNOWN
                    0x1E950,  // 1E950..1E959; ADLAM
                    0x1E95A,  // 1E95A..1E95D; UNKNOWN
                    0x1E95E,  // 1E95E..1E95F; ADLAM
                    0x1E960,  // 1E960..1EC70; UNKNOWN
                    0x1EC71,  // 1EC71..1ECB4; COMMON
                    0x1ECB5,  // 1ECB5..1ED00; UNKNOWN
                    0x1ED01,  // 1ED01..1ED3D; COMMON
                    0x1ED3E,  // 1ED3E..1EDFF; UNKNOWN
                    0x1EE00,  // 1EE00..1EE03; ARABIC
                    0x1EE04,  // 1EE04       ; UNKNOWN
                    0x1EE05,  // 1EE05..1EE1F; ARABIC
                    0x1EE20,  // 1EE20       ; UNKNOWN
                    0x1EE21,  // 1EE21..1EE22; ARABIC
                    0x1EE23,  // 1EE23       ; UNKNOWN
                    0x1EE24,  // 1EE24       ; ARABIC
                    0x1EE25,  // 1EE25..1EE26; UNKNOWN
                    0x1EE27,  // 1EE27       ; ARABIC
                    0x1EE28,  // 1EE28       ; UNKNOWN
                    0x1EE29,  // 1EE29..1EE32; ARABIC
                    0x1EE33,  // 1EE33       ; UNKNOWN
                    0x1EE34,  // 1EE34..1EE37; ARABIC
                    0x1EE38,  // 1EE38       ; UNKNOWN
                    0x1EE39,  // 1EE39       ; ARABIC
                    0x1EE3A,  // 1EE3A       ; UNKNOWN
                    0x1EE3B,  // 1EE3B       ; ARABIC
                    0x1EE3C,  // 1EE3C..1EE41; UNKNOWN
                    0x1EE42,  // 1EE42       ; ARABIC
                    0x1EE43,  // 1EE43..1EE46; UNKNOWN
                    0x1EE47,  // 1EE47       ; ARABIC
                    0x1EE48,  // 1EE48       ; UNKNOWN
                    0x1EE49,  // 1EE49       ; ARABIC
                    0x1EE4A,  // 1EE4A       ; UNKNOWN
                    0x1EE4B,  // 1EE4B       ; ARABIC
                    0x1EE4C,  // 1EE4C       ; UNKNOWN
                    0x1EE4D,  // 1EE4D..1EE4F; ARABIC
                    0x1EE50,  // 1EE50       ; UNKNOWN
                    0x1EE51,  // 1EE51..1EE52; ARABIC
                    0x1EE53,  // 1EE53       ; UNKNOWN
                    0x1EE54,  // 1EE54       ; ARABIC
                    0x1EE55,  // 1EE55..1EE56; UNKNOWN
                    0x1EE57,  // 1EE57       ; ARABIC
                    0x1EE58,  // 1EE58       ; UNKNOWN
                    0x1EE59,  // 1EE59       ; ARABIC
                    0x1EE5A,  // 1EE5A       ; UNKNOWN
                    0x1EE5B,  // 1EE5B       ; ARABIC
                    0x1EE5C,  // 1EE5C       ; UNKNOWN
                    0x1EE5D,  // 1EE5D       ; ARABIC
                    0x1EE5E,  // 1EE5E       ; UNKNOWN
                    0x1EE5F,  // 1EE5F       ; ARABIC
                    0x1EE60,  // 1EE60       ; UNKNOWN
                    0x1EE61,  // 1EE61..1EE62; ARABIC
                    0x1EE63,  // 1EE63       ; UNKNOWN
                    0x1EE64,  // 1EE64       ; ARABIC
                    0x1EE65,  // 1EE65..1EE66; UNKNOWN
                    0x1EE67,  // 1EE67..1EE6A; ARABIC
                    0x1EE6B,  // 1EE6B       ; UNKNOWN
                    0x1EE6C,  // 1EE6C..1EE72; ARABIC
                    0x1EE73,  // 1EE73       ; UNKNOWN
                    0x1EE74,  // 1EE74..1EE77; ARABIC
                    0x1EE78,  // 1EE78       ; UNKNOWN
                    0x1EE79,  // 1EE79..1EE7C; ARABIC
                    0x1EE7D,  // 1EE7D       ; UNKNOWN
                    0x1EE7E,  // 1EE7E       ; ARABIC
                    0x1EE7F,  // 1EE7F       ; UNKNOWN
                    0x1EE80,  // 1EE80..1EE89; ARABIC
                    0x1EE8A,  // 1EE8A       ; UNKNOWN
                    0x1EE8B,  // 1EE8B..1EE9B; ARABIC
                    0x1EE9C,  // 1EE9C..1EEA0; UNKNOWN
                    0x1EEA1,  // 1EEA1..1EEA3; ARABIC
                    0x1EEA4,  // 1EEA4       ; UNKNOWN
                    0x1EEA5,  // 1EEA5..1EEA9; ARABIC
                    0x1EEAA,  // 1EEAA       ; UNKNOWN
                    0x1EEAB,  // 1EEAB..1EEBB; ARABIC
                    0x1EEBC,  // 1EEBC..1EEEF; UNKNOWN
                    0x1EEF0,  // 1EEF0..1EEF1; ARABIC
                    0x1EEF2,  // 1EEF2..1EFFF; UNKNOWN
                    0x1F000,  // 1F000..1F02B; COMMON
                    0x1F02C,  // 1F02C..1F02F; UNKNOWN
                    0x1F030,  // 1F030..1F093; COMMON
                    0x1F094,  // 1F094..1F09F; UNKNOWN
                    0x1F0A0,  // 1F0A0..1F0AE; COMMON
                    0x1F0AF,  // 1F0AF..1F0B0; UNKNOWN
                    0x1F0B1,  // 1F0B1..1F0BF; COMMON
                    0x1F0C0,  // 1F0C0       ; UNKNOWN
                    0x1F0C1,  // 1F0C1..1F0CF; COMMON
                    0x1F0D0,  // 1F0D0       ; UNKNOWN
                    0x1F0D1,  // 1F0D1..1F0F5; COMMON
                    0x1F0F6,  // 1F0F6..1F0FF; UNKNOWN
                    0x1F100,  // 1F100..1F1AD; COMMON
                    0x1F1AE,  // 1F1AE..1F1E5; UNKNOWN
                    0x1F1E6,  // 1F1E6..1F1FF; COMMON
                    0x1F200,  // 1F200       ; HIRAGANA
                    0x1F201,  // 1F201..1F202; COMMON
                    0x1F203,  // 1F203..1F20F; UNKNOWN
                    0x1F210,  // 1F210..1F23B; COMMON
                    0x1F23C,  // 1F23C..1F23F; UNKNOWN
                    0x1F240,  // 1F240..1F248; COMMON
                    0x1F249,  // 1F249..1F24F; UNKNOWN
                    0x1F250,  // 1F250..1F251; COMMON
                    0x1F252,  // 1F252..1F25F; UNKNOWN
                    0x1F260,  // 1F260..1F265; COMMON
                    0x1F266,  // 1F266..1F2FF; UNKNOWN
                    0x1F300,  // 1F300..1F6D7; COMMON
                    0x1F6D8,  // 1F6D8..1F6DB; UNKNOWN
                    0x1F6DC,  // 1F6DC..1F6EC; COMMON
                    0x1F6ED,  // 1F6ED..1F6EF; UNKNOWN
                    0x1F6F0,  // 1F6F0..1F6FC; COMMON
                    0x1F6FD,  // 1F6FD..1F6FF; UNKNOWN
                    0x1F700,  // 1F700..1F776; COMMON
                    0x1F777,  // 1F777..1F77A; UNKNOWN
                    0x1F77B,  // 1F77B..1F7D9; COMMON
                    0x1F7DA,  // 1F7DA..1F7DF; UNKNOWN
                    0x1F7E0,  // 1F7E0..1F7EB; COMMON
                    0x1F7EC,  // 1F7EC..1F7EF; UNKNOWN
                    0x1F7F0,  // 1F7F0       ; COMMON
                    0x1F7F1,  // 1F7F1..1F7FF; UNKNOWN
                    0x1F800,  // 1F800..1F80B; COMMON
                    0x1F80C,  // 1F80C..1F80F; UNKNOWN
                    0x1F810,  // 1F810..1F847; COMMON
                    0x1F848,  // 1F848..1F84F; UNKNOWN
                    0x1F850,  // 1F850..1F859; COMMON
                    0x1F85A,  // 1F85A..1F85F; UNKNOWN
                    0x1F860,  // 1F860..1F887; COMMON
                    0x1F888,  // 1F888..1F88F; UNKNOWN
                    0x1F890,  // 1F890..1F8AD; COMMON
                    0x1F8AE,  // 1F8AE..1F8AF; UNKNOWN
                    0x1F8B0,  // 1F8B0..1F8BB; COMMON
                    0x1F8BC,  // 1F8BC..1F8BF; UNKNOWN
                    0x1F8C0,  // 1F8C0..1F8C1; COMMON
                    0x1F8C2,  // 1F8C2..1F8FF; UNKNOWN
                    0x1F900,  // 1F900..1FA53; COMMON
                    0x1FA54,  // 1FA54..1FA5F; UNKNOWN
                    0x1FA60,  // 1FA60..1FA6D; COMMON
                    0x1FA6E,  // 1FA6E..1FA6F; UNKNOWN
                    0x1FA70,  // 1FA70..1FA7C; COMMON
                    0x1FA7D,  // 1FA7D..1FA7F; UNKNOWN
                    0x1FA80,  // 1FA80..1FA89; COMMON
                    0x1FA8A,  // 1FA8A..1FA8E; UNKNOWN
                    0x1FA8F,  // 1FA8F..1FAC6; COMMON
                    0x1FAC7,  // 1FAC7..1FACD; UNKNOWN
                    0x1FACE,  // 1FACE..1FADC; COMMON
                    0x1FADD,  // 1FADD..1FADE; UNKNOWN
                    0x1FADF,  // 1FADF..1FAE9; COMMON
                    0x1FAEA,  // 1FAEA..1FAEF; UNKNOWN
                    0x1FAF0,  // 1FAF0..1FAF8; COMMON
                    0x1FAF9,  // 1FAF9..1FAFF; UNKNOWN
                    0x1FB00,  // 1FB00..1FB92; COMMON
                    0x1FB93,  // 1FB93       ; UNKNOWN
                    0x1FB94,  // 1FB94..1FBF9; COMMON
                    0x1FBFA,  // 1FBFA..1FFFF; UNKNOWN
                    0x20000,  // 20000..2A6DF; HAN
                    0x2A6E0,  // 2A6E0..2A6FF; UNKNOWN
                    0x2A700,  // 2A700..2B739; HAN
                    0x2B73A,  // 2B73A..2B73F; UNKNOWN
                    0x2B740,  // 2B740..2B81D; HAN
                    0x2B81E,  // 2B81E..2B81F; UNKNOWN
                    0x2B820,  // 2B820..2CEA1; HAN
                    0x2CEA2,  // 2CEA2..2CEAF; UNKNOWN
                    0x2CEB0,  // 2CEB0..2EBE0; HAN
                    0x2EBE1,  // 2EBE1..2EBEF; UNKNOWN
                    0x2EBF0,  // 2EBF0..2EE5D; HAN
                    0x2EE5E,  // 2EE5E..2F7FF; UNKNOWN
                    0x2F800,  // 2F800..2FA1D; HAN
                    0x2FA1E,  // 2FA1E..2FFFF; UNKNOWN
                    0x30000,  // 30000..3134A; HAN
                    0x3134B,  // 3134B..3134F; UNKNOWN
                    0x31350,  // 31350..323AF; HAN
                    0x323B0,  // 323B0..E0000; UNKNOWN
                    0xE0001,  // E0001       ; COMMON
                    0xE0002,  // E0002..E001F; UNKNOWN
                    0xE0020,  // E0020..E007F; COMMON
                    0xE0080,  // E0080..E00FF; UNKNOWN
                    0xE0100,  // E0100..E01EF; INHERITED
                    0xE01F0,  // E01F0..10FFFF; UNKNOWN
                )

                private val scripts = arrayOf<UnicodeScript?>(
                    COMMON,  // 0000..0040
                    LATIN,  // 0041..005A
                    COMMON,  // 005B..0060
                    LATIN,  // 0061..007A
                    COMMON,  // 007B..00A9
                    LATIN,  // 00AA
                    COMMON,  // 00AB..00B9
                    LATIN,  // 00BA
                    COMMON,  // 00BB..00BF
                    LATIN,  // 00C0..00D6
                    COMMON,  // 00D7
                    LATIN,  // 00D8..00F6
                    COMMON,  // 00F7
                    LATIN,  // 00F8..02B8
                    COMMON,  // 02B9..02DF
                    LATIN,  // 02E0..02E4
                    COMMON,  // 02E5..02E9
                    BOPOMOFO,  // 02EA..02EB
                    COMMON,  // 02EC..02FF
                    INHERITED,  // 0300..036F
                    GREEK,  // 0370..0373
                    COMMON,  // 0374
                    GREEK,  // 0375..0377
                    UNKNOWN,  // 0378..0379
                    GREEK,  // 037A..037D
                    COMMON,  // 037E
                    GREEK,  // 037F
                    UNKNOWN,  // 0380..0383
                    GREEK,  // 0384
                    COMMON,  // 0385
                    GREEK,  // 0386
                    COMMON,  // 0387
                    GREEK,  // 0388..038A
                    UNKNOWN,  // 038B
                    GREEK,  // 038C
                    UNKNOWN,  // 038D
                    GREEK,  // 038E..03A1
                    UNKNOWN,  // 03A2
                    GREEK,  // 03A3..03E1
                    COPTIC,  // 03E2..03EF
                    GREEK,  // 03F0..03FF
                    CYRILLIC,  // 0400..0484
                    INHERITED,  // 0485..0486
                    CYRILLIC,  // 0487..052F
                    UNKNOWN,  // 0530
                    ARMENIAN,  // 0531..0556
                    UNKNOWN,  // 0557..0558
                    ARMENIAN,  // 0559..058A
                    UNKNOWN,  // 058B..058C
                    ARMENIAN,  // 058D..058F
                    UNKNOWN,  // 0590
                    HEBREW,  // 0591..05C7
                    UNKNOWN,  // 05C8..05CF
                    HEBREW,  // 05D0..05EA
                    UNKNOWN,  // 05EB..05EE
                    HEBREW,  // 05EF..05F4
                    UNKNOWN,  // 05F5..05FF
                    ARABIC,  // 0600..0604
                    COMMON,  // 0605
                    ARABIC,  // 0606..060B
                    COMMON,  // 060C
                    ARABIC,  // 060D..061A
                    COMMON,  // 061B
                    ARABIC,  // 061C..061E
                    COMMON,  // 061F
                    ARABIC,  // 0620..063F
                    COMMON,  // 0640
                    ARABIC,  // 0641..064A
                    INHERITED,  // 064B..0655
                    ARABIC,  // 0656..066F
                    INHERITED,  // 0670
                    ARABIC,  // 0671..06DC
                    COMMON,  // 06DD
                    ARABIC,  // 06DE..06FF
                    SYRIAC,  // 0700..070D
                    UNKNOWN,  // 070E
                    SYRIAC,  // 070F..074A
                    UNKNOWN,  // 074B..074C
                    SYRIAC,  // 074D..074F
                    ARABIC,  // 0750..077F
                    THAANA,  // 0780..07B1
                    UNKNOWN,  // 07B2..07BF
                    NKO,  // 07C0..07FA
                    UNKNOWN,  // 07FB..07FC
                    NKO,  // 07FD..07FF
                    SAMARITAN,  // 0800..082D
                    UNKNOWN,  // 082E..082F
                    SAMARITAN,  // 0830..083E
                    UNKNOWN,  // 083F
                    MANDAIC,  // 0840..085B
                    UNKNOWN,  // 085C..085D
                    MANDAIC,  // 085E
                    UNKNOWN,  // 085F
                    SYRIAC,  // 0860..086A
                    UNKNOWN,  // 086B..086F
                    ARABIC,  // 0870..088E
                    UNKNOWN,  // 088F
                    ARABIC,  // 0890..0891
                    UNKNOWN,  // 0892..0896
                    ARABIC,  // 0897..08E1
                    COMMON,  // 08E2
                    ARABIC,  // 08E3..08FF
                    DEVANAGARI,  // 0900..0950
                    INHERITED,  // 0951..0954
                    DEVANAGARI,  // 0955..0963
                    COMMON,  // 0964..0965
                    DEVANAGARI,  // 0966..097F
                    BENGALI,  // 0980..0983
                    UNKNOWN,  // 0984
                    BENGALI,  // 0985..098C
                    UNKNOWN,  // 098D..098E
                    BENGALI,  // 098F..0990
                    UNKNOWN,  // 0991..0992
                    BENGALI,  // 0993..09A8
                    UNKNOWN,  // 09A9
                    BENGALI,  // 09AA..09B0
                    UNKNOWN,  // 09B1
                    BENGALI,  // 09B2
                    UNKNOWN,  // 09B3..09B5
                    BENGALI,  // 09B6..09B9
                    UNKNOWN,  // 09BA..09BB
                    BENGALI,  // 09BC..09C4
                    UNKNOWN,  // 09C5..09C6
                    BENGALI,  // 09C7..09C8
                    UNKNOWN,  // 09C9..09CA
                    BENGALI,  // 09CB..09CE
                    UNKNOWN,  // 09CF..09D6
                    BENGALI,  // 09D7
                    UNKNOWN,  // 09D8..09DB
                    BENGALI,  // 09DC..09DD
                    UNKNOWN,  // 09DE
                    BENGALI,  // 09DF..09E3
                    UNKNOWN,  // 09E4..09E5
                    BENGALI,  // 09E6..09FE
                    UNKNOWN,  // 09FF..0A00
                    GURMUKHI,  // 0A01..0A03
                    UNKNOWN,  // 0A04
                    GURMUKHI,  // 0A05..0A0A
                    UNKNOWN,  // 0A0B..0A0E
                    GURMUKHI,  // 0A0F..0A10
                    UNKNOWN,  // 0A11..0A12
                    GURMUKHI,  // 0A13..0A28
                    UNKNOWN,  // 0A29
                    GURMUKHI,  // 0A2A..0A30
                    UNKNOWN,  // 0A31
                    GURMUKHI,  // 0A32..0A33
                    UNKNOWN,  // 0A34
                    GURMUKHI,  // 0A35..0A36
                    UNKNOWN,  // 0A37
                    GURMUKHI,  // 0A38..0A39
                    UNKNOWN,  // 0A3A..0A3B
                    GURMUKHI,  // 0A3C
                    UNKNOWN,  // 0A3D
                    GURMUKHI,  // 0A3E..0A42
                    UNKNOWN,  // 0A43..0A46
                    GURMUKHI,  // 0A47..0A48
                    UNKNOWN,  // 0A49..0A4A
                    GURMUKHI,  // 0A4B..0A4D
                    UNKNOWN,  // 0A4E..0A50
                    GURMUKHI,  // 0A51
                    UNKNOWN,  // 0A52..0A58
                    GURMUKHI,  // 0A59..0A5C
                    UNKNOWN,  // 0A5D
                    GURMUKHI,  // 0A5E
                    UNKNOWN,  // 0A5F..0A65
                    GURMUKHI,  // 0A66..0A76
                    UNKNOWN,  // 0A77..0A80
                    GUJARATI,  // 0A81..0A83
                    UNKNOWN,  // 0A84
                    GUJARATI,  // 0A85..0A8D
                    UNKNOWN,  // 0A8E
                    GUJARATI,  // 0A8F..0A91
                    UNKNOWN,  // 0A92
                    GUJARATI,  // 0A93..0AA8
                    UNKNOWN,  // 0AA9
                    GUJARATI,  // 0AAA..0AB0
                    UNKNOWN,  // 0AB1
                    GUJARATI,  // 0AB2..0AB3
                    UNKNOWN,  // 0AB4
                    GUJARATI,  // 0AB5..0AB9
                    UNKNOWN,  // 0ABA..0ABB
                    GUJARATI,  // 0ABC..0AC5
                    UNKNOWN,  // 0AC6
                    GUJARATI,  // 0AC7..0AC9
                    UNKNOWN,  // 0ACA
                    GUJARATI,  // 0ACB..0ACD
                    UNKNOWN,  // 0ACE..0ACF
                    GUJARATI,  // 0AD0
                    UNKNOWN,  // 0AD1..0ADF
                    GUJARATI,  // 0AE0..0AE3
                    UNKNOWN,  // 0AE4..0AE5
                    GUJARATI,  // 0AE6..0AF1
                    UNKNOWN,  // 0AF2..0AF8
                    GUJARATI,  // 0AF9..0AFF
                    UNKNOWN,  // 0B00
                    ORIYA,  // 0B01..0B03
                    UNKNOWN,  // 0B04
                    ORIYA,  // 0B05..0B0C
                    UNKNOWN,  // 0B0D..0B0E
                    ORIYA,  // 0B0F..0B10
                    UNKNOWN,  // 0B11..0B12
                    ORIYA,  // 0B13..0B28
                    UNKNOWN,  // 0B29
                    ORIYA,  // 0B2A..0B30
                    UNKNOWN,  // 0B31
                    ORIYA,  // 0B32..0B33
                    UNKNOWN,  // 0B34
                    ORIYA,  // 0B35..0B39
                    UNKNOWN,  // 0B3A..0B3B
                    ORIYA,  // 0B3C..0B44
                    UNKNOWN,  // 0B45..0B46
                    ORIYA,  // 0B47..0B48
                    UNKNOWN,  // 0B49..0B4A
                    ORIYA,  // 0B4B..0B4D
                    UNKNOWN,  // 0B4E..0B54
                    ORIYA,  // 0B55..0B57
                    UNKNOWN,  // 0B58..0B5B
                    ORIYA,  // 0B5C..0B5D
                    UNKNOWN,  // 0B5E
                    ORIYA,  // 0B5F..0B63
                    UNKNOWN,  // 0B64..0B65
                    ORIYA,  // 0B66..0B77
                    UNKNOWN,  // 0B78..0B81
                    TAMIL,  // 0B82..0B83
                    UNKNOWN,  // 0B84
                    TAMIL,  // 0B85..0B8A
                    UNKNOWN,  // 0B8B..0B8D
                    TAMIL,  // 0B8E..0B90
                    UNKNOWN,  // 0B91
                    TAMIL,  // 0B92..0B95
                    UNKNOWN,  // 0B96..0B98
                    TAMIL,  // 0B99..0B9A
                    UNKNOWN,  // 0B9B
                    TAMIL,  // 0B9C
                    UNKNOWN,  // 0B9D
                    TAMIL,  // 0B9E..0B9F
                    UNKNOWN,  // 0BA0..0BA2
                    TAMIL,  // 0BA3..0BA4
                    UNKNOWN,  // 0BA5..0BA7
                    TAMIL,  // 0BA8..0BAA
                    UNKNOWN,  // 0BAB..0BAD
                    TAMIL,  // 0BAE..0BB9
                    UNKNOWN,  // 0BBA..0BBD
                    TAMIL,  // 0BBE..0BC2
                    UNKNOWN,  // 0BC3..0BC5
                    TAMIL,  // 0BC6..0BC8
                    UNKNOWN,  // 0BC9
                    TAMIL,  // 0BCA..0BCD
                    UNKNOWN,  // 0BCE..0BCF
                    TAMIL,  // 0BD0
                    UNKNOWN,  // 0BD1..0BD6
                    TAMIL,  // 0BD7
                    UNKNOWN,  // 0BD8..0BE5
                    TAMIL,  // 0BE6..0BFA
                    UNKNOWN,  // 0BFB..0BFF
                    TELUGU,  // 0C00..0C0C
                    UNKNOWN,  // 0C0D
                    TELUGU,  // 0C0E..0C10
                    UNKNOWN,  // 0C11
                    TELUGU,  // 0C12..0C28
                    UNKNOWN,  // 0C29
                    TELUGU,  // 0C2A..0C39
                    UNKNOWN,  // 0C3A..0C3B
                    TELUGU,  // 0C3C..0C44
                    UNKNOWN,  // 0C45
                    TELUGU,  // 0C46..0C48
                    UNKNOWN,  // 0C49
                    TELUGU,  // 0C4A..0C4D
                    UNKNOWN,  // 0C4E..0C54
                    TELUGU,  // 0C55..0C56
                    UNKNOWN,  // 0C57
                    TELUGU,  // 0C58..0C5A
                    UNKNOWN,  // 0C5B..0C5C
                    TELUGU,  // 0C5D
                    UNKNOWN,  // 0C5E..0C5F
                    TELUGU,  // 0C60..0C63
                    UNKNOWN,  // 0C64..0C65
                    TELUGU,  // 0C66..0C6F
                    UNKNOWN,  // 0C70..0C76
                    TELUGU,  // 0C77..0C7F
                    KANNADA,  // 0C80..0C8C
                    UNKNOWN,  // 0C8D
                    KANNADA,  // 0C8E..0C90
                    UNKNOWN,  // 0C91
                    KANNADA,  // 0C92..0CA8
                    UNKNOWN,  // 0CA9
                    KANNADA,  // 0CAA..0CB3
                    UNKNOWN,  // 0CB4
                    KANNADA,  // 0CB5..0CB9
                    UNKNOWN,  // 0CBA..0CBB
                    KANNADA,  // 0CBC..0CC4
                    UNKNOWN,  // 0CC5
                    KANNADA,  // 0CC6..0CC8
                    UNKNOWN,  // 0CC9
                    KANNADA,  // 0CCA..0CCD
                    UNKNOWN,  // 0CCE..0CD4
                    KANNADA,  // 0CD5..0CD6
                    UNKNOWN,  // 0CD7..0CDC
                    KANNADA,  // 0CDD..0CDE
                    UNKNOWN,  // 0CDF
                    KANNADA,  // 0CE0..0CE3
                    UNKNOWN,  // 0CE4..0CE5
                    KANNADA,  // 0CE6..0CEF
                    UNKNOWN,  // 0CF0
                    KANNADA,  // 0CF1..0CF3
                    UNKNOWN,  // 0CF4..0CFF
                    MALAYALAM,  // 0D00..0D0C
                    UNKNOWN,  // 0D0D
                    MALAYALAM,  // 0D0E..0D10
                    UNKNOWN,  // 0D11
                    MALAYALAM,  // 0D12..0D44
                    UNKNOWN,  // 0D45
                    MALAYALAM,  // 0D46..0D48
                    UNKNOWN,  // 0D49
                    MALAYALAM,  // 0D4A..0D4F
                    UNKNOWN,  // 0D50..0D53
                    MALAYALAM,  // 0D54..0D63
                    UNKNOWN,  // 0D64..0D65
                    MALAYALAM,  // 0D66..0D7F
                    UNKNOWN,  // 0D80
                    SINHALA,  // 0D81..0D83
                    UNKNOWN,  // 0D84
                    SINHALA,  // 0D85..0D96
                    UNKNOWN,  // 0D97..0D99
                    SINHALA,  // 0D9A..0DB1
                    UNKNOWN,  // 0DB2
                    SINHALA,  // 0DB3..0DBB
                    UNKNOWN,  // 0DBC
                    SINHALA,  // 0DBD
                    UNKNOWN,  // 0DBE..0DBF
                    SINHALA,  // 0DC0..0DC6
                    UNKNOWN,  // 0DC7..0DC9
                    SINHALA,  // 0DCA
                    UNKNOWN,  // 0DCB..0DCE
                    SINHALA,  // 0DCF..0DD4
                    UNKNOWN,  // 0DD5
                    SINHALA,  // 0DD6
                    UNKNOWN,  // 0DD7
                    SINHALA,  // 0DD8..0DDF
                    UNKNOWN,  // 0DE0..0DE5
                    SINHALA,  // 0DE6..0DEF
                    UNKNOWN,  // 0DF0..0DF1
                    SINHALA,  // 0DF2..0DF4
                    UNKNOWN,  // 0DF5..0E00
                    THAI,  // 0E01..0E3A
                    UNKNOWN,  // 0E3B..0E3E
                    COMMON,  // 0E3F
                    THAI,  // 0E40..0E5B
                    UNKNOWN,  // 0E5C..0E80
                    LAO,  // 0E81..0E82
                    UNKNOWN,  // 0E83
                    LAO,  // 0E84
                    UNKNOWN,  // 0E85
                    LAO,  // 0E86..0E8A
                    UNKNOWN,  // 0E8B
                    LAO,  // 0E8C..0EA3
                    UNKNOWN,  // 0EA4
                    LAO,  // 0EA5
                    UNKNOWN,  // 0EA6
                    LAO,  // 0EA7..0EBD
                    UNKNOWN,  // 0EBE..0EBF
                    LAO,  // 0EC0..0EC4
                    UNKNOWN,  // 0EC5
                    LAO,  // 0EC6
                    UNKNOWN,  // 0EC7
                    LAO,  // 0EC8..0ECE
                    UNKNOWN,  // 0ECF
                    LAO,  // 0ED0..0ED9
                    UNKNOWN,  // 0EDA..0EDB
                    LAO,  // 0EDC..0EDF
                    UNKNOWN,  // 0EE0..0EFF
                    TIBETAN,  // 0F00..0F47
                    UNKNOWN,  // 0F48
                    TIBETAN,  // 0F49..0F6C
                    UNKNOWN,  // 0F6D..0F70
                    TIBETAN,  // 0F71..0F97
                    UNKNOWN,  // 0F98
                    TIBETAN,  // 0F99..0FBC
                    UNKNOWN,  // 0FBD
                    TIBETAN,  // 0FBE..0FCC
                    UNKNOWN,  // 0FCD
                    TIBETAN,  // 0FCE..0FD4
                    COMMON,  // 0FD5..0FD8
                    TIBETAN,  // 0FD9..0FDA
                    UNKNOWN,  // 0FDB..0FFF
                    MYANMAR,  // 1000..109F
                    GEORGIAN,  // 10A0..10C5
                    UNKNOWN,  // 10C6
                    GEORGIAN,  // 10C7
                    UNKNOWN,  // 10C8..10CC
                    GEORGIAN,  // 10CD
                    UNKNOWN,  // 10CE..10CF
                    GEORGIAN,  // 10D0..10FA
                    COMMON,  // 10FB
                    GEORGIAN,  // 10FC..10FF
                    HANGUL,  // 1100..11FF
                    ETHIOPIC,  // 1200..1248
                    UNKNOWN,  // 1249
                    ETHIOPIC,  // 124A..124D
                    UNKNOWN,  // 124E..124F
                    ETHIOPIC,  // 1250..1256
                    UNKNOWN,  // 1257
                    ETHIOPIC,  // 1258
                    UNKNOWN,  // 1259
                    ETHIOPIC,  // 125A..125D
                    UNKNOWN,  // 125E..125F
                    ETHIOPIC,  // 1260..1288
                    UNKNOWN,  // 1289
                    ETHIOPIC,  // 128A..128D
                    UNKNOWN,  // 128E..128F
                    ETHIOPIC,  // 1290..12B0
                    UNKNOWN,  // 12B1
                    ETHIOPIC,  // 12B2..12B5
                    UNKNOWN,  // 12B6..12B7
                    ETHIOPIC,  // 12B8..12BE
                    UNKNOWN,  // 12BF
                    ETHIOPIC,  // 12C0
                    UNKNOWN,  // 12C1
                    ETHIOPIC,  // 12C2..12C5
                    UNKNOWN,  // 12C6..12C7
                    ETHIOPIC,  // 12C8..12D6
                    UNKNOWN,  // 12D7
                    ETHIOPIC,  // 12D8..1310
                    UNKNOWN,  // 1311
                    ETHIOPIC,  // 1312..1315
                    UNKNOWN,  // 1316..1317
                    ETHIOPIC,  // 1318..135A
                    UNKNOWN,  // 135B..135C
                    ETHIOPIC,  // 135D..137C
                    UNKNOWN,  // 137D..137F
                    ETHIOPIC,  // 1380..1399
                    UNKNOWN,  // 139A..139F
                    CHEROKEE,  // 13A0..13F5
                    UNKNOWN,  // 13F6..13F7
                    CHEROKEE,  // 13F8..13FD
                    UNKNOWN,  // 13FE..13FF
                    CANADIAN_ABORIGINAL,  // 1400..167F
                    OGHAM,  // 1680..169C
                    UNKNOWN,  // 169D..169F
                    RUNIC,  // 16A0..16EA
                    COMMON,  // 16EB..16ED
                    RUNIC,  // 16EE..16F8
                    UNKNOWN,  // 16F9..16FF
                    TAGALOG,  // 1700..1715
                    UNKNOWN,  // 1716..171E
                    TAGALOG,  // 171F
                    HANUNOO,  // 1720..1734
                    COMMON,  // 1735..1736
                    UNKNOWN,  // 1737..173F
                    BUHID,  // 1740..1753
                    UNKNOWN,  // 1754..175F
                    TAGBANWA,  // 1760..176C
                    UNKNOWN,  // 176D
                    TAGBANWA,  // 176E..1770
                    UNKNOWN,  // 1771
                    TAGBANWA,  // 1772..1773
                    UNKNOWN,  // 1774..177F
                    KHMER,  // 1780..17DD
                    UNKNOWN,  // 17DE..17DF
                    KHMER,  // 17E0..17E9
                    UNKNOWN,  // 17EA..17EF
                    KHMER,  // 17F0..17F9
                    UNKNOWN,  // 17FA..17FF
                    MONGOLIAN,  // 1800..1801
                    COMMON,  // 1802..1803
                    MONGOLIAN,  // 1804
                    COMMON,  // 1805
                    MONGOLIAN,  // 1806..1819
                    UNKNOWN,  // 181A..181F
                    MONGOLIAN,  // 1820..1878
                    UNKNOWN,  // 1879..187F
                    MONGOLIAN,  // 1880..18AA
                    UNKNOWN,  // 18AB..18AF
                    CANADIAN_ABORIGINAL,  // 18B0..18F5
                    UNKNOWN,  // 18F6..18FF
                    LIMBU,  // 1900..191E
                    UNKNOWN,  // 191F
                    LIMBU,  // 1920..192B
                    UNKNOWN,  // 192C..192F
                    LIMBU,  // 1930..193B
                    UNKNOWN,  // 193C..193F
                    LIMBU,  // 1940
                    UNKNOWN,  // 1941..1943
                    LIMBU,  // 1944..194F
                    TAI_LE,  // 1950..196D
                    UNKNOWN,  // 196E..196F
                    TAI_LE,  // 1970..1974
                    UNKNOWN,  // 1975..197F
                    NEW_TAI_LUE,  // 1980..19AB
                    UNKNOWN,  // 19AC..19AF
                    NEW_TAI_LUE,  // 19B0..19C9
                    UNKNOWN,  // 19CA..19CF
                    NEW_TAI_LUE,  // 19D0..19DA
                    UNKNOWN,  // 19DB..19DD
                    NEW_TAI_LUE,  // 19DE..19DF
                    KHMER,  // 19E0..19FF
                    BUGINESE,  // 1A00..1A1B
                    UNKNOWN,  // 1A1C..1A1D
                    BUGINESE,  // 1A1E..1A1F
                    TAI_THAM,  // 1A20..1A5E
                    UNKNOWN,  // 1A5F
                    TAI_THAM,  // 1A60..1A7C
                    UNKNOWN,  // 1A7D..1A7E
                    TAI_THAM,  // 1A7F..1A89
                    UNKNOWN,  // 1A8A..1A8F
                    TAI_THAM,  // 1A90..1A99
                    UNKNOWN,  // 1A9A..1A9F
                    TAI_THAM,  // 1AA0..1AAD
                    UNKNOWN,  // 1AAE..1AAF
                    INHERITED,  // 1AB0..1ACE
                    UNKNOWN,  // 1ACF..1AFF
                    BALINESE,  // 1B00..1B4C
                    UNKNOWN,  // 1B4D
                    BALINESE,  // 1B4E..1B7F
                    SUNDANESE,  // 1B80..1BBF
                    BATAK,  // 1BC0..1BF3
                    UNKNOWN,  // 1BF4..1BFB
                    BATAK,  // 1BFC..1BFF
                    LEPCHA,  // 1C00..1C37
                    UNKNOWN,  // 1C38..1C3A
                    LEPCHA,  // 1C3B..1C49
                    UNKNOWN,  // 1C4A..1C4C
                    LEPCHA,  // 1C4D..1C4F
                    OL_CHIKI,  // 1C50..1C7F
                    CYRILLIC,  // 1C80..1C8A
                    UNKNOWN,  // 1C8B..1C8F
                    GEORGIAN,  // 1C90..1CBA
                    UNKNOWN,  // 1CBB..1CBC
                    GEORGIAN,  // 1CBD..1CBF
                    SUNDANESE,  // 1CC0..1CC7
                    UNKNOWN,  // 1CC8..1CCF
                    INHERITED,  // 1CD0..1CD2
                    COMMON,  // 1CD3
                    INHERITED,  // 1CD4..1CE0
                    COMMON,  // 1CE1
                    INHERITED,  // 1CE2..1CE8
                    COMMON,  // 1CE9..1CEC
                    INHERITED,  // 1CED
                    COMMON,  // 1CEE..1CF3
                    INHERITED,  // 1CF4
                    COMMON,  // 1CF5..1CF7
                    INHERITED,  // 1CF8..1CF9
                    COMMON,  // 1CFA
                    UNKNOWN,  // 1CFB..1CFF
                    LATIN,  // 1D00..1D25
                    GREEK,  // 1D26..1D2A
                    CYRILLIC,  // 1D2B
                    LATIN,  // 1D2C..1D5C
                    GREEK,  // 1D5D..1D61
                    LATIN,  // 1D62..1D65
                    GREEK,  // 1D66..1D6A
                    LATIN,  // 1D6B..1D77
                    CYRILLIC,  // 1D78
                    LATIN,  // 1D79..1DBE
                    GREEK,  // 1DBF
                    INHERITED,  // 1DC0..1DFF
                    LATIN,  // 1E00..1EFF
                    GREEK,  // 1F00..1F15
                    UNKNOWN,  // 1F16..1F17
                    GREEK,  // 1F18..1F1D
                    UNKNOWN,  // 1F1E..1F1F
                    GREEK,  // 1F20..1F45
                    UNKNOWN,  // 1F46..1F47
                    GREEK,  // 1F48..1F4D
                    UNKNOWN,  // 1F4E..1F4F
                    GREEK,  // 1F50..1F57
                    UNKNOWN,  // 1F58
                    GREEK,  // 1F59
                    UNKNOWN,  // 1F5A
                    GREEK,  // 1F5B
                    UNKNOWN,  // 1F5C
                    GREEK,  // 1F5D
                    UNKNOWN,  // 1F5E
                    GREEK,  // 1F5F..1F7D
                    UNKNOWN,  // 1F7E..1F7F
                    GREEK,  // 1F80..1FB4
                    UNKNOWN,  // 1FB5
                    GREEK,  // 1FB6..1FC4
                    UNKNOWN,  // 1FC5
                    GREEK,  // 1FC6..1FD3
                    UNKNOWN,  // 1FD4..1FD5
                    GREEK,  // 1FD6..1FDB
                    UNKNOWN,  // 1FDC
                    GREEK,  // 1FDD..1FEF
                    UNKNOWN,  // 1FF0..1FF1
                    GREEK,  // 1FF2..1FF4
                    UNKNOWN,  // 1FF5
                    GREEK,  // 1FF6..1FFE
                    UNKNOWN,  // 1FFF
                    COMMON,  // 2000..200B
                    INHERITED,  // 200C..200D
                    COMMON,  // 200E..2064
                    UNKNOWN,  // 2065
                    COMMON,  // 2066..2070
                    LATIN,  // 2071
                    UNKNOWN,  // 2072..2073
                    COMMON,  // 2074..207E
                    LATIN,  // 207F
                    COMMON,  // 2080..208E
                    UNKNOWN,  // 208F
                    LATIN,  // 2090..209C
                    UNKNOWN,  // 209D..209F
                    COMMON,  // 20A0..20C0
                    UNKNOWN,  // 20C1..20CF
                    INHERITED,  // 20D0..20F0
                    UNKNOWN,  // 20F1..20FF
                    COMMON,  // 2100..2125
                    GREEK,  // 2126
                    COMMON,  // 2127..2129
                    LATIN,  // 212A..212B
                    COMMON,  // 212C..2131
                    LATIN,  // 2132
                    COMMON,  // 2133..214D
                    LATIN,  // 214E
                    COMMON,  // 214F..215F
                    LATIN,  // 2160..2188
                    COMMON,  // 2189..218B
                    UNKNOWN,  // 218C..218F
                    COMMON,  // 2190..2429
                    UNKNOWN,  // 242A..243F
                    COMMON,  // 2440..244A
                    UNKNOWN,  // 244B..245F
                    COMMON,  // 2460..27FF
                    BRAILLE,  // 2800..28FF
                    COMMON,  // 2900..2B73
                    UNKNOWN,  // 2B74..2B75
                    COMMON,  // 2B76..2B95
                    UNKNOWN,  // 2B96
                    COMMON,  // 2B97..2BFF
                    GLAGOLITIC,  // 2C00..2C5F
                    LATIN,  // 2C60..2C7F
                    COPTIC,  // 2C80..2CF3
                    UNKNOWN,  // 2CF4..2CF8
                    COPTIC,  // 2CF9..2CFF
                    GEORGIAN,  // 2D00..2D25
                    UNKNOWN,  // 2D26
                    GEORGIAN,  // 2D27
                    UNKNOWN,  // 2D28..2D2C
                    GEORGIAN,  // 2D2D
                    UNKNOWN,  // 2D2E..2D2F
                    TIFINAGH,  // 2D30..2D67
                    UNKNOWN,  // 2D68..2D6E
                    TIFINAGH,  // 2D6F..2D70
                    UNKNOWN,  // 2D71..2D7E
                    TIFINAGH,  // 2D7F
                    ETHIOPIC,  // 2D80..2D96
                    UNKNOWN,  // 2D97..2D9F
                    ETHIOPIC,  // 2DA0..2DA6
                    UNKNOWN,  // 2DA7
                    ETHIOPIC,  // 2DA8..2DAE
                    UNKNOWN,  // 2DAF
                    ETHIOPIC,  // 2DB0..2DB6
                    UNKNOWN,  // 2DB7
                    ETHIOPIC,  // 2DB8..2DBE
                    UNKNOWN,  // 2DBF
                    ETHIOPIC,  // 2DC0..2DC6
                    UNKNOWN,  // 2DC7
                    ETHIOPIC,  // 2DC8..2DCE
                    UNKNOWN,  // 2DCF
                    ETHIOPIC,  // 2DD0..2DD6
                    UNKNOWN,  // 2DD7
                    ETHIOPIC,  // 2DD8..2DDE
                    UNKNOWN,  // 2DDF
                    CYRILLIC,  // 2DE0..2DFF
                    COMMON,  // 2E00..2E5D
                    UNKNOWN,  // 2E5E..2E7F
                    HAN,  // 2E80..2E99
                    UNKNOWN,  // 2E9A
                    HAN,  // 2E9B..2EF3
                    UNKNOWN,  // 2EF4..2EFF
                    HAN,  // 2F00..2FD5
                    UNKNOWN,  // 2FD6..2FEF
                    COMMON,  // 2FF0..3004
                    HAN,  // 3005
                    COMMON,  // 3006
                    HAN,  // 3007
                    COMMON,  // 3008..3020
                    HAN,  // 3021..3029
                    INHERITED,  // 302A..302D
                    HANGUL,  // 302E..302F
                    COMMON,  // 3030..3037
                    HAN,  // 3038..303B
                    COMMON,  // 303C..303F
                    UNKNOWN,  // 3040
                    HIRAGANA,  // 3041..3096
                    UNKNOWN,  // 3097..3098
                    INHERITED,  // 3099..309A
                    COMMON,  // 309B..309C
                    HIRAGANA,  // 309D..309F
                    COMMON,  // 30A0
                    KATAKANA,  // 30A1..30FA
                    COMMON,  // 30FB..30FC
                    KATAKANA,  // 30FD..30FF
                    UNKNOWN,  // 3100..3104
                    BOPOMOFO,  // 3105..312F
                    UNKNOWN,  // 3130
                    HANGUL,  // 3131..318E
                    UNKNOWN,  // 318F
                    COMMON,  // 3190..319F
                    BOPOMOFO,  // 31A0..31BF
                    COMMON,  // 31C0..31E5
                    UNKNOWN,  // 31E6..31EE
                    COMMON,  // 31EF
                    KATAKANA,  // 31F0..31FF
                    HANGUL,  // 3200..321E
                    UNKNOWN,  // 321F
                    COMMON,  // 3220..325F
                    HANGUL,  // 3260..327E
                    COMMON,  // 327F..32CF
                    KATAKANA,  // 32D0..32FE
                    COMMON,  // 32FF
                    KATAKANA,  // 3300..3357
                    COMMON,  // 3358..33FF
                    HAN,  // 3400..4DBF
                    COMMON,  // 4DC0..4DFF
                    HAN,  // 4E00..9FFF
                    YI,  // A000..A48C
                    UNKNOWN,  // A48D..A48F
                    YI,  // A490..A4C6
                    UNKNOWN,  // A4C7..A4CF
                    LISU,  // A4D0..A4FF
                    VAI,  // A500..A62B
                    UNKNOWN,  // A62C..A63F
                    CYRILLIC,  // A640..A69F
                    BAMUM,  // A6A0..A6F7
                    UNKNOWN,  // A6F8..A6FF
                    COMMON,  // A700..A721
                    LATIN,  // A722..A787
                    COMMON,  // A788..A78A
                    LATIN,  // A78B..A7CD
                    UNKNOWN,  // A7CE..A7CF
                    LATIN,  // A7D0..A7D1
                    UNKNOWN,  // A7D2
                    LATIN,  // A7D3
                    UNKNOWN,  // A7D4
                    LATIN,  // A7D5..A7DC
                    UNKNOWN,  // A7DD..A7F1
                    LATIN,  // A7F2..A7FF
                    SYLOTI_NAGRI,  // A800..A82C
                    UNKNOWN,  // A82D..A82F
                    COMMON,  // A830..A839
                    UNKNOWN,  // A83A..A83F
                    PHAGS_PA,  // A840..A877
                    UNKNOWN,  // A878..A87F
                    SAURASHTRA,  // A880..A8C5
                    UNKNOWN,  // A8C6..A8CD
                    SAURASHTRA,  // A8CE..A8D9
                    UNKNOWN,  // A8DA..A8DF
                    DEVANAGARI,  // A8E0..A8FF
                    KAYAH_LI,  // A900..A92D
                    COMMON,  // A92E
                    KAYAH_LI,  // A92F
                    REJANG,  // A930..A953
                    UNKNOWN,  // A954..A95E
                    REJANG,  // A95F
                    HANGUL,  // A960..A97C
                    UNKNOWN,  // A97D..A97F
                    JAVANESE,  // A980..A9CD
                    UNKNOWN,  // A9CE
                    COMMON,  // A9CF
                    JAVANESE,  // A9D0..A9D9
                    UNKNOWN,  // A9DA..A9DD
                    JAVANESE,  // A9DE..A9DF
                    MYANMAR,  // A9E0..A9FE
                    UNKNOWN,  // A9FF
                    CHAM,  // AA00..AA36
                    UNKNOWN,  // AA37..AA3F
                    CHAM,  // AA40..AA4D
                    UNKNOWN,  // AA4E..AA4F
                    CHAM,  // AA50..AA59
                    UNKNOWN,  // AA5A..AA5B
                    CHAM,  // AA5C..AA5F
                    MYANMAR,  // AA60..AA7F
                    TAI_VIET,  // AA80..AAC2
                    UNKNOWN,  // AAC3..AADA
                    TAI_VIET,  // AADB..AADF
                    MEETEI_MAYEK,  // AAE0..AAF6
                    UNKNOWN,  // AAF7..AB00
                    ETHIOPIC,  // AB01..AB06
                    UNKNOWN,  // AB07..AB08
                    ETHIOPIC,  // AB09..AB0E
                    UNKNOWN,  // AB0F..AB10
                    ETHIOPIC,  // AB11..AB16
                    UNKNOWN,  // AB17..AB1F
                    ETHIOPIC,  // AB20..AB26
                    UNKNOWN,  // AB27
                    ETHIOPIC,  // AB28..AB2E
                    UNKNOWN,  // AB2F
                    LATIN,  // AB30..AB5A
                    COMMON,  // AB5B
                    LATIN,  // AB5C..AB64
                    GREEK,  // AB65
                    LATIN,  // AB66..AB69
                    COMMON,  // AB6A..AB6B
                    UNKNOWN,  // AB6C..AB6F
                    CHEROKEE,  // AB70..ABBF
                    MEETEI_MAYEK,  // ABC0..ABED
                    UNKNOWN,  // ABEE..ABEF
                    MEETEI_MAYEK,  // ABF0..ABF9
                    UNKNOWN,  // ABFA..ABFF
                    HANGUL,  // AC00..D7A3
                    UNKNOWN,  // D7A4..D7AF
                    HANGUL,  // D7B0..D7C6
                    UNKNOWN,  // D7C7..D7CA
                    HANGUL,  // D7CB..D7FB
                    UNKNOWN,  // D7FC..F8FF
                    HAN,  // F900..FA6D
                    UNKNOWN,  // FA6E..FA6F
                    HAN,  // FA70..FAD9
                    UNKNOWN,  // FADA..FAFF
                    LATIN,  // FB00..FB06
                    UNKNOWN,  // FB07..FB12
                    ARMENIAN,  // FB13..FB17
                    UNKNOWN,  // FB18..FB1C
                    HEBREW,  // FB1D..FB36
                    UNKNOWN,  // FB37
                    HEBREW,  // FB38..FB3C
                    UNKNOWN,  // FB3D
                    HEBREW,  // FB3E
                    UNKNOWN,  // FB3F
                    HEBREW,  // FB40..FB41
                    UNKNOWN,  // FB42
                    HEBREW,  // FB43..FB44
                    UNKNOWN,  // FB45
                    HEBREW,  // FB46..FB4F
                    ARABIC,  // FB50..FBC2
                    UNKNOWN,  // FBC3..FBD2
                    ARABIC,  // FBD3..FD3D
                    COMMON,  // FD3E..FD3F
                    ARABIC,  // FD40..FD8F
                    UNKNOWN,  // FD90..FD91
                    ARABIC,  // FD92..FDC7
                    UNKNOWN,  // FDC8..FDCE
                    ARABIC,  // FDCF
                    UNKNOWN,  // FDD0..FDEF
                    ARABIC,  // FDF0..FDFF
                    INHERITED,  // FE00..FE0F
                    COMMON,  // FE10..FE19
                    UNKNOWN,  // FE1A..FE1F
                    INHERITED,  // FE20..FE2D
                    CYRILLIC,  // FE2E..FE2F
                    COMMON,  // FE30..FE52
                    UNKNOWN,  // FE53
                    COMMON,  // FE54..FE66
                    UNKNOWN,  // FE67
                    COMMON,  // FE68..FE6B
                    UNKNOWN,  // FE6C..FE6F
                    ARABIC,  // FE70..FE74
                    UNKNOWN,  // FE75
                    ARABIC,  // FE76..FEFC
                    UNKNOWN,  // FEFD..FEFE
                    COMMON,  // FEFF
                    UNKNOWN,  // FF00
                    COMMON,  // FF01..FF20
                    LATIN,  // FF21..FF3A
                    COMMON,  // FF3B..FF40
                    LATIN,  // FF41..FF5A
                    COMMON,  // FF5B..FF65
                    KATAKANA,  // FF66..FF6F
                    COMMON,  // FF70
                    KATAKANA,  // FF71..FF9D
                    COMMON,  // FF9E..FF9F
                    HANGUL,  // FFA0..FFBE
                    UNKNOWN,  // FFBF..FFC1
                    HANGUL,  // FFC2..FFC7
                    UNKNOWN,  // FFC8..FFC9
                    HANGUL,  // FFCA..FFCF
                    UNKNOWN,  // FFD0..FFD1
                    HANGUL,  // FFD2..FFD7
                    UNKNOWN,  // FFD8..FFD9
                    HANGUL,  // FFDA..FFDC
                    UNKNOWN,  // FFDD..FFDF
                    COMMON,  // FFE0..FFE6
                    UNKNOWN,  // FFE7
                    COMMON,  // FFE8..FFEE
                    UNKNOWN,  // FFEF..FFF8
                    COMMON,  // FFF9..FFFD
                    UNKNOWN,  // FFFE..FFFF
                    LINEAR_B,  // 10000..1000B
                    UNKNOWN,  // 1000C
                    LINEAR_B,  // 1000D..10026
                    UNKNOWN,  // 10027
                    LINEAR_B,  // 10028..1003A
                    UNKNOWN,  // 1003B
                    LINEAR_B,  // 1003C..1003D
                    UNKNOWN,  // 1003E
                    LINEAR_B,  // 1003F..1004D
                    UNKNOWN,  // 1004E..1004F
                    LINEAR_B,  // 10050..1005D
                    UNKNOWN,  // 1005E..1007F
                    LINEAR_B,  // 10080..100FA
                    UNKNOWN,  // 100FB..100FF
                    COMMON,  // 10100..10102
                    UNKNOWN,  // 10103..10106
                    COMMON,  // 10107..10133
                    UNKNOWN,  // 10134..10136
                    COMMON,  // 10137..1013F
                    GREEK,  // 10140..1018E
                    UNKNOWN,  // 1018F
                    COMMON,  // 10190..1019C
                    UNKNOWN,  // 1019D..1019F
                    GREEK,  // 101A0
                    UNKNOWN,  // 101A1..101CF
                    COMMON,  // 101D0..101FC
                    INHERITED,  // 101FD
                    UNKNOWN,  // 101FE..1027F
                    LYCIAN,  // 10280..1029C
                    UNKNOWN,  // 1029D..1029F
                    CARIAN,  // 102A0..102D0
                    UNKNOWN,  // 102D1..102DF
                    INHERITED,  // 102E0
                    COMMON,  // 102E1..102FB
                    UNKNOWN,  // 102FC..102FF
                    OLD_ITALIC,  // 10300..10323
                    UNKNOWN,  // 10324..1032C
                    OLD_ITALIC,  // 1032D..1032F
                    GOTHIC,  // 10330..1034A
                    UNKNOWN,  // 1034B..1034F
                    OLD_PERMIC,  // 10350..1037A
                    UNKNOWN,  // 1037B..1037F
                    UGARITIC,  // 10380..1039D
                    UNKNOWN,  // 1039E
                    UGARITIC,  // 1039F
                    OLD_PERSIAN,  // 103A0..103C3
                    UNKNOWN,  // 103C4..103C7
                    OLD_PERSIAN,  // 103C8..103D5
                    UNKNOWN,  // 103D6..103FF
                    DESERET,  // 10400..1044F
                    SHAVIAN,  // 10450..1047F
                    OSMANYA,  // 10480..1049D
                    UNKNOWN,  // 1049E..1049F
                    OSMANYA,  // 104A0..104A9
                    UNKNOWN,  // 104AA..104AF
                    OSAGE,  // 104B0..104D3
                    UNKNOWN,  // 104D4..104D7
                    OSAGE,  // 104D8..104FB
                    UNKNOWN,  // 104FC..104FF
                    ELBASAN,  // 10500..10527
                    UNKNOWN,  // 10528..1052F
                    CAUCASIAN_ALBANIAN,  // 10530..10563
                    UNKNOWN,  // 10564..1056E
                    CAUCASIAN_ALBANIAN,  // 1056F
                    VITHKUQI,  // 10570..1057A
                    UNKNOWN,  // 1057B
                    VITHKUQI,  // 1057C..1058A
                    UNKNOWN,  // 1058B
                    VITHKUQI,  // 1058C..10592
                    UNKNOWN,  // 10593
                    VITHKUQI,  // 10594..10595
                    UNKNOWN,  // 10596
                    VITHKUQI,  // 10597..105A1
                    UNKNOWN,  // 105A2
                    VITHKUQI,  // 105A3..105B1
                    UNKNOWN,  // 105B2
                    VITHKUQI,  // 105B3..105B9
                    UNKNOWN,  // 105BA
                    VITHKUQI,  // 105BB..105BC
                    UNKNOWN,  // 105BD..105BF
                    TODHRI,  // 105C0..105F3
                    UNKNOWN,  // 105F4..105FF
                    LINEAR_A,  // 10600..10736
                    UNKNOWN,  // 10737..1073F
                    LINEAR_A,  // 10740..10755
                    UNKNOWN,  // 10756..1075F
                    LINEAR_A,  // 10760..10767
                    UNKNOWN,  // 10768..1077F
                    LATIN,  // 10780..10785
                    UNKNOWN,  // 10786
                    LATIN,  // 10787..107B0
                    UNKNOWN,  // 107B1
                    LATIN,  // 107B2..107BA
                    UNKNOWN,  // 107BB..107FF
                    CYPRIOT,  // 10800..10805
                    UNKNOWN,  // 10806..10807
                    CYPRIOT,  // 10808
                    UNKNOWN,  // 10809
                    CYPRIOT,  // 1080A..10835
                    UNKNOWN,  // 10836
                    CYPRIOT,  // 10837..10838
                    UNKNOWN,  // 10839..1083B
                    CYPRIOT,  // 1083C
                    UNKNOWN,  // 1083D..1083E
                    CYPRIOT,  // 1083F
                    IMPERIAL_ARAMAIC,  // 10840..10855
                    UNKNOWN,  // 10856
                    IMPERIAL_ARAMAIC,  // 10857..1085F
                    PALMYRENE,  // 10860..1087F
                    NABATAEAN,  // 10880..1089E
                    UNKNOWN,  // 1089F..108A6
                    NABATAEAN,  // 108A7..108AF
                    UNKNOWN,  // 108B0..108DF
                    HATRAN,  // 108E0..108F2
                    UNKNOWN,  // 108F3
                    HATRAN,  // 108F4..108F5
                    UNKNOWN,  // 108F6..108FA
                    HATRAN,  // 108FB..108FF
                    PHOENICIAN,  // 10900..1091B
                    UNKNOWN,  // 1091C..1091E
                    PHOENICIAN,  // 1091F
                    LYDIAN,  // 10920..10939
                    UNKNOWN,  // 1093A..1093E
                    LYDIAN,  // 1093F
                    UNKNOWN,  // 10940..1097F
                    MEROITIC_HIEROGLYPHS,  // 10980..1099F
                    MEROITIC_CURSIVE,  // 109A0..109B7
                    UNKNOWN,  // 109B8..109BB
                    MEROITIC_CURSIVE,  // 109BC..109CF
                    UNKNOWN,  // 109D0..109D1
                    MEROITIC_CURSIVE,  // 109D2..109FF
                    KHAROSHTHI,  // 10A00..10A03
                    UNKNOWN,  // 10A04
                    KHAROSHTHI,  // 10A05..10A06
                    UNKNOWN,  // 10A07..10A0B
                    KHAROSHTHI,  // 10A0C..10A13
                    UNKNOWN,  // 10A14
                    KHAROSHTHI,  // 10A15..10A17
                    UNKNOWN,  // 10A18
                    KHAROSHTHI,  // 10A19..10A35
                    UNKNOWN,  // 10A36..10A37
                    KHAROSHTHI,  // 10A38..10A3A
                    UNKNOWN,  // 10A3B..10A3E
                    KHAROSHTHI,  // 10A3F..10A48
                    UNKNOWN,  // 10A49..10A4F
                    KHAROSHTHI,  // 10A50..10A58
                    UNKNOWN,  // 10A59..10A5F
                    OLD_SOUTH_ARABIAN,  // 10A60..10A7F
                    OLD_NORTH_ARABIAN,  // 10A80..10A9F
                    UNKNOWN,  // 10AA0..10ABF
                    MANICHAEAN,  // 10AC0..10AE6
                    UNKNOWN,  // 10AE7..10AEA
                    MANICHAEAN,  // 10AEB..10AF6
                    UNKNOWN,  // 10AF7..10AFF
                    AVESTAN,  // 10B00..10B35
                    UNKNOWN,  // 10B36..10B38
                    AVESTAN,  // 10B39..10B3F
                    INSCRIPTIONAL_PARTHIAN,  // 10B40..10B55
                    UNKNOWN,  // 10B56..10B57
                    INSCRIPTIONAL_PARTHIAN,  // 10B58..10B5F
                    INSCRIPTIONAL_PAHLAVI,  // 10B60..10B72
                    UNKNOWN,  // 10B73..10B77
                    INSCRIPTIONAL_PAHLAVI,  // 10B78..10B7F
                    PSALTER_PAHLAVI,  // 10B80..10B91
                    UNKNOWN,  // 10B92..10B98
                    PSALTER_PAHLAVI,  // 10B99..10B9C
                    UNKNOWN,  // 10B9D..10BA8
                    PSALTER_PAHLAVI,  // 10BA9..10BAF
                    UNKNOWN,  // 10BB0..10BFF
                    OLD_TURKIC,  // 10C00..10C48
                    UNKNOWN,  // 10C49..10C7F
                    OLD_HUNGARIAN,  // 10C80..10CB2
                    UNKNOWN,  // 10CB3..10CBF
                    OLD_HUNGARIAN,  // 10CC0..10CF2
                    UNKNOWN,  // 10CF3..10CF9
                    OLD_HUNGARIAN,  // 10CFA..10CFF
                    HANIFI_ROHINGYA,  // 10D00..10D27
                    UNKNOWN,  // 10D28..10D2F
                    HANIFI_ROHINGYA,  // 10D30..10D39
                    UNKNOWN,  // 10D3A..10D3F
                    GARAY,  // 10D40..10D65
                    UNKNOWN,  // 10D66..10D68
                    GARAY,  // 10D69..10D85
                    UNKNOWN,  // 10D86..10D8D
                    GARAY,  // 10D8E..10D8F
                    UNKNOWN,  // 10D90..10E5F
                    ARABIC,  // 10E60..10E7E
                    UNKNOWN,  // 10E7F
                    YEZIDI,  // 10E80..10EA9
                    UNKNOWN,  // 10EAA
                    YEZIDI,  // 10EAB..10EAD
                    UNKNOWN,  // 10EAE..10EAF
                    YEZIDI,  // 10EB0..10EB1
                    UNKNOWN,  // 10EB2..10EC1
                    ARABIC,  // 10EC2..10EC4
                    UNKNOWN,  // 10EC5..10EFB
                    ARABIC,  // 10EFC..10EFF
                    OLD_SOGDIAN,  // 10F00..10F27
                    UNKNOWN,  // 10F28..10F2F
                    SOGDIAN,  // 10F30..10F59
                    UNKNOWN,  // 10F5A..10F6F
                    OLD_UYGHUR,  // 10F70..10F89
                    UNKNOWN,  // 10F8A..10FAF
                    CHORASMIAN,  // 10FB0..10FCB
                    UNKNOWN,  // 10FCC..10FDF
                    ELYMAIC,  // 10FE0..10FF6
                    UNKNOWN,  // 10FF7..10FFF
                    BRAHMI,  // 11000..1104D
                    UNKNOWN,  // 1104E..11051
                    BRAHMI,  // 11052..11075
                    UNKNOWN,  // 11076..1107E
                    BRAHMI,  // 1107F
                    KAITHI,  // 11080..110C2
                    UNKNOWN,  // 110C3..110CC
                    KAITHI,  // 110CD
                    UNKNOWN,  // 110CE..110CF
                    SORA_SOMPENG,  // 110D0..110E8
                    UNKNOWN,  // 110E9..110EF
                    SORA_SOMPENG,  // 110F0..110F9
                    UNKNOWN,  // 110FA..110FF
                    CHAKMA,  // 11100..11134
                    UNKNOWN,  // 11135
                    CHAKMA,  // 11136..11147
                    UNKNOWN,  // 11148..1114F
                    MAHAJANI,  // 11150..11176
                    UNKNOWN,  // 11177..1117F
                    SHARADA,  // 11180..111DF
                    UNKNOWN,  // 111E0
                    SINHALA,  // 111E1..111F4
                    UNKNOWN,  // 111F5..111FF
                    KHOJKI,  // 11200..11211
                    UNKNOWN,  // 11212
                    KHOJKI,  // 11213..11241
                    UNKNOWN,  // 11242..1127F
                    MULTANI,  // 11280..11286
                    UNKNOWN,  // 11287
                    MULTANI,  // 11288
                    UNKNOWN,  // 11289
                    MULTANI,  // 1128A..1128D
                    UNKNOWN,  // 1128E
                    MULTANI,  // 1128F..1129D
                    UNKNOWN,  // 1129E
                    MULTANI,  // 1129F..112A9
                    UNKNOWN,  // 112AA..112AF
                    KHUDAWADI,  // 112B0..112EA
                    UNKNOWN,  // 112EB..112EF
                    KHUDAWADI,  // 112F0..112F9
                    UNKNOWN,  // 112FA..112FF
                    GRANTHA,  // 11300..11303
                    UNKNOWN,  // 11304
                    GRANTHA,  // 11305..1130C
                    UNKNOWN,  // 1130D..1130E
                    GRANTHA,  // 1130F..11310
                    UNKNOWN,  // 11311..11312
                    GRANTHA,  // 11313..11328
                    UNKNOWN,  // 11329
                    GRANTHA,  // 1132A..11330
                    UNKNOWN,  // 11331
                    GRANTHA,  // 11332..11333
                    UNKNOWN,  // 11334
                    GRANTHA,  // 11335..11339
                    UNKNOWN,  // 1133A
                    INHERITED,  // 1133B
                    GRANTHA,  // 1133C..11344
                    UNKNOWN,  // 11345..11346
                    GRANTHA,  // 11347..11348
                    UNKNOWN,  // 11349..1134A
                    GRANTHA,  // 1134B..1134D
                    UNKNOWN,  // 1134E..1134F
                    GRANTHA,  // 11350
                    UNKNOWN,  // 11351..11356
                    GRANTHA,  // 11357
                    UNKNOWN,  // 11358..1135C
                    GRANTHA,  // 1135D..11363
                    UNKNOWN,  // 11364..11365
                    GRANTHA,  // 11366..1136C
                    UNKNOWN,  // 1136D..1136F
                    GRANTHA,  // 11370..11374
                    UNKNOWN,  // 11375..1137F
                    TULU_TIGALARI,  // 11380..11389
                    UNKNOWN,  // 1138A
                    TULU_TIGALARI,  // 1138B
                    UNKNOWN,  // 1138C..1138D
                    TULU_TIGALARI,  // 1138E
                    UNKNOWN,  // 1138F
                    TULU_TIGALARI,  // 11390..113B5
                    UNKNOWN,  // 113B6
                    TULU_TIGALARI,  // 113B7..113C0
                    UNKNOWN,  // 113C1
                    TULU_TIGALARI,  // 113C2
                    UNKNOWN,  // 113C3..113C4
                    TULU_TIGALARI,  // 113C5
                    UNKNOWN,  // 113C6
                    TULU_TIGALARI,  // 113C7..113CA
                    UNKNOWN,  // 113CB
                    TULU_TIGALARI,  // 113CC..113D5
                    UNKNOWN,  // 113D6
                    TULU_TIGALARI,  // 113D7..113D8
                    UNKNOWN,  // 113D9..113E0
                    TULU_TIGALARI,  // 113E1..113E2
                    UNKNOWN,  // 113E3..113FF
                    NEWA,  // 11400..1145B
                    UNKNOWN,  // 1145C
                    NEWA,  // 1145D..11461
                    UNKNOWN,  // 11462..1147F
                    TIRHUTA,  // 11480..114C7
                    UNKNOWN,  // 114C8..114CF
                    TIRHUTA,  // 114D0..114D9
                    UNKNOWN,  // 114DA..1157F
                    SIDDHAM,  // 11580..115B5
                    UNKNOWN,  // 115B6..115B7
                    SIDDHAM,  // 115B8..115DD
                    UNKNOWN,  // 115DE..115FF
                    MODI,  // 11600..11644
                    UNKNOWN,  // 11645..1164F
                    MODI,  // 11650..11659
                    UNKNOWN,  // 1165A..1165F
                    MONGOLIAN,  // 11660..1166C
                    UNKNOWN,  // 1166D..1167F
                    TAKRI,  // 11680..116B9
                    UNKNOWN,  // 116BA..116BF
                    TAKRI,  // 116C0..116C9
                    UNKNOWN,  // 116CA..116CF
                    MYANMAR,  // 116D0..116E3
                    UNKNOWN,  // 116E4..116FF
                    AHOM,  // 11700..1171A
                    UNKNOWN,  // 1171B..1171C
                    AHOM,  // 1171D..1172B
                    UNKNOWN,  // 1172C..1172F
                    AHOM,  // 11730..11746
                    UNKNOWN,  // 11747..117FF
                    DOGRA,  // 11800..1183B
                    UNKNOWN,  // 1183C..1189F
                    WARANG_CITI,  // 118A0..118F2
                    UNKNOWN,  // 118F3..118FE
                    WARANG_CITI,  // 118FF
                    DIVES_AKURU,  // 11900..11906
                    UNKNOWN,  // 11907..11908
                    DIVES_AKURU,  // 11909
                    UNKNOWN,  // 1190A..1190B
                    DIVES_AKURU,  // 1190C..11913
                    UNKNOWN,  // 11914
                    DIVES_AKURU,  // 11915..11916
                    UNKNOWN,  // 11917
                    DIVES_AKURU,  // 11918..11935
                    UNKNOWN,  // 11936
                    DIVES_AKURU,  // 11937..11938
                    UNKNOWN,  // 11939..1193A
                    DIVES_AKURU,  // 1193B..11946
                    UNKNOWN,  // 11947..1194F
                    DIVES_AKURU,  // 11950..11959
                    UNKNOWN,  // 1195A..1199F
                    NANDINAGARI,  // 119A0..119A7
                    UNKNOWN,  // 119A8..119A9
                    NANDINAGARI,  // 119AA..119D7
                    UNKNOWN,  // 119D8..119D9
                    NANDINAGARI,  // 119DA..119E4
                    UNKNOWN,  // 119E5..119FF
                    ZANABAZAR_SQUARE,  // 11A00..11A47
                    UNKNOWN,  // 11A48..11A4F
                    SOYOMBO,  // 11A50..11AA2
                    UNKNOWN,  // 11AA3..11AAF
                    CANADIAN_ABORIGINAL,  // 11AB0..11ABF
                    PAU_CIN_HAU,  // 11AC0..11AF8
                    UNKNOWN,  // 11AF9..11AFF
                    DEVANAGARI,  // 11B00..11B09
                    UNKNOWN,  // 11B0A..11BBF
                    SUNUWAR,  // 11BC0..11BE1
                    UNKNOWN,  // 11BE2..11BEF
                    SUNUWAR,  // 11BF0..11BF9
                    UNKNOWN,  // 11BFA..11BFF
                    BHAIKSUKI,  // 11C00..11C08
                    UNKNOWN,  // 11C09
                    BHAIKSUKI,  // 11C0A..11C36
                    UNKNOWN,  // 11C37
                    BHAIKSUKI,  // 11C38..11C45
                    UNKNOWN,  // 11C46..11C4F
                    BHAIKSUKI,  // 11C50..11C6C
                    UNKNOWN,  // 11C6D..11C6F
                    MARCHEN,  // 11C70..11C8F
                    UNKNOWN,  // 11C90..11C91
                    MARCHEN,  // 11C92..11CA7
                    UNKNOWN,  // 11CA8
                    MARCHEN,  // 11CA9..11CB6
                    UNKNOWN,  // 11CB7..11CFF
                    MASARAM_GONDI,  // 11D00..11D06
                    UNKNOWN,  // 11D07
                    MASARAM_GONDI,  // 11D08..11D09
                    UNKNOWN,  // 11D0A
                    MASARAM_GONDI,  // 11D0B..11D36
                    UNKNOWN,  // 11D37..11D39
                    MASARAM_GONDI,  // 11D3A
                    UNKNOWN,  // 11D3B
                    MASARAM_GONDI,  // 11D3C..11D3D
                    UNKNOWN,  // 11D3E
                    MASARAM_GONDI,  // 11D3F..11D47
                    UNKNOWN,  // 11D48..11D4F
                    MASARAM_GONDI,  // 11D50..11D59
                    UNKNOWN,  // 11D5A..11D5F
                    GUNJALA_GONDI,  // 11D60..11D65
                    UNKNOWN,  // 11D66
                    GUNJALA_GONDI,  // 11D67..11D68
                    UNKNOWN,  // 11D69
                    GUNJALA_GONDI,  // 11D6A..11D8E
                    UNKNOWN,  // 11D8F
                    GUNJALA_GONDI,  // 11D90..11D91
                    UNKNOWN,  // 11D92
                    GUNJALA_GONDI,  // 11D93..11D98
                    UNKNOWN,  // 11D99..11D9F
                    GUNJALA_GONDI,  // 11DA0..11DA9
                    UNKNOWN,  // 11DAA..11EDF
                    MAKASAR,  // 11EE0..11EF8
                    UNKNOWN,  // 11EF9..11EFF
                    KAWI,  // 11F00..11F10
                    UNKNOWN,  // 11F11
                    KAWI,  // 11F12..11F3A
                    UNKNOWN,  // 11F3B..11F3D
                    KAWI,  // 11F3E..11F5A
                    UNKNOWN,  // 11F5B..11FAF
                    LISU,  // 11FB0
                    UNKNOWN,  // 11FB1..11FBF
                    TAMIL,  // 11FC0..11FF1
                    UNKNOWN,  // 11FF2..11FFE
                    TAMIL,  // 11FFF
                    CUNEIFORM,  // 12000..12399
                    UNKNOWN,  // 1239A..123FF
                    CUNEIFORM,  // 12400..1246E
                    UNKNOWN,  // 1246F
                    CUNEIFORM,  // 12470..12474
                    UNKNOWN,  // 12475..1247F
                    CUNEIFORM,  // 12480..12543
                    UNKNOWN,  // 12544..12F8F
                    CYPRO_MINOAN,  // 12F90..12FF2
                    UNKNOWN,  // 12FF3..12FFF
                    EGYPTIAN_HIEROGLYPHS,  // 13000..13455
                    UNKNOWN,  // 13456..1345F
                    EGYPTIAN_HIEROGLYPHS,  // 13460..143FA
                    UNKNOWN,  // 143FB..143FF
                    ANATOLIAN_HIEROGLYPHS,  // 14400..14646
                    UNKNOWN,  // 14647..160FF
                    GURUNG_KHEMA,  // 16100..16139
                    UNKNOWN,  // 1613A..167FF
                    BAMUM,  // 16800..16A38
                    UNKNOWN,  // 16A39..16A3F
                    MRO,  // 16A40..16A5E
                    UNKNOWN,  // 16A5F
                    MRO,  // 16A60..16A69
                    UNKNOWN,  // 16A6A..16A6D
                    MRO,  // 16A6E..16A6F
                    TANGSA,  // 16A70..16ABE
                    UNKNOWN,  // 16ABF
                    TANGSA,  // 16AC0..16AC9
                    UNKNOWN,  // 16ACA..16ACF
                    BASSA_VAH,  // 16AD0..16AED
                    UNKNOWN,  // 16AEE..16AEF
                    BASSA_VAH,  // 16AF0..16AF5
                    UNKNOWN,  // 16AF6..16AFF
                    PAHAWH_HMONG,  // 16B00..16B45
                    UNKNOWN,  // 16B46..16B4F
                    PAHAWH_HMONG,  // 16B50..16B59
                    UNKNOWN,  // 16B5A
                    PAHAWH_HMONG,  // 16B5B..16B61
                    UNKNOWN,  // 16B62
                    PAHAWH_HMONG,  // 16B63..16B77
                    UNKNOWN,  // 16B78..16B7C
                    PAHAWH_HMONG,  // 16B7D..16B8F
                    UNKNOWN,  // 16B90..16D3F
                    KIRAT_RAI,  // 16D40..16D79
                    UNKNOWN,  // 16D7A..16E3F
                    MEDEFAIDRIN,  // 16E40..16E9A
                    UNKNOWN,  // 16E9B..16EFF
                    MIAO,  // 16F00..16F4A
                    UNKNOWN,  // 16F4B..16F4E
                    MIAO,  // 16F4F..16F87
                    UNKNOWN,  // 16F88..16F8E
                    MIAO,  // 16F8F..16F9F
                    UNKNOWN,  // 16FA0..16FDF
                    TANGUT,  // 16FE0
                    NUSHU,  // 16FE1
                    HAN,  // 16FE2..16FE3
                    KHITAN_SMALL_SCRIPT,  // 16FE4
                    UNKNOWN,  // 16FE5..16FEF
                    HAN,  // 16FF0..16FF1
                    UNKNOWN,  // 16FF2..16FFF
                    TANGUT,  // 17000..187F7
                    UNKNOWN,  // 187F8..187FF
                    TANGUT,  // 18800..18AFF
                    KHITAN_SMALL_SCRIPT,  // 18B00..18CD5
                    UNKNOWN,  // 18CD6..18CFE
                    KHITAN_SMALL_SCRIPT,  // 18CFF
                    TANGUT,  // 18D00..18D08
                    UNKNOWN,  // 18D09..1AFEF
                    KATAKANA,  // 1AFF0..1AFF3
                    UNKNOWN,  // 1AFF4
                    KATAKANA,  // 1AFF5..1AFFB
                    UNKNOWN,  // 1AFFC
                    KATAKANA,  // 1AFFD..1AFFE
                    UNKNOWN,  // 1AFFF
                    KATAKANA,  // 1B000
                    HIRAGANA,  // 1B001..1B11F
                    KATAKANA,  // 1B120..1B122
                    UNKNOWN,  // 1B123..1B131
                    HIRAGANA,  // 1B132
                    UNKNOWN,  // 1B133..1B14F
                    HIRAGANA,  // 1B150..1B152
                    UNKNOWN,  // 1B153..1B154
                    KATAKANA,  // 1B155
                    UNKNOWN,  // 1B156..1B163
                    KATAKANA,  // 1B164..1B167
                    UNKNOWN,  // 1B168..1B16F
                    NUSHU,  // 1B170..1B2FB
                    UNKNOWN,  // 1B2FC..1BBFF
                    DUPLOYAN,  // 1BC00..1BC6A
                    UNKNOWN,  // 1BC6B..1BC6F
                    DUPLOYAN,  // 1BC70..1BC7C
                    UNKNOWN,  // 1BC7D..1BC7F
                    DUPLOYAN,  // 1BC80..1BC88
                    UNKNOWN,  // 1BC89..1BC8F
                    DUPLOYAN,  // 1BC90..1BC99
                    UNKNOWN,  // 1BC9A..1BC9B
                    DUPLOYAN,  // 1BC9C..1BC9F
                    COMMON,  // 1BCA0..1BCA3
                    UNKNOWN,  // 1BCA4..1CBFF
                    COMMON,  // 1CC00..1CCF9
                    UNKNOWN,  // 1CCFA..1CCFF
                    COMMON,  // 1CD00..1CEB3
                    UNKNOWN,  // 1CEB4..1CEFF
                    INHERITED,  // 1CF00..1CF2D
                    UNKNOWN,  // 1CF2E..1CF2F
                    INHERITED,  // 1CF30..1CF46
                    UNKNOWN,  // 1CF47..1CF4F
                    COMMON,  // 1CF50..1CFC3
                    UNKNOWN,  // 1CFC4..1CFFF
                    COMMON,  // 1D000..1D0F5
                    UNKNOWN,  // 1D0F6..1D0FF
                    COMMON,  // 1D100..1D126
                    UNKNOWN,  // 1D127..1D128
                    COMMON,  // 1D129..1D166
                    INHERITED,  // 1D167..1D169
                    COMMON,  // 1D16A..1D17A
                    INHERITED,  // 1D17B..1D182
                    COMMON,  // 1D183..1D184
                    INHERITED,  // 1D185..1D18B
                    COMMON,  // 1D18C..1D1A9
                    INHERITED,  // 1D1AA..1D1AD
                    COMMON,  // 1D1AE..1D1EA
                    UNKNOWN,  // 1D1EB..1D1FF
                    GREEK,  // 1D200..1D245
                    UNKNOWN,  // 1D246..1D2BF
                    COMMON,  // 1D2C0..1D2D3
                    UNKNOWN,  // 1D2D4..1D2DF
                    COMMON,  // 1D2E0..1D2F3
                    UNKNOWN,  // 1D2F4..1D2FF
                    COMMON,  // 1D300..1D356
                    UNKNOWN,  // 1D357..1D35F
                    COMMON,  // 1D360..1D378
                    UNKNOWN,  // 1D379..1D3FF
                    COMMON,  // 1D400..1D454
                    UNKNOWN,  // 1D455
                    COMMON,  // 1D456..1D49C
                    UNKNOWN,  // 1D49D
                    COMMON,  // 1D49E..1D49F
                    UNKNOWN,  // 1D4A0..1D4A1
                    COMMON,  // 1D4A2
                    UNKNOWN,  // 1D4A3..1D4A4
                    COMMON,  // 1D4A5..1D4A6
                    UNKNOWN,  // 1D4A7..1D4A8
                    COMMON,  // 1D4A9..1D4AC
                    UNKNOWN,  // 1D4AD
                    COMMON,  // 1D4AE..1D4B9
                    UNKNOWN,  // 1D4BA
                    COMMON,  // 1D4BB
                    UNKNOWN,  // 1D4BC
                    COMMON,  // 1D4BD..1D4C3
                    UNKNOWN,  // 1D4C4
                    COMMON,  // 1D4C5..1D505
                    UNKNOWN,  // 1D506
                    COMMON,  // 1D507..1D50A
                    UNKNOWN,  // 1D50B..1D50C
                    COMMON,  // 1D50D..1D514
                    UNKNOWN,  // 1D515
                    COMMON,  // 1D516..1D51C
                    UNKNOWN,  // 1D51D
                    COMMON,  // 1D51E..1D539
                    UNKNOWN,  // 1D53A
                    COMMON,  // 1D53B..1D53E
                    UNKNOWN,  // 1D53F
                    COMMON,  // 1D540..1D544
                    UNKNOWN,  // 1D545
                    COMMON,  // 1D546
                    UNKNOWN,  // 1D547..1D549
                    COMMON,  // 1D54A..1D550
                    UNKNOWN,  // 1D551
                    COMMON,  // 1D552..1D6A5
                    UNKNOWN,  // 1D6A6..1D6A7
                    COMMON,  // 1D6A8..1D7CB
                    UNKNOWN,  // 1D7CC..1D7CD
                    COMMON,  // 1D7CE..1D7FF
                    SIGNWRITING,  // 1D800..1DA8B
                    UNKNOWN,  // 1DA8C..1DA9A
                    SIGNWRITING,  // 1DA9B..1DA9F
                    UNKNOWN,  // 1DAA0
                    SIGNWRITING,  // 1DAA1..1DAAF
                    UNKNOWN,  // 1DAB0..1DEFF
                    LATIN,  // 1DF00..1DF1E
                    UNKNOWN,  // 1DF1F..1DF24
                    LATIN,  // 1DF25..1DF2A
                    UNKNOWN,  // 1DF2B..1DFFF
                    GLAGOLITIC,  // 1E000..1E006
                    UNKNOWN,  // 1E007
                    GLAGOLITIC,  // 1E008..1E018
                    UNKNOWN,  // 1E019..1E01A
                    GLAGOLITIC,  // 1E01B..1E021
                    UNKNOWN,  // 1E022
                    GLAGOLITIC,  // 1E023..1E024
                    UNKNOWN,  // 1E025
                    GLAGOLITIC,  // 1E026..1E02A
                    UNKNOWN,  // 1E02B..1E02F
                    CYRILLIC,  // 1E030..1E06D
                    UNKNOWN,  // 1E06E..1E08E
                    CYRILLIC,  // 1E08F
                    UNKNOWN,  // 1E090..1E0FF
                    NYIAKENG_PUACHUE_HMONG,  // 1E100..1E12C
                    UNKNOWN,  // 1E12D..1E12F
                    NYIAKENG_PUACHUE_HMONG,  // 1E130..1E13D
                    UNKNOWN,  // 1E13E..1E13F
                    NYIAKENG_PUACHUE_HMONG,  // 1E140..1E149
                    UNKNOWN,  // 1E14A..1E14D
                    NYIAKENG_PUACHUE_HMONG,  // 1E14E..1E14F
                    UNKNOWN,  // 1E150..1E28F
                    TOTO,  // 1E290..1E2AE
                    UNKNOWN,  // 1E2AF..1E2BF
                    WANCHO,  // 1E2C0..1E2F9
                    UNKNOWN,  // 1E2FA..1E2FE
                    WANCHO,  // 1E2FF
                    UNKNOWN,  // 1E300..1E4CF
                    NAG_MUNDARI,  // 1E4D0..1E4F9
                    UNKNOWN,  // 1E4FA..1E5CF
                    OL_ONAL,  // 1E5D0..1E5FA
                    UNKNOWN,  // 1E5FB..1E5FE
                    OL_ONAL,  // 1E5FF
                    UNKNOWN,  // 1E600..1E7DF
                    ETHIOPIC,  // 1E7E0..1E7E6
                    UNKNOWN,  // 1E7E7
                    ETHIOPIC,  // 1E7E8..1E7EB
                    UNKNOWN,  // 1E7EC
                    ETHIOPIC,  // 1E7ED..1E7EE
                    UNKNOWN,  // 1E7EF
                    ETHIOPIC,  // 1E7F0..1E7FE
                    UNKNOWN,  // 1E7FF
                    MENDE_KIKAKUI,  // 1E800..1E8C4
                    UNKNOWN,  // 1E8C5..1E8C6
                    MENDE_KIKAKUI,  // 1E8C7..1E8D6
                    UNKNOWN,  // 1E8D7..1E8FF
                    ADLAM,  // 1E900..1E94B
                    UNKNOWN,  // 1E94C..1E94F
                    ADLAM,  // 1E950..1E959
                    UNKNOWN,  // 1E95A..1E95D
                    ADLAM,  // 1E95E..1E95F
                    UNKNOWN,  // 1E960..1EC70
                    COMMON,  // 1EC71..1ECB4
                    UNKNOWN,  // 1ECB5..1ED00
                    COMMON,  // 1ED01..1ED3D
                    UNKNOWN,  // 1ED3E..1EDFF
                    ARABIC,  // 1EE00..1EE03
                    UNKNOWN,  // 1EE04
                    ARABIC,  // 1EE05..1EE1F
                    UNKNOWN,  // 1EE20
                    ARABIC,  // 1EE21..1EE22
                    UNKNOWN,  // 1EE23
                    ARABIC,  // 1EE24
                    UNKNOWN,  // 1EE25..1EE26
                    ARABIC,  // 1EE27
                    UNKNOWN,  // 1EE28
                    ARABIC,  // 1EE29..1EE32
                    UNKNOWN,  // 1EE33
                    ARABIC,  // 1EE34..1EE37
                    UNKNOWN,  // 1EE38
                    ARABIC,  // 1EE39
                    UNKNOWN,  // 1EE3A
                    ARABIC,  // 1EE3B
                    UNKNOWN,  // 1EE3C..1EE41
                    ARABIC,  // 1EE42
                    UNKNOWN,  // 1EE43..1EE46
                    ARABIC,  // 1EE47
                    UNKNOWN,  // 1EE48
                    ARABIC,  // 1EE49
                    UNKNOWN,  // 1EE4A
                    ARABIC,  // 1EE4B
                    UNKNOWN,  // 1EE4C
                    ARABIC,  // 1EE4D..1EE4F
                    UNKNOWN,  // 1EE50
                    ARABIC,  // 1EE51..1EE52
                    UNKNOWN,  // 1EE53
                    ARABIC,  // 1EE54
                    UNKNOWN,  // 1EE55..1EE56
                    ARABIC,  // 1EE57
                    UNKNOWN,  // 1EE58
                    ARABIC,  // 1EE59
                    UNKNOWN,  // 1EE5A
                    ARABIC,  // 1EE5B
                    UNKNOWN,  // 1EE5C
                    ARABIC,  // 1EE5D
                    UNKNOWN,  // 1EE5E
                    ARABIC,  // 1EE5F
                    UNKNOWN,  // 1EE60
                    ARABIC,  // 1EE61..1EE62
                    UNKNOWN,  // 1EE63
                    ARABIC,  // 1EE64
                    UNKNOWN,  // 1EE65..1EE66
                    ARABIC,  // 1EE67..1EE6A
                    UNKNOWN,  // 1EE6B
                    ARABIC,  // 1EE6C..1EE72
                    UNKNOWN,  // 1EE73
                    ARABIC,  // 1EE74..1EE77
                    UNKNOWN,  // 1EE78
                    ARABIC,  // 1EE79..1EE7C
                    UNKNOWN,  // 1EE7D
                    ARABIC,  // 1EE7E
                    UNKNOWN,  // 1EE7F
                    ARABIC,  // 1EE80..1EE89
                    UNKNOWN,  // 1EE8A
                    ARABIC,  // 1EE8B..1EE9B
                    UNKNOWN,  // 1EE9C..1EEA0
                    ARABIC,  // 1EEA1..1EEA3
                    UNKNOWN,  // 1EEA4
                    ARABIC,  // 1EEA5..1EEA9
                    UNKNOWN,  // 1EEAA
                    ARABIC,  // 1EEAB..1EEBB
                    UNKNOWN,  // 1EEBC..1EEEF
                    ARABIC,  // 1EEF0..1EEF1
                    UNKNOWN,  // 1EEF2..1EFFF
                    COMMON,  // 1F000..1F02B
                    UNKNOWN,  // 1F02C..1F02F
                    COMMON,  // 1F030..1F093
                    UNKNOWN,  // 1F094..1F09F
                    COMMON,  // 1F0A0..1F0AE
                    UNKNOWN,  // 1F0AF..1F0B0
                    COMMON,  // 1F0B1..1F0BF
                    UNKNOWN,  // 1F0C0
                    COMMON,  // 1F0C1..1F0CF
                    UNKNOWN,  // 1F0D0
                    COMMON,  // 1F0D1..1F0F5
                    UNKNOWN,  // 1F0F6..1F0FF
                    COMMON,  // 1F100..1F1AD
                    UNKNOWN,  // 1F1AE..1F1E5
                    COMMON,  // 1F1E6..1F1FF
                    HIRAGANA,  // 1F200
                    COMMON,  // 1F201..1F202
                    UNKNOWN,  // 1F203..1F20F
                    COMMON,  // 1F210..1F23B
                    UNKNOWN,  // 1F23C..1F23F
                    COMMON,  // 1F240..1F248
                    UNKNOWN,  // 1F249..1F24F
                    COMMON,  // 1F250..1F251
                    UNKNOWN,  // 1F252..1F25F
                    COMMON,  // 1F260..1F265
                    UNKNOWN,  // 1F266..1F2FF
                    COMMON,  // 1F300..1F6D7
                    UNKNOWN,  // 1F6D8..1F6DB
                    COMMON,  // 1F6DC..1F6EC
                    UNKNOWN,  // 1F6ED..1F6EF
                    COMMON,  // 1F6F0..1F6FC
                    UNKNOWN,  // 1F6FD..1F6FF
                    COMMON,  // 1F700..1F776
                    UNKNOWN,  // 1F777..1F77A
                    COMMON,  // 1F77B..1F7D9
                    UNKNOWN,  // 1F7DA..1F7DF
                    COMMON,  // 1F7E0..1F7EB
                    UNKNOWN,  // 1F7EC..1F7EF
                    COMMON,  // 1F7F0
                    UNKNOWN,  // 1F7F1..1F7FF
                    COMMON,  // 1F800..1F80B
                    UNKNOWN,  // 1F80C..1F80F
                    COMMON,  // 1F810..1F847
                    UNKNOWN,  // 1F848..1F84F
                    COMMON,  // 1F850..1F859
                    UNKNOWN,  // 1F85A..1F85F
                    COMMON,  // 1F860..1F887
                    UNKNOWN,  // 1F888..1F88F
                    COMMON,  // 1F890..1F8AD
                    UNKNOWN,  // 1F8AE..1F8AF
                    COMMON,  // 1F8B0..1F8BB
                    UNKNOWN,  // 1F8BC..1F8BF
                    COMMON,  // 1F8C0..1F8C1
                    UNKNOWN,  // 1F8C2..1F8FF
                    COMMON,  // 1F900..1FA53
                    UNKNOWN,  // 1FA54..1FA5F
                    COMMON,  // 1FA60..1FA6D
                    UNKNOWN,  // 1FA6E..1FA6F
                    COMMON,  // 1FA70..1FA7C
                    UNKNOWN,  // 1FA7D..1FA7F
                    COMMON,  // 1FA80..1FA89
                    UNKNOWN,  // 1FA8A..1FA8E
                    COMMON,  // 1FA8F..1FAC6
                    UNKNOWN,  // 1FAC7..1FACD
                    COMMON,  // 1FACE..1FADC
                    UNKNOWN,  // 1FADD..1FADE
                    COMMON,  // 1FADF..1FAE9
                    UNKNOWN,  // 1FAEA..1FAEF
                    COMMON,  // 1FAF0..1FAF8
                    UNKNOWN,  // 1FAF9..1FAFF
                    COMMON,  // 1FB00..1FB92
                    UNKNOWN,  // 1FB93
                    COMMON,  // 1FB94..1FBF9
                    UNKNOWN,  // 1FBFA..1FFFF
                    HAN,  // 20000..2A6DF
                    UNKNOWN,  // 2A6E0..2A6FF
                    HAN,  // 2A700..2B739
                    UNKNOWN,  // 2B73A..2B73F
                    HAN,  // 2B740..2B81D
                    UNKNOWN,  // 2B81E..2B81F
                    HAN,  // 2B820..2CEA1
                    UNKNOWN,  // 2CEA2..2CEAF
                    HAN,  // 2CEB0..2EBE0
                    UNKNOWN,  // 2EBE1..2EBEF
                    HAN,  // 2EBF0..2EE5D
                    UNKNOWN,  // 2EE5E..2F7FF
                    HAN,  // 2F800..2FA1D
                    UNKNOWN,  // 2FA1E..2FFFF
                    HAN,  // 30000..3134A
                    UNKNOWN,  // 3134B..3134F
                    HAN,  // 31350..323AF
                    UNKNOWN,  // 323B0..E0000
                    COMMON,  // E0001
                    UNKNOWN,  // E0002..E001F
                    COMMON,  // E0020..E007F
                    UNKNOWN,  // E0080..E00FF
                    INHERITED,  // E0100..E01EF
                    UNKNOWN,  // E01F0..10FFFF
                )

                private val aliases: HashMap<String, UnicodeScript> = HashMap<String, UnicodeScript>(
                    UNKNOWN.ordinal + 1
                )

                init {
                    aliases["ADLM"] = ADLAM
                    aliases["AGHB"] = CAUCASIAN_ALBANIAN
                    aliases["AHOM"] = AHOM
                    aliases["ARAB"] = ARABIC
                    aliases["ARMI"] = IMPERIAL_ARAMAIC
                    aliases["ARMN"] = ARMENIAN
                    aliases["AVST"] = AVESTAN
                    aliases["BALI"] = BALINESE
                    aliases["BAMU"] = BAMUM
                    aliases["BASS"] = BASSA_VAH
                    aliases["BATK"] = BATAK
                    aliases["BENG"] = BENGALI
                    aliases["BHKS"] = BHAIKSUKI
                    aliases["BOPO"] = BOPOMOFO
                    aliases["BRAH"] = BRAHMI
                    aliases["BRAI"] = BRAILLE
                    aliases["BUGI"] = BUGINESE
                    aliases["BUHD"] = BUHID
                    aliases["CAKM"] = CHAKMA
                    aliases["CANS"] = CANADIAN_ABORIGINAL
                    aliases["CARI"] = CARIAN
                    aliases["CHAM"] = CHAM
                    aliases["CHER"] = CHEROKEE
                    aliases["CHRS"] = CHORASMIAN
                    aliases["COPT"] = COPTIC
                    aliases["CPMN"] = CYPRO_MINOAN
                    aliases["CPRT"] = CYPRIOT
                    aliases["CYRL"] = CYRILLIC
                    aliases["DEVA"] = DEVANAGARI
                    aliases["DIAK"] = DIVES_AKURU
                    aliases["DOGR"] = DOGRA
                    aliases["DSRT"] = DESERET
                    aliases["DUPL"] = DUPLOYAN
                    aliases["EGYP"] = EGYPTIAN_HIEROGLYPHS
                    aliases["ELBA"] = ELBASAN
                    aliases["ELYM"] = ELYMAIC
                    aliases["ETHI"] = ETHIOPIC
                    aliases["GARA"] = GARAY
                    aliases["GEOR"] = GEORGIAN
                    aliases["GLAG"] = GLAGOLITIC
                    aliases["GONG"] = GUNJALA_GONDI
                    aliases["GONM"] = MASARAM_GONDI
                    aliases["GOTH"] = GOTHIC
                    aliases["GRAN"] = GRANTHA
                    aliases["GREK"] = GREEK
                    aliases["GUJR"] = GUJARATI
                    aliases["GUKH"] = GURUNG_KHEMA
                    aliases["GURU"] = GURMUKHI
                    aliases["HANG"] = HANGUL
                    aliases["HANI"] = HAN
                    aliases["HANO"] = HANUNOO
                    aliases["HATR"] = HATRAN
                    aliases["HEBR"] = HEBREW
                    aliases["HIRA"] = HIRAGANA
                    aliases["HLUW"] = ANATOLIAN_HIEROGLYPHS
                    aliases["HMNG"] = PAHAWH_HMONG
                    aliases["HMNP"] = NYIAKENG_PUACHUE_HMONG
                    aliases["HUNG"] = OLD_HUNGARIAN
                    aliases["ITAL"] = OLD_ITALIC
                    aliases["JAVA"] = JAVANESE
                    aliases["KALI"] = KAYAH_LI
                    aliases["KANA"] = KATAKANA
                    aliases["KAWI"] = KAWI
                    aliases["KHAR"] = KHAROSHTHI
                    aliases["KHMR"] = KHMER
                    aliases["KHOJ"] = KHOJKI
                    aliases["KITS"] = KHITAN_SMALL_SCRIPT
                    aliases["KNDA"] = KANNADA
                    aliases["KRAI"] = KIRAT_RAI
                    aliases["KTHI"] = KAITHI
                    aliases["LANA"] = TAI_THAM
                    aliases["LAOO"] = LAO
                    aliases["LATN"] = LATIN
                    aliases["LEPC"] = LEPCHA
                    aliases["LIMB"] = LIMBU
                    aliases["LINA"] = LINEAR_A
                    aliases["LINB"] = LINEAR_B
                    aliases["LISU"] = LISU
                    aliases["LYCI"] = LYCIAN
                    aliases["LYDI"] = LYDIAN
                    aliases["MAHJ"] = MAHAJANI
                    aliases["MAKA"] = MAKASAR
                    aliases["MAND"] = MANDAIC
                    aliases["MANI"] = MANICHAEAN
                    aliases["MARC"] = MARCHEN
                    aliases["MEDF"] = MEDEFAIDRIN
                    aliases["MEND"] = MENDE_KIKAKUI
                    aliases["MERC"] = MEROITIC_CURSIVE
                    aliases["MERO"] = MEROITIC_HIEROGLYPHS
                    aliases["MLYM"] = MALAYALAM
                    aliases["MODI"] = MODI
                    aliases["MONG"] = MONGOLIAN
                    aliases["MROO"] = MRO
                    aliases["MTEI"] = MEETEI_MAYEK
                    aliases["MULT"] = MULTANI
                    aliases["MYMR"] = MYANMAR
                    aliases["NAGM"] = NAG_MUNDARI
                    aliases["NAND"] = NANDINAGARI
                    aliases["NARB"] = OLD_NORTH_ARABIAN
                    aliases["NBAT"] = NABATAEAN
                    aliases["NEWA"] = NEWA
                    aliases["NKOO"] = NKO
                    aliases["NSHU"] = NUSHU
                    aliases["OGAM"] = OGHAM
                    aliases["OLCK"] = OL_CHIKI
                    aliases["ONAO"] = OL_ONAL
                    aliases["ORKH"] = OLD_TURKIC
                    aliases["ORYA"] = ORIYA
                    aliases["OSGE"] = OSAGE
                    aliases["OSMA"] = OSMANYA
                    aliases["OUGR"] = OLD_UYGHUR
                    aliases["PALM"] = PALMYRENE
                    aliases["PAUC"] = PAU_CIN_HAU
                    aliases["PERM"] = OLD_PERMIC
                    aliases["PHAG"] = PHAGS_PA
                    aliases["PHLI"] = INSCRIPTIONAL_PAHLAVI
                    aliases["PHLP"] = PSALTER_PAHLAVI
                    aliases["PHNX"] = PHOENICIAN
                    aliases["PLRD"] = MIAO
                    aliases["PRTI"] = INSCRIPTIONAL_PARTHIAN
                    aliases["RJNG"] = REJANG
                    aliases["ROHG"] = HANIFI_ROHINGYA
                    aliases["RUNR"] = RUNIC
                    aliases["SAMR"] = SAMARITAN
                    aliases["SARB"] = OLD_SOUTH_ARABIAN
                    aliases["SAUR"] = SAURASHTRA
                    aliases["SGNW"] = SIGNWRITING
                    aliases["SHAW"] = SHAVIAN
                    aliases["SHRD"] = SHARADA
                    aliases["SIDD"] = SIDDHAM
                    aliases["SIND"] = KHUDAWADI
                    aliases["SINH"] = SINHALA
                    aliases["SOGD"] = SOGDIAN
                    aliases["SOGO"] = OLD_SOGDIAN
                    aliases["SORA"] = SORA_SOMPENG
                    aliases["SOYO"] = SOYOMBO
                    aliases["SUND"] = SUNDANESE
                    aliases["SUNU"] = SUNUWAR
                    aliases["SYLO"] = SYLOTI_NAGRI
                    aliases["SYRC"] = SYRIAC
                    aliases["TAGB"] = TAGBANWA
                    aliases["TAKR"] = TAKRI
                    aliases["TALE"] = TAI_LE
                    aliases["TALU"] = NEW_TAI_LUE
                    aliases["TAML"] = TAMIL
                    aliases["TANG"] = TANGUT
                    aliases["TAVT"] = TAI_VIET
                    aliases["TELU"] = TELUGU
                    aliases["TFNG"] = TIFINAGH
                    aliases["TGLG"] = TAGALOG
                    aliases["THAA"] = THAANA
                    aliases["THAI"] = THAI
                    aliases["TIBT"] = TIBETAN
                    aliases["TIRH"] = TIRHUTA
                    aliases["TNSA"] = TANGSA
                    aliases["TODR"] = TODHRI
                    aliases["TOTO"] = TOTO
                    aliases["TUTG"] = TULU_TIGALARI
                    aliases["UGAR"] = UGARITIC
                    aliases["VAII"] = VAI
                    aliases["VITH"] = VITHKUQI
                    aliases["WARA"] = WARANG_CITI
                    aliases["WCHO"] = WANCHO
                    aliases["XPEO"] = OLD_PERSIAN
                    aliases["XSUX"] = CUNEIFORM
                    aliases["YEZI"] = YEZIDI
                    aliases["YIII"] = YI
                    aliases["ZANB"] = ZANABAZAR_SQUARE
                    aliases["ZINH"] = INHERITED
                    aliases["ZYYY"] = COMMON
                    aliases["ZZZZ"] = UNKNOWN
                }

                /**
                 * Returns the enum constant representing the Unicode script of which
                 * the given character (Unicode code point) is assigned to.
                 *
                 * @param   codePoint the character (Unicode code point) in question.
                 * @return  The `UnicodeScript` constant representing the
                 * Unicode script of which this character is assigned to.
                 *
                 * @throws  IllegalArgumentException if the specified
                 * `codePoint` is an invalid Unicode code point.
                 * @see Character.isValidCodePoint
                 */
                fun of(codePoint: Int): UnicodeScript? {
                    require(isValidCodePoint(codePoint)) {
                        "Not a valid Unicode code point: $codePoint"
                    }
                    val type: Int = getType(codePoint)
                    // leave SURROGATE and PRIVATE_USE for table lookup
                    if (type == UNASSIGNED.toInt()) return UNKNOWN
                    var index: Int = Arrays.binarySearch(scriptStarts, codePoint)
                    if (index < 0) index = -index - 2
                    return scripts[index]
                }

                /**
                 * Returns the UnicodeScript constant with the given Unicode script
                 * name or the script name alias. Script names and their aliases are
                 * determined by The Unicode Standard. The files `Scripts.txt`
                 * and `PropertyValueAliases.txt` define script names
                 * and the script name aliases for a particular version of the
                 * standard. The [Character] class specifies the version of
                 * the standard that it supports.
                 *
                 *
                 * Character case is ignored for all of the valid script names.
                 * The en_US locale's case mapping rules are used to provide
                 * case-insensitive string comparisons for script name validation.
                 *
                 * @param scriptName A `UnicodeScript` name.
                 * @return The `UnicodeScript` constant identified
                 * by `scriptName`
                 * @throws IllegalArgumentException if `scriptName` is an
                 * invalid name
                 * @throws NullPointerException if `scriptName` is null
                 */
                fun forName(scriptName: String): UnicodeScript {
                    var scriptName = scriptName
                    scriptName = scriptName.uppercase()
                    //.replace(' ', '_'));
                    val sc: UnicodeScript? = aliases[scriptName]
                    if (sc != null) return sc
                    return UnicodeScript.valueOf(scriptName)
                }
            }
        }


        // end of companion object
    }
}
