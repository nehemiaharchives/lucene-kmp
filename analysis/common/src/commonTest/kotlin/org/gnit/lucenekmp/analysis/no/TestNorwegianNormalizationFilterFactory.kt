package org.gnit.lucenekmp.analysis.no

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestNorwegianNormalizationFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDefault() {
        var stream: TokenStream = whitespaceMockTokenizer("räksmörgås_ae_oe_aa_oo_ao_AE_OE_AA_OO_AO")
        stream = tokenFilterFactory("NorwegianNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ræksmørgås_æ_ø_å_oo_ao_Æ_Ø_Å_OO_AO"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("NorwegianNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}

