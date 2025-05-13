package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LongExtTest {

    @Test
    fun testCompare() {
        assertEquals(0, Long.compare(123456789L, 123456789L))
        assertTrue(Long.compare(123456789L, 987654321L) < 0)
        assertTrue(Long.compare(987654321L, 123456789L) > 0)
    }

    @Test
    fun testToBinaryString() {
        val value = 10L
        val binaryString = Long.toBinaryString(value)
        assertEquals("1010", binaryString)
    }

    @Test
    fun testToUnsignedString() {
        val value = -1L
        val unsignedString = Long.toUnsignedString(value)
        assertEquals("18446744073709551615", unsignedString)
    }

    @Test
    fun testToHexString() {
        val value = 255L
        val hexString = Long.toHexString(value)
        assertEquals("ff", hexString)
    }

    @Test
    fun testNumberOfLeadingZeros() {
        val value = 0b0000_1000L
        val leadingZeros = Long.numberOfLeadingZeros(value)
        assertEquals(60, leadingZeros)
    }

    @Test
    fun testNumberOfTrailingZeros() {
        val value = 0b1000_0000L
        val trailingZeros = Long.numberOfTrailingZeros(value)
        assertEquals(7, trailingZeros)
    }

    @Test
    fun testBitCount() {
        val value = 0b1010_1010L
        val bitCount = Long.bitCount(value)
        assertEquals(4, bitCount)
    }

    @Test
    fun testRotateLeft() {
        val value = 0b0001_0000L
        val rotated = Long.rotateLeft(value, 2)
        assertEquals(0b0100_0000L, rotated)
    }

    @Test
    fun testRotateRight() {
        val value = 0b0001_0000L
        val rotated = Long.rotateRight(value, 2)
        assertEquals(0b0000_0100L, rotated)
    }

    @Test
    fun testReverseBytes() {
        val value = 0x0102030405060708L
        val reversed = Long.reverseBytes(value)
        assertEquals(0x0807060504030201L, reversed)
    }
}
