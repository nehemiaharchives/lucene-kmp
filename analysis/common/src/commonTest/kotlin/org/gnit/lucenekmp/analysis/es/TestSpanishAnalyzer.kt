package org.gnit.lucenekmp.analysis.es

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestSpanishAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        SpanishAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = SpanishAnalyzer()
        checkOneTerm(a, "chicana", "chican")
        checkOneTerm(a, "chicano", "chican")
        assertAnalyzesTo(a, "los", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("chicano"), false)
        val a: Analyzer = SpanishAnalyzer(SpanishAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "chicana", "chican")
        checkOneTerm(a, "chicano", "chicano")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = SpanishAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
