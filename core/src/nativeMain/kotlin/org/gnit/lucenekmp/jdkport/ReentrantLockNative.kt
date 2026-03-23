package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.Volatile
import platform.posix.sched_yield

@OptIn(ExperimentalAtomicApi::class)
@Ported(from = "java.util.concurrent.locks.ReentrantLock")
actual class ReentrantLock actual constructor() : Lock {
    private val state = AtomicInt(0)

    @Volatile
    private var ownerThreadId: Long = NO_OWNER

    @Volatile
    private var holdCount: Int = 0

    actual override fun tryLock(): Boolean {
        val current = currentThreadId()
        if (ownerThreadId == current) {
            val next = holdCount + 1
            if (next < 0) {
                throw Error("Maximum lock count exceeded")
            }
            holdCount = next
            return true
        }
        val locked = state.compareAndSet(0, 1)
        if (locked) {
            ownerThreadId = current
            holdCount = 1
        }
        return locked
    }

    actual override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        throw NotImplementedError("Not yet implemented")
    }

    actual override fun lock() {
        val current = currentThreadId()
        if (ownerThreadId == current) {
            val next = holdCount + 1
            if (next < 0) {
                throw Error("Maximum lock count exceeded")
            }
            holdCount = next
            return
        }
        while (!state.compareAndSet(0, 1)) {
            sched_yield()
        }
        ownerThreadId = current
        holdCount = 1
    }

    actual override fun unlock() {
        val current = currentThreadId()
        if (ownerThreadId != current) {
            throw IllegalMonitorStateException()
        }
        val next = holdCount - 1
        if (next == 0) {
            holdCount = 0
            ownerThreadId = NO_OWNER
            state.store(0)
        } else {
            holdCount = next
        }
    }

    actual fun isHeldByCurrentThread(): Boolean {
        return ownerThreadId == currentThreadId()
    }

    actual override fun newCondition(): Condition {
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
                lock.ownerThreadId = NO_OWNER
                lock.state.store(0)
                return saved
            }

            private fun fullyLock(saved: Int) {
                lock.lock()
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
                    waiters.remove(d)
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
                    waiters.remove(d)
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
                while (waiters.isNotEmpty()) {
                    val waiter = waiters.removeFirst()
                    if (!waiter.isCompleted) {
                        waiter.complete(Unit)
                        break
                    }
                }
            }

            override suspend fun signalAll() {
                ensureLocked()
                while (waiters.isNotEmpty()) {
                    val waiter = waiters.removeFirst()
                    if (!waiter.isCompleted) {
                        waiter.complete(Unit)
                    }
                }
            }
        }
    }

    actual override fun lockInterruptibly() {
        throw NotImplementedError("Not yet implemented")
    }

    private companion object {
        private const val NO_OWNER: Long = -1L
    }
}
