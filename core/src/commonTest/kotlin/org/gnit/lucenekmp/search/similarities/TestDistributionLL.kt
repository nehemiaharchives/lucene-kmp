package org.gnit.lucenekmp.search.similarities

import kotlin.test.Test

class TestDistributionLL : DistributionTestCase() {

    override fun getDistribution(): Distribution {
        return DistributionLL()
    }

    // tests inherited from BaseSimilarityTestCase
    @Test
    override fun testRandomScoring() = super.testRandomScoring()
}
