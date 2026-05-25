package org.gnit.lucenekmp.analysis.om

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [OromoStemmer] and [OromoStemFilter]. */
class TestOromoStemmer : BaseTokenStreamTestCase() {
    @Test
    fun testManualLemmaMappings() {
        assertEquals("afeeramuu", stem("afeeramaniiru"))
        assertEquals("dubbachuu", stem("dubbanne"))
        assertEquals("nama", stem("namoota"))
        assertEquals("mana", stem("manaan"))
    }

    @Test
    fun testGeneratedDictionaryMappings() {
        assertEquals("fedh", stem("fedhi"))
        assertEquals("ameerikaa", stem("ameerikaatti"))
        assertEquals("ameerikaanummaa", stem("ameerikaanummaa"))
    }

    @Test
    fun testLightSuffixFallback() {
        assertEquals("galmee", stem("galmeewwan"))
        assertEquals("mana", stem("manatti"))
    }

    @Test
    fun testShortAndUnknownTermsAreStable() {
        assertEquals("", stem(""))
        assertEquals("ab", stem("ab"))
        assertEquals("zzzzzz", stem("zzzzzz"))
    }

    @Test
    @Throws(IOException::class)
    fun testFilterMultipleTokens() {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("fedhi ameerikaatti zzzzzz"))
        val filter: TokenFilter = OromoStemFilter(tokenizer)
        assertTokenStreamContents(filter, arrayOf("fedh", "ameerikaa", "zzzzzz"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, OromoStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }

    private fun stem(input: String): String {
        val buffer = CharArray(maxOf(input.length, 32))
        input.toCharArray().copyInto(buffer)
        val length = OromoStemmer().stem(buffer, input.length)
        return buffer.concatToString(0, length)
    }
}
