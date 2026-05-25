package org.gnit.lucenekmp.analysis.sw

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the SwahiliAnalyzer. */
class TestSwahiliAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        SwahiliAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = SwahiliAnalyzer()
        checkOneTerm(a, "Ninasoma", "som")
        checkOneTerm(a, "Vitabu", "tabu")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ninasoma"), true)
        val a: Analyzer = SwahiliAnalyzer(SwahiliAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Ninasoma", "ninasoma")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = SwahiliAnalyzer()
        checkOneTerm(a, "1234", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = SwahiliAnalyzer()
        assertAnalyzesTo(a, "mimi na mtoto ninasoma kitabu", arrayOf("toto", "som", "tabu"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = SwahiliAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
