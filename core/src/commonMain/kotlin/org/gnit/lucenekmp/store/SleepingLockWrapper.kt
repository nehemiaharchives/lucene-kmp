package org.gnit.lucenekmp.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.util.ThreadInterruptedException

/**
 * Directory that wraps another, and that sleeps and retries if obtaining the lock fails.
 *
 * This is not a good idea.
 */
class SleepingLockWrapper : FilterDirectory {
    /**
     * How long [obtainLock] waits, in milliseconds, in between attempts to acquire the lock.
     */
    private val lockWaitTimeout: Long
    private val pollInterval: Long

    /**
     * Create a new SleepingLockFactory
     *
     * @param delegate underlying directory to wrap
     * @param lockWaitTimeout length of time to wait in milliseconds or [LOCK_OBTAIN_WAIT_FOREVER] to retry forever.
     */
    constructor(delegate: Directory, lockWaitTimeout: Long) : this(
        delegate,
        lockWaitTimeout,
        DEFAULT_POLL_INTERVAL
    )

    /**
     * Create a new SleepingLockFactory
     *
     * @param delegate underlying directory to wrap
     * @param lockWaitTimeout length of time to wait in milliseconds or [LOCK_OBTAIN_WAIT_FOREVER] to retry forever.
     * @param pollInterval poll once per this interval in milliseconds until `lockWaitTimeout`
     * is exceeded.
     */
    constructor(delegate: Directory, lockWaitTimeout: Long, pollInterval: Long) : super(delegate) {
        this.lockWaitTimeout = lockWaitTimeout
        this.pollInterval = pollInterval
        if (lockWaitTimeout < 0 && lockWaitTimeout != LOCK_OBTAIN_WAIT_FOREVER) {
            throw IllegalArgumentException(
                "lockWaitTimeout should be LOCK_OBTAIN_WAIT_FOREVER or a non-negative number (got $lockWaitTimeout)"
            )
        }
        if (pollInterval < 0) {
            throw IllegalArgumentException(
                "pollInterval must be a non-negative number (got $pollInterval)"
            )
        }
    }

    @Throws(IOException::class)
    override fun obtainLock(lockName: String): Lock {
        var failureReason: LockObtainFailedException? = null
        val maxSleepCount: Long = lockWaitTimeout / pollInterval
        var sleepCount: Long = 0

        do {
            try {
                return `in`.obtainLock(lockName)
            } catch (failed: LockObtainFailedException) {
                if (failureReason == null) {
                    failureReason = failed
                }
            }
            try {
                runBlocking {
                    delay(pollInterval)
                }
            } catch (ie: CancellationException) {
                throw ThreadInterruptedException(ie)
            }
        } while (sleepCount++ < maxSleepCount || lockWaitTimeout == LOCK_OBTAIN_WAIT_FOREVER)

        // we failed to obtain the lock in the required time
        val reason = "Lock obtain timed out: ${this}: $failureReason"
        throw LockObtainFailedException(reason, failureReason)
    }

    override fun toString(): String {
        return "SleepingLockWrapper(${`in`})"
    }

    companion object {
        /** Pass this lockWaitTimeout to try forever to obtain the lock. */
        const val LOCK_OBTAIN_WAIT_FOREVER: Long = -1

        /**
         * How long [obtainLock] waits, in milliseconds, in between attempts to acquire the lock.
         */
        const val DEFAULT_POLL_INTERVAL: Long = 1000
    }
}
