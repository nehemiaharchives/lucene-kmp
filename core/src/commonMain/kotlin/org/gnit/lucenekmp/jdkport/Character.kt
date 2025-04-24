package org.gnit.lucenekmp.jdkport

class Character {

    companion object {

        const val MIN_RADIX: Int = 2
        const val MAX_RADIX: Int = 36
        const val MIN_CODE_POINT: Int = 0x000000
        const val MAX_CODE_POINT: Int = 0X10FFFF
        const val MIN_SUPPLEMENTARY_CODE_POINT: Int = 0x010000
        const val UNASSIGNED: Byte = 0
        const val ERROR: Int = -0x1
        const val DIRECTIONALITY_UNDEFINED: Byte = -1
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

        fun charCount(codePoint: Int): Int {
            return if (codePoint >= MIN_SUPPLEMENTARY_CODE_POINT) 2 else 1
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

    }
}
