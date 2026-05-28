package org.gnit.lucenekmp.analysis.uz

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the UzbekAnalyzer. */
class TestUzbekAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        UzbekAnalyzer().close()
    }

    /** test tokenization, normalization and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = UzbekAnalyzer()
        checkOneTerm(a, "Oʻzbekistonda", "o'zbekiston")
        checkOneTerm(a, "kitoblardan", "kitob")
        checkOneTerm(a, "uylarimizdan", "uy")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("kitoblardan"), true)
        val a: Analyzer = UzbekAnalyzer(UzbekAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "kitoblardan", "kitoblardan")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = UzbekAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = UzbekAnalyzer()
        assertAnalyzesTo(a, "bu kitoblar va uylar", arrayOf("kitob", "uy"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = UzbekAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
