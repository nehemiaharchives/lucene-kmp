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
package org.gnit.lucenekmp.analysis.tr

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Character

/**
 * Normalizes Turkish token text to lower case.
 *
 * Turkish and Azeri have unique casing behavior for some characters. This filter applies Turkish
 * lowercase rules. For more information, see
 * [Turkish dotted and dotless I](http://en.wikipedia.org/wiki/Turkish_dotted_and_dotless_I)
 */
class TurkishLowerCaseFilter(input: TokenStream) : TokenFilter(input) {
    private companion object {
        private const val LATIN_CAPITAL_LETTER_I = 0x0049
        private const val LATIN_SMALL_LETTER_I = 0x0069
        private const val LATIN_SMALL_LETTER_DOTLESS_I = 0x0131
        private const val COMBINING_DOT_ABOVE = 0x0307
    }

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /**
     * Create a new TurkishLowerCaseFilter, that normalizes Turkish token text to lower case.
     *
     * @param `in` TokenStream to filter
     */
    override fun incrementToken(): Boolean {
        var iOrAfter = false

        if (input.incrementToken()) {
            val buffer = termAtt.buffer()
            var length = termAtt.length
            var i = 0
            while (i < length) {
                val ch = Character.codePointAt(buffer, i, length)

                iOrAfter =
                    (ch == LATIN_CAPITAL_LETTER_I
                        || (iOrAfter && Character.getType(ch) == Character.NON_SPACING_MARK.toInt()))

                if (iOrAfter) { // all the special I turkish handling happens here.
                    when (ch) {
                        // remove COMBINING_DOT_ABOVE to mimic composed lowercase
                        COMBINING_DOT_ABOVE -> {
                            length = delete(buffer, i, length)
                            continue
                        }
                        // i itself, it depends if it is followed by COMBINING_DOT_ABOVE
                        // if it is, we will make it small i and later remove the dot
                        LATIN_CAPITAL_LETTER_I -> {
                            if (isBeforeDot(buffer, i + 1, length)) {
                                buffer[i] = LATIN_SMALL_LETTER_I.toChar()
                            } else {
                                buffer[i] = LATIN_SMALL_LETTER_DOTLESS_I.toChar()
                                // below is an optimization. no COMBINING_DOT_ABOVE follows,
                                // so don't waste time calculating Character.getType(), etc
                                iOrAfter = false
                            }
                            i++
                            continue
                        }
                    }
                }

                i += Character.toChars(Character.toLowerCase(ch), buffer, i)
            }

            termAtt.setLength(length)
            return true
        } else return false
    }

    /** lookahead for a combining dot above. other NSMs may be in between. */
    private fun isBeforeDot(s: CharArray, pos: Int, len: Int): Boolean {
        var i = pos
        while (i < len) {
            val ch = Character.codePointAt(s, i, len)
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) return false
            if (ch == COMBINING_DOT_ABOVE) return true
            i += Character.charCount(ch)
        }

        return false
    }

    /**
     * delete a character in-place. rarely happens, only if COMBINING_DOT_ABOVE is found after an i
     */
    private fun delete(s: CharArray, pos: Int, len: Int): Int {
        if (pos < len) s.copyInto(s, pos, pos + 1, len)

        return len - 1
    }
}

