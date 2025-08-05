package org.gnit.lucenekmp.index

import dev.scottpierce.envvar.EnvVar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.gnit.lucenekmp.index.MergePolicy.OneMerge
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.RateLimitedIndexOutput
import org.gnit.lucenekmp.store.RateLimiter
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.ThreadInterruptedException
import okio.IOException
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.LinkedBlockingQueue
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.ThreadPoolExecutor
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.get
// import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.max

/**
 * A [MergeScheduler] that runs each merge using a separate thread.
 *
 *
 * Specify the max number of threads that may run at once, and the maximum number of simultaneous
 * merges with [.setMaxMergesAndThreads].
 *
 *
 * If the number of merges exceeds the max number of threads then the largest merges are paused
 * until one of the smaller merges completes.
 *
 *
 * If more than [.getMaxMergeCount] merges are requested then this class will forcefully
 * throttle the incoming threads by pausing until one more merges complete.
 *
 *
 * This class sets defaults based on Java's view of the cpu count, and it assumes a solid state
 * disk (or similar). If you have a spinning disk and want to maximize performance, use [ ][.setDefaultMaxMergesAndThreads].
 */
open class ConcurrentMergeScheduler
/** Sole constructor, with all settings set to default values.  */
    : MergeScheduler() {
    /** List of currently active [MergeThread]s.  */
    protected val mergeThreads: MutableList<MergeThread> = mutableListOf()

    /**
     * Returns `maxThreadCount`.
     *
     * @see .setMaxMergesAndThreads
     */
    // Max number of merge threads allowed to be running at
    // once.  When there are more merges then this, we
    // forcefully pause the larger ones, letting the smaller
    // ones run, up until maxMergeCount merges at which point
    // we forcefully pause incoming threads (that presumably
    // are the ones causing so much merging).
    // TODO synchronized is not supoorted in KMP, need to think what to do here
    /*@get:Synchronized*/
    var maxThreadCount: Int = AUTO_DETECT_MERGES_AND_THREADS
        private set

    /** See [.setMaxMergesAndThreads].  */
    // Max number of merges we accept before forcefully
    // throttling the incoming threads
    // TODO synchronized is not supoorted in KMP, need to think what to do here
    /*@get:Synchronized*/
    var maxMergeCount: Int = AUTO_DETECT_MERGES_AND_THREADS
        private set

    /** How many [MergeThread]s have kicked off (this is use to name them).  */
    protected var mergeThreadCount: Int = 0

    /** Current IO writes throttle rate  */
    protected var targetMBPerSec: Double = START_MB_PER_SEC

    /** Returns true if auto IO throttling is currently enabled.  */
    /** true if we should rate-limit writes for each merge  */
    // TODO synchronized is not supoorted in KMP, need to think what to do here
    /*@get:Synchronized*/
    var autoIOThrottle: Boolean = false
        private set

    private var forceMergeMBPerSec = Double.POSITIVE_INFINITY

    /** The executor provided for intra-merge parallelization  */
    protected var intraMergeExecutor: CachedExecutor? = null

    /** Mutex used to stall threads when too many merges are running. */
    protected val stallMutex = Mutex()

    /**
     * Expert: directly set the maximum number of merge threads and simultaneous merges allowed.
     *
     * @param maxMergeCount the max # simultaneous merges that are allowed. If a merge is necessary
     * yet we already have this many threads running, the incoming thread (that is calling
     * add/updateDocument) will block until a merge thread has completed. Note that we will only
     * run the smallest `maxThreadCount` merges at a time.
     * @param maxThreadCount the max # simultaneous merge threads that should be running at once. This
     * must be &lt;= `maxMergeCount`
     */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun setMaxMergesAndThreads(maxMergeCount: Int, maxThreadCount: Int) {
        if (maxMergeCount == AUTO_DETECT_MERGES_AND_THREADS
            && maxThreadCount == AUTO_DETECT_MERGES_AND_THREADS
        ) {
            // OK
            this.maxMergeCount = AUTO_DETECT_MERGES_AND_THREADS
            this.maxThreadCount = AUTO_DETECT_MERGES_AND_THREADS
        } else require(maxMergeCount != AUTO_DETECT_MERGES_AND_THREADS) { "both maxMergeCount and maxThreadCount must be AUTO_DETECT_MERGES_AND_THREADS" }
        require(maxThreadCount != AUTO_DETECT_MERGES_AND_THREADS) { "both maxMergeCount and maxThreadCount must be AUTO_DETECT_MERGES_AND_THREADS" }
        require(maxThreadCount >= 1) { "maxThreadCount should be at least 1" }
        require(maxMergeCount >= 1) { "maxMergeCount should be at least 1" }
        require(maxThreadCount <= maxMergeCount) { "maxThreadCount should be <= maxMergeCount (= $maxMergeCount)" }
        this.maxThreadCount = maxThreadCount
        this.maxMergeCount = maxMergeCount
    }

    /**
     * Sets max merges and threads to proper defaults for rotational or non-rotational storage.
     *
     * @param spins true to set defaults best for traditional rotational storage (spinning disks),
     * else false (e.g. for solid-state disks)
     */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun setDefaultMaxMergesAndThreads(spins: Boolean) {
        if (spins) {
            maxThreadCount = 1
            maxMergeCount = 6
        } else {
            var coreCount = /*java.lang.Runtime.getRuntime().availableProcessors()*/ 1

            // Let tests override this to help reproducing a failure on a machine that has a different
            // core count than the one where the test originally failed:
            try {
                val value: String? = EnvVar[DEFAULT_CPU_CORE_COUNT_PROPERTY] /*java.lang.System.getProperty(DEFAULT_CPU_CORE_COUNT_PROPERTY)*/
                if (value != null) {
                    coreCount = value.toInt()
                }
            } catch (ignored: Throwable) {
            }

            // If you are indexing at full throttle, how many merge threads do you need to keep up It
            // depends: for most data structures, merging is cheaper than indexing/flushing, but for knn
            // vectors, merges can require about as much work as the initial indexing/flushing. Plus
            // documents are indexed/flushed only once, but may be merged multiple times.
            // Here, we assume an intermediate scenario where merging requires about as much work as
            // indexing/flushing overall, so we give half the core count to merges.
            maxThreadCount = max(1, coreCount / 2)
            maxMergeCount = maxThreadCount + 5
        }
    }

    /**
     * Set the per-merge IO throttle rate for forced merges (default: `Double.POSITIVE_INFINITY`).
     */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun setForceMergeMBPerSec(v: Double) {
        forceMergeMBPerSec = v
        updateMergeThreads()
    }

    /** Get the per-merge IO throttle rate for forced merges.  */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun getForceMergeMBPerSec(): Double {
        return forceMergeMBPerSec
    }

    /**
     * Turn on dynamic IO throttling, to adaptively rate limit writes bytes/sec to the minimal rate
     * necessary so merges do not fall behind. By default this is disabled and writes are not
     * rate-limited.
     */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun enableAutoIOThrottle() {
        this.autoIOThrottle = true
        targetMBPerSec = START_MB_PER_SEC
        updateMergeThreads()
    }

    /**
     * Turn off auto IO throttling.
     *
     * @see .enableAutoIOThrottle
     */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun disableAutoIOThrottle() {
        this.autoIOThrottle = false
        updateMergeThreads()
    }

    // TODO synchronized is not supoorted in KMP, need to think what to do here
    /*@get:Synchronized*/
    val iORateLimitMBPerSec: Double
        /**
         * Returns the currently set per-merge IO writes rate limit, if [.enableAutoIOThrottle] was
         * called, else `Double.POSITIVE_INFINITY`.
         */
        get() {
            return if (this.autoIOThrottle) {
                targetMBPerSec
            } else {
                Double.POSITIVE_INFINITY
            }
        }

    /** Removes the calling thread from the active merge threads.  */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun removeMergeThread() {
        val currentThread = Job() /*java.lang.Thread.currentThread()*/
        // Paranoia: don't trust Thread.equals:
        for (i in mergeThreads.indices) {
            if (mergeThreads[i] === currentThread) {
                mergeThreads.removeAt(i)
                return
            }
        }

        assert(false) { "merge thread $currentThread was not found" }
    }

    override fun getIntraMergeExecutor(merge: OneMerge): Executor {
        checkNotNull(intraMergeExecutor) { "scaledExecutor is not initialized" }
        // don't do multithreaded merges for small merges
        if (merge.estimatedMergeBytes < MIN_BIG_MERGE_MB * 1024 * 1024) {
            return super.getIntraMergeExecutor(merge)
        }
        return intraMergeExecutor!!
    }

    override fun wrapForMerge(
        merge: OneMerge,
        `in`: Directory
    ): Directory {
        val mergeThread = Job()

        // TODO not sure what to do here, synchronized is not supported in KMP
        /*if (!MergeThread::class.java.isInstance(mergeThread)) {
            throw java.lang.AssertionError(
                "wrapForMerge should be called from MergeThread. Current thread: " + mergeThread
            )
        }*/

        // Return a wrapped Directory which has rate-limited output.
        // Note: the rate limiter is only per thread. So, if there are multiple merge threads running
        // and throttling is required, each thread will be throttled independently.
        // The implication of this, is that the total IO rate could be higher than the target rate.
        val rateLimiter: RateLimiter = (mergeThread as MergeThread).rateLimiter
        return object : FilterDirectory(`in`) {
            @Throws(IOException::class)
            override fun createOutput(
                name: String,
                context: IOContext
            ): IndexOutput {
                ensureOpen()

                // This Directory is only supposed to be used during merging,
                // so all writes should have MERGE context, else there is a bug
                // somewhere that is failing to pass down the right IOContext:
                assert(context.context == IOContext.Context.MERGE) { "got context=" + context.context }

                return RateLimitedIndexOutput(rateLimiter, `in`.createOutput(name, context))
            }
        }
    }

    /**
     * Called whenever the running merges have changed, to set merge IO limits. This method sorts the
     * merge threads by their merge size in descending order and then pauses/unpauses threads from
     * first to last -- that way, smaller merges are guaranteed to run before larger ones.
     */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    protected fun updateMergeThreads() {
        // Only look at threads that are alive & not in the
        // process of stopping (ie have an active merge):

        val activeMerges: MutableList<MergeThread> = mutableListOf()

        var threadIdx = 0
        while (threadIdx < mergeThreads.size) {
            val mergeThread = mergeThreads[threadIdx]
            if (!mergeThread.isAlive()) {
                // Prune any dead threads
                mergeThreads.removeAt(threadIdx)
                continue
            }
            activeMerges.add(mergeThread)
            threadIdx++
        }

        // Sort the merge threads, largest first:
        CollectionUtil.timSort(activeMerges)

        val activeMergeCount = activeMerges.size

        var bigMergeCount = 0

        threadIdx = activeMergeCount - 1
        while (threadIdx >= 0) {
            val mergeThread = activeMerges[threadIdx]
            if (mergeThread.merge.estimatedMergeBytes > MIN_BIG_MERGE_MB * 1024 * 1024) {
                bigMergeCount = 1 + threadIdx
                break
            }
            threadIdx--
        }

        val now: Long = System.nanoTime()

        val message: StringBuilder?
        if (verbose()) {
            message = StringBuilder()
            message.append(
                "updateMergeThreads ioThrottle=${this.autoIOThrottle} targetMBPerSec=$targetMBPerSec"
            )
        } else {
            message = null
        }

        threadIdx = 0
        while (threadIdx < activeMergeCount) {
            val mergeThread = activeMerges[threadIdx]

            val merge: OneMerge = mergeThread.merge

            // pause the thread if maxThreadCount is smaller than the number of merge threads.
            val doPause = threadIdx < bigMergeCount - maxThreadCount
            val newMBPerSec: Double = if (doPause) {
                0.0
            } else if (merge.maxNumSegments != -1) {
                forceMergeMBPerSec
            } else if (!this.autoIOThrottle) {
                Double.POSITIVE_INFINITY
            } else if (merge.estimatedMergeBytes < MIN_BIG_MERGE_MB * 1024 * 1024) {
                // Don't rate limit small merges:
                Double.POSITIVE_INFINITY
            } else {
                targetMBPerSec
            }

            val rateLimiter: MergeRateLimiter = mergeThread.rateLimiter
            val curMBPerSec: Double = rateLimiter.mBPerSec

            if (verbose()) {
                var mergeStartNS: Long = merge.mergeStartNS
                if (mergeStartNS == -1L) {
                    // IndexWriter didn't start the merge yet:
                    mergeStartNS = now
                }
                message!!.append('\n')
                message.append(
                    "merge thread ${mergeThread.getName()} estSize=${bytesToMB(merge.estimatedMergeBytes)} MB (written=${bytesToMB(rateLimiter.getTotalBytesWritten())} MB) runTime=${nsToSec(now - mergeStartNS)}fs (stopped=${nsToSec(rateLimiter.totalStoppedNS)}s, paused=${nsToSec(rateLimiter.totalPausedNS)}s) rate=${rateToString(rateLimiter.mBPerSec)}\n"
                )

                if (newMBPerSec != curMBPerSec) {
                    if (newMBPerSec == 0.0) {
                        message.append("  now stop")
                    } else if (curMBPerSec == 0.0) {
                        if (newMBPerSec == Double.POSITIVE_INFINITY) {
                            message.append("  now resume")
                        } else {
                            message.append(
                                "  now resume to $newMBPerSec MB/sec"
                            )
                        }
                    } else {
                        message.append(
                            "  now change from $curMBPerSec MB/sec to $newMBPerSec MB/sec"
                        )
                    }
                } else if (curMBPerSec == 0.0) {
                    message.append("  leave stopped")
                } else {
                    message.append("  leave running at $curMBPerSec MB/sec")
                }
            }

            rateLimiter.mBPerSec = newMBPerSec
            threadIdx++
        }
        if (verbose()) {
            message(message.toString())
        }
    }

    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun initDynamicDefaults(directory: Directory) {
        if (maxThreadCount == AUTO_DETECT_MERGES_AND_THREADS) {
            setDefaultMaxMergesAndThreads(false)
            if (verbose()) {
                message(
                    ("initDynamicDefaults maxThreadCount="
                            + maxThreadCount
                            + " maxMergeCount="
                            + maxMergeCount)
                )
            }
        }
    }

    override fun close() {
        super.close()
        try {
            runBlocking{ sync() }
        } finally {
            if (intraMergeExecutor != null) {
                intraMergeExecutor!!.shutdown()
            }
        }
    }

    /**
     * Wait for any running merge threads to finish. This call is not interruptible as used by [ ][.close].
     */
    suspend fun sync() {
        var interrupted = false
        try {
            while (true) {
                var toSync: MergeThread? = null

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                    for (t in mergeThreads) {
                        // In case a merge thread is calling us, don't try to sync on
                        // itself, since that will never finish!
                        if (t.isAlive() && t !== currentCoroutineContext()[Job]) {
                            toSync = t
                            break
                        }
                    }
                //}
                if (toSync != null) {
                    try {
                        toSync.join()
                    } catch (ie: InterruptedException) {
                        // ignore this Exception, we will retry until all threads are dead
                        interrupted = true
                    }
                } else {
                    break
                }
            }
        } finally {
            // finally, restore interrupt status:
            if (interrupted)
                currentCoroutineContext()[Job]!!.cancel()
                //java.lang.Thread.currentThread().interrupt()
        }
    }

    /**
     * Returns the number of merge threads that are alive, ignoring the calling thread if it is a
     * merge thread. Note that this number is  [.mergeThreads] size.
     *
     * @lucene.internal
     */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun mergeThreadCount(): Int {
        val currentThread = Job()
        var count = 0
        for (mergeThread in mergeThreads) {
            if (currentThread !== mergeThread && mergeThread.isAlive()
                && mergeThread.merge.isAborted == false
            ) {
                count++
            }
        }
        return count
    }

    @Throws(IOException::class)
    override fun initialize(
        infoStream: InfoStream,
        directory: Directory
    ) {
        super.initialize(infoStream, directory)
        initDynamicDefaults(directory)
        if (intraMergeExecutor == null) {
            intraMergeExecutor = CachedExecutor()
        }
    }

    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    override suspend fun merge(
        mergeSource: MergeSource,
        trigger: MergeTrigger
    ) {
        if (trigger == MergeTrigger.CLOSING) {
            // Disable throttling on close:
            targetMBPerSec = MAX_MERGE_MB_PER_SEC
            updateMergeThreads()
        }

        // First, quickly run through the newly proposed merges
        // and add any orthogonal merges (ie a merge not
        // involving segments already pending to be merged) to
        // the queue.  If we are way behind on merging, many of
        // these newly proposed merges will likely already be
        // registered.
        if (verbose()) {
            message("now merge")
            message("  index(source): $mergeSource")
        }

        // Iterate, pulling from the IndexWriter's queue of
        // pending merges, until it's empty:
        while (true) {
            if (!maybeStall(mergeSource)) {
                break
            }

            val merge: OneMerge = mergeSource.nextMerge
            if (merge == null) {
                if (verbose()) {
                    message("  no more merges pending; now return")
                }
                return
            }

            var success = false
            try {
                // OK to spawn a new merge thread to handle this
                // merge:
                val newMergeThread = getMergeThread(mergeSource, merge)
                mergeThreads.add(newMergeThread)

                updateIOThrottle(newMergeThread.merge, newMergeThread.rateLimiter)

                if (verbose()) {
                    message("    launch new thread [" + newMergeThread.getName() + "]")
                }

                newMergeThread.start()
                updateMergeThreads()

                success = true
            } finally {
                if (!success) {
                    mergeSource.onMergeFinished(merge)
                }
            }
        }
    }

    /**
     * This is invoked by [.merge] to possibly stall the incoming thread when there are too many
     * merges running or pending. The default behavior is to force this thread, which is producing too
     * many segments for merging to keep up, to wait until merges catch up. Applications that can take
     * other less drastic measures, such as limiting how many threads are allowed to index, can do
     * nothing here and throttle elsewhere.
     *
     *
     * If this method wants to stall but the calling thread is a merge thread, it should return
     * false to tell caller not to kick off any new merges.
     */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    protected suspend fun maybeStall(mergeSource: MergeSource): Boolean {
        var startStallTime: Long = 0
        while (mergeSource.hasPendingMerges() && mergeThreadCount() >= maxMergeCount) {
            // This means merging has fallen too far behind: we
            // have already created maxMergeCount threads, and
            // now there's at least one more merge pending.
            // Note that only maxThreadCount of
            // those created merge threads will actually be
            // running; the rest will be paused (see
            // updateMergeThreads).  We stall this producer
            // thread to prevent creation of new segments,
            // until merging has caught up:

            if (mergeThreads.any { it.job === currentCoroutineContext()[Job] /*java.lang.Thread.currentThread()*/ }) {
                // Never stall a merge thread since this blocks the thread from
                // finishing and calling updateMergeThreads, and blocking it
                // accomplishes nothing anyway (it's not really a segment producer):
                return false
            }

            if (startStallTime == 0L) {
                startStallTime = System.currentTimeMillis()
                if (verbose()) {
                    message("    too many merges; stalling...")
                }
            }
            doStall()
        }

        if (verbose() && startStallTime != 0L) {
            message("  stalled for " + (System.currentTimeMillis() - startStallTime) + " ms")
        }

        return true
    }

    /** Called from [.maybeStall] to pause the calling thread for a bit.  */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    protected suspend fun doStall() {
        try {
            // Wait for only .25 seconds or until notified
            withTimeoutOrNull(250) {
                stallMutex.withLock {
                    // Just acquire/release to simulate wait/notify
                }
            }
        } catch (ie: CancellationException) {
            throw ThreadInterruptedException(ie)
        }
    }

    /**
     * Does the actual merge, by calling [ ][MergeScheduler.MergeSource.merge]
     */
    @Throws(IOException::class)
    protected fun doMerge(
        mergeSource: MergeSource,
        merge: OneMerge
    ) {
        mergeSource.merge(merge)
    }

    /** Create and return a new MergeThread  */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    protected fun getMergeThread(
        mergeSource: MergeSource,
        merge: OneMerge
    ): MergeThread {
        val thread = MergeThread(mergeSource, merge)
        /*thread.setDaemon(true)*/ // no setDaemon in coroutine Job
        /*thread.setName("Lucene Merge Thread #" + mergeThreadCount++)*/ // no setName in coroutine Job
        return thread
    }

    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    suspend fun runOnMergeFinished(mergeSource: MergeSource) {
        // the merge call as well as the merge thread handling in the finally
        // block must be sync'd on CMS otherwise stalling decisions might cause
        // us to miss pending merges
        assert(mergeThreads.any { it.job === currentCoroutineContext()[Job] }) { "caller is not a merge thread" }
        // Let CMS run new merges if necessary:
        try {
            merge(mergeSource, MergeTrigger.MERGE_FINISHED)
        } catch (ace: AlreadyClosedException) {
            // OK
        } catch (ioe: IOException) {
            throw UncheckedIOException(ioe)
        } finally {
            removeMergeThread()
            updateMergeThreads()
            // In case we had stalled indexing, we can now wake up and possibly unstall:
            // (this as java.lang.Object).notifyAll()
            // TODO perform same effect using KMP coroutines
            if (stallMutex.isLocked) {
                stallMutex.unlock()
            }
        }
    }

    /** Runs a merge thread to execute a single merge, then exits.  */
    protected open inner class MergeThread(
        private val mergeSource: MergeSource,
        val merge: OneMerge,
        // you can inject a scope or create one here:
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    ) : Comparable<MergeThread> {

        val rateLimiter: MergeRateLimiter = MergeRateLimiter(merge.mergeProgress)

        // create a Job that will execute your merge logic when started
        val job: Job = scope.launch(start = CoroutineStart.LAZY) {
            runMerge()  // suspending function containing your merge code
        }

        /** Mirrors Thread.getName() */
        fun getName(): String = "Lucene Merge Thread #${mergeThreadCount}"

        /** Mirrors Thread.isAlive() */
        fun isAlive(): Boolean = job.isActive

        /** Mirrors Thread.start() */
        fun start() {
            job.start()
        }

        /** Mirrors Thread.join() by blocking until completion */
        fun join() {
            runBlocking {
                job.join()
            }
        }

        override fun compareTo(other: MergeThread): Int =
            other.merge.estimatedMergeBytes
                .compareTo(this.merge.estimatedMergeBytes)

        private suspend fun runMerge() {
            try {
                if (verbose()) message("merge thread ${getName()} start")
                doMerge(mergeSource, merge)
                if (verbose()) {
                    message(
                        "merge thread ${this.getName()} merge segment [${getSegmentName(merge)}] done estSize=${bytesToMB(merge.estimatedMergeBytes)} MB (written=${bytesToMB(rateLimiter.getTotalBytesWritten())} MB) runTime=${nsToSec(System.nanoTime() - merge.mergeStartNS)}s (stopped=${nsToSec(rateLimiter.totalStoppedNS)}s, paused=${nsToSec(rateLimiter.totalPausedNS)}s) rate=${rateToString(rateLimiter.mBPerSec)}"
                    )
                }
                runOnMergeFinished(mergeSource)
                if (verbose()) message("merge thread ${getName()} end")
            } catch (exc: Throwable) {
                when {
                    exc is MergePolicy.MergeAbortedException -> { /* ignore */ }
                    !suppressExceptions               -> handleMergeException(exc)
                }
            }
        }
    }


    /** Called when an exception is hit in a background merge thread  */
    protected fun handleMergeException(exc: Throwable) {
        throw MergePolicy.MergeException(exc)
    }

    private var suppressExceptions = false

    /** Used for testing  */
    fun setSuppressExceptions() {
        if (verbose()) {
            message("will suppress merge exceptions")
        }
        suppressExceptions = true
    }

    /** Used for testing  */
    fun clearSuppressExceptions() {
        if (verbose()) {
            message("will not suppress merge exceptions")
        }
        suppressExceptions = false
    }

    override fun toString(): String {
        return (this::class.simpleName
                + ": "
                + "maxThreadCount="
                + maxThreadCount
                + ", "
                + "maxMergeCount="
                + maxMergeCount
                + ", "
                + "ioThrottle="
                + this.autoIOThrottle)
    }

    private fun isBacklog(now: Long, merge: OneMerge): Boolean {
        val mergeMB = bytesToMB(merge.estimatedMergeBytes)
        for (mergeThread in mergeThreads) {
            val mergeStartNS: Long = mergeThread.merge.mergeStartNS
            if (mergeThread.isAlive()
                && mergeThread.merge !== merge && mergeStartNS != -1L && mergeThread.merge.estimatedMergeBytes >= MIN_BIG_MERGE_MB * 1024 * 1024 && nsToSec(
                    now - mergeStartNS
                ) > 3.0
            ) {
                val otherMergeMB = bytesToMB(mergeThread.merge.estimatedMergeBytes)
                val ratio = otherMergeMB / mergeMB
                if (ratio > 0.3 && ratio < 3.0) {
                    return true
                }
            }
        }

        return false
    }

    /** Tunes IO throttle when a new merge starts.  */
    // Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun updateIOThrottle(
        newMerge: OneMerge,
        rateLimiter: MergeRateLimiter
    ) {
        if (this.autoIOThrottle == false) {
            return
        }

        val mergeMB = bytesToMB(newMerge.estimatedMergeBytes)
        if (mergeMB < MIN_BIG_MERGE_MB) {
            // Only watch non-trivial merges for throttling; this is safe because the MP must eventually
            // have to do larger merges:
            return
        }

        val now: Long = System.nanoTime()

        // Simplistic closed-loop feedback control: if we find any other similarly
        // sized merges running, then we are falling behind, so we bump up the
        // IO throttle, else we lower it:
        val newBacklog = isBacklog(now, newMerge)

        var curBacklog = false

        if (newBacklog == false) {
            if (mergeThreads.size > maxThreadCount) {
                // If there are already more than the maximum merge threads allowed, count that as backlog:
                curBacklog = true
            } else {
                // Now see if any still-running merges are backlog'd:
                for (mergeThread in mergeThreads) {
                    if (isBacklog(now, mergeThread.merge)) {
                        curBacklog = true
                        break
                    }
                }
            }
        }

        val curMBPerSec = targetMBPerSec

        if (newBacklog) {
            // This new merge adds to the backlog: increase IO throttle by 20%
            targetMBPerSec *= 1.20
            if (targetMBPerSec > MAX_MERGE_MB_PER_SEC) {
                targetMBPerSec = MAX_MERGE_MB_PER_SEC
            }
            if (verbose()) {
                if (curMBPerSec == targetMBPerSec) {
                    message(
                        "io throttle: new merge backlog; leave IO rate at ceiling $targetMBPerSec MB/sec"
                    )
                } else {
                    message(
                        "io throttle: new merge backlog; increase IO rate to $targetMBPerSec MB/sec"
                    )
                }
            }
        } else if (curBacklog) {
            // We still have an existing backlog; leave the rate as is:
            if (verbose()) {
                message(
                    "io throttle: current merge backlog; leave IO rate at $targetMBPerSec MB/sec"
                )
            }
        } else {
            // We are not falling behind: decrease IO throttle by 10%
            targetMBPerSec /= 1.10
            if (targetMBPerSec < MIN_MERGE_MB_PER_SEC) {
                targetMBPerSec = MIN_MERGE_MB_PER_SEC
            }
            if (verbose()) {
                if (curMBPerSec == targetMBPerSec) {
                    message(
                        "io throttle: no merge backlog; leave IO rate at floor $targetMBPerSec MB/sec"
                    )
                } else {
                    message(
                        "io throttle: no merge backlog; decrease IO rate to $targetMBPerSec MB/sec"
                    )
                }
            }
        }

        val rate: Double = if (newMerge.maxNumSegments != -1) {
            forceMergeMBPerSec
        } else {
            targetMBPerSec
        }
        rateLimiter.mBPerSec = rate
        targetMBPerSecChanged()
    }

    /** Subclass can override to tweak targetMBPerSec.  */
    protected fun targetMBPerSecChanged() {}

    /**
     * This executor provides intra-merge threads for parallel execution of merge tasks. It provides a
     * limited number of threads to execute merge tasks. In particular, if the number of
     * `mergeThreads` is equal to `maxThreadCount`, then the executor will execute the merge task in
     * the calling thread.
     */
    inner class CachedExecutor : Executor {
        @OptIn(ExperimentalAtomicApi::class)
        private val activeCount: AtomicInteger = AtomicInteger(0)
        private val executor: ThreadPoolExecutor = ThreadPoolExecutor(
            corePoolSize = 0,
            maximumPoolSize = 1024,
            keepAliveTime = 1L,
            unit = TimeUnit.MINUTES,
            workQueue = /*SynchronousQueue*/LinkedBlockingQueue() // TODO implement SynchronousQueue if needed
        )

        fun shutdown() {
            executor.shutdown()
        }

        @OptIn(ExperimentalAtomicApi::class)
        override fun execute(command: Runnable) {
            val isThreadAvailable: Boolean
            // we need to check if a thread is available before submitting the task to the executor
            // synchronize on CMS to get an accurate count of current threads

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this@ConcurrentMergeScheduler) {
                val max = maxThreadCount - mergeThreads.size - 1
                val value: Int = activeCount.get()
                if (value < max) {
                    activeCount.incrementAndFetch()
                    assert(activeCount.get() > 0) { "active count must be greater than 0 after increment" }
                    isThreadAvailable = true
                } else {
                    isThreadAvailable = false
                }
            //}
            if (isThreadAvailable) {
                executor.execute {
                    try {
                        command.run()
                    } catch (exc: Throwable) {
                        if (!suppressExceptions) {
                            // suppressExceptions is normally only set during
                            // testing.
                            handleMergeException(exc)
                        }
                    } finally {
                        activeCount.decrementAndFetch()
                        assert(activeCount.get() >= 0) { "unexpected negative active count" }
                    }
                }
            } else {
                command.run()
            }
        }
    }

    companion object {
        /**
         * Dynamic default for `maxThreadCount` and `maxMergeCount`, based on CPU core count.
         * `maxThreadCount` is set to `max(1, min(4, cpuCoreCount/2))`. `maxMergeCount`
         * is set to `maxThreadCount + 5`.
         */
        const val AUTO_DETECT_MERGES_AND_THREADS: Int = -1

        /**
         * Used for testing.
         *
         * @lucene.internal
         */
        const val DEFAULT_CPU_CORE_COUNT_PROPERTY: String = "lucene.cms.override_core_count"

        /** Floor for IO write rate limit (we will never go any lower than this)  */
        private const val MIN_MERGE_MB_PER_SEC = 5.0

        /** Ceiling for IO write rate limit (we will never go any higher than this)  */
        private const val MAX_MERGE_MB_PER_SEC = 10240.0

        /** Initial value for IO write rate limit when doAutoIOThrottle is true  */
        private const val START_MB_PER_SEC = 20.0

        /**
         * Merges below this size are not counted in the maxThreadCount, i.e. they can freely run in their
         * own thread (up until maxMergeCount).
         */
        private const val MIN_BIG_MERGE_MB = 50.0

        private fun rateToString(mbPerSec: Double): String {
            return when (mbPerSec) {
                0.0 -> "stopped"
                Double.POSITIVE_INFINITY -> "unlimited"
                else -> "$mbPerSec MB/sec"
            }
        }

        private fun nsToSec(ns: Long): Double {
            return ns / TimeUnit.SECONDS.toNanos(1).toDouble()
        }

        private fun bytesToMB(bytes: Long): Double {
            return bytes / 1024.0 / 1024.0
        }

        private fun getSegmentName(merge: OneMerge): String {
            return if (merge.info != null) merge.info!!.info.name else "_na_"
        }

        init {
            TestSecrets.setConcurrentMergeSchedulerAccess { obj: ConcurrentMergeScheduler -> obj.setSuppressExceptions() }
        }
    }
}
