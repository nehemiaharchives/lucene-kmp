package org.gnit.lucenekmp.analysis.lv

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestLatvianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        LatvianAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = LatvianAnalyzer()
        // stemming
        checkOneTerm(a, "tirgiem", "tirg")
        checkOneTerm(a, "tirgus", "tirg")
        // stopword
        assertAnalyzesTo(a, "un", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("tirgiem"), false)
        val a: Analyzer = LatvianAnalyzer(LatvianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "tirgiem", "tirgiem")
        checkOneTerm(a, "tirgus", "tirg")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = LatvianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
