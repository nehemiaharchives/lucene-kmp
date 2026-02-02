package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.index.DocumentsWriterFlushQueue.FlushTicket
import org.gnit.lucenekmp.index.DocumentsWriterPerThread.FlushedSegment
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.IOConsumer
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import okio.IOException
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.get
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * This class accepts multiple added documents and directly writes segment files.
 *
 *
 * Each added document is passed to the indexing chain, which in turn processes the document into
 * the different codec formats. Some formats write bytes to files immediately, e.g. stored fields
 * and term vectors, while others are buffered by the indexing chain and written only on flush.
 *
 *
 * Once we have used our allowed RAM buffer, or the number of added docs is large enough (in the
 * case we are flushing by doc count instead of RAM usage), we create a real segment and flush it to
 * the Directory.
 *
 *
 * Threads:
 *
 *
 * Multiple threads are allowed into addDocument at once. There is an initial synchronized call
 * to [DocumentsWriterFlushControl.obtainAndLock] which allocates a DWPT for this indexing
 * thread. The same thread will not necessarily get the same DWPT over time. Then updateDocuments is
 * called on that DWPT without synchronization (most of the "heavy lifting" is in this call). Once a
 * DWPT fills up enough RAM or hold enough documents in memory the DWPT is checked out for flush and
 * all changes are written to the directory. Each DWPT corresponds to one segment being written.
 *
 *
 * When flush is called by IndexWriter we check out all DWPTs that are associated with the
 * current [DocumentsWriterDeleteQueue] out of the [DocumentsWriterPerThreadPool] and
 * write them to disk. The flush process can piggyback on incoming indexing threads or even block
 * them from adding documents if flushing can't keep up with new documents being added. Unless the
 * stall control kicks in to block indexing threads flushes are happening concurrently to actual
 * index requests.
 *
 *
 * Exceptions:
 *
 *
 * Because this class directly updates in-memory posting lists, and flushes stored fields and
 * term vectors directly to files in the directory, there are certain limited times when an
 * exception can corrupt this state. For example, a disk full while flushing stored fields leaves
 * this file in a corrupt state. Or, an OOM exception while appending to the in-memory posting lists
 * can corrupt that posting list. We call such exceptions "aborting exceptions". In these cases we
 * must call abort() to discard all docs added since the last flush.
 *
 *
 * All other exceptions ("non-aborting exceptions") can still partially update the index
 * structures. These updates are consistent, but, they represent only a part of the document seen up
 * until the exception was hit. When this happens, we immediately mark the document as deleted so
 * that the document is always atomically ("all or none") added to the index.
 */
@OptIn(ExperimentalAtomicApi::class)
class DocumentsWriter @OptIn(ExperimentalAtomicApi::class) constructor(
    flushNotifications: FlushNotifications,
    indexCreatedVersionMajor: Int,
    pendingNumDocs: AtomicLong,
    enableTestPoints: Boolean,
    segmentNameSupplier: () -> String /*java.util.function.Supplier<String>*/,
    private val config: LiveIndexWriterConfig,
    directoryOrig: Directory,
    directory: Directory,
    globalFieldNumberMap: FieldInfos.FieldNumbers
) : AutoCloseable, Accountable {
    private val logger = KotlinLogging.logger {}
    @OptIn(ExperimentalAtomicApi::class)
    private val pendingNumDocs: AtomicLong

    private val flushNotifications: FlushNotifications

    @Volatile
    private var closed = false

    private val infoStream: InfoStream = config.infoStream

    @OptIn(ExperimentalAtomicApi::class)
    private val numDocsInRAM: AtomicInteger = AtomicInteger(0)

    // TODO: cut over to BytesRefHash in BufferedDeletes
    @Volatile
    var deleteQueue: DocumentsWriterDeleteQueue
    private val ticketQueue: DocumentsWriterFlushQueue = DocumentsWriterFlushQueue()

    /*
   * we preserve changes during a full flush since IW might not check out before
   * we release all changes. NRT Readers otherwise suddenly return true from
   * isCurrent while there are actually changes currently committed. See also
   * #anyChanges() & #flushAllThreads
   */
    @Volatile
    private var pendingChangesInCurrentFullFlush = false

    val perThreadPool: DocumentsWriterPerThreadPool
    val flushControl: DocumentsWriterFlushControl

    @Throws(IOException::class)
    fun deleteQueries(vararg queries: Query): Long {
        return applyDeleteOrUpdate { q: DocumentsWriterDeleteQueue ->
            q.addDelete(
                *queries
            )
        }
    }

    @Throws(IOException::class)
    fun deleteTerms(vararg terms: Term): Long {
        return applyDeleteOrUpdate { q: DocumentsWriterDeleteQueue ->
            q.addDelete(
                *terms
            )
        }
    }

    @Throws(IOException::class)
    fun updateDocValues(vararg updates: DocValuesUpdate): Long {
        return applyDeleteOrUpdate { q: DocumentsWriterDeleteQueue ->
            q.addDocValuesUpdates(
                *updates
            )
        }
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun applyDeleteOrUpdate(function: (DocumentsWriterDeleteQueue) -> Long): Long {
        // This method is synchronized to make sure we don't replace the deleteQueue while applying this
        // update / delete
        // otherwise we might lose an update / delete if this happens concurrently to a full flush.
        val deleteQueue: DocumentsWriterDeleteQueue = this.deleteQueue
        var seqNo: Long = function(deleteQueue)
        flushControl.doOnDelete()
        if (applyAllDeletes()) {
            seqNo = -seqNo
        }
        return seqNo
    }

    /** If buffered deletes are using too much heap, resolve them and write disk and return true.  */
    @Throws(IOException::class)
    private fun applyAllDeletes(): Boolean {
        val deleteQueue: DocumentsWriterDeleteQueue = this.deleteQueue

        // Check the applyAllDeletes flag first. This helps exit early most of the time without checking
        // isFullFlush(), which takes a lock and introduces contention on small documents that are quick
        // to index.
        if (flushControl.getApplyAllDeletes()
            && flushControl.isFullFlush == false // never apply deletes during full flush this breaks happens before relationship.
            && deleteQueue.isOpen // if it's closed then it's already fully applied and we have a new delete queue
            && flushControl.getAndResetApplyAllDeletes()
        ) {
            if (ticketQueue.addTicket { maybeFreezeGlobalBuffer(deleteQueue)!! } != null) {
                flushNotifications.onDeletesApplied() // apply deletes event forces a purge
                return true
            }
        }
        return false
    }

    @Throws(IOException::class)
    fun purgeFlushTickets(forced: Boolean, consumer: IOConsumer<FlushTicket>) {
        if (forced) {
            ticketQueue.forcePurge(consumer)
        } else {
            ticketQueue.tryPurge(consumer)
        }
    }

    val numDocs: Int
        /** Returns how many docs are currently buffered in RAM.  */
        get() = numDocsInRAM.get()

    @Throws(AlreadyClosedException::class)
    private fun ensureOpen() {
        if (closed) {
            throw AlreadyClosedException("this DocumentsWriter is closed")
        }
    }

    /**
     * Called if we hit an exception at a bad time (when updating the index files) and must discard
     * all currently buffered docs. This resets our state, discarding any docs added since last flush.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    suspend fun abort() {
        var success = false
        try {
            deleteQueue.clear()
            if (infoStream.isEnabled("DW")) {
                infoStream.message("DW", "abort")
            }
            for (perThread in perThreadPool.filterAndLock { `_`: DocumentsWriterPerThread -> true }) {
                try {
                    abortDocumentsWriterPerThread(perThread)
                } finally {
                    perThread.unlock()
                }
            }
            flushControl.abortPendingFlushes()
            flushControl.waitForFlush()
            assert(
                perThreadPool.size() == 0
            ) { "There are still active DWPT in the pool: " + perThreadPool.size() }
            success = true
        } finally {
            if (success) {
                assert(
                    flushControl.flushingBytes == 0L
                ) { "flushingBytes has unexpected value 0 != " + flushControl.flushingBytes }
                assert(
                    flushControl.netBytes() == 0L
                ) { "netBytes has unexpected value 0 != " + flushControl.netBytes() }
            }
            if (infoStream.isEnabled("DW")) {
                infoStream.message("DW", "done abort success=$success")
            }
        }
    }

    suspend fun flushOneDWPT(): Boolean {
        if (infoStream.isEnabled("DW")) {
            infoStream.message("DW", "startFlushOneDWPT")
        }
        if (!maybeFlush()) {
            val documentsWriterPerThread: DocumentsWriterPerThread? =
                flushControl.checkoutLargestNonPendingWriter()
            if (documentsWriterPerThread != null) {
                doFlush(documentsWriterPerThread)
                return true
            }
            return false
        }
        return true
    }

    /**
     * Locks all currently active DWPT and aborts them. The returned Closeable should be closed once
     * the locks for the aborted DWPTs can be released.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    suspend fun lockAndAbortAll(): AutoCloseable {
        if (infoStream.isEnabled("DW")) {
            infoStream.message("DW", "lockAndAbortAll")
        }
        // Make sure we move all pending tickets into the flush queue:
        ticketQueue.forcePurge { ticket: FlushTicket ->
            if (ticket.flushedSegment != null) {
                pendingNumDocs.addAndFetch(-ticket.flushedSegment!!.segmentInfo.info.maxDoc().toLong())
            }
        }
        val writers: MutableList<DocumentsWriterPerThread> = mutableListOf()
        val released = AtomicBoolean(false)
        val release =
            AutoCloseable {
                // we return this closure to unlock all writers once done
                // or if hit an exception below in the try block.
                // we can't assign this later otherwise the ref can't be final
                if (released.compareAndSet(false, newValue = true)) { // only once
                    if (infoStream.isEnabled("DW")) {
                        infoStream.message("DW", "unlockAllAbortedThread")
                    }
                    runBlocking{ perThreadPool.unlockNewWriters() }
                    for (writer in writers) {
                        writer.unlock()
                    }
                }
            }
        try {
            deleteQueue.clear()
            perThreadPool.lockNewWriters()
            writers.addAll(perThreadPool.filterAndLock { `_`: DocumentsWriterPerThread -> true })
            for (perThread in writers) {
                assert(perThread.isHeldByCurrentThread)
                abortDocumentsWriterPerThread(perThread)
            }
            deleteQueue.clear()

            // jump over any possible in flight ops:
            deleteQueue.skipSequenceNumbers((perThreadPool.size() + 1).toLong())

            flushControl.abortPendingFlushes()
            flushControl.waitForFlush()
            if (infoStream.isEnabled("DW")) {
                infoStream.message("DW", "finished lockAndAbortAll success=true")
            }
            return release
        } catch (t: Throwable) {
            if (infoStream.isEnabled("DW")) {
                infoStream.message("DW", "finished lockAndAbortAll success=false")
            }
            try {
                // if something happens here we unlock all states again
                release.close()
            } catch (t1: Throwable) {
                t.addSuppressed(t1)
            }
            throw t
        }
    }

    /** Returns how many documents were aborted.  */
    @Throws(IOException::class)
    private fun abortDocumentsWriterPerThread(perThread: DocumentsWriterPerThread) {
        assert(perThread.isHeldByCurrentThread)
        try {
            subtractFlushedNumDocs(perThread.numDocsInRAM)
            perThread.abort()
        } finally {
            flushControl.doOnAbort(perThread)
        }
    }

    val maxCompletedSequenceNumber: Long
        /** returns the maximum sequence number for all previously completed operations  */
        get() = deleteQueue.maxCompletedSeqNo

    fun anyChanges(): Boolean {
        /*
     * changes are either in a DWPT or in the deleteQueue.
     * yet if we currently flush deletes and / or dwpt there
     * could be a window where all changes are in the ticket queue
     * before they are published to the IW. ie we need to check if the
     * ticket queue has any tickets.
     */
        val anyChanges =
            numDocsInRAM.get() != 0 || anyDeletions()
                    || ticketQueue.hasTickets()
                    || pendingChangesInCurrentFullFlush
        if (infoStream.isEnabled("DW") && anyChanges) {
            infoStream.message(
                "DW",
                ("anyChanges numDocsInRam="
                        + numDocsInRAM.get()
                        + " deletes="
                        + anyDeletions()
                        + " hasTickets:"
                        + ticketQueue.hasTickets()
                        + " pendingChangesInFullFlush: "
                        + pendingChangesInCurrentFullFlush)
            )
        }
        return anyChanges
    }

    val bufferedDeleteTermsSize: Int
        get() = deleteQueue.bufferedUpdatesTermsSize

    fun anyDeletions(): Boolean {
        return deleteQueue.anyChanges()
    }

    override fun close() {
        closed = true
        IOUtils.close(flushControl, perThreadPool)
    }

    private suspend fun preUpdate(): Boolean {
        ensureOpen()
        var hasEvents = false
        while (flushControl.anyStalledThreads()
            || (config.checkPendingFlushOnUpdate && flushControl.numQueuedFlushes() > 0)
        ) {
            //logger.debug { "DW.preUpdate() stalled=${flushControl.anyStalledThreads()} queued=${flushControl.numQueuedFlushes()} flushing=${flushControl.numFlushingDWPT()}" }
            // Proactively flush to help un-stall. This will either pick a pending DWPT
            // or check out the largest non-pending DWPT and flush it.
            val flushed = flushOneDWPT()
            hasEvents = hasEvents || flushed
            if (!flushed) {
                // Nothing to flush right now; avoid busy spinning. Original Java blocks
                // up to 1s here; we yield cooperatively instead.
                //logger.debug { "DW.preUpdate() nothing to flush; yielding via waitIfStalled" }
                flushControl.waitIfStalled()
                break
            }
        }
        return hasEvents
    }

    private suspend fun postUpdate(
        flushingDWPT: DocumentsWriterPerThread?,
        hasEvents: Boolean
    ): Boolean {
        var hasEvents = hasEvents
        hasEvents = hasEvents or applyAllDeletes()
        if (flushingDWPT != null) {
            doFlush(flushingDWPT)
            hasEvents = true
        } else if (config.checkPendingFlushOnUpdate) {
            hasEvents = hasEvents or maybeFlush()
        }
        return hasEvents
    }

    suspend fun updateDocuments(
        docs: Iterable<Iterable<IndexableField>>,
        delNode: DocumentsWriterDeleteQueue.Node<*>?
    ): Long {
        val hasEvents = preUpdate()

        val dwpt: DocumentsWriterPerThread = flushControl.obtainAndLock()
        val flushingDWPT: DocumentsWriterPerThread?
        var seqNo: Long

        try {
            // This must happen after we've pulled the DWPT because IW.close
            // waits for all DWPT to be released:
            ensureOpen()
            try {
                seqNo =
                    dwpt.updateDocuments(
                        docs,
                        delNode,
                        flushNotifications
                    ) { numDocsInRAM.incrementAndFetch() }
            } finally {
                if (dwpt.isAborted) {
                    flushControl.doOnAbort(dwpt)
                }
            }
            flushingDWPT = flushControl.doAfterDocument(dwpt)
        } finally {
            // If a flush is occurring, we don't want to allow this dwpt to be reused
            // If it is aborted, we shouldn't allow it to be reused
            // If the deleteQueue is advanced, this means the maximum seqNo has been set and it cannot be
            // reused

            // TODO Synchronized is not supported in KMP, need to think what to do here
            //synchronized(flushControl) {
            if (dwpt.isFlushPending() || dwpt.isAborted || dwpt.isQueueAdvanced) {
                dwpt.unlock()
            } else {
                perThreadPool.marksAsFreeAndUnlock(dwpt)
            }
            //}
            assert(!dwpt.isHeldByCurrentThread) { "we didn't release the dwpt even on abort" }
        }

        if (postUpdate(flushingDWPT, hasEvents)) {
            seqNo = -seqNo
        }
        return seqNo
    }

    private suspend fun maybeFlush(): Boolean {
        //logger.debug { "DW.maybeFlush() called: queued=${flushControl.numQueuedFlushes()} fullFlush=${flushControl.isFullFlush}" }
        val flushingDWPT: DocumentsWriterPerThread? = flushControl.nextPendingFlush()
        if (flushingDWPT != null) {
            //logger.debug { "DW.maybeFlush() got DWPT seg=${flushingDWPT.getSegmentInfo().name} docsInRAM=${flushingDWPT.numDocsInRAM}" }
            doFlush(flushingDWPT)
            return true
        }
        //logger.debug { "DW.maybeFlush() nothing to flush" }
        return false
    }

    private suspend fun doFlush(flushingDWPT: DocumentsWriterPerThread) {
        var flushingDWPT: DocumentsWriterPerThread = flushingDWPT
        checkNotNull(flushingDWPT) { "Flushing DWPT must not be null" }
        //logger.debug { "DW.doFlush() start seg=${flushingDWPT.getSegmentInfo().name} docsInRAM=${flushingDWPT.numDocsInRAM}" }
        do {
            assert(!flushingDWPT.hasFlushed())
            var success = false
            var ticket: FlushTicket? = null
            try {
                assert(
                    currentFullFlushDelQueue == null
                            || flushingDWPT.deleteQueue == currentFullFlushDelQueue
                ) {
                    ("expected: "
                            + currentFullFlushDelQueue
                            + " but was: "
                            + flushingDWPT.deleteQueue
                            + " "
                            + flushControl.isFullFlush)
                }
                /*
         * Since with DWPT the flush process is concurrent and several DWPT
         * could flush at the same time we must maintain the order of the
         * flushes before we can apply the flushed segment and the frozen global
         * deletes it is buffering. The reason for this is that the global
         * deletes mark a certain point in time where we took a DWPT out of
         * rotation and freeze the global deletes.
         *
         * Example: A flush 'A' starts and freezes the global deletes, then
         * flush 'B' starts and freezes all deletes occurred since 'A' has
         * started. if 'B' finishes before 'A' we need to wait until 'A' is done
         * otherwise the deletes frozen by 'B' are not applied to 'A' and we
         * might miss to delete documents in 'A'.
         */
                try {
                    assert(assertTicketQueueModification(flushingDWPT.deleteQueue))
                    val dwpt: DocumentsWriterPerThread = flushingDWPT
                    // Each flush is assigned a ticket in the order they acquire the ticketQueue lock
                    ticket =
                        ticketQueue.addTicket { FlushTicket(dwpt.prepareFlush(), true) }
                    val flushingDocsInRam: Int = flushingDWPT.numDocsInRAM
                    var dwptSuccess = false
                    try {
                        // flush concurrently without locking
                        val newSegment: FlushedSegment = flushingDWPT.flush(flushNotifications)!!
                        ticketQueue.addSegment(ticket, newSegment)
                        dwptSuccess = true
                        //logger.debug { "DW.doFlush() flushed seg=${newSegment.segmentInfo.info.name} delCount=${newSegment.delCount}" }
                    } finally {
                        subtractFlushedNumDocs(flushingDocsInRam)
                        if (!flushingDWPT.pendingFilesToDelete().isEmpty()) {
                            val files: MutableSet<String> = flushingDWPT.pendingFilesToDelete()
                            flushNotifications.deleteUnusedFiles(files)
                        }
                        if (!dwptSuccess) {
                            flushNotifications.flushFailed(flushingDWPT.getSegmentInfo())
                        }
                    }
                    // flush was successful once we reached this point - new seg. has been assigned to the
                    // ticket!
                    success = true
                } finally {
                    if (!success && ticket != null) {
                        // In the case of a failure make sure we are making progress and
                        // apply all the deletes since the segment flush failed since the flush
                        // ticket could hold global deletes see FlushTicket#canPublish()
                        ticketQueue.markTicketFailed(ticket)
                    }
                }
                /*
                 * Now we are done and try to flush the ticket queue if the head of the
                 * queue has already finished the flush.
                 */
                if (ticketQueue.getTicketCount() >= perThreadPool.size()) {
                    // This means there is a backlog: the one
                    // thread in innerPurge can't keep up with all
                    // other threads flushing segments.  In this case
                    // we forcefully stall the producers.
                    flushNotifications.onTicketBacklog()
                }
            } finally {
                flushControl.doAfterFlush(flushingDWPT)
                //logger.debug { "DW.doFlush() after doAfterFlush seg=${flushingDWPT.getSegmentInfo().name} queued=${flushControl.numQueuedFlushes()} flushing=${flushControl.numFlushingDWPT()}" }
            }
            // poll next pending flush safely
            val next: DocumentsWriterPerThread? = flushControl.nextPendingFlush()
            if (next != null) {
                flushingDWPT = next
                //logger.debug { "DW.doFlush() chaining to next seg=${flushingDWPT.getSegmentInfo().name}" }
            } else {
                break
            }
        } while (true)
        flushNotifications.afterSegmentsFlushed()
        //logger.debug { "DW.doFlush() end" }
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@get:Synchronized*/
    val nextSequenceNumber: Long
        get() =// this must be synced otherwise the delete queue might change concurrently
            deleteQueue.nextSequenceNumber

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun resetDeleteQueue(maxNumPendingOps: Int): Long {
        val newQueue: DocumentsWriterDeleteQueue = deleteQueue.advanceQueue(maxNumPendingOps)
        assert(deleteQueue.isAdvanced)
        assert(!newQueue.isAdvanced)
        assert(deleteQueue.lastSequenceNumber <= newQueue.lastSequenceNumber)
        assert(
            deleteQueue.maxSeqNo <= newQueue.lastSequenceNumber
        ) { "maxSeqNo: " + deleteQueue.maxSeqNo + " vs. " + newQueue.lastSequenceNumber }
        val oldMaxSeqNo: Long = deleteQueue.maxSeqNo
        deleteQueue = newQueue
        return oldMaxSeqNo
    }

    interface FlushNotifications {
        // TODO maybe we find a better name for this
        /**
         * Called when files were written to disk that are not used anymore. It's the implementation's
         * responsibility to clean these files up
         */
        fun deleteUnusedFiles(files: MutableCollection<String>)

        /** Called when a segment failed to flush.  */
        fun flushFailed(info: SegmentInfo)

        /** Called after one or more segments were flushed to disk.  */
        @Throws(IOException::class)
        fun afterSegmentsFlushed()

        /**
         * Should be called if a flush or an indexing operation caused a tragic / unrecoverable event.
         */
        fun onTragicEvent(event: Throwable, message: String)

        /** Called once deletes have been applied either after a flush or on a deletes call  */
        fun onDeletesApplied()

        /**
         * Called once the DocumentsWriter ticket queue has a backlog. This means there is an inner
         * thread that tries to publish flushed segments but can't keep up with the other threads
         * flushing new segments. This likely requires other thread to forcefully purge the buffer to
         * help publishing. This can't be done in-place since we might hold index writer locks when this
         * is called. The caller must ensure that the purge happens without an index writer lock being
         * held.
         *
         * @see DocumentsWriter.purgeFlushTickets
         */
        fun onTicketBacklog()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun subtractFlushedNumDocs(numFlushed: Int) {
        var oldValue: Int = numDocsInRAM.get()
        while (!numDocsInRAM.compareAndSet(oldValue, oldValue - numFlushed)) {
            oldValue = numDocsInRAM.get()
        }
        assert(numDocsInRAM.get() >= 0)
    }

    // for asserts
    @Volatile
    private var currentFullFlushDelQueue: DocumentsWriterDeleteQueue? = null

    init {
        this.deleteQueue = DocumentsWriterDeleteQueue(infoStream)
        this.perThreadPool =
            DocumentsWriterPerThreadPool {
                val infos: FieldInfos.Builder =
                    FieldInfos.Builder(globalFieldNumberMap)
                DocumentsWriterPerThread(
                    indexCreatedVersionMajor,
                    segmentNameSupplier(),
                    directoryOrig,
                    directory,
                    config,
                    deleteQueue,
                    infos,
                    pendingNumDocs,
                    enableTestPoints
                )
            }
        this.pendingNumDocs = pendingNumDocs
        flushControl = DocumentsWriterFlushControl(this, config)
        this.flushNotifications = flushNotifications
    }

    // for asserts
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun setFlushingDeleteQueue(session: DocumentsWriterDeleteQueue?): Boolean {
        assert(
            currentFullFlushDelQueue == null || !currentFullFlushDelQueue!!.isOpen
        ) { "Can not replace a full flush queue if the queue is not closed" }
        currentFullFlushDelQueue = session
        return true
    }

    private fun assertTicketQueueModification(deleteQueue: DocumentsWriterDeleteQueue): Boolean {
        // assign it then we don't need to sync on DW
        val currentFullFlushDelQueue: DocumentsWriterDeleteQueue? = this.currentFullFlushDelQueue
        assert(
            currentFullFlushDelQueue == null || currentFullFlushDelQueue == deleteQueue
        ) { "only modifications from the current flushing queue are permitted while doing a full flush" }
        return true
    }

    /*
     * FlushAllThreads is synced by IW fullFlushLock. Flushing all threads is a
     * two stage operation; the caller must ensure (in try/finally) that finishFlush
     * is called after this method, to release the flush lock in DWFlushControl
     */
    suspend fun flushAllThreads(): Long {
        if (infoStream.isEnabled("DW")) {
            infoStream.message("DW", "startFullFlush")
        }
        //logger.debug { "DW.flushAllThreads() enter" }

        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
        pendingChangesInCurrentFullFlush = anyChanges()
        val flushingDeleteQueue: DocumentsWriterDeleteQueue = deleteQueue
        /* Cutover to a new delete queue.  This must be synced on the flush control
         * otherwise a new DWPT could sneak into the loop with an already flushing
         * delete queue */
        val seqNo: Long = flushControl.markForFullFlush() // swaps this.deleteQueue synced on FlushControl
        assert(setFlushingDeleteQueue(flushingDeleteQueue))
        //}
        checkNotNull(currentFullFlushDelQueue)
        assert(currentFullFlushDelQueue != deleteQueue)

        var anythingFlushed = false
        try {
            // Keep flushing pending DWPTs until none are left. This avoids relying on
            // other threads to help out and prevents waitForFlush() from spinning.
            do {
                val queued = flushControl.numQueuedFlushes()
                val blocked = flushControl.numBlockedFlushes()
                val flushing = flushControl.numFlushingDWPT()
                //logger.debug { "DW.flushAllThreads() loop: queued=$queued blocked=$blocked flushing=$flushing fullFlush=${flushControl.isFullFlush}" }
                val flushedNow = maybeFlush()
                //logger.debug { "DW.flushAllThreads() maybeFlush -> $flushedNow" }
                anythingFlushed = anythingFlushed or flushedNow
            } while (flushControl.numQueuedFlushes() > 0)

            //logger.debug { "DW.flushAllThreads() waiting for inflight flushes... flushing=${flushControl.numFlushingDWPT()}" }
            // If a concurrent flush is still in flight wait for it
            flushControl.waitForFlush()
            //logger.debug { "DW.flushAllThreads() inflight flushes done. queued=${flushControl.numQueuedFlushes()} flushing=${flushControl.numFlushingDWPT()}" }
            if (!anythingFlushed && flushingDeleteQueue.anyChanges()
            ) { // apply deletes if we did not flush any document
                if (infoStream.isEnabled("DW")) {
                    infoStream.message(
                        // TODO Thread is not supported in KMP, need to think what to do here
                        "DW", /*java.lang.Thread.currentThread().getName() + */": flush naked frozen global deletes"
                    )
                }
                //logger.debug { "DW.flushAllThreads() adding ticket to freeze global deletes" }
                assert(assertTicketQueueModification(flushingDeleteQueue))
                ticketQueue.addTicket { maybeFreezeGlobalBuffer(flushingDeleteQueue)!! }
            }
            // we can't assert that we don't have any tickets in the queue since we might add a
            // DocumentsWriterDeleteQueue
            // concurrently if we have very small ram buffers this happens quite frequently
            assert(!flushingDeleteQueue.anyChanges())
        } finally {
            //logger.debug { "DW.flushAllThreads() finally: closing currentFullFlushDelQueue" }
            assert(flushingDeleteQueue == currentFullFlushDelQueue)
            flushingDeleteQueue
                .close() // all DWPT have been processed and this queue has been fully flushed to the
            // ticket-queue
        }
        val result = if (anythingFlushed) -seqNo else seqNo
        //logger.debug { "DW.flushAllThreads() exit: resultSeq=$result" }
        return result
    }

    private fun maybeFreezeGlobalBuffer(
        deleteQueue: DocumentsWriterDeleteQueue
    ): FlushTicket? {
        val frozenBufferedUpdates: FrozenBufferedUpdates? =
            deleteQueue.maybeFreezeGlobalBuffer()
        if (frozenBufferedUpdates != null) {
            // no need to publish anything if we don't have any frozen updates
            return FlushTicket(frozenBufferedUpdates, false)
        }
        return null
    }

    @Throws(IOException::class)
    fun finishFullFlush(success: Boolean) {
        try {
            if (infoStream.isEnabled("DW")) {
                infoStream.message(
                    // TODO Thread is not supported in KMP, need to think what to do here
                    "DW", /*java.lang.Thread.currentThread().getName() + */" finishFullFlush success=$success"
                )
            }
            //logger.debug { "DW.finishFullFlush(): start success=$success" }
            assert(setFlushingDeleteQueue(null))
            if (success) {
                // Release the flush lock
                flushControl.finishFullFlush()
            } else {
                flushControl.abortFullFlushes()
            }
        } finally {
            pendingChangesInCurrentFullFlush = false
            //logger.debug { "DW.finishFullFlush(): applyAllDeletes start" }
            applyAllDeletes() // make sure we do execute this since we block applying deletes during full
            // flush
            //logger.debug { "DW.finishFullFlush(): applyAllDeletes done" }
        }
    }

    override fun ramBytesUsed(): Long {
        return flushControl.ramBytesUsed()
    }

    val flushingBytes: Long
        /**
         * Returns the number of bytes currently being flushed
         *
         *
         * This is a subset of the value returned by [.ramBytesUsed]
         */
        get() = flushControl.flushingBytes
}
