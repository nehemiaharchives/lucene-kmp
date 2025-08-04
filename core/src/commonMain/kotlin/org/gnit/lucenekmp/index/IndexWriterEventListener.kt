package org.gnit.lucenekmp.index

/**
 * A callback event listener for recording key events happened inside IndexWriter
 *
 * @lucene.experimental
 */
interface IndexWriterEventListener {
    /**
     * Invoked at the start of merge on commit
     *
     * @param merge specification to be tracked
     */
    fun beginMergeOnFullFlush(merge: MergePolicy.MergeSpecification)

    /**
     * Invoked at the end of merge on commit, due to either merge completed, or merge timed out
     * according to [IndexWriterConfig.setMaxFullFlushMergeWaitMillis]
     *
     * @param merge specification to be tracked
     */
    fun endMergeOnFullFlush(merge: MergePolicy.MergeSpecification)

    companion object {
        /** A no-op listener that helps to save null checks  */
        val NO_OP_LISTENER: IndexWriterEventListener = object : IndexWriterEventListener {
            override fun beginMergeOnFullFlush(merge: MergePolicy.MergeSpecification) {}

            override fun endMergeOnFullFlush(merge: MergePolicy.MergeSpecification) {}
        }
    }
}
