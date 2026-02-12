package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.index.FilterMergePolicy
import org.gnit.lucenekmp.index.MergePolicy
import org.gnit.lucenekmp.index.MergeTrigger
import org.gnit.lucenekmp.index.SegmentInfos

/**
 * A [MergePolicy] that only returns forced merges.
 *
 *
 * **NOTE**: Use this policy if you wish to disallow background merges but wish to run
 * optimize/forceMerge segment merges.
 *
 * @lucene.experimental
 */
class ForceMergePolicy
/** Create a new `ForceMergePolicy` around the given `MergePolicy`  */
    (`in`: MergePolicy) : FilterMergePolicy(`in`) {
    @Throws(IOException::class)
    override fun findMerges(
        mergeTrigger: MergeTrigger?, segmentInfos: SegmentInfos?, mergeContext: MergeContext?
    ): MergeSpecification? {
        return null
    }
}
