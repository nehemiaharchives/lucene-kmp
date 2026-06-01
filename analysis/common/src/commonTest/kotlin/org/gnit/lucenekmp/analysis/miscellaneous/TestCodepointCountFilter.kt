package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestCodepointCountFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testFilterWithPosIncr() {
        val stream: TokenStream =
            whitespaceMockTokenizer("short toolong evenmuchlongertext a ab toolong foo")
        val filter = CodepointCountFilter(stream, 2, 6)
        assertTokenStreamContents(filter, arrayOf("short", "ab", "foo"), intArrayOf(1, 4, 2))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, CodepointCountFilter(tokenizer, 0, 5))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRandomStrings() {
        repeat(10000) {
            val text = TestUtil.randomUnicodeString(random(), 100)
            var min = TestUtil.nextInt(random(), 0, 100)
            var max = TestUtil.nextInt(random(), 0, 100)
            val count = Character.codePointCount(text.toCharArray(), 0, text.length)
            if (min > max) {
                val temp = min
                min = max
                max = temp
            }
            val expected = count >= min && count <= max
            var stream: TokenStream = KeywordTokenizer()
            (stream as Tokenizer).setReader(StringReader(text))
            stream = CodepointCountFilter(stream, min, max)
            stream.reset()
            assertEquals(expected, stream.incrementToken())
            stream.end()
            stream.close()
        }
    }

    /** checking the validity of constructor arguments */
    @Test
    @Throws(Exception::class)
    fun testIllegalArguments() {
        assertFailsWith<IllegalArgumentException> {
            CodepointCountFilter(whitespaceMockTokenizer("accept only valid arguments"), 4, 1)
        }
    }
}
