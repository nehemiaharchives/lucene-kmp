package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestEnglishAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        EnglishAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = EnglishAnalyzer()
        checkOneTerm(a, "books", "book")
        checkOneTerm(a, "book", "book")
        assertAnalyzesTo(a, "the", emptyArray())
        checkOneTerm(a, "steven's", "steven")
        checkOneTerm(a, "steven\u2019s", "steven")
        checkOneTerm(a, "steven\uFF07s", "steven")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("books"), false)
        val a: Analyzer = EnglishAnalyzer(EnglishAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "books", "books")
        checkOneTerm(a, "book", "book")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = EnglishAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
