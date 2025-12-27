package org.gnit.lucenekmp.analysis.id

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestIndonesianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        IndonesianAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a = IndonesianAnalyzer()
        // stemming
        checkOneTerm(a, "peledakan", "ledak")
        checkOneTerm(a, "pembunuhan", "bunuh")
        // stopword
        assertAnalyzesTo(a, "bahwa", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("peledakan"), false)
        val a = IndonesianAnalyzer(IndonesianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "peledakan", "peledakan")
        checkOneTerm(a, "pembunuhan", "bunuh")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = IndonesianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
