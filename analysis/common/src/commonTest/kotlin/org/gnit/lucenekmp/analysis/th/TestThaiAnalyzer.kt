package org.gnit.lucenekmp.analysis.th

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.en.EnglishAnalyzer
import org.gnit.lucenekmp.analysis.tokenattributes.FlagsAttribute
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test case for ThaiAnalyzer, modified from TestFrenchAnalyzer. */
class TestThaiAnalyzer : BaseTokenStreamTestCase() {

    @Test
    @Throws(Exception::class)
    fun testOffsets() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer: Analyzer = ThaiAnalyzer(CharArraySet.EMPTY_SET)
        assertAnalyzesTo(
            analyzer,
            "การที่ได้ต้องแสดงว่างานดี",
            arrayOf("การ", "ที่", "ได้", "ต้อง", "แสดง", "ว่า", "งาน", "ดี"),
            intArrayOf(0, 3, 6, 9, 13, 17, 20, 23),
            intArrayOf(3, 6, 9, 13, 17, 20, 23, 25)
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testStopWords() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer: Analyzer = ThaiAnalyzer()
        assertAnalyzesTo(
            analyzer,
            "การที่ได้ต้องแสดงว่างานดี",
            arrayOf("แสดง", "งาน", "ดี"),
            intArrayOf(13, 20, 23),
            intArrayOf(17, 23, 25),
            intArrayOf(5, 2, 1)
        )
        analyzer.close()
    }

    /** Test that position increments are adjusted correctly for stopwords. */
    @Test
    @Throws(Exception::class)
    fun testPositionIncrements() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer = ThaiAnalyzer(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET)
        assertAnalyzesTo(
            analyzer,
            "การที่ได้ต้อง the แสดงว่างานดี",
            arrayOf("การ", "ที่", "ได้", "ต้อง", "แสดง", "ว่า", "งาน", "ดี"),
            intArrayOf(0, 3, 6, 9, 18, 22, 25, 28),
            intArrayOf(3, 6, 9, 13, 22, 25, 28, 30),
            intArrayOf(1, 1, 1, 1, 2, 1, 1, 1)
        )

        assertAnalyzesTo(
            analyzer,
            "การที่ได้ต้องthe แสดงว่างานดี",
            arrayOf("การ", "ที่", "ได้", "ต้อง", "แสดง", "ว่า", "งาน", "ดี"),
            intArrayOf(0, 3, 6, 9, 17, 21, 24, 27),
            intArrayOf(3, 6, 9, 13, 21, 24, 27, 29),
            intArrayOf(1, 1, 1, 1, 2, 1, 1, 1)
        )
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer = ThaiAnalyzer(CharArraySet.EMPTY_SET)
        assertAnalyzesTo(analyzer, "", arrayOf())

        assertAnalyzesTo(
            analyzer,
            "การที่ได้ต้องแสดงว่างานดี",
            arrayOf("การ", "ที่", "ได้", "ต้อง", "แสดง", "ว่า", "งาน", "ดี")
        )

        assertAnalyzesTo(
            analyzer,
            "บริษัทชื่อ XY&Z - คุยกับ xyz@demo.com",
            arrayOf("บริษัท", "ชื่อ", "xy", "z", "คุย", "กับ", "xyz", "demo.com")
        )
        analyzer.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer: Analyzer = ThaiAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }

    /** blast some random large strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomHugeStrings() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer: Analyzer = ThaiAnalyzer()
        checkRandomData(random(), analyzer, 3 * RANDOM_MULTIPLIER, 8192)
        analyzer.close()
    }

    // LUCENE-3044
    @Test
    @Throws(Exception::class)
    fun testAttributeReuse() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer: Analyzer = ThaiAnalyzer()
        var ts: TokenStream = analyzer.tokenStream("dummy", "ภาษาไทย")
        assertTokenStreamContents(ts, arrayOf("ภาษา", "ไทย"))
        ts = analyzer.tokenStream("dummy", "ภาษาไทย")
        ts.addAttribute(FlagsAttribute::class)
        assertTokenStreamContents(ts, arrayOf("ภาษา", "ไทย"))
        analyzer.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer: Analyzer = ThaiAnalyzer()
        checkOneTerm(analyzer, "๑๒๓๔", "1234")
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTwoSentences() {
        if (!ThaiTokenizer.DBBI_AVAILABLE) return
        val analyzer: Analyzer = ThaiAnalyzer(CharArraySet.EMPTY_SET)
        assertAnalyzesTo(
            analyzer,
            "This is a test. การที่ได้ต้องแสดงว่างานดี",
            arrayOf(
                "this", "is", "a", "test", "การ", "ที่", "ได้", "ต้อง", "แสดง", "ว่า", "งาน", "ดี"
            ),
            intArrayOf(0, 5, 8, 10, 16, 19, 22, 25, 29, 33, 36, 39),
            intArrayOf(4, 7, 9, 14, 19, 22, 25, 29, 33, 36, 39, 41)
        )
        analyzer.close()
    }
}
