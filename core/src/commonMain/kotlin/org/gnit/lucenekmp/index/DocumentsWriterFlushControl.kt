package org.gnit.lucenekmp.index

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.poll
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * This class controls [DocumentsWriterPerThread] flushing during indexing. It tracks the
 * memory consumption per [DocumentsWriterPerThread] and uses a configured [FlushPolicy]
 * to decide if a [DocumentsWriterPerThread] must flush.
 *
 *
 * In addition to the [FlushPolicy] the flush control might set certain [ ] as flush pending iff a [DocumentsWriterPerThread] exceeds the
 * [IndexWriterConfig.rAMPerThreadHardLimitMB] to prevent address space exhaustion.
 */
class DocumentsWriterFlushControl(
    private val documentsWriter: DocumentsWriter,
    private val config: LiveIndexWriterConfig
) : Accountable, AutoCloseable {

    private val logger = KotlinLogging.logger {}

    private val hardMaxBytesPerDWPT: Long = config.rAMPerThreadHardLimitMB * 1024L * 1024L
    private var activeBytes: Long = 0

    @Volatile
    var flushingBytes: Long = 0
        private set

    @Volatile
    private var numPending = 0
    private var numDocsSinceStalled = 0 // only with assert
    @OptIn(ExperimentalAtomicApi::class)
    private val flushDeletes: AtomicBoolean = AtomicBoolean(false)

    /** Returns `true` if a full flush is currently running  */
    // TODO Synchronized is not supported in Kotlin Multiplatform, need to think what to do here
    /*@get:Synchronized*/
    var isFullFlush: Boolean = false
        private set

    // only for assertion that we don't get stale DWPTs from the pool
    private var fullFlushMarkDone = false

    // The flushQueue is used to concurrently distribute DWPTs that are ready to be flushed ie. when a
    // full flush is in
    // progress. This might be triggered by a commit or NRT refresh. The trigger will only walk all
    // eligible DWPTs and
    // mark them as flushable putting them in the flushQueue ready for other threads (ie. indexing
    // threads) to help flushing
    private val flushQueue: /*Queue*/ ArrayDeque<DocumentsWriterPerThread> = ArrayDeque() /*LinkedList<DocumentsWriterPerThread>()*/

    // only for safety reasons if a DWPT is close to the RAM limit
    private val blockedFlushes: /*Queue*/ ArrayDeque<DocumentsWriterPerThread> = ArrayDeque() /*LinkedList<DocumentsWriterPerThread>()*/

    // flushingWriters holds all currently flushing writers. There might be writers in this list that
    // are also in the flushQueue which means that writers in the flushingWriters list are not
    // necessarily
    // already actively flushing. They are only in the state of flushing and might be picked up in the
    // future by
    // polling the flushQueue
    private val flushingWriters: MutableList<DocumentsWriterPerThread> = mutableListOf()

    private var maxConfiguredRamBuffer = 0.0
    var peakActiveBytes: Long = 0 // only with assert
        private set
    private var peakFlushBytes: Long = 0 // only with assert
    var peakNetBytes: Long = 0 // only with assert
        private set
    private var peakDelta: Long = 0 // only with assert
    private var flushByRAMWasDisabled = false // only with assert
    val stallControl: DocumentsWriterStallControl = DocumentsWriterStallControl()
    private val perThreadPool: DocumentsWriterPerThreadPool = documentsWriter.perThreadPool
    private val flushPolicy: FlushPolicy = config.flushPolicy
    private var closed = false
    private val infoStream: InfoStream = config.infoStream

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun activeBytes(): Long {
        return activeBytes
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun netBytes(): Long {
        return this.flushingBytes + activeBytes
    }

    private fun stallLimitBytes(): Long {
        val maxRamMB: Double = config.rAMBufferSizeMB
        return if (maxRamMB != IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble()) (2 * (maxRamMB * 1024 * 1024)).toLong() else Long.Companion.MAX_VALUE
    }

    private fun assertMemory(): Boolean {
        val maxRamMB: Double = config.rAMBufferSizeMB
        // We can only assert if we have always been flushing by RAM usage; otherwise the assert will
        // false trip if e.g. the
        // flush-by-doc-count * doc size was large enough to use far more RAM than the sudden change to
        // IWC's maxRAMBufferSizeMB:
        if (maxRamMB != IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble() && !flushByRAMWasDisabled) {
            // for this assert we must be tolerant to ram buffer changes!
            maxConfiguredRamBuffer = max(maxRamMB, maxConfiguredRamBuffer)
            val ram = this.flushingBytes + activeBytes
            val ramBufferBytes = (maxConfiguredRamBuffer * 1024 * 1024).toLong()

            // take peakDelta into account - worst case is that all flushing, pending and blocked DWPT had
            // maxMem and the last doc had the peakDelta

            // 2 * ramBufferBytes -> before we stall we need to cross the 2xRAM Buffer border this is
            // still a valid limit
            // (numPending + numFlushingDWPT() + numBlockedFlushes()) * peakDelta) -> those are the total
            // number of DWPT that are not active but not yet fully flushed
            // all of them could theoretically be taken out of the loop once they crossed the RAM buffer
            // and the last document was the peak delta
            // (numDocsSinceStalled * peakDelta) -> at any given time there could be n threads in flight
            // that crossed the stall control before we reached the limit and each of them could hold a
            // peak document
            val expected =
                ((2 * ramBufferBytes)
                        + ((numPending + numFlushingDWPT() + numBlockedFlushes()) * peakDelta)
                        + (numDocsSinceStalled * peakDelta))
            // the expected ram consumption is an upper bound at this point and not really the expected
            // consumption
            if (peakDelta < (ramBufferBytes shr 1)) {
                /*
         * if we are indexing with very low maxRamBuffer like 0.1MB memory can
         * easily overflow if we check out some DWPT based on docCount and have
         * several DWPT in flight indexing large documents (compared to the ram
         * buffer). This means that those DWPT and their threads will not hit
         * the stall control before asserting the memory which would in turn
         * fail. To prevent this we only assert if the largest document seen
         * is smaller than the 1/2 of the maxRamBufferMB
         */
                assert(
                    ram <= expected
                ) {
                    ("actual mem: "
                            + ram
                            + " byte, expected mem: "
                            + expected
                            + " byte, flush mem: "
                            + this.flushingBytes
                            + ", active mem: "
                            + activeBytes
                            + ", pending DWPT: "
                            + numPending
                            + ", flushing DWPT: "
                            + numFlushingDWPT()
                            + ", blocked DWPT: "
                            + numBlockedFlushes()
                            + ", peakDelta mem: "
                            + peakDelta
                            + " bytes, ramBufferBytes="
                            + ramBufferBytes
                            + ", maxConfiguredRamBuffer="
                            + maxConfiguredRamBuffer)
                }
            }
        } else {
            flushByRAMWasDisabled = true
        }
        return true
    }

    // only for asserts
    private fun updatePeaks(delta: Long): Boolean {
        peakActiveBytes = max(peakActiveBytes, activeBytes)
        peakFlushBytes = max(peakFlushBytes, this.flushingBytes)
        peakNetBytes = max(peakNetBytes, netBytes())
        peakDelta = max(peakDelta, delta)

        return true
    }

    /**
     * Return the smallest number of bytes that we would like to make sure to not miss from the global
     * RAM accounting.
     */
    private fun ramBufferGranularity(): Long {
        var ramBufferSizeMB: Double = config.rAMBufferSizeMB
        if (ramBufferSizeMB == IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble()) {
            ramBufferSizeMB = config.rAMPerThreadHardLimitMB.toDouble()
        }
        // No more than ~0.1% of the RAM buffer size.
        var granularity = (ramBufferSizeMB * 1024.0).toLong()
        // Or 16kB, so that with e.g. 64 active DWPTs, we'd never be missing more than 64*16kB = 1MB in
        // the global RAM buffer accounting.
        granularity = min(granularity, 16 * 1024L)
        return granularity
    }

    fun doAfterDocument(perThread: DocumentsWriterPerThread): DocumentsWriterPerThread? {
        val delta: Long = perThread.commitLastBytesUsedDelta
        // in order to prevent contention in the case of many threads indexing small documents
        // we skip ram accounting unless the DWPT accumulated enough ram to be worthwhile
        if (config.maxBufferedDocs == IndexWriterConfig.DISABLE_AUTO_FLUSH
            && delta < ramBufferGranularity()
        ) {
            // Skip accounting for now, we'll come back to it later when the delta is bigger
            return null
        }

        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
            // we need to commit this under lock but calculate it outside of the lock to minimize the time
            // this lock is held
            // per document. The reason we update this under lock is that we mark DWPTs as pending without
            // acquiring its
            // lock in #setFlushPending and this also reads the committed bytes and modifies the
            // flush/activeBytes.
            // In the future we can clean this up to be more intuitive.
            perThread.commitLastBytesUsed(delta)
            try {
                /*
         * We need to differentiate here if we are pending since setFlushPending
         * moves the perThread memory to the flushBytes and we could be set to
         * pending during a delete
         */
                if (perThread.isFlushPending()) {
                    this.flushingBytes += delta
                    assert(updatePeaks(delta))
                } else {
                    activeBytes += delta
                    assert(updatePeaks(delta))
                    flushPolicy.onChange(this, perThread)
                    if (!perThread.isFlushPending() && perThread.ramBytesUsed() > hardMaxBytesPerDWPT) {
                        // Safety check to prevent a single DWPT exceeding its RAM limit. This
                        // is super important since we can not address more than 2048 MB per DWPT
                        setFlushPending(perThread)
                    }
                }
                return checkout(perThread, false)
            } finally {
                val stalled = updateStallState()
                assert(assertNumDocsSinceStalled(stalled) && assertMemory())
            }
        //}
    }

    private fun checkout(
        perThread: DocumentsWriterPerThread, markPending: Boolean
    ): DocumentsWriterPerThread? {

        // TODO Thread is not supported in KMP, need to think what to do here
        /*assert(java.lang.Thread.holdsLock(this))*/
        if (this.isFullFlush) {
            if (perThread.isFlushPending()) {
                checkoutAndBlock(perThread)
                return nextPendingFlush()
            }
        } else {
            if (markPending) {
                assert(!perThread.isFlushPending())
                setFlushPending(perThread)
            }

            if (perThread.isFlushPending()) {
                return checkOutForFlush(perThread)
            }
        }
        return null
    }

    private fun assertNumDocsSinceStalled(stalled: Boolean): Boolean {
        /*
     *  updates the number of documents "finished" while we are in a stalled state.
     *  this is important for asserting memory upper bounds since it corresponds
     *  to the number of threads that are in-flight and crossed the stall control
     *  check before we actually stalled.
     *  see #assertMemory()
     */
        if (stalled) {
            numDocsSinceStalled++
        } else {
            numDocsSinceStalled = 0
        }
        return true
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun doAfterFlush(dwpt: DocumentsWriterPerThread) {
        assert(flushingWriters.contains(dwpt))
        try {
            flushingWriters.remove(dwpt)
            this.flushingBytes -= dwpt.lastCommittedBytesUsed
            logger.debug { "DWFC.doAfterFlush() seg=${dwpt.getSegmentInfo().name} flushingWriters=${flushingWriters.size} flushingBytes=$flushingBytes" }
            assert(assertMemory())
        } finally {
            try {
                updateStallState()
            } finally {
                // TODO notifyAll is not supported in KMP, need to think what to do here
                /*(this as java.lang.Object).notifyAll()*/
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private lateinit var stallStartNS: Instant

    @OptIn(ExperimentalTime::class)
    private fun updateStallState(): Boolean {

        // TODO Thread is not supported in KMP, need to think what to do here
        /*assert(java.lang.Thread.holdsLock(this))*/
        val limit = stallLimitBytes()
        /*
         * we block indexing threads if net byte grows due to slow flushes
         * yet, for small ram buffers and large documents we can easily
         * reach the limit without any ongoing flushes. we need to ensure
         * that we don't stall/block if an ongoing or pending flush can
         * not free up enough memory to release the stall lock.
         */
        val stall = (activeBytes + this.flushingBytes) > limit && activeBytes < limit && !closed

        if (infoStream.isEnabled("DWFC")) {
            if (stall != stallControl.anyStalledThreads()) {
                if (stall) {
                    infoStream.message(
                        "DW",
                        "now stalling flushes: netBytes: ${netBytes() / 1024.0 / 1024.0} MB flushBytes: ${this.flushingBytes / 1024.0 / 1024.0} MB fullFlush: ${this.isFullFlush}"
                    )
                    stallStartNS = Clock.System.now()
                } else {
                    infoStream.message(
                        "DW",
                        "done stalling flushes for ${(Clock.System.now() - stallStartNS).toString(unit = DurationUnit.MILLISECONDS)} msec: netBytes: ${netBytes() / 1024.0 / 1024.0} MB flushBytes: ${this.flushingBytes / 1024.0 / 1024.0} MB fullFlush: ${this.isFullFlush}"
                    )
                }
            }
        }

        stallControl.updateStalled(stall)
        return stall
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun waitForFlush() {
        logger.debug { "DWFC.waitForFlush() enter: flushingWriters=${flushingWriters.size} queued=${flushQueue.size} blocked=${blockedFlushes.size} fullFlush=$isFullFlush" }
        while (flushingWriters.isNotEmpty()) {
            try {
                /*(this as java.lang.Object).wait()*/
                // TODO notifyAll is not supported in KMP, need to think what to do here
                logger.debug { "DWFC.waitForFlush() loop: flushingWriters=${flushingWriters.size} queued=${flushQueue.size} blocked=${blockedFlushes.size}" }
            } catch (e: CancellationException) {
                throw ThreadInterruptedException(e)
            }
        }
        logger.debug { "DWFC.waitForFlush() exit" }
    }

    /**
     * Sets flush pending state on the given [DocumentsWriterPerThread]. The [ ] must have indexed at least on Document and must not be already
     * pending.
     */
    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun setFlushPending(perThread: DocumentsWriterPerThread) {
        assert(!perThread.isFlushPending())
        if (perThread.numDocsInRAM > 0) {
            perThread.setFlushPending() // write access synced
            val bytes: Long = perThread.lastCommittedBytesUsed
            this.flushingBytes += bytes
            activeBytes -= bytes
            numPending++ // write access synced
            logger.debug { "DWFC.setFlushPending() seg=${perThread.getSegmentInfo().name} numDocs=${perThread.numDocsInRAM} numPending=$numPending activeBytes=$activeBytes flushingBytes=$flushingBytes" }
            assert(assertMemory())
        } // don't assert on numDocs since we could hit an abort excp. while selecting that dwpt for

        // flushing
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun doOnAbort(perThread: DocumentsWriterPerThread) {
        try {
            assert(perThreadPool.isRegistered(perThread))
            assert(perThread.isHeldByCurrentThread)
            if (perThread.isFlushPending()) {
                this.flushingBytes -= perThread.lastCommittedBytesUsed
            } else {
                activeBytes -= perThread.lastCommittedBytesUsed
            }
            assert(assertMemory())
            // Take it out of the loop this DWPT is stale
        } finally {
            updateStallState()
            val checkedOut: Boolean = perThreadPool.checkout(perThread)
            assert(checkedOut)
        }
    }

    /** To be called only by the owner of this object's monitor lock  */
    private fun checkoutAndBlock(perThread: DocumentsWriterPerThread) {

        // TODO Thread is not supported in KMP, need to think what to do here
        /*assert(java.lang.Thread.holdsLock(this))*/
        assert(perThreadPool.isRegistered(perThread))
        assert(perThread.isHeldByCurrentThread)
        assert(perThread.isFlushPending()) { "can not block non-pending threadstate" }
        assert(this.isFullFlush) { "can not block if fullFlush == false" }
        numPending-- // write access synced
        blockedFlushes.add(perThread)
        val checkedOut: Boolean = perThreadPool.checkout(perThread)
        assert(checkedOut)
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun checkOutForFlush(
        perThread: DocumentsWriterPerThread
    ): DocumentsWriterPerThread {

        // TODO Thread is not supported in KMP, need to think what to do here
        /*assert(java.lang.Thread.holdsLock(this))*/
        assert(perThread.isFlushPending())
        assert(perThread.isHeldByCurrentThread)
        assert(perThreadPool.isRegistered(perThread))
        try {
            addFlushingDWPT(perThread)
            numPending-- // write access synced
            val checkedOut: Boolean = perThreadPool.checkout(perThread)
            assert(checkedOut)
            logger.debug { "DWFC.checkOutForFlush() seg=${perThread.getSegmentInfo().name} numPending=$numPending flushingWriters=${flushingWriters.size}" }
            return perThread
        } finally {
            updateStallState()
        }
    }

    private fun addFlushingDWPT(perThread: DocumentsWriterPerThread) {
        assert(!flushingWriters.contains(perThread)) { "DWPT is already flushing" }
        flushingWriters.add(perThread)
        logger.debug { "DWFC.addFlushingDWPT() seg=${perThread.getSegmentInfo().name} flushingWriters=${flushingWriters.size}" }
    }

    /**
     * Check whether deletes need to be applied. This can be used as a pre-flight check before calling
     * [.getAndResetApplyAllDeletes] to make sure that a single thread applies deletes.
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun getApplyAllDeletes(): Boolean {
        return flushDeletes.load()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun setApplyAllDeletes() {
        flushDeletes.store(true)
    }

    fun obtainAndLock(): DocumentsWriterPerThread {
        while (!closed) {
            val perThread: DocumentsWriterPerThread = perThreadPool.getAndLock()
            if (perThread.deleteQueue == documentsWriter.deleteQueue) {
                // simply return the DWPT even in a flush all case since we already hold the lock and the
                // DWPT is not stale
                // since it has the current delete queue associated with it. This means we have established
                // a happens-before
                // relationship and all docs indexed into this DWPT are guaranteed to not be flushed with
                // the currently
                // progress full flush.
                return perThread
            } else {
                try {
                    // we must first assert otherwise the full flush might make progress once we unlock the
                    // dwpt
                    assert(
                        this.isFullFlush && !fullFlushMarkDone
                    ) {
                        ("found a stale DWPT but full flush mark phase is already done fullFlush: "
                                + this.isFullFlush
                                + " markDone: "
                                + fullFlushMarkDone)
                    }
                } finally {
                    perThread.unlock()
                    // There is a flush-all in process and this DWPT is
                    // now stale - try another one
                }
            }
        }
        throw AlreadyClosedException("flush control is closed")
    }

    suspend fun markForFullFlush(): Long {
        var seqNo: Long
        //synchronized(this) {
        assert(
            !this.isFullFlush
        ) { "called DWFC#markForFullFlush() while full flush is still running" }
        assert(!fullFlushMarkDone) { "full flush collection marker is still set to true" }
        this.isFullFlush = true
        val flushingQueue: DocumentsWriterDeleteQueue = documentsWriter.deleteQueue
        logger.debug { "DWFC.markForFullFlush() enter: activeBytes=$activeBytes flushingBytes=$flushingBytes numPending=$numPending" }
        // Set a new delete queue - all subsequent DWPT will use this queue until
        // we do another full flush
        perThreadPool
            .lockNewWriters() // no new thread-states while we do a flush otherwise the seqNo
        // accounting might be off
        try {
            // Insert a gap in seqNo of current active thread count, in the worst case each of those
            // threads now have one operation in flight.  It's fine
            // if we have some sequence numbers that were never assigned:
            seqNo = documentsWriter.resetDeleteQueue(perThreadPool.size())
        } finally {
            perThreadPool.unlockNewWriters()
        }
        //}
        val fullFlushBuffer: MutableList<DocumentsWriterPerThread> = mutableListOf()
        for (next in perThreadPool.filterAndLock { dwpt: DocumentsWriterPerThread -> dwpt.deleteQueue == flushingQueue }) {
            try {
                if (next.numDocsInRAM > 0) {
                    //synchronized(this) {
                    if (!next.isFlushPending()) {
                        setFlushPending(next)
                    }
                    val flushingDWPT: DocumentsWriterPerThread = checkOutForFlush(next)
                    //}
                    checkNotNull(flushingDWPT) { "DWPT must never be null here since we hold the lock and it holds documents" }
                    assert(next == flushingDWPT) { "flushControl returned different DWPT" }
                    fullFlushBuffer.add(flushingDWPT)
                } else {
                    val checkout: Boolean = perThreadPool.checkout(next)
                    assert(checkout)
                }
            } finally {
                next.unlock()
            }
        }
        //synchronized(this) {
        pruneBlockedQueue(flushingQueue)
        assert(assertBlockedFlushes(documentsWriter.deleteQueue))
        flushQueue.addAll(fullFlushBuffer)
        logger.debug { "DWFC.markForFullFlush() collected: toFlush=${fullFlushBuffer.size} queued=${flushQueue.size} blocked=${blockedFlushes.size}" }
        updateStallState()
        fullFlushMarkDone = true
        //}
        assert(assertActiveDeleteQueue(documentsWriter.deleteQueue))
        assert(flushingQueue.lastSequenceNumber <= flushingQueue.maxSeqNo)
        logger.debug { "DWFC.markForFullFlush() exit: seqNo will be set by resetDeleteQueue" }
        return seqNo
    }

    private fun assertActiveDeleteQueue(queue: DocumentsWriterDeleteQueue): Boolean {
        for (next in perThreadPool) {
            assert(next.deleteQueue == queue) { "numDocs: " + next.numDocsInRAM }
        }
        return true
    }

    /**
     * Prunes the blockedQueue by removing all DWPTs that are associated with the given flush queue.
     */
    private fun pruneBlockedQueue(flushingQueue: DocumentsWriterDeleteQueue) {

        // TODO Thread is not supported in KMP, need to think what to do here
        /*assert(java.lang.Thread.holdsLock(this))*/
        val iterator: MutableIterator<DocumentsWriterPerThread> = blockedFlushes.iterator()
        while (iterator.hasNext()) {
            val blockedFlush: DocumentsWriterPerThread = iterator.next()
            if (blockedFlush.deleteQueue == flushingQueue) {
                iterator.remove()
                addFlushingDWPT(blockedFlush)
                // don't decr pending here - it's already done when DWPT is blocked
                flushQueue.add(blockedFlush)
            }
        }
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun finishFullFlush() {
        assert(this.isFullFlush)
        assert(flushQueue.isEmpty())
        assert(flushingWriters.isEmpty())
        try {
            if (!blockedFlushes.isEmpty()) {
                assert(assertBlockedFlushes(documentsWriter.deleteQueue))
                pruneBlockedQueue(documentsWriter.deleteQueue)
                assert(blockedFlushes.isEmpty())
            }
        } finally {
            this.isFullFlush = false
            fullFlushMarkDone = this.isFullFlush
            logger.debug { "DWFC.finishFullFlush(): fullFlush cleared" }
            updateStallState()
        }
    }

    fun assertBlockedFlushes(flushingQueue: DocumentsWriterDeleteQueue): Boolean {
        for (blockedFlush in blockedFlushes) {
            assert(blockedFlush.deleteQueue == flushingQueue)
        }
        return true
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun abortFullFlushes() {
        try {
            abortPendingFlushes()
        } finally {
            this.isFullFlush = false
            fullFlushMarkDone = this.isFullFlush
        }
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun abortPendingFlushes() {
        try {
            for (dwpt in flushQueue) {
                try {
                    documentsWriter.subtractFlushedNumDocs(dwpt.numDocsInRAM)
                    dwpt.abort()
                } catch (ex: Exception) {
                    // that's fine we just abort everything here this is best effort
                } finally {
                    doAfterFlush(dwpt)
                }
            }
            for (blockedFlush in blockedFlushes) {
                try {
                    addFlushingDWPT(
                        blockedFlush
                    )
                    documentsWriter.subtractFlushedNumDocs(blockedFlush.numDocsInRAM)
                    blockedFlush.abort()
                } catch (ex: Exception) {
                    // that's fine we just abort everything here this is best effort
                } finally {
                    doAfterFlush(blockedFlush)
                }
            }
        } finally {
            logger.debug { "DWFC.abortPendingFlushes(): clearing queues queued=${flushQueue.size} blocked=${blockedFlushes.size}" }
            flushQueue.clear()
            blockedFlushes.clear()
            updateStallState()
        }
    }

    /** Returns the number of flushes that are already checked out but not yet actively flushing  */
    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun numQueuedFlushes(): Int {
        return flushQueue.size
    }

    /**
     * Returns the number of flushes that are checked out but not yet available for flushing. This
     * only applies during a full flush if a DWPT needs flushing but must not be flushed until the
     * full flush has finished.
     */
    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun numBlockedFlushes(): Int {
        return blockedFlushes.size
    }

    /**
     * Returns the number of DWPTs currently marked as flushing (in-flight). */
    fun numFlushingDWPT(): Int {
        return flushingWriters.size
    }

    /**
     * This method will block if too many DWPT are currently flushing and no checked out DWPT are
     * available
     */
    fun waitIfStalled() {
        stallControl.waitIfStalled()
    }

    /** Returns `true` iff stalled  */
    fun anyStalledThreads(): Boolean {
        return stallControl.anyStalledThreads()
    }

    /** Returns the [IndexWriter] [InfoStream]  */
    fun getInfoStream(): InfoStream {
        return infoStream
    }

    // TODO Syncronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun findLargestNonPendingWriter(): DocumentsWriterPerThread {
        var maxRamUsingWriter: DocumentsWriterPerThread? = null
        // Note: should be initialized to -1 since some DWPTs might return 0 if their RAM usage has not
        // been committed yet.
        var maxRamSoFar: Long = -1
        var count = 0
        for (next in perThreadPool) {
            if (!next.isFlushPending() && next.numDocsInRAM > 0) {
                val nextRam: Long = next.lastCommittedBytesUsed
                if (infoStream.isEnabled("FP")) {
                    infoStream.message(
                        "FP", "thread state has " + nextRam + " bytes; docInRAM=" + next.numDocsInRAM
                    )
                }
                count++
                if (nextRam > maxRamSoFar) {
                    maxRamSoFar = nextRam
                    maxRamUsingWriter = next
                }
            }
        }
        if (infoStream.isEnabled("FP")) {
            infoStream.message("FP", "$count in-use non-flushing threads states")
        }
        return maxRamUsingWriter!!
    }

    /** Returns the largest non-pending flushable DWPT or `null` if there is none.  */
    fun checkoutLargestNonPendingWriter(): DocumentsWriterPerThread? {
        val largestNonPendingWriter: DocumentsWriterPerThread = findLargestNonPendingWriter()
        if (largestNonPendingWriter != null) {
            // we only lock this very briefly to swap it's DWPT out - we don't go through the DWPTPool and
            // it's free queue
            largestNonPendingWriter.lock()
            try {
                if (perThreadPool.isRegistered(largestNonPendingWriter)) {
                    // TODO Syncronized is not supported in KMP, need to think what to do here
                    //synchronized(this) {
                        try {
                            return checkout(
                                largestNonPendingWriter, largestNonPendingWriter.isFlushPending() == false
                            )
                        } finally {
                            updateStallState()
                        }
                    //}
                }
            } finally {
                largestNonPendingWriter.unlock()
            }
        }
        return null
    }

    /** Bytes used by buffered deletes. */
    val deleteBytesUsed: Long
        get() = documentsWriter.deleteQueue.ramBytesUsed()

    /** Atomically get and reset the apply-all-deletes flag. */
    @OptIn(ExperimentalAtomicApi::class)
    fun getAndResetApplyAllDeletes(): Boolean {
        if (flushDeletes.load()) {
            flushDeletes.store(false)
            return true
        }
        return false
    }

    /** Notify flush policy that a delete-only operation occurred. */
    fun doOnDelete() {
        // For pure deletes, perThread is null; policy can decide to mark applyAllDeletes or select a DWPT.
        flushPolicy.onChange(this, null)
        // Update stall state given delete RAM may have grown.
        updateStallState()
    }

    /** Retrieve next pending DWPT to flush, if any (typically during full flush). */
    fun nextPendingFlush(): DocumentsWriterPerThread? {
        logger.debug { "DWFC.nextPendingFlush() called" }
        val next = flushQueue.poll()
        if (next == null) {
            logger.debug { "DWFC.nextPendingFlush() poll was null. queued=${flushQueue.size} fullFlush=$isFullFlush numPending=$numPending" }
            return null
        }
        logger.debug { "DWFC.nextPendingFlush() -> seg=${next.getSegmentInfo().name} docsInRAM=${next.numDocsInRAM}" }
        return next
    }

    override fun ramBytesUsed(): Long {
        // Track active + flushing + deletes RAM usage
        return activeBytes + flushingBytes + deleteBytesUsed
    }

    override fun close() {
        closed = true
    }
}
