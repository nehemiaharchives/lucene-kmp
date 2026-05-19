package org.gnit.lucenekmp.analysis.uk.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestBibleUkrainianAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testBibleNameCanonicalization() {
        val analyzer: Analyzer = BibleUkrainianAnalyzer()
        assertAnalyzesTo(
            analyzer,
            "Ісуса",
            arrayOf("ісуса", "ісус"),
            posIncrements = intArrayOf(1, 0)
        )
        assertAnalyzesTo(
            analyzer,
            "Ісуса Христа",
            arrayOf("ісуса", "ісус", "христа", "христос"),
            posIncrements = intArrayOf(1, 0, 1, 0)
        )
        assertAnalyzesTo(
            analyzer,
            "Ісусом Христом",
            arrayOf("ісусом", "ісус", "христом", "христос"),
            posIncrements = intArrayOf(1, 0, 1, 0)
        )
        analyzer.close()
    }

    @Test
    fun testJesusSearchTermsUseNewTestamentScope() {
        assertTrue(BibleUkrainianAnalyzer.requiresNewTestamentScope("Ісуса"))
        assertTrue(BibleUkrainianAnalyzer.requiresNewTestamentScope("Ісуса Христа"))
        assertTrue(BibleUkrainianAnalyzer.requiresNewTestamentScope("Христа"))
    }

    @Test
    fun testJoshuaContextDoesNotUseNewTestamentScope() {
        assertFalse(BibleUkrainianAnalyzer.requiresNewTestamentScope("Ісус Навин"))
        assertFalse(BibleUkrainianAnalyzer.requiresNewTestamentScope("Ісуса, сина Навина"))
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = BibleUkrainianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
