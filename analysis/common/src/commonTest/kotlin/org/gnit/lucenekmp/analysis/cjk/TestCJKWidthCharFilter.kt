package org.gnit.lucenekmp.analysis.cjk

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

class TestCJKWidthCharFilter : BaseTokenStreamTestCase() {
    /** Full-width ASCII forms normalized to half-width (basic latin) */
    @Test
    @Throws(IOException::class)
    fun testFullWidthASCII() {
        val reader: CharFilter = CJKWidthCharFilter(StringReader("Ｔｅｓｔ １２３４"))
        val ts: TokenStream = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("Test", "1234"), intArrayOf(0, 5), intArrayOf(4, 9), 9)
    }

    /**
     * Half-width katakana forms normalized to standard katakana. A bit trickier in some cases, since
     * half-width forms are decomposed and voice marks need to be recombined with a preceding base
     * form.
     */
    @Test
    @Throws(IOException::class)
    fun testHalfWidthKana() {
        var reader: CharFilter = CJKWidthCharFilter(StringReader("ｶﾀｶﾅ"))
        var ts: TokenStream = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("カタカナ"), intArrayOf(0), intArrayOf(4), 4)

        reader = CJKWidthCharFilter(StringReader("ｳﾞｨｯﾂ"))
        ts = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("ヴィッツ"), intArrayOf(0), intArrayOf(5), 5)

        reader = CJKWidthCharFilter(StringReader("ﾊﾟﾅｿﾆｯｸ"))
        ts = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("パナソニック"), intArrayOf(0), intArrayOf(7), 7)

        reader = CJKWidthCharFilter(StringReader("ｳﾞｨｯﾂ ﾊﾟﾅｿﾆｯｸ"))
        ts = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(
            ts,
            arrayOf("ヴィッツ", "パナソニック"),
            intArrayOf(0, 6),
            intArrayOf(5, 13),
            13
        )
    }

    /** Input may contain orphan voiced marks that cannot be combined with the previous character. */
    @Test
    @Throws(Exception::class)
    fun testOrphanVoiceMark() {
        var reader: CharFilter = CJKWidthCharFilter(StringReader("ｱﾞｨｯﾂ"))
        var ts: TokenStream = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("ア\u3099ィッツ"), intArrayOf(0), intArrayOf(5), 5)

        reader = CJKWidthCharFilter(StringReader("ﾞｨｯﾂ"))
        ts = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("\u3099ィッツ"), intArrayOf(0), intArrayOf(4), 4)

        reader = CJKWidthCharFilter(StringReader("ｱﾟﾅｿﾆｯｸ"))
        ts = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("ア\u309Aナソニック"), intArrayOf(0), intArrayOf(7), 7)

        reader = CJKWidthCharFilter(StringReader("ﾟﾅｿﾆｯｸ"))
        ts = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("\u309Aナソニック"), intArrayOf(0), intArrayOf(6), 6)
    }

    @Test
    @Throws(Exception::class)
    fun testComplexInput() {
        var reader: CharFilter = CJKWidthCharFilter(StringReader("Ｔｅst １２34"))
        var ts: TokenStream = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, arrayOf("Test", "1234"), intArrayOf(0, 5), intArrayOf(4, 9), 9)

        reader = CJKWidthCharFilter(StringReader("ｶﾀカナ ｳﾞｨッツ ﾊﾟﾅｿニック"))
        ts = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(
            ts,
            arrayOf("カタカナ", "ヴィッツ", "パナソニック"),
            intArrayOf(0, 5, 11),
            intArrayOf(4, 10, 18),
            18
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyInput() {
        val reader: CharFilter = CJKWidthCharFilter(StringReader(""))
        val ts: TokenStream = whitespaceMockTokenizer(reader)
        assertTokenStreamContents(ts, emptyArray())
    }

    @Test
    @Throws(Exception::class)
    fun testRandom() {
        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(tokenizer, tokenizer)
                }

                override fun initReader(fieldName: String, reader: Reader): Reader {
                    return CJKWidthCharFilter(reader)
                }
            }
        val numRounds = RANDOM_MULTIPLIER * 1000
        checkRandomData(random(), analyzer, numRounds)
        analyzer.close()
    }
}
