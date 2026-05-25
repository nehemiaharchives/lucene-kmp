package org.gnit.lucenekmp.analysis.jv

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the JavaneseAnalyzer. */
class TestJavaneseAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        JavaneseAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = JavaneseAnalyzer()
        checkOneTerm(a, "Ditulisake", "tulis")
        checkOneTerm(a, "Buku", "buku")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ditulisake"), true)
        val a: Analyzer = JavaneseAnalyzer(JavaneseAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Ditulisake", "ditulisake")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = JavaneseAnalyzer()
        checkOneTerm(a, "1234", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = JavaneseAnalyzer()
        assertAnalyzesTo(a, "aku lan dheweke ditulisake buku", arrayOf("tulis", "buku"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = JavaneseAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
