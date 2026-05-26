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
import kotlin.test.assertTrue

/** Tests for [CharReorderFilter]. */
class TestCharReorderFilter : BaseTokenStreamTestCase() {
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
        try {
            while (tokenStream.incrementToken()) {
                termList.add(charTermAttribute.toString())
            }
            assertEquals(expected, termList)
        } catch (e: Exception) {
            assertTrue(false, "Exception: ${e.message}")
        }
    }

    @Throws(Exception::class)
    private fun assertReorder(s: String, expected: List<String>) {
        val reader = StringReader(s)
        val toks = tokenize(reader, WhitespaceTokenizer())
        val res = CharReorderFilter(toks)
        assertTokenStream(res, expected)
    }

    @Test
    @Throws(Exception::class)
    fun testCanonicalReordering() {
        assertReorder(
            "ស្រ្តី ស្ត្រី ស្រ្ដី ស្ដ្រី ស្រី្ត ស្តី្រ ស្រី្ដ ស្ដី្រ សី្ត្រ សី្ដ្រ សី្រ្ត សី្រ្ដ",
            listOf("ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី", "ស្ត្រី")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testSplitVowelComposition() {
        assertReorder("ស៊ើ សើុ ស៊េី សេីុ សុើ", listOf("ស៊ើ", "ស៊ើ", "ស៊ើ", "ស៊ើ", "ស៊ើ"))
    }

    @Test
    @Throws(Exception::class)
    fun testSelectedCorrectCharacterReplacements() {
        assertReorder(
            "ប្តី ផ្តើម ផ្តល់ ម្តង កណ្តាល",
            listOf("ប្ដី", "ផ្ដើម", "ផ្ដល់", "ម្ដង", "កណ្ដាល")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNonKhmerAndNumericTokensRemainUnchanged() {
        assertReorder("abc _ ១២៣", listOf("abc", "_", "១២៣"))
    }

    @Test
    @Throws(Exception::class)
    fun testDuplicateCoengCollapsed() {
        assertReorder("ក្្", listOf("ក្"))
    }
}
