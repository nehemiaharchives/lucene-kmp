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
package org.gnit.lucenekmp.analysis.commongrams

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.util.ClasspathResourceLoader
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests pretty much copied from StopFilterFactoryTest We use the test files used by the
 * StopFilterFactoryTest TODO: consider creating separate test files so this won't break if stop
 * filter test files change
 */
class TestCommonGramsQueryFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testInform() {
        val loader: ResourceLoader = ClasspathResourceLoader(this::class)
        assertTrue(loader != null, "loader is null and it shouldn't be")
        var factory =
            tokenFilterFactory(
                "CommonGramsQuery",
                Version.LATEST,
                loader,
                "words",
                "stop-1.txt",
                "ignoreCase",
                "true"
            ) as CommonGramsQueryFilterFactory
        var words: CharArraySet? = factory.getCommonWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.size == 2, "words Size: " + words.size + " is not: " + 2)
        assertTrue(factory.isIgnoreCase() == true, factory.isIgnoreCase().toString() + " does not equal: " + true)

        factory =
            tokenFilterFactory(
                "CommonGramsQuery",
                Version.LATEST,
                loader,
                "words",
                "stop-1.txt, stop-2.txt",
                "ignoreCase",
                "true"
            ) as CommonGramsQueryFilterFactory
        words = factory.getCommonWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.size == 4, "words Size: " + words.size + " is not: " + 4)
        assertTrue(factory.isIgnoreCase() == true, factory.isIgnoreCase().toString() + " does not equal: " + true)

        factory =
            tokenFilterFactory(
                "CommonGramsQuery",
                Version.LATEST,
                loader,
                "words",
                "stop-snowball.txt",
                "format",
                "snowball",
                "ignoreCase",
                "true"
            ) as CommonGramsQueryFilterFactory
        words = factory.getCommonWords()
        assertEquals(8, words!!.size)
        assertTrue(words.contains("he"))
        assertTrue(words.contains("him"))
        assertTrue(words.contains("his"))
        assertTrue(words.contains("himself"))
        assertTrue(words.contains("she"))
        assertTrue(words.contains("her"))
        assertTrue(words.contains("hers"))
        assertTrue(words.contains("herself"))
    }

    /** If no words are provided, then a set of english default stopwords is used. */
    @Test
    fun testDefaults() {
        val factory = tokenFilterFactory("CommonGramsQuery") as CommonGramsQueryFilterFactory
        val words: CharArraySet? = factory.getCommonWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.contains("the"))
        val tokenizer: Tokenizer = whitespaceMockTokenizer("testing the factory")
        val stream: TokenStream = factory.create(tokenizer)
        assertTokenStreamContents(stream, arrayOf("testing_the", "the_factory"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("CommonGramsQuery", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }

    @Test
    fun testCompleteGraph() {
        val factory = tokenFilterFactory("CommonGramsQuery") as CommonGramsQueryFilterFactory
        val words: CharArraySet? = factory.getCommonWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.contains("the"))
        val tokenizer: Tokenizer = whitespaceMockTokenizer("testing the factory works")
        val stream: TokenStream = factory.create(tokenizer)
        assertGraphStrings(stream, "testing_the the_factory factory works")
    }
}
