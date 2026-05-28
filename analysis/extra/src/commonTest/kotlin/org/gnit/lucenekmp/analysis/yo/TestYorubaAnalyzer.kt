package org.gnit.lucenekmp.analysis.yo

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the YorubaAnalyzer. */
class TestYorubaAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        YorubaAnalyzer().close()
    }

    /** test stopwords, normalization and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = YorubaAnalyzer()
        checkOneTerm(a, "Yorùbá", "yoruba")
        checkOneTerm(a, "ìkọwé", "kowe")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("yoruba"), true)
        val a: Analyzer = YorubaAnalyzer(YorubaAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Yorùbá", "yoruba")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = YorubaAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = YorubaAnalyzer()
        assertAnalyzesTo(a, "àwọn ọmọ ní ilé ìkọwé", arrayOf("omo", "ile", "kowe"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = YorubaAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
