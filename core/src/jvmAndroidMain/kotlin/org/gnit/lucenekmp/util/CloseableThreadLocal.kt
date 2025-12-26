package org.gnit.lucenekmp.util

/**
 * JVM/Android implementation backed by java.lang.ThreadLocal.
 */
actual class CloseableThreadLocal<T> actual constructor() : AutoCloseable {
    private val threadLocal = ThreadLocal<T?>()

    actual fun get(): T? {
        return threadLocal.get()
    }

    actual fun set(value: T?) {
        threadLocal.set(value)
    }

    actual override fun close() {
        threadLocal.remove()
    }
}
