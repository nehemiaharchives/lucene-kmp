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

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests CommonGrams(Query)Filter */
class TestCommonGramsFilter : BaseTokenStreamTestCase() {

    @Test
    fun testReset() {
        val input = "How the s a brown s cow d like A B thing?"
        val wt = WhitespaceTokenizer()
        wt.setReader(StringReader(input))
        val cgf = CommonGramsFilter(wt, commonWords)

        val term = cgf.addAttribute(CharTermAttribute::class)
        cgf.reset()
        assertTrue(cgf.incrementToken())
        assertEquals("How", term.toString())
        assertTrue(cgf.incrementToken())
        assertEquals("How_the", term.toString())
        assertTrue(cgf.incrementToken())
        assertEquals("the", term.toString())
        assertTrue(cgf.incrementToken())
        assertEquals("the_s", term.toString())
        cgf.close()

        wt.setReader(StringReader(input))
        cgf.reset()
        assertTrue(cgf.incrementToken())
        assertEquals("How", term.toString())
    }

    @Test
    fun testQueryReset() {
        val input = "How the s a brown s cow d like A B thing?"
        val wt = WhitespaceTokenizer()
        wt.setReader(StringReader(input))
        val cgf = CommonGramsFilter(wt, commonWords)
        val nsf = CommonGramsQueryFilter(cgf)

        val term = wt.addAttribute(CharTermAttribute::class)
        nsf.reset()
        assertTrue(nsf.incrementToken())
        assertEquals("How_the", term.toString())
        assertTrue(nsf.incrementToken())
        assertEquals("the_s", term.toString())
        nsf.close()

        wt.setReader(StringReader(input))
        nsf.reset()
        assertTrue(nsf.incrementToken())
        assertEquals("How_the", term.toString())
    }

    /**
     * This is for testing CommonGramsQueryFilter which outputs a set of tokens optimized for querying
     * with only one token at each position, either a unigram or a bigram It also will not return a
     * token for the final position if the final word is already in the preceding bigram
     * Example:(three tokens/positions in) "foo bar the"=>"foo:1|bar:2,bar-the:2|the:3=> "foo"
     * "bar-the" (2 tokens out)
     */
    @Test
    fun testCommonGramsQueryFilter() {
        val a = object : Analyzer() {
            override fun createComponents(field: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                return TokenStreamComponents(
                    tokenizer,
                    CommonGramsQueryFilter(CommonGramsFilter(tokenizer, commonWords))
                )
            }
        }

        // Stop words used below are "of" "the" and "s"

        // two word queries
        assertAnalyzesTo(a, "brown fox", arrayOf("brown", "fox"))
        assertAnalyzesTo(a, "the fox", arrayOf("the_fox"))
        assertAnalyzesTo(a, "fox of", arrayOf("fox_of"))
        assertAnalyzesTo(a, "of the", arrayOf("of_the"))

        // one word queries
        assertAnalyzesTo(a, "the", arrayOf("the"))
        assertAnalyzesTo(a, "foo", arrayOf("foo"))

        // 3 word combinations s=stopword/common word n=not a stop word
        assertAnalyzesTo(a, "n n n", arrayOf("n", "n", "n"))
        assertAnalyzesTo(a, "quick brown fox", arrayOf("quick", "brown", "fox"))

        assertAnalyzesTo(a, "n n s", arrayOf("n", "n_s"))
        assertAnalyzesTo(a, "quick brown the", arrayOf("quick", "brown_the"))

        assertAnalyzesTo(a, "n s n", arrayOf("n_s", "s_n"))
        assertAnalyzesTo(a, "quick the brown", arrayOf("quick_the", "the_brown"))

        assertAnalyzesTo(a, "n s s", arrayOf("n_s", "s_s"))
        assertAnalyzesTo(a, "fox of the", arrayOf("fox_of", "of_the"))

        assertAnalyzesTo(a, "s n n", arrayOf("s_n", "n", "n"))
        assertAnalyzesTo(a, "the quick brown", arrayOf("the_quick", "quick", "brown"))

        assertAnalyzesTo(a, "s n s", arrayOf("s_n", "n_s"))
        assertAnalyzesTo(a, "the fox of", arrayOf("the_fox", "fox_of"))

        assertAnalyzesTo(a, "s s n", arrayOf("s_s", "s_n"))
        assertAnalyzesTo(a, "of the fox", arrayOf("of_the", "the_fox"))

        assertAnalyzesTo(a, "s s s", arrayOf("s_s", "s_s"))
        assertAnalyzesTo(a, "of the of", arrayOf("of_the", "the_of"))
        a.close()
    }

    @Test
    fun testCommonGramsFilter() {
        val a = object : Analyzer() {
            override fun createComponents(field: String): TokenStreamComponents {
                val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                return TokenStreamComponents(
                    tokenizer, CommonGramsFilter(tokenizer, commonWords)
                )
            }
        }

        // Stop words used below are "of" "the" and "s"
        // one word queries
        assertAnalyzesTo(a, "the", arrayOf("the"))
        assertAnalyzesTo(a, "foo", arrayOf("foo"))

        // two word queries
        assertAnalyzesTo(a, "brown fox", arrayOf("brown", "fox"), intArrayOf(1, 1))
        assertAnalyzesTo(a, "the fox", arrayOf("the", "the_fox", "fox"), intArrayOf(1, 0, 1))
        assertAnalyzesTo(a, "fox of", arrayOf("fox", "fox_of", "of"), intArrayOf(1, 0, 1))
        assertAnalyzesTo(a, "of the", arrayOf("of", "of_the", "the"), intArrayOf(1, 0, 1))

        // 3 word combinations s=stopword/common word n=not a stop word
        assertAnalyzesTo(a, "n n n", arrayOf("n", "n", "n"), intArrayOf(1, 1, 1))
        assertAnalyzesTo(
            a, "quick brown fox", arrayOf("quick", "brown", "fox"), intArrayOf(1, 1, 1)
        )

        assertAnalyzesTo(a, "n n s", arrayOf("n", "n", "n_s", "s"), intArrayOf(1, 1, 0, 1))
        assertAnalyzesTo(
            a,
            "quick brown the",
            arrayOf("quick", "brown", "brown_the", "the"),
            intArrayOf(1, 1, 0, 1)
        )

        assertAnalyzesTo(
            a, "n s n", arrayOf("n", "n_s", "s", "s_n", "n"), intArrayOf(1, 0, 1, 0, 1)
        )
        assertAnalyzesTo(
            a,
            "quick the fox",
            arrayOf("quick", "quick_the", "the", "the_fox", "fox"),
            intArrayOf(1, 0, 1, 0, 1)
        )

        assertAnalyzesTo(
            a, "n s s", arrayOf("n", "n_s", "s", "s_s", "s"), intArrayOf(1, 0, 1, 0, 1)
        )
        assertAnalyzesTo(
            a,
            "fox of the",
            arrayOf("fox", "fox_of", "of", "of_the", "the"),
            intArrayOf(1, 0, 1, 0, 1)
        )

        assertAnalyzesTo(a, "s n n", arrayOf("s", "s_n", "n", "n"), intArrayOf(1, 0, 1, 1))
        assertAnalyzesTo(
            a,
            "the quick brown",
            arrayOf("the", "the_quick", "quick", "brown"),
            intArrayOf(1, 0, 1, 1)
        )

        assertAnalyzesTo(
            a, "s n s", arrayOf("s", "s_n", "n", "n_s", "s"), intArrayOf(1, 0, 1, 0, 1)
        )
        assertAnalyzesTo(
            a,
            "the fox of",
            arrayOf("the", "the_fox", "fox", "fox_of", "of"),
            intArrayOf(1, 0, 1, 0, 1)
        )

        assertAnalyzesTo(
            a, "s s n", arrayOf("s", "s_s", "s", "s_n", "n"), intArrayOf(1, 0, 1, 0, 1)
        )
        assertAnalyzesTo(
            a,
            "of the fox",
            arrayOf("of", "of_the", "the", "the_fox", "fox"),
            intArrayOf(1, 0, 1, 0, 1)
        )

        assertAnalyzesTo(
            a, "s s s", arrayOf("s", "s_s", "s", "s_s", "s"), intArrayOf(1, 0, 1, 0, 1)
        )
        assertAnalyzesTo(
            a,
            "of the of",
            arrayOf("of", "of_the", "the", "the_of", "of"),
            intArrayOf(1, 0, 1, 0, 1)
        )
        a.close()
    }

    /** Test that CommonGramsFilter works correctly in case-insensitive mode */
    @Test
    fun testCaseSensitive() {
        val input = "How The s a brown s cow d like A B thing?"
        val wt = MockTokenizer(MockTokenizer.WHITESPACE, false)
        wt.setReader(StringReader(input))
        val cgf: TokenFilter = CommonGramsFilter(wt, commonWords)
        assertTokenStreamContents(
            cgf,
            arrayOf(
                "How", "The", "The_s", "s", "s_a", "a", "a_brown", "brown", "brown_s", "s", "s_cow",
                "cow", "cow_d", "d", "d_like", "like", "A", "B", "thing?"
            )
        )
    }

    /** Test CommonGramsQueryFilter in the case that the last word is a stopword */
    @Test
    fun testLastWordisStopWord() {
        val input = "dog the"
        val wt = MockTokenizer(MockTokenizer.WHITESPACE, false)
        wt.setReader(StringReader(input))
        val cgf = CommonGramsFilter(wt, commonWords)
        val nsf: TokenFilter = CommonGramsQueryFilter(cgf)
        assertTokenStreamContents(nsf, arrayOf("dog_the"))
    }

    /** Test CommonGramsQueryFilter in the case that the first word is a stopword */
    @Test
    fun testFirstWordisStopWord() {
        val input = "the dog"
        val wt = MockTokenizer(MockTokenizer.WHITESPACE, false)
        wt.setReader(StringReader(input))
        val cgf = CommonGramsFilter(wt, commonWords)
        val nsf: TokenFilter = CommonGramsQueryFilter(cgf)
        assertTokenStreamContents(nsf, arrayOf("the_dog"))
    }

    /** Test CommonGramsQueryFilter in the case of a single (stop)word query */
    @Test
    fun testOneWordQueryStopWord() {
        val input = "the"
        val wt = MockTokenizer(MockTokenizer.WHITESPACE, false)
        wt.setReader(StringReader(input))
        val cgf = CommonGramsFilter(wt, commonWords)
        val nsf: TokenFilter = CommonGramsQueryFilter(cgf)
        assertTokenStreamContents(nsf, arrayOf("the"))
    }

    /** Test CommonGramsQueryFilter in the case of a single word query */
    @Test
    fun testOneWordQuery() {
        val input = "monster"
        val wt = MockTokenizer(MockTokenizer.WHITESPACE, false)
        wt.setReader(StringReader(input))
        val cgf = CommonGramsFilter(wt, commonWords)
        val nsf: TokenFilter = CommonGramsQueryFilter(cgf)
        assertTokenStreamContents(nsf, arrayOf("monster"))
    }

    /** Test CommonGramsQueryFilter when first and last words are stopwords. */
    @Test
    fun TestFirstAndLastStopWord() {
        val input = "the of"
        val wt = MockTokenizer(MockTokenizer.WHITESPACE, false)
        wt.setReader(StringReader(input))
        val cgf = CommonGramsFilter(wt, commonWords)
        val nsf: TokenFilter = CommonGramsQueryFilter(cgf)
        assertTokenStreamContents(nsf, arrayOf("the_of"))
    }

    /** blast some random strings through the analyzer */
    @Test
    fun testRandomStrings() {
        val a = object : Analyzer() {

            override fun createComponents(fieldName: String): TokenStreamComponents {
                val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val cgf = CommonGramsFilter(t, commonWords)
                return TokenStreamComponents(t, cgf)
            }
        }

        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()

        val b = object : Analyzer() {

            override fun createComponents(fieldName: String): TokenStreamComponents {
                val t: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val cgf = CommonGramsFilter(t, commonWords)
                return TokenStreamComponents(t, CommonGramsQueryFilter(cgf))
            }
        }

        checkRandomData(random(), b, 200 * RANDOM_MULTIPLIER)
        b.close()
    }

    companion object {
        private val commonWords =
            CharArraySet(mutableListOf<Any>("s", "a", "b", "c", "d", "the", "of"), false)
    }
}
