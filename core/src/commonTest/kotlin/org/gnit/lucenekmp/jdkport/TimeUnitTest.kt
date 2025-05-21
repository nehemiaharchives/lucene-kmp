package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class TimeUnitTest {
    @Test
    fun testTimeUnitConversion() {
        assertEquals(1000L, TimeUnit.SECONDS.toMillis(1))
        assertEquals(1L, TimeUnit.MILLISECONDS.toSeconds(1000))
        assertEquals(60L, TimeUnit.MINUTES.toSeconds(1))
        logger.debug { "testTimeUnitConversion passed" }
    }

    @Test
    fun testToNanos() {
        assertEquals(1_000_000L, TimeUnit.MILLISECONDS.toNanos(1))
        assertEquals(1L, TimeUnit.NANOSECONDS.toNanos(1))
        assertEquals(0L, TimeUnit.SECONDS.toNanos(0))
    }

    @Test
    fun testToMicros() {
        assertEquals(1_000L, TimeUnit.MILLISECONDS.toMicros(1))
        assertEquals(1L, TimeUnit.MICROSECONDS.toMicros(1))
        assertEquals(0L, TimeUnit.SECONDS.toMicros(0))
    }

    @Test
    fun testToMillis() {
        assertEquals(1L, TimeUnit.MILLISECONDS.toMillis(1))
        assertEquals(1_000L, TimeUnit.SECONDS.toMillis(1))
        assertEquals(0L, TimeUnit.MINUTES.toMillis(0))
    }

    @Test
    fun testToSeconds() {
        assertEquals(1L, TimeUnit.SECONDS.toSeconds(1))
        assertEquals(60L, TimeUnit.MINUTES.toSeconds(1))
        assertEquals(0L, TimeUnit.MILLISECONDS.toSeconds(999))
    }

    @Test
    fun testToMinutes() {
        assertEquals(1L, TimeUnit.MINUTES.toMinutes(1))
        assertEquals(60L, TimeUnit.HOURS.toMinutes(1))
        assertEquals(0L, TimeUnit.SECONDS.toMinutes(59))
    }

    @Test
    fun testToHours() {
        assertEquals(1L, TimeUnit.HOURS.toHours(1))
        assertEquals(24L, TimeUnit.DAYS.toHours(1))
        assertEquals(0L, TimeUnit.MINUTES.toHours(59))
    }

    @Test
    fun testToDays() {
        assertEquals(1L, TimeUnit.DAYS.toDays(1))
        assertEquals(0L, TimeUnit.HOURS.toDays(23))
        assertEquals(2L, TimeUnit.HOURS.toDays(48))
    }

    @Test
    fun testConvertDuration() {
        assertEquals(1L, TimeUnit.SECONDS.convert(1.seconds))
        assertEquals(1000L, TimeUnit.MILLISECONDS.convert(1.seconds))
        assertEquals(1_000_000_000L, TimeUnit.NANOSECONDS.convert(1.seconds))
        assertEquals(1L, TimeUnit.MINUTES.convert(60.seconds))
        assertEquals(0L, TimeUnit.MINUTES.convert(59.seconds))
    }

    @Test
    fun testConvertBetweenUnits() {
        assertEquals(60_000L, TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES))
        assertEquals(1L, TimeUnit.MINUTES.convert(60, TimeUnit.SECONDS))
        assertEquals(0L, TimeUnit.SECONDS.convert(999, TimeUnit.MILLISECONDS))
        assertEquals(1L, TimeUnit.SECONDS.convert(1000, TimeUnit.MILLISECONDS))
    }

    @Test
    fun testOverflowAndUnderflow() {
        val max = Long.MAX_VALUE
        val min = Long.MIN_VALUE
        // Overflow
        assertEquals(max, TimeUnit.SECONDS.toMillis(max))
        assertEquals(min, TimeUnit.SECONDS.toMillis(min))
        // Underflow
        assertEquals(min, TimeUnit.MILLISECONDS.toNanos(min))
        assertEquals(max, TimeUnit.MILLISECONDS.toNanos(max))
    }

    @Test
    fun testCvtFunction() {
        // src == dst
        assertEquals(42L, TimeUnit.cvt(42L, 1000L, 1000L))
        // src < dst (should divide)
        assertEquals(2L, TimeUnit.cvt(2000L, 1000L, 1L))
        // src > dst (should multiply)
        assertEquals(2000L, TimeUnit.cvt(2L, 1L, 1000L))
        // Overflow
        assertEquals(Long.MAX_VALUE, TimeUnit.cvt(Long.MAX_VALUE, 1L, 1000L))
        // Underflow
        assertEquals(Long.MIN_VALUE, TimeUnit.cvt(Long.MIN_VALUE, 1L, 1000L))
    }
}
