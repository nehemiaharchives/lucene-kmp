package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.InfoStream

/**
 * [FlushPolicy] controls when segments are flushed from a RAM resident internal
 * data-structure to the [IndexWriter]s [Directory].
 *
 *
 * Segments are traditionally flushed by:
 *
 *
 *  * RAM consumption - configured via [IndexWriterConfig.setRAMBufferSizeMB]
 *  * Number of RAM resident documents - configured via [       ][IndexWriterConfig.setMaxBufferedDocs]
 *
 *
 *
 * [IndexWriter] consults the provided [FlushPolicy] to control the flushing process.
 * The policy is informed for each added or updated document as well as for each delete term. Based
 * on the [FlushPolicy], the information provided via [DocumentsWriterPerThread] and
 * [DocumentsWriterFlushControl], the [FlushPolicy] decides if a [ ] needs flushing and mark it as flush-pending via [ ][DocumentsWriterFlushControl.setFlushPending], or if deletes need to be applied.
 *
 * @see DocumentsWriterFlushControl
 *
 * @see DocumentsWriterPerThread
 *
 * @see IndexWriterConfig.setFlushPolicy
 */
abstract class FlushPolicy {
    protected var indexWriterConfig: LiveIndexWriterConfig? = null
    protected var infoStream: InfoStream? = null

    /**
     * Called for each delete, insert or update. For pure deletes, the given [ ] may be `null`.
     *
     *
     * Note: This method is called synchronized on the given [DocumentsWriterFlushControl]
     * and it is guaranteed that the calling thread holds the lock on the given [ ]
     */
    abstract fun onChange(
        control: DocumentsWriterFlushControl, perThread: DocumentsWriterPerThread?
    )

    /** Called by DocumentsWriter to initialize the FlushPolicy  */
    // TODO Synchronized is not supported in Kotlin Multiplatform, need to think what to do here
    /*@Synchronized*/
    fun init(indexWriterConfig: LiveIndexWriterConfig) {
        this.indexWriterConfig = indexWriterConfig
        infoStream = indexWriterConfig.infoStream
    }

    /**
     * Returns the current most RAM consuming non-pending [DocumentsWriterPerThread] with at
     * least one indexed document.
     *
     *
     * This method will never return `null`
     */
    protected fun findLargestNonPendingWriter(
        control: DocumentsWriterFlushControl, perThread: DocumentsWriterPerThread
    ): DocumentsWriterPerThread {
        assert(perThread.numDocsInRAM > 0)
        // the dwpt which needs to be flushed eventually
        val maxRamUsingWriter: DocumentsWriterPerThread = control.findLargestNonPendingWriter() ?: perThread
        assert(assertMessage("set largest ram consuming thread pending on lower watermark"))
        return maxRamUsingWriter
    }

    private fun assertMessage(s: String): Boolean {
        if (infoStream!!.isEnabled("FP")) {
            infoStream!!.message("FP", s)
        }
        return true
    }
}
