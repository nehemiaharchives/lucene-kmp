package org.gnit.lucenekmp.analysis.`as`

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the AssameseAnalyzer. */
class TestAssameseAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        AssameseAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = AssameseAnalyzer()
        checkOneTerm(a, "ঘৰলৈ", "ঘৰ")
        checkOneTerm(a, "মানুহবোৰৰ", "মানুহ")
        checkOneTerm(a, "কিতাপখনত", "কিতাপ")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ঘৰলৈ"), false)
        val a: Analyzer = AssameseAnalyzer(AssameseAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "ঘৰলৈ", "ঘৰলৈ")
        a.close()
    }

    /** test we fold digits to latin-1. */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = AssameseAnalyzer()
        checkOneTerm(a, "১২৩৪", "1234")
        a.close()
    }

    /** test stopword removal. */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = AssameseAnalyzer()
        assertAnalyzesTo(a, "এই আৰু ঘৰলৈ", arrayOf("ঘৰ"))
        a.close()
    }

    /** blast some random strings through the analyzer. */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = AssameseAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
