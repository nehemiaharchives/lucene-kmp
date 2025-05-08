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
