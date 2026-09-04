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
package org.gnit.lucenekmp.queryparser.flexible.core.util

import org.gnit.lucenekmp.jdkport.Locale

/** CharsSequence with escaped chars information. */
class UnescapedCharSequence : CharSequence {
    private val chars: CharArray

    private val wasEscaped: BooleanArray

    /** Create an escaped CharSequence */
    constructor(chars: CharArray, wasEscaped: BooleanArray, offset: Int, length: Int) {
        this.chars = CharArray(length)
        this.wasEscaped = BooleanArray(length)
        chars.copyInto(this.chars, 0, offset, offset + length)
        wasEscaped.copyInto(this.wasEscaped, 0, offset, offset + length)
    }

    /** Create a non-escaped CharSequence */
    constructor(text: CharSequence) {
        chars = CharArray(text.length)
        wasEscaped = BooleanArray(text.length)
        for (i in 0..<text.length) {
            chars[i] = text[i]
            wasEscaped[i] = false
        }
    }

    override fun get(index: Int): Char {
        return chars[index]
    }

    override val length: Int
        get() = chars.size

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        val newLength = endIndex - startIndex

        return UnescapedCharSequence(chars, wasEscaped, startIndex, newLength)
    }

    override fun toString(): String {
        return chars.concatToString()
    }

    /**
     * Return a escaped String
     *
     * @return a escaped String
     */
    fun toStringEscaped(): String {
        // non efficient implementation
        val result = StringBuilder(length)
        for (i in 0..<length) {
            if (chars[i] == '\\' || wasEscaped[i]) {
                result.append('\\')
            }

            result.append(chars[i])
        }
        return result.toString()
    }

    /**
     * Return a escaped String
     *
     * @param enabledChars - array of chars to be escaped
     * @return a escaped String
     */
    fun toStringEscaped(enabledChars: CharArray): String {
        // TODO: non efficient implementation, refactor this code
        val result = StringBuilder(length)
        for (i in 0..<length) {
            if (chars[i] == '\\') {
                result.append('\\')
            } else {
                for (character in enabledChars) {
                    if (chars[i] == character && wasEscaped[i]) {
                        result.append('\\')
                        break
                    }
                }
            }

            result.append(chars[i])
        }
        return result.toString()
    }

    fun wasEscaped(index: Int): Boolean {
        return wasEscaped[index]
    }

    companion object {
        fun wasEscaped(text: CharSequence, index: Int): Boolean {
            if (text is UnescapedCharSequence)
                return text.wasEscaped[index]
            else return false
        }

        fun toLowerCase(text: CharSequence, locale: Locale): CharSequence {
            if (text is UnescapedCharSequence) {
                val chars = text.toString().lowercase().toCharArray()
                val wasEscaped = text.wasEscaped
                return UnescapedCharSequence(chars, wasEscaped, 0, chars.size)
            } else return UnescapedCharSequence(text.toString().lowercase())
        }
    }
}
