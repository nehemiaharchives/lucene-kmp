package org.gnit.lucenekmp.analysis.ti

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

/** Simple tests to ensure the Tigrinya filter Factories are working. */
class TestTigrinyaFilters : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
            AnalysisExtraFactories.ensureInitialized()
        }
    }

    /** Test TigrinyaNormalizationFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testTigrinyaNormalizer() {
        val reader: Reader = StringReader("ሠላም ፀሓይ")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("TigrinyaNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ሰላም", "ጸሀይ"))
    }

    /** Test TigrinyaStemFilterFactory */
    @Test
    @Throws(Exception::class)
    fun testStemmer() {
        val reader: Reader = StringReader("መጽሀፍታት")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("TigrinyaStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("መጽሀፍ"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expectedTigrinyaNorm = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("TigrinyaNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expectedTigrinyaNorm.message?.contains("Unknown parameters") == true)

        val expectedTigrinyaStem = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("TigrinyaStem", "bogusArg", "bogusValue")
        }
        assertTrue(expectedTigrinyaStem.message?.contains("Unknown parameters") == true)
    }
}
