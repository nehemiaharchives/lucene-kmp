package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestScandinavianFoldingFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun testStemming() {
        val reader = StringReader("räksmörgås")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("ScandinavianFolding").create(stream)
        assertTokenStreamContents(stream, arrayOf("raksmorgas"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("ScandinavianFolding", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
