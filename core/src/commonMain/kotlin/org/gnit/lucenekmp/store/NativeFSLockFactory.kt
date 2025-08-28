package org.gnit.lucenekmp.store

import okio.FileSystem
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.StandardOpenOption
import okio.IOException
import okio.Path
import okio.SYSTEM
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Implements [LockFactory] using native OS file locks. Note that because this LockFactory
 * relies on java.nio.* APIs for locking, any problems with those APIs will cause locking to fail.
 * Specifically, on certain NFS environments the java.nio.* locks will fail (the lock can
 * incorrectly be double acquired) whereas [SimpleFSLockFactory] worked perfectly in those
 * same environments. For NFS based access to an index, it's recommended that you try [ ] first and work around the one limitation that a lock file could be left when
 * the JVM exits abnormally.
 *
 *
 * The primary benefit of [NativeFSLockFactory] is that locks (not the lock file itself)
 * will be properly removed (by the OS) if the JVM has an abnormal exit.
 *
 *
 * Note that, unlike [SimpleFSLockFactory], the existence of leftover lock files in the
 * filesystem is fine because the OS will free the locks held against these files even though the
 * files still remain. Lucene will never actively remove the lock files, so although you see them,
 * the index may not be locked.
 *
 *
 * Special care needs to be taken if you change the locking implementation: First be certain that
 * no writer is in fact writing to the index otherwise you can easily corrupt your index. Be sure to
 * do the LockFactory change on all Lucene instances and clean up all leftover lock files before
 * starting the new configuration for the first time. Different implementations can not work
 * together!
 *
 *
 * If you suspect that this or any other LockFactory is not working properly in your environment,
 * you can easily test it by using [VerifyingLockFactory], [LockVerifyServer] and [ ].
 *
 *
 * This is a singleton, you have to use [.INSTANCE].
 *
 * @see LockFactory
 */
class NativeFSLockFactory constructor(
    val fs: FileSystem = FileSystem.SYSTEM
) : FSLockFactory() {
    @OptIn(ExperimentalTime::class)
    override fun obtainFSLock(
        dir: FSDirectory,
        lockName: String
    ): Lock {
        val lockDir: Path = dir.directory

        // Ensure that lockDir exists and is a directory.
        // note: this will fail if lockDir is a symlink
        Files.createDirectories(lockDir)

        val lockFile: Path = lockDir.resolve(lockName)

        var creationException: IOException? = null
        try {
            Files.createFile(lockFile)
        } catch (ignore: IOException) {
            // we must create the file to have a truly canonical path.
            // if it's already created, we don't care. if it cant be created, it will fail below.
            creationException = ignore
        }

        // fails if the lock file does not exist
        val realPath: Path
        try {
            realPath = lockFile.normalized()
        } catch (e: IOException) {
            // if we couldn't resolve the lock file, it might be because we couldn't create it.
            // so append any exception from createFile as a suppressed exception, in case its useful
            if (creationException != null) {
                e.addSuppressed(creationException)
            }
            throw e
        }

        // used as a best-effort check, to see if the underlying file has changed
        val creationTime = Files.creationTime(realPath)

        if (LOCK_HELD.add(realPath.toString())) {
            /*var channel: FileChannel = null*/
            /*var lock: FileLock = null*/

            var lock: NativeFSLock? = null

            try {
                /*channel =  FileChannel.open(realPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                lock = channel.tryLock()
                if (lock != null) {
                    return NativeFSLock(lock, channel, realPath, creationTime)
                } else {
                    throw LockObtainFailedException("Lock held by another program: $realPath")
                }*/

                lock = NativeFSLock(
                    fs = fs,
                    path = realPath,
                    creationTime = creationTime
                )


            }
            catch (ioe: IOException){
                throw LockObtainFailedException(
                    "Unable to obtain lock on $realPath, IOException: ${ioe.message}",
                    ioe
                )
            }
            finally {
                if (lock == null) { // not successful - clear up and move out
                    /*IOUtils.closeWhileHandlingException(channel)*/ // LUCENE_JAVA_TODO: addSuppressed, in lucene-kmp, this will not implemented as it use okio instead of java.nio.
                    clearLockHeld(realPath) // clear LOCK_HELD last
                }
            }

            return lock

        } else {
            throw LockObtainFailedException("Lock held by this virtual machine: $realPath")
        }
    }

    // TODO: kind of bogus we even pass channel:
    // FileLock has an accessor, but mockfs doesnt yet mock the locks, too scary atm.
    @OptIn(ExperimentalTime::class)
    internal class NativeFSLock(
        /*lock: FileLock,
        channel: FileChannel,*/
        private val fs: FileSystem,
        path: Path,
        creationTime: Long? /*FileTime*/
    ) : Lock() {
        /*val lock: FileLock
        val channel: FileChannel*/
        val path: Path
        val creationTime: Long? /*FileTime*/ //implement if needed

        @Volatile
        var closed: Boolean = false

        init {
            /*this.lock = lock
            this.channel = channel*/
            // Don't modify the lock file here; we already ensured its existence above.
            // Creating and then atomically replacing it would change its creation time
            // and cause ensureValid() to falsely detect external modification.
            this.path = path
            this.creationTime = creationTime
        }

        override fun ensureValid() {
            if (closed) {
                throw AlreadyClosedException("Lock instance already released: $this")
            }
            // check we are still in the locks map (some debugger or something crazy didn't remove us)
            if (!LOCK_HELD.contains(path.toString())) {
                throw AlreadyClosedException("Lock path unexpectedly cleared from map: $this")
            }
            // check our lock wasn't invalidated.
            /*if (!lock.isValid()) {
                throw AlreadyClosedException("FileLock invalidated by an external force: $this")
            }*/
            // try to validate the underlying file descriptor.
            // this will throw IOException if something is wrong.
            /*val size: Long? = channel?.size()
            if (size != 0L) {
                throw AlreadyClosedException(
                    "Unexpected lock file size: $size, (lock=$this)"
                )
            }*/
            // try to validate the backing file name, that it still exists,
            // and has the same creation time as when we obtained the lock.
            // if it differs, someone deleted our lock file (and we are ineffective)
            val ctime: Long? = Files.creationTime(path)
            if (creationTime != ctime) {
                throw AlreadyClosedException(
                    "Underlying file changed by an external force at $ctime, (lock=$this)"
                )
            }
        }

        override fun close() {
            if (closed) {
                return
            }
            // NOTE: we don't validate, as unlike SimpleFSLockFactory, we can't break others locks
            // first release the lock, then the channel
            try {
                /*this.channel.use { channel ->
                    this.lock.use { lock ->
                        checkNotNull(lock)
                        checkNotNull(channel)
                    }
                }*/

                fs.delete(path) // delete the lock file

            } finally {
                closed = true
                clearLockHeld(path)
            }
        }

        override fun toString(): String {
            return "NativeFSLock(path=$path,creationTime=$creationTime)"
        }
    }

    companion object {
        /** Singleton instance  */
        val INSTANCE: NativeFSLockFactory = NativeFSLockFactory()

        private val LOCK_HELD: MutableSet<String> =
            mutableSetOf() /*java.util.Collections.synchronizedSet<String>(HashSet<String>())*/

        @Throws(IOException::class)
        private fun clearLockHeld(path: Path) {
            val remove = LOCK_HELD.remove(path.toString())
            if (remove == false) {
                throw AlreadyClosedException("Lock path was cleared but never marked as held: $path")
            }
        }
    }
}
