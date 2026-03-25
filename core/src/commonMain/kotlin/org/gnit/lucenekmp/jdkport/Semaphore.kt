package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Minimal common-code port of java.util.concurrent.Semaphore used by tests/utilities.
 */
@Ported(from = "java.util.concurrent.Semaphore")
class Semaphore(permits: Int) {
    @OptIn(ExperimentalAtomicApi::class)
    private val permits = AtomicInt(permits)

    init {
        require(permits >= 0) { "permits must be >= 0; got $permits" }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun release() {
        permits.fetchAndIncrement()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun release(permits: Int) {
        require(permits >= 0) { "permits must be >= 0; got $permits" }
        repeat(permits) {
            release()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun acquire() {
        while (true) {
            val current = permits.load()
            if (current > 0 && permits.compareAndSet(current, current - 1)) {
                return
            }
        }
    }

    fun acquire(permits: Int) {
        require(permits >= 0) { "permits must be >= 0; got $permits" }
        repeat(permits) {
            acquire()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun tryAcquire(): Boolean {
        while (true) {
            val current = permits.load()
            if (current == 0) {
                return false
            }
            if (permits.compareAndSet(current, current - 1)) {
                return true
            }
        }
    }
}
