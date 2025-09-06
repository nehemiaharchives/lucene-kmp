package org.gnit.lucenekmp.document

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TestDateTools : LuceneTestCase() {

    @Test
    fun testStringToDate() {
        var d = DateTools.stringToDate("2004")
        assertEquals("2004-01-01 00:00:00:000", isoFormat(d))
        d = DateTools.stringToDate("20040705")
        assertEquals("2004-07-05 00:00:00:000", isoFormat(d))
        d = DateTools.stringToDate("200407050910")
        assertEquals("2004-07-05 09:10:00:000", isoFormat(d))
        d = DateTools.stringToDate("20040705091055990")
        assertEquals("2004-07-05 09:10:55:990", isoFormat(d))

        expectThrows(ParseException::class) { DateTools.stringToDate("97") }
        expectThrows(ParseException::class) { DateTools.stringToDate("200401011235009999") }
        expectThrows(ParseException::class) { DateTools.stringToDate("aaaa") }
    }

    @Test
    fun testStringtoTime() {
        var time = DateTools.stringToTime("197001010000")
        assertEquals(0L, time)
        val cal = LocalDateTime(1980, 2, 2, 11, 5, 0)
        time = DateTools.stringToTime("198002021105")
        assertEquals(cal.toInstant(TimeZone.UTC).toEpochMilliseconds(), time)
    }

    @Test
    fun testDateAndTimetoString() {
        var cal = LocalDateTime(2004, 2, 3, 22, 8, 56, 333_000_000)

        var dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.YEAR)
        assertEquals("2004", dateString)
        assertEquals("2004-01-01 00:00:00:000", isoFormat(DateTools.stringToDate(dateString)))

        dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.MONTH)
        assertEquals("200402", dateString)
        assertEquals("2004-02-01 00:00:00:000", isoFormat(DateTools.stringToDate(dateString)))

        dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.DAY)
        assertEquals("20040203", dateString)
        assertEquals("2004-02-03 00:00:00:000", isoFormat(DateTools.stringToDate(dateString)))

        dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.HOUR)
        assertEquals("2004020322", dateString)
        assertEquals("2004-02-03 22:00:00:000", isoFormat(DateTools.stringToDate(dateString)))

        dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.MINUTE)
        assertEquals("200402032208", dateString)
        assertEquals("2004-02-03 22:08:00:000", isoFormat(DateTools.stringToDate(dateString)))

        dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.SECOND)
        assertEquals("20040203220856", dateString)
        assertEquals("2004-02-03 22:08:56:000", isoFormat(DateTools.stringToDate(dateString)))

        dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.MILLISECOND)
        assertEquals("20040203220856333", dateString)
        assertEquals("2004-02-03 22:08:56:333", isoFormat(DateTools.stringToDate(dateString)))

        cal = LocalDateTime(1961, 3, 5, 23, 9, 51, 444_000_000)
        dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.MILLISECOND)
        assertEquals("19610305230951444", dateString)
        assertEquals("1961-03-05 23:09:51:444", isoFormat(DateTools.stringToDate(dateString)))

        dateString = DateTools.dateToString(cal.toInstant(TimeZone.UTC), DateTools.Resolution.HOUR)
        assertEquals("1961030523", dateString)
        assertEquals("1961-03-05 23:00:00:000", isoFormat(DateTools.stringToDate(dateString)))

        cal = LocalDateTime(1970, 1, 1, 0, 0, 0)
        dateString = DateTools.timeToString(cal.toInstant(TimeZone.UTC).toEpochMilliseconds(), DateTools.Resolution.MILLISECOND)
        assertEquals("19700101000000000", dateString)

        cal = LocalDateTime(1970, 1, 1, 1, 2, 3)
        dateString = DateTools.timeToString(cal.toInstant(TimeZone.UTC).toEpochMilliseconds(), DateTools.Resolution.MILLISECOND)
        assertEquals("19700101010203000", dateString)
    }

    @Test
    fun testRound() {
        val cal = LocalDateTime(2004, 2, 3, 22, 8, 56, 333_000_000)
        val date = cal.toInstant(TimeZone.UTC)
        assertEquals("2004-02-03 22:08:56:333", isoFormat(date))

        val dateYear = DateTools.round(date, DateTools.Resolution.YEAR)
        assertEquals("2004-01-01 00:00:00:000", isoFormat(dateYear))

        val dateMonth = DateTools.round(date, DateTools.Resolution.MONTH)
        assertEquals("2004-02-01 00:00:00:000", isoFormat(dateMonth))

        val dateDay = DateTools.round(date, DateTools.Resolution.DAY)
        assertEquals("2004-02-03 00:00:00:000", isoFormat(dateDay))

        val dateHour = DateTools.round(date, DateTools.Resolution.HOUR)
        assertEquals("2004-02-03 22:00:00:000", isoFormat(dateHour))

        val dateMinute = DateTools.round(date, DateTools.Resolution.MINUTE)
        assertEquals("2004-02-03 22:08:00:000", isoFormat(dateMinute))

        val dateSecond = DateTools.round(date, DateTools.Resolution.SECOND)
        assertEquals("2004-02-03 22:08:56:000", isoFormat(dateSecond))

        val dateMillisecond = DateTools.round(date, DateTools.Resolution.MILLISECOND)
        assertEquals("2004-02-03 22:08:56:333", isoFormat(dateMillisecond))

        val dateYearLong = DateTools.round(date.toEpochMilliseconds(), DateTools.Resolution.YEAR)
        assertEquals("2004-01-01 00:00:00:000", isoFormat(Instant.fromEpochMilliseconds(dateYearLong)))

        val dateMillisecondLong = DateTools.round(date.toEpochMilliseconds(), DateTools.Resolution.MILLISECOND)
        assertEquals("2004-02-03 22:08:56:333", isoFormat(Instant.fromEpochMilliseconds(dateMillisecondLong)))
    }

    @Test
    fun testDateToolsUTC() {
        val time = 1_130_630_400L
        val d1 = DateTools.dateToString(Instant.fromEpochSeconds(time), DateTools.Resolution.MINUTE)
        val d2 = DateTools.dateToString(Instant.fromEpochSeconds(time + 3_600), DateTools.Resolution.MINUTE)
        assertNotEquals(d1, d2, "different times")
        assertEquals(time * 1_000, DateTools.stringToTime(d1), "midnight")
        assertEquals((time + 3_600) * 1_000, DateTools.stringToTime(d2), "later")
    }

    private fun isoFormat(instant: Instant): String {
        val dt = instant.toLocalDateTime(DateTools.GMT)
        fun pad2(v: Int) = if (v < 10) "0$v" else "$v"
        fun pad3(v: Int) = when {
            v < 10 -> "00$v"
            v < 100 -> "0$v"
            else -> "$v"
        }
        val y = dt.year.toString().padStart(4, '0')
        val M = pad2(dt.month.number)
        val d = pad2(dt.dayOfMonth)
        val h = pad2(dt.hour)
        val m = pad2(dt.minute)
        val s = pad2(dt.second)
        val ms = pad3(dt.nanosecond / 1_000_000)
        return "$y-$M-$d $h:$m:$s:$ms"
    }
}

