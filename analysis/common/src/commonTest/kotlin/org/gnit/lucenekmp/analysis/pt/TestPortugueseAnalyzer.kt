package org.gnit.lucenekmp.analysis.pt

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestPortugueseAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        PortugueseAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = PortugueseAnalyzer()
        checkOneTerm(a, "quilométricas", "quilometric")
        checkOneTerm(a, "quilométricos", "quilometric")
        assertAnalyzesTo(a, "não", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(1, false)
        exclusionSet.add("quilométricas")
        val a = PortugueseAnalyzer(PortugueseAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "quilométricas", "quilométricas")
        checkOneTerm(a, "quilométricos", "quilometric")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = PortugueseAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
