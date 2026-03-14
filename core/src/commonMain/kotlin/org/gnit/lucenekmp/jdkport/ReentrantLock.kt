package org.gnit.lucenekmp.jdkport

/**
 * Port of `java.util.concurrent.locks.ReentrantLock`.
 *
 * Use platform-specific actual implementations so JVM code can rely on the
 * real JDK lock semantics while native continues using the shared coroutine-backed port.
 */
@Ported(from = "java.util.concurrent.locks.ReentrantLock")
expect class ReentrantLock() : Lock {
    override fun tryLock(): Boolean

    override fun tryLock(time: Long, unit: TimeUnit): Boolean

    override fun lock()

    override fun unlock()

    fun isHeldByCurrentThread(): Boolean

    override fun newCondition(): Condition

    override fun lockInterruptibly()
}

inline fun <T> ReentrantLock.withLock(action: () -> T): T {
    lock()
    try {
        return action()
    } finally {
        unlock()
    }
}
