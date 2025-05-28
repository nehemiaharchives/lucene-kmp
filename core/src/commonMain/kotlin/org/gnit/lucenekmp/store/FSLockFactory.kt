package org.gnit.lucenekmp.store

import okio.IOException

/**
 * Base class for file system based locking implementation. This class is explicitly checking that
 * the passed [Directory] is an [FSDirectory].
 */
abstract class FSLockFactory : LockFactory() {
    @Throws(IOException::class)
    override fun obtainLock(dir: Directory, lockName: String): Lock {
        if (dir !is FSDirectory) {
            throw UnsupportedOperationException("${this::class.simpleName} can only be used with FSDirectory subclasses, got: $dir")
        }
        return obtainFSLock(dir as FSDirectory, lockName)
    }

    /**
     * Implement this method to obtain a lock for a FSDirectory instance.
     *
     * @throws IOException if the lock could not be obtained.
     */
    @Throws(IOException::class)
    protected abstract fun obtainFSLock(
        dir: FSDirectory,
        lockName: String
    ): Lock

    companion object {
        val default: FSLockFactory
            /**
             * Returns the default locking implementation for this platform. This method currently always
             * returns [NativeFSLockFactory].
             */
            get() = NativeFSLockFactory.INSTANCE
    }
}
