package org.gnit.lucenekmp.analysis.ko

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test Korean morphological analyzer */
class TestKoreanAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testSentence() {
        val analyzer = KoreanAnalyzer()
        assertAnalyzesTo(
            analyzer,
            "한국은 대단한 나라입니다.",
            arrayOf("한국", "대단", "나라", "이"),
            intArrayOf(0, 4, 8, 10),
            intArrayOf(2, 6, 10, 13),
            intArrayOf(1, 2, 3, 1)
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testStopTags() {
        val stopTags = setOf(POS.Tag.NNP, POS.Tag.NNG)
        val analyzer = KoreanAnalyzer(null, KoreanTokenizer.DecompoundMode.DISCARD, stopTags, false)
        assertAnalyzesTo(
            analyzer,
            "한국은 대단한 나라입니다.",
            arrayOf("은", "대단", "하", "ᆫ", "이", "ᄇ니다"),
            intArrayOf(2, 4, 6, 6, 10, 10),
            intArrayOf(3, 6, 7, 7, 13, 13),
            intArrayOf(2, 1, 1, 1, 2, 1)
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testUnknownWord() {
        var analyzer = KoreanAnalyzer(
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
            true
        )

        assertAnalyzesTo(
            analyzer,
            "2018 평창 동계올림픽대회",
            arrayOf("2", "0", "1", "8", "평창", "동계", "올림픽", "대회"),
            intArrayOf(0, 1, 2, 3, 5, 8, 10, 13),
            intArrayOf(1, 2, 3, 4, 7, 10, 13, 15),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1)
        )
        analyzer.close()

        analyzer = KoreanAnalyzer(
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
            false
        )

        assertAnalyzesTo(
            analyzer,
            "2018 평창 동계올림픽대회",
            arrayOf("2018", "평창", "동계", "올림픽", "대회"),
            intArrayOf(0, 5, 8, 10, 13),
            intArrayOf(4, 7, 10, 13, 15),
            intArrayOf(1, 1, 1, 1, 1)
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRandom() {
        val random = random()
        val analyzer = KoreanAnalyzer()
        checkRandomData(random, analyzer, atLeast(200))
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val random = random()
        val analyzer = KoreanAnalyzer()
        checkRandomData(random, analyzer, RANDOM_MULTIPLIER, 4096)
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomHugeStringsAtNight() {
        val random = random()
        val analyzer = KoreanAnalyzer()
        checkRandomData(random, analyzer, 3 * RANDOM_MULTIPLIER, 8192)
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testUserDict() {
        val analyzer = KoreanAnalyzer(
            TestKoreanTokenizer.readDict(),
            KoreanTokenizer.DEFAULT_DECOMPOUND,
            KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
            false
        )
        assertAnalyzesTo(
            analyzer,
            "c++ 프로그래밍 언어",
            arrayOf("c++", "프로그래밍", "언어"),
            intArrayOf(0, 4, 10),
            intArrayOf(3, 9, 12),
            intArrayOf(1, 1, 1)
        )
    }
}
