package org.gnit.lucenekmp.analysis.te.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleTeluguAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testChristLocativeNormalization() {
        val a = BibleTeluguAnalyzer()
        assertAnalyzesTo(a, "యేసు క్రీస్తు", arrayOf("యెసు", "క్రిస్త"))
        assertAnalyzesTo(
            a,
            "యేసు క్రీస్తులో",
            arrayOf("యెసు", "క్రిస్తులొ", "క్రిస్త"),
            posIncrements = intArrayOf(1, 1, 0)
        )
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testChristComitativeNormalization() {
        val a = BibleTeluguAnalyzer()
        assertAnalyzesTo(
            a,
            "యేసు క్రీస్తుతోను",
            arrayOf("యెసు", "క్రిస్తుతొను", "క్రిస్త"),
            posIncrements = intArrayOf(1, 1, 0)
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a = BibleTeluguAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
