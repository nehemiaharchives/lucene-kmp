package org.gnit.lucenekmp.analysis.mr

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the MarathiAnalyzer. */
class TestMarathiAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        MarathiAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = MarathiAnalyzer()
        checkOneTerm(a, "पुस्तके", "पुसतक")
        checkOneTerm(a, "मुली", "मुल")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("मुली"), false)
        val a: Analyzer = MarathiAnalyzer(MarathiAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "मुली", "मुली")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = MarathiAnalyzer()
        checkOneTerm(a, "१२३४", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = MarathiAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
