package org.gnit.lucenekmp.analysis.bg

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test the Bulgarian analyzer */
class TestBulgarianAnalyzer : BaseTokenStreamTestCase() {

    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        BulgarianAnalyzer().close()
    }

    @Test
    @Throws(okio.IOException::class)
    fun testStopwords() {
        val a = BulgarianAnalyzer()
        assertAnalyzesTo(a, "Как се казваш?", arrayOf("казваш"))
        a.close()
    }

    @Test
    @Throws(okio.IOException::class)
    fun testCustomStopwords() {
        val a = BulgarianAnalyzer(CharArraySet.EMPTY_SET)
        assertAnalyzesTo(a, "Как се казваш?", arrayOf("как", "се", "казваш"))
        a.close()
    }

    @Test
    @Throws(okio.IOException::class)
    fun testReusableTokenStream() {
        val a = BulgarianAnalyzer()
        assertAnalyzesTo(a, "документи", arrayOf("документ"))
        assertAnalyzesTo(a, "документ", arrayOf("документ"))
        a.close()
    }

    /** Test some examples from the paper */
    @Test
    @Throws(okio.IOException::class)
    fun testBasicExamples() {
        val a = BulgarianAnalyzer()
        assertAnalyzesTo(a, "енергийни кризи", arrayOf("енергийн", "криз"))
        assertAnalyzesTo(a, "Атомната енергия", arrayOf("атомн", "енерг"))

        assertAnalyzesTo(a, "компютри", arrayOf("компютр"))
        assertAnalyzesTo(a, "компютър", arrayOf("компютр"))

        assertAnalyzesTo(a, "градове", arrayOf("град"))
        a.close()
    }

    @Test
    @Throws(okio.IOException::class)
    fun testWithStemExclusionSet() {
        val set = CharArraySet(1, true)
        set.add("строеве")
        val a = BulgarianAnalyzer(CharArraySet.EMPTY_SET, set)
        assertAnalyzesTo(a, "строевете строеве", arrayOf("строй", "строеве"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a = BulgarianAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
