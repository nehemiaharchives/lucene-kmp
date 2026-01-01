package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.cjk.CJKWidthFilter
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests for [JapaneseReadingFormFilter] */
class TestJapaneseReadingFormFilter : BaseTokenStreamTestCase() {
    private var katakanaAnalyzer: Analyzer? = null
    private var romajiAnalyzer: Analyzer? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        katakanaAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH)
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseReadingFormFilter(tokenizer, false)
                    )
                }
            }
        romajiAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH)
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseReadingFormFilter(tokenizer, true)
                    )
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        IOUtils.close(katakanaAnalyzer, romajiAnalyzer)
        /*super.tearDown()*/
    }

    @Test
    @Throws(IOException::class)
    fun testKatakanaReadings() {
        assertAnalyzesTo(
            katakanaAnalyzer!!,
            "今夜はロバート先生と話した",
            arrayOf("コンヤ", "ハ", "ロバート", "センセイ", "ト", "ハナシ", "タ")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKatakanaReadingsHalfWidth() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH)
                    val stream: TokenStream = CJKWidthFilter(tokenizer)
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseReadingFormFilter(stream, false)
                    )
                }
            }
        assertAnalyzesTo(
            analyzer,
            "今夜はﾛﾊﾞｰﾄ先生と話した",
            arrayOf("コンヤ", "ハ", "ロバート", "センセイ", "ト", "ハナシ", "タ")
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testKatakanaReadingsHiragana() {
        assertAnalyzesTo(
            katakanaAnalyzer!!,
            "が ぎ ぐ げ ご ぁ ゔ",
            arrayOf("ガ", "ギ", "グ", "ゲ", "ゴ", "ァ", "ヴ")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testRomajiReadings() {
        assertAnalyzesTo(
            romajiAnalyzer!!,
            "今夜はロバート先生と話した",
            arrayOf("kon'ya", "ha", "robato", "sensei", "to", "hanashi", "ta")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testRomajiReadingsHalfWidth() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(newAttributeFactory(), null, true, JapaneseTokenizer.Mode.SEARCH)
                    val stream: TokenStream = CJKWidthFilter(tokenizer)
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseReadingFormFilter(stream, true)
                    )
                }
            }
        assertAnalyzesTo(
            analyzer,
            "今夜はﾛﾊﾞｰﾄ先生と話した",
            arrayOf("kon'ya", "ha", "robato", "sensei", "to", "hanashi", "ta")
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRomajiReadingsHiragana() {
        assertAnalyzesTo(
            romajiAnalyzer!!,
            "が ぎ ぐ げ ご ぁ ゔ",
            arrayOf("ga", "gi", "gu", "ge", "go", "a", "v")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testRandomData() {
        val random = random()
        checkRandomData(random, katakanaAnalyzer!!, 200 * RANDOM_MULTIPLIER)
        checkRandomData(random, romajiAnalyzer!!, 200 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, JapaneseReadingFormFilter(tokenizer))
                }
            }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
