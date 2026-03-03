package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import kotlin.random.Random
import kotlin.test.Test

class TestIndependenceStandardized : BaseSimilarityTestCase() {

    override fun getSimilarity(random: Random): Similarity {
        return DFISimilarity(IndependenceStandardized())
    }

    // tests inherited from BaseSimilarityTestCase
    @Test
    override fun testRandomScoring() = super.testRandomScoring()
}
