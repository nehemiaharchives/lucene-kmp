package org.gnit.lucenekmp.analysis.ko

import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

class TestKoreanNumberFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        val userDictionary = TestKoreanTokenizer.readDict()
        val stopTags = setOf(POS.Tag.SP)
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KoreanTokenizer(
                    newAttributeFactory(),
                    userDictionary,
                    KoreanTokenizer.DEFAULT_DECOMPOUND,
                    false,
                    false
                )
                val stream: TokenStream = KoreanPartOfSpeechStopFilter(tokenizer, stopTags)
                return TokenStreamComponents(tokenizer, KoreanNumberFilter(stream))
            }
        }
    }

    @AfterTest
    fun tearDown() {
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testBasics() {
        assertAnalyzesTo(
            analyzer,
            "오늘 십만이천오백원의 와인 구입",
            arrayOf("오늘", "102500", "원", "의", "와인", "구입"),
            intArrayOf(0, 3, 9, 10, 12, 15),
            intArrayOf(2, 9, 10, 11, 14, 17)
        )

        assertAnalyzesTo(
            analyzer,
            "어제 초밥 가격은 10만 원",
            arrayOf("어제", "초", "밥", "가격", "은", "100000", "원"),
            intArrayOf(0, 3, 4, 6, 8, 10, 14, 15, 13),
            intArrayOf(2, 4, 5, 8, 9, 13, 15, 13, 14)
        )

        assertAnalyzesTo(
            analyzer,
            "자본금 600만 원",
            arrayOf("자본", "금", "6000000", "원"),
            intArrayOf(0, 2, 4, 9, 10),
            intArrayOf(2, 3, 8, 10, 11)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testVariants() {
        assertAnalyzesTo(analyzer, "3", arrayOf("3"))
        assertAnalyzesTo(analyzer, "３", arrayOf("3"))
        assertAnalyzesTo(analyzer, "삼", arrayOf("3"))

        assertAnalyzesTo(analyzer, "03", arrayOf("3"))
        assertAnalyzesTo(analyzer, "０３", arrayOf("3"))
        assertAnalyzesTo(analyzer, "영삼", arrayOf("3"))
        assertAnalyzesTo(analyzer, "003", arrayOf("3"))
        assertAnalyzesTo(analyzer, "００３", arrayOf("3"))
        assertAnalyzesTo(analyzer, "영영삼", arrayOf("3"))

        assertAnalyzesTo(analyzer, "천", arrayOf("1000"))
        assertAnalyzesTo(analyzer, "1천", arrayOf("1000"))
        assertAnalyzesTo(analyzer, "１천", arrayOf("1000"))
        assertAnalyzesTo(analyzer, "일천", arrayOf("1000"))
        assertAnalyzesTo(analyzer, "일영영영", arrayOf("1000"))
        assertAnalyzesTo(analyzer, "１０백", arrayOf("1000"))
    }

    @Test
    @Throws(IOException::class)
    fun testLargeVariants() {
        assertAnalyzesTo(analyzer, "삼오칠팔구", arrayOf("35789"))
        assertAnalyzesTo(analyzer, "육백이만오천일", arrayOf("6025001"))
        assertAnalyzesTo(analyzer, "조육백만오천일", arrayOf("1000006005001"))
        assertAnalyzesTo(analyzer, "십조육백만오천일", arrayOf("10000006005001"))
        assertAnalyzesTo(analyzer, "일경일", arrayOf("10000000000000001"))
        assertAnalyzesTo(analyzer, "십경십", arrayOf("100000000000000010"))
        assertAnalyzesTo(analyzer, "해경조억만천백십일", arrayOf("100010001000100011111"))
    }

    @Test
    @Throws(IOException::class)
    fun testNegative() {
        assertAnalyzesTo(analyzer, "-백만", arrayOf("-", "1000000"))
    }

    @Test
    @Throws(IOException::class)
    fun testMixed() {
        assertAnalyzesTo(analyzer, "삼천2백２십삼", arrayOf("3223"))
        assertAnalyzesTo(analyzer, "３２이삼", arrayOf("3223"))
    }

    @Test
    @Throws(IOException::class)
    fun testFunny() {
        assertAnalyzesTo(analyzer, "십십", arrayOf("20"))
        assertAnalyzesTo(analyzer, "백백백", arrayOf("300"))
        assertAnalyzesTo(analyzer, "천천천천", arrayOf("4000"))
    }

    @Test
    @Throws(IOException::class)
    fun testHangulArabic() {
        assertAnalyzesTo(
            analyzer,
            "영일이삼사오육칠팔구구팔칠육오사삼이일영",
            arrayOf("1234567899876543210")
        )
        assertAnalyzesTo(analyzer, "영영칠", arrayOf("7"))
    }

    @Test
    @Throws(IOException::class)
    fun testDoubleZero() {
        assertAnalyzesTo(analyzer, "영영", arrayOf("0"), intArrayOf(0), intArrayOf(2), intArrayOf(1))
    }

    @Test
    @Throws(IOException::class)
    fun testName() {
        assertAnalyzesTo(
            analyzer,
            "전중경일",
            arrayOf("전중", "10000000000000001"),
            intArrayOf(0, 2),
            intArrayOf(2, 4),
            intArrayOf(1, 1)
        )

        val keywordMarkingAnalyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val set = CharArraySet(1, false)
                set.add("경일")
                val userDictionary = TestKoreanTokenizer.readDict()
                val stopTags = setOf(POS.Tag.SP)
                val tokenizer: Tokenizer = KoreanTokenizer(
                    newAttributeFactory(),
                    userDictionary,
                    KoreanTokenizer.DEFAULT_DECOMPOUND,
                    false,
                    false
                )
                val stream: TokenStream = KoreanPartOfSpeechStopFilter(tokenizer, stopTags)
                return TokenStreamComponents(
                    tokenizer,
                    KoreanNumberFilter(SetKeywordMarkerFilter(stream, set))
                )
            }
        }

        assertAnalyzesTo(
            keywordMarkingAnalyzer,
            "전중경일",
            arrayOf("전중", "경일"),
            intArrayOf(0, 2),
            intArrayOf(2, 4),
            intArrayOf(1, 1)
        )
        keywordMarkingAnalyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDecimal() {
        assertAnalyzesTo(analyzer, "１．２만３４５．６７", arrayOf("12345.67"))
    }

    @Test
    @Throws(IOException::class)
    fun testDecimalPunctuation() {
        assertAnalyzesTo(analyzer, "３．２천 원", arrayOf("3200", "원"))
    }

    @Test
    @Throws(IOException::class)
    fun testThousandSeparator() {
        assertAnalyzesTo(analyzer, "4,647", arrayOf("4647"))
    }

    @Test
    @Throws(IOException::class)
    fun testDecimalThousandSeparator() {
        assertAnalyzesTo(analyzer, "4,647.0010", arrayOf("4647.001"))
    }

    @Test
    @Throws(IOException::class)
    fun testCommaDecimalSeparator() {
        assertAnalyzesTo(analyzer, "15,7", arrayOf("157"))
    }

    @Test
    @Throws(IOException::class)
    fun testTrailingZeroStripping() {
        assertAnalyzesTo(analyzer, "1000.1000", arrayOf("1000.1"))
        assertAnalyzesTo(analyzer, "1000.0000", arrayOf("1000"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmpty() {
        assertAnalyzesTo(analyzer, "", emptyArray())
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        checkRandomData(random(), analyzer, RANDOM_MULTIPLIER, 4096)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStringsAtNight() {
        checkRandomData(random(), analyzer, 5 * RANDOM_MULTIPLIER, 8192)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomSmallStrings() {
        checkRandomData(random(), analyzer, 100 * RANDOM_MULTIPLIER, 128)
    }

    @Test
    @Throws(Exception::class)
    fun testFunnyIssue() {
        BaseTokenStreamTestCase.checkAnalysisConsistency(
            random(),
            analyzer,
            true,
            "영영\u302f\u3029\u3039\u3023\u3033\u302bB",
            true
        )
    }

    @Ignore
    @Test
    @Throws(IOException::class)
    fun testLargeData() {
        val input: Path = "/tmp/test.txt".toPath()
        val tokenizedOutput: Path = "/tmp/test.tok.txt".toPath()
        val normalizedOutput: Path = "/tmp/test.norm.txt".toPath()

        val plainAnalyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val userDictionary = TestKoreanTokenizer.readDict()
                val stopTags = setOf(POS.Tag.SP)
                val tokenizer: Tokenizer = KoreanTokenizer(
                    newAttributeFactory(),
                    userDictionary,
                    KoreanTokenizer.DEFAULT_DECOMPOUND,
                    false,
                    false
                )
                return TokenStreamComponents(tokenizer, KoreanPartOfSpeechStopFilter(tokenizer, stopTags))
            }
        }

        analyze(
            plainAnalyzer,
            Files.newBufferedReader(input, StandardCharsets.UTF_8),
            Files.newBufferedWriter(tokenizedOutput, StandardCharsets.UTF_8)
        )

        analyze(
            analyzer,
            Files.newBufferedReader(input, StandardCharsets.UTF_8),
            Files.newBufferedWriter(normalizedOutput, StandardCharsets.UTF_8)
        )
        plainAnalyzer.close()
    }

    @Throws(IOException::class)
    private fun analyze(analyzer: Analyzer, reader: org.gnit.lucenekmp.jdkport.Reader, writer: org.gnit.lucenekmp.jdkport.Writer) {
        val stream = analyzer.tokenStream("dummy", reader)
        stream.reset()

        val termAttr = stream.addAttribute(CharTermAttribute::class)

        while (stream.incrementToken()) {
            writer.write(termAttr.toString())
            writer.write("\n")
        }

        reader.close()
        writer.close()
    }
}
