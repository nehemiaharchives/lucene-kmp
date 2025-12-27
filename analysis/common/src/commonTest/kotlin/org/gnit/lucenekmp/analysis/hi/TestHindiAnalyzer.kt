package org.gnit.lucenekmp.analysis.hi

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the HindiAnalyzer. */
class TestHindiAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        HindiAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = HindiAnalyzer()
        // two ways to write 'hindi' itself.
        checkOneTerm(a, "हिन्दी", "हिंद")
        checkOneTerm(a, "हिंदी", "हिंद")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("हिंदी"), false)
        val a: Analyzer = HindiAnalyzer(HindiAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "हिंदी", "हिंदी")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = HindiAnalyzer()
        checkOneTerm(a, "१२३४", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = HindiAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
