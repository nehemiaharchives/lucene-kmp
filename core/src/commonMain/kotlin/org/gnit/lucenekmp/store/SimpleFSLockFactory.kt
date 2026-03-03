package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.AccessDeniedException
import org.gnit.lucenekmp.jdkport.FileAlreadyExistsException
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.ReentrantLock
import okio.IOException
import okio.Path
import kotlin.concurrent.Volatile

/**
 * Implements [LockFactory] using [Files.createFile].
 *
 * The main downside with using this API for locking is that the Lucene write lock may not be
 * released when the JVM exits abnormally.
 *
 * When this happens, an [LockObtainFailedException] is hit when trying to create a writer,
 * in which case you may need to explicitly clear the lock file first by manually removing the file.
 * But, first be certain that no writer is in fact writing to the index otherwise you can easily
 * corrupt your index.
 *
 * Special care needs to be taken if you change the locking implementation: First be certain that
 * no writer is in fact writing to the index otherwise you can easily corrupt your index. Be sure to
 * do the LockFactory change all Lucene instances and clean up all leftover lock files before
 * starting the new configuration for the first time. Different implementations can not work
 * together!
 *
 * If you suspect that this or any other LockFactory is not working properly in your environment,
 * you can easily test it by using [VerifyingLockFactory], [LockVerifyServer] and [LockStressTest].
 *
 * This is a singleton, you have to use [INSTANCE].
 *
 * @see LockFactory
 */
class SimpleFSLockFactory private constructor() : FSLockFactory() {

    @Throws(IOException::class)
    override fun obtainFSLock(dir: FSDirectory, lockName: String): Lock {
        val lockDir = dir.directory

        // Ensure that lockDir exists and is a directory.
        // note: this will fail if lockDir is a symlink
        Files.createDirectories(lockDir)

        val lockFile = lockDir.resolve(lockName)
        val realPath = lockFile.normalized()
        if (!markLockHeld(realPath)) {
            throw LockObtainFailedException("Lock held by this virtual machine: $realPath")
        }

        // create the file: this will fail if it already exists
        var obtained = false
        try {
            try {
                Files.createFile(lockFile)
            } catch (e: Exception) {
                if (e is FileAlreadyExistsException || e is AccessDeniedException || e is IOException) {
                    // convert optional specific exception to our optional specific exception
                    throw LockObtainFailedException("Lock held elsewhere: $lockFile", e)
                }
                throw e
            } catch (t: Throwable) {
                throw LockObtainFailedException("Lock held elsewhere: $lockFile", t)
            }
            obtained = true
        } finally {
            if (!obtained) {
                clearLockHeld(realPath)
            }
        }

        // used as a best-effort check, to see if the underlying file has changed
        val creationTime = Files.creationTime(lockFile)

        return SimpleFSLock(realPath, creationTime)
    }

    class SimpleFSLock internal constructor(path: Path, creationTime: Long?) : Lock() {
        private val path: Path
        private val creationTime: Long?

        @Volatile
        private var closed = false
        private val closeLock = ReentrantLock()

        init {
            this.path = path
            this.creationTime = creationTime
        }

        @Throws(IOException::class)
        override fun ensureValid() {
            if (closed) {
                throw AlreadyClosedException("Lock instance already released: $this")
            }
            // try to validate the backing file name, that it still exists,
            // and has the same creation time as when we obtained the lock.
            // if it differs, someone deleted our lock file (and we are ineffective)
            val ctime = Files.creationTime(path)
            if (creationTime != ctime) {
                throw AlreadyClosedException(
                    "Underlying file changed by an external force at $ctime, (lock=$this)"
                )
            }
        }

        override fun close() {
            try {
                closeLock.lock()
                if (closed) {
                    return
                }
                try {
                    // NOTE: unlike NativeFSLockFactory, we can potentially delete someone else's
                    // lock if things have gone wrong. we do best-effort check (ensureValid) to
                    // avoid doing this.
                    try {
                        ensureValid()
                    } catch (exc: Throwable) {
                        // notify the user they may need to intervene.
                        throw LockReleaseFailedException(
                            "Lock file cannot be safely removed. Manual intervention is recommended.",
                            exc
                        )
                    }
                    // we did a best effort check, now try to remove the file. if something goes wrong,
                    // we need to make it clear to the user that the directory may still remain locked.
                    try {
                        Files.delete(path)
                    } catch (exc: Throwable) {
                        throw LockReleaseFailedException(
                            "Unable to remove lock file. Manual intervention is recommended",
                            exc
                        )
                    }
                } finally {
                    clearLockHeld(path)
                    closed = true
                }
            } finally {
                closeLock.unlock()
            }            
        }

        override fun toString(): String {
            return "SimpleFSLock(path=$path,creationTime=$creationTime)"
        }
    }

    companion object {
        /** Singleton instance */
        val INSTANCE: SimpleFSLockFactory = SimpleFSLockFactory()

        private val LOCK_HELD: MutableSet<String> = mutableSetOf()
        private val lockHeldLock = ReentrantLock()

        private fun markLockHeld(path: Path): Boolean {
            return try {
                lockHeldLock.lock()
                LOCK_HELD.add(path.toString())
            } finally {
                lockHeldLock.unlock()
            }
        }

        private fun clearLockHeld(path: Path) {
            try {
                lockHeldLock.lock()
                LOCK_HELD.remove(path.toString())
            } finally {
                lockHeldLock.unlock()
            }
        }
    }
}
