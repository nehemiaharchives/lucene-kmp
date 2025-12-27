package org.gnit.lucenekmp.analysis.ta

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the TamilAnalyzer. */
class TestTamilAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        TamilAnalyzer().close()
    }

    /** test that snowball stemmer is hooked in */
    @Test
    @Throws(Exception::class)
    fun testStemming() {
        val a: Analyzer = TamilAnalyzer()
        // friend
        checkOneTerm(a, "நண்பன்", "நண்")
        // friends
        checkOneTerm(a, "நண்பர்கள்", "நண்")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("நண்பர்கள்"), false)
        val a: Analyzer = TamilAnalyzer(TamilAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "நண்பர்கள்", "நண்பர்கள்")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = TamilAnalyzer()
        checkOneTerm(a, "௧௨௩௪", "1234")
        a.close()
    }

    /** tamil doesn't have case, but test we case-fold any latin-1 etc */
    @Test
    @Throws(Exception::class)
    fun testLowerCase() {
        val a = TamilAnalyzer()
        checkOneTerm(a, "FIFA", "fifa")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = TamilAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
