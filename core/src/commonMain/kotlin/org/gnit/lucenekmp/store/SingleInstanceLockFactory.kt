package org.gnit.lucenekmp.store

import okio.IOException
import kotlin.concurrent.Volatile

/** Implements a [LockFactory] that provides locks scoped to a single JVM. */
class SingleInstanceLockFactory : LockFactory() {
    private val locks: MutableSet<String> = mutableSetOf()

    @Throws(IOException::class)
    override fun obtainLock(dir: Directory, lockName: String): Lock {
        return if (locks.add(lockName)) {
            SingleInstanceLock(lockName)
        } else {
            throw LockObtainFailedException("lock instance already obtained: (dir=$dir, lockName=$lockName)")
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
            if (!locks.contains(lockName)) {
                throw AlreadyClosedException("Lock instance was invalidated from map: $this")
            }
        }

        override fun close() {
            if (closed) {
                return
            }
            try {
                if (!locks.remove(lockName)) {
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
