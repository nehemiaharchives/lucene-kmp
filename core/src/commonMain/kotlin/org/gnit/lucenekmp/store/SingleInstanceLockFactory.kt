@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.Thread
import okio.IOException
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt

/** Implements a [LockFactory] that provides locks scoped to a single JVM. */
class SingleInstanceLockFactory : LockFactory() {
    private val locks: MutableSet<String> = mutableSetOf()
    private val locksGuard = AtomicInt(0)

    @Throws(IOException::class)
    override fun obtainLock(dir: Directory, lockName: String): Lock {
        return withLocksGuard {
            if (locks.add(lockName)) {
                SingleInstanceLock(lockName)
            } else {
                throw LockObtainFailedException("lock instance already obtained: (dir=$dir, lockName=$lockName)")
            }
        }
    }

    private inner class SingleInstanceLock(private val lockName: String) : Lock() {
        @Volatile
        private var closed = false
        private val closeGuard = AtomicInt(0)

        @Throws(IOException::class)
        override fun ensureValid() {
            if (closed) {
                throw AlreadyClosedException("Lock instance already released: $this")
            }
            val held = withLocksGuard {
                locks.contains(lockName)
            }
            if (!held) {
                throw AlreadyClosedException("Lock instance was invalidated from map: $this")
            }
        }

        override fun close() {
            try {
                lockAtomic(closeGuard)
                if (closed) {
                    return
                }
                val removed = withLocksGuard {
                    locks.remove(lockName)
                }
                if (!removed) {
                    throw AlreadyClosedException("Lock was already released: $this")
                }
            } finally {
                closed = true
                unlockAtomic(closeGuard)
            }
        }

        override fun toString(): String {
            return super.toString() + ": " + lockName
        }
    }

    private inline fun <T> withLocksGuard(action: () -> T): T {
        lockAtomic(locksGuard)
        try {
            return action()
        } finally {
            unlockAtomic(locksGuard)
        }
    }

    private fun lockAtomic(guard: AtomicInt) {
        while (!guard.compareAndSet(0, 1)) {
            Thread.yield()
        }
    }

    private fun unlockAtomic(guard: AtomicInt) {
        guard.store(0)
    }
}
