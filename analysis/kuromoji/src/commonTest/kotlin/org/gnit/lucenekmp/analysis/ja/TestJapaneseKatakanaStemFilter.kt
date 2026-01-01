package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests for [JapaneseKatakanaStemFilter] */
class TestJapaneseKatakanaStemFilter : BaseTokenStreamTestCase() {
    private var analyzer: Analyzer? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    return TokenStreamComponents(source, JapaneseKatakanaStemFilter(source))
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        analyzer!!.close()
        /*super.tearDown()*/
    }

    /**
     * Test a few common katakana spelling variations.
     *
     * English translations are as follows:
     * - copy
     * - coffee
     * - taxi
     * - party
     * - party (without long sound)
     * - center
     *
     * Note that we remove a long sound in the case of "coffee" that is required.
     */
    @Test
    @Throws(IOException::class)
    fun testStemVariants() {
        assertAnalyzesTo(
            analyzer!!,
            "コピー コーヒー タクシー パーティー パーティ センター",
            arrayOf("コピー", "コーヒ", "タクシ", "パーティ", "パーティ", "センタ"),
            intArrayOf(0, 4, 9, 14, 20, 25),
            intArrayOf(3, 8, 13, 19, 24, 29)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKeyword() {
        val exclusionSet = CharArraySet(mutableListOf("コーヒー"), false)
        val analyzerWithKeyword =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                    val sink: TokenStream = SetKeywordMarkerFilter(source, exclusionSet)
                    return TokenStreamComponents(source, JapaneseKatakanaStemFilter(sink))
                }
            }
        checkOneTerm(analyzerWithKeyword, "コーヒー", "コーヒー")
        analyzerWithKeyword.close()
    }

    @Test
    @Throws(IOException::class)
    fun testUnsupportedHalfWidthVariants() {
        // The below result is expected since only full-width katakana is supported
        assertAnalyzesTo(analyzer!!, "ﾀｸｼｰ", arrayOf("ﾀｸｼｰ"))
    }

    @Test
    @Throws(IOException::class)
    fun testRandomData() {
        checkRandomData(random(), analyzer!!, 200 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, JapaneseKatakanaStemFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
