package org.gnit.lucenekmp.index

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.index.MergePolicy.OneMergeProgress
import org.gnit.lucenekmp.index.MergePolicy.OneMergeProgress.PauseReason
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.updateAndGet
import org.gnit.lucenekmp.store.RateLimiter
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.min
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * This is the [RateLimiter] that [IndexWriter] assigns to each running merge, to give
 * [MergeScheduler]s ionice like control.
 *
 * @lucene.internal
 */
class MergeRateLimiter(private val mergeProgress: OneMergeProgress) :
    RateLimiter() {
    @Volatile
    private var mbPerSec = 0.0

    @Volatile
    override var minPauseCheckBytes: Long = 0
        private set

    @OptIn(ExperimentalAtomicApi::class)
    private val lastNS: AtomicLong = AtomicLong(0)

    @OptIn(ExperimentalAtomicApi::class)
    private val totalBytesWritten: AtomicLong = AtomicLong(0)

    // Initially no IO limit; use setter here so minPauseCheckBytes is set:

    override var mBPerSec: Double
        get() = mbPerSec
        set(mbPerSec) {

            // TODO synchronized is not supported in KMP, need to think what to do here
            // Synchronized to make updates to mbPerSec and minPauseCheckBytes atomic.
            //synchronized(this) {
            // 0.0 is allowed: it means the merge is paused
            require(!(mbPerSec < 0.0)) { "mbPerSec must be positive; got: $mbPerSec" }
            this.mbPerSec = mbPerSec

            // NOTE: Double.POSITIVE_INFINITY casts to Long.MAX_VALUE
            this.minPauseCheckBytes =
                min((1024 * 1024).toLong(), ((MIN_PAUSE_CHECK_MSEC / 1000.0) * mbPerSec * 1024 * 1024).toLong())
            assert(minPauseCheckBytes >= 0)
            //}

            runBlocking{ mergeProgress.wakeup() }
        }

    /** Sole constructor.  */
    init {
        this.mBPerSec = Double.Companion.POSITIVE_INFINITY
    }

    /** Returns total bytes written by this merge.  */
    @OptIn(ExperimentalAtomicApi::class)
    fun getTotalBytesWritten(): Long {
        return totalBytesWritten.load()
    }

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun pause(bytes: Long): Long {
        totalBytesWritten.addAndFetch(bytes)

        // While loop because we may wake up and check again when our rate limit
        // is changed while we were pausing:
        var paused: Long = 0
        var delta: Long
        while ((maybePause(bytes).also { delta = it }) >= 0) {
            // Keep waiting.
            paused += delta
        }

        return paused
    }

    val totalStoppedNS: Long
        /** Total NS merge was stopped.  */
        get() = mergeProgress.pauseTimes[PauseReason.STOPPED]!!

    val totalPausedNS: Long
        /** Total NS merge was paused to rate limit IO.  */
        get() = mergeProgress.pauseTimes[PauseReason.PAUSED]!!

    /**
     * Returns the number of nanoseconds spent in a paused state or `-1` if no pause was
     * applied. If the thread needs pausing, this method delegates to the linked [ ].
     */
    @OptIn(ExperimentalAtomicApi::class, ExperimentalTime::class)
    private suspend fun maybePause(bytes: Long): Long {
        // Now is a good time to abort the merge:
        if (mergeProgress.isAborted) {
            throw MergePolicy.MergeAbortedException("Merge aborted.")
        }

        val rate = mbPerSec // read from volatile rate once.
        val secondsToPause = (bytes / 1024.0 / 1024.0) / rate

        val curPauseNSSetter = AtomicLong(0)
        // While we use updateAndGet to avoid a race condition between multiple threads, this doesn't
        // mean
        // that multiple threads will end up getting paused at the same time.
        // We only pause the calling thread. This means if the upstream caller (e.g.
        // ConcurrentMergeScheduler)
        // is using multiple intra-threads, they will all be paused independently.
        lastNS.updateAndGet { last: Long ->
            val curNS: Long = TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds
            // Time we should sleep until; this is purely instantaneous
            // rate (just adds seconds onto the last time we had paused to);
            // maybe we should also offer decayed recent history one?
            val targetNS = last + (1000000000 * secondsToPause).toLong()
            val curPauseNS = targetNS - curNS
            // We don't bother with thread pausing if the pause is smaller than 2 msec.
            if (curPauseNS <= MIN_PAUSE_NS) {
                // Set to curNS, not targetNS, to enforce the instant rate, not
                // the "averaged over all history" rate:
                curPauseNSSetter.store(0)
                return@updateAndGet curNS
            }
            curPauseNSSetter.store(curPauseNS)
            last
        }

        if (curPauseNSSetter.load() == 0L) {
            return -1
        }
        var curPauseNS: Long = curPauseNSSetter.load()
        // Defensive: don't sleep for too long; the loop above will call us again if
        // we should keep sleeping and the rate may be adjusted in between.
        if (curPauseNS > MAX_PAUSE_NS) {
            curPauseNS = MAX_PAUSE_NS
        }

        val start: Long = TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds
        try {
            mergeProgress.pauseNanos(
                curPauseNS,
                if (rate == 0.0) PauseReason.STOPPED else PauseReason.PAUSED
            ) { rate == mbPerSec }
        } catch (ie: CancellationException) {
            throw ThreadInterruptedException(ie)
        }
        return TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds - start
    }

    companion object {
        private const val MIN_PAUSE_CHECK_MSEC = 25

        private val MIN_PAUSE_NS: Long = TimeUnit.MILLISECONDS.toNanos(2)
        private val MAX_PAUSE_NS: Long = TimeUnit.MILLISECONDS.toNanos(250)
    }
}
