package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ArraysTest {

    private val logger = KotlinLogging.logger {}

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

    @Test
    fun testMismatchByteArrayOffsetsAndLenIdentical() {
        val arr1 = byteArrayOf(1, 2, 3, 4)
        val arr2 = byteArrayOf(1, 2, 3, 4)
        assertEquals(-1, Arrays.mismatch(arr1, 0, arr2, 0, 4))

        val arr3 = byteArrayOf(0, 1, 2, 3, 4, 5)
        val arr4 = byteArrayOf(9, 1, 2, 3, 4, 8)
        assertEquals(-1, Arrays.mismatch(arr3, 1, arr4, 1, 4))
    }

    @Test
    fun testMismatchByteArrayOffsetsAndLenBeginning() {
        val arr1 = byteArrayOf(0, 1, 2)
        val arr2 = byteArrayOf(9, 1, 2)
        assertEquals(0, Arrays.mismatch(arr1, 0, arr2, 0, 3))
    }

    @Test
    fun testMismatchByteArrayOffsetsAndLenMiddle() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 9, 3)
        assertEquals(1, Arrays.mismatch(arr1, 0, arr2, 0, 3))
    }

    @Test
    fun testMismatchByteArrayOffsetsAndLenEnd() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 2, 9)
        assertEquals(2, Arrays.mismatch(arr1, 0, arr2, 0, 3))
    }

    @Test
    fun testMismatchByteArrayOffsetsAndLenCommonPartMatches() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 2, 3, 4)
        assertEquals(-1, Arrays.mismatch(arr1, 0, arr2, 0, 3))
    }

    @Test
    fun testMismatchByteArrayOffsetsAndLenEmptyLen() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 2, 3)
        // According to JDK, if len is 0, it returns -1 as there are no elements to compare.
        assertEquals(-1, Arrays.mismatch(arr1, 0, arr2, 0, 0))
    }

    @Test
    fun testMismatchByteArrayOffsetsAndLenWithOffsets() {
        val arr1 = byteArrayOf(0, 1, 2, 3, 4) // effective part for comparison: [1,2,3]
        val arr2 = byteArrayOf(0, 1, 2, 9, 4) // effective part for comparison: [1,2,9]
        // Mismatch is at index 2 of the effective parts (3 vs 9)
        assertEquals(2, Arrays.mismatch(arr1, 1, arr2, 1, 3))
    }
    
    @Test
    fun testMismatchByteArrayOffsetsAndLenRespectsLen() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 9, 0, 0) 
        // Compare only first 2 elements: [1,2] vs [1,9]. Mismatch at index 1.
        assertEquals(1, Arrays.mismatch(arr1, 0, arr2, 0, 2))
    }

    @Test
    fun testMismatchByteArrayRangesIdentical() {
        val arr1 = byteArrayOf(1, 2, 3, 4)
        val arr2 = byteArrayOf(1, 2, 3, 4)
        assertEquals(-1, Arrays.mismatch(arr1, 0, 4, arr2, 0, 4))

        val arr3 = byteArrayOf(0, 1, 2, 3, 4, 5) // Effective: [1,2,3,4]
        val arr4 = byteArrayOf(9, 1, 2, 3, 4, 8) // Effective: [1,2,3,4]
        assertEquals(-1, Arrays.mismatch(arr3, 1, 5, arr4, 1, 5))
    }

    @Test
    fun testMismatchByteArrayRangesBeginning() {
        val arr1 = byteArrayOf(0, 1, 2)
        val arr2 = byteArrayOf(9, 1, 2)
        assertEquals(0, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchByteArrayRangesMiddle() {
        // a = [1, 2, 3, 4], aFrom = 1, aTo = 4 -> effectiveA = [2, 3, 4]
        // b = [1, 2, 9, 4], bFrom = 1, bTo = 4 -> effectiveB = [2, 9, 4]
        // Mismatch at index 1 of effective arrays (3 vs 9)
        val arr1 = byteArrayOf(1, 2, 3, 4) 
        val arr2 = byteArrayOf(1, 2, 9, 4) 
        assertEquals(1, Arrays.mismatch(arr1, 1, 4, arr2, 1, 4))
    }
    
    @Test
    fun testMismatchByteArrayRangesEnd() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 2, 9)
        assertEquals(2, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchByteArrayRangesAPrefixOfB() {
        val arrA = byteArrayOf(1, 2)
        val arrB = byteArrayOf(1, 2, 3)
        // Compare [1,2] with [1,2,3]. Mismatch is at index 2 (length of arrA's range).
        assertEquals(2, Arrays.mismatch(arrA, 0, 2, arrB, 0, 3))
    }

    @Test
    fun testMismatchByteArrayRangesBPrefixOfA() {
        val arrA = byteArrayOf(1, 2, 3)
        val arrB = byteArrayOf(1, 2)
        // Compare [1,2,3] with [1,2]. Mismatch is at index 2 (length of arrB's range).
        assertEquals(2, Arrays.mismatch(arrA, 0, 3, arrB, 0, 2))
    }
    
    @Test
    fun testMismatchByteArrayRangesCompletelyDifferent() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(4, 5, 6)
        assertEquals(0, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchByteArrayRangesEmptyRanges() {
        val arr1 = byteArrayOf(1)
        val arr2 = byteArrayOf(2)
        // Both ranges are empty, length 0.
        assertEquals(-1, Arrays.mismatch(arr1, 0, 0, arr2, 0, 0))

        val arr3 = byteArrayOf(1)
        val arr4 = byteArrayOf(2, 3)
        // arr3 range empty, arr4 range has 1 element. Expected 0 as per JDK (lengths differ, mismatch at first possible point).
        assertEquals(0, Arrays.mismatch(arr3, 0, 0, arr4, 0, 1))
         // arr3 range has 1 element, arr4 range empty. Expected 0.
        assertEquals(0, Arrays.mismatch(arr4, 0, 1, arr3, 0, 0))
    }

    @Test
    fun testMismatchByteArrayRangesDifferentLengthsMatchUpToShorter() {
        val arrShorter = byteArrayOf(1, 2)
        val arrLonger = byteArrayOf(1, 2, 3)
        // Compare [1,2] with [1,2,3]. Mismatch is at index 2 (length of shorter range).
        assertEquals(2, Arrays.mismatch(arrShorter, 0, 2, arrLonger, 0, 3))
    }

    @Test
    fun testMismatchByteArrayRangesIllegalArgumentException() {
        val arr = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            Arrays.mismatch(arr, 2, 1, arr, 0, 1) // aFromIndex > aToIndex
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.mismatch(arr, 0, 1, arr, 2, 1) // bFromIndex > bToIndex
        }
    }

    @Test
    fun testMismatchByteArrayRangesArrayIndexOutOfBoundsException() {
        val arr = byteArrayOf(1, 2, 3)
        val goodArr = byteArrayOf(1,2,3,4)
        assertFailsWith<IndexOutOfBoundsException> { // Kotlin specific is IndexOutOfBoundsException
            Arrays.mismatch(arr, -1, 2, goodArr, 0, 2) // aFromIndex < 0
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(arr, 0, 4, goodArr, 0, 2) // aToIndex > a.size
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(goodArr, 0, 2, arr, -1, 2) // bFromIndex < 0
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(goodArr, 0, 2, arr, 0, 4) // bToIndex > b.size
        }
    }

    @Test
    fun testMismatchIntArrayRangesIdentical() {
        val arr1 = intArrayOf(1, 2, 3, 4)
        val arr2 = intArrayOf(1, 2, 3, 4)
        assertEquals(-1, Arrays.mismatch(arr1, 0, 4, arr2, 0, 4))

        val arr3 = intArrayOf(0, 1, 2, 3, 4, 5) // Effective: [1,2,3,4]
        val arr4 = intArrayOf(9, 1, 2, 3, 4, 8) // Effective: [1,2,3,4]
        assertEquals(-1, Arrays.mismatch(arr3, 1, 5, arr4, 1, 5))
    }

    @Test
    fun testMismatchIntArrayRangesBeginning() {
        val arr1 = intArrayOf(0, 1, 2)
        val arr2 = intArrayOf(9, 1, 2)
        assertEquals(0, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchIntArrayRangesMiddle() {
        val arr1 = intArrayOf(1, 2, 3, 4) 
        val arr2 = intArrayOf(1, 2, 9, 4) 
        assertEquals(1, Arrays.mismatch(arr1, 1, 4, arr2, 1, 4)) // Mismatch at index 1 of effective ranges [2,3,4] vs [2,9,4]
    }
    
    @Test
    fun testMismatchIntArrayRangesEnd() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 2, 9)
        assertEquals(2, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchIntArrayRangesAPrefixOfB() {
        val arrA = intArrayOf(1, 2)
        val arrB = intArrayOf(1, 2, 3)
        assertEquals(2, Arrays.mismatch(arrA, 0, 2, arrB, 0, 3))
    }

    @Test
    fun testMismatchIntArrayRangesBPrefixOfA() {
        val arrA = intArrayOf(1, 2, 3)
        val arrB = intArrayOf(1, 2)
        assertEquals(2, Arrays.mismatch(arrA, 0, 3, arrB, 0, 2))
    }
    
    @Test
    fun testMismatchIntArrayRangesCompletelyDifferent() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(4, 5, 6)
        assertEquals(0, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchIntArrayRangesEmptyRanges() {
        val arr1 = intArrayOf(1)
        val arr2 = intArrayOf(2)
        assertEquals(-1, Arrays.mismatch(arr1, 0, 0, arr2, 0, 0)) // Both empty

        val arr3 = intArrayOf(1)
        val arr4 = intArrayOf(2, 3)
        assertEquals(0, Arrays.mismatch(arr3, 0, 0, arr4, 0, 1)) // First empty, second not
        assertEquals(0, Arrays.mismatch(arr4, 0, 1, arr3, 0, 0)) // First not, second empty
    }

    @Test
    fun testMismatchIntArrayRangesDifferentLengthsMatchUpToShorter() {
        val arrShorter = intArrayOf(1, 2)
        val arrLonger = intArrayOf(1, 2, 3)
        assertEquals(2, Arrays.mismatch(arrShorter, 0, 2, arrLonger, 0, 3))
    }

    @Test
    fun testMismatchIntArrayRangesIllegalArgumentException() {
        val arr = intArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            Arrays.mismatch(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.mismatch(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testMismatchIntArrayRangesArrayIndexOutOfBoundsException() {
        val arr = intArrayOf(1, 2, 3)
        val goodArr = intArrayOf(1,2,3,4)
        assertFailsWith<IndexOutOfBoundsException> { 
            Arrays.mismatch(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testMismatchIntArrayOffsetsAndLenIdentical() {
        val arr1 = intArrayOf(1, 2, 3, 4)
        val arr2 = intArrayOf(1, 2, 3, 4)
        assertEquals(-1, Arrays.mismatch(arr1, 0, arr2, 0, 4))

        val arr3 = intArrayOf(0, 1, 2, 3, 4, 5)
        val arr4 = intArrayOf(9, 1, 2, 3, 4, 8)
        assertEquals(-1, Arrays.mismatch(arr3, 1, arr4, 1, 4))
    }

    @Test
    fun testMismatchIntArrayOffsetsAndLenBeginning() {
        val arr1 = intArrayOf(0, 1, 2)
        val arr2 = intArrayOf(9, 1, 2)
        assertEquals(0, Arrays.mismatch(arr1, 0, arr2, 0, 3))
    }

    @Test
    fun testMismatchIntArrayOffsetsAndLenMiddle() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 9, 3)
        assertEquals(1, Arrays.mismatch(arr1, 0, arr2, 0, 3))
    }

    @Test
    fun testMismatchIntArrayOffsetsAndLenEnd() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 2, 9)
        assertEquals(2, Arrays.mismatch(arr1, 0, arr2, 0, 3))
    }

    @Test
    fun testMismatchIntArrayOffsetsAndLenCommonPartMatches() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 2, 3, 4)
        assertEquals(-1, Arrays.mismatch(arr1, 0, arr2, 0, 3))
    }

    @Test
    fun testMismatchIntArrayOffsetsAndLenEmptyLen() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 2, 3)
        assertEquals(-1, Arrays.mismatch(arr1, 0, arr2, 0, 0))
    }

    @Test
    fun testMismatchIntArrayOffsetsAndLenWithOffsets() {
        val arr1 = intArrayOf(0, 1, 2, 3, 4) // effective part for comparison: [1,2,3]
        val arr2 = intArrayOf(0, 1, 2, 9, 4) // effective part for comparison: [1,2,9]
        assertEquals(2, Arrays.mismatch(arr1, 1, arr2, 1, 3)) // Mismatch at index 2 of effective parts
    }
    
    @Test
    fun testMismatchIntArrayOffsetsAndLenRespectsLen() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 9, 0, 0) 
        assertEquals(1, Arrays.mismatch(arr1, 0, arr2, 0, 2)) // Compare only first 2 elements
    }

    @Test
    fun testMismatchCharArrayRangesIdentical() {
        val arr1 = charArrayOf('a', 'b', 'c', 'd')
        val arr2 = charArrayOf('a', 'b', 'c', 'd')
        assertEquals(-1, Arrays.mismatch(arr1, 0, 4, arr2, 0, 4))

        val arr3 = charArrayOf('x', 'a', 'b', 'c', 'd', 'y') // Effective: ['a','b','c','d']
        val arr4 = charArrayOf('z', 'a', 'b', 'c', 'd', 'w') // Effective: ['a','b','c','d']
        assertEquals(-1, Arrays.mismatch(arr3, 1, 5, arr4, 1, 5))
    }

    @Test
    fun testMismatchCharArrayRangesBeginning() {
        val arr1 = charArrayOf('a', 'b', 'c')
        val arr2 = charArrayOf('x', 'b', 'c')
        assertEquals(0, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchCharArrayRangesMiddle() {
        val arr1 = charArrayOf('a', 'b', 'c', 'd') 
        val arr2 = charArrayOf('a', 'b', 'x', 'd') 
        // Effective arr1: ['b','c','d'], Effective arr2: ['b','x','d'] -> Mismatch at index 1
        assertEquals(1, Arrays.mismatch(arr1, 1, 4, arr2, 1, 4)) 
    }
    
    @Test
    fun testMismatchCharArrayRangesEnd() {
        val arr1 = charArrayOf('a', 'b', 'c')
        val arr2 = charArrayOf('a', 'b', 'x')
        assertEquals(2, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchCharArrayRangesAPrefixOfB() {
        val arrA = charArrayOf('a', 'b')
        val arrB = charArrayOf('a', 'b', 'c')
        assertEquals(2, Arrays.mismatch(arrA, 0, 2, arrB, 0, 3))
    }

    @Test
    fun testMismatchCharArrayRangesBPrefixOfA() {
        val arrA = charArrayOf('a', 'b', 'c')
        val arrB = charArrayOf('a', 'b')
        assertEquals(2, Arrays.mismatch(arrA, 0, 3, arrB, 0, 2))
    }
    
    @Test
    fun testMismatchCharArrayRangesCompletelyDifferent() {
        val arr1 = charArrayOf('a', 'b', 'c')
        val arr2 = charArrayOf('x', 'y', 'z')
        assertEquals(0, Arrays.mismatch(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testMismatchCharArrayRangesEmptyRanges() {
        val arr1 = charArrayOf('a')
        val arr2 = charArrayOf('b')
        assertEquals(-1, Arrays.mismatch(arr1, 0, 0, arr2, 0, 0)) // Both empty

        val arr3 = charArrayOf('a')
        val arr4 = charArrayOf('b', 'c')
        assertEquals(0, Arrays.mismatch(arr3, 0, 0, arr4, 0, 1)) // First empty, second not
        assertEquals(0, Arrays.mismatch(arr4, 0, 1, arr3, 0, 0)) // First not, second empty
    }

    @Test
    fun testMismatchCharArrayRangesDifferentLengthsMatchUpToShorter() {
        val arrShorter = charArrayOf('a', 'b')
        val arrLonger = charArrayOf('a', 'b', 'c')
        assertEquals(2, Arrays.mismatch(arrShorter, 0, 2, arrLonger, 0, 3))
    }

    @Test
    fun testMismatchCharArrayRangesIllegalArgumentException() {
        val arr = charArrayOf('a', 'b', 'c')
        assertFailsWith<IllegalArgumentException> {
            Arrays.mismatch(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.mismatch(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testMismatchCharArrayRangesArrayIndexOutOfBoundsException() {
        val arr = charArrayOf('a', 'b', 'c')
        val goodArr = charArrayOf('a','b','c','d')
        assertFailsWith<IndexOutOfBoundsException> { 
            Arrays.mismatch(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.mismatch(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testEqualsByteArrayRangesEqual() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 2, 3)
        assertTrue(Arrays.equals(arr1, 0, 3, arr2, 0, 3))

        val arr3 = byteArrayOf(0, 1, 2, 3, 4) // Effective: [1,2,3]
        val arr4 = byteArrayOf(9, 1, 2, 3, 8) // Effective: [1,2,3]
        assertTrue(Arrays.equals(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testEqualsByteArrayRangesContentMismatch() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 0, 3)
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testEqualsByteArrayRangesLengthMismatch() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 2)
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 2)) // a is longer
        assertFalse(Arrays.equals(arr2, 0, 2, arr1, 0, 3)) // b is longer
    }

    @Test
    fun testEqualsByteArrayRangesEmptyRanges() {
        val emptyArr1 = byteArrayOf()
        val emptyArr2 = byteArrayOf()
        assertTrue(Arrays.equals(emptyArr1, 0, 0, emptyArr2, 0, 0))

        val arr1 = byteArrayOf(1)
        // Test one empty, one not
        assertFalse(Arrays.equals(emptyArr1, 0, 0, arr1, 0, 1))
        assertFalse(Arrays.equals(arr1, 0, 1, emptyArr1, 0, 0))
        
        // Test two different non-empty arrays but with empty ranges selected
        val arr2 = byteArrayOf(1,2)
        val arr3 = byteArrayOf(3,4,5)
        assertTrue(Arrays.equals(arr2, 1, 1, arr3, 2, 2))
    }
    
    @Test
    fun testEqualsByteArrayRangesIllegalArgumentException() {
        val arr = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            Arrays.equals(arr, 2, 1, arr, 0, 1) // aFromIndex > aToIndex
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.equals(arr, 0, 1, arr, 2, 1) // bFromIndex > bToIndex
        }
    }

    @Test
    fun testEqualsByteArrayRangesArrayIndexOutOfBoundsException() {
        val arr = byteArrayOf(1, 2, 3)
        val goodArr = byteArrayOf(1,2,3,4)
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, -1, 2, goodArr, 0, 2) // aFromIndex < 0
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, 0, 4, goodArr, 0, 2) // aToIndex > a.size
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, -1, 2) // bFromIndex < 0
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, 0, 4) // bToIndex > b.size
        }
    }

    @Test
    fun testEqualsLongArrayRangesEqual() {
        val arr1 = longArrayOf(1L, 2L, 3L)
        val arr2 = longArrayOf(1L, 2L, 3L)
        assertTrue(Arrays.equals(arr1, 0, 3, arr2, 0, 3))

        val arr3 = longArrayOf(0L, 1L, 2L, 3L, 4L) // Effective: [1,2,3]
        val arr4 = longArrayOf(9L, 1L, 2L, 3L, 8L) // Effective: [1,2,3]
        assertTrue(Arrays.equals(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testEqualsLongArrayRangesContentMismatch() {
        val arr1 = longArrayOf(1L, 2L, 3L)
        val arr2 = longArrayOf(1L, 0L, 3L)
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testEqualsLongArrayRangesLengthMismatch() {
        val arr1 = longArrayOf(1L, 2L, 3L)
        val arr2 = longArrayOf(1L, 2L)
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 2))
        assertFalse(Arrays.equals(arr2, 0, 2, arr1, 0, 3))
    }

    @Test
    fun testEqualsLongArrayRangesEmptyRanges() {
        val emptyArr1 = longArrayOf()
        val emptyArr2 = longArrayOf()
        assertTrue(Arrays.equals(emptyArr1, 0, 0, emptyArr2, 0, 0))

        val arr1 = longArrayOf(1L)
        assertFalse(Arrays.equals(emptyArr1, 0, 0, arr1, 0, 1))
        assertFalse(Arrays.equals(arr1, 0, 1, emptyArr1, 0, 0))
        
        val arr2 = longArrayOf(1L,2L)
        val arr3 = longArrayOf(3L,4L,5L)
        assertTrue(Arrays.equals(arr2, 1, 1, arr3, 2, 2))
    }
    
    @Test
    fun testEqualsLongArrayRangesIllegalArgumentExceptionFromRequire() { // fromIndex > toIndex
        val arr = longArrayOf(1L, 2L, 3L)
        assertFailsWith<IllegalArgumentException> { 
            Arrays.equals(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> { 
            Arrays.equals(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testEqualsLongArrayRangesIndexOutOfBoundsException() { // Other bounds issues
        val arr = longArrayOf(1L, 2L, 3L)
        val goodArr = longArrayOf(1L,2L,3L,4L)
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testEqualsFloatArrayRangesEqual() {
        val arr1 = floatArrayOf(1.0f, 2.5f, 3.14f)
        val arr2 = floatArrayOf(1.0f, 2.5f, 3.14f)
        assertTrue(Arrays.equals(arr1, 0, 3, arr2, 0, 3))

        val arr3 = floatArrayOf(0.0f, 1.0f, 2.5f, 3.0f, 4.0f) // Effective: [1.0f, 2.5f, 3.0f]
        val arr4 = floatArrayOf(9.0f, 1.0f, 2.5f, 3.0f, 8.0f) // Effective: [1.0f, 2.5f, 3.0f]
        assertTrue(Arrays.equals(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testEqualsFloatArrayRangesContentMismatch() {
        val arr1 = floatArrayOf(1.0f, 2.5f, 3.14f)
        val arr2 = floatArrayOf(1.0f, 0.0f, 3.14f)
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testEqualsFloatArrayRangesLengthMismatch() {
        val arr1 = floatArrayOf(1.0f, 2.5f, 3.14f)
        val arr2 = floatArrayOf(1.0f, 2.5f)
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 2))
        assertFalse(Arrays.equals(arr2, 0, 2, arr1, 0, 3))
    }

    @Test
    fun testEqualsFloatArrayRangesEmptyRanges() {
        val emptyArr1 = floatArrayOf()
        val emptyArr2 = floatArrayOf()
        assertTrue(Arrays.equals(emptyArr1, 0, 0, emptyArr2, 0, 0))

        val arr1 = floatArrayOf(1.0f)
        assertFalse(Arrays.equals(emptyArr1, 0, 0, arr1, 0, 1))
        assertFalse(Arrays.equals(arr1, 0, 1, emptyArr1, 0, 0))
        
        val arr2 = floatArrayOf(1.0f, 2.0f)
        val arr3 = floatArrayOf(3.0f, 4.0f, 5.0f)
        assertTrue(Arrays.equals(arr2, 1, 1, arr3, 2, 2))
    }

    @Test
    fun testEqualsFloatArrayRangesNaNHandling() {
        // NaN should be equal to NaN
        val arr1NaN = floatArrayOf(Float.NaN)
        val arr2NaN = floatArrayOf(Float.NaN)
        assertTrue(Arrays.equals(arr1NaN, 0, 1, arr2NaN, 0, 1))

        val arr3 = floatArrayOf(1.0f, Float.NaN, 2.0f)
        val arr4 = floatArrayOf(1.0f, Float.NaN, 2.0f)
        assertTrue(Arrays.equals(arr3, 0, 3, arr4, 0, 3))

        val arr5 = floatArrayOf(1.0f, Float.NaN, 2.0f)
        val arr6 = floatArrayOf(1.0f, 1.0f, 2.0f) // Different from arr5
        assertFalse(Arrays.equals(arr5, 0, 3, arr6, 0, 3))
    }
    
    @Test
    fun testEqualsFloatArrayRangesZeroHandling() {
        // 0.0f should be equal to 0.0f
        val arr1Zero = floatArrayOf(0.0f)
        val arr2Zero = floatArrayOf(0.0f)
        assertTrue(Arrays.equals(arr1Zero, 0, 1, arr2Zero, 0, 1))

        // -0.0f should be equal to -0.0f
        val arr1NegZero = floatArrayOf(-0.0f)
        val arr2NegZero = floatArrayOf(-0.0f)
        assertTrue(Arrays.equals(arr1NegZero, 0, 1, arr2NegZero, 0, 1))

        // 0.0f should NOT be equal to -0.0f (due to floatToRawIntBits comparison)
        assertFalse(Arrays.equals(arr1Zero, 0, 1, arr1NegZero, 0, 1))
        
        val arrMix1 = floatArrayOf(1.0f, 0.0f, -0.0f)
        val arrMix2 = floatArrayOf(1.0f, 0.0f, -0.0f) // identical
        assertTrue(Arrays.equals(arrMix1, 0, 3, arrMix2, 0, 3))

        val arrMix3 = floatArrayOf(1.0f, 0.0f, 0.0f) // different from arrMix1
        assertFalse(Arrays.equals(arrMix1, 0, 3, arrMix3, 0, 3))
    }

    @Test
    fun testEqualsFloatArrayRangesIllegalArgumentException() {
        val arr = floatArrayOf(1.0f, 2.0f, 3.0f)
        assertFailsWith<IllegalArgumentException> {
            Arrays.equals(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.equals(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testEqualsFloatArrayRangesArrayIndexOutOfBoundsException() {
        val arr = floatArrayOf(1.0f, 2.0f, 3.0f)
        val goodArr = floatArrayOf(1.0f,2.0f,3.0f,4.0f)
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testEqualsCharArrayRangesEqual() {
        val arr1 = charArrayOf('a', 'b', 'c')
        val arr2 = charArrayOf('a', 'b', 'c')
        assertTrue(Arrays.equals(arr1, 0, 3, arr2, 0, 3))

        val arr3 = charArrayOf('x', 'a', 'b', 'c', 'y') // Effective: ['a','b','c']
        val arr4 = charArrayOf('z', 'a', 'b', 'c', 'w') // Effective: ['a','b','c']
        assertTrue(Arrays.equals(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testEqualsCharArrayRangesContentMismatch() {
        val arr1 = charArrayOf('a', 'b', 'c')
        val arr2 = charArrayOf('a', 'x', 'c')
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testEqualsCharArrayRangesLengthMismatch() {
        val arr1 = charArrayOf('a', 'b', 'c')
        val arr2 = charArrayOf('a', 'b')
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 2))
        assertFalse(Arrays.equals(arr2, 0, 2, arr1, 0, 3))
    }

    @Test
    fun testEqualsCharArrayRangesEmptyRanges() {
        val emptyArr1 = charArrayOf()
        val emptyArr2 = charArrayOf()
        assertTrue(Arrays.equals(emptyArr1, 0, 0, emptyArr2, 0, 0))

        val arr1 = charArrayOf('a')
        assertFalse(Arrays.equals(emptyArr1, 0, 0, arr1, 0, 1))
        assertFalse(Arrays.equals(arr1, 0, 1, emptyArr1, 0, 0))
        
        val arr2 = charArrayOf('a','b')
        val arr3 = charArrayOf('c','d','e')
        assertTrue(Arrays.equals(arr2, 1, 1, arr3, 2, 2))
    }
    
    @Test
    fun testEqualsCharArrayRangesIllegalArgumentException() {
        val arr = charArrayOf('a', 'b', 'c')
        // This version of equals in Arrays.kt has direct checks
        assertFailsWith<IllegalArgumentException> {
            Arrays.equals(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.equals(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testEqualsCharArrayRangesIndexOutOfBoundsException() {
        val arr = charArrayOf('a', 'b', 'c')
        val goodArr = charArrayOf('a','b','c','d')
        // This version of equals in Arrays.kt has direct checks
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testEqualsIntArrayRangesEqual() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 2, 3)
        assertTrue(Arrays.equals(arr1, 0, 3, arr2, 0, 3))

        val arr3 = intArrayOf(0, 1, 2, 3, 4) // Effective: [1,2,3]
        val arr4 = intArrayOf(9, 1, 2, 3, 8) // Effective: [1,2,3]
        assertTrue(Arrays.equals(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testEqualsIntArrayRangesContentMismatch() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 0, 3)
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 3))
    }

    @Test
    fun testEqualsIntArrayRangesLengthMismatch() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 2)
        assertFalse(Arrays.equals(arr1, 0, 3, arr2, 0, 2))
        assertFalse(Arrays.equals(arr2, 0, 2, arr1, 0, 3))
    }

    @Test
    fun testEqualsIntArrayRangesEmptyRanges() {
        val emptyArr1 = intArrayOf()
        val emptyArr2 = intArrayOf()
        assertTrue(Arrays.equals(emptyArr1, 0, 0, emptyArr2, 0, 0))

        val arr1 = intArrayOf(1)
        assertFalse(Arrays.equals(emptyArr1, 0, 0, arr1, 0, 1))
        assertFalse(Arrays.equals(arr1, 0, 1, emptyArr1, 0, 0))
        
        val arr2 = intArrayOf(1,2)
        val arr3 = intArrayOf(3,4,5)
        assertTrue(Arrays.equals(arr2, 1, 1, arr3, 2, 2))
    }
    
    @Test
    fun testEqualsIntArrayRangesIllegalArgumentException() {
        val arr = intArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            Arrays.equals(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.equals(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testEqualsIntArrayRangesArrayIndexOutOfBoundsException() {
        val arr = intArrayOf(1, 2, 3)
        val goodArr = intArrayOf(1,2,3,4)
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.equals(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testCompareIntArrayRangesEqual() {
        val arr1 = intArrayOf(1, 2, 3)
        val arr2 = intArrayOf(1, 2, 3)
        assertEquals(0, Arrays.compare(arr1, 0, 3, arr2, 0, 3))

        val arr3 = intArrayOf(0, 1, 2, 3, 4) // Effective: [1,2,3]
        val arr4 = intArrayOf(9, 1, 2, 3, 8) // Effective: [1,2,3]
        assertEquals(0, Arrays.compare(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testCompareIntArrayRangesALessThanB() {
        val arrA1 = intArrayOf(1, 2, 3)
        val arrB1 = intArrayOf(1, 2, 4) // Mismatch at end
        assertTrue(Arrays.compare(arrA1, 0, 3, arrB1, 0, 3) < 0)

        val arrA2 = intArrayOf(1, 1, 3) // Mismatch in middle
        val arrB2 = intArrayOf(1, 2, 3)
        assertTrue(Arrays.compare(arrA2, 0, 3, arrB2, 0, 3) < 0)
        
        val arrA3 = intArrayOf(0, 2, 3) // Mismatch at start
        val arrB3 = intArrayOf(1, 2, 3)
        assertTrue(Arrays.compare(arrA3, 0, 3, arrB3, 0, 3) < 0)
    }

    @Test
    fun testCompareIntArrayRangesAGreaterThanB() {
        val arrA1 = intArrayOf(1, 2, 4) // Mismatch at end
        val arrB1 = intArrayOf(1, 2, 3)
        assertTrue(Arrays.compare(arrA1, 0, 3, arrB1, 0, 3) > 0)

        val arrA2 = intArrayOf(1, 2, 3) // Mismatch in middle
        val arrB2 = intArrayOf(1, 1, 3)
        assertTrue(Arrays.compare(arrA2, 0, 3, arrB2, 0, 3) > 0)

        val arrA3 = intArrayOf(1, 2, 3) // Mismatch at start
        val arrB3 = intArrayOf(0, 2, 3)
        assertTrue(Arrays.compare(arrA3, 0, 3, arrB3, 0, 3) > 0)
    }
    
    @Test
    fun testCompareIntArrayRangesAPrefixOfB() {
        val arrA = intArrayOf(1, 2)
        val arrB = intArrayOf(1, 2, 3)
        // a is shorter and a prefix of b, so a < b
        assertTrue(Arrays.compare(arrA, 0, 2, arrB, 0, 3) < 0)
    }

    @Test
    fun testCompareIntArrayRangesBPrefixOfA() {
        val arrA = intArrayOf(1, 2, 3)
        val arrB = intArrayOf(1, 2)
        // b is shorter and a prefix of a, so a > b
        assertTrue(Arrays.compare(arrA, 0, 3, arrB, 0, 2) > 0)
    }

    @Test
    fun testCompareIntArrayRangesEmptyRanges() {
        val emptyArr = intArrayOf()
        val arr = intArrayOf(1)

        // Both empty
        assertEquals(0, Arrays.compare(emptyArr, 0, 0, emptyArr, 0, 0))
        
        // A empty, B not
        assertTrue(Arrays.compare(emptyArr, 0, 0, arr, 0, 1) < 0)

        // A not, B empty
        assertTrue(Arrays.compare(arr, 0, 1, emptyArr, 0, 0) > 0)

        // Empty ranges from non-empty arrays
        val arr2 = intArrayOf(1,2)
        val arr3 = intArrayOf(3,4,5)
        assertEquals(0, Arrays.compare(arr2, 1, 1, arr3, 2, 2)) // Both effectively empty
        assertTrue(Arrays.compare(arr2, 1, 1, arr3, 1, 2) < 0) // a empty, b not
    }

    @Test
    fun testCompareIntArrayRangesIllegalArgumentException() {
        val arr = intArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            Arrays.compare(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.compare(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testCompareIntArrayRangesArrayIndexOutOfBoundsException() {
        val arr = intArrayOf(1, 2, 3)
        val goodArr = intArrayOf(1,2,3,4)
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testCompareLongArrayRangesEqual() {
        val arr1 = longArrayOf(1L, 2L, 3L)
        val arr2 = longArrayOf(1L, 2L, 3L)
        assertEquals(0, Arrays.compare(arr1, 0, 3, arr2, 0, 3))

        val arr3 = longArrayOf(0L, 1L, 2L, 3L, 4L) // Effective: [1,2,3]
        val arr4 = longArrayOf(9L, 1L, 2L, 3L, 8L) // Effective: [1,2,3]
        assertEquals(0, Arrays.compare(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testCompareLongArrayRangesALessThanB() {
        val arrA1 = longArrayOf(1L, 2L, 3L)
        val arrB1 = longArrayOf(1L, 2L, 4L)
        assertTrue(Arrays.compare(arrA1, 0, 3, arrB1, 0, 3) < 0)

        val arrA2 = longArrayOf(1L, 1L, 3L)
        val arrB2 = longArrayOf(1L, 2L, 3L)
        assertTrue(Arrays.compare(arrA2, 0, 3, arrB2, 0, 3) < 0)
        
        val arrA3 = longArrayOf(0L, 2L, 3L)
        val arrB3 = longArrayOf(1L, 2L, 3L)
        assertTrue(Arrays.compare(arrA3, 0, 3, arrB3, 0, 3) < 0)
    }

    @Test
    fun testCompareLongArrayRangesAGreaterThanB() {
        val arrA1 = longArrayOf(1L, 2L, 4L)
        val arrB1 = longArrayOf(1L, 2L, 3L)
        assertTrue(Arrays.compare(arrA1, 0, 3, arrB1, 0, 3) > 0)

        val arrA2 = longArrayOf(1L, 2L, 3L)
        val arrB2 = longArrayOf(1L, 1L, 3L)
        assertTrue(Arrays.compare(arrA2, 0, 3, arrB2, 0, 3) > 0)

        val arrA3 = longArrayOf(1L, 2L, 3L)
        val arrB3 = longArrayOf(0L, 2L, 3L)
        assertTrue(Arrays.compare(arrA3, 0, 3, arrB3, 0, 3) > 0)
    }
    
    @Test
    fun testCompareLongArrayRangesAPrefixOfB() {
        val arrA = longArrayOf(1L, 2L)
        val arrB = longArrayOf(1L, 2L, 3L)
        assertTrue(Arrays.compare(arrA, 0, 2, arrB, 0, 3) < 0)
    }

    @Test
    fun testCompareLongArrayRangesBPrefixOfA() {
        val arrA = longArrayOf(1L, 2L, 3L)
        val arrB = longArrayOf(1L, 2L)
        assertTrue(Arrays.compare(arrA, 0, 3, arrB, 0, 2) > 0)
    }

    @Test
    fun testCompareLongArrayRangesEmptyRanges() {
        val emptyArr = longArrayOf()
        val arr = longArrayOf(1L)

        assertEquals(0, Arrays.compare(emptyArr, 0, 0, emptyArr, 0, 0))
        assertTrue(Arrays.compare(emptyArr, 0, 0, arr, 0, 1) < 0)
        assertTrue(Arrays.compare(arr, 0, 1, emptyArr, 0, 0) > 0)

        val arr2 = longArrayOf(1L,2L)
        val arr3 = longArrayOf(3L,4L,5L)
        assertEquals(0, Arrays.compare(arr2, 1, 1, arr3, 2, 2))
        assertTrue(Arrays.compare(arr2, 1, 1, arr3, 1, 2) < 0)
    }

    @Test
    fun testCompareLongArrayRangesIllegalArgumentException() {
        val arr = longArrayOf(1L, 2L, 3L)
        assertFailsWith<IllegalArgumentException> {
            Arrays.compare(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.compare(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testCompareLongArrayRangesArrayIndexOutOfBoundsException() {
        val arr = longArrayOf(1L, 2L, 3L)
        val goodArr = longArrayOf(1L,2L,3L,4L)
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testCompareCharArrayRangesEqual() {
        val arr1 = charArrayOf('a', 'b', 'c')
        val arr2 = charArrayOf('a', 'b', 'c')
        assertEquals(0, Arrays.compare(arr1, 0, 3, arr2, 0, 3))

        val arr3 = charArrayOf('x', 'a', 'b', 'c', 'y') // Effective: ['a','b','c']
        val arr4 = charArrayOf('z', 'a', 'b', 'c', 'w') // Effective: ['a','b','c']
        assertEquals(0, Arrays.compare(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testCompareCharArrayRangesALessThanB() {
        val arrA1 = charArrayOf('a', 'b', 'c')
        val arrB1 = charArrayOf('a', 'b', 'd')
        assertTrue(Arrays.compare(arrA1, 0, 3, arrB1, 0, 3) < 0)

        val arrA2 = charArrayOf('a', 'a', 'c')
        val arrB2 = charArrayOf('a', 'b', 'c')
        assertTrue(Arrays.compare(arrA2, 0, 3, arrB2, 0, 3) < 0)
        
        val arrA3 = charArrayOf('a', 'b', 'c')
        val arrB3 = charArrayOf('b', 'b', 'c')
        assertTrue(Arrays.compare(arrA3, 0, 3, arrB3, 0, 3) < 0)
    }

    @Test
    fun testCompareCharArrayRangesAGreaterThanB() {
        val arrA1 = charArrayOf('a', 'b', 'd')
        val arrB1 = charArrayOf('a', 'b', 'c')
        assertTrue(Arrays.compare(arrA1, 0, 3, arrB1, 0, 3) > 0)

        val arrA2 = charArrayOf('a', 'b', 'c')
        val arrB2 = charArrayOf('a', 'a', 'c')
        assertTrue(Arrays.compare(arrA2, 0, 3, arrB2, 0, 3) > 0)

        val arrA3 = charArrayOf('b', 'b', 'c')
        val arrB3 = charArrayOf('a', 'b', 'c')
        assertTrue(Arrays.compare(arrA3, 0, 3, arrB3, 0, 3) > 0)
    }
    
    @Test
    fun testCompareCharArrayRangesAPrefixOfB() {
        val arrA = charArrayOf('a', 'b')
        val arrB = charArrayOf('a', 'b', 'c')
        assertTrue(Arrays.compare(arrA, 0, 2, arrB, 0, 3) < 0)
    }

    @Test
    fun testCompareCharArrayRangesBPrefixOfA() {
        val arrA = charArrayOf('a', 'b', 'c')
        val arrB = charArrayOf('a', 'b')
        assertTrue(Arrays.compare(arrA, 0, 3, arrB, 0, 2) > 0)
    }

    @Test
    fun testCompareCharArrayRangesEmptyRanges() {
        val emptyArr = charArrayOf()
        val arr = charArrayOf('a')

        assertEquals(0, Arrays.compare(emptyArr, 0, 0, emptyArr, 0, 0))
        assertTrue(Arrays.compare(emptyArr, 0, 0, arr, 0, 1) < 0)
        assertTrue(Arrays.compare(arr, 0, 1, emptyArr, 0, 0) > 0)

        val arr2 = charArrayOf('a','b')
        val arr3 = charArrayOf('c','d','e')
        assertEquals(0, Arrays.compare(arr2, 1, 1, arr3, 2, 2))
        assertTrue(Arrays.compare(arr2, 1, 1, arr3, 1, 2) < 0)
    }

    @Test
    fun testCompareCharArrayRangesIllegalArgumentException() {
        val arr = charArrayOf('a', 'b', 'c')
        assertFailsWith<IllegalArgumentException> {
            Arrays.compare(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.compare(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testCompareCharArrayRangesArrayIndexOutOfBoundsException() {
        val arr = charArrayOf('a', 'b', 'c')
        val goodArr = charArrayOf('a','b','c','d')
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compare(goodArr, 0, 2, arr, 0, 4) 
        }
    }

    @Test
    fun testCompareUnsignedByteArrayRangesEqual() {
        val arr1 = byteArrayOf(1, 2, 3)
        val arr2 = byteArrayOf(1, 2, 3)
        assertEquals(0, Arrays.compareUnsigned(arr1, 0, 3, arr2, 0, 3))

        val arr3 = byteArrayOf(0, 100, -128, 3, 4) // Effective: [100, -128 (0x80), 3]
        val arr4 = byteArrayOf(9, 100, -128, 3, 8) // Effective: [100, -128 (0x80), 3]
        assertEquals(0, Arrays.compareUnsigned(arr3, 1, 4, arr4, 1, 4))
    }

    @Test
    fun testCompareUnsignedByteArrayRangesALessThanB() {
        // Standard positive values
        val arrA1 = byteArrayOf(10, 20, 30)
        val arrB1 = byteArrayOf(10, 20, 40)
        assertTrue(Arrays.compareUnsigned(arrA1, 0, 3, arrB1, 0, 3) < 0)

        // Test unsigned: 0x00 (0) vs 0x80 (128). 0 < 128
        val arrA2 = byteArrayOf(0)
        val arrB2 = byteArrayOf(-128) // 0x80
        assertTrue(Arrays.compareUnsigned(arrA2, 0, 1, arrB2, 0, 1) < 0, "0x00 should be less than 0x80 (unsigned)")
        
        // Test unsigned: 0x7F (127) vs 0x80 (128). 127 < 128
        val arrA3 = byteArrayOf(127) // 0x7F
        val arrB3 = byteArrayOf(-128) // 0x80
        assertTrue(Arrays.compareUnsigned(arrA3, 0, 1, arrB3, 0, 1) < 0, "0x7F should be less than 0x80 (unsigned)")
    }

    @Test
    fun testCompareUnsignedByteArrayRangesAGreaterThanB() {
        // Standard positive values
        val arrA1 = byteArrayOf(10, 20, 40)
        val arrB1 = byteArrayOf(10, 20, 30)
        assertTrue(Arrays.compareUnsigned(arrA1, 0, 3, arrB1, 0, 3) > 0)

        // Test unsigned: 0xFF (255) vs 0x00 (0). 255 > 0
        val arrA2 = byteArrayOf(-1) // 0xFF
        val arrB2 = byteArrayOf(0)  // 0x00
        assertTrue(Arrays.compareUnsigned(arrA2, 0, 1, arrB2, 0, 1) > 0, "0xFF should be greater than 0x00 (unsigned)")

        // Test unsigned: 0xFF (255) vs 0xFE (254)
        val arrA3 = byteArrayOf(-1) // 0xFF
        val arrB3 = byteArrayOf(-2) // 0xFE
        assertTrue(Arrays.compareUnsigned(arrA3, 0, 1, arrB3, 0, 1) > 0, "0xFF should be greater than 0xFE (unsigned)")

        // Test unsigned: 0x80 (128) vs 0x7F (127)
        val arrA4 = byteArrayOf(-128) // 0x80
        val arrB4 = byteArrayOf(127)  // 0x7F
        assertTrue(Arrays.compareUnsigned(arrA4, 0, 1, arrB4, 0, 1) > 0, "0x80 should be greater than 0x7F (unsigned)")
    }
    
    @Test
    fun testCompareUnsignedByteArrayRangesAPrefixOfB() {
        val arrA = byteArrayOf(10, 20)
        val arrB = byteArrayOf(10, 20, 30)
        assertTrue(Arrays.compareUnsigned(arrA, 0, 2, arrB, 0, 3) < 0)
    }

    @Test
    fun testCompareUnsignedByteArrayRangesBPrefixOfA() {
        val arrA = byteArrayOf(10, 20, 30)
        val arrB = byteArrayOf(10, 20)
        assertTrue(Arrays.compareUnsigned(arrA, 0, 3, arrB, 0, 2) > 0)
    }

    @Test
    fun testCompareUnsignedByteArrayRangesEmptyRanges() {
        val emptyArr = byteArrayOf()
        val arr = byteArrayOf(1)

        assertEquals(0, Arrays.compareUnsigned(emptyArr, 0, 0, emptyArr, 0, 0))
        assertTrue(Arrays.compareUnsigned(emptyArr, 0, 0, arr, 0, 1) < 0)
        assertTrue(Arrays.compareUnsigned(arr, 0, 1, emptyArr, 0, 0) > 0)
        
        val arr2 = byteArrayOf(1,2)
        val arr3 = byteArrayOf(3,4,5)
        assertEquals(0, Arrays.compareUnsigned(arr2, 1, 1, arr3, 2, 2))
        assertTrue(Arrays.compareUnsigned(arr2, 1, 1, arr3, 1, 2) < 0)
    }

    @Test
    fun testCompareUnsignedByteArrayRangesIllegalArgumentException() {
        val arr = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            Arrays.compareUnsigned(arr, 2, 1, arr, 0, 1) 
        }
        assertFailsWith<IllegalArgumentException> {
            Arrays.compareUnsigned(arr, 0, 1, arr, 2, 1) 
        }
    }

    @Test
    fun testCompareUnsignedByteArrayRangesArrayIndexOutOfBoundsException() {
        val arr = byteArrayOf(1, 2, 3)
        val goodArr = byteArrayOf(1,2,3,4)
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compareUnsigned(arr, -1, 2, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compareUnsigned(arr, 0, 4, goodArr, 0, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compareUnsigned(goodArr, 0, 2, arr, -1, 2) 
        }
        assertFailsWith<IndexOutOfBoundsException> {
            Arrays.compareUnsigned(goodArr, 0, 2, arr, 0, 4) 
        }
    }
}
