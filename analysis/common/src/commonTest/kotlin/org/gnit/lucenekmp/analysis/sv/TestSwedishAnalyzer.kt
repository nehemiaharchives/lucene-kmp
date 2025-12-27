package org.gnit.lucenekmp.analysis.sv

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestSwedishAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        SwedishAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a = SwedishAnalyzer()
        // stemming
        checkOneTerm(a, "jaktkarlarne", "jaktkarl")
        checkOneTerm(a, "jaktkarlens", "jaktkarl")
        // stopword
        assertAnalyzesTo(a, "och", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("jaktkarlarne"), false)
        val a = SwedishAnalyzer(SwedishAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "jaktkarlarne", "jaktkarlarne")
        checkOneTerm(a, "jaktkarlens", "jaktkarl")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = SwedishAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
