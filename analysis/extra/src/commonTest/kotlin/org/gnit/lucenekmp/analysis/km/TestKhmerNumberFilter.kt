package org.gnit.lucenekmp.analysis.km

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.WhitespaceTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for [KhmerNumberFilter]. */
class TestKhmerNumberFilter : BaseTokenStreamTestCase() {
    @Throws(Exception::class)
    private fun tokenize(reader: Reader, tokenizer: Tokenizer): TokenStream {
        tokenizer.close()
        tokenizer.end()
        tokenizer.setReader(reader)
        tokenizer.reset()
        return tokenizer
    }

    private fun assertTokenStream(tokenStream: TokenStream, expected: List<String>) {
        val termList = mutableListOf<String>()
        val charTermAttribute: CharTermAttribute = tokenStream.addAttribute(CharTermAttribute::class)
        while (tokenStream.incrementToken()) {
            termList.add(charTermAttribute.toString())
        }
        assertEquals(expected, termList)
    }

    @Test
    @Throws(Exception::class)
    fun testIncrementTokenNormalizesKhmerDigits() {
        val reader = StringReader("១២៣៤៥.៦៧ 12345.67 ABC")
        val toks = tokenize(reader, WhitespaceTokenizer())
        val res = KhmerNumberFilter(toks)
        assertTokenStream(res, listOf("12345.67", "12345.67", "ABC"))
    }

    @Test
    fun testNormalizeNumber() {
        val filter = KhmerNumberFilter(WhitespaceTokenizer())
        assertEquals("0123456789,.", filter.normalizeNumber("០១២៣៤៥៦៧៨៩,."))
    }

    @Test
    fun testIsNumeralString() {
        val filter = KhmerNumberFilter(WhitespaceTokenizer())
        assertTrue(filter.isNumeral("១២៣៤៥"))
        assertTrue(filter.isNumeral("១២៣,៤៥"))
        assertFalse(filter.isNumeral("១២៣abc"))
    }

    @Test
    fun testIsNumeralChar() {
        val filter = KhmerNumberFilter(WhitespaceTokenizer())
        assertTrue(filter.isNumeral('១'))
        assertTrue(filter.isNumeral('.'))
        assertFalse(filter.isNumeral('ក'))
    }
}
