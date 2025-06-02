package org.gnit.lucenekmp.index

/**
 * MergeTrigger is passed to [MergePolicy.findMerges] to indicate the event that triggered the merge.
 */
enum class MergeTrigger {
    /** Merge was triggered by a segment flush.  */
    SEGMENT_FLUSH,

    /**
     * Merge was triggered by a full flush. Full flushes can be caused by a commit, NRT reader reopen
     * or a close call on the index writer.
     */
    FULL_FLUSH,

    /** Merge has been triggered explicitly by the user.  */
    EXPLICIT,

    /** Merge was triggered by a successfully finished merge.  */
    MERGE_FINISHED,

    /** Merge was triggered by a closing IndexWriter.  */
    CLOSING,

    /** Merge was triggered on commit.  */
    COMMIT,

    /** Merge was triggered on opening NRT readers.  */
    GET_READER,

    /** Merge was triggered by an [IndexWriter.addIndexes] operation  */
    ADD_INDEXES,
}
