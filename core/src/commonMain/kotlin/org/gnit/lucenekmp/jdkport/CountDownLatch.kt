package org.gnit.lucenekmp.jdkport

/**
 * port of java.util.concurrent.CountDownLatch
 * currently only have placeholder implementation to make compile pass
 *
 * TODO later we will implement or refactor with kotlin coroutines
 */
import kotlinx.coroutines.Job
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// TODO this is a very small subset of the original JDK CountDownLatch API
// in order to make the ported code compile. Proper waiting semantics are
// not implemented yet.
import org.gnit.lucenekmp.jdkport.TimeUnit

class CountDownLatch(count: Int) {

    @OptIn(ExperimentalAtomicApi::class)
    private val count = AtomicInt(count)

    private val completed = Job()

    init {
        require(count >= 0) { "count < 0" }
        if (count == 0) {
            completed.complete()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun getCount(): Long {
        return count.load().toLong()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun countDown() {
        while (true) {
            val c = count.load()
            if (c == 0) return
            if (count.compareAndSet(c, c - 1)) {
                if (c - 1 == 0) {
                    completed.complete()
                }
                return
            }
        }
    }

    /**
     * Waits until the latch has counted down to zero. This placeholder
     * implementation does not block and simply returns immediately.
     */
    fun await() {
        // no-op for now
    }

    /**
     * Waits until the latch has counted down to zero, or the specified
     * waiting time elapses. This placeholder always returns whether the
     * latch has already counted down.
     */
    fun await(timeout: Long, unit: TimeUnit): Boolean {
        // ignore timeout and just check count
        return getCount() == 0L
    }
}
