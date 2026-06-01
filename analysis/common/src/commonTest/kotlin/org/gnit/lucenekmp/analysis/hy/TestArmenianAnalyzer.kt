package org.gnit.lucenekmp.analysis.hy

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestArmenianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        ArmenianAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = ArmenianAnalyzer()
        // stemming
        checkOneTerm(a, "արծիվ", "արծ")
        checkOneTerm(a, "արծիվներ", "արծ")
        // stopword
        assertAnalyzesTo(a, "է", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("արծիվներ"), false)
        val a: Analyzer = ArmenianAnalyzer(ArmenianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "արծիվներ", "արծիվներ")
        checkOneTerm(a, "արծիվ", "արծ")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = ArmenianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
