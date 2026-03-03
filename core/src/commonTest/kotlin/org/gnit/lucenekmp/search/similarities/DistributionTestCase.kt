package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import kotlin.random.Random

abstract class DistributionTestCase : BaseSimilarityTestCase() {

    override fun getSimilarity(random: Random): Similarity {
        val lambda: Lambda =
            if (random.nextBoolean()) {
                LambdaDF()
            } else {
                LambdaTTF()
            }

        // normalization hyper-parameter c
        val c: Float =
            when (random.nextInt(4)) {
                0 -> 0f
                1 -> Float.MIN_VALUE
                2 -> Int.MAX_VALUE.toFloat()
                else -> Int.MAX_VALUE * random.nextFloat()
            }

        // normalization hyper-parameter z
        val z: Float =
            when (random.nextInt(3)) {
                0 -> Float.MIN_VALUE
                1 -> Math.nextDown(0.5f)
                else -> {
                    val zcand = random.nextFloat() / 2
                    if (zcand == 0f) {
                        Math.nextUp(zcand)
                    } else {
                        zcand
                    }
                }
            }

        // dirichlet parameter mu
        val mu: Float =
            when (random.nextInt(4)) {
                0 -> 0f
                1 -> Float.MIN_VALUE
                2 -> Int.MAX_VALUE.toFloat()
                else -> Int.MAX_VALUE * random.nextFloat()
            }

        val normalization: Normalization =
            when (random.nextInt(5)) {
                0 -> Normalization.NoNormalization()
                1 -> NormalizationH1(c)
                2 -> NormalizationH2(c)
                3 -> NormalizationH3(mu)
                else -> NormalizationZ(z)
            }
        return IBSimilarity(getDistribution(), lambda, normalization)
    }

    /** return BasicModel under test */
    protected abstract fun getDistribution(): Distribution
}
