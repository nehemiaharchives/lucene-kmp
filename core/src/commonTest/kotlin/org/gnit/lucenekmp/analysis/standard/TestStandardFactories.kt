package org.gnit.lucenekmp.analysis.standard

import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.analysis.AnalysisSPIRegistry
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamFactoryTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple tests to ensure the standard lucene factories are working. */
class TestStandardFactories : BaseTokenStreamFactoryTestCase() {
    init {
        AnalysisSPIRegistry.register(
            TokenizerFactory::class,
            StandardTokenizerFactory.NAME,
            StandardTokenizerFactory::class,
            ::StandardTokenizerFactory
        )
    }

    /** Test StandardTokenizerFactory */
    @Test
    @Throws(Exception::class)
    fun testStandardTokenizer() {
        val reader: Reader = StringReader("Wha\u0301t's this thing do?")
        val stream: Tokenizer = tokenizerFactory("Standard").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(stream, arrayOf("Wha\u0301t's", "this", "thing", "do"))
    }

    @Test
    @Throws(Exception::class)
    fun testStandardTokenizerMaxTokenLength() {
        val builder = StringBuilder()
        for (i in 0..<100) {
            builder.append("abcdefg") // 7 * 100 = 700 char "word"
        }
        val longWord = builder.toString()
        val content = "one two three $longWord four five six"
        val reader: Reader = StringReader(content)
        val stream: Tokenizer =
            tokenizerFactory("Standard", "maxTokenLength", "1000").create(newAttributeFactory())
        stream.setReader(reader)
        assertTokenStreamContents(
            stream, arrayOf("one", "two", "three", longWord, "four", "five", "six")
        )
    }

    /** Test that bogus arguments result in exception */
    @Test
    @Throws(Exception::class)
    fun testBogusArguments() {
        val expected = expectThrows(IllegalArgumentException::class) {
            tokenizerFactory("Standard", "bogusArg", "bogusValue")
        }
        assertTrue(expected.message!!.contains("Unknown parameters"))
    }
}
