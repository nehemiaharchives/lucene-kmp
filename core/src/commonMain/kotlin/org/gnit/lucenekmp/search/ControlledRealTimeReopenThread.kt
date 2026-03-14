package org.gnit.lucenekmp.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.jdkport.Closeable
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.TimeUnit
import kotlin.concurrent.Volatile
import kotlin.math.max

/**
 * Utility class that runs a thread to manage periodicc reopens of a [ReferenceManager], with
 * methods to wait for a specific index changes to become visible. When a given search request needs
 * to see a specific index change, call the {#waitForGeneration} to wait for that change to be
 * visible. Note that this will only scale well if most searches do not need to wait for a specific
 * index generation.
 *
 * @lucene.experimental
 */
open class ControlledRealTimeReopenThread<T>(
    private val writer: IndexWriter,
    private val manager: ReferenceManager<T>,
    targetMaxStaleSec: Double,
    targetMinStaleSec: Double
) : Thread(), Closeable {
    private val targetMaxStaleNS: Long
    private val targetMinStaleNS: Long

    @Volatile
    private var finish = false

    @Volatile
    private var waitingGen = 0L

    @Volatile
    private var searchingGen = 0L
    private var refreshStartGen = 0L

    private val reopenLock = ReentrantLock()
    private val reopenCond = reopenLock.newCondition()
    private val generationLock = ReentrantLock()
    private val generationCond = generationLock.newCondition()

    init {
        require(targetMaxStaleSec >= targetMinStaleSec) {
            "targetMaxScaleSec (= $targetMaxStaleSec) < targetMinStaleSec (=$targetMinStaleSec)"
        }
        targetMaxStaleNS = (1_000_000_000 * targetMaxStaleSec).toLong()
        targetMinStaleNS = (1_000_000_000 * targetMinStaleSec).toLong()
        manager.addListener(HandleRefresh())
    }

    private inner class HandleRefresh : ReferenceManager.RefreshListener {
        override fun beforeRefresh() {
            // Save the gen as of when we started the reopen; the
            // listener (HandleRefresh above) copies this to
            // searchingGen once the reopen completes:
            generationLock.lock()
            try {
                refreshStartGen = writer.getMaxCompletedSequenceNumber()
            } finally {
                generationLock.unlock()
            }
        }

        override fun afterRefresh(didRefresh: Boolean) {
            generationLock.lock()
            try {
                searchingGen = refreshStartGen
                runBlocking {
                    generationCond.signalAll()
                }
            } finally {
                generationLock.unlock()
            }
        }
    }

    override fun close() {
        // System.out.println("NRT: set finish");
        finish = true

        // So thread wakes up and notices it should finish:
        reopenLock.lock()
        try {
            runBlocking {
                reopenCond.signal()
            }
        } finally {
            reopenLock.unlock()
        }

        join()

        // Max it out so any waiting search threads will return:
        generationLock.lock()
        try {
            searchingGen = Long.MAX_VALUE
            runBlocking {
                generationCond.signalAll()
            }
        } finally {
            generationLock.unlock()
        }
    }

    /**
     * Waits for the target generation to become visible in the searcher. If the current searcher is
     * older than the target generation, this method will block until the searcher is reopened, by
     * another via [ReferenceManager.maybeRefresh] or until the [ReferenceManager] is
     * closed.
     *
     * @param targetGen the generation to wait for
     */
    @Throws(org.gnit.lucenekmp.jdkport.InterruptedException::class)
    fun waitForGeneration(targetGen: Long) {
        waitForGeneration(targetGen, -1)
    }

    /**
     * Waits for the target generation to become visible in the searcher, up to a maximum specified
     * milli-seconds. If the current searcher is older than the target generation, this method will
     * block until the searcher has been reopened by another thread via [ReferenceManager.maybeRefresh], the given waiting time has elapsed, or until the [ReferenceManager] is
     * closed.
     *
     * <p>NOTE: if the waiting time elapses before the requested target generation is available the
     * current [SearcherManager] is returned instead.
     *
     * @param targetGen the generation to wait for
     * @param maxMS maximum milliseconds to wait, or -1 to wait indefinitely
     * @return true if the targetGeneration is now available, or false if maxMS wait time was exceeded
     */
    @Throws(org.gnit.lucenekmp.jdkport.InterruptedException::class)
    fun waitForGeneration(targetGen: Long, maxMS: Int): Boolean {
        generationLock.lock()
        try {
            if (targetGen > searchingGen) {
                // Notify the reopen thread that the waitingGen has
                // changed, so it may wake up and realize it should
                // not sleep for much or any longer before reopening:
                reopenLock.lock()

                // Need to find waitingGen inside lock as it's used to determine
                // stale time
                waitingGen = max(waitingGen, targetGen)

                try {
                    runBlocking {
                        reopenCond.signal()
                    }
                } finally {
                    reopenLock.unlock()
                }

                val startMS = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

                while (targetGen > searchingGen) {
                    if (maxMS < 0) {
                        runBlocking {
                            generationCond.await()
                        }
                    } else {
                        val msLeft =
                            (startMS + maxMS) - TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
                        if (msLeft <= 0) {
                            return false
                        } else {
                            val signaled =
                                runBlocking {
                                    generationCond.await(msLeft, TimeUnit.MILLISECONDS)
                                }
                            if (!signaled && targetGen > searchingGen) {
                                return false
                            }
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw org.gnit.lucenekmp.jdkport.InterruptedException(ce.message ?: "interrupted")
        } finally {
            generationLock.unlock()
        }

        return true
    }

    override fun run() {
        runBlocking {
            // TODO: maybe use private thread ticktock timer, in
            // case clock shift messes up nanoTime?
            var lastReopenStartNS = System.nanoTime()

            // System.out.println("reopen: start");
            while (!finish) {

                // TODO: try to guestimate how long reopen might
                // take based on past data?

                // Loop until we've waiting long enough before the
                // next reopen:
                while (!finish) {

                    // Need lock before finding out if has waiting
                    reopenLock.lock()
                    try {
                        // True if we have someone waiting for reopened searcher:
                        val hasWaiting = waitingGen > searchingGen
                        val nextReopenStartNS =
                            lastReopenStartNS + (if (hasWaiting) targetMinStaleNS else targetMaxStaleNS)

                        val sleepNS = nextReopenStartNS - System.nanoTime()

                        if (sleepNS > 0) {
                            reopenCond.awaitNanos(sleepNS)
                        } else {
                            break
                        }
                    } finally {
                        reopenLock.unlock()
                    }
                }

                if (finish) {
                    break
                }

                lastReopenStartNS = System.nanoTime()
                try {
                    manager.maybeRefreshBlocking()
                } catch (ioe: IOException) {
                    throw RuntimeException(ioe)
                }
            }
        }
    }

    /** Returns which `generation` the current searcher is guaranteed to include. */
    fun getSearchingGen(): Long {
        return searchingGen
    }
}
