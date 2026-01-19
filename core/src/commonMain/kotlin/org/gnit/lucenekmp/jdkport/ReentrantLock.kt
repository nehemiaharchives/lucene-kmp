package org.gnit.lucenekmp.jdkport


/**
 * port of java.util.concurrent.locks.ReentrantLock
 * currently only have placeholder implementation to make compile pass
 *
 * TODO later we will implement or refactor with kotlin coroutines
 */
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

class ReentrantLock: Lock {

    private val mutex = Mutex()
    // Track lock depth for simple condition checks; not owner-specific
    private var holdCount = 0

    override fun tryLock(): Boolean {
        if (holdCount > 0) {
            holdCount++
            return true
        }
        val locked = mutex.tryLock()
        if (locked) {
            holdCount = 1
        }
        return locked
    }

    override fun tryLock(timeout: Long, unit: TimeUnit): Boolean {
        // Not needed in current usage; can be implemented if required
        throw NotImplementedError("Not yet implemented")
    }

    override fun lock() {
        if (holdCount > 0) {
            holdCount++
            return
        }
        runBlocking { mutex.lock() }
        holdCount = 1
    }

    override fun unlock() {
        if (holdCount <= 0) {
            return
        }
        holdCount--
        if (holdCount == 0) {
            mutex.unlock()
        }
    }

    // Best-effort check whether the lock is currently held (by anyone)
    fun isHeldByCurrentThread(): Boolean {
        return holdCount > 0
    }

    override fun newCondition(): Condition {
        val lock = this
        return object : Condition {
            private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

            private fun ensureLocked() {
                check(lock.isHeldByCurrentThread()) { "Lock not held" }
            }

            private fun fullyUnlock(): Int {
                ensureLocked()
                val saved = lock.holdCount
                lock.holdCount = 0
                lock.mutex.unlock()
                return saved
            }

            private fun fullyLock(saved: Int) {
                runBlocking { lock.mutex.lock() }
                lock.holdCount = saved
            }

            override suspend fun await() {
                ensureLocked()
                val d = CompletableDeferred<Unit>()
                waiters.addLast(d)
                val saved = fullyUnlock()
                try {
                    d.await()
                } finally {
                    fullyLock(saved)
                }
            }

            override suspend fun awaitUninterruptibly() {
                await()
            }

            override suspend fun awaitNanos(nanosTimeout: Long): Long {
                ensureLocked()
                if (nanosTimeout <= 0L) return 0L
                val start = System.nanoTime()
                val d = CompletableDeferred<Unit>()
                waiters.addLast(d)
                val saved = fullyUnlock()
                val millis = (nanosTimeout / 1_000_000).coerceAtLeast(1L)
                val signalled = try {
                    withTimeoutOrNull(millis) { d.await() } != null
                } finally {
                    fullyLock(saved)
                }
                val elapsed = System.nanoTime() - start
                return if (!signalled) 0L else maxOf(0L, nanosTimeout - elapsed)
            }

            override suspend fun await(time: Long, unit: TimeUnit): Boolean {
                return awaitNanos(unit.toNanos(time)) > 0
            }

            @kotlin.time.ExperimentalTime
            override suspend fun awaitUntil(deadline: kotlin.time.Instant): Boolean {
                val nowMs = org.gnit.lucenekmp.jdkport.System.currentTimeMillis()
                val remainingMs = (deadline.toEpochMilliseconds() - nowMs).coerceAtLeast(0)
                return await(remainingMs, TimeUnit.MILLISECONDS)
            }

            override suspend fun signal() {
                ensureLocked()
                val w = if (waiters.isNotEmpty()) waiters.removeFirst() else null
                w?.complete(Unit)
            }

            override suspend fun signalAll() {
                ensureLocked()
                while (waiters.isNotEmpty()) {
                    waiters.removeFirst().complete(Unit)
                }
            }
        }
    }

    override fun lockInterruptibly() {
        throw NotImplementedError("Not yet implemented")
    }
}
