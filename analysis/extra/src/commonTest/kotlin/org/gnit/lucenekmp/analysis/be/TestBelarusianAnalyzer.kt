package org.gnit.lucenekmp.analysis.be

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the BelarusianAnalyzer. */
class TestBelarusianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        BelarusianAnalyzer().close()
    }

    /** test stopwords, normalization and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = BelarusianAnalyzer()
        checkOneTerm(a, "Мінску", "мінск")
        checkOneTerm(a, "пʼе", "п'е")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("мінску"), true)
        val a: Analyzer = BelarusianAnalyzer(BelarusianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Мінску", "мінску")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = BelarusianAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = BelarusianAnalyzer()
        assertAnalyzesTo(a, "я і ты ў мінску", arrayOf("мінск"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = BelarusianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
