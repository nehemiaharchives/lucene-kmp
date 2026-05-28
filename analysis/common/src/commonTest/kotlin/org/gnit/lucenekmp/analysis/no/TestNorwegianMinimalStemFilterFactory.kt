package org.gnit.lucenekmp.analysis.no

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simple tests to ensure the Norwegian Minimal stem factory is working. */
class TestNorwegianMinimalStemFilterFactory : BaseTokenStreamFactoryTestCase() {
    companion object {
        init {
            AnalysisCommonFactories.ensureInitialized()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testStemming() {
        val reader: Reader = StringReader("eple eplet epler eplene eplets eplenes")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("NorwegianMinimalStem").create(stream)
        assertTokenStreamContents(stream, arrayOf("epl", "epl", "epl", "epl", "epl", "epl"))
    }

    /** Test stemming with variant set explicitly to Bokmål */
    @Test
    @Throws(Exception::class)
    fun testBokmaalStemming() {
        val reader: Reader = StringReader("eple eplet epler eplene eplets eplenes")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("NorwegianMinimalStem", "variant", "nb").create(stream)
        assertTokenStreamContents(stream, arrayOf("epl", "epl", "epl", "epl", "epl", "epl"))
    }

    /** Test stemming with variant set explicitly to Nynorsk */
    @Test
    @Throws(Exception::class)
    fun testNynorskStemming() {
        val reader: Reader = StringReader("gut guten gutar gutane gutens gutanes")
        var stream: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        (stream as Tokenizer).setReader(reader)
        stream = tokenFilterFactory("NorwegianMinimalStem", "variant", "nn").create(stream)
        assertTokenStreamContents(stream, arrayOf("gut", "gut", "gut", "gut", "gut", "gut"))
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = assertFailsWith<IllegalArgumentException> {
            tokenFilterFactory("NorwegianMinimalStem", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message?.contains("Unknown parameters") == true)
    }
}

