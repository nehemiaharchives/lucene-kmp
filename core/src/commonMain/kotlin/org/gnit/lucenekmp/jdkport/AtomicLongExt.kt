package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Atomically adds the given value to the current value,
 * with memory effects as specified by {@link VarHandle#getAndAdd}.
 *
 * @param delta the value to add
 * @return the updated value
 */
@OptIn(ExperimentalAtomicApi::class)
fun AtomicLong.addAndGet(delta: Long): Long {
    val currentValue = this.load()
    val newValue = currentValue + delta
    this.store(newValue)
    return newValue
}

@OptIn(ExperimentalAtomicApi::class)
fun AtomicLong.decrementAndGet() = this.decrementAndFetch()

@OptIn(ExperimentalAtomicApi::class)
fun AtomicLong.incrementAndGet() = this.incrementAndFetch()

@OptIn(ExperimentalAtomicApi::class)
fun AtomicLong.getAndIncrement() = this.fetchAndIncrement()
