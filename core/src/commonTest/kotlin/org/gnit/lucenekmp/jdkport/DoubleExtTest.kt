package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoubleExtTest {

    @Test
    fun testDoubleToRawLongBits() {
        val value = 123.456
        val bits = Double.doubleToRawLongBits(value)
        assertEquals(value.toBits(), bits)
    }

    @Test
    fun testDoubleToLongBits() {
        val value = Double.NaN
        val bits = Double.doubleToLongBits(value)
        assertEquals(0x7ff8000000000000L, bits)
    }

    @Test
    fun testIsNaN() {
        assertTrue(Double.isNaN(Double.NaN))
        assertTrue(!Double.isNaN(123.456))
    }

    @Test
    fun testCompare() {
        assertEquals(0, Double.compare(123.456, 123.456))
        assertTrue(Double.compare(123.456, 654.321) < 0)
        assertTrue(Double.compare(654.321, 123.456) > 0)
    }

    @Test
    fun testLongBitsToDouble() {
        val bits = 0x405edd2f1a9fbe77L
        val value = Double.longBitsToDouble(bits)
        assertEquals(123.456, value)
    }

    @Test
    fun testIsFinite() {
        assertTrue(Double.isFinite(0.0))
        assertTrue(Double.isFinite(123.456))
        assertTrue(!Double.isFinite(Double.POSITIVE_INFINITY))
        assertTrue(!Double.isFinite(Double.NEGATIVE_INFINITY))
        assertTrue(!Double.isFinite(Double.NaN))
    }

}
