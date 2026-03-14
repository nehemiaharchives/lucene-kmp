package org.gnit.lucenekmp.jdkport

import java.util.Date
import java.util.concurrent.TimeUnit as JavaTimeUnit
import java.lang.IllegalMonitorStateException as JavaIllegalMonitorStateException
import java.util.concurrent.locks.ReentrantLock as JavaReentrantLock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Ported(from = "java.util.concurrent.locks.ReentrantLock")
actual class ReentrantLock actual constructor() : Lock {
    private val delegate = JavaReentrantLock()

    actual override fun tryLock(): Boolean {
        return delegate.tryLock()
    }

    actual override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        return delegate.tryLock(time, unit.toJavaTimeUnit())
    }

    actual override fun lock() {
        delegate.lock()
    }

    actual override fun unlock() {
        try {
            delegate.unlock()
        } catch (_: JavaIllegalMonitorStateException) {
            throw IllegalMonitorStateException()
        }
    }

    actual fun isHeldByCurrentThread(): Boolean {
        return delegate.isHeldByCurrentThread
    }

    actual override fun newCondition(): Condition {
        val condition = delegate.newCondition()
        return object : Condition {
            private fun ensureLocked() {
                check(delegate.isHeldByCurrentThread) { "Lock not held" }
            }

            override suspend fun await() {
                ensureLocked()
                condition.await()
            }

            override suspend fun awaitUninterruptibly() {
                ensureLocked()
                condition.awaitUninterruptibly()
            }

            override suspend fun awaitNanos(nanosTimeout: Long): Long {
                ensureLocked()
                return condition.awaitNanos(nanosTimeout)
            }

            override suspend fun await(time: Long, unit: TimeUnit): Boolean {
                ensureLocked()
                return condition.await(time, unit.toJavaTimeUnit())
            }

            @OptIn(ExperimentalTime::class)
            override suspend fun awaitUntil(deadline: Instant): Boolean {
                ensureLocked()
                return condition.awaitUntil(Date(deadline.toEpochMilliseconds()))
            }

            override suspend fun signal() {
                ensureLocked()
                condition.signal()
            }

            override suspend fun signalAll() {
                ensureLocked()
                condition.signalAll()
            }
        }
    }

    actual override fun lockInterruptibly() {
        delegate.lockInterruptibly()
    }
}

private fun TimeUnit.toJavaTimeUnit(): JavaTimeUnit {
    return when (this) {
        TimeUnit.NANOSECONDS -> JavaTimeUnit.NANOSECONDS
        TimeUnit.MICROSECONDS -> JavaTimeUnit.MICROSECONDS
        TimeUnit.MILLISECONDS -> JavaTimeUnit.MILLISECONDS
        TimeUnit.SECONDS -> JavaTimeUnit.SECONDS
        TimeUnit.MINUTES -> JavaTimeUnit.MINUTES
        TimeUnit.HOURS -> JavaTimeUnit.HOURS
        TimeUnit.DAYS -> JavaTimeUnit.DAYS
    }
}
