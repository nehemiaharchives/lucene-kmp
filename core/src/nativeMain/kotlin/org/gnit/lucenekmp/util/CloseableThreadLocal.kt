package org.gnit.lucenekmp.util

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private object ThreadLocalStorage {
    val map = mutableMapOf<CloseableThreadLocal<*>, Any?>()
}

/**
 * Native implementation: per-thread storage via a thread-local map.
 */
actual open class CloseableThreadLocal<T> actual constructor() : AutoCloseable {
    private object NullSentinel

    @Suppress("UNCHECKED_CAST")
    actual fun get(): T? {
        val map = ThreadLocalStorage.map
        if (!map.containsKey(this)) {
            val iv = initialValue()
            if (iv != null) {
                set(iv)
                return iv
            }
            return null
        }
        val stored = map[this]
        if (stored === NullSentinel) {
            return null
        }
        return stored as T?
    }

    actual fun set(value: T?) {
        ThreadLocalStorage.map[this] = value ?: NullSentinel
    }

    actual open fun initialValue(): T? {
        return null
    }

    actual override fun close() {
        ThreadLocalStorage.map.remove(this)
    }
}
