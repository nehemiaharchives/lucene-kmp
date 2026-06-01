package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure the miscellaneous lucene factories are working. */
class TestMiscellaneousFactories : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    /** Ensure the ASCIIFoldingFilterFactory works */
    @Test
    fun testASCIIFolding() {
        val reader: Reader = StringReader("Česká")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("ASCIIFolding").create(stream)
        assertTokenStreamContents(stream, arrayOf("Ceska"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("ASCIIFolding", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
