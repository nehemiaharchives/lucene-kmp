package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Lock
import org.gnit.lucenekmp.jdkport.incrementAndGet
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch


/** A [ConcurrentApproximatePriorityQueue] of [Lock] objects.  */
internal class LockableConcurrentApproximatePriorityQueue<T : Lock> {
    private val queue: ConcurrentApproximatePriorityQueue<T>
    @OptIn(ExperimentalAtomicApi::class)
    private val addAndUnlockCounter: AtomicInteger = AtomicInteger(0)

    constructor(concurrency: Int) {
        this.queue = ConcurrentApproximatePriorityQueue<T>(concurrency)
    }

    constructor() {
        this.queue = ConcurrentApproximatePriorityQueue<T>()
    }

    /**
     * Lock an entry, and poll it from the queue, in that order. If no entry can be found and locked,
     * `null` is returned.
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun lockAndPoll(): T? {
        var addAndUnlockCount: Int
        do {
            addAndUnlockCount = addAndUnlockCounter.load()
            val entry: T? =
                queue.poll { obj: Lock -> obj.tryLock() }
            if (entry != null) {
                return entry
            }
            // If an entry has been added to the queue in the meantime, try again.
        } while (addAndUnlockCount != addAndUnlockCounter.load())

        return null
    }

    /** Remove an entry from the queue.  */
    fun remove(o: T): Boolean {
        return queue.remove(o)
    }

    // Only used for assertions
    fun contains(o: T): Boolean {
        return queue.contains(o)
    }

    /** Add an entry to the queue and unlock it, in that order.  */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun addAndUnlock(entry: T, weight: Long) {
        queue.add(entry, weight)
        entry.unlock()
        addAndUnlockCounter.incrementAndFetch()
    }
}
