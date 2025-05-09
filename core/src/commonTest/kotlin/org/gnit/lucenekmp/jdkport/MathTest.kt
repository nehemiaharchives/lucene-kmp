package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathTest {

    @Test
    fun testAbs() {
        assertEquals(10, Math.abs(-10))
        assertEquals(10, Math.abs(10))
        assertEquals(0, Math.abs(0))
    }

    @Test
    fun testMax() {
        assertEquals(10, Math.max(10, 5))
        assertEquals(10, Math.max(5, 10))
        assertEquals(10, Math.max(10, 10))
    }

    @Test
    fun testMin() {
        assertEquals(5, Math.min(10, 5))
        assertEquals(5, Math.min(5, 10))
        assertEquals(5, Math.min(5, 5))
    }

    @Test
    fun testPow() {
        assertEquals(100.0, Math.pow(10.0, 2.0))
        assertEquals(1.0, Math.pow(10.0, 0.0))
        assertEquals(0.1, Math.pow(10.0, -1.0))
    }

    @Test
    fun testSqrt() {
        assertEquals(10.0, Math.sqrt(100.0))
        assertEquals(0.0, Math.sqrt(0.0))
        assertTrue(Math.sqrt(-1.0).isNaN())
    }
}
