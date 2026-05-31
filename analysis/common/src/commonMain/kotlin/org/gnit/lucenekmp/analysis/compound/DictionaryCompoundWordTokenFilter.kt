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
package org.gnit.lucenekmp.analysis.compound

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream

/**
 * A [org.gnit.lucenekmp.analysis.TokenFilter] that decomposes compound words found in many
 * Germanic languages.
 *
 * <p>"Donaudampfschiff" becomes Donau, dampf, schiff so that you can find "Donaudampfschiff" even
 * when you only enter "schiff". It uses a brute-force algorithm to achieve this.
 */
class DictionaryCompoundWordTokenFilter : CompoundWordTokenFilterBase {

    private var reuseChars = true

    /**
     * Creates a new [DictionaryCompoundWordTokenFilter]
     *
     * @param input the [org.gnit.lucenekmp.analysis.TokenStream] to process
     * @param dictionary the word dictionary to match against.
     */
    constructor(input: TokenStream, dictionary: CharArraySet) : super(input, dictionary) {
        if (dictionary == null) {
            throw IllegalArgumentException("dictionary must not be null")
        }
    }

    /**
     * Creates a new [DictionaryCompoundWordTokenFilter]
     *
     * @param input the [org.gnit.lucenekmp.analysis.TokenStream] to process
     * @param dictionary the word dictionary to match against.
     * @param minWordSize only words longer than this get processed
     * @param minSubwordSize only subwords longer than this get to the output stream
     * @param maxSubwordSize only subwords shorter than this get to the output stream
     * @param onlyLongestMatch Add only the longest matching subword to the stream
     * @param reuseChars Characters are reused for multiple matching words, e.g. if a word contains
     * 'schwein', the word 'schwein' and 'wein' will be extracted. If set to false, only the
     * longer word, 'schwein' in this case, will be extracted.
     */
    constructor(
        input: TokenStream,
        dictionary: CharArraySet,
        minWordSize: Int,
        minSubwordSize: Int,
        maxSubwordSize: Int,
        onlyLongestMatch: Boolean,
        reuseChars: Boolean
    ) : super(input, dictionary, minWordSize, minSubwordSize, maxSubwordSize, onlyLongestMatch) {
        this.reuseChars = reuseChars

        if (dictionary == null) {
            throw IllegalArgumentException("dictionary must not be null")
        }

        if (!reuseChars && !onlyLongestMatch) {
            throw IllegalArgumentException(
                "reuseChars can only be set to false if onlyLongestMatch is set to true"
            )
        }
    }

    override fun decompose() {
        val len = termAtt.length
        var i = 0
        while (i <= len - this.minSubwordSize) {
            var longestMatchToken: CompoundToken? = null
            var j = this.minSubwordSize
            while (j <= this.maxSubwordSize) {
                if (i + j > len) {
                    break
                }
                if (dictionary!!.contains(termAtt.buffer(), i, j)) {
                    if (this.onlyLongestMatch) {
                        if (longestMatchToken != null) {
                            if (longestMatchToken.txt.length < j) {
                                longestMatchToken = CompoundToken(i, j)
                            }
                        } else {
                            longestMatchToken = CompoundToken(i, j)
                        }
                    } else {
                        tokens.add(CompoundToken(i, j))
                    }
                }
                ++j
            }

            if (longestMatchToken != null && !reuseChars) {
                i += longestMatchToken.txt.length - 1
            }

            if (this.onlyLongestMatch && longestMatchToken != null) {
                tokens.add(longestMatchToken)
            }
            ++i
        }
    }
}
