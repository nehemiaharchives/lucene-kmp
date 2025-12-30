package org.gnit.lucenekmp.analysis.cn.smart

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test

class TestSmartChineseAnalyzer : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testChineseStopWordsDefault() {
        var ca: Analyzer = SmartChineseAnalyzer()
        val sentence = "我购买了道具和服装。"
        val result = arrayOf("我", "购买", "了", "道具", "和", "服装")
        assertAnalyzesTo(ca, sentence, result)
        ca.close()
        ca = SmartChineseAnalyzer(SmartChineseAnalyzer.getDefaultStopSet())
        assertAnalyzesTo(ca, sentence, result)
        ca.close()
    }

    @Test
    @Throws(IOException::class)
    fun testChineseStopWordsDefaultTwoPhrases() {
        val ca: Analyzer = SmartChineseAnalyzer()
        val sentence = "我购买了道具和服装。 我购买了道具和服装。"
        val result = arrayOf("我", "购买", "了", "道具", "和", "服装", "我", "购买", "了", "道具", "和", "服装")
        assertAnalyzesTo(ca, sentence, result)
        ca.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSurrogatePairCharacter() {
        val ca: Analyzer = SmartChineseAnalyzer()
        val sentence = listOf(
            "\uD872\uDF3B",
            "\uD872\uDF4A",
            "\uD872\uDF73",
            "\uD872\uDF5B",
            "\u9FCF",
            "\uD86D\uDFFC",
            "\uD872\uDF2D",
            "\u9FD4"
        ).joinToString("")
        val result = arrayOf(
            "\uD872\uDF3B",
            "\uD872\uDF4A",
            "\uD872\uDF73",
            "\uD872\uDF5B",
            "\u9FCF",
            "\uD86D\uDFFC",
            "\uD872\uDF2D",
            "\u9FD4"
        )
        assertAnalyzesTo(ca, sentence, result)
        ca.close()
    }

    @Test
    @Throws(IOException::class)
    fun testChineseStopWordsDefaultTwoPhrasesIdeoSpace() {
        val ca: Analyzer = SmartChineseAnalyzer()
        val sentence = "我购买了道具和服装　我购买了道具和服装。"
        val result = arrayOf("我", "购买", "了", "道具", "和", "服装", "我", "购买", "了", "道具", "和", "服装")
        assertAnalyzesTo(ca, sentence, result)
        ca.close()
    }

    @Test
    @Throws(IOException::class)
    fun testChineseStopWordsOff() {
        val analyzers = arrayOf(
            SmartChineseAnalyzer(false),
            SmartChineseAnalyzer(null)
        )
        val sentence = "我购买了道具和服装。"
        val result = arrayOf("我", "购买", "了", "道具", "和", "服装", ",")
        for (analyzer in analyzers) {
            assertAnalyzesTo(analyzer, sentence, result)
            assertAnalyzesTo(analyzer, sentence, result)
        }
        IOUtils.close(*analyzers)
    }

    @Test
    @Throws(IOException::class)
    fun testChineseStopWords2() {
        val ca: Analyzer = SmartChineseAnalyzer()
        val sentence = "Title:San"
        val result = arrayOf("titl", "san")
        val startOffsets = intArrayOf(0, 6)
        val endOffsets = intArrayOf(5, 9)
        val posIncr = intArrayOf(1, 2)
        assertAnalyzesTo(ca, sentence, result, startOffsets, endOffsets, posIncr)
        ca.close()
    }

    @Test
    @Throws(IOException::class)
    fun testChineseAnalyzer() {
        val ca: Analyzer = SmartChineseAnalyzer(true)
        val sentence = "我购买了道具和服装。"
        val result = arrayOf("我", "购买", "了", "道具", "和", "服装")
        assertAnalyzesTo(ca, sentence, result)
        ca.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMixedLatinChinese() {
        val analyzer = SmartChineseAnalyzer(true)
        assertAnalyzesTo(analyzer, "我购买 Tests 了道具和服装", arrayOf("我", "购买", "test", "了", "道具", "和", "服装"))
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNumerics() {
        val analyzer = SmartChineseAnalyzer(true)
        assertAnalyzesTo(
            analyzer,
            "我购买 Tests 了道具和服装1234",
            arrayOf("我", "购买", "test", "了", "道具", "和", "服装", "1234")
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFullWidth() {
        val analyzer = SmartChineseAnalyzer(true)
        assertAnalyzesTo(
            analyzer,
            "我购买 Ｔｅｓｔｓ 了道具和服装１２３４",
            arrayOf("我", "购买", "test", "了", "道具", "和", "服装", "1234")
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDelimiters() {
        val analyzer = SmartChineseAnalyzer(true)
        assertAnalyzesTo(
            analyzer,
            "我购买︱ Tests 了道具和服装",
            arrayOf("我", "购买", "test", "了", "道具", "和", "服装")
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNonChinese() {
        val analyzer = SmartChineseAnalyzer(true)
        assertAnalyzesTo(
            analyzer,
            "我购买 روبرتTests 了道具和服装",
            arrayOf("我", "购买", "ر", "و", "ب", "ر", "ت", "test", "了", "道具", "和", "服装")
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testOOV() {
        val analyzer = SmartChineseAnalyzer(true)
        assertAnalyzesTo(analyzer, "优素福·拉扎·吉拉尼", arrayOf("优", "素", "福", "拉", "扎", "吉", "拉", "尼"))
        assertAnalyzesTo(analyzer, "优素福拉扎吉拉尼", arrayOf("优", "素", "福", "拉", "扎", "吉", "拉", "尼"))
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testOffsets() {
        val analyzer = SmartChineseAnalyzer(true)
        assertAnalyzesTo(
            analyzer,
            "我购买了道具和服装",
            arrayOf("我", "购买", "了", "道具", "和", "服装"),
            intArrayOf(0, 1, 3, 4, 6, 7),
            intArrayOf(1, 3, 4, 6, 7, 9)
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testReusableTokenStream() {
        val a = SmartChineseAnalyzer()
        assertAnalyzesTo(
            a,
            "我购买 Tests 了道具和服装",
            arrayOf("我", "购买", "test", "了", "道具", "和", "服装"),
            intArrayOf(0, 1, 4, 10, 11, 13, 14),
            intArrayOf(1, 3, 9, 11, 13, 14, 16)
        )
        assertAnalyzesTo(
            a,
            "我购买了道具和服装。",
            arrayOf("我", "购买", "了", "道具", "和", "服装"),
            intArrayOf(0, 1, 3, 4, 6, 7),
            intArrayOf(1, 3, 4, 6, 7, 9)
        )
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testLargeDocument() {
        val sb = StringBuilder()
        repeat(5000) {
            sb.append("我购买了道具和服装。")
        }
        SmartChineseAnalyzer().use { analyzer ->
            analyzer.tokenStream("", sb.toString()).use { stream ->
                stream.reset()
                while (stream.incrementToken()) {
                }
                stream.end()
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testLargeSentence() {
        val sb = StringBuilder()
        repeat(5000) {
            sb.append("我购买了道具和服装")
        }
        SmartChineseAnalyzer().use { analyzer ->
            analyzer.tokenStream("", sb.toString()).use { stream ->
                stream.reset()
                while (stream.incrementToken()) {
                }
                stream.end()
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRandomStrings() {
        val analyzer = SmartChineseAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRandomHugeStrings() {
        val analyzer = SmartChineseAnalyzer()
        checkRandomData(random(), analyzer, 3 * RANDOM_MULTIPLIER, 8192)
        analyzer.close()
    }
}
