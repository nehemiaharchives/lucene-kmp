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

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure the Dictionary compound filter factory is working. */
class TestDictionaryCompoundWordTokenFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    /** Ensure the filter actually decompounds text. */
    @Test
    fun testDecompounding() {
        val reader = StringReader("I like to play softball")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream =
            tokenFilterFactory("DictionaryCompoundWord", "dictionary", "compoundDictionary.txt")
                .create(stream)
        assertTokenStreamContents(
            stream, arrayOf("I", "like", "to", "play", "softball", "soft", "ball")
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected =
            expectThrows(IllegalArgumentException::class) {
                tokenFilterFactory(
                    "DictionaryCompoundWord",
                    "dictionary",
                    "compoundDictionary.txt",
                    "bogusArg",
                    "bogusValue"
                )
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
