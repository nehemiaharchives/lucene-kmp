package org.gnit.lucenekmp.store

import okio.IOException


/**
 * An interprocess mutex lock.
 *
 *
 * Typical use might look like:
 *
 * ```
 * directory.obtainLock("my.lock").use { lock ->
 * // ... code to execute while locked ...
 * }
 *```
 * @see Directory.obtainLock
 * @lucene.internal
 */
abstract class Lock : AutoCloseable {
    /**
     * Releases exclusive access.
     *
     *
     * Note that exceptions thrown from close may require human intervention, as it may mean the
     * lock was no longer valid, or that fs permissions prevent removal of the lock file, or other
     * reasons.
     *
     *
     * {@inheritDoc}
     *
     * @throws LockReleaseFailedException optional specific exception) if the lock could not be
     * properly released.
     */
    abstract override fun close()

    /**
     * Best effort check that this lock is still valid. Locks could become invalidated externally for
     * a number of reasons, for example if a user deletes the lock file manually or when a network
     * filesystem is in use.
     *
     * @throws IOException if the lock is no longer valid.
     */
    @Throws(IOException::class)
    abstract fun ensureValid()
}
