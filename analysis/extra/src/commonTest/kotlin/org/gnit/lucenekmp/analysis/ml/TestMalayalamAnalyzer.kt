package org.gnit.lucenekmp.analysis.ml

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the MalayalamAnalyzer. */
class TestMalayalamAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        MalayalamAnalyzer().close()
    }

    /** test normalization and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = MalayalamAnalyzer()
        checkOneTerm(a, "പുസ്തകങ്ങൾ", "പുസ്തക")
        checkOneTerm(a, "രാജ്യത്തിൽ", "രാജ്യ")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("പുസ്തകങ്ങൾ"), false)
        val a: Analyzer = MalayalamAnalyzer(MalayalamAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "പുസ്തകങ്ങൾ", "പുസ്തകങ്ങൾ")
        a.close()
    }

    /** test we fold Malayalam digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = MalayalamAnalyzer()
        checkOneTerm(a, "൧൨൩൪", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = MalayalamAnalyzer()
        assertAnalyzesTo(a, "ഈ പുസ്തകങ്ങൾ ആണ്", arrayOf("പുസ്തക"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = MalayalamAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
