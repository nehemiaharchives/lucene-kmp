package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Ported
import org.gnit.lucenekmp.jdkport.appendCodePoint
import org.gnit.lucenekmp.jdkport.codePointAt
import kotlin.random.Random

/**
 * A string generator from a predefined set of codepoints or characters.
 */
@Ported(from = "com.carrotsearch.randomizedtesting.generators.CodepointSetGenerator")
open class CodepointSetGenerator : StringGenerator {
    val bmp: IntArray
    val supplementary: IntArray
    val all: IntArray

    /**
     * All characters must be from BMP (no parts of surrogate pairs allowed).
     */
    constructor(chars: CharArray) {
        this.bmp = IntArray(chars.size)
        this.supplementary = IntArray(0)

        for (i in chars.indices) {
            bmp[i] = chars[i].code and 0xffff

            if (isSurrogate(chars[i])) {
                throw IllegalArgumentException(
                    "Value is part of a surrogate pair: 0x${bmp[i].toString(16)}"
                )
            }
        }

        this.all = concat(bmp, supplementary)
        if (all.isEmpty()) {
            throw IllegalArgumentException("Empty set of characters?")
        }
    }

    /**
     * Parse the given [String] and split into BMP and supplementary codepoints.
     */
    constructor(s: String) {
        var bmps = 0
        var supplementaries = 0
        var i = 0
        while (i < s.length) {
            val codepoint = s.codePointAt(i)
            if (Character.isSupplementaryCodePoint(codepoint)) {
                supplementaries++
            } else {
                bmps++
            }
            i += Character.charCount(codepoint)
        }

        this.bmp = IntArray(bmps)
        this.supplementary = IntArray(supplementaries)
        i = 0
        while (i < s.length) {
            val codepoint = s.codePointAt(i)
            if (Character.isSupplementaryCodePoint(codepoint)) {
                supplementary[--supplementaries] = codepoint
            } else {
                bmp[--bmps] = codepoint
            }
            i += Character.charCount(codepoint)
        }

        this.all = concat(bmp, supplementary)
        if (all.isEmpty()) {
            throw IllegalArgumentException("Empty set of characters?")
        }
    }

    override fun ofCodeUnitsLength(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String {
        var length = RandomNumbers.randomIntBetween(r, minCodeUnits, maxCodeUnits)

        // Check and cater for odd number of code units if no bmp characters are given.
        if (bmp.isEmpty() && isOdd(length)) {
            if (minCodeUnits == maxCodeUnits) {
                throw IllegalArgumentException(
                    "Cannot return an odd number of code units when surrogate pairs are the only available codepoints."
                )
            } else {
                // length is odd so we move forward or backward to the closest even number.
                if (length == minCodeUnits) {
                    length++
                } else {
                    length--
                }
            }
        }

        val codepoints = IntArray(length)
        var actual = 0
        while (length > 0) {
            codepoints[actual] = if (length == 1) {
                bmp[r.nextInt(bmp.size)]
            } else {
                all[r.nextInt(all.size)]
            }

            length -= if (Character.isSupplementaryCodePoint(codepoints[actual])) 2 else 1
            actual++
        }
        return stringFromCodePoints(codepoints, actual)
    }

    override fun ofCodePointsLength(r: Random, minCodePoints: Int, maxCodePoints: Int): String {
        var length = RandomNumbers.randomIntBetween(r, minCodePoints, maxCodePoints)
        val codepoints = IntArray(length)
        while (length > 0) {
            codepoints[--length] = all[r.nextInt(all.size)]
        }
        return stringFromCodePoints(codepoints, codepoints.size)
    }

    /** Is a given number odd? */
    private fun isOdd(v: Int): Boolean {
        return (v and 1) != 0
    }

    private fun concat(vararg arrays: IntArray): IntArray {
        var totalLength = 0
        for (a in arrays) {
            totalLength += a.size
        }
        val concat = IntArray(totalLength)
        var i = 0
        var j = 0
        while (j < arrays.size) {
            arrays[j].copyInto(concat, i, 0, arrays[j].size)
            i += arrays[j].size
            j++
        }
        return concat
    }

    private fun isSurrogate(chr: Char): Boolean {
        return chr in '\uD800'..'\uDFFF'
    }

    private fun stringFromCodePoints(codepoints: IntArray, length: Int): String {
        val sb = StringBuilder()
        for (i in 0 until length) {
            sb.appendCodePoint(codepoints[i])
        }
        return sb.toString()
    }
}
