package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestFilterMergePolicy : LuceneTestCase() {

    private class RecordingMergePolicy : MergePolicy() {
        val calls = mutableListOf<String>()
        var noCFSRatioValue = 0.0
        var maxCFSSegmentSizeMBValue = 0.0
        val mergeSpecification = MergeSpecification()
        val forcedMergeSpecification = MergeSpecification()
        val forcedDeletesMergeSpecification = MergeSpecification()
        val fullFlushMergeSpecification = MergeSpecification()
        val useCompoundFileResult = true
        val sizeResult = 17L
        val keepFullyDeletedSegmentResult = true
        val numDeletesToMergeResult = 3
        val maxFullFlushMergeSizeResult = 19L

        @Throws(IOException::class)
        override fun findMerges(
            mergeTrigger: MergeTrigger?,
            segmentInfos: SegmentInfos?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            calls.add("findMerges")
            return mergeSpecification
        }

        @Throws(IOException::class)
        override fun findMerges(vararg readers: CodecReader): MergeSpecification {
            calls.add("findMergesReaders")
            return mergeSpecification
        }

        @Throws(IOException::class)
        override fun findForcedMerges(
            segmentInfos: SegmentInfos?,
            maxSegmentCount: Int,
            segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            calls.add("findForcedMerges")
            return forcedMergeSpecification
        }

        @Throws(IOException::class)
        override fun findForcedDeletesMerges(
            segmentInfos: SegmentInfos?,
            mergeContext: MergeContext?
        ): MergeSpecification? {
            calls.add("findForcedDeletesMerges")
            return forcedDeletesMergeSpecification
        }

        @Throws(IOException::class)
        override fun findFullFlushMerges(
            mergeTrigger: MergeTrigger,
            segmentInfos: SegmentInfos,
            mergeContext: MergeContext
        ): MergeSpecification? {
            calls.add("findFullFlushMerges")
            return fullFlushMergeSpecification
        }

        @Throws(IOException::class)
        override fun useCompoundFile(
            infos: SegmentInfos,
            mergedInfo: SegmentCommitInfo,
            mergeContext: MergeContext
        ): Boolean {
            calls.add("useCompoundFile")
            return useCompoundFileResult
        }

        @Throws(IOException::class)
        override fun size(info: SegmentCommitInfo, mergeContext: MergeContext): Long {
            calls.add("size")
            return sizeResult
        }

        override var noCFSRatio: Double
            get() {
                calls.add("getNoCFSRatio")
                return noCFSRatioValue
            }
            set(noCFSRatio) {
                calls.add("setNoCFSRatio")
                noCFSRatioValue = noCFSRatio
            }

        override var maxCFSSegmentSizeMB: Double
            get() {
                calls.add("getMaxCFSSegmentSizeMB")
                return maxCFSSegmentSizeMBValue
            }
            set(v) {
                calls.add("setMaxCFSSegmentSizeMB")
                maxCFSSegmentSizeMBValue = v
            }

        @Throws(IOException::class)
        override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
            calls.add("keepFullyDeletedSegment")
            return keepFullyDeletedSegmentResult
        }

        @Throws(IOException::class)
        override fun numDeletesToMerge(
            info: SegmentCommitInfo,
            delCount: Int,
            readerSupplier: IOSupplier<CodecReader>
        ): Int {
            calls.add("numDeletesToMerge")
            return numDeletesToMergeResult
        }

        override fun maxFullFlushMergeSize(): Long {
            calls.add("maxFullFlushMergeSize")
            return maxFullFlushMergeSizeResult
        }

        override fun toString(): String {
            return "RecordingMergePolicy"
        }
    }

    private class MockMergeContext : MergePolicy.MergeContext {
        override fun numDeletesToMerge(info: SegmentCommitInfo): Int = 0

        override fun numDeletedDocs(info: SegmentCommitInfo): Int = 0

        override val infoStream: InfoStream
            get() = InfoStream.NO_OUTPUT

        override val mergingSegments: MutableSet<SegmentCommitInfo>
            get() = mutableSetOf()
    }

    @Test
    fun testMethodsOverridden() {
        val mergePolicy = RecordingMergePolicy()
        val filterMergePolicy = FilterMergePolicy(mergePolicy)
        val segmentInfos = SegmentInfos(Version.LATEST.major)
        val mergeContext = MockMergeContext()
        val segmentCommitInfo = newSegmentCommitInfo()
        val segmentsToMerge = mutableMapOf(segmentCommitInfo to true)
        val readerIOSupplier = IOSupplier<CodecReader> {
            throw UnsupportedOperationException("not used by this test")
        }

        assertSame(
            mergePolicy.mergeSpecification,
            filterMergePolicy.findMerges(MergeTrigger.EXPLICIT, segmentInfos, mergeContext)
        )
        assertSame(
            mergePolicy.mergeSpecification,
            filterMergePolicy.findMerges(*emptyArray())
        )
        assertSame(
            mergePolicy.forcedMergeSpecification,
            filterMergePolicy.findForcedMerges(segmentInfos, 1, segmentsToMerge, mergeContext)
        )
        assertSame(
            mergePolicy.forcedDeletesMergeSpecification,
            filterMergePolicy.findForcedDeletesMerges(segmentInfos, mergeContext)
        )
        assertSame(
            mergePolicy.fullFlushMergeSpecification,
            filterMergePolicy.findFullFlushMerges(MergeTrigger.FULL_FLUSH, segmentInfos, mergeContext)
        )
        assertEquals(
            mergePolicy.useCompoundFileResult,
            filterMergePolicy.useCompoundFile(segmentInfos, segmentCommitInfo, mergeContext)
        )
        assertEquals(mergePolicy.sizeResult, filterMergePolicy.size(segmentCommitInfo, mergeContext))

        filterMergePolicy.noCFSRatio = 0.5
        assertEquals(0.5, filterMergePolicy.noCFSRatio, 0.0)

        filterMergePolicy.maxCFSSegmentSizeMB = 10.0
        assertEquals(10.0, filterMergePolicy.maxCFSSegmentSizeMB, 0.0)

        assertEquals(
            mergePolicy.keepFullyDeletedSegmentResult,
            filterMergePolicy.keepFullyDeletedSegment(readerIOSupplier)
        )
        assertEquals(
            mergePolicy.numDeletesToMergeResult,
            filterMergePolicy.numDeletesToMerge(segmentCommitInfo, 1, readerIOSupplier)
        )
        assertSame(mergePolicy, filterMergePolicy.unwrap())
        assertEquals(mergePolicy.maxFullFlushMergeSizeResult, filterMergePolicy.maxFullFlushMergeSize())
        assertEquals("FilterMergePolicy(RecordingMergePolicy)", filterMergePolicy.toString())

        assertEquals(
            listOf(
                "findMerges",
                "findMergesReaders",
                "findForcedMerges",
                "findForcedDeletesMerges",
                "findFullFlushMerges",
                "useCompoundFile",
                "size",
                "setNoCFSRatio",
                "getNoCFSRatio",
                "setMaxCFSSegmentSizeMB",
                "getMaxCFSSegmentSizeMB",
                "keepFullyDeletedSegment",
                "numDeletesToMerge",
                "maxFullFlushMergeSize",
            ),
            mergePolicy.calls
        )
    }

    private fun newSegmentCommitInfo(): SegmentCommitInfo {
        val dir = ByteBuffersDirectory()
        val si = SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            "_0",
            1,
            isCompoundFile = false,
            hasBlocks = false,
            null,
            mutableMapOf(),
            StringHelper.randomId(),
            mutableMapOf(),
            null
        )
        return SegmentCommitInfo(si, 0, 0, 0, 0, 0, StringHelper.randomId())
    }
}
