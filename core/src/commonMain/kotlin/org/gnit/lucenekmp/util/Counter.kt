package org.gnit.lucenekmp.util

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmOverloads


/**
 * Simple counter class
 *
 * @lucene.internal
 * @lucene.experimental
 */
abstract class Counter {
    /**
     * Adds the given delta to the counters current value
     *
     * @param delta the delta to add
     * @return the counters updated value
     */
    abstract fun addAndGet(delta: Long): Long

    /**
     * Returns the counters current value
     *
     * @return the counters current value
     */
    abstract fun get(): Long

    private class SerialCounter : Counter() {
        private var count: Long = 0

        override fun addAndGet(delta: Long): Long {
            return delta.let { count += it; count }
        }

        override fun get(): Long {
            return count
        }
    }

    private class AtomicCounter : Counter() {
        @OptIn(ExperimentalAtomicApi::class)
        private val count: AtomicLong = AtomicLong(0L)

        @OptIn(ExperimentalAtomicApi::class)
        override fun addAndGet(delta: Long): Long {
            return count.addAndFetch(delta)
        }

        @OptIn(ExperimentalAtomicApi::class)
        override fun get(): Long {
            return count.load()
        }
    }

    companion object {
        /**
         * Returns a new counter.
         *
         * @param threadSafe `true` if the returned counter can be used by multiple threads
         * concurrently.
         * @return a new counter.
         */
        /** Returns a new counter. The returned counter is not thread-safe.  */
        @JvmOverloads
        fun newCounter(threadSafe: Boolean = false): Counter {
            return if (threadSafe) AtomicCounter() else SerialCounter()
        }
    }
}
