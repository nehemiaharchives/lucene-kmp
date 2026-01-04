package org.gnit.lucenekmp.analysis.ur

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the UrduAnalyzer. */
class TestUrduAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        UrduAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = UrduAnalyzer()
        checkOneTerm(a, "پاکستان", "پاکستان")
        checkOneTerm(a, "پاکستانی", "پاکستانی")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("پاکستان"), false)
        val a: Analyzer = UrduAnalyzer(UrduAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "پاکستان", "پاکستان")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = UrduAnalyzer()
        checkOneTerm(a, "۱۲۳۴", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = UrduAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
