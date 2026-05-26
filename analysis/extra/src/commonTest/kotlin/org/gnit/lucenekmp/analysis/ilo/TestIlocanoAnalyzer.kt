package org.gnit.lucenekmp.analysis.ilo

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the IlocanoAnalyzer. */
class TestIlocanoAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        IlocanoAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = IlocanoAnalyzer()
        checkOneTerm(a, "Ilokáno", "ilokano")
        checkOneTerm(a, "nagadal", "adal")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("nagadal"), true)
        val a: Analyzer = IlocanoAnalyzer(IlocanoAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "nagadal", "nagadal")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = IlocanoAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = IlocanoAnalyzer()
        assertAnalyzesTo(a, "dagiti ubing ket agbasa iti libro", arrayOf("ubing", "basa", "libro"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = IlocanoAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
