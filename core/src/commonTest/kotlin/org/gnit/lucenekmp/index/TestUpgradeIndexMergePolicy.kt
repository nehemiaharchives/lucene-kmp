package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.index.MergePolicy.MergeSpecification
import org.gnit.lucenekmp.tests.index.BaseMergePolicyTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestUpgradeIndexMergePolicy : BaseMergePolicyTestCase() {

    public override fun mergePolicy(): MergePolicy {
        val `in`: TieredMergePolicy = newTieredMergePolicy()
        // Avoid low values of the max merged segment size which prevent this merge policy from
        // scaling well
        `in`.setMaxMergedSegmentMB(TestUtil.nextInt(random(), 1024, 10 * 1024).toDouble())
        return UpgradeIndexMergePolicy(`in`)
    }

    @Throws(IOException::class)
    override fun assertSegmentInfos(
        policy: MergePolicy,
        infos: SegmentInfos
    ) {
        // no-op
    }

    @Throws(IOException::class)
    override fun assertMerge(
        policy: MergePolicy,
        merge: MergeSpecification
    ) {
        // no-op
    }

    // tests inherited from BaseMergePolicyTestCase

    @Test
    override fun testForceMergeNotNeeded() = super.testForceMergeNotNeeded()

    @Test
    override fun testFindForcedDeletesMerges() = super.testFindForcedDeletesMerges()

    @Test
    override fun testSimulateAppendOnly() = super.testSimulateAppendOnly()

    @Test
    override fun testSimulateUpdates() = super.testSimulateUpdates()

    @Test
    override fun testNoPathologicalMerges() = super.testNoPathologicalMerges()

}
