package org.gnit.lucenekmp.analysis.pa

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the PunjabiAnalyzer. */
class TestPunjabiAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        PunjabiAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = PunjabiAnalyzer()
        checkOneTerm(a, "ਭੱਜਣਾ", "ਭੱਜ")
        checkOneTerm(a, "ਪੜਾਉਂਦਾ", "ਪੜਾ")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ਭੱਜਣਾ"), false)
        val a: Analyzer = PunjabiAnalyzer(PunjabiAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "ਭੱਜਣਾ", "ਭੱਜਣਾ")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = PunjabiAnalyzer()
        checkOneTerm(a, "੧੨੩੪", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = PunjabiAnalyzer()
        assertAnalyzesTo(a, "ਇਹ ਇੱਕ ਉਦਾਹਰਣ ਵਾਕ ਹੈ", arrayOf("ਉਦਾਹਰਣ", "ਵਾਕ"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = PunjabiAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
