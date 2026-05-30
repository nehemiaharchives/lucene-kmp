package org.gnit.lucenekmp.analysis.da

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestDanishAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        DanishAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = DanishAnalyzer()
        // stemming
        checkOneTerm(a, "undersøg", "undersøg")
        checkOneTerm(a, "undersøgelse", "undersøg")
        // stopword
        assertAnalyzesTo(a, "på", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("undersøgelse"), false)
        val a: Analyzer = DanishAnalyzer(DanishAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "undersøgelse", "undersøgelse")
        checkOneTerm(a, "undersøg", "undersøg")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = DanishAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
