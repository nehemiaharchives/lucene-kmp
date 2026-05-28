package org.gnit.lucenekmp.analysis.my

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

/** Simple tests to ensure the Burmese filter Factories are working. */
class TestBurmeseFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test BurmeseNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testBurmeseNormalizer() {
        val reader: Reader = StringReader("၁၂၃၄\u200B")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("BurmeseNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("1234"))
    }

    /** Test BurmeseStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("စာအုပ်တွေ လူများ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("BurmeseStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("စာအုပ်", "လူ"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedBurmeseNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("BurmeseNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedBurmeseNorm.message?.contains("Unknown parameters") == true)

        val expectedBurmeseStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("BurmeseStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedBurmeseStem.message?.contains("Unknown parameters") == true)
    }
}
