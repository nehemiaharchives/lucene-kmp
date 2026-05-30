package org.gnit.lucenekmp.analysis.eu

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBasqueAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        BasqueAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = BasqueAnalyzer()
        // stemming
        checkOneTerm(a, "zaldi", "zaldi")
        checkOneTerm(a, "zaldiak", "zaldi")
        // stopword
        assertAnalyzesTo(a, "izan", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("zaldiak"), false)
        val a: Analyzer = BasqueAnalyzer(BasqueAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "zaldiak", "zaldiak")
        checkOneTerm(a, "mendiari", "mendi")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = BasqueAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
