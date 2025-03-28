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

        fun charCount(codePoint: Int): Int {
            return if (codePoint >= MIN_SUPPLEMENTARY_CODE_POINT) 2 else 1
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
