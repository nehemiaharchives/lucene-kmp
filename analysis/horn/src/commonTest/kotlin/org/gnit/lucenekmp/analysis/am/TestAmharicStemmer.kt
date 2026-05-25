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

/** Tests [AmharicStemmer] and [AmharicStemFilter]. */
class TestAmharicStemmer : BaseTokenStreamTestCase() {
    @Test
    fun testManualLemmaMappings() {
        assertEquals("አስፈለገ", stem("የማያስፈልጋትስ"))
        assertEquals("ነው", stem("አይደለችም"))
        assertEquals("መጣ", stem("ይመጣሉ"))
    }

    @Test
    fun testGeneratedDictionaryMappings() {
        assertEquals("hager", stem("ሀገር"))
        assertEquals("hager", stem("yehagerocn"))
        assertEquals("mT'", stem("na"))
    }

    @Test
    fun testLightStemmingAndPluralRepair() {
        assertEquals("መጽሐፍ", stem("መጽሐፎችን"))
        assertEquals("ዘመድ", stem("ለዘመዶቻችንም"))
    }

    @Test
    fun testShortAndUnknownTermsAreStable() {
        assertEquals("", stem(""))
        assertEquals("x", stem("x"))
        assertEquals("zzzzzz", stem("zzzzzz"))
    }

    @Test
    @Throws(IOException::class)
    fun testFilterMultipleTokens() {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("ሀገር yehagerocn zzzzzz"))
        val filter: TokenFilter = AmharicStemFilter(tokenizer)
        assertTokenStreamContents(filter, arrayOf("hager", "hager", "zzzzzz"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, AmharicStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }

    private fun stem(input: String): String {
        val buffer = CharArray(maxOf(input.length, 32))
        input.toCharArray().copyInto(buffer)
        val length = AmharicStemmer().stem(buffer, input.length)
        return buffer.concatToString(0, length)
    }
}
