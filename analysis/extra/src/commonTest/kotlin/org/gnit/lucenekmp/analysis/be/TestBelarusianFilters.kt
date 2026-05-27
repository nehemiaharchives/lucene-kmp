package org.gnit.lucenekmp.analysis.be

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

/** Simple tests to ensure the Belarusian filter Factories are working. */
class TestBelarusianFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test BelarusianNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testBelarusianNormalizer() {
        val reader: Reader = StringReader("пʼе у\u0306")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("BelarusianNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("п'е", "ў"))
    }

    /** Test BelarusianStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("мінску")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("BelarusianStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("мінск"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedBelarusianNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("BelarusianNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedBelarusianNorm.message?.contains("Unknown parameters") == true)

        val expectedBelarusianStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("BelarusianStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedBelarusianStem.message?.contains("Unknown parameters") == true)
    }
}
