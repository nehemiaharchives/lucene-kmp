package org.gnit.lucenekmp.analysis.gu

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the GujaratiAnalyzer. */
class TestGujaratiAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        GujaratiAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = GujaratiAnalyzer()
        checkOneTerm(a, "ગુજરાતી", "ગુજરાતી")
        checkOneTerm(a, "ગુજરાતીઓ", "ગુજરાતી")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ગુજરાતી"), false)
        val a: Analyzer = GujaratiAnalyzer(GujaratiAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "ગુજરાતી", "ગુજરાતી")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = GujaratiAnalyzer()
        checkOneTerm(a, "૧૨૩૪", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = GujaratiAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
