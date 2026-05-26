package org.gnit.lucenekmp.analysis.ceb

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

/** Simple tests to ensure the Cebuano filter Factories are working. */
class TestCebuanoFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test CebuanoNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testCebuanoNormalizer() {
        val reader: Reader = StringReader("Cebuano")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("CebuanoNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("Cebuano"))
    }

    /** Test CebuanoStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("gipalitan")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("CebuanoStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("palit"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedCebuanoNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("CebuanoNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedCebuanoNorm.message?.contains("Unknown parameters") == true)

        val expectedCebuanoStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("CebuanoStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedCebuanoStem.message?.contains("Unknown parameters") == true)
    }
}
