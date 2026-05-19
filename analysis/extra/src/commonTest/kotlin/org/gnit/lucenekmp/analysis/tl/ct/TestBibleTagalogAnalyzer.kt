package org.gnit.lucenekmp.analysis.tl.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleTagalogAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testJesusChristCompoundExpansion() {
        val a = BibleTagalogAnalyzer()
        assertAnalyzesTo(
            a,
            "Jesucristo",
            arrayOf("jesucristo", "jesus", "cristo"),
            posIncrements = intArrayOf(1, 0, 0)
        )
        assertAnalyzesTo(a, "Jesus", arrayOf("jesus"))
        assertAnalyzesTo(a, "Cristo", arrayOf("cristo"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a = BibleTagalogAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
