package org.gnit.lucenekmp.analysis.es.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleSpanishAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testJesusChristCompoundExpansion() {
        val a = BibleSpanishAnalyzer()
        assertAnalyzesTo(
            a,
            "Jesucristo",
            arrayOf("jesucrist", "jesus", "crist"),
            posIncrements = intArrayOf(1, 0, 0)
        )
        assertAnalyzesTo(a, "Jesús", arrayOf("jesus"))
        assertAnalyzesTo(a, "Cristo", arrayOf("crist"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a = BibleSpanishAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
