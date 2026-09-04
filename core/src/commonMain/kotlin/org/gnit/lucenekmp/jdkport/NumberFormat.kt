package org.gnit.lucenekmp.jdkport

/**
 * Kotlin common subset of `java.text.NumberFormat` used by Lucene's flexible query parser.
 */
@Ported(from = "java.text.NumberFormat")
abstract class NumberFormat {
    abstract fun parse(source: String): Number

    open fun parse(source: String, parsePosition: ParsePosition): Number? {
        return try {
            val number = parse(source.substring(parsePosition.index))
            parsePosition.index = source.length
            number
        } catch (_: ParseException) {
            parsePosition.errorIndex = parsePosition.index
            null
        }
    }

    open fun format(number: Long): String = number.toString()

    open fun format(number: Double): String = number.toString()

    open fun format(
        number: Long,
        toAppendTo: StringBuilder,
        pos: FieldPosition
    ): StringBuilder = toAppendTo.append(format(number))

    open fun format(
        number: Double,
        toAppendTo: StringBuilder,
        pos: FieldPosition
    ): StringBuilder = toAppendTo.append(format(number))

    open fun format(number: Number): String {
        return when (number) {
            is Byte, is Short, is Int, is Long -> format(number.toLong())
            else -> format(number.toDouble())
        }
    }

    open fun format(
        number: Any,
        toAppendTo: StringBuilder,
        pos: FieldPosition
    ): StringBuilder {
        return toAppendTo.append(format(number as Number))
    }

    companion object {
        fun getIntegerInstance(locale: Locale): NumberFormat {
            return IntegerNumberFormat
        }

        fun getNumberInstance(locale: Locale): NumberFormat {
            return DecimalNumberFormat
        }

        private object IntegerNumberFormat : NumberFormat() {
            override fun parse(source: String): Number {
                try {
                    return source.toLong()
                } catch (e: NumberFormatException) {
                    throw ParseException("Unparseable number: \"$source\"", 0).initCause(e)
                }
            }
        }

        private object DecimalNumberFormat : NumberFormat() {
            override fun parse(source: String): Number {
                try {
                    return source.toDouble()
                } catch (e: NumberFormatException) {
                    throw ParseException("Unparseable number: \"$source\"", 0).initCause(e)
                }
            }
        }
    }
}
