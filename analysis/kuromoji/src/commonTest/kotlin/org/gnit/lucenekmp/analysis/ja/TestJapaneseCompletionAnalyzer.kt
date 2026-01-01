package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.ja.JapaneseCompletionFilter.Mode
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestJapaneseCompletionAnalyzer : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun testCompletionDefault() {
        val analyzer: Analyzer = JapaneseCompletionAnalyzer()
        assertAnalyzesTo(
            analyzer,
            "東京",
            arrayOf("東京", "toukyou"),
            intArrayOf(0, 0),
            intArrayOf(2, 2),
            intArrayOf(1, 0)
        )
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCompletionQuery() {
        val analyzer: Analyzer = JapaneseCompletionAnalyzer(null, Mode.QUERY)
        assertAnalyzesTo(
            analyzer,
            "東京ｔ",
            arrayOf("東京t", "toukyout"),
            intArrayOf(0, 0),
            intArrayOf(3, 3),
            intArrayOf(1, 0)
        )
        analyzer.close()
    }

    /** blast random strings against the analyzer */
    @Test
    @Throws(IOException::class)
    fun testRandom() {
        val random = random()
        val analyzer: Analyzer = JapaneseCompletionAnalyzer()
        checkRandomData(random, analyzer, atLeast(100))
        analyzer.close()
    }

    /** blast some random large strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        val random = random()
        val analyzer: Analyzer = JapaneseCompletionAnalyzer()
        checkRandomData(random, analyzer, 2 * RANDOM_MULTIPLIER, 8192)
        analyzer.close()
    }
}
