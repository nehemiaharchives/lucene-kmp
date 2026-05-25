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

/** Tests [OromoNormalizer] and [OromoNormalizationFilter]. */
class TestOromoNormalizer : BaseTokenStreamTestCase() {
    @Test
    fun testApostropheVariants() {
        assertEquals("a'a'a'a'a'a", normalize("a’a‘aʼa`a´a"))
    }

    @Test
    fun testKeepsUnmappedCharacters() {
        assertEquals("Ameerikaa-123", normalize("Ameerikaa-123"))
    }

    @Test
    @Throws(IOException::class)
    fun testFilterMultipleTokens() {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("gooftaaʼummaa aadaa`wwan"))
        val filter: TokenFilter = OromoNormalizationFilter(tokenizer)
        assertTokenStreamContents(filter, arrayOf("gooftaa'ummaa", "aadaa'wwan"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, OromoNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }

    private fun normalize(input: String): String {
        val buffer = input.toCharArray()
        val length = OromoNormalizer().normalize(buffer, buffer.size)
        return buffer.concatToString(0, length)
    }
}
