package org.gnit.lucenekmp.search.similarities

import kotlin.test.Test

class TestDistributionSPL : DistributionTestCase() {

    override fun getDistribution(): Distribution {
        return DistributionSPL()
    }

    // tests inherited from BaseSimilarityTestCase
    @Test
    override fun testRandomScoring() = super.testRandomScoring()
}
