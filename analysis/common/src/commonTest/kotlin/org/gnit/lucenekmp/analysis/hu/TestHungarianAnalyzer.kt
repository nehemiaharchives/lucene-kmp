package org.gnit.lucenekmp.analysis.hu

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestHungarianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        HungarianAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = HungarianAnalyzer()
        // stemming
        checkOneTerm(a, "babakocsi", "babakocs")
        checkOneTerm(a, "babakocsijáért", "babakocs")
        // stopword
        assertAnalyzesTo(a, "által", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("babakocsi"), false)
        val a: Analyzer = HungarianAnalyzer(HungarianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "babakocsi", "babakocsi")
        checkOneTerm(a, "babakocsijáért", "babakocs")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = HungarianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
