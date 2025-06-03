package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
    fun testSortIntArray() {
        val a = intArrayOf(5, 4, 3, 2, 1)
        Arrays.sort(a)
        assertTrue(Arrays.equals(a, 0, 5, intArrayOf(1, 2, 3, 4, 5), 0, 5))
    }

    @Test
    fun testSortLongArray() {
        val a = longArrayOf(50, 40, 10, 30, 20)
        Arrays.sort(a)
        assertTrue(Arrays.equals(a, 0, a.size, longArrayOf(10, 20, 30, 40, 50), 0, 5))
    }

    @Test
    fun testSortFloatArray() {
        val a = floatArrayOf(3.5f, 1.1f, 2.2f, -4.0f, 0f)
        Arrays.sort(a)
        assertTrue(Arrays.equals(a, 0, a.size, floatArrayOf(-4.0f, 0f, 1.1f, 2.2f, 3.5f), 0, 5))
    }

    @Test
    fun testSortDoubleArray() {
        val a = doubleArrayOf(3.5, 1.1, 2.2, -4.0, 0.0)
        Arrays.sort(a)
        //assertTrue(Arrays.equals(a, 0, a.size, doubleArrayOf(-4.0, 0.0, 1.1, 2.2, 3.5), 0, 5))
        assertContentEquals(
            doubleArrayOf(-4.0, 0.0, 1.1, 2.2, 3.5),
            a,
            "Double array sort failed"
        )
    }

    @Test
    fun testSortIntArrayRange() {
        val a = intArrayOf(9, 7, 5, 3, 1)
        Arrays.sort(a, 1, 4) // Should sort [7,5,3] to [3,5,7]: array becomes [9,3,5,7,1]
        assertTrue(Arrays.equals(a, 0, a.size, intArrayOf(9, 3, 5, 7, 1), 0, 5))
    }

    @Test
    fun testSortLongArrayRange() {
        val a = longArrayOf(100, 80, 60, 40, 20)
        Arrays.sort(a, 2, 5) // Should sort [60,40,20] to [20,40,60]: array becomes [100,80,20,40,60]
        assertTrue(Arrays.equals(a, 0, a.size, longArrayOf(100, 80, 20, 40, 60), 0, 5))
    }

    @Test
    fun testSortGenericArrayWithComparator() {
        val a = arrayOf("banana", "apple", "pear")
        Arrays.sort(a, Comparator { s1, s2 -> s1.length - s2.length })
        // Sorted by length: ["pear","apple","banana"]
        assertTrue(a contentEquals arrayOf("pear", "apple", "banana"))
    }

    @Test
    fun testMismatchByteArrayWithRanges() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 9, 4, 5)
        // Mismatch at index 2 of the specified range
        assertEquals(2, Arrays.mismatch(a, 0, 5, b, 0, 5))
        // Equal over this range
        assertEquals(1, Arrays.mismatch(a, 1, 3, b, 1, 3))
        // a is a proper prefix of b in the specified range
        assertEquals(2, Arrays.mismatch(a, 0, 2, b, 0, 4))
        // b is a proper prefix of a
        assertEquals(2, Arrays.mismatch(a, 0, 4, b, 0, 2))
        // Empty range, should be equal
        assertEquals(-1, Arrays.mismatch(a, 2, 2, b, 2, 2))
    }

    @Test
    fun testMismatchIntArrayWithRanges() {
        val a = intArrayOf(10, 20, 30, 40, 50)
        val b = intArrayOf(10, 20, 99, 40, 50)
        // Mismatch at index 2 of the range
        assertEquals(2, Arrays.mismatch(a, 0, 5, b, 0, 5))
        // Equal over this restricted range
        assertEquals(1, Arrays.mismatch(a, 1, 3, b, 1, 3))
        // a is a proper prefix of b
        assertEquals(2, Arrays.mismatch(a, 0, 2, b, 0, 4))
        // b is a proper prefix of a
        assertEquals(2, Arrays.mismatch(a, 0, 4, b, 0, 2))
        // Empty range
        assertEquals(-1, Arrays.mismatch(a, 2, 2, b, 2, 2))
    }

    @Test
    fun testMismatchByteArrayWithNullsAndBounds() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3)
        // Null checks (should throw NPE)
        try {
            Arrays.mismatch(null as ByteArray, 0, 3, b, 0, 3)
            kotlin.test.fail("Expected NullPointerException")
        } catch (_: NullPointerException) { }
        try {
            Arrays.mismatch(a, 0, 3, null as ByteArray, 0, 3)
            kotlin.test.fail("Expected NullPointerException")
        } catch (_: NullPointerException) { }
        // IllegalArgumentException checks
        try {
            Arrays.mismatch(a, 2, 1, b, 0, 3)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            Arrays.mismatch(a, 0, 3, b, 2, 1)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        // Out of bounds
        try {
            Arrays.mismatch(a, 0, 4, b, 0, 3)
            kotlin.test.fail("Expected IndexOutOfBoundsException")
        } catch (_: IndexOutOfBoundsException) { }
        try {
            Arrays.mismatch(a, 0, 3, b, 0, 5)
            kotlin.test.fail("Expected IndexOutOfBoundsException")
        } catch (_: IndexOutOfBoundsException) { }
    }

    @Test
    fun testMismatchIntArrayWithNullsAndBounds() {
        val a = intArrayOf(5, 6, 7)
        val b = intArrayOf(5, 6, 7)
        // Null checks
        try {
            Arrays.mismatch(null as IntArray, 0, 3, b, 0, 3)
            kotlin.test.fail("Expected NullPointerException")
        } catch (_: NullPointerException) { }
        try {
            Arrays.mismatch(a, 0, 3, null as IntArray, 0, 3)
            kotlin.test.fail("Expected NullPointerException")
        } catch (_: NullPointerException) { }
        // IllegalArgumentExceptions
        try {
            Arrays.mismatch(a, 2, 1, b, 0, 3)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            Arrays.mismatch(a, 0, 3, b, 2, 1)
            kotlin.test.fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        // Out of bounds
        try {
            Arrays.mismatch(a, 0, 4, b, 0, 3)
            kotlin.test.fail("Expected IndexOutOfBoundsException")
        } catch (_: IndexOutOfBoundsException) { }
        try {
            Arrays.mismatch(a, 0, 3, b, 0, 5)
            kotlin.test.fail("Expected IndexOutOfBoundsException")
        } catch (_: IndexOutOfBoundsException) { }
    }

    @Test
    fun testMismatchEdgeCases() {
        // Both arrays empty, ranges empty
        assertEquals(-1, Arrays.mismatch(byteArrayOf(), 0, 0, byteArrayOf(), 0, 0))
        // a empty, b non-empty
        assertEquals(0, Arrays.mismatch(byteArrayOf(), 0, 0, byteArrayOf(1), 0, 1))
        // b empty, a non-empty
        assertEquals(0, Arrays.mismatch(byteArrayOf(1), 0, 1, byteArrayOf(), 0, 0))
        // Single element, mismatch
        assertEquals(0, Arrays.mismatch(byteArrayOf(2), 0, 1, byteArrayOf(3), 0, 1))
        // Single element, equal
        assertEquals(-1, Arrays.mismatch(byteArrayOf(42), 0, 1, byteArrayOf(42), 0, 1))
    }

    // Tests for Arrays.fill

    @Test
    fun testFillByteArray() {
        val arr = ByteArray(5) { it.toByte() }
        Arrays.fill(arr, 1, 4, 9)
        assertContentEquals(byteArrayOf(0, 9, 9, 9, 4), arr)
        Arrays.fill(arr, 7)
        assertContentEquals(byteArrayOf(7, 7, 7, 7, 7), arr)
    }

    @Test
    fun testFillShortArray() {
        val arr = ShortArray(4) { it.toShort() }
        Arrays.fill(arr, 0, 2, 5)
        assertContentEquals(shortArrayOf(5, 5, 2, 3), arr)
        Arrays.fill(arr, 8)
        assertContentEquals(shortArrayOf(8, 8, 8, 8), arr)
    }

    @Test
    fun testFillIntArray() {
        val arr = IntArray(6) { it }
        Arrays.fill(arr, 2, 5, 42)
        assertContentEquals(intArrayOf(0, 1, 42, 42, 42, 5), arr)
        Arrays.fill(arr, 99)
        assertContentEquals(IntArray(6) { 99 }, arr)
    }

    @Test
    fun testFillLongArray() {
        val arr = LongArray(3) { it.toLong() }
        Arrays.fill(arr, 1, 3, 123L)
        assertContentEquals(longArrayOf(0, 123, 123), arr)
        Arrays.fill(arr, -1L)
        assertContentEquals(longArrayOf(-1, -1, -1), arr)
    }

    @Test
    fun testFillFloatArray() {
        val arr = FloatArray(4) { it.toFloat() }
        Arrays.fill(arr, 1.5f)
        assertContentEquals(FloatArray(4) { 1.5f }, arr)
    }

    @Test
    fun testFillGenericArray() {
        val arr = arrayOfNulls<String>(3)
        Arrays.fill(arr, 0, 2, "foo")
        assertContentEquals(arrayOf("foo", "foo", null), arr)
        Arrays.fill(arr, "bar")
        assertContentEquals(arrayOf("bar", "bar", "bar"), arr)
    }

    @Test
    fun testFillArrayOfIntArray() {
        val arr = Array(3) { intArrayOf(1, 2) }
        val fillVal = intArrayOf(9, 9)
        Arrays.fill(arr, 1, 3, fillVal)
        assertTrue(arr[0] contentEquals intArrayOf(1, 2))
        assertTrue(arr[1] contentEquals fillVal)
        assertTrue(arr[2] contentEquals fillVal)
    }

    @Test
    fun testFillArrayOfByteArrayNullable() {
        val arr = arrayOfNulls<ByteArray>(2)
        val fillVal = byteArrayOf(7, 8)
        Arrays.fill(arr, 0, 2, fillVal)
        assertContentEquals(fillVal, arr[0])
        assertContentEquals(fillVal, arr[1])
    }
}
