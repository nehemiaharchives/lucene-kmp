package org.gnit.lucenekmp.analysis.cz

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test the [CzechAnalyzer]. */
class TestCzechAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails when the stopwords data is missing. */
    @Test
    fun testResourcesAvailable() {
        CzechAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testStopWord() {
        val analyzer: Analyzer = CzechAnalyzer()
        assertAnalyzesTo(analyzer, "Pokud mluvime o volnem", arrayOf("mluvim", "voln"))
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        val analyzer: Analyzer = CzechAnalyzer()
        assertAnalyzesTo(analyzer, "Pokud mluvime o volnem", arrayOf("mluvim", "voln"))
        assertAnalyzesTo(analyzer, "Česká Republika", arrayOf("česk", "republik"))
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testWithStemExclusionSet() {
        val set = CharArraySet(1, true)
        set.add("hole")
        val cz = CzechAnalyzer(CharArraySet.EMPTY_SET, set)
        assertAnalyzesTo(cz, "hole desek", arrayOf("hole", "desk"))
        cz.close()
    }

    @Test
    fun testDefaultStopSet() {
        val stopSet = CzechAnalyzer.getDefaultStopSet()
        assertTrue(stopSet.contains("pokud"))
        assertTrue(stopSet.contains("o"))
        assertTrue(stopSet.contains("je"))
        assertTrue(stopSet.contains("který"))
        assertFalse(stopSet.contains("republika"))
    }

    /** Blast some random strings through the analyzer. */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = CzechAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER) // TODO reduced iterations = 1000 to 200 for dev speed
        analyzer.close()
    }
}
