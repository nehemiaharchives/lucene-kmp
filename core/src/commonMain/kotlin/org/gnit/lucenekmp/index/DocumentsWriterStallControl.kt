package org.gnit.lucenekmp.index

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.currentThreadId
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.concurrent.Volatile

/**
 * Controls the health status of a [DocumentsWriter] sessions. This class used to block
 * incoming indexing threads if flushing significantly slower than indexing to ensure the [ ]s healthiness. If flushing is significantly slower than indexing the net memory
 * used within an [IndexWriter] session can increase very quickly and easily exceed the JVM's
 * available memory.
 *
 *
 * To prevent OOM Errors and ensure IndexWriter's stability this class blocks incoming threads
 * from indexing once 2 x number of available [DocumentsWriterPerThread]s in [ ] is exceeded. Once flushing catches up and the number of flushing
 * DWPT is equal or lower than the number of active [DocumentsWriterPerThread]s threads are
 * released and can continue indexing.
 */
class DocumentsWriterStallControl {
    @Volatile
    private var stalled = false

    // for tests
    var numWaiting: Int = 0 // only with assert
        private set
    private var wasStalled = false // only with assert
    private val waiting: MutableSet<Long> = mutableSetOf() // only with assert
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    /**
     * Update the stalled flag status. This method will set the stalled flag to `true` iff
     * the number of flushing [DocumentsWriterPerThread] is greater than the number of active
     * [DocumentsWriterPerThread]. Otherwise it will reset the [ ] to healthy and release all threads waiting on [ ][.waitIfStalled]
     */
    fun updateStalled(stalled: Boolean) {
        lock.lock()
        try {
            if (this.stalled != stalled) {
                this.stalled = stalled
                if (stalled) {
                    wasStalled = true
                }
                runBlocking { condition.signalAll() }
            }
        } finally {
            lock.unlock()
        }
    }

    /** Blocks if documents writing is currently in a stalled state.  */
    fun waitIfStalled() {
        if (stalled) {
            lock.lock()
            try {
                if (stalled) { // react on the first wakeup call!
                    // don't loop here, higher level logic will re-stall!
                    val threadId = currentThreadId()
                    try {
                        incWaiters(threadId)
                        // Defensive, in case we have a concurrency bug that fails to .notify/All our thread:
                        // just wait for up to 1 second here, and let caller re-stall if it's still needed:
                        runBlocking { condition.await(1000, TimeUnit.MILLISECONDS) }
                    } catch (e: CancellationException) {
                        throw ThreadInterruptedException(e)
                    } finally {
                        decrWaiters(threadId)
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    fun anyStalledThreads(): Boolean {
        return stalled
    }

    private fun incWaiters(threadId: Long) {
        numWaiting++
        assert(waiting.add(threadId))
        assert(numWaiting > 0)
    }

    private fun decrWaiters(threadId: Long) {
        numWaiting--
        assert(waiting.remove(threadId))
        assert(numWaiting >= 0)
    }

    fun hasBlocked(): Boolean { // for tests
        lock.lock()
        try {
            return numWaiting > 0
        } finally {
            lock.unlock()
        }
    }

    val isHealthy: Boolean
        get() = !stalled // volatile read!

    fun isThreadQueued(threadId: Long): Boolean { // for tests
        lock.lock()
        try {
            return waiting.contains(threadId)
        } finally {
            lock.unlock()
        }
    }

    fun wasStalled(): Boolean { // for tests
        lock.lock()
        try {
            return wasStalled
        } finally {
            lock.unlock()
        }
    }
}
