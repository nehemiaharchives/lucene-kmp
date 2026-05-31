package org.gnit.lucenekmp.analysis.classic

import org.gnit.lucenekmp.analysis.AnalysisSPIRegistry
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestClassicFactories : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            ClassicTokenizerFactory.NAME,
            ClassicTokenizerFactory::class,
            ::ClassicTokenizerFactory
        )
        AnalysisSPIRegistry.register(
            TokenFilterFactory::class,
            ClassicFilterFactory.NAME,
            ClassicFilterFactory::class,
            ::ClassicFilterFactory
        )
    }

    @Test
    fun testClassicTokenizer() {
        val reader: Reader = StringReader("What's this thing do?")
        val stream: Tokenizer = tokenizerFactory("Classic").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("What's", "this", "thing", "do"))
    }

    @Test
    fun testClassicTokenizerMaxTokenLength() {
        val builder = StringBuilder()
        for (i in 0..<100) {
            builder.append("abcdefg")
        }
        val longWord = builder.toString()
        val content = "one two three $longWord four five six"
        val reader: Reader = StringReader(content)
        val stream: Tokenizer =
            tokenizerFactory("Classic", "maxTokenLength", "1000").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("one", "two", "three", longWord, "four", "five", "six"))
    }

    @Test
    fun testClassicFilter() {
        val reader: Reader = StringReader("What's this thing do?")
        val tokenizer: Tokenizer = tokenizerFactory("Classic").create(newAttributeFactory())
        tokenizer.setReader(reader)
        val stream: TokenStream = tokenFilterFactory("Classic").create(tokenizer)
        assertTokenStreamContents(stream, arrayOf("What", "this", "thing", "do"))
    }

    @Test
    fun testBogusArguments() {
        var expected = expectThrows(IllegalArgumentException::class) {
            tokenizerFactory("Classic", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))

        expected = expectThrows(IllegalArgumentException::class) {
            tokenFilterFactory("Classic", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
