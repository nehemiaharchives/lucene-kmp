package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * tests functions in [Character] to see if it behaves like [java.lang.Character](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/Character.html)
 */
class CharacterTest {

    @Test
    fun testToLowerCase() {
        assertEquals('a'.code, Character.toLowerCase('A'.code))
        assertEquals('z'.code, Character.toLowerCase('Z'.code))
        assertEquals('a'.code, Character.toLowerCase('a'.code))
    }

    @Test
    fun testToUpperCase() {
        assertEquals('A'.code, Character.toUpperCase('a'.code))
        assertEquals('Z'.code, Character.toUpperCase('z'.code))
        assertEquals('A'.code, Character.toUpperCase('A'.code))
    }

    @Test
    fun testIsLowerCase() {
        assertTrue(Character.isLowerCase('a'.code))
        assertFalse(Character.isLowerCase('A'.code))
        assertFalse(Character.isLowerCase('1'.code))
        assertTrue(Character.isLowerCase('ß'.code)) // German sharp S
    }

    @Test
    fun testIsHighSurrogate() {
        assertTrue(Character.isHighSurrogate('\uD800'))
        assertTrue(Character.isHighSurrogate('\uDBFF'))
        assertFalse(Character.isHighSurrogate('\uDC00'))
        assertFalse(Character.isHighSurrogate('A'))
    }

    @Test
    fun testCompare() {
        assertEquals(0, Character.compare('a', 'a'))
        assertTrue(Character.compare('a', 'b') < 0)
        assertTrue(Character.compare('b', 'a') > 0)
    }

    @Test
    fun testCharCount() {
        assertEquals(1, Character.charCount('A'.code))
        // Supplementary code point (outside BMP, needs 2 chars in UTF-16)
        assertEquals(2, Character.charCount(0x1F600)) // 😀
    }

    @Test
    fun testCodePointAtSequence() {
        val text = "A\uD83D\uDE00B" // A, 😀 (surrogate pair), B
        assertEquals('A'.code, Character.codePointAt(text, 0))
        assertEquals(0x1F600, Character.codePointAt(text, 1)) // 😀
        assertEquals('\uDE00'.code, Character.codePointAt(text, 2)) // Only low surrogate if used alone
        assertEquals('B'.code, Character.codePointAt(text, 3))
        // Out of bounds
        assertFailsWith<IndexOutOfBoundsException> { Character.codePointAt(text, 5) }
    }

    @Test
    fun testCodePointAtArray() {
        val arr = charArrayOf('A', '\uD83D', '\uDE00', 'B')
        assertEquals('A'.code, Character.codePointAt(arr, 0, arr.size))
        assertEquals(0x1F600, Character.codePointAt(arr, 1, arr.size)) // 😀
        assertEquals('\uDE00'.code, Character.codePointAt(arr, 2, arr.size))
        assertEquals('B'.code, Character.codePointAt(arr, 3, arr.size))
        // Check exception for OOB
        assertFailsWith<IndexOutOfBoundsException> { Character.codePointAt(arr, 4, arr.size) }
        assertFailsWith<IndexOutOfBoundsException> { Character.codePointAt(arr, 0, 0) }
    }

    @Test
    fun testConstants() {
        assertEquals(2, Character.BYTES)
        assertEquals(0, Character.MIN_CODE_POINT)
        assertEquals(0x10FFFF, Character.MAX_CODE_POINT)
        assertEquals('\uD800', Character.MIN_HIGH_SURROGATE)
        assertEquals('\uDBFF', Character.MAX_HIGH_SURROGATE)
        assertEquals(2, Character.MIN_RADIX)
        assertEquals(36, Character.MAX_RADIX)
        assertEquals(16, Character.SIZE)
    }

    @Test
    fun testToCodePoint() {
        val high = '\uD83D'
        val low = '\uDE00'
        val codePoint = Character.toCodePoint(high, low)
        assertEquals(0x1F600, codePoint)
    }

    @Test
    fun testToCharsBmp() {
        val arr = CharArray(1)
        val written = Character.toChars('A'.code, arr, 0)
        assertEquals(1, written)
        assertEquals('A', arr[0])
    }

    @Test
    fun testToCharsSupplementary() {
        val arr = CharArray(2)
        val codePoint = 0x1F600
        val written = Character.toChars(codePoint, arr, 0)
        assertEquals(2, written)
        assertEquals(Character.highSurrogate(codePoint), arr[0])
        assertEquals(Character.lowSurrogate(codePoint), arr[1])
    }

    @Test
    fun testToCharsInvalid() {
        val arr = CharArray(1)
        assertFailsWith<IllegalArgumentException> {
            Character.toChars(-1, arr, 0)
        }
    }

    @Test
    fun testToSurrogates() {
        val codePoint = 0x1F600
        val arr = CharArray(2)
        Character.toSurrogates(codePoint, arr, 0)
        assertEquals(Character.highSurrogate(codePoint), arr[0])
        assertEquals(Character.lowSurrogate(codePoint), arr[1])
    }

    @Test
    fun testLowHighSurrogate() {
        val codePoint = 0x1F600
        val high = Character.highSurrogate(codePoint)
        val low = Character.lowSurrogate(codePoint)
        assertTrue(Character.isHighSurrogate(high))
        assertTrue(low.isLowSurrogate())
        assertEquals(codePoint, Character.toCodePoint(high, low))
    }

    @Test
    fun testIsBmpCodePoint() {
        assertTrue(Character.isBmpCodePoint('A'.code))
        assertFalse(Character.isBmpCodePoint(0x1F600))
    }

    @Test
    fun testIsValidCodePoint() {
        assertTrue(Character.isValidCodePoint(0))
        assertTrue(Character.isValidCodePoint(0x10FFFF))
        assertFalse(Character.isValidCodePoint(-1))
        assertFalse(Character.isValidCodePoint(0x110000))
    }

    @Test
    fun testIsSupplementaryCodePoint() {
        assertTrue(Character.isSupplementaryCodePoint(0x10000))
        assertTrue(Character.isSupplementaryCodePoint(0x10FFFF))
        assertFalse(Character.isSupplementaryCodePoint(0xFFFF))
    }
}