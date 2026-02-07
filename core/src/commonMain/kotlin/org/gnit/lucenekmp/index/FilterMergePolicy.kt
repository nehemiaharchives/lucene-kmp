package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.Unwrappable
import okio.IOException

/**
 * A wrapper for [MergePolicy] instances.
 *
 * @lucene.experimental
 */
open class FilterMergePolicy(
    /** The wrapped [MergePolicy].  */
    protected val `in`: MergePolicy
) : MergePolicy(), Unwrappable<MergePolicy> {

    @Throws(IOException::class)
    override fun findMerges(
        mergeTrigger: MergeTrigger?,
        segmentInfos: SegmentInfos?,
        mergeContext:MergeContext?
    ):MergeSpecification? {
        return `in`.findMerges(mergeTrigger, segmentInfos, mergeContext)
    }

    @Throws(IOException::class)
    override fun findMerges(vararg readers: CodecReader):MergeSpecification {
        return `in`.findMerges(*readers)
    }

    @Throws(IOException::class)
    override fun findForcedMerges(
        segmentInfos: SegmentInfos?,
        maxSegmentCount: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
        mergeContext:MergeContext?
    ):MergeSpecification? {
        return `in`.findForcedMerges(segmentInfos, maxSegmentCount, segmentsToMerge, mergeContext)
    }

    @Throws(IOException::class)
    override fun findForcedDeletesMerges(
        segmentInfos: SegmentInfos?,
        mergeContext:MergeContext?
    ):MergeSpecification? {
        return `in`.findForcedDeletesMerges(segmentInfos, mergeContext)
    }

    @Throws(IOException::class)
    override fun findFullFlushMerges(
        mergeTrigger: MergeTrigger,
        segmentInfos: SegmentInfos,
        mergeContext:MergeContext
    ):MergeSpecification? {
        return `in`.findFullFlushMerges(mergeTrigger, segmentInfos, mergeContext)
    }

    @Throws(IOException::class)
    override fun useCompoundFile(
        infos: SegmentInfos,
        mergedInfo: SegmentCommitInfo,
        mergeContext:MergeContext
    ): Boolean {
        return `in`.useCompoundFile(infos, mergedInfo, mergeContext)
    }

    @Throws(IOException::class)
    override fun size(
        info: SegmentCommitInfo,
        context:MergeContext
    ): Long {
        return `in`.size(info, context)
    }

    override var noCFSRatio: Double
        get() = `in`.noCFSRatio
        set(noCFSRatio) {
            `in`.noCFSRatio = noCFSRatio
        }

    override var maxCFSSegmentSizeMB: Double
        get() = `in`.maxCFSSegmentSizeMB
        set(v) {
            `in`.maxCFSSegmentSizeMB = v
        }

    override fun toString(): String {
        return this::class.simpleName + "(" + `in` + ")"
    }

    @Throws(IOException::class)
    override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
        return `in`.keepFullyDeletedSegment(readerIOSupplier)
    }

    @Throws(IOException::class)
    override fun numDeletesToMerge(
        info: SegmentCommitInfo,
        delCount: Int,
        readerSupplier: IOSupplier<CodecReader>
    ): Int {
        return `in`.numDeletesToMerge(info, delCount, readerSupplier)
    }

    override fun unwrap(): MergePolicy {
        return `in`
    }

    override fun maxFullFlushMergeSize(): Long {
        return `in`.maxFullFlushMergeSize()
    }
}
