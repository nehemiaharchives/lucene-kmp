package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ReentrantLock
import okio.IOException
import kotlin.concurrent.Volatile

/** Implements a [LockFactory] that provides locks scoped to a single JVM. */
class SingleInstanceLockFactory : LockFactory() {
    private val locks: MutableSet<String> = mutableSetOf()
    private val locksMutex = ReentrantLock()

    @Throws(IOException::class)
    override fun obtainLock(dir: Directory, lockName: String): Lock {
        return try {
            locksMutex.lock()
            if (locks.add(lockName)) {
                SingleInstanceLock(lockName)
            } else {
                throw LockObtainFailedException("lock instance already obtained: (dir=$dir, lockName=$lockName)")
            }
        } finally {
            locksMutex.unlock()
        }
    }

    private inner class SingleInstanceLock(private val lockName: String) : Lock() {
        @Volatile
        private var closed = false

        @Throws(IOException::class)
        override fun ensureValid() {
            if (closed) {
                throw AlreadyClosedException("Lock instance already released: $this")
            }
            val held = try {
                locksMutex.lock()
                locks.contains(lockName)
            } finally {
                locksMutex.unlock()
            }
            if (!held) {
                throw AlreadyClosedException("Lock instance was invalidated from map: $this")
            }
        }

        override fun close() {
            if (closed) {
                return
            }
            try {
                val removed = try {
                    locksMutex.lock()
                    locks.remove(lockName)
                } finally {
                    locksMutex.unlock()
                }
                if (!removed) {
                    throw AlreadyClosedException("Lock was already released: $this")
                }
            } finally {
                closed = true
            }
        }

        override fun toString(): String {
            return super.toString() + ": " + lockName
        }
    }
}
