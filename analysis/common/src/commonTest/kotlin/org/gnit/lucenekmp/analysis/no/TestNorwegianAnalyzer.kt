package org.gnit.lucenekmp.analysis.no

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestNorwegianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        NorwegianAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a = NorwegianAnalyzer()
        // stemming
        checkOneTerm(a, "havnedistriktene", "havnedistrikt")
        checkOneTerm(a, "havnedistrikter", "havnedistrikt")
        // stopword
        assertAnalyzesTo(a, "det", arrayOf())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("havnedistriktene"), false)
        val a = NorwegianAnalyzer(NorwegianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "havnedistriktene", "havnedistriktene")
        checkOneTerm(a, "havnedistrikter", "havnedistrikt")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = NorwegianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}

