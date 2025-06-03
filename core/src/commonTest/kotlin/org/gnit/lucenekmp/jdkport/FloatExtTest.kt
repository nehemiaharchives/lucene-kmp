package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.*

private val logger = KotlinLogging.logger {}

class FloatExtTest {
    @Test
    fun testCompare() {
        logger.debug { "Testing Float.compare" }
        assertEquals(0, Float.compare(0.0f, 0.0f))
        assertEquals(0, Float.compare(-0.0f, -0.0f))
        // According to JDK documentation, -0.0f is less than 0.0f
        assertEquals(-1, Float.compare(-0.0f, 0.0f))
        assertEquals(1, Float.compare(0.0f, -0.0f))

        assertEquals(-1, Float.compare(1.0f, 2.0f))
        assertEquals(1, Float.compare(2.0f, 1.0f))
        assertEquals(0, Float.compare(1.0f, 1.0f))

        assertEquals(-1, Float.compare(-2.0f, -1.0f))
        assertEquals(1, Float.compare(-1.0f, -2.0f))
        assertEquals(0, Float.compare(-1.0f, -1.0f))

        // NaN comparisons
        assertEquals(1, Float.compare(Float.NaN, 1.0f)) // NaN is greater than any non-NaN
        assertEquals(-1, Float.compare(1.0f, Float.NaN)) // Any non-NaN is less than NaN
        assertEquals(0, Float.compare(Float.NaN, Float.NaN)) // NaN is equal to NaN

        // Infinity comparisons
        assertEquals(0, Float.compare(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
        assertEquals(0, Float.compare(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY))
        assertEquals(1, Float.compare(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
        assertEquals(-1, Float.compare(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY))
        assertEquals(1, Float.compare(Float.POSITIVE_INFINITY, 1.0f))
        assertEquals(-1, Float.compare(Float.NEGATIVE_INFINITY, 1.0f))
        assertEquals(-1, Float.compare(1.0f, Float.POSITIVE_INFINITY))
        assertEquals(1, Float.compare(1.0f, Float.NEGATIVE_INFINITY))

        // Test with Float.MIN_VALUE and Float.MAX_VALUE
        assertEquals(-1, Float.compare(Float.MIN_VALUE, Float.MAX_VALUE))
        assertEquals(1, Float.compare(Float.MAX_VALUE, Float.MIN_VALUE))
        assertEquals(0, Float.compare(Float.MIN_VALUE, Float.MIN_VALUE))
        assertEquals(0, Float.compare(Float.MAX_VALUE, Float.MAX_VALUE))

        // Test comparison of NaN with infinities
        assertEquals(1, Float.compare(Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(1, Float.compare(Float.NaN, Float.NEGATIVE_INFINITY))
        assertEquals(-1, Float.compare(Float.POSITIVE_INFINITY, Float.NaN))
        assertEquals(-1, Float.compare(Float.NEGATIVE_INFINITY, Float.NaN))
    }

    @Test
    fun testIsNaN() {
        logger.debug { "Testing Float.isNaN" }
        assertTrue(Float.isNaN(Float.NaN))
        assertFalse(Float.isNaN(0.0f))
        assertFalse(Float.isNaN(-0.0f))
        assertFalse(Float.isNaN(1.0f))
        assertFalse(Float.isNaN(-1.0f))
        assertFalse(Float.isNaN(Float.POSITIVE_INFINITY))
        assertFalse(Float.isNaN(Float.NEGATIVE_INFINITY))
        assertFalse(Float.isNaN(Float.MAX_VALUE))
        assertFalse(Float.isNaN(Float.MIN_VALUE))
    }

    @Test
    fun testIsFinite() {
        logger.debug { "Testing Float.isFinite" }
        assertTrue(Float.isFinite(0.0f))
        assertTrue(Float.isFinite(-0.0f))
        assertTrue(Float.isFinite(1.0f))
        assertTrue(Float.isFinite(-1.0f))
        assertTrue(Float.isFinite(Float.MAX_VALUE))
        assertTrue(Float.isFinite(Float.MIN_VALUE))
        //MIN_VALUE is the smallest positive non-zero value that a float can represent.
        //It is a finite number. The smallest (most negative) finite float is -Float.MAX_VALUE.
        assertTrue(Float.isFinite(-Float.MAX_VALUE))


        assertFalse(Float.isFinite(Float.NaN))
        assertFalse(Float.isFinite(Float.POSITIVE_INFINITY))
        assertFalse(Float.isFinite(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun testIsInfinite() {
        logger.debug { "Testing Float.isInfinite" }
        assertTrue(Float.isInfinite(Float.POSITIVE_INFINITY))
        assertTrue(Float.isInfinite(Float.NEGATIVE_INFINITY))

        assertFalse(Float.isInfinite(Float.NaN))
        assertFalse(Float.isInfinite(0.0f))
        assertFalse(Float.isInfinite(-0.0f))
        assertFalse(Float.isInfinite(1.0f))
        assertFalse(Float.isInfinite(-1.0f))
        assertFalse(Float.isInfinite(Float.MAX_VALUE))
        assertFalse(Float.isInfinite(Float.MIN_VALUE))
    }

    @Test
    fun testFloatToRawIntBits() {
        logger.debug { "Testing Float.floatToRawIntBits" }
        // Test with 0.0f
        assertEquals(0, Float.floatToRawIntBits(0.0f))
        // Test with -0.0f
        assertEquals(0x80000000.toInt(), Float.floatToRawIntBits(-0.0f))

        // Test with 1.0f
        assertEquals(0x3f800000, Float.floatToRawIntBits(1.0f))
        // Test with -1.0f
        assertEquals(0xbf800000.toInt(), Float.floatToRawIntBits(-1.0f))

        // Test with Float.NaN
        // In Kotlin/Native, Float.NaN.toRawBits() can be 0x7fc00000 or 0x7ff80000 or other NaN representation
        // So we check if the result is a NaN representation
        val nanBits = Float.floatToRawIntBits(Float.NaN)
        assertTrue((nanBits ushr 23) == 0xff && (nanBits and 0x007fffff) != 0, "NaN should have exponent all 1s and non-zero mantissa. Got: ${nanBits.toUInt().toString(16)}")

        // Test with Float.POSITIVE_INFINITY
        assertEquals(0x7f800000, Float.floatToRawIntBits(Float.POSITIVE_INFINITY))
        // Test with Float.NEGATIVE_INFINITY
        assertEquals(0xff800000.toInt(), Float.floatToRawIntBits(Float.NEGATIVE_INFINITY))

        // Test with Float.MAX_VALUE
        assertEquals(0x7f7fffff, Float.floatToRawIntBits(Float.MAX_VALUE))
        // Test with Float.MIN_VALUE (smallest positive normal)
        // MIN_VALUE in Kotlin is the smallest positive non-zero value, which is subnormal.
        // The bit representation for subnormal numbers can be tricky.
        // For Float.MIN_VALUE (2^-149), bits should be 0x00000001
         assertEquals(1, Float.floatToRawIntBits(Float.MIN_VALUE))


        // A non-canonical NaN
        val nonCanonicalNaN = Float.fromBits(0x7fC00001) // Exponent all 1s, non-zero mantissa
        assertEquals(0x7fC00001, Float.floatToRawIntBits(nonCanonicalNaN))
    }

    @Test
    fun testFloatToIntBits() {
        logger.debug { "Testing Float.floatToIntBits" }
        // Test with 0.0f
        assertEquals(0, Float.floatToIntBits(0.0f))
        // Test with -0.0f
        assertEquals(0x80000000.toInt(), Float.floatToIntBits(-0.0f))

        // Test with 1.0f
        assertEquals(0x3f800000, Float.floatToIntBits(1.0f))
        // Test with -1.0f
        assertEquals(0xbf800000.toInt(), Float.floatToIntBits(-1.0f))

        // Test with Float.NaN - should return canonical NaN
        assertEquals(0x7fc00000, Float.floatToIntBits(Float.NaN))
        // Test with another NaN representation
        val anotherNaN = Float.fromBits(0x7fC00001) // A non-canonical NaN
        assertEquals(0x7fc00000, Float.floatToIntBits(anotherNaN))


        // Test with Float.POSITIVE_INFINITY
        assertEquals(0x7f800000, Float.floatToIntBits(Float.POSITIVE_INFINITY))
        // Test with Float.NEGATIVE_INFINITY
        assertEquals(0xff800000.toInt(), Float.floatToIntBits(Float.NEGATIVE_INFINITY))

        // Test with Float.MAX_VALUE
        assertEquals(0x7f7fffff, Float.floatToIntBits(Float.MAX_VALUE))
        // Test with Float.MIN_VALUE (smallest positive normal)
        // MIN_VALUE in Kotlin is the smallest positive non-zero value (subnormal).
        // floatToIntBits should produce the same result as floatToRawIntBits for normal and subnormal numbers
        assertEquals(1, Float.floatToIntBits(Float.MIN_VALUE))
    }

    @Test
    fun testIntBitsToFloat() {
        logger.debug { "Testing Float.intBitsToFloat" }

        // Test with 0 (0.0f)
        assertEquals(0.0f, Float.intBitsToFloat(0))
        // Test with 0x80000000 (-0.0f)
        assertEquals(-0.0f, Float.intBitsToFloat(0x80000000.toInt()))
        assertTrue(Float.intBitsToFloat(0x80000000.toInt()).toBits() == (-0.0f).toBits())


        // Test with 0x3f800000 (1.0f)
        assertEquals(1.0f, Float.intBitsToFloat(0x3f800000))
        // Test with 0xbf800000 (-1.0f)
        assertEquals(-1.0f, Float.intBitsToFloat(0xbf800000.toInt()))

        // Test with canonical NaN (0x7fc00000)
        assertTrue(Float.isNaN(Float.intBitsToFloat(0x7fc00000)))
        // Test with another NaN representation (e.g., 0x7fc00001)
        assertTrue(Float.isNaN(Float.intBitsToFloat(0x7fc00001)))
        // Test with another NaN representation (e.g., 0xffc00001) - sign bit set for NaN
        assertTrue(Float.isNaN(Float.intBitsToFloat(0xffc00001.toInt())))


        // Test with Positive Infinity (0x7f800000)
        assertEquals(Float.POSITIVE_INFINITY, Float.intBitsToFloat(0x7f800000))
        // Test with Negative Infinity (0xff800000)
        assertEquals(Float.NEGATIVE_INFINITY, Float.intBitsToFloat(0xff800000.toInt()))

        // Test with Float.MAX_VALUE (0x7f7fffff)
        assertEquals(Float.MAX_VALUE, Float.intBitsToFloat(0x7f7fffff))
        // Test with Float.MIN_VALUE (smallest positive non-zero value, subnormal) (0x1)
        assertEquals(Float.MIN_VALUE, Float.intBitsToFloat(0x1))

        // Test a denormalized number (e.g. 0x00400000)
        // This represents 2^-126 * 2^-1 = 2^-127
        // Expected value: 2f.pow(-127) which is approximately 5.877471754111438E-39
        val denormalizedBits = 0x00400000
        val expectedDenormalized = Float.fromBits(denormalizedBits) // Use Kotlin's fromBits to get expected
        assertEquals(expectedDenormalized, Float.intBitsToFloat(denormalizedBits))

        // Test another denormalized number (e.g. 0x00000002)
        // This represents 2 * 2^-149 = 2^-148
        val denormalizedBits2 = 0x00000002
        val expectedDenormalized2 = Float.fromBits(denormalizedBits2)
        assertEquals(expectedDenormalized2, Float.intBitsToFloat(denormalizedBits2))

        // Test a normalized number close to MIN_NORMAL
        // MIN_NORMAL is 2^-126, its bit representation is 0x00800000
        val minNormalBits = 0x00800000
        val expectedMinNormal = Float.fromBits(minNormalBits)
        assertEquals(expectedMinNormal, Float.intBitsToFloat(minNormalBits))
    }

    @Test
    fun testProperties() {
        logger.debug { "Testing Float properties" }
        assertEquals(24, Float.PRECISION)
        assertEquals(-126, Float.MIN_EXPONENT)
        assertEquals(127, Float.MAX_EXPONENT)
        assertEquals(32, Float.SIZE)
    }
}
