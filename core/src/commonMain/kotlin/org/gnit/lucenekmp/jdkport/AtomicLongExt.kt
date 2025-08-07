package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Atomically updates (with memory effects as specified by {@link
 * VarHandle#compareAndSet}) the current value with the results of
 * applying the given function, returning the updated value. The
 * function should be side-effect-free, since it may be re-applied
 * when attempted updates fail due to contention among threads.
 *
 * @param updateFunction a side-effect-free function
 * @return the updated value
 * @since 1.8
 */
@OptIn(ExperimentalAtomicApi::class)
fun AtomicLong.updateAndGet(updateFunction: (Long) -> Long): Long {
    while (true) {
        val currentValue = this.load()
        val newValue = updateFunction(currentValue)
        if (this.compareAndSet(currentValue, newValue)) {
            return newValue
        }
    }
}
