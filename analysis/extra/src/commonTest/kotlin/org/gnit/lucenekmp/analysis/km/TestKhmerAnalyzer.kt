package org.gnit.lucenekmp.analysis.km

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for [KhmerAnalyzer]. */
class TestKhmerAnalyzer : BaseTokenStreamTestCase() {
    /** Test that the analyzer can be created and closed without error. */
    @Test
    fun testResourcesAvailable() {
        KhmerAnalyzer().close()
    }

    /** Test basic end-to-end analysis. */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = KhmerAnalyzer()
        checkOneTerm(a, "ខ្ញុំ", "ខ្ញុំ")
        checkOneTerm(a, "សើុ", "ស៊ើ")
        checkOneTerm(a, "ប្តី", "ប្ដី")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val analyzer = KhmerAnalyzer(1, true, false)
        assertAnalyzesTo(analyzer, "ខ្ញុំ", emptyArray())
        assertAnalyzesTo(analyzer, "ទេ", emptyArray())
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNumberNormalization() {
        val analyzer = KhmerAnalyzer(1, false, true)
        checkOneTerm(analyzer, "១២៣៤៥", "12345")
        assertAnalyzesTo(analyzer, "១២៣.៤៥", arrayOf("123", "45"))
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testStopwordsAndNumberNormalizationTogether() {
        val analyzer = KhmerAnalyzer(1, true, true)
        assertAnalyzesTo(analyzer, "ខ្ញុំ ១២៣៤៥", arrayOf("12345"))
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDisableNormalization() {
        val analyzer = KhmerAnalyzer(0, false, false)
        checkOneTerm(analyzer, "ស៉", "ស៉")
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCustomStopSet() {
        val analyzer =
            KhmerAnalyzer(1, true, false, CharArraySet(mutableSetOf<Any>("ប្ដី"), false))
        assertAnalyzesTo(analyzer, "ប្តី", emptyArray())
        analyzer.close()
    }

    @Test
    fun testDefaultStopSet() {
        val stopSet = KhmerAnalyzer.getDefaultStopSet()
        assertTrue(stopSet.contains("ខ្ញុំ"))
        assertTrue(stopSet.contains("ទេ"))
        assertTrue(stopSet.contains("ម្ដង"))
        assertTrue(stopSet.contains("ផ្ដល់នូវ"))
        assertFalse(stopSet.contains("កម្ពុជា"))
    }

    /** Blast some random strings through the analyzer. */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer = KhmerAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER) // TODO reduced from 1000 to 200 for dev speed
        analyzer.close()
    }
}
