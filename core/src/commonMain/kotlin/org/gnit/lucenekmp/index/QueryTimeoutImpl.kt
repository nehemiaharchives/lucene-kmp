package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit

/**
 * An implementation of [QueryTimeout] that can be used by the [ExitableDirectoryReader]
 * class to time out and exit out when a query takes a long time to rewrite.
 */
class QueryTimeoutImpl(timeAllowed: Long) : QueryTimeout {
    /** The local variable to store the time beyond which, the processing should exit.  */
    private var timeoutAt: Long?

    /**
     * Sets the time at which to time out by adding the given timeAllowed to the current time.
     *
     * @param timeAllowed Number of milliseconds after which to time out. Use `Long.MAX_VALUE`
     * to effectively never time out.
     */
    init {
        var timeAllowed = timeAllowed
        if (timeAllowed < 0L) {
            timeAllowed = Long.MAX_VALUE
        }
        timeoutAt = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeAllowed, TimeUnit.MILLISECONDS)
    }

    /**
     * Returns time at which to time out, in nanoseconds relative to the (JVM-specific) epoch for
     * [System.nanoTime], to compare with the value returned by `nanoTime()`.
     */
    fun getTimeoutAt(): Long? {
        return timeoutAt
    }

    /**
     * Return true if [reset] has not been called and the elapsed time has exceeded the time
     * allowed.
     */
    override fun shouldExit(): Boolean {
        return timeoutAt != null && System.nanoTime() - timeoutAt!! > 0
    }

    /** Reset the timeout value.  */
    fun reset() {
        timeoutAt = null
    }

    override fun toString(): String {
        return "timeoutAt: $timeoutAt (System.nanoTime(): ${System.nanoTime()})"
    }
}
