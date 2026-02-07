package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.util.IOSupplier
import okio.IOException

/**
 * A [MergePolicy] which never returns merges to execute. Use it if you want to prevent segment merges.
 */
class NoMergePolicy private constructor() : MergePolicy() {

    companion object {
        /** Singleton instance. */
        val INSTANCE: MergePolicy = NoMergePolicy()
    }

    override fun findMerges(
        mergeTrigger: MergeTrigger?,
        segmentInfos: SegmentInfos?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        return null
    }

    @Throws(IOException::class)
    override fun findMerges(vararg readers: CodecReader): MergeSpecification {
        // retain default behavior from super to allow addIndexes
        return super.findMerges(*readers)
    }

    override fun findForcedMerges(
        segmentInfos: SegmentInfos?,
        maxSegmentCount: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        return null
    }

    override fun findForcedDeletesMerges(
        segmentInfos: SegmentInfos?,
        mergeContext: MergeContext?
    ): MergeSpecification? {
        return null
    }

    override fun findFullFlushMerges(
        mergeTrigger: MergeTrigger,
        segmentInfos: SegmentInfos,
        mergeContext: MergeContext
    ): MergeSpecification? {
        return null
    }

    override fun useCompoundFile(
        segments: SegmentInfos,
        newSegment: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Boolean {
        return newSegment.info.useCompoundFile
    }

    @Throws(IOException::class)
    override fun size(info: SegmentCommitInfo, context: MergeContext): Long {
        return Long.MAX_VALUE
    }

    override var noCFSRatio: Double
        get() = super.noCFSRatio
        set(noCFSRatio) {
            super.noCFSRatio = noCFSRatio
        }

    override var maxCFSSegmentSizeMB: Double
        get() = super.maxCFSSegmentSizeMB
        set(v) {
            super.maxCFSSegmentSizeMB = v
        }

    override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
        return super.keepFullyDeletedSegment(readerIOSupplier)
    }

    @Throws(IOException::class)
    override fun numDeletesToMerge(
        info: SegmentCommitInfo,
        delCount: Int,
        readerSupplier: IOSupplier<CodecReader>
    ): Int {
        return super.numDeletesToMerge(info, delCount, readerSupplier)
    }

    override fun toString(): String {
        return "NoMergePolicy"
    }
}

