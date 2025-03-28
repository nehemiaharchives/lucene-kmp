package org.gnit.lucenekmp.jdkport


/** The CharacterData class encapsulates the large tables found in
 * Java.lang.Character.  */
internal class CharacterDataLatin1 private constructor() // The A table has 256 entries for a total of 1024 bytes.
    : CharacterData() {
    /* The character properties are currently encoded into 32 bits in the following manner:
            1 bit   mirrored property
            4 bits  directionality property
            9 bits  signed offset used for converting case
            1 bit   if 1, adding the signed offset converts the character to lowercase
            1 bit   if 1, subtracting the signed offset converts the character to uppercase
            1 bit   if 1, this character has a titlecase equivalent (possibly itself)
            3 bits  0  may not be part of an identifier
                    1  ignorable control; may continue a Unicode identifier or Java identifier
                    2  may continue a Java identifier but not a Unicode identifier (unused)
                    3  may continue a Unicode identifier or Java identifier
                    4  is a Java whitespace character
                    5  may start or continue a Java identifier;
                       may continue but not start a Unicode identifier (underscores)
                    6  may start or continue a Java identifier but not a Unicode identifier ($)
                    7  may start or continue a Unicode identifier or Java identifier
                    Thus:
                       5, 6, 7 may start a Java identifier
                       1, 2, 3, 5, 6, 7 may continue a Java identifier
                       7 may start a Unicode identifier
                       1, 3, 5, 7 may continue a Unicode identifier
                       1 is ignorable within an identifier
                       4 is Java whitespace
            2 bits  0  this character has no numeric property
                    1  adding the digit offset to the character code and then
                       masking with 0x1F will produce the desired numeric value
                    2  this character has a "strange" numeric value
                    3  a Java supradecimal digit: adding the digit offset to the
                       character code, then masking with 0x1F, then adding 10
                       will produce the desired numeric value
            5 bits  digit offset
            5 bits  character type

            The encoding of character properties is subject to change at any time.
         */
    override fun getProperties(ch: Int): Int {
        val offset = ch.toChar()
        val props = A!![offset.code]
        return props
    }

    fun getPropertiesEx(ch: Int): Int {
        val offset = ch.toChar()
        val props = B!![offset.code].code
        return props
    }

    override fun isDigit(ch: Int): Boolean {
        return '0'.code <= ch && ch <= '9'.code
    }

    override fun isLowerCase(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0001) != 0
    }

    override fun isUpperCase(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0002) != 0
    }

    override fun isOtherAlphabetic(ch: Int): Boolean {
        val props = getPropertiesEx(ch)
        return (props and 0x0004) != 0
    }

    override fun isIdeographic(ch: Int): Boolean {
        val props = getPropertiesEx(ch)
        return (props and 0x0008) != 0
    }

    override fun getType(ch: Int): Int {
        val props = getProperties(ch)
        return (props and 0x1F)
    }

    override fun isJavaIdentifierStart(ch: Int): Boolean {
        val props = getProperties(ch)
        return ((props and 0x00007000) >= 0x00005000)
    }

    override fun isJavaIdentifierPart(ch: Int): Boolean {
        val props = getProperties(ch)
        return ((props and 0x00003000) != 0)
    }

    override fun isUnicodeIdentifierStart(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0010) != 0
    }

    override fun isUnicodeIdentifierPart(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0020) != 0 ||
                isIdentifierIgnorable(ch)
    }

    override fun isIdentifierIgnorable(ch: Int): Boolean {
        val props = getProperties(ch)
        return ((props and 0x00007000) == 0x00001000)
    }

    override fun isEmoji(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0040) != 0
    }

    override fun isEmojiPresentation(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0080) != 0
    }

    override fun isEmojiModifier(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0100) != 0
    }

    override fun isEmojiModifierBase(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0200) != 0
    }

    override fun isEmojiComponent(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0400) != 0
    }

    override fun isExtendedPictographic(ch: Int): Boolean {
        return (getPropertiesEx(ch) and 0x0800) != 0
    }

    override fun toLowerCase(ch: Int): Int {
        if (ch < 'A'.code) { // Fast path for low code points
            return ch
        }
        // ASCII and Latin-1 were designed to optimize case-twiddling operations
        val lower = ch or 0x20
        if (lower <= 'z'.code // In range a-z
            || (lower >= 0xE0 && lower <= 0xFE && lower != 0xF7)
        ) { // ..or agrave-thorn, excluding division
            return lower
        }
        return ch
    }

    override fun toUpperCase(ch: Int): Int {
        if (ch < 'a'.code) { // Fast path for low code points
            return ch
        }
        // ASCII and Latin-1 were designed to optimize case-twiddling operations
        val upper = ch and 0xDF
        if (upper <= 'Z'.code // In range A-Z
            || (upper >= 0xC0 && upper <= 0xDE && upper != 0xD7)
        ) { // ..or Agrave-Thorn, not multiplication
            return upper
        }

        // Special-case for 'y with Diaeresis' which uppercases out of latin1
        if (ch == 0xFF) {
            return 0x178 // Capital Letter Y with Diaeresis
        }
        // Special-case for 'Micro Sign' which uppercases out of latin1
        if (ch == 0xB5) {
            return 0x39C // Greek Capital Letter Mu
        }
        return ch
    }

    override fun toTitleCase(ch: Int): Int {
        return toUpperCase(ch)
    }

    override fun digit(ch: Int, radix: Int): Int {
        val value = DIGITS[ch].toInt()
        return if (value >= 0 && value < radix && radix >= Character.MIN_RADIX && radix <= Character.MAX_RADIX) value else -1
    }

    override fun getNumericValue(ch: Int): Int {
        val `val` = getProperties(ch)
        var retval = -1

        when (`val` and 0xC00) {
            (0x00000000) -> retval = -1
            (0x00000400) -> retval = ch + ((`val` and 0x3E0) shr 5) and 0x1F
            (0x00000800) -> retval = -2
            (0x00000C00) -> retval = (ch + ((`val` and 0x3E0) shr 5) and 0x1F) + 10
            else -> retval = -1
        }
        return retval
    }

    override fun isWhitespace(ch: Int): Boolean {
        val props = getProperties(ch)
        return ((props and 0x00007000) == 0x00004000)
    }

    override fun getDirectionality(ch: Int): Byte {
        val `val` = getProperties(ch)
        var directionality = ((`val` and 0x78000000) shr 27).toByte()

        if (directionality.toInt() == 0xF) {
            directionality = -1
        }
        return directionality
    }

    override fun isMirrored(ch: Int): Boolean {
        val props = getProperties(ch)
        return ((props and -0x80000000) != 0)
    }

    override fun toUpperCaseEx(ch: Int): Int {
        var mapChar = ch
        val `val` = getProperties(ch)

        if ((`val` and 0x00010000) != 0) {
            if ((`val` and 0x07FC0000) != 0x07FC0000) {
                val offset = `val` shl 5 shr (5 + 18)
                mapChar = ch - offset
            } else {
                when (ch) {
                    0x00B5 -> mapChar = 0x039C
                    else -> mapChar = Character.ERROR
                }
            }
        }
        return mapChar
    }

    override fun toUpperCaseCharArray(ch: Int): CharArray? {
        var upperMap: CharArray? = charArrayOf(ch.toChar())
        if (ch == 0x00DF) {
            upperMap = sharpsMap
        }
        return upperMap
    }

    companion object {
        /**
         * Compares two latin1 code points, ignoring case considerations
         *
         * @param b1 byte representing a latin1 code point
         * @param b2 another byte representing a latin1 code point
         * @return true if the two bytes are considered equals ignoring case in latin1
         */
        fun equalsIgnoreCase(b1: Byte, b2: Byte): Boolean {
            if (b1 == b2) {
                return true
            }
            // ASCII and Latin-1 were designed to optimize case-twiddling operations
            val upper = b1.toInt() and 0xDF
            if (upper < 'A'.code) {
                return false // Low ASCII
            }
            return (upper <= 'Z'.code // In range A-Z
                    || (upper >= 0xC0 && upper <= 0XDE && upper != 0xD7)) // ..or A-grave-Thorn, not multiplication
                    && upper == (b2.toInt() and 0xDF) // b2 has same uppercase
        }

        // Digit values for codePoints in the 0-255 range. Contents generated using:
        // for (char i = 0; i < 256; i++) {
        //     int v = -1;
        //     if (i >= '0' && i <= '9') { v = i - '0'; }
        //     else if (i >= 'A' && i <= 'Z') { v = i - 'A' + 10; }
        //     else if (i >= 'a' && i <= 'z') { v = i - 'a' + 10; }
        //     if (i % 20 == 0) System.out.println();
        //     System.out.printf("%2d, ", v);
        // }
        //
        // Analysis has shown that generating the whole array allows the JIT to generate
        // better code compared to a slimmed down array, such as one cutting off after 'z'
        private val DIGITS = byteArrayOf(
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1,
            -1, -1, -1, -1, -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
            25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, -1, -1, -1, -1, -1, -1, 10, 11, 12,
            13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
            33, 34, 35, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        )

        var sharpsMap: CharArray = charArrayOf('S', 'S')

        val instance: CharacterDataLatin1 = CharacterDataLatin1()
        val A: IntArray? = intArrayOf(
            0x4800100F,  //   0   Cc, ignorable
            0x4800100F,  //   1   Cc, ignorable
            0x4800100F,  //   2   Cc, ignorable
            0x4800100F,  //   3   Cc, ignorable
            0x4800100F,  //   4   Cc, ignorable
            0x4800100F,  //   5   Cc, ignorable
            0x4800100F,  //   6   Cc, ignorable
            0x4800100F,  //   7   Cc, ignorable
            0x4800100F,  //   8   Cc, ignorable
            0x5800400F,  //   9   Cc, S, whitespace
            0x5000400F,  //  10   Cc, B, whitespace
            0x5800400F,  //  11   Cc, S, whitespace
            0x6000400F,  //  12   Cc, WS, whitespace
            0x5000400F,  //  13   Cc, B, whitespace
            0x4800100F,  //  14   Cc, ignorable
            0x4800100F,  //  15   Cc, ignorable
            0x4800100F,  //  16   Cc, ignorable
            0x4800100F,  //  17   Cc, ignorable
            0x4800100F,  //  18   Cc, ignorable
            0x4800100F,  //  19   Cc, ignorable
            0x4800100F,  //  20   Cc, ignorable
            0x4800100F,  //  21   Cc, ignorable
            0x4800100F,  //  22   Cc, ignorable
            0x4800100F,  //  23   Cc, ignorable
            0x4800100F,  //  24   Cc, ignorable
            0x4800100F,  //  25   Cc, ignorable
            0x4800100F,  //  26   Cc, ignorable
            0x4800100F,  //  27   Cc, ignorable
            0x5000400F,  //  28   Cc, B, whitespace
            0x5000400F,  //  29   Cc, B, whitespace
            0x5000400F,  //  30   Cc, B, whitespace
            0x5800400F,  //  31   Cc, S, whitespace
            0x6000400C,  //  32   Zs, WS, whitespace
            0x68000018,  //  33   Po, ON
            0x68000018,  //  34   Po, ON
            -0x28000018,  //  35   Mc, B, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31, emoji, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            0x2800601A,  //  36   Sc, ET, currency
            0x28000018,  //  37   Po, ET
            0x68000018,  //  38   Po, ON
            0x68000018,  //  39   Po, ON
            -0x17FFFFEB,  //  40   No, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            -0x17FFFFEA,  //  41   Nl, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            -0x68000018,  //  42   Mc, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31, emoji, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            0x20000019,  //  43   Sm, ES
            0x38000018,  //  44   Po, CS
            0x20000014,  //  45   Pd, ES
            0x38000018,  //  46   Po, CS
            0x38000018,  //  47   Po, CS
            -0x18003609,  //  48   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  49   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  50   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  51   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  52   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  53   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  54   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  55   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  56   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            -0x18003609,  //  57   Pc, WS, hasUpper (subtract 511), hasLower (add 511), hasTitle, whitespace, strange, IDContinue, emojiPresentation, emojiModifier, emojiModifierBase, extendedPictographic
            0x38000018,  //  58   Po, CS
            0x68000018,  //  59   Po, ON
            -0x17FFFFE7,  //  60   Me, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            0x68000019,  //  61   Sm, ON
            -0x17FFFFE7,  //  62   Me, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            0x68000018,  //  63   Po, ON
            0x68000018,  //  64   Po, ON
            -0x827fe1,  //  65   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  66   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  67   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  68   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  69   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  70   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  71   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  72   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  73   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  74   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  75   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  76   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  77   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  78   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  79   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  80   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  81   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  82   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  83   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  84   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  85   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  86   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  87   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  88   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  89   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827fe1,  //  90   , hasUpper (subtract 479), hasTitle, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x17FFFFEB,  //  91   No, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            0x68000018,  //  92   Po, ON
            -0x17FFFFEA,  //  93   Nl, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            0x6800001B,  //  94   Sk, ON
            -0x68005017,  //  95   Nd, hasUpper (subtract 511), hasLower (add 511), hasTitle, supradecimal 31, IDContinue, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            0x6800001B,  //  96   Sk, ON
            -0x817fe2,  //  97   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  //  98   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  //  99   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 100   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 101   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 102   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 103   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 104   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 105   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 106   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 107   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 108   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 109   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 110   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 111   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 112   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 113   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 114   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 115   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 116   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 117   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 118   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 119   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 120   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 121   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817fe2,  // 122   , hasLower (add 479), hasTitle, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x17FFFFEB,  // 123   No, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            0x68000019,  // 124   Sm, ON
            -0x17FFFFEA,  // 125   Nl, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            0x68000019,  // 126   Sm, ON
            0x4800100F,  // 127   Cc, ignorable
            0x4800100F,  // 128   Cc, ignorable
            0x4800100F,  // 129   Cc, ignorable
            0x4800100F,  // 130   Cc, ignorable
            0x4800100F,  // 131   Cc, ignorable
            0x4800100F,  // 132   Cc, ignorable
            0x5000100F,  // 133   Cc, B, ignorable
            0x4800100F,  // 134   Cc, ignorable
            0x4800100F,  // 135   Cc, ignorable
            0x4800100F,  // 136   Cc, ignorable
            0x4800100F,  // 137   Cc, ignorable
            0x4800100F,  // 138   Cc, ignorable
            0x4800100F,  // 139   Cc, ignorable
            0x4800100F,  // 140   Cc, ignorable
            0x4800100F,  // 141   Cc, ignorable
            0x4800100F,  // 142   Cc, ignorable
            0x4800100F,  // 143   Cc, ignorable
            0x4800100F,  // 144   Cc, ignorable
            0x4800100F,  // 145   Cc, ignorable
            0x4800100F,  // 146   Cc, ignorable
            0x4800100F,  // 147   Cc, ignorable
            0x4800100F,  // 148   Cc, ignorable
            0x4800100F,  // 149   Cc, ignorable
            0x4800100F,  // 150   Cc, ignorable
            0x4800100F,  // 151   Cc, ignorable
            0x4800100F,  // 152   Cc, ignorable
            0x4800100F,  // 153   Cc, ignorable
            0x4800100F,  // 154   Cc, ignorable
            0x4800100F,  // 155   Cc, ignorable
            0x4800100F,  // 156   Cc, ignorable
            0x4800100F,  // 157   Cc, ignorable
            0x4800100F,  // 158   Cc, ignorable
            0x4800100F,  // 159   Cc, ignorable
            0x3800000C,  // 160   Zs, CS
            0x68000018,  // 161   Po, ON
            0x2800601A,  // 162   Sc, ET, currency
            0x2800601A,  // 163   Sc, ET, currency
            0x2800601A,  // 164   Sc, ET, currency
            0x2800601A,  // 165   Sc, ET, currency
            0x6800001C,  // 166   So, ON
            0x68000018,  // 167   Po, ON
            0x6800001B,  // 168   Sk, ON
            -0x6800001c,  // 169   Lm, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent
            -0x7005,  // 170   Sk, hasUpper (subtract 511), hasLower (add 511), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x17FFFFE3,  // 171   Lt, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            0x68000019,  // 172   Sm, ON
            0x48001010,  // 173   Cf, ignorable
            -0x6800001c,  // 174   Lm, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent
            0x6800001B,  // 175   Sk, ON
            0x2800001C,  // 176   So, ET
            0x28000019,  // 177   Sm, ET
            0x1800060B,  // 178   No, EN, decimal 16
            0x1800060B,  // 179   No, EN, decimal 16
            0x6800001B,  // 180   Sk, ON
            -0x7fd7002,  // 181   , hasLower (add 0), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            0x68000018,  // 182   Po, ON
            -0x68000018,  // 183   Mc, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31, IDContinue, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            0x6800001B,  // 184   Sk, ON
            0x1800050B,  // 185   No, EN, decimal 8
            -0x7005,  // 186   Sk, hasUpper (subtract 511), hasLower (add 511), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x17FFFFE2,  // 187   Ll, hasUpper (subtract 511), hasLower (add 511), hasTitle, identifier start, supradecimal 31
            0x6800080B,  // 188   No, ON, strange
            0x6800080B,  // 189   No, ON, strange
            0x6800080B,  // 190   No, ON, strange
            0x68000018,  // 191   Po, ON
            -0x827001,  // 192   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 193   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 194   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 195   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 196   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 197   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 198   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 199   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 200   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 201   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 202   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 203   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 204   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 205   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 206   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 207   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 208   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 209   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 210   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 211   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 212   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 213   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 214   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            0x68000019,  // 215   Sm, ON
            -0x827001,  // 216   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 217   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 218   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 219   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 220   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 221   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x827001,  // 222   , hasUpper (subtract 479), hasTitle, supradecimal 31, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x7fd7002,  // 223   , hasLower (add 0), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 224   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 225   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 226   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 227   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 228   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 229   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 230   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 231   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 232   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 233   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 234   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 235   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 236   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 237   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 238   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 239   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 240   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 241   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 242   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 243   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 244   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 245   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 246   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            0x68000019,  // 247   Sm, ON
            -0x817002,  // 248   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 249   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 250   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 251   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 252   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 253   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x817002,  // 254   , hasLower (add 479), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
            -0x61d7002 // 255   , hasLower (add 120), hasTitle, supradecimal 31, otherLowercase, otherUppercase, otherAlphabetic, ideographic, emoji, emojiPresentation, emojiModifier, emojiModifierBase, emojiComponent, extendedPictographic
        )

        // The B table has 256 entries for a total of 512 bytes.
        val B: CharArray? = charArrayOf(
            0x0000.toChar(),  //   0   unassigned, L
            0x0000.toChar(),  //   1   unassigned, L
            0x0000.toChar(),  //   2   unassigned, L
            0x0000.toChar(),  //   3   unassigned, L
            0x0000.toChar(),  //   4   unassigned, L
            0x0000.toChar(),  //   5   unassigned, L
            0x0000.toChar(),  //   6   unassigned, L
            0x0000.toChar(),  //   7   unassigned, L
            0x0000.toChar(),  //   8   unassigned, L
            0x0000.toChar(),  //   9   unassigned, L
            0x0000.toChar(),  //  10   unassigned, L
            0x0000.toChar(),  //  11   unassigned, L
            0x0000.toChar(),  //  12   unassigned, L
            0x0000.toChar(),  //  13   unassigned, L
            0x0000.toChar(),  //  14   unassigned, L
            0x0000.toChar(),  //  15   unassigned, L
            0x0000.toChar(),  //  16   unassigned, L
            0x0000.toChar(),  //  17   unassigned, L
            0x0000.toChar(),  //  18   unassigned, L
            0x0000.toChar(),  //  19   unassigned, L
            0x0000.toChar(),  //  20   unassigned, L
            0x0000.toChar(),  //  21   unassigned, L
            0x0000.toChar(),  //  22   unassigned, L
            0x0000.toChar(),  //  23   unassigned, L
            0x0000.toChar(),  //  24   unassigned, L
            0x0000.toChar(),  //  25   unassigned, L
            0x0000.toChar(),  //  26   unassigned, L
            0x0000.toChar(),  //  27   unassigned, L
            0x0000.toChar(),  //  28   unassigned, L
            0x0000.toChar(),  //  29   unassigned, L
            0x0000.toChar(),  //  30   unassigned, L
            0x0000.toChar(),  //  31   unassigned, L
            0x0000.toChar(),  //  32   unassigned, L
            0x0000.toChar(),  //  33   unassigned, L
            0x0000.toChar(),  //  34   unassigned, L
            0x0440.toChar(),  //  35   unassigned, L, emoji, emojiComponent
            0x0000.toChar(),  //  36   unassigned, L
            0x0000.toChar(),  //  37   unassigned, L
            0x0000.toChar(),  //  38   unassigned, L
            0x0000.toChar(),  //  39   unassigned, L
            0x0000.toChar(),  //  40   unassigned, L
            0x0000.toChar(),  //  41   unassigned, L
            0x0440.toChar(),  //  42   unassigned, L, emoji, emojiComponent
            0x0000.toChar(),  //  43   unassigned, L
            0x0000.toChar(),  //  44   unassigned, L
            0x0000.toChar(),  //  45   unassigned, L
            0x0000.toChar(),  //  46   unassigned, L
            0x0000.toChar(),  //  47   unassigned, L
            0x0460.toChar(),  //  48   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  49   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  50   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  51   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  52   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  53   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  54   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  55   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  56   unassigned, L, IDContinue, emoji, emojiComponent
            0x0460.toChar(),  //  57   unassigned, L, IDContinue, emoji, emojiComponent
            0x0000.toChar(),  //  58   unassigned, L
            0x0000.toChar(),  //  59   unassigned, L
            0x0000.toChar(),  //  60   unassigned, L
            0x0000.toChar(),  //  61   unassigned, L
            0x0000.toChar(),  //  62   unassigned, L
            0x0000.toChar(),  //  63   unassigned, L
            0x0000.toChar(),  //  64   unassigned, L
            0x0032.toChar(),  //  65   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  66   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  67   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  68   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  69   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  70   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  71   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  72   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  73   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  74   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  75   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  76   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  77   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  78   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  79   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  80   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  81   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  82   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  83   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  84   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  85   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  86   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  87   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  88   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  89   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  //  90   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0000.toChar(),  //  91   unassigned, L
            0x0000.toChar(),  //  92   unassigned, L
            0x0000.toChar(),  //  93   unassigned, L
            0x0000.toChar(),  //  94   unassigned, L
            0x0020.toChar(),  //  95   unassigned, L, IDContinue
            0x0000.toChar(),  //  96   unassigned, L
            0x0031.toChar(),  //  97   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  //  98   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  //  99   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 100   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 101   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 102   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 103   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 104   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 105   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 106   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 107   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 108   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 109   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 110   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 111   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 112   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 113   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 114   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 115   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 116   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 117   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 118   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 119   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 120   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 121   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 122   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0000.toChar(),  // 123   unassigned, L
            0x0000.toChar(),  // 124   unassigned, L
            0x0000.toChar(),  // 125   unassigned, L
            0x0000.toChar(),  // 126   unassigned, L
            0x0000.toChar(),  // 127   unassigned, L
            0x0000.toChar(),  // 128   unassigned, L
            0x0000.toChar(),  // 129   unassigned, L
            0x0000.toChar(),  // 130   unassigned, L
            0x0000.toChar(),  // 131   unassigned, L
            0x0000.toChar(),  // 132   unassigned, L
            0x0000.toChar(),  // 133   unassigned, L
            0x0000.toChar(),  // 134   unassigned, L
            0x0000.toChar(),  // 135   unassigned, L
            0x0000.toChar(),  // 136   unassigned, L
            0x0000.toChar(),  // 137   unassigned, L
            0x0000.toChar(),  // 138   unassigned, L
            0x0000.toChar(),  // 139   unassigned, L
            0x0000.toChar(),  // 140   unassigned, L
            0x0000.toChar(),  // 141   unassigned, L
            0x0000.toChar(),  // 142   unassigned, L
            0x0000.toChar(),  // 143   unassigned, L
            0x0000.toChar(),  // 144   unassigned, L
            0x0000.toChar(),  // 145   unassigned, L
            0x0000.toChar(),  // 146   unassigned, L
            0x0000.toChar(),  // 147   unassigned, L
            0x0000.toChar(),  // 148   unassigned, L
            0x0000.toChar(),  // 149   unassigned, L
            0x0000.toChar(),  // 150   unassigned, L
            0x0000.toChar(),  // 151   unassigned, L
            0x0000.toChar(),  // 152   unassigned, L
            0x0000.toChar(),  // 153   unassigned, L
            0x0000.toChar(),  // 154   unassigned, L
            0x0000.toChar(),  // 155   unassigned, L
            0x0000.toChar(),  // 156   unassigned, L
            0x0000.toChar(),  // 157   unassigned, L
            0x0000.toChar(),  // 158   unassigned, L
            0x0000.toChar(),  // 159   unassigned, L
            0x0000.toChar(),  // 160   unassigned, L
            0x0000.toChar(),  // 161   unassigned, L
            0x0000.toChar(),  // 162   unassigned, L
            0x0000.toChar(),  // 163   unassigned, L
            0x0000.toChar(),  // 164   unassigned, L
            0x0000.toChar(),  // 165   unassigned, L
            0x0000.toChar(),  // 166   unassigned, L
            0x0000.toChar(),  // 167   unassigned, L
            0x0000.toChar(),  // 168   unassigned, L
            0x0840.toChar(),  // 169   unassigned, L, emoji, extendedPictographic
            0x0031.toChar(),  // 170   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0000.toChar(),  // 171   unassigned, L
            0x0000.toChar(),  // 172   unassigned, L
            0x0000.toChar(),  // 173   unassigned, L
            0x0840.toChar(),  // 174   unassigned, L, emoji, extendedPictographic
            0x0000.toChar(),  // 175   unassigned, L
            0x0000.toChar(),  // 176   unassigned, L
            0x0000.toChar(),  // 177   unassigned, L
            0x0000.toChar(),  // 178   unassigned, L
            0x0000.toChar(),  // 179   unassigned, L
            0x0000.toChar(),  // 180   unassigned, L
            0x0031.toChar(),  // 181   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0000.toChar(),  // 182   unassigned, L
            0x0020.toChar(),  // 183   unassigned, L, IDContinue
            0x0000.toChar(),  // 184   unassigned, L
            0x0000.toChar(),  // 185   unassigned, L
            0x0031.toChar(),  // 186   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0000.toChar(),  // 187   unassigned, L
            0x0000.toChar(),  // 188   unassigned, L
            0x0000.toChar(),  // 189   unassigned, L
            0x0000.toChar(),  // 190   unassigned, L
            0x0000.toChar(),  // 191   unassigned, L
            0x0032.toChar(),  // 192   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 193   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 194   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 195   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 196   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 197   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 198   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 199   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 200   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 201   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 202   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 203   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 204   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 205   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 206   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 207   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 208   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 209   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 210   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 211   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 212   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 213   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 214   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0000.toChar(),  // 215   unassigned, L
            0x0032.toChar(),  // 216   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 217   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 218   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 219   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 220   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 221   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0032.toChar(),  // 222   unassigned, L, otherUppercase, IDStart, IDContinue
            0x0031.toChar(),  // 223   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 224   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 225   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 226   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 227   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 228   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 229   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 230   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 231   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 232   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 233   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 234   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 235   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 236   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 237   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 238   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 239   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 240   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 241   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 242   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 243   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 244   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 245   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 246   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0000.toChar(),  // 247   unassigned, L
            0x0031.toChar(),  // 248   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 249   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 250   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 251   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 252   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 253   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar(),  // 254   unassigned, L, otherLowercase, IDStart, IDContinue
            0x0031.toChar() // 255   unassigned, L, otherLowercase, IDStart, IDContinue
        )

        // In all, the character property tables require 1024 bytes.
    }
}

