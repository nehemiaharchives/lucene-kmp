package org.gnit.lucenekmp.analysis.my

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the BurmeseAnalyzer. */
class TestBurmeseAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        BurmeseAnalyzer().close()
    }

    /** test tokenization, stopwords, normalization and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = BurmeseAnalyzer()
        assertAnalyzesTo(a, "မြန်မာစာ", arrayOf("မြန်", "မာ", "စာ"))
        assertAnalyzesTo(a, "သူသည်စာအုပ်တွေဖတ်သည်", arrayOf("စာ", "အုပ်", "ဖတ်"))
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("စာအုပ်တွေ"), true)
        val a: Analyzer = BurmeseAnalyzer(CharArraySet.EMPTY_SET, exclusionSet)
        assertAnalyzesTo(a, "စာအုပ်တွေ", arrayOf("စာ", "အုပ်", "တွေ"))
        a.close()
    }

    /** test we fold Myanmar digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = BurmeseAnalyzer()
        checkOneTerm(a, "၁၂၃၄", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = BurmeseAnalyzer()
        assertAnalyzesTo(a, "သူတို့က မြန်မာစာကို ဖတ်သည်", arrayOf("မြန်", "မာ", "စာ", "ဖတ်"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = BurmeseAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
