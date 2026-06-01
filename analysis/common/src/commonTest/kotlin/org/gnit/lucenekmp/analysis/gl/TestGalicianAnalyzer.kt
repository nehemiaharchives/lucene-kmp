package org.gnit.lucenekmp.analysis.gl

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestGalicianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        GalicianAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = GalicianAnalyzer()
        // stemming
        checkOneTerm(a, "correspondente", "correspond")
        checkOneTerm(a, "corresponderá", "correspond")
        // stopword
        assertAnalyzesTo(a, "e", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("correspondente"), false)
        val a: Analyzer = GalicianAnalyzer(GalicianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "correspondente", "correspondente")
        checkOneTerm(a, "corresponderá", "correspond")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = GalicianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
