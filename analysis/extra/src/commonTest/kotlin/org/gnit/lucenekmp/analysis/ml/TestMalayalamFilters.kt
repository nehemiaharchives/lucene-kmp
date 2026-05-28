package org.gnit.lucenekmp.analysis.ml

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

/** Simple tests to ensure the Malayalam filter Factories are working. */
class TestMalayalamFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test MalayalamNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testMalayalamNormalizer() {
        val reader: Reader = StringReader("അവന്‍")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("MalayalamNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("അവൻ"))
    }

    /** Test MalayalamStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("പുസ്തകങ്ങൾ കുട്ടികളുടെ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IndicNormalization").create(stream)
        stream = tokenFilterFactory("MalayalamNormalization").create(stream)
        stream = tokenFilterFactory("MalayalamStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("പുസ്തക", "കുട്ടി"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedMalayalamNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("MalayalamNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedMalayalamNorm.message?.contains("Unknown parameters") == true)

        val expectedMalayalamStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("MalayalamStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedMalayalamStem.message?.contains("Unknown parameters") == true)
    }
}
