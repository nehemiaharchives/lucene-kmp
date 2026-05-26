package org.gnit.lucenekmp.analysis.ht

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the HaitianCreoleAnalyzer. */
class TestHaitianCreoleAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        HaitianCreoleAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = HaitianCreoleAnalyzer()
        checkOneTerm(a, "Kreyòl", "kreyol")
        checkOneTerm(a, "rapidman", "rapid")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("rapidman"), true)
        val a: Analyzer = HaitianCreoleAnalyzer(HaitianCreoleAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "rapidman", "rapidman")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = HaitianCreoleAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = HaitianCreoleAnalyzer()
        assertAnalyzesTo(a, "Mwen ap pale ak timoun yo rapidman", arrayOf("pale", "timoun", "rapid"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = HaitianCreoleAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
