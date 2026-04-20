@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Ported(from = "java.util.concurrent.locks.ReentrantLock")
actual class ReentrantLock actual constructor() : Lock {
    private val stateMutex = AtomicInt(0)

    private var ownerThreadId: Long = NO_OWNER
    private var holdCount: Int = 0
    private var releaseGeneration: Long = 0L

    actual override fun tryLock(): Boolean {
        val current = currentThreadId()
        return withStateMutex {
            if (ownerThreadId == current) {
                incrementHoldCount()
                true
            } else if (ownerThreadId == NO_OWNER) {
                ownerThreadId = current
                holdCount = 1
                true
            } else {
                false
            }
        }
    }

    actual override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        if (tryLock()) {
            return true
        }
        val timeoutNanos = unit.toNanos(time)
        if (timeoutNanos <= 0L) {
            return false
        }
        val deadline = System.nanoTime() + timeoutNanos
        while (System.nanoTime() < deadline) {
            if (Thread.interrupted()) {
                throw InterruptedException("lock interrupted")
            }
            if (tryLock()) {
                return true
            }
            Thread.sleep(WAIT_POLL_MILLIS)
        }
        return tryLock()
    }

    actual override fun lock() {
        val current = currentThreadId()
        while (true) {
            val waitGeneration = withStateMutex {
                if (ownerThreadId == current) {
                    incrementHoldCount()
                    return
                }
                if (ownerThreadId == NO_OWNER) {
                    ownerThreadId = current
                    holdCount = 1
                    return
                }
                releaseGeneration
            }
            waitForRelease(waitGeneration)
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
                releaseGeneration++
            } else {
                holdCount = next
            }
        }
    }

    actual fun isHeldByCurrentThread(): Boolean {
        return withStateMutex { ownerThreadId == currentThreadId() }
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
                    lock.releaseGeneration++
                    saved
                }
            }

            private fun fullyLock(saved: Int) {
                val current = currentThreadId()
                while (true) {
                    val waitGeneration = lock.withStateMutex {
                        if (lock.ownerThreadId == NO_OWNER) {
                            lock.ownerThreadId = current
                            lock.holdCount = saved
                            return
                        }
                        lock.releaseGeneration
                    }
                    lock.waitForRelease(waitGeneration)
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
                }
            }

            override suspend fun awaitUninterruptibly() {
                await()
            }

            override suspend fun awaitNanos(nanosTimeout: Long): Long {
                ensureLocked()
                if (nanosTimeout <= 0L) {
                    return 0L
                }
                val start = System.nanoTime()
                val waiter = Waiter()
                lock.withStateMutex {
                    waiters.addLast(waiter)
                }
                val saved = fullyUnlock()
                val signalled = try {
                    waiter.await((nanosTimeout / 1_000_000L).coerceAtLeast(1L))
                } finally {
                    fullyLock(saved)
                    lock.withStateMutex {
                        waiters.remove(waiter)
                    }
                }
                val elapsed = System.nanoTime() - start
                return if (!signalled) 0L else maxOf(0L, nanosTimeout - elapsed)
            }

            override suspend fun await(time: Long, unit: TimeUnit): Boolean {
                return awaitNanos(unit.toNanos(time)) > 0L
            }

            override fun awaitBlocking(time: Long, unit: TimeUnit): Boolean {
                ensureLocked()
                val nanos = unit.toNanos(time)
                if (nanos <= 0L) {
                    return false
                }
                val waiter = Waiter()
                lock.withStateMutex {
                    waiters.addLast(waiter)
                }
                val saved = fullyUnlock()
                return try {
                    waiter.await((nanos / 1_000_000L).coerceAtLeast(1L))
                } finally {
                    fullyLock(saved)
                    lock.withStateMutex {
                        waiters.remove(waiter)
                    }
                }
            }

            @OptIn(ExperimentalTime::class)
            override suspend fun awaitUntil(deadline: Instant): Boolean {
                val remainingMs = (deadline.toEpochMilliseconds() - System.currentTimeMillis()).coerceAtLeast(0L)
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
                signalAllInternal()
            }

            override fun signalAllBlocking() {
                signalAllInternal()
            }

            private fun signalAllInternal() {
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
        if (Thread.interrupted()) {
            throw InterruptedException("lock interrupted")
        }
        val current = currentThreadId()
        while (true) {
            val waitGeneration = withStateMutex {
                if (ownerThreadId == current) {
                    incrementHoldCount()
                    return
                }
                if (ownerThreadId == NO_OWNER) {
                    ownerThreadId = current
                    holdCount = 1
                    return
                }
                releaseGeneration
            }
            if (Thread.interrupted()) {
                throw InterruptedException("lock interrupted")
            }
            waitForRelease(waitGeneration)
        }
    }

    private inline fun <T> withStateMutex(action: () -> T): T {
        while (!stateMutex.compareAndSet(0, 1)) {
            Thread.yield()
        }
        try {
            return action()
        } finally {
            stateMutex.store(0)
        }
    }

    private fun waitForRelease(startGeneration: Long) {
        while (true) {
            val released = withStateMutex { ownerThreadId == NO_OWNER || releaseGeneration != startGeneration }
            if (released) {
                return
            }
            Thread.yield()
        }
    }

    private fun incrementHoldCount() {
        val next = holdCount + 1
        if (next < 0) {
            throw Error("Maximum lock count exceeded")
        }
        holdCount = next
    }

    private companion object {
        private const val NO_OWNER: Long = -1L
        private const val WAIT_POLL_MILLIS: Long = 1L
    }

    private class Waiter {
        private val signalled = AtomicBoolean(false)

        fun isSignalled(): Boolean = signalled.load()

        fun signal() {
            signalled.store(true)
        }

        fun await(timeoutMillis: Long?): Boolean {
            val deadline = timeoutMillis?.let { System.currentTimeMillis() + it }
            while (!isSignalled()) {
                if (Thread.interrupted()) {
                    throw InterruptedException("condition wait interrupted")
                }
                if (deadline != null && System.currentTimeMillis() >= deadline) {
                    return isSignalled()
                }
                Thread.sleep(WAIT_POLL_MILLIS)
            }
            return true
        }
    }
}
