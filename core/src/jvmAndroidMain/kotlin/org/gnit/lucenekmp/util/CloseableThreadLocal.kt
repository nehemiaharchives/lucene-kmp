package org.gnit.lucenekmp.util

/**
 * JVM/Android implementation backed by java.lang.ThreadLocal.
 */
actual open class CloseableThreadLocal<T> actual constructor() : AutoCloseable {
    private object Unset
    private object NullSentinel

    private val threadLocal = object : ThreadLocal<Any?>() {
        override fun initialValue(): Any? = Unset
    }

    actual fun get(): T? {
        val value = threadLocal.get()
        if (value === Unset) {
            val iv = initialValue()
            if (iv != null) {
                set(iv)
                return iv
            }
            return null
        }
        if (value === NullSentinel) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return value as T?
    }

    actual fun set(value: T?) {
        if (value == null) {
            threadLocal.set(NullSentinel)
        } else {
            threadLocal.set(value)
        }
    }

    actual open fun initialValue(): T? {
        return null
    }

    actual override fun close() {
        threadLocal.remove()
    }
}
