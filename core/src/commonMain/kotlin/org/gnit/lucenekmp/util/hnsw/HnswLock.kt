package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.jdkport.decrementAndGet
import org.gnit.lucenekmp.jdkport.incrementAndGet
import org.gnit.lucenekmp.jdkport.set
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Platform-agnostic lock interface
 */
interface Lock {
    fun lock()
    fun unlock()
}

/**
 * port of java.util.concurrent.locks.ReentrantReadWriteLock
 *
 * Platform-agnostic read-write lock implementation
 */
class ReentrantReadWriteLock {
    @OptIn(ExperimentalAtomicApi::class)
    private val mutex = AtomicInt(0)
    @OptIn(ExperimentalAtomicApi::class)
    private val readCount = AtomicInt(0)
    @OptIn(ExperimentalAtomicApi::class)
    private val writeWaiters = AtomicInt(0)

    fun readLock(): Lock = ReadLock()
    fun writeLock(): Lock = WriteLock()

    private inner class ReadLock : Lock {
        @OptIn(ExperimentalAtomicApi::class)
        override fun lock() {
            while (true) {
                // Wait until there are no writers
                while (mutex.load() != 0) {
                    // Busy wait (not ideal but platform-agnostic)
                }
                readCount.incrementAndGet()
                // If a writer appeared since we checked, retry
                if (mutex.load() != 0) {
                    readCount.decrementAndGet()
                    continue
                }
                break
            }
        }

        @OptIn(ExperimentalAtomicApi::class)
        override fun unlock() {
            readCount.decrementAndGet()
        }
    }

    private inner class WriteLock : Lock {
        @OptIn(ExperimentalAtomicApi::class)
        override fun lock() {
            writeWaiters.incrementAndGet()
            while (true) {
                // Try to acquire the write lock
                if (mutex.compareAndSet(0, 1)) {
                    // Wait until all readers are done
                    while (readCount.load() > 0) {
                        // Busy wait
                    }
                    break
                }
                // Busy wait
            }
            writeWaiters.decrementAndGet()
        }

        @OptIn(ExperimentalAtomicApi::class)
        override fun unlock() {
            mutex.set(0)
        }
    }
}


/**
 * Provide (read-and-write) striped locks for access to nodes of an [OnHeapHnswGraph]. For use
 * by [HnswConcurrentMergeBuilder] and its HnswGraphBuilders.
 */
class HnswLock {
    private val locks: Array<ReentrantReadWriteLock> = Array(NUM_LOCKS) { ReentrantReadWriteLock() }

    fun read(level: Int, node: Int): Lock {
        val lockid = hash(level, node) % NUM_LOCKS
        val lock: Lock = locks[lockid].readLock()
        lock.lock()
        return lock
    }

    fun write(level: Int, node: Int): Lock {
        val lockid = hash(level, node) % NUM_LOCKS
        val lock: Lock = locks[lockid].writeLock()
        lock.lock()
        return lock
    }

    companion object {
        private const val NUM_LOCKS = 512
        private fun hash(v1: Int, v2: Int): Int {
            return v1 * 31 + v2
        }
    }
}
