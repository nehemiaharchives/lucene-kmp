package org.gnit.lucenekmp.analysis.ga

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestIrishAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        IrishAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = IrishAnalyzer()
        // stemming
        checkOneTerm(a, "siopadóireacht", "siopadóir")
        checkOneTerm(a, "síceapatacha", "síceapaite")
        // stopword
        assertAnalyzesTo(a, "le", arrayOf())
        a.close()
    }

    /** test use of elisionfilter */
    @Test
    @Throws(IOException::class)
    fun testContractions() {
        val a: Analyzer = IrishAnalyzer()
        assertAnalyzesTo(a, "b'fhearr m'athair", arrayOf("fearr", "athair"))
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("feirmeoireacht"), false)
        val a: Analyzer = IrishAnalyzer(IrishAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "feirmeoireacht", "feirmeoireacht")
        checkOneTerm(a, "siopadóireacht", "siopadóir")
        a.close()
    }

    /** test special hyphen handling */
    @Test
    @Throws(IOException::class)
    fun testHyphens() {
        val a: Analyzer = IrishAnalyzer()
        assertAnalyzesTo(a, "n-athair", arrayOf("athair"), intArrayOf(2))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = IrishAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
