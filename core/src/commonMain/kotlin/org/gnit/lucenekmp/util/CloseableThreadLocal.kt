package org.gnit.lucenekmp.util

/**
 * Thread-local storage with a close hook to release references.
 */
expect class CloseableThreadLocal<T> : AutoCloseable {
    constructor()
    fun get(): T?
    fun set(value: T?)
    override fun close()
}
