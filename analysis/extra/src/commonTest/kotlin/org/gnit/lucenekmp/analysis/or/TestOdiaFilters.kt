package org.gnit.lucenekmp.analysis.or

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

/** Simple tests to ensure the Odia filter Factories are working. */
class TestOdiaFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test OdiaNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testOdiaNormalizer() {
        val reader: Reader = StringReader("ଓଡ଼ିଆ୤")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("OdiaNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ଓଡ଼ିଆ।"))
    }

    /** Test OdiaStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ଘରକୁ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("OdiaNormalization").create(stream)
        stream = tokenFilterFactory("OdiaStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("ଘର"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedOdiaNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("OdiaNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedOdiaNorm.message?.contains("Unknown parameters") == true)

        val expectedOdiaStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("OdiaStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedOdiaStem.message?.contains("Unknown parameters") == true)
    }
}
