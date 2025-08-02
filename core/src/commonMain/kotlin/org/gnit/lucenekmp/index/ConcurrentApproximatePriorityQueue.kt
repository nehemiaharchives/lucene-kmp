package org.gnit.lucenekmp.index

import kotlinx.coroutines.Job
import org.gnit.lucenekmp.jdkport.Lock
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.assert
import kotlin.coroutines.coroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min


/**
 * Concurrent version of [ApproximatePriorityQueue], which trades a bit more of ordering for
 * better concurrency by maintaining multiple sub [ApproximatePriorityQueue]s that are locked
 * independently. The number of subs is computed dynamically based on hardware concurrency.
 */
internal class ConcurrentApproximatePriorityQueue<T> @JvmOverloads constructor(concurrency: Int = getConcurrency()) {
    val concurrency: Int
    val locks: Array<Lock>
    val queues: Array<ApproximatePriorityQueue<T>>

    init {
        require(!(concurrency < MIN_CONCURRENCY || concurrency > MAX_CONCURRENCY)) {
            ("concurrency must be in ["
                    + MIN_CONCURRENCY
                    + ", "
                    + MAX_CONCURRENCY
                    + "], got "
                    + concurrency)
        }
        this.concurrency = concurrency
        locks = kotlin.arrayOfNulls<Lock?>(concurrency) as Array<Lock>
        val queues: Array<ApproximatePriorityQueue<T>> =
            kotlin.arrayOfNulls<ApproximatePriorityQueue<T>?>(concurrency) as Array<ApproximatePriorityQueue<T>>
        this.queues = queues
        for (i in 0..<concurrency) {
            queues[i] = ApproximatePriorityQueue<T>()
            locks[i] = ReentrantLock()
        }
    }

    suspend fun add(entry: T, weight: Long) {
        // Seed the order in which to look at entries based on the current thread. This helps distribute
        // entries across queues and gives a bit of thread affinity between entries and threads, which
        // can't hurt.
        val threadHash = coroutineContext[Job]?.hashCode()?.and(0xFFFF)?:0 /*java.lang.Thread.currentThread().hashCode() and 0xFFFF*/
        for (i in 0..<concurrency) {
            val index = (threadHash + i) % concurrency
            val lock: Lock = locks[index]
            val queue: ApproximatePriorityQueue<T> = queues[index]
            if (lock.tryLock()) {
                try {
                    queue.add(entry, weight)
                    return
                } finally {
                    lock.unlock()
                }
            }
        }
        val index = threadHash % concurrency
        val lock: Lock = locks[index]
        val queue: ApproximatePriorityQueue<T> = queues[index]
        lock.lock()
        try {
            queue.add(entry, weight)
        } finally {
            lock.unlock()
        }
    }

    suspend fun poll(predicate: (T) -> Boolean): T? {
        val threadHash = coroutineContext[Job]?.hashCode()?.and(0xFFFF)?:0 /*java.lang.Thread.currentThread().hashCode() and 0xFFFF*/
        for (i in 0..<concurrency) {
            val index = (threadHash + i) % concurrency
            val lock: Lock = locks[index]
            val queue: ApproximatePriorityQueue<T> = queues[index]
            if (lock.tryLock()) {
                try {
                    val entry: T? = queue.poll(predicate)
                    if (entry != null) {
                        return entry
                    }
                } finally {
                    lock.unlock()
                }
            }
        }
        for (i in 0..<concurrency) {
            val index = (threadHash + i) % concurrency
            val lock: Lock = locks[index]
            val queue: ApproximatePriorityQueue<T> = queues[index]
            lock.lock()
            try {
                val entry: T? = queue.poll(predicate)
                if (entry != null) {
                    return entry
                }
            } finally {
                lock.unlock()
            }
        }
        return null
    }

    // Only used for assertions
    fun contains(o: T): Boolean {
        var assertionsAreEnabled = false
        assert(true.also { assertionsAreEnabled = it })
        if (assertionsAreEnabled == false) {
            throw AssertionError("contains should only be used for assertions")
        }

        for (i in 0..<concurrency) {
            val lock: Lock = locks[i]
            val queue: ApproximatePriorityQueue<T> = queues[i]
            lock.lock()
            try {
                if (queue.contains(o)) {
                    return true
                }
            } finally {
                lock.unlock()
            }
        }
        return false
    }

    fun remove(o: T): Boolean {
        for (i in 0..<concurrency) {
            val lock: Lock = locks[i]
            val queue: ApproximatePriorityQueue<T> = queues[i]
            lock.lock()
            try {
                if (queue.remove(o)) {
                    return true
                }
            } finally {
                lock.unlock()
            }
        }
        return false
    }

    companion object {
        const val MIN_CONCURRENCY: Int = 1
        const val MAX_CONCURRENCY: Int = 256

        private fun getConcurrency(): Int {

            // TODO availableProcessors() is not supported in Kotlin Multiplatform, so we use a fixed value for now. need to think how to handle this.
            val coreCount = /*java.lang.Runtime.getRuntime().availableProcessors()*/ 1
            // Aim for ~4 entries per slot when indexing with one thread per CPU core. The trade-off is
            // that if we set the concurrency too high then we'll completely lose the bias towards larger
            // DWPTs. And if we set it too low then we risk seeing contention.
            var concurrency = coreCount / 4
            concurrency = max(MIN_CONCURRENCY, concurrency)
            concurrency = min(MAX_CONCURRENCY, concurrency)
            return concurrency
        }
    }
}
