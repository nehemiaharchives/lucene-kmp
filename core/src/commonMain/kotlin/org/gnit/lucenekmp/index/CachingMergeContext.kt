package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.InfoStream

/**
 * a wrapper of IndexWriter MergeContext. Try to cache the [ ][.numDeletesToMerge] result in merge phase, to avoid duplicate calculation
 */
internal class CachingMergeContext(val mergeContext: MergePolicy.MergeContext) :
    MergePolicy.MergeContext {
    val cachedNumDeletesToMerge: HashMap<SegmentCommitInfo, Int> = HashMap()

    @Throws(IOException::class)
    override fun numDeletesToMerge(info: SegmentCommitInfo): Int {
        var numDeletesToMerge = cachedNumDeletesToMerge[info]
        if (numDeletesToMerge != null) {
            return numDeletesToMerge
        }
        numDeletesToMerge = mergeContext.numDeletesToMerge(info)
        cachedNumDeletesToMerge.put(info, numDeletesToMerge)
        return numDeletesToMerge
    }

    override fun numDeletedDocs(info: SegmentCommitInfo): Int {
        return mergeContext.numDeletedDocs(info)
    }

    override val infoStream: InfoStream
        get() = mergeContext.infoStream

    override val mergingSegments: MutableSet<SegmentCommitInfo>
        get() = mergeContext.mergingSegments
}
