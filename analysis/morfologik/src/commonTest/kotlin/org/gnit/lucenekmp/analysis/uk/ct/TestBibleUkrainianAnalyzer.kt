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
    fun testBibleNamePreservation() {
        val analyzer: Analyzer = BibleUkrainianAnalyzer()
        assertAnalyzesTo(analyzer, "Ісуса", arrayOf("ісуса"))
        assertAnalyzesTo(analyzer, "Ісуса Христа", arrayOf("ісуса", "христа"))
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
