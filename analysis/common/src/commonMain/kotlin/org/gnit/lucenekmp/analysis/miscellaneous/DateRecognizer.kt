package org.gnit.lucenekmp.analysis.miscellaneous

import kotlinx.datetime.LocalDate

fun interface DateRecognizer {
    fun isDate(text: String): Boolean
}

object EnglishDefaultDateRecognizer : DateRecognizer {
    private val monthNames =
        mapOf(
            "jan" to 1,
            "january" to 1,
            "feb" to 2,
            "february" to 2,
            "mar" to 3,
            "march" to 3,
            "apr" to 4,
            "april" to 4,
            "may" to 5,
            "jun" to 6,
            "june" to 6,
            "jul" to 7,
            "july" to 7,
            "aug" to 8,
            "august" to 8,
            "sep" to 9,
            "sept" to 9,
            "september" to 9,
            "oct" to 10,
            "october" to 10,
            "nov" to 11,
            "november" to 11,
            "dec" to 12,
            "december" to 12
        )

    override fun isDate(text: String): Boolean {
        val s = text.trim()
        return parseIso(s) || parseEnglishMonth(s)
    }

    private fun parseIso(text: String): Boolean {
        return try {
            LocalDate.parse(text)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun parseEnglishMonth(text: String): Boolean {
        val match = Regex("""^([A-Za-z]+)\s+(\d{1,2}),\s*(\d{4})$""").matchEntire(text) ?: return false
        val month = monthNames[match.groupValues[1].lowercase()] ?: return false
        val day = match.groupValues[2].toInt()
        val year = match.groupValues[3].toInt()
        return isValidDate(year, month, day)
    }
}

class PatternDateRecognizer(private val datePattern: String) : DateRecognizer {
    override fun isDate(text: String): Boolean {
        val s = text.trim()
        return when (datePattern) {
            "MM/dd/yyyy" -> parseNumericDate(s, '/')
            "MM-dd-yyyy" -> parseNumericDate(s, '-')
            "yyyy/MM/dd" -> parseYearFirstDate(s, '/')
            "yyyy-MM-dd" -> parseYearFirstDate(s, '-')
            else -> throw UnsupportedOperationException("Unsupported datePattern: $datePattern")
        }
    }

    private fun parseNumericDate(text: String, separator: Char): Boolean {
        val match = Regex("""^(\d{1,2})\Q$separator\E(\d{1,2})\Q$separator\E(\d{4})$""").matchEntire(text) ?: return false
        val month = match.groupValues[1].toInt()
        val day = match.groupValues[2].toInt()
        val year = match.groupValues[3].toInt()
        return isValidDate(year, month, day)
    }

    private fun parseYearFirstDate(text: String, separator: Char): Boolean {
        val match = Regex("""^(\d{4})\Q$separator\E(\d{1,2})\Q$separator\E(\d{1,2})$""").matchEntire(text) ?: return false
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        return isValidDate(year, month, day)
    }
}

class CompositeDateRecognizer(private vararg val recognizers: DateRecognizer) : DateRecognizer {
    override fun isDate(text: String): Boolean {
        return recognizers.any { it.isDate(text) }
    }
}

internal fun isValidDate(year: Int, month: Int, day: Int): Boolean {
    return try {
        LocalDate(year, month, day)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}
