package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests for [JapaneseHiraganaUppercaseFilter]  */
class TestJapaneseHiraganaUppercaseFilter :
    BaseTokenStreamTestCase() {
    private var keywordAnalyzer: Analyzer? = null
    private var japaneseAnalyzer: Analyzer? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        keywordAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        MockTokenizer(
                            MockTokenizer.WHITESPACE,
                            false
                        )
                    return TokenStreamComponents(
                        tokenizer, JapaneseHiraganaUppercaseFilter(tokenizer)
                    )
                }
            }
        japaneseAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            null,
                            false,
                            JapaneseTokenizer.Mode.SEARCH
                        )
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseHiraganaUppercaseFilter(tokenizer)
                    )
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        keywordAnalyzer!!.close()
        japaneseAnalyzer!!.close()
        /*super.tearDown()*/
    }

    @Test
    @Throws(IOException::class)
    fun testKanaUppercase() {
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "ぁぃぅぇぉっゃゅょゎゕゖ",
            arrayOf("あいうえおつやゆよわかけ")
        )
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "ちょっとまって",
            arrayOf("ちよつとまつて")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKanaUppercaseWithSurrogatePair() {
        // 𠀋 : \uD840\uDC0B
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "\uD840\uDC0Bちょっとまって ちょっと\uD840\uDC0Bまって ちょっとまって\uD840\uDC0B",
            arrayOf(
                "\uD840\uDC0Bちよつとまつて",
                "ちよつと\uD840\uDC0Bまつて",
                "ちよつとまつて\uD840\uDC0B"
            )
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKanaUppercaseWithJapaneseTokenizer() {
        assertAnalyzesTo(
            japaneseAnalyzer!!,
            "ちょっとまって",
            arrayOf("ちよつと", "まつ", "て")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testRandomData() {
        checkRandomData(
            random(),
            keywordAnalyzer!!,
            200 * RANDOM_MULTIPLIER
        )
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "",
            arrayOf()
        )
    }
}
