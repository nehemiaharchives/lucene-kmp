package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import kotlin.random.Random
import kotlin.test.Test

class TestLMJelinekMercerSimilarity : BaseSimilarityTestCase() {

    override fun getSimilarity(random: Random): Similarity {
        // smoothing parameter lambda: (0..1]
        val lambda: Float = when (random.nextInt(3)) {
            0 -> Float.MIN_VALUE // tiny value
            1 -> 1f // maximum value
            else -> random.nextFloat() // random value
        }
        return LMJelinekMercerSimilarity(lambda)
    }

    // tests inherited from BaseSimilarityTestCase
    @Test
    override fun testRandomScoring() = super.testRandomScoring()
}
