package org.gnit.lucenekmp.analysis.util

/** Utility class for parsing CSV text. */
class CSVUtil private constructor() {
    companion object {
        private const val QUOTE: Char = '"'
        private const val COMMA: Char = ','
        private val QUOTE_REPLACE_PATTERN = Regex("""^"([^"]+)"$""")
        private const val ESCAPED_QUOTE: String = "\"\""

        /**
         * Parse CSV line.
         *
         * @param line line containing csv-encoded data
         * @return Array of values
         */
        fun parse(line: String): Array<String> {
            var insideQuote = false
            val result = mutableListOf<String>()
            var quoteCount = 0
            val sb = StringBuilder()
            for (i in line.indices) {
                val c = line[i]
                if (c == QUOTE) {
                    insideQuote = !insideQuote
                    quoteCount++
                }

                if (c == COMMA && !insideQuote) {
                    var value = sb.toString()
                    value = unQuoteUnEscape(value)
                    result.add(value)
                    sb.setLength(0)
                    continue
                }

                sb.append(c)
            }

            result.add(sb.toString())

            // Validate
            if (quoteCount % 2 != 0) {
                return emptyArray()
            }

            return result.toTypedArray()
        }

        private fun unQuoteUnEscape(original: String): String {
            var result = original

            // Unquote
            if (result.indexOf(QUOTE) >= 0) {
                val match = QUOTE_REPLACE_PATTERN.matchEntire(original)
                if (match != null) {
                    result = match.groupValues[1]
                }

                // Unescape
                if (result.contains(ESCAPED_QUOTE)) {
                    result = result.replace(ESCAPED_QUOTE, "\"")
                }
            }

            return result
        }

        /** Quote and escape input value for CSV. */
        fun quoteEscape(original: String): String {
            var result = original

            if (result.indexOf(QUOTE) >= 0) {
                result = result.replace("\"", ESCAPED_QUOTE)
            }
            if (result.indexOf(COMMA) >= 0) {
                result = "\"" + result + "\""
            }
            return result
        }
    }
}
