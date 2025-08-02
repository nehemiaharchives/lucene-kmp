package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.AlreadyClosedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * [DocumentsWriterPerThreadPool] controls [DocumentsWriterPerThread] instances and
 * their thread assignments during indexing. Each [DocumentsWriterPerThread] is, once obtained
 * from the pool, exclusively used for indexing a single document or list of documents by the
 * obtaining thread. Each indexing thread must obtain such a [DocumentsWriterPerThread] to
 * make progress. Depending on the [DocumentsWriterPerThreadPool] implementation [ ] assignments might differ from document to document.
 *
 *
 * Once a [DocumentsWriterPerThread] is selected for flush, the [ ] will be checked out of the thread pool and won't be reused for
 * indexing. See [.checkout].
 */
class DocumentsWriterPerThreadPool(private val dwptFactory: () -> DocumentsWriterPerThread) :
    Iterable<DocumentsWriterPerThread>, AutoCloseable {
    private val dwpts: MutableSet<DocumentsWriterPerThread> = mutableSetOf()
        /*java.util.Collections.newSetFromMap<DocumentsWriterPerThread>(java.util.IdentityHashMap<DocumentsWriterPerThread, Boolean>())*/
    private val freeList: LockableConcurrentApproximatePriorityQueue<DocumentsWriterPerThread> =
        LockableConcurrentApproximatePriorityQueue()
    private var takenWriterPermits = 0

    @Volatile
    private var closed = false

    private val mutex = Mutex()
    private val waiters = mutableListOf<CompletableDeferred<Unit>>()

    /** Returns the active number of [DocumentsWriterPerThread] instances.  */
    // Synchronized is not supported in KMP, so we use a mutex here
    /*@Synchronized*/
    suspend fun size(): Int = mutex.withLock { dwpts.size }

    // Synchronized is not supported in KMP, so we use a mutex here
    /*@Synchronized*/
    suspend fun lockNewWriters() = mutex.withLock {
        // this is similar to a semaphore - we need to acquire all permits ie. takenWriterPermits must
        // be == 0
        // any call to lockNewWriters() must be followed by unlockNewWriters() otherwise we will
        // deadlock at some
        // point
        assert(takenWriterPermits >= 0)
        takenWriterPermits++
    }

    // Synchronized is not supported in KMP, so we use a mutex here
    /*@Synchronized*/
    suspend fun unlockNewWriters() = mutex.withLock {
        assert(takenWriterPermits > 0)
        takenWriterPermits--
        if (takenWriterPermits == 0) {
            // Wake up all waiting coroutines
            waiters.forEach { it.complete(Unit) }
            waiters.clear()
        }
    }

    /**
     * Returns a new already locked [DocumentsWriterPerThread]
     *
     * @return a new [DocumentsWriterPerThread]
     */
    // Synchronized is not supported in KMP, so we use a mutex here
    /*@Synchronized*/
    private suspend fun newWriter(): DocumentsWriterPerThread = mutex.withLock {
        assert(takenWriterPermits >= 0)
        while (takenWriterPermits > 0) {
            val waiter = CompletableDeferred<Unit>()
            waiters.add(waiter)
            mutex.unlock() // Release lock before suspending
            try {
                waiter.await()
            } finally {
                mutex.lock() // Re-acquire lock after waking
            }
        }
        // we must check if we are closed since this might happen while we are waiting for the writer
        // permit
        // and if we miss that we might release a new DWPT even though the pool is closed. Yet, that
        // wouldn't be the
        // end of the world it's violating the contract that we don't release any new DWPT after this
        // pool is closed
        ensureOpen()
        val dwpt: DocumentsWriterPerThread = dwptFactory()
        dwpt.lock() // lock so nobody else will get this DWPT
        dwpts.add(dwpt)
        return dwpt
    }

    /**
     * This method is used by DocumentsWriter/FlushControl to obtain a DWPT to do an indexing
     * operation (add/updateDocument).
     */
    // TODO: maybe we should try to do load leveling here: we want roughly even numbers
    // of items (docs, deletes, DV updates) to most take advantage of concurrency while flushing
    fun getAndLock(): DocumentsWriterPerThread
         = runBlocking {
            ensureOpen()
            val dwpt: DocumentsWriterPerThread? = freeList.lockAndPoll()
            if (dwpt != null) {
                return@runBlocking dwpt
            }
            return@runBlocking newWriter()
        }

    private fun ensureOpen() {
        if (closed) {
            throw AlreadyClosedException("DWPTPool is already closed")
        }
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun contains(state: DocumentsWriterPerThread): Boolean {
        return dwpts.contains(state)
    }

    suspend fun marksAsFreeAndUnlock(state: DocumentsWriterPerThread) {
        val ramBytesUsed: Long = state.ramBytesUsed()
        assert(
            !state.isFlushPending() && !state.isAborted && !state.isQueueAdvanced
        ) {
            ("DWPT has pending flush: "
                    + state.isFlushPending()
                    + " aborted="
                    + state.isAborted
                    + " queueAdvanced="
                    + state.isQueueAdvanced)
        }
        assert(
            contains(state)
        ) { "we tried to add a DWPT back to the pool but the pool doesn't know about this DWPT" }
        freeList.addAndUnlock(state, ramBytesUsed)
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    override fun iterator(): MutableIterator<DocumentsWriterPerThread> {
        // copy on read - this is a quick op since num states is low
        return dwpts.toMutableSet().iterator()
    }

    /**
     * Filters all DWPTs the given predicate applies to and that can be checked out of the pool via
     * [.checkout]. All DWPTs returned from this method are already
     * locked and [.isRegistered] will return `true` for
     * all returned DWPTs
     */
    fun filterAndLock(predicate: (DocumentsWriterPerThread) -> Boolean): MutableList<DocumentsWriterPerThread> {
        val list: MutableList<DocumentsWriterPerThread> = mutableListOf()
        for (perThread in this) {
            if (predicate(perThread)) {
                perThread.lock()
                if (isRegistered(perThread)) {
                    list.add(perThread)
                } else {
                    // somebody else has taken this DWPT out of the pool.
                    // unlock and let it go
                    perThread.unlock()
                }
            }
        }
        return list.toMutableList()
    }

    /**
     * Removes the given DWPT from the pool unless it's already been removed before.
     *
     * @return `true` iff the given DWPT has been removed. Otherwise `false`
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun checkout(perThread: DocumentsWriterPerThread): Boolean {
        // The DWPT must be held by the current thread. This guarantees that concurrent calls to
        // #getAndLock cannot pull this DWPT out of the pool since #getAndLock does a DWPT#tryLock to
        // check if the DWPT is available.
        assert(perThread.isHeldByCurrentThread)
        if (dwpts.remove(perThread)) {
            freeList.remove(perThread)
        } else {
            assert(!freeList.contains(perThread))
            return false
        }
        return true
    }

    /** Returns `true` if this DWPT is still part of the pool  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun isRegistered(perThread: DocumentsWriterPerThread): Boolean {
        return dwpts.contains(perThread)
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    override fun close() {
        this.closed = true
    }
}
