package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestAxiomaticSimilarity : LuceneTestCase() {

    @Test
    fun testIllegalS() {
        var expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF2EXP(Float.Companion.POSITIVE_INFINITY, 0.1f)
            }
        assertTrue(expected.message!!.contains("illegal s value"))

        expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF2EXP(-1f, 0.1f)
            }
        assertTrue(expected.message!!.contains("illegal s value"))

        expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF2EXP(Float.Companion.NaN, 0.1f)
            }
        assertTrue(expected.message!!.contains("illegal s value"))
    }

    @Test
    fun testIllegalK() {
        var expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF2EXP(0.35f, 2f)
            }
        assertTrue(expected.message!!.contains("illegal k value"))

        expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF2EXP(0.35f, -1f)
            }
        assertTrue(expected.message!!.contains("illegal k value"))

        expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF2EXP(0.35f, Float.Companion.POSITIVE_INFINITY)
            }
        assertTrue(expected.message!!.contains("illegal k value"))

        expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF2EXP(0.35f, Float.Companion.NaN)
            }
        assertTrue(expected.message!!.contains("illegal k value"))
    }

    @Test
    fun testIllegalQL() {
        var expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF3EXP(0.35f, -1)
            }
        assertTrue(expected.message!!.contains("illegal query length value"))

        expected =
            expectThrows(
                IllegalArgumentException::class,
            ) {
                AxiomaticF2EXP(0.35f, Int.MAX_VALUE + 1f)
            }
        assertTrue(expected.message!!.contains("illegal k value"))
    }
}
