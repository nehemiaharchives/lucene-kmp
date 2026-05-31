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
import org.gnit.lucenekmp.jdkport.InputSource
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.ClasspathResourceLoader
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TestCompoundWordTokenFilter : BaseTokenStreamTestCase() {

    private fun makeDictionary(vararg dictionary: String): CharArraySet {
        return CharArraySet(dictionary.map { it as Any }.toMutableList(), true)
    }

    @Test
    fun testHyphenationCompoundWordsDA() {
        val dict = makeDictionary("læse", "hest")

        val loader = ClasspathResourceLoader(this::class)
        val inputSource = InputSource(loader.openResource("da_UTF8.xml"))
        inputSource.systemId = "da_UTF8.xml"
        val hyphenator = HyphenationCompoundWordTokenFilter.getHyphenationTree(inputSource)

        val tf =
            HyphenationCompoundWordTokenFilter(
                whitespaceMockTokenizer("min veninde som er lidt af en læsehest"),
                hyphenator,
                dict,
                CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE,
                CompoundWordTokenFilterBase.DEFAULT_MIN_SUBWORD_SIZE,
                CompoundWordTokenFilterBase.DEFAULT_MAX_SUBWORD_SIZE,
                false
            )
        assertTokenStreamContents(
            tf,
            arrayOf("min", "veninde", "som", "er", "lidt", "af", "en", "læsehest", "læse", "hest"),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 0, 0)
        )
    }

    @Test
    fun testHyphenationCompoundWordsDELongestMatch() {
        val dict = makeDictionary("basketball", "basket", "ball", "kurv")

        val loader = ClasspathResourceLoader(this::class)
        val inputSource = InputSource(loader.openResource("da_UTF8.xml"))
        inputSource.systemId = "da_UTF8.xml"
        val hyphenator = HyphenationCompoundWordTokenFilter.getHyphenationTree(inputSource)

        // the word basket will not be added due to the longest match option
        val tf =
            HyphenationCompoundWordTokenFilter(
                whitespaceMockTokenizer("basketballkurv"),
                hyphenator,
                dict,
                CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE,
                CompoundWordTokenFilterBase.DEFAULT_MIN_SUBWORD_SIZE,
                40,
                true
            )
        assertTokenStreamContents(
            tf, arrayOf("basketballkurv", "basketball", "ball", "kurv"), intArrayOf(1, 0, 0, 0)
        )
    }

    /**
     * With hyphenation-only, you can get a lot of nonsense tokens. This can be controlled with the
     * min/max subword size.
     */
    @Test
    fun testHyphenationOnly() {
        val loader = ClasspathResourceLoader(this::class)
        val inputSource = InputSource(loader.openResource("da_UTF8.xml"))
        inputSource.systemId = "da_UTF8.xml"
        val hyphenator = HyphenationCompoundWordTokenFilter.getHyphenationTree(inputSource)

        var tf =
            HyphenationCompoundWordTokenFilter(
                whitespaceMockTokenizer("basketballkurv"),
                hyphenator,
                CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE,
                2,
                4
            )

        // min=2, max=4
        assertTokenStreamContents(
            tf, arrayOf("basketballkurv", "ba", "sket", "ball", "bal", "kurv")
        )

        tf =
            HyphenationCompoundWordTokenFilter(
                whitespaceMockTokenizer("basketballkurv"),
                hyphenator,
                CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE,
                4,
                6
            )

        // min=4, max=6
        assertTokenStreamContents(
            tf, arrayOf("basketballkurv", "basket", "sket", "ball", "lkurv", "kurv")
        )
    }

    @Test
    fun testDumbCompoundWordsSE() {
        val dict =
            makeDictionary(
                "Bil", "Dörr", "Motor", "Tak", "Borr", "Slag", "Hammar", "Pelar", "Glas", "Ögon",
                "Fodral", "Bas", "Fiol", "Makare", "Gesäll", "Sko", "Vind", "Rute", "Torkare", "Blad"
            )

        val tf =
            DictionaryCompoundWordTokenFilter(
                whitespaceMockTokenizer(
                    "Bildörr Bilmotor Biltak Slagborr Hammarborr Pelarborr Glasögonfodral Basfiolsfodral Basfiolsfodralmakaregesäll Skomakare Vindrutetorkare Vindrutetorkarblad abba"
                ),
                dict
            )

        assertTokenStreamContents(
            tf,
            arrayOf(
                "Bildörr", "Bil", "dörr", "Bilmotor", "Bil", "motor", "Biltak", "Bil", "tak",
                "Slagborr", "Slag", "borr", "Hammarborr", "Hammar", "borr", "Pelarborr", "Pelar",
                "borr", "Glasögonfodral", "Glas", "ögon", "fodral", "Basfiolsfodral", "Bas",
                "fiol", "fodral", "Basfiolsfodralmakaregesäll", "Bas", "fiol", "fodral",
                "makare", "gesäll", "Skomakare", "Sko", "makare", "Vindrutetorkare", "Vind",
                "rute", "torkare", "Vindrutetorkarblad", "Vind", "rute", "blad", "abba"
            )
        )
    }

    @Test
    fun testDecompoundingWithInvalidParameterCombination() {
        assertFailsWith<IllegalArgumentException> {
            DictionaryCompoundWordTokenFilter(
                whitespaceMockTokenizer("basketballkurv"),
                makeDictionary("basketball", "basket", "ball", "kurv"),
                CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE,
                CompoundWordTokenFilterBase.DEFAULT_MIN_SUBWORD_SIZE,
                40,
                onlyLongestMatch = false,
                reuseChars = false
            )
        }
    }
}
