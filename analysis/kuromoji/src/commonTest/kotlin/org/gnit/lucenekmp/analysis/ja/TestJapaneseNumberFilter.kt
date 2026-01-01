package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestJapaneseNumberFilter : BaseTokenStreamTestCase() {
    private var analyzer: Analyzer? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(
                            newAttributeFactory(),
                            null,
                            false,
                            false,
                            JapaneseTokenizer.Mode.SEARCH
                        )
                    return TokenStreamComponents(tokenizer, JapaneseNumberFilter(tokenizer))
                }
            }
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        analyzer!!.close()
        /*super.tearDown()*/
    }

    @Test
    @Throws(IOException::class)
    fun testBasics() {
        assertAnalyzesTo(
            analyzer!!,
            "本日十万二千五百円のワインを買った",
            arrayOf("本日", "102500", "円", "の", "ワイン", "を", "買っ", "た"),
            intArrayOf(0, 2, 8, 9, 10, 13, 14, 16),
            intArrayOf(2, 8, 9, 10, 13, 14, 16, 17)
        )

        assertAnalyzesTo(
            analyzer!!,
            "昨日のお寿司は１０万円でした。",
            arrayOf("昨日", "の", "お", "寿司", "は", "100000", "円", "でし", "た", "。"),
            intArrayOf(0, 2, 3, 4, 6, 7, 10, 11, 13, 14),
            intArrayOf(2, 3, 4, 6, 7, 10, 11, 13, 14, 15)
        )

        assertAnalyzesTo(
            analyzer!!,
            "アティリカの資本金は６００万円です",
            arrayOf("アティリカ", "の", "資本", "金", "は", "6000000", "円", "です"),
            intArrayOf(0, 5, 6, 8, 9, 10, 14, 15),
            intArrayOf(5, 6, 8, 9, 10, 14, 15, 17)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testVariants() {
        // Test variants of three
        assertAnalyzesTo(analyzer!!, "3", arrayOf("3"))
        assertAnalyzesTo(analyzer!!, "３", arrayOf("3"))
        assertAnalyzesTo(analyzer!!, "三", arrayOf("3"))

        // Test three variations with trailing zero
        assertAnalyzesTo(analyzer!!, "03", arrayOf("3"))
        assertAnalyzesTo(analyzer!!, "０３", arrayOf("3"))
        assertAnalyzesTo(analyzer!!, "〇三", arrayOf("3"))
        assertAnalyzesTo(analyzer!!, "003", arrayOf("3"))
        assertAnalyzesTo(analyzer!!, "００３", arrayOf("3"))
        assertAnalyzesTo(analyzer!!, "〇〇三", arrayOf("3"))

        // Test thousand variants
        assertAnalyzesTo(analyzer!!, "千", arrayOf("1000"))
        assertAnalyzesTo(analyzer!!, "1千", arrayOf("1000"))
        assertAnalyzesTo(analyzer!!, "１千", arrayOf("1000"))
        assertAnalyzesTo(analyzer!!, "一千", arrayOf("1000"))
        assertAnalyzesTo(analyzer!!, "一〇〇〇", arrayOf("1000"))
        assertAnalyzesTo(analyzer!!, "１０百", arrayOf("1000")) // Strange, but supported
    }

    @Test
    @Throws(IOException::class)
    fun testLargeVariants() {
        // Test large numbers
        assertAnalyzesTo(analyzer!!, "三五七八九", arrayOf("35789"))
        assertAnalyzesTo(analyzer!!, "六百二万五千一", arrayOf("6025001"))
        assertAnalyzesTo(analyzer!!, "兆六百万五千一", arrayOf("1000006005001"))
        assertAnalyzesTo(analyzer!!, "十兆六百万五千一", arrayOf("10000006005001"))
        assertAnalyzesTo(analyzer!!, "一京一", arrayOf("10000000000000001"))
        assertAnalyzesTo(analyzer!!, "十京十", arrayOf("100000000000000010"))
        assertAnalyzesTo(analyzer!!, "垓京兆億万千百十一", arrayOf("100010001000100011111"))
    }

    @Test
    @Throws(IOException::class)
    fun testNegative() {
        assertAnalyzesTo(analyzer!!, "-100万", arrayOf("-", "1000000"))
    }

    @Test
    @Throws(IOException::class)
    fun testMixed() {
        // Test mixed numbers
        assertAnalyzesTo(analyzer!!, "三千2百２十三", arrayOf("3223"))
        assertAnalyzesTo(analyzer!!, "３２二三", arrayOf("3223"))
    }

    @Test
    @Throws(IOException::class)
    fun testNininsankyaku() {
        // Unstacked tokens
        assertAnalyzesTo(analyzer!!, "二", arrayOf("2"))
        assertAnalyzesTo(analyzer!!, "二人", arrayOf("2", "人"))
        assertAnalyzesTo(analyzer!!, "二人三", arrayOf("2", "人", "3"))
        // Stacked tokens - emit tokens as they are
        assertAnalyzesTo(analyzer!!, "二人三脚", arrayOf("二", "二人三脚", "人", "三", "脚"))
    }

    @Test
    @Throws(IOException::class)
    fun testFujiyaichinisanu() {
        // Stacked tokens with a numeral partial
        assertAnalyzesTo(analyzer!!, "不二家一二三", arrayOf("不", "不二家", "二", "家", "123"))
    }

    @Test
    @Throws(IOException::class)
    fun testFunny() {
        // Test some oddities for inconsistent input
        assertAnalyzesTo(analyzer!!, "十十", arrayOf("20")) // 100?
        assertAnalyzesTo(analyzer!!, "百百百", arrayOf("300")) // 10,000?
        assertAnalyzesTo(analyzer!!, "千千千千", arrayOf("4000")) // 1,000,000,000,000?
    }

    @Test
    @Throws(IOException::class)
    fun testKanjiArabic() {
        // Test kanji numerals used as Arabic numbers (with head zero)
        assertAnalyzesTo(analyzer!!, "〇一二三四五六七八九九八七六五四三二一〇", arrayOf("1234567899876543210"))

        // I'm Bond, James "normalized" Bond...
        assertAnalyzesTo(analyzer!!, "〇〇七", arrayOf("7"))
    }

    @Test
    @Throws(IOException::class)
    fun testDoubleZero() {
        assertAnalyzesTo(
            analyzer!!,
            "〇〇",
            arrayOf("0"),
            intArrayOf(0),
            intArrayOf(2),
            intArrayOf(1)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testName() {
        // Test name that normalises to number
        assertAnalyzesTo(
            analyzer!!,
            "田中京一",
            arrayOf("田中", "10000000000000001"), // 京一 is normalized to a number
            intArrayOf(0, 2),
            intArrayOf(2, 4),
            intArrayOf(1, 1)
        )

        // An analyzer that marks 京一 as a keyword
        val keywordMarkingAnalyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val set = CharArraySet(1, false)
                    set.add("京一")

                    val tokenizer: Tokenizer =
                        JapaneseTokenizer(newAttributeFactory(), null, false, JapaneseTokenizer.Mode.SEARCH)
                    return TokenStreamComponents(
                        tokenizer,
                        JapaneseNumberFilter(SetKeywordMarkerFilter(tokenizer, set))
                    )
                }
            }

        assertAnalyzesTo(
            keywordMarkingAnalyzer,
            "田中京一",
            arrayOf("田中", "京一"), // 京一 is not normalized
            intArrayOf(0, 2),
            intArrayOf(2, 4),
            intArrayOf(1, 1)
        )
        keywordMarkingAnalyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDecimal() {
        // Test Arabic numbers with punctuation, i.e. 3.2 thousands
        assertAnalyzesTo(analyzer!!, "１．２万３４５．６７", arrayOf("12345.67"))
    }

    @Test
    @Throws(IOException::class)
    fun testDecimalPunctuation() {
        // Test Arabic numbers with punctuation, i.e. 3.2 thousands yen
        assertAnalyzesTo(analyzer!!, "３．２千円", arrayOf("3200", "円"))
    }

    @Test
    @Throws(IOException::class)
    fun testThousandSeparator() {
        assertAnalyzesTo(analyzer!!, "4,647", arrayOf("4647"))
    }

    @Test
    @Throws(IOException::class)
    fun testDecimalThousandSeparator() {
        assertAnalyzesTo(analyzer!!, "4,647.0010", arrayOf("4647.001"))
    }

    @Test
    @Throws(IOException::class)
    fun testCommaDecimalSeparator() {
        assertAnalyzesTo(analyzer!!, "15,7", arrayOf("157"))
    }

    @Test
    @Throws(IOException::class)
    fun testTrailingZeroStripping() {
        assertAnalyzesTo(analyzer!!, "1000.1000", arrayOf("1000.1"))
        assertAnalyzesTo(analyzer!!, "1000.0000", arrayOf("1000"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmpty() {
        assertAnalyzesTo(analyzer!!, "", emptyArray())
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        checkRandomData(random(), analyzer!!, RANDOM_MULTIPLIER, 4096)
    }

    @Test
    @LuceneTestCase.Companion.Nightly
    @Throws(Exception::class)
    fun testRandomHugeStringsAtNight() {
        checkRandomData(random(), analyzer!!, 3 * RANDOM_MULTIPLIER, 8192)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomSmallStrings() {
        checkRandomData(random(), analyzer!!, 100 * RANDOM_MULTIPLIER, 128)
    }

    @Test
    @Throws(Exception::class)
    fun testFunnyIssue() {
        checkAnalysisConsistency(
            random(),
            analyzer!!,
            true,
            "〇〇\u302f\u3029\u3039\u3023\u3033\u302bB",
            true
        )
    }
}
