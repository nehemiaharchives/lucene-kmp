package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
fun AtomicBoolean.getAndSet(newValue: Boolean): Boolean {
    val currentValue = this.load()
    this.store(newValue)
    return currentValue
}