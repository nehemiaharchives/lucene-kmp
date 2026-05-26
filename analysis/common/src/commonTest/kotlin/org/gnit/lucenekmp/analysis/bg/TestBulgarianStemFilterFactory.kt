package org.gnit.lucenekmp.analysis.bg

import org.gnit.lucenekmp.analysis.AnalysisSPIRegistry
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure the Bulgarian stem filter factory is working. */
class TestBulgarianStemFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            BulgarianStemFilterFactory.NAME,
            BulgarianStemFilterFactory::class,
            ::BulgarianStemFilterFactory
        )
    }

    /** Ensure the filter actually stems text. */
    @Test
    @Throws(Exception::class)
    fun testStemming() {
        val reader: Reader = StringReader("компютри")
        val tokenizer: Tokenizer = whitespaceMockTokenizer(reader)
        val stream: TokenStream = tokenFilterFactory("BulgarianStem").create(tokenizer)
        assertTokenStreamContents(stream, arrayOf("компютр"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected =
            expectThrows(IllegalArgumentException::class) {
                tokenFilterFactory("BulgarianStem", "bogusArg", "bogusValue")
            }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
