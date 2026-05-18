package org.gnit.lucenekmp.analysis.pt.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBiblePortugueseAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testJesusStaysDistinctFromJesua() {
        val a = BiblePortugueseAnalyzer()
        assertAnalyzesTo(a, "Jesus", arrayOf("jesus"))
        assertAnalyzesTo(a, "Jesua", arrayOf("jesu"))
        assertAnalyzesTo(a, "Jesus Cristo", arrayOf("jesus", "cristo"))
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a = BiblePortugueseAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
