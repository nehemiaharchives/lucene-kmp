package org.gnit.lucenekmp.analysis.ru.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class TestBibleRussianAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testJesusCaseFormsEmitCanonicalName() {
        val analyzer: Analyzer = BibleRussianAnalyzer()
        assertAnalyzesTo(
            analyzer,
            "Иисуса",
            arrayOf("иисуса", "иисус"),
            posIncrements = intArrayOf(1, 0)
        )
        assertAnalyzesTo(
            analyzer,
            "Иисусу",
            arrayOf("иисусу", "иисус"),
            posIncrements = intArrayOf(1, 0)
        )
        assertAnalyzesTo(
            analyzer,
            "Иисуса Христа",
            arrayOf("иисуса", "иисус", "христа", "христос"),
            posIncrements = intArrayOf(1, 0, 1, 0)
        )
        assertAnalyzesTo(
            analyzer,
            "Иисусом Христом",
            arrayOf("иисусом", "иисус", "христом", "христос"),
            posIncrements = intArrayOf(1, 0, 1, 0)
        )
        analyzer.close()
    }

    @Test
    fun testJesusSearchTermsUseNewTestamentScope() {
        assertTrue(BibleRussianAnalyzer.requiresNewTestamentScope("Иисуса"))
        assertTrue(BibleRussianAnalyzer.requiresNewTestamentScope("Христа"))
        assertTrue(BibleRussianAnalyzer.requiresNewTestamentScope("Иисуса Христа"))
    }

    @Test
    fun testJoshuaContextDoesNotUseNewTestamentScope() {
        assertFalse(BibleRussianAnalyzer.requiresNewTestamentScope("Иисуса, сына Навина"))
        assertFalse(BibleRussianAnalyzer.requiresNewTestamentScope("Иисус Навин"))
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = BibleRussianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
