package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import kotlin.random.Random
import kotlin.test.Test

class TestIndependenceSaturated : BaseSimilarityTestCase() {

    override fun getSimilarity(random: Random): Similarity {
        return DFISimilarity(IndependenceSaturated())
    }

    // tests inherited from BaseSimilarityTestCase
    @Test
    override fun testRandomScoring() = super.testRandomScoring()
}
