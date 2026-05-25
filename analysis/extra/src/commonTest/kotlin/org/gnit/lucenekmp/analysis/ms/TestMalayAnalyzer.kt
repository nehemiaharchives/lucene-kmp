package org.gnit.lucenekmp.analysis.ms

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the MalayAnalyzer. */
class TestMalayAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        MalayAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = MalayAnalyzer()
        checkOneTerm(a, "Membaca", "baca")
        checkOneTerm(a, "Dituliskan", "tulis")
        assertAnalyzesTo(a, "dan yang kepada", emptyArray())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("membaca"), true)
        val a: Analyzer = MalayAnalyzer(MalayAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Membaca", "membaca")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = MalayAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = MalayAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
