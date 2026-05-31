package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class TestUnicodeWhitespaceTokenizer : BaseTokenStreamTestCase() {
    // clone of test from WhitespaceTokenizer
    @Test
    @Throws(IOException::class)
    fun testSimple() {
        val reader = StringReader("Tokenizer \ud801\udc1ctest")
        val tokenizer = UnicodeWhitespaceTokenizer()
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Tokenizer", "\ud801\udc1ctest"))
    }

    @Test
    @Throws(IOException::class)
    fun testNBSP() {
        val reader = StringReader("Tokenizer\u00A0test")
        val tokenizer = UnicodeWhitespaceTokenizer()
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Tokenizer", "test"))
    }

    @Test
    fun testFactory() {
        val args: MutableMap<String, String> = mutableMapOf()
        args["rule"] = "unicode"
        val factory = WhitespaceTokenizerFactory(args)
        val attributeFactory: AttributeFactory = newAttributeFactory()
        val tokenizer: Tokenizer = factory.create(attributeFactory)
        assertEquals(UnicodeWhitespaceTokenizer::class, tokenizer::class)
    }

    private fun makeArgs(vararg args: String): MutableMap<String, String> {
        val ret: MutableMap<String, String> = mutableMapOf()
        var idx = 0
        while (idx < args.size) {
            ret[args[idx]] = args[idx + 1]
            idx += 2
        }
        return ret
    }

    @Test
    @Throws(IOException::class)
    fun testParamsFactory() {
        // negative maxTokenLen
        var iae = expectThrows(IllegalArgumentException::class) {
            WhitespaceTokenizerFactory(makeArgs("rule", "unicode", "maxTokenLen", "-1"))
        }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: -1", iae.message)

        // zero maxTokenLen
        iae = expectThrows(IllegalArgumentException::class) {
            WhitespaceTokenizerFactory(makeArgs("rule", "unicode", "maxTokenLen", "0"))
        }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 0", iae.message)

        // Added random param, should throw illegal error
        iae = expectThrows(IllegalArgumentException::class) {
            WhitespaceTokenizerFactory(
                makeArgs("rule", "unicode", "maxTokenLen", "255", "randomParam", "rValue")
            )
        }
        assertEquals("Unknown parameters: {randomParam=rValue}", iae.message)

        // tokeniser will split at 5, Token | izer, no matter what happens
        var factory = WhitespaceTokenizerFactory(makeArgs("rule", "unicode", "maxTokenLen", "5"))
        var attributeFactory: AttributeFactory = newAttributeFactory()
        var tokenizer: Tokenizer = factory.create(attributeFactory)
        var reader = StringReader("Tokenizer \ud801\udc1ctest")
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Token", "izer", "\ud801\udc1ctes", "t"))

        // tokeniser will split at 2, To | ke | ni | ze | r, no matter what happens
        factory = WhitespaceTokenizerFactory(makeArgs("rule", "unicode", "maxTokenLen", "2"))
        attributeFactory = newAttributeFactory()
        tokenizer = factory.create(attributeFactory)
        reader = StringReader("Tokenizer\u00A0test")
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("To", "ke", "ni", "ze", "r", "te", "st"))

        // tokeniser will split at 10, no matter what happens,
        // but tokens' length are less than that
        factory = WhitespaceTokenizerFactory(makeArgs("rule", "unicode", "maxTokenLen", "10"))
        attributeFactory = newAttributeFactory()
        tokenizer = factory.create(attributeFactory)
        reader = StringReader("Tokenizer\u00A0test")
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Tokenizer", "test"))
    }
}

