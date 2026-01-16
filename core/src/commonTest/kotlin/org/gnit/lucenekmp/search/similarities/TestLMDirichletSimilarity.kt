package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import kotlin.random.Random
import kotlin.test.Test

class TestLMDirichletSimilarity : BaseSimilarityTestCase() {

    @Test
    override fun testRandomScoring() = super.testRandomScoring()

    override fun getSimilarity(random: Random): Similarity {
        // smoothing parameter mu, unbounded
        val mu: Float
        when (random.nextInt(4)) {
            0 ->         // minimum value
                mu = 0f

            1 ->         // tiny value
                mu = Float.MIN_VALUE

            2 ->         // maximum value
                // we just limit the test to "reasonable" mu values but don't enforce this anywhere.
                mu = Int.MAX_VALUE.toFloat()

            else ->         // random value
                mu = Int.MAX_VALUE * random.nextFloat()
        }
        return LMDirichletSimilarity(mu)
    }
}
