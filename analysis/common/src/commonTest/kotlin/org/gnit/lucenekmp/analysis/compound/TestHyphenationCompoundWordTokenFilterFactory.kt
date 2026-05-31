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

/** Simple tests to ensure the Hyphenation compound filter factory is working. */
class TestHyphenationCompoundWordTokenFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    /** Ensure the factory works with hyphenation grammar+dictionary: using default options. */
    @Test
    fun testHyphenationWithDictionary() {
        val reader = StringReader("min veninde som er lidt af en læsehest")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream =
            tokenFilterFactory(
                "HyphenationCompoundWord",
                "hyphenator",
                "da_UTF8.xml",
                "dictionary",
                "da_compoundDictionary.txt"
            )
                .create(stream)

        assertTokenStreamContents(
            stream,
            arrayOf("min", "veninde", "som", "er", "lidt", "af", "en", "læsehest", "læse", "hest"),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 0, 0)
        )
    }

    /**
     * just tests that the two no configuration options are correctly processed tests for the
     * functionality are part of [TestCompoundWordTokenFilter]
     */
    @Test
    fun testLucene8183() {
        val reader = StringReader("basketballkurv")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream =
            tokenFilterFactory(
                "HyphenationCompoundWord",
                "hyphenator",
                "da_UTF8.xml",
                "dictionary",
                "compoundDictionary_lucene8183.txt",
                "onlyLongestMatch",
                "false",
                "noSubMatches",
                "true",
                "noOverlappingMatches",
                "false"
            )
                .create(stream)

        assertTokenStreamContents(
            stream, arrayOf("basketballkurv", "basketball", "kurv"), intArrayOf(1, 0, 0)
        )
    }

    /**
     * Ensure the factory works with no dictionary: using hyphenation grammar only. Also change the
     * min/max subword sizes from the default. When using no dictionary, it's generally necessary to
     * tweak these, or you get lots of expansions.
     */
    @Test
    fun testHyphenationOnly() {
        val reader = StringReader("basketballkurv")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream =
            tokenFilterFactory(
                "HyphenationCompoundWord",
                "hyphenator",
                "da_UTF8.xml",
                "minSubwordSize",
                "2",
                "maxSubwordSize",
                "4"
            )
                .create(stream)

        assertTokenStreamContents(
            stream, arrayOf("basketballkurv", "ba", "sket", "ball", "bal", "kurv")
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected =
            expectThrows(IllegalArgumentException::class) {
                tokenFilterFactory(
                    "HyphenationCompoundWord", "hyphenator", "da_UTF8.xml", "bogusArg", "bogusValue"
                )
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
