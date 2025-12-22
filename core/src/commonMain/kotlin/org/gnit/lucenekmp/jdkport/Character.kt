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
        const val DIRECTIONALITY_UNDEFINED: Byte = -1;

        /**
         * Strong bidirectional character type "L" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_LEFT_TO_RIGHT: Byte = 0;

        /**
         * Strong bidirectional character type "R" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT: Byte = 1;

        /**
         * Strong bidirectional character type "AL" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC: Byte = 2;

        /**
         * Weak bidirectional character type "EN" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_EUROPEAN_NUMBER: Byte = 3;

        /**
         * Weak bidirectional character type "ES" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR: Byte = 4;

        /**
         * Weak bidirectional character type "ET" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR: Byte = 5;

        /**
         * Weak bidirectional character type "AN" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_ARABIC_NUMBER: Byte = 6;

        /**
         * Weak bidirectional character type "CS" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_COMMON_NUMBER_SEPARATOR: Byte = 7;

        /**
         * Weak bidirectional character type "NSM" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_NONSPACING_MARK: Byte = 8;

        /**
         * Weak bidirectional character type "BN" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_BOUNDARY_NEUTRAL: Byte = 9;

        /**
         * Neutral bidirectional character type "B" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_PARAGRAPH_SEPARATOR: Byte = 10;

        /**
         * Neutral bidirectional character type "S" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_SEGMENT_SEPARATOR: Byte = 11;

        /**
         * Neutral bidirectional character type "WS" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_WHITESPACE: Byte = 12;

        /**
         * Neutral bidirectional character type "ON" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_OTHER_NEUTRALS: Byte = 13;

        /**
         * Strong bidirectional character type "LRE" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING: Byte = 14;

        /**
         * Strong bidirectional character type "LRO" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE: Byte = 15;

        /**
         * Strong bidirectional character type "RLE" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING: Byte = 16;

        /**
         * Strong bidirectional character type "RLO" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE: Byte = 17;

        /**
         * Weak bidirectional character type "PDF" in the Unicode specification.
         * @since 1.4
         */
        const val DIRECTIONALITY_POP_DIRECTIONAL_FORMAT: Byte = 18;

        /**
         * Weak bidirectional character type "LRI" in the Unicode specification.
         * @since 9
         */
        const val DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE: Byte = 19;

        /**
         * Weak bidirectional character type "RLI" in the Unicode specification.
         * @since 9
         */
        const val DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE: Byte = 20;

        /**
         * Weak bidirectional character type "FSI" in the Unicode specification.
         * @since 9
         */
        const val DIRECTIONALITY_FIRST_STRONG_ISOLATE: Byte = 21;

        /**
         * Weak bidirectional character type "PDI" in the Unicode specification.
         * @since 9
         */
        const val DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE: Byte = 22;


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
            if (index >= limit || index < 0 || limit > a.size) {
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

        // end of companion object
    }
}
