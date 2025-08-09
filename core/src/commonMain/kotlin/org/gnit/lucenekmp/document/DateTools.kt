package org.gnit.lucenekmp.document

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.TermRangeQuery
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Provides support for converting dates to strings and vice-versa. The strings are structured so
 * that lexicographic sorting orders them by date, which makes them suitable for use as field values
 * and search terms.
 *
 *
 * This class also helps you to limit the resolution of your dates. Do not save dates with a
 * finer resolution than you really need, as then [TermRangeQuery] and [PrefixQuery]
 * will require more memory and become slower.
 *
 *
 * Another approach is [LongPoint], which indexes the values in sorted order. For indexing
 * a [Date] or [Calendar], just get the unix timestamp as `long` using [ ][Date.getTime] or [Calendar.getTimeInMillis] and index this as a numeric value with [ ] and use [org.apache.lucene.search.PointRangeQuery] to query it.
 */
object DateTools {
    val GMT: TimeZone = TimeZone.UTC

    /**
     * Converts a Date to a string suitable for indexing.
     *
     * @param date the date to be converted
     * @param resolution the desired resolution, see [.round]
     * @return a string in format `yyyyMMddHHmmssSSS` or shorter, depending on `
     * resolution`; using GMT as timezone
     */
    @OptIn(ExperimentalTime::class)
    fun dateToString(date: Instant, resolution: Resolution): String {
        return timeToString(date.toEpochMilliseconds(), resolution)
    }

    /**
     * Converts a millisecond time to a string suitable for indexing.
     *
     * @param time the date expressed as milliseconds since January 1, 1970, 00:00:00 GMT
     * @param resolution the desired resolution, see [.round]
     * @return a string in format `yyyyMMddHHmmssSSS` or shorter, depending on `
     * resolution`; using GMT as timezone
     */
    @OptIn(ExperimentalTime::class)
    fun timeToString(time: Long, resolution: Resolution): String {
        val date = Instant.fromEpochMilliseconds(time).toLocalDateTime(GMT)
        val full = buildSortable(date)
        return full.take(resolution.formatLen)
    }

    /**
     * Converts a string produced by `timeToString` or `dateToString` back to a
     * time, represented as the number of milliseconds since January 1, 1970, 00:00:00 GMT.
     *
     * @param dateString the date string to be converted
     * @return the number of milliseconds since January 1, 1970, 00:00:00 GMT
     * @throws ParseException if `dateString` is not in the expected format
     */
    @OptIn(ExperimentalTime::class)
    @Throws(ParseException::class)
    fun stringToTime(dateString: String): Long {
        val n = dateString.length
        require(n in 4..17) { "Invalid date string length: $n" }
        val full = (dateString + "0".repeat(17 - n))

        val y = full.take(4).toInt()
        val M = full.substring(4, 6).toInt().coerceIn(1, 12)
        val d = full.substring(6, 8).toInt().coerceAtLeast(1)
        val h = full.substring(8, 10).toInt()
        val m = full.substring(10, 12).toInt()
        val s = full.substring(12, 14).toInt()
        val ms = full.substring(14, 17).toInt()
        val day = d.coerceAtMost(daysInMonth(y, M))

        val ldt = LocalDateTime(y, M, day, h, m, s, ms * 1_000_000)
        return ldt.toInstant(GMT).toEpochMilliseconds()
    }

    /**
     * Converts a string produced by `timeToString` or `dateToString` back to a
     * time, represented as a Date object.
     *
     * @param dateString the date string to be converted
     * @return the parsed time as a Date object
     * @throws ParseException if `dateString` is not in the expected format
     */
    @OptIn(ExperimentalTime::class)
    @Throws(ParseException::class)
    fun stringToDate(dateString: String): Instant {
        try {
            return Instant.fromEpochMilliseconds(stringToTime(dateString))
        } catch (e: Exception) {
            val ex = ParseException("Input is not a valid date string: $dateString", 0)
            ex.initCause(e)
            throw ex
        }
    }

    /**
     * Limit a date's resolution. For example, the date `2004-09-21 13:50:11` will be
     * changed to `2004-09-01 00:00:00` when using `Resolution.MONTH`.
     *
     * @param resolution The desired resolution of the date to be returned
     * @return the date with all values more precise than `resolution` set to 0 or 1
     */
    @OptIn(ExperimentalTime::class)
    fun round(date: Instant, resolution: Resolution): Instant {
        return Instant.fromEpochMilliseconds(round(date.toEpochMilliseconds(), resolution))
    }

    /**
     * Limit a date's resolution. For example, the date `1095767411000` (which represents
     * 2004-09-21 13:50:11) will be changed to `1093989600000` (2004-09-01 00:00:00) when
     * using `Resolution.MONTH`.
     *
     * @param resolution The desired resolution of the date to be returned
     * @return the date with all values more precise than `resolution` set to 0 or 1,
     * expressed as milliseconds since January 1, 1970, 00:00:00 GMT
     */
    @OptIn(ExperimentalTime::class)
    fun round(time: Long, resolution: Resolution): Long {
        val ldt: LocalDateTime = Instant.fromEpochMilliseconds(time).toLocalDateTime(GMT)

        val floored = when (resolution) {
            Resolution.YEAR -> LocalDateTime(ldt.year, 1, 1, 0, 0, 0, 0)
            Resolution.MONTH -> LocalDateTime(ldt.year, ldt.month.number, 1, 0, 0, 0, 0)
            Resolution.DAY -> LocalDateTime(ldt.year, ldt.month.number, ldt.day, 0, 0, 0, 0)
            Resolution.HOUR -> LocalDateTime(ldt.year, ldt.month.number, ldt.day, ldt.hour, 0, 0, 0)
            Resolution.MINUTE -> LocalDateTime(ldt.year, ldt.month.number, ldt.day, ldt.hour, ldt.minute, 0, 0)
            Resolution.SECOND -> LocalDateTime(ldt.year, ldt.month.number, ldt.day, ldt.hour, ldt.minute, ldt.second, 0)
            Resolution.MILLISECOND -> LocalDateTime(ldt.year, ldt.month.number, ldt.day, ldt.hour, ldt.minute, ldt.second, (ldt.nanosecond / 1_000_000) * 1_000_000)
        }
        return floored.toInstant(GMT).toEpochMilliseconds()
    }

    // Helpers
    private fun buildSortable(ldt: LocalDateTime): String {
        // yyyyMMddHHmmssSSS (17 chars), zero-padded
        // Allocate once and fill (faster than String.format).
        val sb = StringBuilder(17)
        append4(sb, ldt.year)
        append2(sb, ldt.month.number)
        append2(sb, ldt.day)
        append2(sb, ldt.hour)
        append2(sb, ldt.minute)
        append2(sb, ldt.second)
        append3(sb, ldt.nanosecond / 1_000_000) // millis
        return sb.toString()
    }

    private fun append2(sb: StringBuilder, v: Int) {
        if (v < 10) sb.append('0')
        sb.append(v)
    }

    private fun append3(sb: StringBuilder, v: Int) {
        when {
            v < 10 -> sb.append("00").append(v)
            v < 100 -> sb.append('0').append(v)
            else -> sb.append(v)
        }
    }

    private fun append4(sb: StringBuilder, v: Int) {
        // handles 0000-9999; for dates outside typical range, adjust if needed
        val s = v.toString()
        repeat(4 - s.length) { sb.append('0') }
        sb.append(s)
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeap(year)) 29 else 28
            else -> 30
        }
    }

    private fun isLeap(y: Int): Boolean =
        (y % 4 == 0) && ((y % 100 != 0) || (y % 400 == 0))

    /** Specifies the time granularity.  */
    enum class Resolution(val formatLen: Int) {
        /** Limit a date's resolution to year granularity.  */
        YEAR(4),

        /** Limit a date's resolution to month granularity.  */
        MONTH(6),

        /** Limit a date's resolution to day granularity.  */
        DAY(8),

        /** Limit a date's resolution to hour granularity.  */
        HOUR(10),

        /** Limit a date's resolution to minute granularity.  */
        MINUTE(12),

        /** Limit a date's resolution to second granularity.  */
        SECOND(14),

        /** Limit a date's resolution to millisecond granularity.  */
        MILLISECOND(17);

        /** this method returns the name of the resolution in lowercase (for backwards compatibility)  */
        override fun toString(): String {
            return super.toString().lowercase()
        }
    }
}
