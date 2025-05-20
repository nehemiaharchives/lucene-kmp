package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MathTest {

    @Test
    fun testToIntExact() {
        assertEquals(123, Math.toIntExact(123L))
        assertEquals(Int.MAX_VALUE, Math.toIntExact(Int.MAX_VALUE.toLong()))
        assertEquals(Int.MIN_VALUE, Math.toIntExact(Int.MIN_VALUE.toLong()))
        assertFailsWith<ArithmeticException> { Math.toIntExact(Int.MAX_VALUE.toLong() + 1) }
        assertFailsWith<ArithmeticException> { Math.toIntExact(Int.MIN_VALUE.toLong() - 1) }
    }

    @Test
    fun testAddExactInt() {
        assertEquals(5, Math.addExact(2, 3))
        assertEquals(Int.MAX_VALUE, Math.addExact(Int.MAX_VALUE - 1, 1))
        assertFailsWith<ArithmeticException> { Math.addExact(Int.MAX_VALUE, 1) }
        assertFailsWith<ArithmeticException> { Math.addExact(Int.MIN_VALUE, -1) }
    }

    @Test
    fun testAddExactLong() {
        assertEquals(5L, Math.addExact(2L, 3L))
        assertEquals(Long.MAX_VALUE, Math.addExact(Long.MAX_VALUE - 1, 1))
        assertFailsWith<ArithmeticException> { Math.addExact(Long.MAX_VALUE, 1) }
        assertFailsWith<ArithmeticException> { Math.addExact(Long.MIN_VALUE, -1) }
    }

    @Test
    fun testMultiplyExact() {
        assertEquals(6, Math.multiplyExact(2, 3))
        assertEquals(0, Math.multiplyExact(0, 100))
        assertFailsWith<ArithmeticException> { Math.multiplyExact(Int.MAX_VALUE, 2) }
        assertFailsWith<ArithmeticException> { Math.multiplyExact(Int.MIN_VALUE, 2) }
    }

    @Test
    fun testRound() {
        assertEquals(2, Math.round(1.5f))
        assertEquals(-2, Math.round(-1.5f))
        assertEquals(0, Math.round(0.0f))
        assertEquals(Int.MAX_VALUE, Math.round(Float.POSITIVE_INFINITY))
        assertEquals(Int.MIN_VALUE, Math.round(Float.NEGATIVE_INFINITY))
        assertEquals(0, Math.round(Float.NaN))
    }

    @Test
    fun testFloorMod() {
        assertEquals(1L, Math.floorMod(10L, 3L))
        assertEquals(2L, Math.floorMod(-4L, 3L))
        assertEquals(0L, Math.floorMod(9L, 3L))
        assertEquals(2L, Math.floorMod(2L, 3L))
    }

    @Test
    fun testNextUp() {
        val f = 1.0f
        val next = Math.nextUp(f)
        assertTrue(next > f)
        assertEquals(Float.POSITIVE_INFINITY, Math.nextUp(Float.POSITIVE_INFINITY))
        assertEquals(Float.NaN, Math.nextUp(Float.NaN))
        assertEquals(Float.MIN_VALUE, Math.nextUp(0.0f))
        assertEquals(Float.MIN_VALUE, Math.nextUp(-0.0f))
    }

    @Test
    fun testNextDown() {
        val f = 1.0f
        val next = Math.nextDown(f)
        assertTrue(next < f)
        assertEquals(Float.NEGATIVE_INFINITY, Math.nextDown(Float.NEGATIVE_INFINITY))
        assertEquals(Float.NaN, Math.nextDown(Float.NaN))
        assertEquals(-Float.MIN_VALUE, Math.nextDown(0.0f))
        assertEquals(-Float.MIN_VALUE, Math.nextDown(-0.0f))
    }

    @Test
    fun testScalb() {
        assertEquals(8.0, Math.scalb(1.0, 3))
        assertEquals(0.5, Math.scalb(1.0, -1))
        assertEquals(0.0, Math.scalb(0.0, 100))
        assertTrue(Math.scalb(Double.NaN, 5).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, Math.scalb(Double.POSITIVE_INFINITY, 2))
        assertEquals(Double.NEGATIVE_INFINITY, Math.scalb(Double.NEGATIVE_INFINITY, 2))
    }

    @Test
    fun testGetExponent() {
        assertEquals(0, Math.getExponent(1.0))
        assertEquals(1, Math.getExponent(2.0))
        assertEquals(-1, Math.getExponent(0.5))
        assertEquals(Double.MAX_EXPONENT + 1, Math.getExponent(Double.POSITIVE_INFINITY))
        assertEquals(Double.MAX_EXPONENT + 1, Math.getExponent(Double.NaN))
        assertEquals(Double.MIN_EXPONENT - 1, Math.getExponent(0.0))
    }
}