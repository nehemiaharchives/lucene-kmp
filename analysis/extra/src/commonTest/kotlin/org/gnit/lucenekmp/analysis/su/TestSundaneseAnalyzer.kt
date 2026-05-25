package org.gnit.lucenekmp.analysis.su

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the SundaneseAnalyzer. */
class TestSundaneseAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        SundaneseAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = SundaneseAnalyzer()
        checkOneTerm(a, "Dibacakeun", "baca")
        checkOneTerm(a, "Buku", "buku")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("dibacakeun"), true)
        val a: Analyzer = SundaneseAnalyzer(SundaneseAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Dibacakeun", "dibacakeun")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = SundaneseAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = SundaneseAnalyzer()
        assertAnalyzesTo(a, "abdi jeung anjeunna dibacakeun buku", arrayOf("baca", "buku"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = SundaneseAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
