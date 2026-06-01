package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestScandinavianNormalizationFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testDefault() {
        var stream: TokenStream = whitespaceMockTokenizer("räksmörgås_ae_oe_aa_oo_ao_AE_OE_AA_OO_AO")
        stream = tokenFilterFactory("ScandinavianNormalization").create(stream)
        assertTokenStreamContents(stream, arrayOf("ræksmørgås_æ_ø_å_ø_å_Æ_Ø_Å_Ø_Å"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("ScandinavianNormalization", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"), "Got ${expected.message}")
    }
}
