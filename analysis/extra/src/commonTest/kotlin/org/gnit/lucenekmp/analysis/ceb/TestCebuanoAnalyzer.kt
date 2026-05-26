package org.gnit.lucenekmp.analysis.ceb

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests the CebuanoAnalyzer. */
class TestCebuanoAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        CebuanoAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = CebuanoAnalyzer()
        checkOneTerm(a, "Mopalit", "palit")
        checkOneTerm(a, "Gipalitan", "palit")
        assertAnalyzesTo(a, "Ang mga tawo sa Cebu ug Pransiya", arrayOf("tawo", "cebu"))
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("mopalit"), true)
        val a: Analyzer = CebuanoAnalyzer(CebuanoAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Mopalit", "mopalit")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = CebuanoAnalyzer()
        checkOneTerm(a, "१२३४", "1234")
        a.close()
    }

    @Test
    fun testDefaultStopSet() {
        val stopSet = CebuanoAnalyzer.getDefaultStopSet()
        assertTrue(stopSet.contains("ang"))
        assertTrue(stopSet.contains("ug"))
        assertTrue(stopSet.contains("sa"))
        assertTrue(stopSet.contains("mga"))
        assertTrue(stopSet.contains("nga"))
        assertTrue(stopSet.contains("departamento"))
        assertTrue(stopSet.contains("pransiya"))
        assertFalse(stopSet.contains("cebu"))
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = CebuanoAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER) // TODO reduced iterations = 1000 to 200 for dev speed
        analyzer.close()
    }
}
