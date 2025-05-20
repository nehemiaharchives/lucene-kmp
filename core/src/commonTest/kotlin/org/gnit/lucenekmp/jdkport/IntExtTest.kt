package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntExtTest {

    @Test
    fun testRotateLeft() {
        val value = 0b0001_0000
        val rotated = Int.rotateLeft(value, 2)
        assertEquals(0b0100_0000, rotated)
    }

    @Test
    fun testToBinaryString() {
        val value = 10
        val binaryString = Int.toBinaryString(value)
        assertEquals("1010", binaryString)
    }

    @Test
    fun testToUnsignedLong() {
        val value = -1
        val unsignedLong = Int.toUnsignedLong(value)
        assertEquals(4294967295L, unsignedLong)
    }

    @Test
    fun testNumberOfLeadingZeros() {
        val value = 0b0000_1000
        val leadingZeros = Int.numberOfLeadingZeros(value)
        assertEquals(28, leadingZeros)
    }

    @Test
    fun testNumberOfTrailingZeros() {
        val value = 0b1000_0000
        val trailingZeros = Int.numberOfTrailingZeros(value)
        assertEquals(7, trailingZeros)
    }

    @Test
    fun testBitCount() {
        val value = 0b1010_1010
        val bitCount = Int.bitCount(value)
        assertEquals(4, bitCount)
    }

    @Test
    fun testCompare() {
        assertEquals(0, Int.compare(10, 10))
        assertTrue(Int.compare(10, 20) < 0)
        assertTrue(Int.compare(20, 10) > 0)
    }

    @Test
    fun testSignum() {
        assertEquals(1, Int.signum(10))
        assertEquals(-1, Int.signum(-10))
        assertEquals(0, Int.signum(0))
    }

    @Test
    fun testReverseBytes() {
        val value = 0x12345678
        val reversed = Int.reverseBytes(value)
        assertEquals(0x78563412, reversed)
    }

    @Test
    fun testToHexString() {
        assertEquals("0", Int.toHexString(0))
        assertEquals("a", Int.toHexString(10))
        assertEquals("ff", Int.toHexString(255))
        assertEquals("7fffffff", Int.toHexString(Int.MAX_VALUE))
        assertEquals("80000000", Int.toHexString(Int.MIN_VALUE))
        // Confirm no leading zeros
        assertEquals("123", Int.toHexString(0x123))
        assertEquals("100", Int.toHexString(0x100))
    }
}
