package org.gnit.lucenekmp.analysis.si

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the SinhalaAnalyzer. */
class TestSinhalaAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        SinhalaAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = SinhalaAnalyzer()
        checkOneTerm(a, "ගෙදරට", "ගෙදර")
        checkOneTerm(a, "පොත්වලට", "පොත්")
        checkOneTerm(a, "කතාවෙන්", "කතා")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ගෙදරට"), false)
        val a: Analyzer = SinhalaAnalyzer(SinhalaAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "ගෙදරට", "ගෙදරට")
        a.close()
    }

    /** test we fold digits to latin-1. */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = SinhalaAnalyzer()
        checkOneTerm(a, "෦෧෨෩", "0123")
        a.close()
    }

    /** test stopword removal. */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = SinhalaAnalyzer()
        assertAnalyzesTo(a, "මේ සහ ගෙදරට", arrayOf("ගෙදර"))
        a.close()
    }

    /** blast some random strings through the analyzer. */
    @Test
    fun testRandomStrings() {
        val analyzer: Analyzer = SinhalaAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
