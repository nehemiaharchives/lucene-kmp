package org.gnit.lucenekmp.analysis.uk

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Test case for UkrainianAnalyzer. */
class TestUkrainianAnalyzer : BaseTokenStreamTestCase() {

    /** Check that UkrainianAnalyzer doesn't discard any numbers */
    @Test
    @Throws(Exception::class)
    fun testDigitsInUkrainianCharset() {
        val ra = UkrainianMorfologikAnalyzer()
        assertAnalyzesTo(ra, "text 1000", arrayOf("text", "1000"))
        ra.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        val a: Analyzer = UkrainianMorfologikAnalyzer()
        assertAnalyzesTo(
            a,
            "Ця п'єса, у свою чергу, рухається по емоційно-напруженому колу за ритм-енд-блюзом.",
            arrayOf(
                "п'єса",
                "черга",
                "рухатися",
                "емоційно",
                "напружений",
                "кола",
                "коло",
                "кіл",
                "ритм",
                "енд",
                "блюз"
            )
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSpecialCharsTokenStream() {
        val a: Analyzer = UkrainianMorfologikAnalyzer()
        assertAnalyzesTo(
            a,
            "м'яса м'я\u0301са м\u02BCяса м\u2019яса м\u2018яса м`яса",
            arrayOf("м'ясо", "м'ясо", "м'ясо", "м'ясо", "м'ясо", "м'ясо")
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCapsTokenStream() {
        val a: Analyzer = UkrainianMorfologikAnalyzer()
        assertAnalyzesTo(a, "Цих Чайковського і Ґете.", arrayOf("Чайковське", "Чайковський", "Гете"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCharNormalization() {
        val a: Analyzer = UkrainianMorfologikAnalyzer()
        assertAnalyzesTo(a, "Ґюмрі та Гюмрі.", arrayOf("Гюмрі", "Гюмрі"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSampleSentence() {
        val a: Analyzer = UkrainianMorfologikAnalyzer()
        assertAnalyzesTo(
            a,
            "Це — проект генерування словника з тегами частин мови для української мови.",
            arrayOf(
                "проект",
                "генерування",
                "словник",
                "тег",
                "частина",
                "мова",
                "українська",
                "український",
                "Українська",
                "мова"
            )
        )
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = UkrainianMorfologikAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }

    @Test
    fun testDefaultStopWords() {
        val stopwords = UkrainianMorfologikAnalyzer.getDefaultStopwords()
        assertTrue(stopwords.contains("аби"))
        stopwords.remove("аби")
        assertTrue(UkrainianMorfologikAnalyzer.getDefaultStopwords().contains("аби"))
    }
}
