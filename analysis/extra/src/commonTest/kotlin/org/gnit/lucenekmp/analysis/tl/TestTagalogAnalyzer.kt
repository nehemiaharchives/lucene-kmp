package org.gnit.lucenekmp.analysis.tl

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the TagalogAnalyzer. */
class TestTagalogAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        TagalogAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = TagalogAnalyzer()
        checkOneTerm(a, "Pilipino", "pilipino")
        checkOneTerm(a, "Tagalog", "tagalog")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("pilipino"), true)
        val a: Analyzer = TagalogAnalyzer(TagalogAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Pilipino", "pilipino")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = TagalogAnalyzer()
        checkOneTerm(a, "1234", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = TagalogAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
