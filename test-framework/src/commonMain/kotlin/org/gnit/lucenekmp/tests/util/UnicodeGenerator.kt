package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Ported
import org.gnit.lucenekmp.jdkport.appendCodePoint
import kotlin.random.Random

/**
 * A string generator that emits valid unicodeGenerator codepoints.
 */
@Ported(from = "com.carrotsearch.randomizedtesting.generators.UnicodeGenerator")
class UnicodeGenerator : StringGenerator() {
    override fun ofCodeUnitsLength(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String {
        val length = RandomNumbers.randomIntBetween(r, minCodeUnits, maxCodeUnits)
        val chars = CharArray(length)
        var i = 0
        while (i < chars.size) {
            val t = RandomNumbers.randomIntBetween(r, 0, 4)
            if (t == 0 && i < length - 1) {
                // Make a surrogate pair
                chars[i++] = RandomNumbers.randomIntBetween(r, 0xd800, 0xdbff).toChar() // high
                chars[i++] = RandomNumbers.randomIntBetween(r, 0xdc00, 0xdfff).toChar() // low
            } else if (t <= 1) {
                chars[i++] = RandomNumbers.randomIntBetween(r, 0, 0x007f).toChar()
            } else if (t == 2) {
                chars[i++] = RandomNumbers.randomIntBetween(r, 0x80, 0x07ff).toChar()
            } else if (t == 3) {
                chars[i++] = RandomNumbers.randomIntBetween(r, 0x800, 0xd7ff).toChar()
            } else if (t == 4) {
                chars[i++] = RandomNumbers.randomIntBetween(r, 0xe000, 0xffff).toChar()
            }
        }
        return chars.concatToString()
    }

    override fun ofCodePointsLength(r: Random, minCodePoints: Int, maxCodePoints: Int): String {
        val length = RandomNumbers.randomIntBetween(r, minCodePoints, maxCodePoints)
        val chars = IntArray(length)
        for (i in chars.indices) {
            var v = RandomNumbers.randomIntBetween(r, 0, CODEPOINT_RANGE)
            if (v >= Character.MIN_SURROGATE.code) {
                v += SURROGATE_RANGE
            }
            chars[i] = v
        }
        return stringFromCodePoints(chars, chars.size)
    }

    /**
     * Returns a random string that will have a random UTF-8 representation length between
     * `minUtf8Length` and `maxUtf8Length`.
     *
     * @param minUtf8Length Minimum UTF-8 representation length (inclusive).
     * @param maxUtf8Length Maximum UTF-8 representation length (inclusive).
     */
    fun ofUtf8Length(r: Random, minUtf8Length: Int, maxUtf8Length: Int): String {
        val length = RandomNumbers.randomIntBetween(r, minUtf8Length, maxUtf8Length)
        val buffer = CharArray(length * 3)
        var bytes = length
        var i = 0
        while (i < buffer.size && bytes != 0) {
            val t = if (bytes >= 4) {
                r.nextInt(5)
            } else if (bytes >= 3) {
                r.nextInt(4)
            } else if (bytes >= 2) {
                r.nextInt(2)
            } else {
                0
            }

            if (t == 0) {
                buffer[i] = RandomNumbers.randomIntBetween(r, 0, 0x7f).toChar()
                bytes--
            } else if (1 == t) {
                buffer[i] = RandomNumbers.randomIntBetween(r, 0x80, 0x7ff).toChar()
                bytes -= 2
            } else if (2 == t) {
                buffer[i] = RandomNumbers.randomIntBetween(r, 0x800, 0xd7ff).toChar()
                bytes -= 3
            } else if (3 == t) {
                buffer[i] = RandomNumbers.randomIntBetween(r, 0xe000, 0xffff).toChar()
                bytes -= 3
            } else if (4 == t) {
                // Make a surrogate pair
                buffer[i++] = RandomNumbers.randomIntBetween(r, 0xd800, 0xdbff).toChar() // high
                buffer[i] = RandomNumbers.randomIntBetween(r, 0xdc00, 0xdfff).toChar() // low
                bytes -= 4
            }
            i++
        }
        return buffer.concatToString(0, i)
    }

    private fun stringFromCodePoints(codepoints: IntArray, length: Int): String {
        val sb = StringBuilder()
        for (i in 0 until length) {
            sb.appendCodePoint(codepoints[i])
        }
        return sb.toString()
    }

    companion object {
        private const val SURROGATE_RANGE = Character.MAX_SURROGATE.code - Character.MIN_SURROGATE.code + 1
        private const val CODEPOINT_RANGE = Character.MAX_CODE_POINT - SURROGATE_RANGE
    }
}
