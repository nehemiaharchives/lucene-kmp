package org.gnit.lucenekmp.analysis.tr

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestTurkishAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        TurkishAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = TurkishAnalyzer()
        // stemming
        checkOneTerm(a, "ağacı", "ağaç")
        checkOneTerm(a, "ağaç", "ağaç")
        // stopword
        assertAnalyzesTo(a, "dolayı", emptyArray())
        // apostrophes
        checkOneTerm(a, "Kıbrıs'ta", "kıbrıs")
        assertAnalyzesTo(a, "Van Gölü'ne", arrayOf("van", "göl"))
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ağacı"), false)
        val a: Analyzer = TurkishAnalyzer(TurkishAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "ağacı", "ağacı")
        checkOneTerm(a, "ağaç", "ağaç")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer = TurkishAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}

