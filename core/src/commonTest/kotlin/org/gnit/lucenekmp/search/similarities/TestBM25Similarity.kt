package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class TestBM25Similarity : BaseSimilarityTestCase() {

    @Test
    override fun testRandomScoring() = super.testRandomScoring()

    @Test
    fun testIllegalK1() {
        var expected: IllegalArgumentException =
            expectThrows(
                IllegalArgumentException::class
            ) {
                BM25Similarity(
                    Float.POSITIVE_INFINITY, 0.75f
                )
            }
        assertTrue(expected.message!!.contains("illegal k1 value"))

        expected =
            expectThrows(
                IllegalArgumentException::class
            ) {
                BM25Similarity(-1f, 0.75f)
            }
        assertTrue(expected.message!!.contains("illegal k1 value"))

        expected =
            expectThrows(
                IllegalArgumentException::class
            ) {
                BM25Similarity(
                    Float.NaN, 0.75f
                )
            }
        assertTrue(expected.message!!.contains("illegal k1 value"))
    }

    @Test
    fun testIllegalB() {
        var expected: IllegalArgumentException =
            expectThrows(
                IllegalArgumentException::class
            ) {
                BM25Similarity(1.2f, 2f)
            }
        assertTrue(expected.message!!.contains("illegal b value"))

        expected =
            expectThrows(
                IllegalArgumentException::class
            ) {
                BM25Similarity(1.2f, -1f)
            }
        assertTrue(expected.message!!.contains("illegal b value"))

        expected =
            expectThrows(
                IllegalArgumentException::class
            ) {
                BM25Similarity(
                    1.2f,
                    Float.POSITIVE_INFINITY
                )
            }
        assertTrue(expected.message!!.contains("illegal b value"))

        expected =
            expectThrows(
                IllegalArgumentException::class
            ) {
                BM25Similarity(1.2f, Float.NaN)
            }
        assertTrue(expected.message!!.contains("illegal b value"))
    }

    override fun getSimilarity(random: Random): Similarity {
        // term frequency normalization parameter k1
        val k1: Float
        when (random.nextInt(4)) {
            0 ->         // minimum value
                k1 = 0f

            1 ->         // tiny value
                k1 = Float.MIN_VALUE

            2 ->         // maximum value
                // upper bounds on individual term's score is 43.262806 * (k1 + 1) * boost
                // we just limit the test to "reasonable" k1 values but don't enforce this anywhere.
                k1 = Int.MAX_VALUE.toFloat()

            else ->         // random value
                k1 = Int.MAX_VALUE * random.nextFloat()
        }

        // length normalization parameter b [0 .. 1]
        val b: Float
        when (random.nextInt(4)) {
            0 ->         // minimum value
                b = 0f

            1 ->         // tiny value
                b = Float.MIN_VALUE

            2 ->         // maximum value
                b = 1f

            else ->         // random value
                b = random.nextFloat()
        }
        return BM25Similarity(k1, b)
    }
}
