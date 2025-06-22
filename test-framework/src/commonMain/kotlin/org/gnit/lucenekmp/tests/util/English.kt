package org.gnit.lucenekmp.tests.util

/**
 * Converts numbers to english strings for testing.
 */
object English {
    fun longToEnglish(i: Long): String {
        val sb = StringBuilder()
        longToEnglish(i, sb)
        return sb.toString()
    }

    fun longToEnglish(i: Long, result: StringBuilder) {
        var n = i
        if (n == 0L) {
            result.append("zero")
            return
        }
        if (n < 0) {
            result.append("minus ")
            n = -n
        }
        if (n >= 1000000000000000000L) {
            longToEnglish(n / 1000000000000000000L, result)
            result.append("quintillion, ")
            n %= 1000000000000000000L
        }
        if (n >= 1000000000000000L) {
            longToEnglish(n / 1000000000000000L, result)
            result.append("quadrillion, ")
            n %= 1000000000000000L
        }
        if (n >= 1000000000000L) {
            longToEnglish(n / 1000000000000L, result)
            result.append("trillion, ")
            n %= 1000000000000L
        }
        if (n >= 1000000000L) {
            longToEnglish(n / 1000000000L, result)
            result.append("billion, ")
            n %= 1000000000L
        }
        if (n >= 1000000L) {
            longToEnglish(n / 1000000L, result)
            result.append("million, ")
            n %= 1000000L
        }
        if (n >= 1000L) {
            longToEnglish(n / 1000L, result)
            result.append("thousand, ")
            n %= 1000L
        }
        if (n >= 100L) {
            longToEnglish(n / 100L, result)
            result.append("hundred ")
            n %= 100L
        }
        if (n >= 20L) {
            when ((n / 10L).toInt()) {
                9 -> result.append("ninety")
                8 -> result.append("eighty")
                7 -> result.append("seventy")
                6 -> result.append("sixty")
                5 -> result.append("fifty")
                4 -> result.append("forty")
                3 -> result.append("thirty")
                2 -> result.append("twenty")
            }
            n %= 10L
            if (n == 0L) result.append(' ') else result.append('-')
        }
        when (n.toInt()) {
            19 -> result.append("nineteen ")
            18 -> result.append("eighteen ")
            17 -> result.append("seventeen ")
            16 -> result.append("sixteen ")
            15 -> result.append("fifteen ")
            14 -> result.append("fourteen ")
            13 -> result.append("thirteen ")
            12 -> result.append("twelve ")
            11 -> result.append("eleven ")
            10 -> result.append("ten ")
            9 -> result.append("nine ")
            8 -> result.append("eight ")
            7 -> result.append("seven ")
            6 -> result.append("six ")
            5 -> result.append("five ")
            4 -> result.append("four ")
            3 -> result.append("three ")
            2 -> result.append("two ")
            1 -> result.append("one ")
            0 -> {}
        }
    }

    fun intToEnglish(i: Int): String {
        val sb = StringBuilder()
        longToEnglish(i.toLong(), sb)
        return sb.toString()
    }

    fun intToEnglish(i: Int, result: StringBuilder) {
        longToEnglish(i.toLong(), result)
    }
}

