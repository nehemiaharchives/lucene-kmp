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
package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.util.BytesRef

/**
 * Wraps a whitespace tokenizer with a filter that sets the first token, and odd tokens to posinc=1,
 * and all others to 0, encoding the position as pos: XXX in the payload.
 */
class MockPayloadAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val result: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
        return TokenStreamComponents(result, MockPayloadFilter(result, fieldName))
    }
}

/** */
private class MockPayloadFilter(input: TokenStream, val fieldName: String) : TokenFilter(input) {
    var pos: Int = 0

    var i: Int = 0

    val posIncrAttr: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    val payloadAttr: PayloadAttribute = addAttribute(PayloadAttribute::class)
    val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            payloadAttr.payload = BytesRef("pos: $pos".encodeToByteArray())
            val posIncr: Int =
                if (pos == 0 || i % 2 == 1) {
                    1
                } else {
                    0
                }
            posIncrAttr.setPositionIncrement(posIncr)
            pos += posIncr
            i++
            return true
        } else {
            return false
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        i = 0
        pos = 0
    }
}
