package org.gnit.lucenekmp.analysis.fi

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestFinnishAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        FinnishAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = FinnishAnalyzer()
        // stemming
        checkOneTerm(a, "edeltäjiinsä", "edeltäj")
        checkOneTerm(a, "edeltäjistään", "edeltäj")
        // stopword
        assertAnalyzesTo(a, "olla", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("edeltäjistään"), false)
        val a: Analyzer = FinnishAnalyzer(FinnishAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "edeltäjiinsä", "edeltäj")
        checkOneTerm(a, "edeltäjistään", "edeltäjistään")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = FinnishAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
