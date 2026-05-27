package org.gnit.lucenekmp.analysis.ti

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the TigrinyaAnalyzer. */
class TestTigrinyaAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        TigrinyaAnalyzer().close()
    }

    /** test stopwords, normalization and stemming */
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = TigrinyaAnalyzer()
        checkOneTerm(a, "መፅሐፍታት", "መጽሀፍ")
        checkOneTerm(a, "ሠላም", "ሰላም")
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("መፅሐፍታት"), true)
        val a: Analyzer = TigrinyaAnalyzer(TigrinyaAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "መፅሐፍታት", "መፅሐፍታት")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = TigrinyaAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = TigrinyaAnalyzer()
        assertAnalyzesTo(a, "እቲ ኣብ መፅሐፍታት እዩ", arrayOf("መጽሀፍ"))
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = TigrinyaAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
