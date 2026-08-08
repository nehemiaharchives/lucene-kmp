/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.queryparser.flexible.standard.parser

import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.queryparser.flexible.core.util.UnescapedCharSequence
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl

/** Implementation of [EscapeQuerySyntax] for the standard lucene syntax. */
class EscapeQuerySyntaxImpl : EscapeQuerySyntax {
    override fun escape(text: CharSequence, locale: Locale, type: EscapeQuerySyntax.Type): CharSequence {
        if (text.isEmpty()) return text

        var text = text

        // escape wildcards and the escape char (this has to be performed before anything else)
        // since we need to preserve the UnescapedCharSequence and escape the original escape chars
        if (text is UnescapedCharSequence) {
            text = text.toStringEscaped(wildcardChars)
        } else {
            text = UnescapedCharSequence(text).toStringEscaped(wildcardChars)
        }

        if (type == EscapeQuerySyntax.Type.STRING) {
            return escapeQuoted(text, locale)
        } else {
            return escapeTerm(text, locale)
        }
    }

    companion object {
        private val wildcardChars = charArrayOf('*', '?')

        private val escapableTermExtraFirstChars = arrayOf("+", "-", "@")

        private val escapableTermChars =
            arrayOf("\"", "<", ">", "=", "!", "(", ")", "^", "[", "{", ":", "]", "}", "~", "/")

        // TODO: check what to do with these "*", "?", "\\"
        private val escapableQuotedChars = arrayOf("\"")
        private val escapableWhiteChars = arrayOf(" ", "\t", "\n", "\r", "\u000c", "\b", "\u3000")
        private val escapableWordTokens =
            arrayOf("AND", "OR", "NOT", "TO", "WITHIN", "SENTENCE", "PARAGRAPH", "INORDER")

        private fun escapeChar(str: CharSequence, locale: Locale): CharSequence {
            if (str.isEmpty()) return str

            var buffer = str

            // regular escapable char for terms
            for (escapableTermChar in escapableTermChars) {
                buffer = escapeIgnoringCase(buffer, escapableTermChar.lowercase(), "\\", locale)
            }

            // first char of a term as more escaping chars
            for (escapableTermExtraFirstChar in escapableTermExtraFirstChars) {
                if (buffer[0] == escapableTermExtraFirstChar[0]) {
                    buffer = "\\$buffer"
                    break
                }
            }

            return buffer
        }

        private fun escapeQuoted(str: CharSequence, locale: Locale): CharSequence {
            if (str.isEmpty()) return str

            var buffer = str

            for (escapableQuotedChar in escapableQuotedChars) {
                buffer = escapeIgnoringCase(buffer, escapableQuotedChar.lowercase(), "\\", locale)
            }
            return buffer
        }

        private fun escapeTerm(term: CharSequence, locale: Locale): CharSequence {
            if (term.isEmpty()) return term

            var term = term

            // escape single chars
            term = escapeChar(term, locale)
            term = escapeWhiteChar(term, locale)

            // escape parser words
            for (escapableWordToken in escapableWordTokens) {
                if (escapableWordToken.equals(term.toString(), ignoreCase = true)) return "\\$term"
            }
            return term
        }

        /**
         * Prepend every case-insensitive occurrence of the [sequence1] in the [string] with the
         * [escapeChar]. When [sequence1] is empty, every character in the [string] is escaped.
         *
         * @param string string to apply escaping to
         * @param sequence1 the old character sequence in lowercase
         * @param escapeChar the escape character to prefix sequence1 in the returned string
         * @return CharSequence with every occurrence of [sequence1] prepended with [escapeChar]
         */
        private fun escapeIgnoringCase(
            string: CharSequence,
            sequence1: CharSequence,
            escapeChar: CharSequence,
            locale: Locale,
        ): CharSequence {
            val count = string.length
            val sequence1Length = sequence1.length

            // empty search string - escape every character
            if (sequence1Length == 0) {
                val result = StringBuilder(count * (1 + escapeChar.length))
                for (i in 0..<count) {
                    result.append(escapeChar)
                    result.append(string[i])
                }
                return result
            }

            // normal case
            val lowercase = string.toString().lowercase()
            val result = StringBuilder()
            val first = sequence1[0]
            var start = 0
            var copyStart = 0
            var firstIndex: Int
            while (start < count) {
                firstIndex = lowercase.indexOf(first, start)
                if (firstIndex == -1) break
                var found = true
                if (sequence1.length > 1) {
                    if (firstIndex + sequence1Length > count) break
                    for (i in 1..<sequence1Length) {
                        if (lowercase[firstIndex + i] != sequence1[i]) {
                            found = false
                            break
                        }
                    }
                }
                if (found) {
                    result.append(string, copyStart, firstIndex)
                    result.append(escapeChar)
                    result.append(string, firstIndex, firstIndex + sequence1Length)
                    start = firstIndex + sequence1Length
                    copyStart = start
                } else {
                    start = firstIndex + 1
                }
            }
            if (result.isEmpty() && copyStart == 0) return string
            result.append(string, copyStart, string.length)
            return result
        }

        /**
         * escape all tokens that are part of the parser syntax on a given string
         *
         * @param str string to get replaced
         * @param locale locale to be used when performing string compares
         * @return the new String
         */
        private fun escapeWhiteChar(str: CharSequence, locale: Locale): CharSequence {
            if (str.isEmpty()) return str

            var buffer = str

            for (escapableWhiteChar in escapableWhiteChars) {
                buffer = escapeIgnoringCase(buffer, escapableWhiteChar.lowercase(), "\\", locale)
            }
            return buffer
        }

        /**
         * Returns a String where the escape char has been removed, or kept only once if there was a
         * double escape.
         *
         * Supports escaped Unicode characters, e.g. translates `\u005Cu0041` to `A`.
         */
        fun discardEscapeChar(input: CharSequence): UnescapedCharSequence {
            // Create char array to hold unescaped char sequence
            val output = CharArray(input.length)
            val wasEscaped = BooleanArray(input.length)

            // The length of the output can be less than the input
            // due to discarded escape chars. This variable holds
            // the actual length of the output
            var length = 0

            // We remember whether the last processed character was
            // an escape character
            var lastCharWasEscapeChar = false

            // The multiplier the current unicode digit must be multiplied with.
            // E.g. the first digit must be multiplied with 16^3, the second with 16^2...
            var codePointMultiplier = 0

            // Used to calculate the codepoint of the escaped unicode character
            var codePoint = 0

            for (i in 0..<input.length) {
                val curChar = input[i]
                if (codePointMultiplier > 0) {
                    codePoint += hexToInt(curChar) * codePointMultiplier
                    codePointMultiplier = codePointMultiplier ushr 4
                    if (codePointMultiplier == 0) {
                        output[length++] = codePoint.toChar()
                        codePoint = 0
                    }
                } else if (lastCharWasEscapeChar) {
                    if (curChar == 'u') {
                        // found an escaped unicode character
                        codePointMultiplier = 16 * 16 * 16
                    } else {
                        // this character was escaped
                        output[length] = curChar
                        wasEscaped[length] = true
                        length++
                    }
                    lastCharWasEscapeChar = false
                } else {
                    if (curChar == '\\') {
                        lastCharWasEscapeChar = true
                    } else {
                        output[length] = curChar
                        length++
                    }
                }
            }

            if (codePointMultiplier > 0) {
                throw ParseException(MessageImpl(QueryParserMessages.INVALID_SYNTAX_ESCAPE_UNICODE_TRUNCATION))
            }

            if (lastCharWasEscapeChar) {
                throw ParseException(MessageImpl(QueryParserMessages.INVALID_SYNTAX_ESCAPE_CHARACTER))
            }

            return UnescapedCharSequence(output, wasEscaped, 0, length)
        }

        /** Returns the numeric value of the hexadecimal character */
        private fun hexToInt(c: Char): Int {
            if (c in '0'..'9') {
                return c - '0'
            } else if (c in 'a'..'f') {
                return c - 'a' + 10
            } else if (c in 'A'..'F') {
                return c - 'A' + 10
            } else {
                throw ParseException(
                    MessageImpl(QueryParserMessages.INVALID_SYNTAX_ESCAPE_NONE_HEX_UNICODE, c),
                )
            }
        }
    }
}
