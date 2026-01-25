package org.gnit.lucenekmp.index

import okio.IOException

/**
 * This is a [LogMergePolicy] that measures size of a segment as the total byte size of the
 * segment's files.
 */
class LogByteSizeMergePolicy : LogMergePolicy() {
    /** Sole constructor, setting all settings to their defaults.  */
    init {
        minMergeSize = (DEFAULT_MIN_MERGE_MB * 1024 * 1024).toLong()
        maxMergeSize = (DEFAULT_MAX_MERGE_MB * 1024 * 1024).toLong()
        // NOTE: in Java, if you cast a too-large double to long, as we are doing here, then it becomes
        // Long.MAX_VALUE
        maxMergeSizeForForcedMerge = (DEFAULT_MAX_MERGE_MB_FOR_FORCED_MERGE * 1024 * 1024).toLong()
    }

    @Throws(IOException::class)
    override fun size(
        info: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Long {
        return sizeBytes(info, mergeContext)
    }

    var maxMergeMB: Double
        /**
         * Returns the largest segment (measured by total byte size of the segment's files, in MB) that
         * may be merged with other segments.
         *
         * @see .setMaxMergeMB
         */
        get() = (maxMergeSize.toDouble()) / 1024 / 1024
        /**
         * Determines the largest segment (measured by total byte size of the segment's files, in MB) that
         * may be merged with other segments. Small values (e.g., less than 50 MB) are best for
         * interactive indexing, as this limits the length of pauses while indexing to a few seconds.
         * Larger values are best for batched indexing and speedier searches.
         *
         *
         * Note that [.setMaxMergeDocs] is also used to check whether a segment is too large for
         * merging (it's either or).
         */
        set(mb) {
            maxMergeSize = (mb * 1024 * 1024).toLong()
        }

    var maxMergeMBForForcedMerge: Double
        /**
         * Returns the largest segment (measured by total byte size of the segment's files, in MB) that
         * may be merged with other segments during forceMerge.
         *
         * @see .setMaxMergeMBForForcedMerge
         */
        get() = (maxMergeSizeForForcedMerge.toDouble()) / 1024 / 1024
        /**
         * Determines the largest segment (measured by total byte size of the segment's files, in MB) that
         * may be merged with other segments during forceMerge. Setting it low will leave the index with
         * more than 1 segment, even if [IndexWriter.forceMerge] is called.
         */
        set(mb) {
            maxMergeSizeForForcedMerge = (mb * 1024 * 1024).toLong()
        }

    var minMergeMB: Double
        /**
         * Get the minimum size for a segment to remain un-merged.
         *
         * @see .setMinMergeMB *
         */
        get() = (minMergeSize.toDouble()) / 1024 / 1024
        /**
         * Sets the minimum size for the lowest level segments. Any segments below this size are
         * candidates for full-flush merges and be merged more aggressively in order to avoid having a
         * long tail of small segments. Large values of this parameter increase the merging cost during
         * indexing if you flush small segments.
         */
        set(mb) {
            minMergeSize = (mb * 1024 * 1024).toLong()
        }

    companion object {
        /** Default minimum segment size. @see setMinMergeMB  */
        const val DEFAULT_MIN_MERGE_MB: Double = 16.0

        /**
         * Default maximum segment size. A segment of this size or larger will never be merged. @see
         * setMaxMergeMB
         */
        const val DEFAULT_MAX_MERGE_MB: Double = 2048.0

        /**
         * Default maximum segment size. A segment of this size or larger will never be merged during
         * forceMerge. @see setMaxMergeMBForForceMerge
         */
        const val DEFAULT_MAX_MERGE_MB_FOR_FORCED_MERGE: Double = Long.MAX_VALUE.toDouble()
    }
}
