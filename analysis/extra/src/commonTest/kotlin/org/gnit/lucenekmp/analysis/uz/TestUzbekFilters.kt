package org.gnit.lucenekmp.analysis.uz

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.AnalysisExtraFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Uzbek filter Factories are working. */
class TestUzbekFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test UzbekNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testUzbekNormalizer() {
        val reader: Reader = StringReader("Oʻzbek–g‘isht")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("UzbekNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("O'zbek-g'isht"))
    }

    /** Test UzbekStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("kitoblardan uylarimizdan")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("UzbekStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("kitob", "uy"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedUzbekNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("UzbekNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedUzbekNorm.message?.contains("Unknown parameters") == true)

        val expectedUzbekStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("UzbekStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedUzbekStem.message?.contains("Unknown parameters") == true)
    }
}
