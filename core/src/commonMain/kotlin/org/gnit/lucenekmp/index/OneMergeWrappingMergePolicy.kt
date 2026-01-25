package org.gnit.lucenekmp.index

import okio.IOException

/**
 * A wrapping merge policy that wraps the [MergePolicy.OneMerge]
 * objects returned by the wrapped merge policy.
 *
 * @lucene.experimental
 */
class OneMergeWrappingMergePolicy(
    `in`: MergePolicy,
    private val wrapOneMerge: (OneMerge) -> OneMerge
) : FilterMergePolicy(`in`) {

    @Throws(IOException::class)
    override fun findMerges(
        mergeTrigger: MergeTrigger?,
        segmentInfos: SegmentInfos,
        mergeContext: MergeContext
    ): MergeSpecification? {
        return wrapSpec(`in`.findMerges(mergeTrigger, segmentInfos, mergeContext))
    }

    @Throws(IOException::class)
    override fun findForcedMerges(
        segmentInfos: SegmentInfos,
        maxSegmentCount: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>,
        mergeContext: MergeContext
    ): MergeSpecification? {
        return wrapSpec(
            `in`.findForcedMerges(segmentInfos, maxSegmentCount, segmentsToMerge, mergeContext)
        )
    }

    @Throws(IOException::class)
    override fun findForcedDeletesMerges(
        segmentInfos: SegmentInfos,
        mergeContext: MergeContext
    ): MergeSpecification? {
        return wrapSpec(`in`.findForcedDeletesMerges(segmentInfos, mergeContext))
    }

    @Throws(IOException::class)
    override fun findFullFlushMerges(
        mergeTrigger: MergeTrigger,
        segmentInfos: SegmentInfos,
        mergeContext: MergeContext
    ): MergeSpecification? {
        return wrapSpec(`in`.findFullFlushMerges(mergeTrigger, segmentInfos, mergeContext))
    }

    private fun wrapSpec(spec: MergeSpecification?): MergeSpecification? {
        val wrapped: MergeSpecification? =
            if (spec == null) null else MergeSpecification()
        if (wrapped != null) {
            for (merge in spec!!.merges) {
                wrapped.add(wrapOneMerge(merge))
            }
        }
        return wrapped
    }
}
