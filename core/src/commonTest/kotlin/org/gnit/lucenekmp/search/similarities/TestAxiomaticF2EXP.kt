package org.gnit.lucenekmp.search.similarities

import kotlin.test.Test

class TestAxiomaticF2EXP : AxiomaticTestCase() {

    @Test
    override fun testRandomScoring() = super.testRandomScoring()

    override fun getAxiomaticModel(
        s: Float,
        queryLen: Int,
        k: Float
    ): Similarity {
        return AxiomaticF2EXP(s, k)
    }
}
