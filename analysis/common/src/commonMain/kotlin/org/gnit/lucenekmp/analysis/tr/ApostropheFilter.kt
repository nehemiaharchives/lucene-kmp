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

/**
 * Strips all characters after an apostrophe (including the apostrophe itself).
 *
 * In Turkish, apostrophe is used to separate suffixes from proper names (continent, sea, river,
 * lake, mountain, upland, proper names related to religion and mythology). This filter intended to
 * be used before stem filters. For more information, see
 * [Role of Apostrophes in Turkish Information Retrieval](http://www.ipcsit.com/vol57/015-ICNI2012-M021.pdf)
 */
class ApostropheFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false

        val buffer = termAtt.buffer()
        val length = termAtt.length

        for (i in 0 until length) {
            if (buffer[i] == '\'' || buffer[i] == '\u2019') {
                termAtt.setLength(i)
                return true
            }
        }
        return true
    }
}

