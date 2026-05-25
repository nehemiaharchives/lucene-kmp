package org.gnit.lucenekmp.analysis.am

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

/** Tests [AmharicNormalizer] and [AmharicNormalizationFilter]. */
class TestAmharicNormalizer : BaseTokenStreamTestCase() {
    @Test
    fun testOrthographicVariants() {
        assertEquals("ሀሁሂሄህሆ", normalize("ሃሑሒሔሕሖ"))
        assertEquals("ሰሱሲሳሴስሶ", normalize("ሠሡሢሣሤሥሦ"))
        assertEquals("አኡኢኣኤእኦ", normalize("ዐዑዒዓዔዕዖ"))
        assertEquals("ፀፁፂፃፄፅፆ", normalize("ጸጹጺጻጼጽጾ"))
    }

    @Test
    fun testKeepsUnmappedCharacters() {
        assertEquals("abc-123", normalize("abc-123"))
        assertEquals("ሀገር", normalize("ሀገር"))
    }

    @Test
    @Throws(IOException::class)
    fun testFilterMultipleTokens() {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("ሃገር ሠላም ጽሑፍ"))
        val filter: TokenFilter = AmharicNormalizationFilter(tokenizer)
        assertTokenStreamContents(filter, arrayOf("ሀገር", "ሰላም", "ፅሁፍ"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, AmharicNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }

    private fun normalize(input: String): String {
        val buffer = input.toCharArray()
        val length = AmharicNormalizer().normalize(buffer, buffer.size)
        return buffer.concatToString(0, length)
    }
}
