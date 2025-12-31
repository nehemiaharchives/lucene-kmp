package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests for [JapaneseKatakanaUppercaseFilter]  */
class TestJapaneseKatakanaUppercaseFilter :
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
                        tokenizer,
                        JapaneseKatakanaUppercaseFilter(tokenizer)
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
                        JapaneseKatakanaUppercaseFilter(tokenizer)
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
            "ァィゥェォヵㇰヶㇱㇲッㇳㇴㇵㇶㇷㇷ゚ㇸㇹㇺャュョㇻㇼㇽㇾㇿヮ",
            arrayOf("アイウエオカクケシスツトヌハヒフプヘホムヤユヨラリルレロワ")
        )
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "ストップウォッチ",
            arrayOf("ストツプウオツチ")
        )
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "サラニㇷ゚ カムイチェㇷ゚ ㇷ゚ㇷ゚",
            arrayOf("サラニプ", "カムイチエプ", "ププ")
        )
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "カムイチェㇷ゚カムイチェ",
            arrayOf("カムイチエプカムイチエ")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKanaUppercaseWithSurrogatePair() {
        // 𠀋 : \uD840\uDC0B
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "\uD840\uDC0Bストップウォッチ ストップ\uD840\uDC0Bウォッチ ストップウォッチ\uD840\uDC0B",
            arrayOf(
                "\uD840\uDC0Bストツプウオツチ",
                "ストツプ\uD840\uDC0Bウオツチ",
                "ストツプウオツチ\uD840\uDC0B"
            )
        )
    }

    @Test
    @Throws(IOException::class)
    fun testKanaUppercaseWithJapaneseTokenizer() {
        assertAnalyzesTo(
            japaneseAnalyzer!!,
            "時間をストップウォッチで測る",
            arrayOf("時間", "を", "ストツプウオツチ", "で", "測る")
        )
    }

    @Test
    @Throws(IOException::class)
    fun testUnsupportedHalfWidthVariants() {
        // The below result is expected since only full-width katakana is supported
        assertAnalyzesTo(
            keywordAnalyzer!!,
            "ｽﾄｯﾌﾟｳｫｯﾁ",
            arrayOf("ｽﾄｯﾌﾟｳｫｯﾁ")
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
