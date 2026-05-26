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
package org.gnit.lucenekmp.analysis.sr

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * Normalizes Serbian Cyrillic to Latin.
 *
 * Note that it expects lowercased input.
 */
class SerbianNormalizationRegularFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            var buffer = termAtt.buffer()
            var length = termAtt.length
            var i = 0
            while (i < length) {
                val c = buffer[i]
                when (c) {
                    'а' -> buffer[i] = 'a'
                    'б' -> buffer[i] = 'b'
                    'в' -> buffer[i] = 'v'
                    'г' -> buffer[i] = 'g'
                    'д' -> buffer[i] = 'd'
                    'ђ' -> buffer[i] = 'đ'
                    'е' -> buffer[i] = 'e'
                    'ж' -> buffer[i] = 'ž'
                    'з' -> buffer[i] = 'z'
                    'и' -> buffer[i] = 'i'
                    'ј' -> buffer[i] = 'j'
                    'к' -> buffer[i] = 'k'
                    'л' -> buffer[i] = 'l'
                    'љ' -> {
                        buffer = termAtt.resizeBuffer(1 + length)
                        if (i < length) {
                            buffer.copyInto(buffer, i + 1, i, length)
                        }
                        buffer[i] = 'l'
                        i++
                        buffer[i] = 'j'
                        length++
                    }
                    'м' -> buffer[i] = 'm'
                    'н' -> buffer[i] = 'n'
                    'њ' -> {
                        buffer = termAtt.resizeBuffer(1 + length)
                        if (i < length) {
                            buffer.copyInto(buffer, i + 1, i, length)
                        }
                        buffer[i] = 'n'
                        i++
                        buffer[i] = 'j'
                        length++
                    }
                    'о' -> buffer[i] = 'o'
                    'п' -> buffer[i] = 'p'
                    'р' -> buffer[i] = 'r'
                    'с' -> buffer[i] = 's'
                    'т' -> buffer[i] = 't'
                    'ћ' -> buffer[i] = 'ć'
                    'у' -> buffer[i] = 'u'
                    'ф' -> buffer[i] = 'f'
                    'х' -> buffer[i] = 'h'
                    'ц' -> buffer[i] = 'c'
                    'ч' -> buffer[i] = 'č'
                    'џ' -> {
                        buffer = termAtt.resizeBuffer(1 + length)
                        if (i < length) {
                            buffer.copyInto(buffer, i + 1, i, length)
                        }
                        buffer[i] = 'd'
                        i++
                        buffer[i] = 'ž'
                        length++
                    }
                    'ш' -> buffer[i] = 'š'
                }
                i++
            }
            termAtt.setLength(length)
            return true
        } else {
            return false
        }
    }
}

