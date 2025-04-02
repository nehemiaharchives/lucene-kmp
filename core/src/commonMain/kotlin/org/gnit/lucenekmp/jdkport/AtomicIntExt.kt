package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.get() = this.load()

@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.set(value: Int) = this.store(value)

@OptIn(ExperimentalAtomicApi::class)
fun AtomicInt.incrementAndGet() = this.incrementAndFetch()
