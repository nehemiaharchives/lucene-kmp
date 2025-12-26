package org.gnit.lucenekmp.util

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private object ThreadLocalStorage {
    val map = mutableMapOf<CloseableThreadLocal<*>, Any?>()
}

/**
 * Native implementation: per-thread storage via a thread-local map.
 */
actual class CloseableThreadLocal<T> actual constructor() : AutoCloseable {
    @Suppress("UNCHECKED_CAST")
    actual fun get(): T? {
        return ThreadLocalStorage.map[this] as T?
    }

    actual fun set(value: T?) {
        if (value == null) {
            ThreadLocalStorage.map.remove(this)
        } else {
            ThreadLocalStorage.map[this] = value
        }
    }

    actual override fun close() {
        ThreadLocalStorage.map.remove(this)
    }
}
