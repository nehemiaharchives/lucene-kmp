package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.AnalysisCommonFactories
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

class TestConcatenateGraphFilterFactory : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisCommonFactories.ensureInitialized()
    }

    @Test
    fun test() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val input = "A1 B2 A1 D4 C3"
            val reader: Reader = StringReader(input)
            val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
            tokenizer.setReader(reader)
            var stream: TokenStream = tokenizer
            stream = tokenFilterFactory("ConcatenateGraph", "tokenSeparator", "\u001F").create(stream)
            assertTokenStreamContents(
                stream,
                arrayOf(input.replace(' ', ConcatenateGraphFilter.SEP_LABEL.toChar()))
            )
        }
    }

    @Test
    fun testEmptyTokenSeparator() {
        val input = "A1 B2 A1 D4 C3"
        val output = "A1A1D4C3"
        val reader: Reader = StringReader(input)
        val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(reader)
        var stream: TokenStream = tokenizer
        stream = StopFilter(stream, StopFilter.makeStopSet("B2"))
        stream = tokenFilterFactory("ConcatenateGraph", "tokenSeparator", "").create(stream)
        assertTokenStreamContents(stream, arrayOf(output))
    }

    @Test
    fun testPreservePositionIncrements() {
        val input = "A1 B2 A1 D4 C3"
        val output = "A1 A1 D4 C3"
        val reader: Reader = StringReader(input)
        val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(reader)
        var stream: TokenStream = tokenizer
        stream = StopFilter(stream, StopFilter.makeStopSet("B2"))
        stream =
            tokenFilterFactory(
                "ConcatenateGraph",
                "tokenSeparator",
                "\u001F",
                "preservePositionIncrements",
                "false"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf(output.replace(' ', ConcatenateGraphFilter.SEP_LABEL.toChar())))
    }

    @Test
    fun testRequired() {
        tokenFilterFactory("ConcatenateGraph")
    }

    /** Test that bogus arguments result in exception */
    @Test
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("ConcatenateGraph", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }

    @Test
    fun testSeparator() {
        val input = "A B C D E F J H"
        val output = "B-C-F-H"
        val reader: Reader = StringReader(input)
        val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(reader)
        var stream: TokenStream = tokenizer
        stream = StopFilter(stream, StopFilter.makeStopSet("A", "D", "E", "J"))
        stream =
            tokenFilterFactory(
                "ConcatenateGraph",
                "tokenSeparator",
                "-",
                "preservePositionIncrements",
                "false"
            ).create(stream)
        assertTokenStreamContents(stream, arrayOf(output))
    }
}
