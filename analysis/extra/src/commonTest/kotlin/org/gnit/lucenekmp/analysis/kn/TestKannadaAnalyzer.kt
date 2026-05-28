package org.gnit.lucenekmp.analysis.kn

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the KannadaAnalyzer. */
class TestKannadaAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        KannadaAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = KannadaAnalyzer()
        checkOneTerm(a, "ಮನೆಗೆ", "ಮನೆ")
        checkOneTerm(a, "ಪುಸ್ತಕಗಳನ್ನು", "ಪುಸ್ತಕ")
        checkOneTerm(a, "ಕನ್ನಡದಲ್ಲಿ", "ಕನ್ನಡ")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ಮನೆಗೆ"), false)
        val a: Analyzer = KannadaAnalyzer(KannadaAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "ಮನೆಗೆ", "ಮನೆಗೆ")
        a.close()
    }

    /** test we fold digits to latin-1. */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = KannadaAnalyzer()
        checkOneTerm(a, "೧೨೩೪", "1234")
        a.close()
    }

    /** test stopword removal. */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = KannadaAnalyzer()
        assertAnalyzesTo(a, "ಈ ಮತ್ತು ಮನೆಗೆ", arrayOf("ಮನೆ"))
        a.close()
    }

    /** blast some random strings through the analyzer. */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = KannadaAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
