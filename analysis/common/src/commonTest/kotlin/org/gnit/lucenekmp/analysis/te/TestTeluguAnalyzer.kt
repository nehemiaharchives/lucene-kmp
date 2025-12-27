package org.gnit.lucenekmp.analysis.te

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests the TeluguAnalyzer. */
class TestTeluguAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        TeluguAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = TeluguAnalyzer()
        // two ways to write oo letter.
        checkOneTerm(a, "ఒౕనమాల", "ఓనమాల")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("వస్తువులు"), false)
        val a: Analyzer = TeluguAnalyzer(TeluguAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "వస్తువులు", "వస్తుమలు")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = TeluguAnalyzer()
        checkOneTerm(a, "౧౨౩౪", "1234")
        a.close()
    }

    @Test
    fun testNormalization() {
        val a = TeluguAnalyzer()
        // DecimalDigitsFilter
        assertEquals(BytesRef("1234"), a.normalize("dummy", "౧౨౩౪"))
        // IndicNormalizationFilter
        assertEquals(BytesRef("ऑऑ"), a.normalize("dummy", "अाॅअाॅ"))
        // TeluguNormalizationFilter
        assertEquals(BytesRef("ఓనమాల"), a.normalize("dummy", "ఒౕనమాల"))
        a.close()
    }

    /** Send some random strings to the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = TeluguAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
