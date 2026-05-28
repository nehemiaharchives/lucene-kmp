package org.gnit.lucenekmp.analysis.ca

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestCatalanAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        CatalanAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = CatalanAnalyzer()
        checkOneTerm(a, "llengües", "llengu")
        checkOneTerm(a, "llengua", "llengu")
        assertAnalyzesTo(a, "un", arrayOf())
        a.close()
    }

    /** test use of elisionfilter */
    @Test
    @Throws(IOException::class)
    fun testContractions() {
        val a: Analyzer = CatalanAnalyzer()
        assertAnalyzesTo(
            a,
            "Diccionari de l'Institut d'Estudis Catalans",
            arrayOf("diccion", "inst", "estud", "catalan")
        )
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(1, false)
        exclusionSet.add("llengües")
        val a: Analyzer = CatalanAnalyzer(CatalanAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "llengües", "llengües")
        checkOneTerm(a, "llengua", "llengu")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a = CatalanAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
