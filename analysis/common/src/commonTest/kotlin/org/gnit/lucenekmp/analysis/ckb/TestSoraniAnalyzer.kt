package org.gnit.lucenekmp.analysis.ckb

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test the Sorani analyzer */
class TestSoraniAnalyzer : BaseTokenStreamTestCase() {

    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        SoraniAnalyzer().close()
    }

    @Test
    @Throws(IOException::class)
    fun testStopwords() {
        val a: Analyzer = SoraniAnalyzer()
        assertAnalyzesTo(a, "ئەم پیاوە", arrayOf("پیاو"))
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCustomStopwords() {
        val a: Analyzer = SoraniAnalyzer(CharArraySet.EMPTY_SET)
        assertAnalyzesTo(a, "ئەم پیاوە", arrayOf("ئەم", "پیاو"))
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testReusableTokenStream() {
        val a: Analyzer = SoraniAnalyzer()
        assertAnalyzesTo(a, "پیاوە", arrayOf("پیاو"))
        assertAnalyzesTo(a, "پیاو", arrayOf("پیاو"))
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testWithStemExclusionSet() {
        val set = CharArraySet(1, true)
        set.add("پیاوە")
        val a: Analyzer = SoraniAnalyzer(CharArraySet.EMPTY_SET, set)
        assertAnalyzesTo(a, "پیاوە", arrayOf("پیاوە"))
        a.close()
    }

    /**
     * test we fold digits to latin-1 (these are somewhat rare, but generally a few % of digits still)
     */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = SoraniAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = SoraniAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}

