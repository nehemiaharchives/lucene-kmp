package org.gnit.lucenekmp.store

import kotlinx.coroutines.delay
import okio.IOException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException

/**
 * Abstract base class to rate limit IO. Typically implementations are shared across multiple
 * IndexInputs or IndexOutputs (for example those involved all merging). Those IndexInputs and
 * IndexOutputs would call [.pause] whenever the have read or written more than [ ][.getMinPauseCheckBytes] bytes.
 */
abstract class RateLimiter {
    /** The current MB per second rate limit.  */
    /**
     * Sets an updated MB per second rate limit. A subclass is allowed to perform dynamic updates of
     * the rate limit during use.
     */
    abstract var mBPerSec: Double

    /**
     * Pauses, if necessary, to keep the instantaneous IO rate at or below the target.
     *
     *
     * Note: the implementation is thread-safe
     *
     * @return the pause time in nano seconds
     */
    abstract suspend fun pause(bytes: Long): Long

    /**
     * How many bytes caller should add up itself before invoking [.pause]. NOTE: The value
     * returned by this method may change over time and is not guaranteed to be constant throughout
     * the lifetime of the RateLimiter. Users are advised to refresh their local values with calls to
     * this method to ensure consistency.
     */
    abstract val minPauseCheckBytes: Long

    /** Simple class to rate limit IO.  */
    class SimpleRateLimiter(mbPerSec: Double) : RateLimiter() {
        @Volatile
        override var mBPerSec: Double = mbPerSec
            set(value) {
                field = value
                minPauseCheckBytes = ((MIN_PAUSE_CHECK_MSEC / 1000.0) * value * 1024 * 1024).toLong()
            }

        @Volatile
        override var minPauseCheckBytes: Long = 0
        private var lastNS: Long

        /** mbPerSec is the MB/sec max IO rate  */
        init {
            this.mBPerSec = mbPerSec
            lastNS = System.nanoTime()
        }

        /**
         * Pauses, if necessary, to keep the instantaneous IO rate at or below the target. Be sure to
         * only call this method when bytes > [.getMinPauseCheckBytes], otherwise it will pause
         * way too long!
         *
         * @return the pause time in nano seconds
         */
        override suspend fun pause(bytes: Long): Long {
            val startNS: Long = System.nanoTime()
            val secondsToPause = (bytes / 1024.0 / 1024.0) / mBPerSec

            // Inline syncAndGetTargetNS logic
            val computedTargetNS = lastNS + (1_000_000_000 * secondsToPause).toLong()
            val targetNS: Long
            if (startNS >= computedTargetNS) {
                lastNS = startNS
                targetNS = startNS
            } else {
                lastNS = computedTargetNS
                targetNS = computedTargetNS
            }

            var curNS = startNS

            while (true) {
                val pauseNS = targetNS - curNS
                if (pauseNS > 0) {
                    try {
                        val pauseMillis = pauseNS / 1_000_000
                        val pauseNanos = pauseNS % 1_000_000
                        if (pauseMillis > 0 || pauseNanos > 0) {
                            delay(pauseMillis + if (pauseNanos > 0) 1 else 0)
                        }
                    } catch (ce: CancellationException) {
                        throw ThreadInterruptedException(ce)
                    }
                    curNS = System.nanoTime()
                    continue
                }
                break
            }

            return curNS - startNS
        }

        companion object {
            private const val MIN_PAUSE_CHECK_MSEC = 5
        }
    }
}
