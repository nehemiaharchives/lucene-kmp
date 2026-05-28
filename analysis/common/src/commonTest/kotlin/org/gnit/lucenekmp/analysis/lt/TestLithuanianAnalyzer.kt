package org.gnit.lucenekmp.analysis.lt

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestLithuanianAnalyzer : BaseTokenStreamTestCase() {

    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        LithuanianAnalyzer().close()
    }

    /** Test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopWord() {
        val a: Analyzer = LithuanianAnalyzer()
        assertAnalyzesTo(a, "man", arrayOf())
        a.close()
    }

    /** Test stemmer exceptions */
    @Test
    @Throws(IOException::class)
    fun testStemExclusion() {
        val set = CharArraySet(1, true)
        set.add("vaikų")
        val a: Analyzer = LithuanianAnalyzer(CharArraySet.EMPTY_SET, set)
        assertAnalyzesTo(a, "vaikų", arrayOf("vaikų"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), LithuanianAnalyzer(), 200 * RANDOM_MULTIPLIER)
    }
}
