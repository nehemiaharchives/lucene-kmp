package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ArraysTest {

    @Test
    fun testMismatchByteArray() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 0, 4, 5)
        val c = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(2, Arrays.mismatch(a, 0, b, 0, 5))
        assertEquals(-1, Arrays.mismatch(a, 0, c, 0, 5))
    }

    @Test
    fun testMismatchIntArray() {
        val a = intArrayOf(1, 2, 3, 4, 5)
        val b = intArrayOf(1, 2, 0, 4, 5)
        val c = intArrayOf(1, 2, 3, 4, 5)
        assertEquals(2, Arrays.mismatch(a, 0, b, 0, 5))
        assertEquals(-1, Arrays.mismatch(a, 0, c, 0, 5))
    }

    @Test
    fun testEqualsByteArray() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 5)
        val c = byteArrayOf(1, 2, 0, 4, 5)
        assertTrue(Arrays.equals(a, 0, 5, b, 0, 5))
        assertFalse(Arrays.equals(a, 0, 5, c, 0, 5))
    }

    @Test
    fun testEqualsIntArray() {
        val a = intArrayOf(1, 2, 3, 4, 5)
        val b = intArrayOf(1, 2, 3, 4, 5)
        val c = intArrayOf(1, 2, 0, 4, 5)
        assertTrue(Arrays.equals(a, 0, 5, b, 0, 5))
        assertFalse(Arrays.equals(a, 0, 5, c, 0, 5))
    }

    @Test
    fun testCompareIntArray() {
        val a = intArrayOf(1, 2, 3, 4, 5)
        val b = intArrayOf(1, 2, 3, 4, 5)
        val c = intArrayOf(1, 2, 0, 4, 5)
        assertEquals(0, Arrays.compare(a, 0, 5, b, 0, 5))
        assertTrue(Arrays.compare(a, 0, 5, c, 0, 5) > 0)
    }

    @Test
    fun testCopyOfRange() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = Arrays.copyOfRange(a, 1, 4)
        assertTrue(Arrays.equals(b, 0, 3, byteArrayOf(2, 3, 4), 0, 3))
    }

    @Test
    fun testBinarySearch() {
        val a = intArrayOf(1, 2, 3, 4, 5)
        assertEquals(2, Arrays.binarySearch(a, 3))
        assertEquals(-1, Arrays.binarySearch(a, 0))
    }

    @Test
    fun testSort() {
        val a = intArrayOf(5, 4, 3, 2, 1)
        Arrays.sort(a)
        assertTrue(Arrays.equals(a, 0, 5, intArrayOf(1, 2, 3, 4, 5), 0, 5))
    }
}
