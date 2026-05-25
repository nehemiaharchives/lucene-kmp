package org.gnit.lucenekmp.analysis.ha

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the HausaAnalyzer. */
class TestHausaAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        HausaAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = HausaAnalyzer()
        checkOneTerm(a, "Nakarantawa", "karanta")
        checkOneTerm(a, "ƙasa", "kasa")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("nakarantawa"), true)
        val a: Analyzer = HausaAnalyzer(HausaAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Nakarantawa", "nakarantawa")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = HausaAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = HausaAnalyzer()
        assertAnalyzesTo(a, "ni da su nakarantawa", arrayOf("karanta"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = HausaAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
