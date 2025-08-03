package org.gnit.lucenekmp.index

/**
 * Default [FlushPolicy] implementation that flushes new segments based on RAM used and
 * document count depending on the IndexWriter's [IndexWriterConfig]. It also applies pending
 * deletes based on the number of buffered delete terms.
 *
 *
 * All [IndexWriterConfig] settings are used to mark [DocumentsWriterPerThread] as
 * flush pending during indexing with respect to their live updates.
 *
 *
 * If [IndexWriterConfig.setRAMBufferSizeMB] is enabled, the largest ram consuming
 * [DocumentsWriterPerThread] will be marked as pending iff the global active RAM consumption
 * is `>=` the configured max RAM buffer.
 */
internal open class FlushByRamOrCountsPolicy : FlushPolicy() {
    override fun onChange(
        control: DocumentsWriterFlushControl,
        perThread: DocumentsWriterPerThread?
    ) {
        if (perThread != null && flushOnDocCount()
            && perThread.numDocsInRAM >= indexWriterConfig!!.getMaxBufferedDocs()
        ) {
            // Flush this state by num docs
            control.setFlushPending(perThread)
        } else if (flushOnRAM()) { // flush by RAM
            val limit = (indexWriterConfig!!.getRAMBufferSizeMB() * 1024.0 * 1024.0).toLong()
            val activeRam: Long = control.activeBytes()
            val deletesRam: Long = control.deleteBytesUsed
            if (deletesRam >= limit && activeRam >= limit && perThread != null) {
                flushDeletes(control)
                flushActiveBytes(control, perThread)
            } else if (deletesRam >= limit) {
                flushDeletes(control)
            } else if (activeRam + deletesRam >= limit && perThread != null) {
                flushActiveBytes(control, perThread)
            }
        }
    }

    private fun flushDeletes(control: DocumentsWriterFlushControl) {
        control.setApplyAllDeletes()
        if (infoStream!!.isEnabled("FP")) {
            infoStream!!.message(
                "FP",
                ("force apply deletes bytesUsed="
                        + control.deleteBytesUsed
                        + " vs ramBufferMB="
                        + indexWriterConfig!!.getRAMBufferSizeMB())
            )
        }
    }

    private fun flushActiveBytes(
        control: DocumentsWriterFlushControl,
        perThread: DocumentsWriterPerThread
    ) {
        if (infoStream!!.isEnabled("FP")) {
            infoStream!!.message(
                "FP",
                ("trigger flush: activeBytes="
                        + control.activeBytes()
                        + " deleteBytes="
                        + control.deleteBytesUsed
                        + " vs ramBufferMB="
                        + indexWriterConfig!!.getRAMBufferSizeMB())
            )
        }
        markLargestWriterPending(control, perThread)
    }

    /** Marks the most ram consuming active [DocumentsWriterPerThread] flush pending  */
    protected fun markLargestWriterPending(
        control: DocumentsWriterFlushControl,
        perThread: DocumentsWriterPerThread
    ) {
        val largestNonPendingWriter: DocumentsWriterPerThread =
            findLargestNonPendingWriter(control, perThread)
        if (largestNonPendingWriter != null) {
            control.setFlushPending(largestNonPendingWriter)
        }
    }

    /**
     * Returns `true` if this [FlushPolicy] flushes on [ ][IndexWriterConfig.getMaxBufferedDocs], otherwise `false`.
     */
    protected fun flushOnDocCount(): Boolean {
        return indexWriterConfig!!.getMaxBufferedDocs() != IndexWriterConfig.DISABLE_AUTO_FLUSH
    }

    /**
     * Returns `true` if this [FlushPolicy] flushes on [ ][IndexWriterConfig.getRAMBufferSizeMB], otherwise `false`.
     */
    protected fun flushOnRAM(): Boolean {
        return indexWriterConfig!!.getRAMBufferSizeMB() != IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble()
    }
}
