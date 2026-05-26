package org.gnit.lucenekmp.analysis.ilo

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

/** Simple tests to ensure the Ilocano filter Factories are working. */
class TestIlocanoFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test IlocanoNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testIlocanoNormalizer() {
        val reader: Reader = StringReader("Ilokáno–Pagsasao")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IlocanoNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("Ilokano-Pagsasao"))
    }

    /** Test IlocanoStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("nagadal")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IlocanoStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("adal"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedIlocanoNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IlocanoNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedIlocanoNorm.message?.contains("Unknown parameters") == true)

        val expectedIlocanoStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IlocanoStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedIlocanoStem.message?.contains("Unknown parameters") == true)
    }
}
