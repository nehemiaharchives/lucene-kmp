package org.gnit.lucenekmp.analysis.el

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Greek stem filter factory is working. */
class TestGreekStemFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    fun testStemming() {
        var stream: TokenStream = whitespaceMockTokenizer("άνθρωπος")
        stream = tokenFilterFactory("GreekLowerCase").create(stream)
        stream = tokenFilterFactory("GreekStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("ανθρωπ"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("GreekStem", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}

