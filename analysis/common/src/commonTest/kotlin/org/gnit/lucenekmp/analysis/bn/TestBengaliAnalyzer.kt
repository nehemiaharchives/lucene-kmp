package org.gnit.lucenekmp.analysis.bn

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the BengaliAnalyzer. */
class TestBengaliAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        BengaliAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = BengaliAnalyzer()
        checkOneTerm(a, "বাড়ী", "বার")
        checkOneTerm(a, "বারী", "বার")
        a.close()
    }

    /** test Digits */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = BengaliAnalyzer()
        checkOneTerm(a, "১২৩৪৫৬৭৮৯০", "1234567890")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = BengaliAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
