package org.gnit.lucenekmp.analysis.ig

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

/** Simple tests to ensure the Igbo filter Factories are working. */
class TestIgboFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test IgboNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testIgboNormalizer() {
        val reader: Reader = StringReader("akwụkwọ–ọzọ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IgboNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("akwukwo-ozo"))
    }

    /** Test IgboStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ikwughi")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("IgboStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("kwu"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedIgboNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IgboNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedIgboNorm.message?.contains("Unknown parameters") == true)

        val expectedIgboStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("IgboStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedIgboStem.message?.contains("Unknown parameters") == true)
    }
}
