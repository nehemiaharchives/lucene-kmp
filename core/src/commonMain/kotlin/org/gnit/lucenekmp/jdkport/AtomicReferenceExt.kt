package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
fun <T> AtomicReference<T>.get(): T = this.load()
