package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.index.MergePolicy.MergeContext
import org.gnit.lucenekmp.index.MergePolicy.MergeSpecification
import org.gnit.lucenekmp.tests.index.BaseMergePolicyTestCase
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestLogMergePolicy : BaseMergePolicyTestCase() {
    public override fun mergePolicy(): LogMergePolicy {
        return newLogMergePolicy(random())
    }

    @Test
    fun testDefaultForcedMergeMB() {
        val mp = LogByteSizeMergePolicy()
        assertTrue(mp.maxMergeMBForForcedMerge > 0.0)
    }

    @Throws(IOException::class)
    override fun assertSegmentInfos(policy: MergePolicy, infos: SegmentInfos) {
        val mp: LogMergePolicy =
            policy as LogMergePolicy

        val mockMergeContext: MergeContext =
            MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        for (info in infos) {
            assertTrue(mp.size(info, mockMergeContext) / mp.mergeFactor < mp.maxMergeSize)
        }
        // TODO: what else can we check
    }

    @Throws(IOException::class)
    override fun assertMerge(policy: MergePolicy, merge: MergeSpecification) {
        val lmp: LogMergePolicy = policy as LogMergePolicy
        val mockMergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        for (oneMerge in merge.merges) {
            var mergeSize: Long = 0
            for (info in oneMerge.segments) {
                mergeSize += lmp.size(info, mockMergeContext)
            }
            assertTrue(
                mergeSize <= lmp.minMergeSize || oneMerge.segments.size <= lmp.mergeFactor,
                "mergeSize: " + mergeSize + " minMergeSize: " + lmp.minMergeSize + " segmentsCount: " + oneMerge.segments.size + " mergeFactor: " + lmp.mergeFactor
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testIncreasingSegmentSizes() {
        val mergePolicy = LogDocMergePolicy()
        val stats = IOStats()
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos =
            SegmentInfos(Version.LATEST.major)
        // 11 segments of increasing sizes
        for (i in 0..10) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    (i + 1) * 1000,
                    0,
                    0.0,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }
        val spec: MergeSpecification? = mergePolicy.findMerges(MergeTrigger.EXPLICIT, segmentInfos, mergeContext)
        assertNotNull(spec)
        for (oneMerge in spec.merges) {
            segmentInfos = applyMerge(segmentInfos, oneMerge, "_" + segNameGenerator.fetchAndIncrement(), stats)
        }
        assertEquals(2, segmentInfos.size().toLong())
        assertEquals(55000, segmentInfos.info(0).info.maxDoc().toLong())
        assertEquals(11000, segmentInfos.info(1).info.maxDoc().toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testOneSmallMiddleSegment() {
        val mergePolicy = LogDocMergePolicy()
        val stats = IOStats()
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos = SegmentInfos(Version.LATEST.major)
        // 5 big segments
        for (i in 0..4) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    10000,
                    0,
                    0.0,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }
        // 1 segment on a lower tier
        segmentInfos.add(
            makeSegmentCommitInfo(
                "_" + segNameGenerator.fetchAndIncrement(),
                100,
                0,
                0.0,
                IndexWriter.SOURCE_MERGE
            )
        )
        // 5 big segments again
        for (i in 0..4) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    10000,
                    0,
                    0.0,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }
        // Ensure that having a small segment in the middle doesn't prevent merging
        val spec: MergeSpecification? =
            mergePolicy.findMerges(
                MergeTrigger.EXPLICIT,
                segmentInfos,
                mergeContext
            )
        assertNotNull(spec)
        for (oneMerge in spec.merges) {
            segmentInfos =
                applyMerge(
                    segmentInfos,
                    oneMerge,
                    "_" + segNameGenerator.fetchAndIncrement(),
                    stats
                )
        }
        assertEquals(2, segmentInfos.size().toLong())
        assertEquals(90100, segmentInfos.info(0).info.maxDoc().toLong())
        assertEquals(10000, segmentInfos.info(1).info.maxDoc().toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testManySmallMiddleSegment() {
        val mergePolicy = LogDocMergePolicy()
        val stats = IOStats()
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos = SegmentInfos(Version.LATEST.major)
        // 1 big segment
        segmentInfos.add(
            makeSegmentCommitInfo(
                "_" + segNameGenerator.fetchAndIncrement(),
                10000,
                0,
                0.0,
                IndexWriter.SOURCE_MERGE
            )
        )
        // 9 segment on a lower tier
        for (i in 0..8) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    100,
                    0,
                    0.0,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }
        // 1 big segment again
        segmentInfos.add(
            makeSegmentCommitInfo(
                "_" + segNameGenerator.fetchAndIncrement(),
                10000,
                0,
                0.0,
                IndexWriter.SOURCE_MERGE
            )
        )
        // Ensure that having small segments in the middle doesn't prevent merging
        val spec: MergeSpecification? =
            mergePolicy.findMerges(
                MergeTrigger.EXPLICIT,
                segmentInfos,
                mergeContext
            )
        assertNotNull(spec)
        for (oneMerge in spec.merges) {
            segmentInfos = applyMerge(segmentInfos, oneMerge, "_" + segNameGenerator.fetchAndIncrement(), stats)
        }
        assertEquals(2, segmentInfos.size().toLong())
        assertEquals(10900, segmentInfos.info(0).info.maxDoc().toLong())
        assertEquals(10000, segmentInfos.info(1).info.maxDoc().toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testRejectUnbalancedMerges() {
        val mergePolicy = LogDocMergePolicy()
        mergePolicy.minMergeDocs = 10000
        val stats = IOStats()
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos = SegmentInfos(Version.LATEST.major)
        // 1 100-docs segment
        segmentInfos.add(
            makeSegmentCommitInfo(
                "_" + segNameGenerator.fetchAndIncrement(),
                100,
                0,
                0.0,
                IndexWriter.SOURCE_MERGE
            )
        )
        // 9 1-doc segments again
        for (i in 0..8) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    1,
                    0,
                    0.0,
                    IndexWriter.SOURCE_FLUSH
                )
            )
        }
        // Ensure though we're below the floor size, the merge would be too unbalanced
        var spec: MergeSpecification? =
            mergePolicy.findMerges(
                MergeTrigger.EXPLICIT,
                segmentInfos,
                mergeContext
            )
        assertNull(spec)

        // another 1-doc segment, now we can merge 10 1-doc segments
        segmentInfos.add(
            makeSegmentCommitInfo(
                "_" + segNameGenerator.fetchAndIncrement(),
                1,
                0,
                0.0,
                IndexWriter.SOURCE_FLUSH
            )
        )
        spec = mergePolicy.findMerges(MergeTrigger.EXPLICIT, segmentInfos, mergeContext)
        assertNotNull(spec)
        for (oneMerge in spec.merges) {
            segmentInfos =
                applyMerge(
                    segmentInfos,
                    oneMerge,
                    "_" + segNameGenerator.fetchAndIncrement(),
                    stats
                )
        }
        assertEquals(2, segmentInfos.size().toLong())
        assertEquals(100, segmentInfos.info(0).info.maxDoc().toLong())
        assertEquals(10, segmentInfos.info(1).info.maxDoc().toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testPackLargeSegments() {
        val mergePolicy = LogDocMergePolicy()
        val stats = IOStats()
        mergePolicy.maxMergeDocs = 10000
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos =
            SegmentInfos(Version.LATEST.major)
        // 10 segments below the max segment size, but larger than maxMergeSize/mergeFactor
        for (i in 0..9) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    3000,
                    0,
                    0.0,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }
        val spec: MergeSpecification? =
            mergePolicy.findMerges(
                MergeTrigger.EXPLICIT,
                segmentInfos,
                mergeContext
            )
        assertNotNull(spec)
        for (oneMerge in spec.merges) {
            segmentInfos =
                applyMerge(
                    segmentInfos,
                    oneMerge,
                    "_" + segNameGenerator.fetchAndIncrement(),
                    stats
                )
        }
        // LogMP packed 3 3k segments together
        assertEquals(9000, segmentInfos.info(0).info.maxDoc().toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testIgnoreLargeSegments() {
        val mergePolicy = LogDocMergePolicy()
        val stats = IOStats()
        mergePolicy.maxMergeDocs = 10000
        val segNameGenerator = AtomicLong(0)
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos =
            SegmentInfos(Version.LATEST.major)
        // 1 segment that reached the maximum segment size
        segmentInfos.add(
            makeSegmentCommitInfo(
                "_" + segNameGenerator.fetchAndIncrement(),
                11000,
                0,
                0.0,
                IndexWriter.SOURCE_MERGE
            )
        )
        // and 10 segments below the max segment size, but within the same level
        for (i in 0..9) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    2000,
                    0,
                    0.0,
                    IndexWriter.SOURCE_MERGE
                )
            )
        }
        // LogMergePolicy used to have a bug that would make it exclude the first mergeFactor segments
        // from merging if any of them was above the maximum merged size
        val spec: MergeSpecification? =
            mergePolicy.findMerges(
                MergeTrigger.EXPLICIT,
                segmentInfos,
                mergeContext
            )
        assertNotNull(spec)
        for (oneMerge in spec.merges) {
            segmentInfos =
                applyMerge(
                    segmentInfos,
                    oneMerge,
                    "_" + segNameGenerator.fetchAndIncrement(),
                    stats
                )
        }
        assertEquals(11000, segmentInfos.info(0).info.maxDoc().toLong())
        assertEquals(10000, segmentInfos.info(1).info.maxDoc().toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testFullFlushMerges() {
        val segNameGenerator = AtomicLong(0)
        val stats = IOStats()
        val mergeContext: MergeContext = MockMergeContext { obj: SegmentCommitInfo -> obj.delCount }
        var segmentInfos = SegmentInfos(Version.LATEST.major)

        val mp: LogMergePolicy = mergePolicy()
        // Number of segments guaranteed to trigger a merge.
        val numSegmentsForMerging: Int = mp.mergeFactor + mp.targetSearchConcurrency

        for (i in 0..<numSegmentsForMerging) {
            segmentInfos.add(
                makeSegmentCommitInfo(
                    "_" + segNameGenerator.fetchAndIncrement(),
                    1,
                    0,
                    Double.MIN_VALUE,
                    IndexWriter.SOURCE_FLUSH
                )
            )
        }
        val spec: MergeSpecification? = mp.findFullFlushMerges(MergeTrigger.FULL_FLUSH, segmentInfos, mergeContext)
        assertNotNull(spec)
        for (merge in spec.merges) {
            segmentInfos = applyMerge(segmentInfos, merge, "_" + segNameGenerator.fetchAndIncrement(), stats)
        }
        assertTrue(segmentInfos.size() < numSegmentsForMerging)
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
