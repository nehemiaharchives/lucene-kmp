package org.gnit.lucenekmp.store

import okio.IOException
import okio.Path
import org.gnit.lucenekmp.tests.store.BaseLockFactoryTestCase
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Ignore
import kotlin.test.Test

/** Simple tests for NativeFSLockFactory */
class TestNativeFSLockFactory : BaseLockFactoryTestCase() {

    @Throws(Exception::class)
    override fun getDirectory(path: Path): Directory {
        return newFSDirectory(path, NativeFSLockFactory.INSTANCE)
    }

    /** Verify NativeFSLockFactory works correctly if the lock file exists */
    @Test
    fun testLockFileExists() {
        val tempDir = createTempDir()
        val lockFile = tempDir.resolve("test.lock")
        org.gnit.lucenekmp.jdkport.Files.createFile(lockFile)

        val dir = getDirectory(tempDir)
        val l = dir.obtainLock("test.lock")
        l.close()
        dir.close()
    }

    /** release the lock and test ensureValid fails */
    @Test
    fun testInvalidateLock() {
        val dir = getDirectory(createTempDir())
        val lock = dir.obtainLock("test.lock") as NativeFSLockFactory.NativeFSLock
        lock.ensureValid()
        // KMP port doesn't expose underlying file lock object; close the lock to invalidate it.
        lock.close()
        expectThrows(AlreadyClosedException::class) {
            lock.ensureValid()
        }

        IOUtils.closeWhileHandlingException(lock)
        dir.close()
    }

    /** close the channel and test ensureValid fails */
    @Test
    fun testInvalidateChannel() {
        val dir = getDirectory(createTempDir())
        val lock = dir.obtainLock("test.lock") as NativeFSLockFactory.NativeFSLock
        lock.ensureValid()
        // KMP port doesn't expose underlying channel; close the lock to invalidate it.
        lock.close()
        expectThrows(AlreadyClosedException::class) {
            lock.ensureValid()
        }

        IOUtils.closeWhileHandlingException(lock)
        dir.close()
    }

    /** delete the lockfile and test ensureValid fails */
    @Test
    fun testDeleteLockFile() {
        getDirectory(createTempDir()).use { dir ->
            // Path/Directory-based Windows FS detection helper is not ported yet.
            assumeFalse("we must be able to delete an open file", Constants.WINDOWS)

            val lock = dir.obtainLock("test.lock")
            lock.ensureValid()

            dir.deleteFile("test.lock")

            expectThrows(IOException::class) {
                lock.ensureValid()
            }

            IOUtils.closeWhileHandlingException(lock)
        }
    }

    /** MockFileSystem that throws AccessDeniedException on creating test.lock */
    @Ignore // TODO tests.mockfile.FilterFileSystemProvider/FilterPath are not ported yet.
    @Test
    fun testBadPermissions() {
    }

    // tests inherited from BaseLockFactoryTestCase
    @Test
    override fun testBasics() = super.testBasics()

    @Test
    override fun testDoubleClose() = super.testDoubleClose()

    @Test
    override fun testValidAfterAcquire() = super.testValidAfterAcquire()

    @Test
    override fun testInvalidAfterClose() = super.testInvalidAfterClose()

    @Test
    override fun testObtainConcurrently() = super.testObtainConcurrently()

    @Test
    override fun testStressLocks() = super.testStressLocks()
}
