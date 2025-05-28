package org.gnit.lucenekmp.store

import okio.IOException

/**
 * Base class for Locking implementation. [Directory] uses instances of this class to
 * implement locking.
 *
 *
 * Lucene uses [NativeFSLockFactory] by default for [FSDirectory]-based index
 * directories.
 *
 *
 * Special care needs to be taken if you change the locking implementation: First be certain that
 * no writer is in fact writing to the index otherwise you can easily corrupt your index. Be sure to
 * do the LockFactory change on all Lucene instances and clean up all leftover lock files before
 * starting the new configuration for the first time. Different implementations can not work
 * together!
 *
 *
 * If you suspect that some LockFactory implementation is not working properly in your
 * environment, you can easily test it by using [VerifyingLockFactory], [ ] and [LockStressTest].
 *
 * @see LockVerifyServer
 *
 * @see LockStressTest
 *
 * @see VerifyingLockFactory
 */
abstract class LockFactory {
    /**
     * Return a new obtained Lock instance identified by lockName.
     *
     * @param lockName name of the lock to be created.
     * @throws LockObtainFailedException (optional specific exception) if the lock could not be
     * obtained because it is currently held elsewhere.
     * @throws IOException if any i/o error occurs attempting to gain the lock
     */
    @Throws(IOException::class)
    abstract fun obtainLock(dir: Directory, lockName: String): Lock
}
