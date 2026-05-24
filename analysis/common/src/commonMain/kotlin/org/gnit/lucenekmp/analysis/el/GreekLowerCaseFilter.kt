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
package org.gnit.lucenekmp.analysis.el

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Character

/**
 * Normalizes token text to lower case, removes some Greek diacritics, and standardizes final sigma
 * to sigma.
 */
class GreekLowerCaseFilter(`in`: TokenStream) : TokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /**
     * Create a GreekLowerCaseFilter that normalizes Greek token text.
     *
     * @param in TokenStream to filter
     */
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val chArray = termAtt.buffer()
            val chLen = termAtt.length
            var i = 0
            while (i < chLen) {
                i += Character.toChars(lowerCase(Character.codePointAt(chArray, i, chLen)), chArray, i)
            }
            return true
        } else {
            return false
        }
    }

    private fun lowerCase(codepoint: Int): Int {
        return when (codepoint) {
            /* There are two lowercase forms of sigma:
             *   U+03C2: small final sigma (end of word)
             *   U+03C3: small sigma (otherwise)
             *
             * Standardize both to U+03C3
             */
            '\u03C2'.code -> '\u03C3'.code /* small sigma */

            /* Some greek characters contain diacritics.
             * This filter removes these, converting to the lowercase base form.
             */
            '\u0386'.code, '\u03AC'.code -> '\u03B1'.code /* small alpha */
            '\u0388'.code, '\u03AD'.code -> '\u03B5'.code /* small epsilon */
            '\u0389'.code, '\u03AE'.code -> '\u03B7'.code /* small eta */
            '\u038A'.code, '\u03AA'.code, '\u03AF'.code, '\u03CA'.code, '\u0390'.code -> '\u03B9'.code /* small iota */
            '\u038E'.code, '\u03AB'.code, '\u03CD'.code, '\u03CB'.code, '\u03B0'.code -> '\u03C5'.code /* small upsilon */
            '\u038C'.code, '\u03CC'.code -> '\u03BF'.code /* small omicron */
            '\u038F'.code, '\u03CE'.code -> '\u03C9'.code /* small omega */

            /* The previous implementation did the conversion below.
             * Only implemented for backwards compatibility with old indexes.
             */
            '\u03A2'.code -> '\u03C2'.code /* small final sigma */
            else -> Character.toLowerCase(codepoint)
        }
    }
}

