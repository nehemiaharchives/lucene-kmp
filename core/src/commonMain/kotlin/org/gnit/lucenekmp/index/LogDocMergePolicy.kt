package org.gnit.lucenekmp.index

import okio.IOException

/**
 * This is a [LogMergePolicy] that measures size of a segment as the number of documents (not
 * taking deletions into account).
 */
class LogDocMergePolicy : LogMergePolicy() {
    /** Sole constructor, setting all settings to their defaults.  */
    init {
        minMergeSize = DEFAULT_MIN_MERGE_DOCS.toLong()

        // maxMergeSize(ForForcedMerge) are never used by LogDocMergePolicy; set
        // it to Long.MAX_VALUE to disable it
        maxMergeSize = Long.MAX_VALUE
        maxMergeSizeForForcedMerge = Long.MAX_VALUE
    }

    @Throws(IOException::class)
    override fun size(
        info: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Long {
        return sizeDocs(info, mergeContext)
    }

    var minMergeDocs: Int
        /**
         * Get the minimum size for a segment to remain un-merged.
         *
         * @see .setMinMergeDocs *
         */
        get() = minMergeSize.toInt()
        /**
         * Sets the minimum size for the lowest level segments. Any segments below this size are
         * candidates for full-flush merges and merged more aggressively in order to avoid having a long
         * tail of small segments. Large values of this parameter increase the merging cost during
         * indexing if you flush small segments.
         */
        set(minMergeDocs) {
            minMergeSize = minMergeDocs.toLong()
        }

    companion object {
        /** Default minimum segment size. @see setMinMergeDocs  */
        const val DEFAULT_MIN_MERGE_DOCS: Int = 1000
    }
}
