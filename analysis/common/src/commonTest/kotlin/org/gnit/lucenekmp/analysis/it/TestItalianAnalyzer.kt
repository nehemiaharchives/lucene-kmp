package org.gnit.lucenekmp.analysis.it

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestItalianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        ItalianAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a = ItalianAnalyzer()
        // stemming
        checkOneTerm(a, "abbandonata", "abbandonat")
        checkOneTerm(a, "abbandonati", "abbandonat")
        // stopword
        assertAnalyzesTo(a, "dallo", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("abbandonata"), false)
        val a = ItalianAnalyzer(ItalianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "abbandonata", "abbandonata")
        checkOneTerm(a, "abbandonati", "abbandonat")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = ItalianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }

    /** test that the elisionfilter is working */
    @Test
    @Throws(IOException::class)
    fun testContractions() {
        val a = ItalianAnalyzer()
        assertAnalyzesTo(a, "dell'Italia", arrayOf("ital"))
        assertAnalyzesTo(a, "l'Italiano", arrayOf("italian"))
        a.close()
    }
}
