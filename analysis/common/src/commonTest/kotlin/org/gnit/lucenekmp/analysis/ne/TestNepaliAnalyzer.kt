package org.gnit.lucenekmp.analysis.ne

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the NepaliAnalyzer. */
class TestNepaliAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        NepaliAnalyzer().close()
    }

    /** test that snowball stemmer is hooked in correctly */
    @Test
    @Throws(Exception::class)
    fun testStemming() {
        val a: Analyzer = NepaliAnalyzer()
        // friend
        checkOneTerm(a, "मित्र", "मित्र")
        // friends
        checkOneTerm(a, "मित्रहरु", "मित्र")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a: Analyzer = NepaliAnalyzer()
        assertAnalyzesTo(
            a,
            "सबै व्यक्तिहरू जन्मजात स्वतन्त्र हुन् ती सबैको समान अधिकार र महत्व",
            arrayOf("व्यक्ति", "जन्मजात", "स्वतन्त्र", "सबै", "समान", "अधिकार", "महत्व")
        )
        a.close()
    }

    /** nepali has no case, but any latin-1 etc should be casefolded */
    @Test
    @Throws(Exception::class)
    fun testLowerCase() {
        val a: Analyzer = NepaliAnalyzer()
        checkOneTerm(a, "FIFA", "fifa")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("मित्रहरु"), false)
        val a: Analyzer = NepaliAnalyzer(NepaliAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "मित्रहरु", "मित्रहरु")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = NepaliAnalyzer()
        checkOneTerm(a, "१२३४", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = NepaliAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
