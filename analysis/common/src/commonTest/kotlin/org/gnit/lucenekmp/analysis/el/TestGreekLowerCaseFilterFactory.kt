package org.gnit.lucenekmp.analysis.el

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Greek lowercase filter factory is working. */
class TestGreekLowerCaseFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    /** Ensure the filter actually lowercases (and a bit more) greek text. */
    @Test
    fun testNormalization() {
        var stream: TokenStream = whitespaceMockTokenizer("Μάϊος ΜΆΪΟΣ")
        stream = tokenFilterFactory("GreekLowerCase").create(stream)
        assertTokenStreamContents(stream, arrayOf("μαιοσ", "μαιοσ"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("GreekLowerCase", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}

