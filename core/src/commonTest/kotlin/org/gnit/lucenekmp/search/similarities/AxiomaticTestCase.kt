package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import kotlin.random.Random

abstract class AxiomaticTestCase : BaseSimilarityTestCase() {
    override fun getSimilarity(random: Random): Similarity {
        // axiomatic parameter s
        val s: Float
        when (random.nextInt(4)) {
            0 ->         // minimum value
                s = 0f

            1 ->         // tiny value
                s = Float.MIN_VALUE

            2 ->         // maximum value
                s = 1f

            else ->         // random value
                s = random.nextFloat()
        }
        // axiomatic query length
        val queryLen: Int
        when (random.nextInt(4)) {
            0 ->         // minimum value
                queryLen = 0

            1 ->         // tiny value
                queryLen = 1

            2 ->         // maximum value
                queryLen = Int.MAX_VALUE

            else ->         // random value
                queryLen = random.nextInt(Int.MAX_VALUE)
        }
        // axiomatic parameter k
        val k: Float
        when (random.nextInt(4)) {
            0 ->         // minimum value
                k = 0f

            1 ->         // tiny value
                k = Float.MIN_VALUE

            2 ->         // maximum value
                k = 1f

            else ->         // random value
                k = random.nextFloat()
        }

        return getAxiomaticModel(s, queryLen, k)
    }

    protected abstract fun getAxiomaticModel(
        s: Float,
        queryLen: Int,
        k: Float
    ): Similarity
}
