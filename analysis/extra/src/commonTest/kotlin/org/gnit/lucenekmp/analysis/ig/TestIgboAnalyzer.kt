package org.gnit.lucenekmp.analysis.ig

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the IgboAnalyzer. */
class TestIgboAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        IgboAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = IgboAnalyzer()
        checkOneTerm(a, "Ikwughi", "kwu")
        checkOneTerm(a, "Akwụkwọ", "akwukwo")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ikwughi"), true)
        val a: Analyzer = IgboAnalyzer(IgboAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Ikwughi", "ikwughi")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = IgboAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = IgboAnalyzer()
        assertAnalyzesTo(a, "anyi na ha ikwughi akwukwo", arrayOf("kwu", "akwukwo"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = IgboAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
