package org.gnit.lucenekmp.store

import okio.IOException

/**
 * Use this [LockFactory] to disable locking entirely. This is a singleton, you have to use [INSTANCE].
 *
 * @see LockFactory
 */
class NoLockFactory private constructor() : LockFactory() {
    override fun obtainLock(dir: Directory, lockName: String): Lock {
        return SINGLETON_LOCK
    }

    private class NoLock : Lock() {
        override fun close() {
            // no-op
        }

        @Throws(IOException::class)
        override fun ensureValid() {
            // no-op
        }

        override fun toString(): String {
            return "NoLock"
        }
    }

    companion object {
        /** The singleton instance */
        val INSTANCE: NoLockFactory = NoLockFactory()

        // visible for tests
        internal val SINGLETON_LOCK: Lock = NoLock()
    }
}

