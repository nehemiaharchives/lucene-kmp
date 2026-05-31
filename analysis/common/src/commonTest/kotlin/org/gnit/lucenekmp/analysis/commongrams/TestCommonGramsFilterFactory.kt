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
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.util.ClasspathResourceLoader
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestCommonGramsFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testInform() {
        val loader: ResourceLoader = ClasspathResourceLoader(this::class)
        assertTrue(loader != null, "loader is null and it shouldn't be")
        var factory =
            tokenFilterFactory(
                "CommonGrams",
                Version.LATEST,
                loader,
                "words",
                "common-1.txt",
                "ignoreCase",
                "true"
            ) as CommonGramsFilterFactory
        var words: CharArraySet? = factory.getCommonWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.size == 2, "words Size: " + words.size + " is not: " + 2)
        assertTrue(factory.isIgnoreCase() == true, factory.isIgnoreCase().toString() + " does not equal: " + true)

        factory =
            tokenFilterFactory(
                "CommonGrams",
                Version.LATEST,
                loader,
                "words",
                "common-1.txt, common-2.txt",
                "ignoreCase",
                "true"
            ) as CommonGramsFilterFactory
        words = factory.getCommonWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.size == 4, "words Size: " + words.size + " is not: " + 4)
        assertTrue(factory.isIgnoreCase() == true, factory.isIgnoreCase().toString() + " does not equal: " + true)

        factory =
            tokenFilterFactory(
                "CommonGrams",
                Version.LATEST,
                loader,
                "words",
                "common-snowball.txt",
                "format",
                "snowball",
                "ignoreCase",
                "true"
            ) as CommonGramsFilterFactory
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
        val factory = tokenFilterFactory("CommonGrams") as CommonGramsFilterFactory
        val words: CharArraySet? = factory.getCommonWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.contains("the"))
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("testing the factory"))
        val stream: TokenStream = factory.create(tokenizer)
        assertTokenStreamContents(
            stream, arrayOf("testing", "testing_the", "the", "the_factory", "factory")
        )
    }

    /**
     * Test that ignoreCase flag is honored when no words are provided and default stopwords are used.
     */
    @Test
    fun testIgnoreCase() {
        val loader: ResourceLoader = ClasspathResourceLoader(this::class)
        val factory =
            tokenFilterFactory("CommonGrams", Version.LATEST, loader, "ignoreCase", "true") as CommonGramsFilterFactory
        val words: CharArraySet? = factory.getCommonWords()
        assertTrue(words != null, "words is null and it shouldn't be")
        assertTrue(words.contains("the"))
        assertTrue(words.contains("The"))
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("testing The factory"))
        val stream: TokenStream = factory.create(tokenizer)
        assertTokenStreamContents(
            stream, arrayOf("testing", "testing_The", "The", "The_factory", "factory")
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("CommonGrams", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
