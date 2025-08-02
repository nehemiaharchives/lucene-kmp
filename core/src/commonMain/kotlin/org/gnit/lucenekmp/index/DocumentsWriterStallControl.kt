package org.gnit.lucenekmp.index

import kotlinx.coroutines.CancellationException
import org.gnit.lucenekmp.jdkport.assert
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
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@get:Synchronized*/
    var numWaiting: Int = 0 // only with assert
        private set
    private var wasStalled = false // only with assert
    /*private val waiting: MutableMap<java.lang.Thread, Boolean> =
        java.util.IdentityHashMap<java.lang.Thread, Boolean>()*/ // only with assert

    /**
     * Update the stalled flag status. This method will set the stalled flag to `true` iff
     * the number of flushing [DocumentsWriterPerThread] is greater than the number of active
     * [DocumentsWriterPerThread]. Otherwise it will reset the [ ] to healthy and release all threads waiting on [ ][.waitIfStalled]

    // TODO Synchronized is not supported in KMP, need to think what to do here  */
    /*@Synchronized*/
    fun updateStalled(stalled: Boolean) {
        if (this.stalled != stalled) {
            this.stalled = stalled
            if (stalled) {
                wasStalled = true
            }

            // TODO need to implement something here
            /*(this as java.lang.Object).notifyAll()*/
        }
    }

    /** Blocks if documents writing is currently in a stalled state.  */
    fun waitIfStalled() {
        if (stalled) {
            // TODO Synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
                if (stalled) { // react on the first wakeup call!
                    // don't loop here, higher level logic will re-stall!
                    try {
                        incWaiters()
                        // Defensive, in case we have a concurrency bug that fails to .notify/All our thread:
                        // just wait for up to 1 second here, and let caller re-stall if it's still needed:

                        // TODO need to implement something here
                        /*(this as java.lang.Object).wait(1000)*/

                        decrWaiters()
                    } catch (e: CancellationException) {
                        throw ThreadInterruptedException(e)
                    }
                }
            //}
        }
    }

    fun anyStalledThreads(): Boolean {
        return stalled
    }

    private fun incWaiters() {
        numWaiting++
        // TODO Thread is not supported in KMP, need to think what to do here
        //assert(waiting.put(java.lang.Thread.currentThread(), java.lang.Boolean.TRUE) == null)
        assert(numWaiting > 0)
    }

    private fun decrWaiters() {
        numWaiting--

        // TODO Thread is not supported in KMP, need to think what to do here
        //checkNotNull(waiting.remove(java.lang.Thread.currentThread()))
        assert(numWaiting >= 0)
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun hasBlocked(): Boolean { // for tests
        return numWaiting > 0
    }

    val isHealthy: Boolean
        get() =// for tests
            !stalled // volatile read!

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    /*fun isThreadQueued(t: java.lang.Thread): Boolean { // for tests
        return waiting.containsKey(t)
    }*/

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun wasStalled(): Boolean { // for tests
        return wasStalled
    }
}
