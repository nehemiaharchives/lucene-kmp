@file:OptIn(ExperimentalForeignApi::class)

package org.gnit.lucenekmp.jdkport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlin.concurrent.Volatile
import platform.posix.CLOCK_REALTIME
import platform.posix.ETIMEDOUT
import platform.posix.clock_gettime
import platform.posix.pthread_cond_destroy
import platform.posix.pthread_cond_init
import platform.posix.pthread_cond_signal
import platform.posix.pthread_cond_t
import platform.posix.pthread_cond_timedwait
import platform.posix.pthread_cond_wait
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.timespec

@Ported(from = "java.util.concurrent.locks.ReentrantLock")
actual class ReentrantLock actual constructor() : Lock {
    private val stateMutex = nativeHeap.alloc<pthread_mutex_t>()
    private val releasedCondition = nativeHeap.alloc<pthread_cond_t>()

    @Volatile
    private var ownerThreadId: Long = NO_OWNER

    @Volatile
    private var holdCount: Int = 0

    init {
        check(pthread_mutex_init(stateMutex.ptr, null) == 0) { "pthread_mutex_init failed" }
        check(pthread_cond_init(releasedCondition.ptr, null) == 0) { "pthread_cond_init failed" }
    }

    actual override fun tryLock(): Boolean {
        val current = currentThreadId()
        withStateMutex {
            if (ownerThreadId == current) {
                val next = holdCount + 1
                if (next < 0) {
                    throw Error("Maximum lock count exceeded")
                }
                holdCount = next
                return true
            }
            if (ownerThreadId == NO_OWNER) {
                ownerThreadId = current
                holdCount = 1
                return true
            }
            return false
        }
    }

    actual override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        throw NotImplementedError("Not yet implemented")
    }

    actual override fun lock() {
        val current = currentThreadId()
        withStateMutex {
            if (ownerThreadId == current) {
                val next = holdCount + 1
                if (next < 0) {
                    throw Error("Maximum lock count exceeded")
                }
                holdCount = next
                return
            }
            while (ownerThreadId != NO_OWNER) {
                check(pthread_cond_wait(releasedCondition.ptr, stateMutex.ptr) == 0) {
                    "pthread_cond_wait failed"
                }
            }
            ownerThreadId = current
            holdCount = 1
        }
    }

    actual override fun unlock() {
        val current = currentThreadId()
        withStateMutex {
            if (ownerThreadId != current) {
                throw IllegalMonitorStateException()
            }
            val next = holdCount - 1
            if (next == 0) {
                holdCount = 0
                ownerThreadId = NO_OWNER
                check(pthread_cond_signal(releasedCondition.ptr) == 0) { "pthread_cond_signal failed" }
            } else {
                holdCount = next
            }
        }
    }

    actual fun isHeldByCurrentThread(): Boolean {
        return ownerThreadId == currentThreadId()
    }

    actual override fun newCondition(): Condition {
        val lock = this
        return object : BlockingCondition {
            private val waiters = ArrayDeque<Waiter>()

            private fun ensureLocked() {
                check(lock.isHeldByCurrentThread()) { "Lock not held" }
            }

            private fun fullyUnlock(): Int {
                ensureLocked()
                return lock.withStateMutex {
                    val saved = lock.holdCount
                    lock.holdCount = 0
                    lock.ownerThreadId = NO_OWNER
                    check(pthread_cond_signal(lock.releasedCondition.ptr) == 0) {
                        "pthread_cond_signal failed"
                    }
                    saved
                }
            }

            private fun fullyLock(saved: Int) {
                val current = currentThreadId()
                lock.withStateMutex {
                    while (lock.ownerThreadId != NO_OWNER) {
                        check(pthread_cond_wait(lock.releasedCondition.ptr, lock.stateMutex.ptr) == 0) {
                            "pthread_cond_wait failed"
                        }
                    }
                    lock.ownerThreadId = current
                    lock.holdCount = saved
                }
            }

            override suspend fun await() {
                ensureLocked()
                val waiter = Waiter()
                lock.withStateMutex {
                    waiters.addLast(waiter)
                }
                val saved = fullyUnlock()
                try {
                    waiter.await(null)
                } finally {
                    fullyLock(saved)
                    lock.withStateMutex {
                        waiters.remove(waiter)
                    }
                    waiter.close()
                }
            }

            override suspend fun awaitUninterruptibly() {
                await()
            }

            override suspend fun awaitNanos(nanosTimeout: Long): Long {
                ensureLocked()
                if (nanosTimeout <= 0L) return 0L
                val start = System.nanoTime()
                val waiter = Waiter()
                lock.withStateMutex {
                    waiters.addLast(waiter)
                }
                val saved = fullyUnlock()
                val millis = (nanosTimeout / 1_000_000).coerceAtLeast(1L)
                val signalled = try {
                    waiter.await(millis)
                } finally {
                    fullyLock(saved)
                    lock.withStateMutex {
                        waiters.remove(waiter)
                    }
                    waiter.close()
                }
                val elapsed = System.nanoTime() - start
                return if (!signalled) 0L else maxOf(0L, nanosTimeout - elapsed)
            }

            override suspend fun await(time: Long, unit: TimeUnit): Boolean {
                return awaitNanos(unit.toNanos(time)) > 0
            }

            override fun awaitBlocking(time: Long, unit: TimeUnit): Boolean {
                val nanos = unit.toNanos(time)
                if (nanos <= 0L) {
                    return false
                }
                val waiter = Waiter()
                lock.withStateMutex {
                    waiters.addLast(waiter)
                }
                val saved = fullyUnlock()
                val signalled = try {
                    waiter.await((nanos / 1_000_000).coerceAtLeast(1L))
                } finally {
                    fullyLock(saved)
                    lock.withStateMutex {
                        waiters.remove(waiter)
                    }
                    waiter.close()
                }
                return signalled
            }

            @kotlin.time.ExperimentalTime
            override suspend fun awaitUntil(deadline: kotlin.time.Instant): Boolean {
                val nowMs = System.currentTimeMillis()
                val remainingMs = (deadline.toEpochMilliseconds() - nowMs).coerceAtLeast(0)
                return await(remainingMs, TimeUnit.MILLISECONDS)
            }

            override suspend fun signal() {
                ensureLocked()
                val waiter = lock.withStateMutex {
                    while (waiters.isNotEmpty()) {
                        val next = waiters.removeFirst()
                        if (!next.isSignalled()) {
                            return@withStateMutex next
                        }
                    }
                    null
                }
                waiter?.signal()
            }

            override suspend fun signalAll() {
                ensureLocked()
                val pending = lock.withStateMutex {
                    val result = waiters.filterNot { it.isSignalled() }
                    waiters.clear()
                    result
                }
                pending.forEach { it.signal() }
            }

            override fun signalAllBlocking() {
                val pending = lock.withStateMutex {
                    val result = waiters.filterNot { it.isSignalled() }
                    waiters.clear()
                    result
                }
                pending.forEach { it.signal() }
            }
        }
    }

    actual override fun lockInterruptibly() {
        throw NotImplementedError("Not yet implemented")
    }

    private inline fun <T> withStateMutex(action: () -> T): T {
        check(pthread_mutex_lock(stateMutex.ptr) == 0) { "pthread_mutex_lock failed" }
        try {
            return action()
        } finally {
            check(pthread_mutex_unlock(stateMutex.ptr) == 0) { "pthread_mutex_unlock failed" }
        }
    }

    private companion object {
        private const val NO_OWNER: Long = -1L
    }

    private class Waiter {
        private val mutex = nativeHeap.alloc<pthread_mutex_t>()
        private val condition = nativeHeap.alloc<pthread_cond_t>()

        @Volatile
        private var signalled = false

        init {
            check(pthread_mutex_init(mutex.ptr, null) == 0) { "pthread_mutex_init failed" }
            check(pthread_cond_init(condition.ptr, null) == 0) { "pthread_cond_init failed" }
        }

        fun isSignalled(): Boolean = signalled

        fun signal() {
            check(pthread_mutex_lock(mutex.ptr) == 0) { "pthread_mutex_lock failed" }
            try {
                signalled = true
                check(pthread_cond_signal(condition.ptr) == 0) { "pthread_cond_signal failed" }
            } finally {
                check(pthread_mutex_unlock(mutex.ptr) == 0) { "pthread_mutex_unlock failed" }
            }
        }

        fun await(timeoutMillis: Long?): Boolean {
            check(pthread_mutex_lock(mutex.ptr) == 0) { "pthread_mutex_lock failed" }
            try {
                if (timeoutMillis == null) {
                    while (!signalled) {
                        check(pthread_cond_wait(condition.ptr, mutex.ptr) == 0) {
                            "pthread_cond_wait failed"
                        }
                    }
                    return true
                }
                if (!signalled) {
                    memScoped {
                        val deadline = alloc<timespec>()
                        check(clock_gettime(CLOCK_REALTIME.convert(), deadline.ptr) == 0) {
                            "clock_gettime failed"
                        }
                        deadline.addMillis(timeoutMillis)
                        while (!signalled) {
                            val rc = pthread_cond_timedwait(condition.ptr, mutex.ptr, deadline.ptr)
                            if (rc == ETIMEDOUT) {
                                return signalled
                            }
                            check(rc == 0) { "pthread_cond_timedwait failed: $rc" }
                        }
                    }
                }
                return signalled
            } finally {
                check(pthread_mutex_unlock(mutex.ptr) == 0) { "pthread_mutex_unlock failed" }
            }
        }

        fun close() {
            check(pthread_cond_destroy(condition.ptr) == 0) { "pthread_cond_destroy failed" }
            check(pthread_mutex_destroy(mutex.ptr) == 0) { "pthread_mutex_destroy failed" }
            nativeHeap.free(condition.rawPtr)
            nativeHeap.free(mutex.rawPtr)
        }

        private fun timespec.addMillis(millis: Long) {
            val totalNanos = tv_nsec + (millis % 1000L) * 1_000_000L
            tv_sec += millis / 1000L + totalNanos / 1_000_000_000L
            tv_nsec = totalNanos % 1_000_000_000L
        }
    }
}
