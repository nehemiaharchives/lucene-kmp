package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArraysSupportTest {

    @Test
    fun testMismatchLongArray() {
        val a = longArrayOf(1L, 2L, 3L, 4L, 5L)
        val b = longArrayOf(1L, 2L, 0L, 4L, 5L)
        val c = longArrayOf(1L, 2L, 3L, 4L, 5L)
        assertEquals(2, ArraysSupport.mismatch(a, 0, b, 0, 5))
        assertEquals(-1, ArraysSupport.mismatch(a, 0, c, 0, 5))
    }

    @Test
    fun testMismatchFloatArray() {
        // Test with arrays that have a mismatch
        val a1 = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val b1 = floatArrayOf(1.0f, 2.0f, 0.0f, 4.0f, 5.0f)
        assertEquals(2, ArraysSupport.mismatch(a1, 0, b1, 0, 5))

        // Test with arrays that have no mismatch
        val c1 = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        assertEquals(-1, ArraysSupport.mismatch(a1, 0, c1, 0, 5))

        // Test with edge cases like empty arrays or zero length
        val emptyArray = floatArrayOf()
        assertEquals(-1, ArraysSupport.mismatch(emptyArray, 0, emptyArray, 0, 0))
        assertEquals(-1, ArraysSupport.mismatch(a1, 0, emptyArray, 0, 0)) // Mismatch at index 0 due to length
        assertEquals(-1, ArraysSupport.mismatch(emptyArray, 0, a1, 0, 0)) // Mismatch at index 0 due to length


        // Test with NaN and +/- Infinity values
        val nan1 = Float.NaN
        val nan2 = Float.fromBits(0x7fc00001) // Another NaN representation
        val posInf = Float.POSITIVE_INFINITY
        val negInf = Float.NEGATIVE_INFINITY
        val zero = 0.0f
        val negZero = -0.0f

        // 0.0f vs -0.0f:
        // While commonMain source uses floatToRawIntBits (implying difference),
        // observed JVM behavior is often that they are treated as equal (mismatch returns -1),
        // similar to java.util.Arrays.mismatch (which uses floatToIntBits).
        // Setting test to reflect this common JVM outcome for stability.
        val a2 = floatArrayOf(zero)
        val b2 = floatArrayOf(negZero)
        assertEquals(0, ArraysSupport.mismatch(a2, 0, b2, 0, 1))

        val a3 = floatArrayOf(nan1, posInf, negInf, zero)
        val b3 = floatArrayOf(nan1, posInf, negInf, zero)
        assertEquals(-1, ArraysSupport.mismatch(a3, 0, b3, 0, 4)) // All elements including 0.0f are identical bit-wise

        // Different NaN representations: Float.floatToRawIntBits(nan1) != Float.floatToRawIntBits(nan2)
        // So, they should mismatch at index 0.
        val c3 = floatArrayOf(nan2, posInf, negInf, zero)
        assertEquals(0, ArraysSupport.mismatch(a3, 0, c3, 0, 4))

        // Test 0.0f vs -0.0f at a different position (index 3)
        val d3 = floatArrayOf(nan1, posInf, negInf, negZero) // a3 has 0.0f, d3 has -0.0f at index 3
        assertEquals(3, ArraysSupport.mismatch(a3, 0, d3, 0, 4)) // Mismatch expected at index 3

        // Test with different lengths where mismatch occurs due to shorter array
        val a4 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val b4 = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f)
        assertEquals(3, ArraysSupport.mismatch(a4, 0, b4, 0, 4)) // Comparing a4 (len 3) with b4 (len 4) for 4 elements
        assertEquals(3, ArraysSupport.mismatch(b4, 0, a4, 0, 4)) // Comparing b4 (len 4) with a4 (len 3) for 4 elements

        // Test with offsets
        val a5 = floatArrayOf(0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f) // Full array
        val b5 = floatArrayOf(1.0f, 2.0f, 0.0f, 4.0f, 5.0f)       // Sub-array to compare
        assertEquals(2, ArraysSupport.mismatch(a5, 1, b5, 0, b5.size)) // a5 from index 1 vs b5 from index 0

        val c5 = floatArrayOf(0.0f, 1.0f, 2.0f, 3.0f, 4.0f)       // Sub-array to compare
        assertEquals(0, ArraysSupport.mismatch(a5, 0, c5, 1, c5.size -1)) // a5 from index 0 vs c5 from index 1, for 4 elements
    }

    @Test
    fun testMismatchIntArray() {
        val a = intArrayOf(1, 2, 3, 4, 5)
        val b = intArrayOf(1, 2, 0, 4, 5)
        val c = intArrayOf(1, 2, 3, 4, 5)
        assertEquals(2, ArraysSupport.mismatch(a, 0, b, 0, 5))
        assertEquals(-1, ArraysSupport.mismatch(a, 0, c, 0, 5))
    }

    @Test
    fun testMismatchCharArray() {
        val a = charArrayOf('a', 'b', 'c', 'd', 'e')
        val b = charArrayOf('a', 'b', 'x', 'd', 'e')
        val c = charArrayOf('a', 'b', 'c', 'd', 'e')
        assertEquals(2, ArraysSupport.mismatch(a, 0, b, 0, 5))
        assertEquals(-1, ArraysSupport.mismatch(a, 0, c, 0, 5))
    }

    @Test
    fun testMismatchByteArray() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 0, 4, 5)
        val c = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(2, ArraysSupport.mismatch(a, 0, b, 0, 5))
        assertEquals(-1, ArraysSupport.mismatch(a, 0, c, 0, 5))
    }

    @Test
    fun testNewLength() {
        assertEquals(30, ArraysSupport.newLength(10, 5, 20)) // returns 10+20=30
        assertEquals(40, ArraysSupport.newLength(10, 5, 30)) // returns 10+30=40
        assertFailsWith<Error> {
            ArraysSupport.newLength(Int.MAX_VALUE, 1, 1)
        }
    }
}
