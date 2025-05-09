package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloatExtTest {

    @Test
    fun testCompare() {
        assertEquals(0, Float.compare(123.456f, 123.456f))
        assertTrue(Float.compare(123.456f, 654.321f) < 0)
        assertTrue(Float.compare(654.321f, 123.456f) > 0)
    }

    @Test
    fun testIsNaN() {
        assertTrue(Float.isNaN(Float.NaN))
        assertTrue(!Float.isNaN(123.456f))
    }

    @Test
    fun testIsFinite() {
        assertTrue(Float.isFinite(123.456f))
        assertTrue(!Float.isFinite(Float.POSITIVE_INFINITY))
        assertTrue(!Float.isFinite(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun testIsInfinite() {
        assertTrue(Float.isInfinite(Float.POSITIVE_INFINITY))
        assertTrue(Float.isInfinite(Float.NEGATIVE_INFINITY))
        assertTrue(!Float.isInfinite(123.456f))
    }

    @Test
    fun testFloatToRawIntBits() {
        val value = 123.456f
        val bits = Float.floatToRawIntBits(value)
        assertEquals(value.toBits(), bits)
    }

    @Test
    fun testFloatToIntBits() {
        val value = Float.NaN
        val bits = Float.floatToIntBits(value)
        assertEquals(0x7fc00000, bits)
    }

    @Test
    fun testIntBitsToFloat() {
        val bits = 0x42f6e979
        val value = Float.intBitsToFloat(bits)
        assertEquals(123.456f, value)
    }
}
