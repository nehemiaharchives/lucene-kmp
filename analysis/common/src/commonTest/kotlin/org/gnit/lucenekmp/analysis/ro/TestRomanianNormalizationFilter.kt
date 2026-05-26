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
package org.gnit.lucenekmp.analysis.ro

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Test the Romanian Normalization Filter */
class TestRomanianNormalizationFilter : BaseTokenStreamTestCase() {
    private lateinit var a: Analyzer

    @BeforeTest
    fun setUp() {
        a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val stream: TokenStream = RomanianNormalizationFilter(tokenizer)
                return TokenStreamComponents(tokenizer, stream)
            }
        }
    }

    @AfterTest
    fun tearDown() {
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSmallSCedilla() {
        checkOneTerm(a, "aceşti", "acești")
    }

    @Test
    @Throws(Exception::class)
    fun testCapitalSCedilla() {
        checkOneTerm(a, "ACEŞTI", "ACEȘTI")
    }

    @Test
    @Throws(Exception::class)
    fun testSmallTCedilla() {
        checkOneTerm(a, "câţi", "câți")
    }

    @Test
    @Throws(Exception::class)
    fun testCapitalTCedilla() {
        checkOneTerm(a, "CÂŢI", "CÂȚI")
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, RomanianNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
