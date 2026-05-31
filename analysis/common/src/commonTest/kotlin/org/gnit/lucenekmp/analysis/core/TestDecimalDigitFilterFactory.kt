package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the digit normalization factory is working. */
class TestDecimalDigitFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Ensure the filter actually normalizes digits. */
    @Test
    @Throws(Exception::class)
    fun testNormalization() {
        val reader: Reader = StringReader("١٢٣٤")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("DecimalDigit").create(stream)
        assertTokenStreamContents(stream, arrayOf("1234"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("DecimalDigit", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}

