package org.gnit.lucenekmp.analysis.util

import okio.IOException
import org.gnit.lucenekmp.analysis.core.KeywordTokenizerFactory
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class TestKeywordTokenizer : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testSimple() {
        val reader = StringReader("Tokenizer \ud801\udc1ctest")
        val tokenizer = KeywordTokenizer()
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Tokenizer \ud801\udc1ctest"))
    }

    @Test
    fun testFactory() {
        val args: MutableMap<String, String> = mutableMapOf()
        val factory = KeywordTokenizerFactory(args)
        val attributeFactory: AttributeFactory = newAttributeFactory()
        val tokenizer: Tokenizer = factory.create(attributeFactory)
        assertEquals(KeywordTokenizer::class, tokenizer::class)
    }

    private fun makeArgs(vararg args: String): MutableMap<String, String> {
        val ret: MutableMap<String, String> = mutableMapOf()
        var idx = 0
        while (idx < args.size) {
            ret.put(args[idx], args[idx + 1])
            idx += 2
        }
        return ret
    }

    @Test
    @Throws(IOException::class)
    fun testParamsFactory() {
        // negative maxTokenLen
        var iae: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) {
                KeywordTokenizerFactory(
                    makeArgs("maxTokenLen", "-1")
                )
            }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: -1", iae.message)

        // zero maxTokenLen
        iae = expectThrows(IllegalArgumentException::class) {
            KeywordTokenizerFactory(
                makeArgs("maxTokenLen", "0")
            )
        }
        assertEquals("maxTokenLen must be greater than 0 and less than 1048576 passed: 0", iae.message)

        // Added random param, should throw illegal error
        iae = expectThrows(IllegalArgumentException::class) {
            KeywordTokenizerFactory(
                makeArgs("maxTokenLen", "255", "randomParam", "rValue")
            )
        }
        assertEquals("Unknown parameters: {randomParam=rValue}", iae.message)

        // tokeniser will never split, no matter what is passed,
        // but the buffer will not be more than length of the token
        var factory = KeywordTokenizerFactory(makeArgs("maxTokenLen", "5"))
        var attributeFactory: AttributeFactory = newAttributeFactory()
        var tokenizer: Tokenizer = factory.create(attributeFactory)
        var reader = StringReader("Tokenizertest")
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Tokenizertest"))

        // tokeniser will never split, no matter what is passed,
        // but the buffer will not be more than length of the token
        factory = KeywordTokenizerFactory(makeArgs("maxTokenLen", "2"))
        attributeFactory = newAttributeFactory()
        tokenizer = factory.create(attributeFactory)
        reader = StringReader("Tokenizer\u00A0test")
        tokenizer.setReader(reader)
        assertTokenStreamContents(tokenizer, arrayOf("Tokenizer\u00A0test"))
    }
}
