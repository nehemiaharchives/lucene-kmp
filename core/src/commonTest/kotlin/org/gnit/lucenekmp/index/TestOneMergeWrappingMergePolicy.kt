package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import org.gnit.lucenekmp.util.InfoStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestOneMergeWrappingMergePolicy : LuceneTestCase() {

    private fun newDirectory(): Directory = ByteBuffersDirectory()

    private class PredeterminedMergePolicy(
        private val merges: MergeSpecification?,
        private val forcedMerges: MergeSpecification?,
        private val forcedDeletesMerges: MergeSpecification?
    ) : MergePolicy() {
        @Throws(IOException::class)
        override fun findMerges(
            mergeTrigger: MergeTrigger?,
            segmentInfos: SegmentInfos?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            return merges
        }

        @Throws(IOException::class)
        override fun findForcedMerges(
            segmentInfos: SegmentInfos?,
            maxSegmentCount: Int,
            segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            return forcedMerges
        }

        @Throws(IOException::class)
        override fun findForcedDeletesMerges(
            segmentInfos: SegmentInfos?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            return forcedDeletesMerges
        }
    }

    private class WrappedOneMerge(val original: MergePolicy.OneMerge) :
        MergePolicy.OneMerge(original.segments)

    @Test
    @Throws(IOException::class)
    fun testSegmentsAreWrapped() {
        newDirectory().use { dir ->
            val msM = createRandomMergeSpecification(dir)
            val msF = createRandomMergeSpecification(dir)
            val msD = createRandomMergeSpecification(dir)
            val originalMP = PredeterminedMergePolicy(msM, msF, msD)
            val oneMergeWrappingMP =
                OneMergeWrappingMergePolicy(originalMP) { merge -> WrappedOneMerge(merge) }
            val mergeTrigger = MergeTrigger.EXPLICIT
            val segmentInfos = SegmentInfos(Version.LATEST.major)
            val mergeContext = object : MergePolicy.MergeContext {
                override fun numDeletesToMerge(info: SegmentCommitInfo): Int = 0
                override fun numDeletedDocs(info: SegmentCommitInfo): Int = 0
                override val infoStream: InfoStream
                    get() = InfoStream.NO_OUTPUT
                override val mergingSegments: MutableSet<SegmentCommitInfo>
                    get() = mutableSetOf()
            }
            implTestSegmentsAreWrapped(
                msM,
                oneMergeWrappingMP.findMerges(mergeTrigger, segmentInfos, mergeContext)
            )
            implTestSegmentsAreWrapped(
                msF,
                oneMergeWrappingMP.findForcedMerges(
                    segmentInfos,
                    0,
                    mutableMapOf(),
                    mergeContext
                )
            )
            implTestSegmentsAreWrapped(
                msD,
                oneMergeWrappingMP.findForcedDeletesMerges(segmentInfos, mergeContext)
            )
        }
    }

    private fun implTestSegmentsAreWrapped(
        originalMS: MergePolicy.MergeSpecification?,
        testMS: MergePolicy.MergeSpecification?
    ) {
        assertEquals(originalMS == null, testMS == null)
        if (originalMS == null) return
        assertEquals(originalMS.merges.size, testMS!!.merges.size)
        for (ii in 0 until originalMS.merges.size) {
            val originalOM = originalMS.merges[ii]
            val testOM = testMS.merges[ii]
            assertTrue(testOM is WrappedOneMerge)
            val wrappedOM = testOM
            assertEquals(originalOM, wrappedOM.original)
        }
    }

    private fun createRandomMergeSpecification(dir: Directory): MergePolicy.MergeSpecification? {
        var ms: MergePolicy.MergeSpecification? = null
        if (0 < random().nextInt(10)) {
            ms = MergePolicy.MergeSpecification()
            for (ii in 0 until random().nextInt(10)) {
                val si = SegmentInfo(
                    dir,
                    Version.LATEST,
                    Version.LATEST,
                    TestUtil.randomSimpleString(random()),
                    kotlin.math.abs(random().nextInt()),
                    random().nextBoolean(),
                    false,
                    null,
                    mutableMapOf(),
                    TestUtil.randomSimpleString(
                        random(),
                        StringHelper.ID_LENGTH,
                        StringHelper.ID_LENGTH
                    ).encodeToByteArray(),
                    mutableMapOf(),
                    null
                )
                val segments = mutableListOf<SegmentCommitInfo>()
                segments.add(SegmentCommitInfo(si, 0, 0, 0, 0, 0, StringHelper.randomId()))
                ms.add(MergePolicy.OneMerge(segments))
            }
        }
        return null
    }
}

