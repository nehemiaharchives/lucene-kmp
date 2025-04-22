package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.get() = this.load()

@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.set(value: Int) = this.store(value)

@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.incrementAndGet() = this.incrementAndFetch()

@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.decrementAndGet() = this.decrementAndFetch()

/**
 * Atomically updates (with memory effects as specified by [ ][VarHandle.compareAndSet]) the current value with the results of
 * applying the given function to the current and given values,
 * returning the updated value. The function should be
 * side-effect-free, since it may be re-applied when attempted
 * updates fail due to contention among threads.  The function is
 * applied with the current value as its first argument, and the
 * given update as the second argument.
 *
 * @param x the update value
 * @param accumulatorFunction a side-effect-free function of two arguments
 * @return the updated value
 * @since 1.8
 */
/*fun accumulateAndGet(
    x: Int,
    accumulatorFunction: java.util.function.IntBinaryOperator
): Int {
    var prev: Int = get()
    var next = 0
    var haveNext = false
    while (true) {
        if (!haveNext) next = accumulatorFunction.applyAsInt(prev, x)
        if (weakCompareAndSetVolatile(prev, next)) return next
        haveNext = (prev == (get().also { prev = it }))
    }
}*/
// following function is porting above java specific function to kotlin common.
@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.accumulateAndGet(
    x: Int,
    accumulatorFunction: (Int, Int) -> Int
): Int {
    var prev: Int = get()
    var next = 0
    var haveNext = false
    while (true) {
        if (!haveNext) next = accumulatorFunction(prev, x)
        if (weakCompareAndSetVolatile(prev, next)) return next
        haveNext = (prev == (get().also { prev = it }))
    }
}

/**
 * Possibly atomically sets the value to `newValue` if
 * the current value `== expectedValue`.
 *
 * @param expectedValue the expected value
 * @param newValue the new value
 * @return `true` if successful
 */
@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.weakCompareAndSetVolatile(expectedValue: Int, newValue: Int): Boolean {
    // Kotlin doesn't have a direct equivalent to Java's weakCompareAndSet,
    // so we use compareAndSet which provides stronger guarantees
    return this.compareAndSet(expectedValue, newValue)
}

@OptIn(ExperimentalAtomicApi::class)
typealias AtomicInteger = AtomicInt
