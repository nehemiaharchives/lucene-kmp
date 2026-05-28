package org.gnit.lucenekmp.analysis.no

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Norwegian Light stem factory is working. */
class TestNorwegianLightStemFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testStemming() {
        val reader: Reader = StringReader("epler eple")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("NorwegianLightStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("epl", "epl"))
    }

    /** Test stemming with variant set explicitly to Bokmål */
    @Test
    @Throws(Exception::class)
    fun testBokmaalStemming() {
        val reader: Reader = StringReader("epler eple")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("NorwegianLightStem", "variant", "nb").create(stream)
        assertTokenStreamContents(stream, arrayOf("epl", "epl"))
    }

    /** Test stemming with variant set explicitly to Nynorsk */
    @Test
    @Throws(Exception::class)
    fun testNynorskStemming() {
        val reader: Reader = StringReader("gutar gutane")
        var stream: TokenStream = whitespaceMockTokenizer(reader)
        stream = tokenFilterFactory("NorwegianLightStem", "variant", "nn").create(stream)
        assertTokenStreamContents(stream, arrayOf("gut", "gut"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("NorwegianLightStem", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}

