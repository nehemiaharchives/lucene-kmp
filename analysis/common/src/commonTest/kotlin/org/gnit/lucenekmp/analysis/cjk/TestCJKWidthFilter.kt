package org.gnit.lucenekmp.analysis.cjk

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests for [CJKWidthFilter] */
class TestCJKWidthFilter : BaseTokenStreamTestCase() {
    private val analyzer: Analyzer =
        object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                return TokenStreamComponents(source, CJKWidthFilter(source))
            }
        }

    /** Full-width ASCII forms normalized to half-width (basic latin) */
    @Test
    @Throws(IOException::class)
    fun testFullWidthASCII() {
        assertAnalyzesTo(
            analyzer,
            "Ｔｅｓｔ １２３４",
            arrayOf("Test", "1234"),
            intArrayOf(0, 5),
            intArrayOf(4, 9)
        )
    }

    /**
     * Half-width katakana forms normalized to standard katakana. A bit trickier in some cases, since
     * half-width forms are decomposed and voice marks need to be recombined with a preceding base
     * form.
     */
    @Test
    @Throws(IOException::class)
    fun testHalfWidthKana() {
        assertAnalyzesTo(analyzer, "ｶﾀｶﾅ", arrayOf("カタカナ"))
        assertAnalyzesTo(analyzer, "ｳﾞｨｯﾂ", arrayOf("ヴィッツ"))
        assertAnalyzesTo(analyzer, "ﾊﾟﾅｿﾆｯｸ", arrayOf("パナソニック"))
    }

    @Test
    @Throws(IOException::class)
    fun testRandomData() {
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, CJKWidthFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
