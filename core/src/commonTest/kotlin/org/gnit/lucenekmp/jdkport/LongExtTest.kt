package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.util.getLogger
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.test.BeforeTest
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue



class LongExtTest {

    private val logger = getLogger()

    @BeforeTest
    fun setup() {
        configureTestLogging()
    }

    @Test
    fun testCompare() {
        assertEquals(0, Long.compare(123456789L, 123456789L))
        assertTrue(Long.compare(123456789L, 987654321L) < 0)
        assertTrue(Long.compare(987654321L, 123456789L) > 0)
    }

    @Test
    fun testToBinaryString() {
        val value = 10L
        val binaryString = Long.toBinaryString(value)
        assertEquals("1010", binaryString)
    }

    @Test
    fun testToUnsignedString() {
        val value = -1L
        val unsignedString = Long.toUnsignedString(value)
        assertEquals("18446744073709551615", unsignedString)
    }

    @Test
    fun testToHexString() {
        val value = 255L
        val hexString = Long.toHexString(value)
        assertEquals("ff", hexString)
    }

    @Test
    fun testNumberOfLeadingZeros() {
        val value = 0b0000_1000L
        val leadingZeros = Long.numberOfLeadingZeros(value)
        assertEquals(60, leadingZeros)
    }

    @Test
    fun testNumberOfTrailingZeros() {
        val value = 0b1000_0000L
        val trailingZeros = Long.numberOfTrailingZeros(value)
        assertEquals(7, trailingZeros)
    }

    @Test
    fun testBitCount() {
        val value = 0b1010_1010L
        val bitCount = Long.bitCount(value)
        assertEquals(4, bitCount)
    }

    @Test
    fun testRotateLeft() {
        val value = 0b0001_0000L
        val rotated = Long.rotateLeft(value, 2)
        assertEquals(0b0100_0000L, rotated)
    }

    @Test
    fun testRotateRight() {
        val value = 0b0001_0000L
        val rotated = Long.rotateRight(value, 2)
        assertEquals(0b0000_0100L, rotated)
    }

    @Test
    fun testReverseBytes() {
        val value = 0x0102030405060708L
        val reversed = Long.reverseBytes(value)
        assertEquals(0x0807060504030201L, reversed)
    }

    @Test
    fun testPerfBitOpsProbe() {
        val values = LongArray(1_000) { i -> (i.toLong() * -7046029254386353131L) + -3335678366873096957L }
        val iterations = 500_000

        val baselineMark = TimeSource.Monotonic.markNow()
        var baseline = 0L
        repeat(iterations) { i ->
            val v = values[i % values.size]
            baseline += baselineNumberOfLeadingZeros(v).toLong()
            baseline += baselineNumberOfTrailingZeros(v).toLong()
            baseline += baselineBitCount(v).toLong()
        }
        val baselineMs = baselineMark.elapsedNow().inWholeMilliseconds

        val optimizedMark = TimeSource.Monotonic.markNow()
        var optimized = 0L
        repeat(iterations) { i ->
            val v = values[i % values.size]
            optimized += Long.numberOfLeadingZeros(v).toLong()
            optimized += Long.numberOfTrailingZeros(v).toLong()
            optimized += Long.bitCount(v).toLong()
        }
        val optimizedMs = optimizedMark.elapsedNow().inWholeMilliseconds

        assertEquals(baseline, optimized)
        logger.debug {
            "perf:LongExt bitOps iterations=$iterations baselineMs=$baselineMs optimizedMs=$optimizedMs checksum=$optimized"
        }
    }

    private fun baselineNumberOfLeadingZeros(i: Long): Int {
        val x = (i ushr 32).toInt()
        return if (x == 0) 32 + Int.numberOfLeadingZeros(i.toInt()) else Int.numberOfLeadingZeros(x)
    }

    private fun baselineNumberOfTrailingZeros(i: Long): Int {
        val x = i.toInt()
        return if (x == 0) 32 + Int.numberOfTrailingZeros((i ushr 32).toInt()) else Int.numberOfTrailingZeros(x)
    }

    private fun baselineBitCount(i: Long): Int {
        var x = i
        x = x - ((x ushr 1) and 0x5555555555555555L)
        x = (x and 0x3333333333333333L) + ((x ushr 2) and 0x3333333333333333L)
        x = (x + (x ushr 4)) and 0x0f0f0f0f0f0f0f0fL
        x += x ushr 8
        x += x ushr 16
        x += x ushr 32
        return (x and 0x7f).toInt()
    }
}
