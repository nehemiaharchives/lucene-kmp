package org.gnit.lucenekmp.analysis.yo

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

/** Simple tests to ensure the Yoruba filter Factories are working. */
class TestYorubaFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test YorubaNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testYorubaNormalizer() {
        val reader: Reader = StringReader("Yorùbá ṣé")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("YorubaNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("Yoruba", "se"))
    }

    /** Test YorubaStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("ikowe")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("YorubaStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("kowe"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedYorubaNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("YorubaNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedYorubaNorm.message?.contains("Unknown parameters") == true)

        val expectedYorubaStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("YorubaStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedYorubaStem.message?.contains("Unknown parameters") == true)
    }
}
