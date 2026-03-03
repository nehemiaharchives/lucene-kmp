package org.gnit.lucenekmp.store

import okio.IOException
import okio.Path
import org.gnit.lucenekmp.tests.store.BaseLockFactoryTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test

/** Simple tests for SimpleFSLockFactory */
class TestSimpleFSLockFactory : BaseLockFactoryTestCase() {

    @Throws(Exception::class)
    override fun getDirectory(path: Path): Directory {
        return newFSDirectory(path, SimpleFSLockFactory.INSTANCE)
    }

    /** delete the lockfile and test ensureValid fails */
    @Test
    fun testDeleteLockFile() {
        val dir = getDirectory(createTempDir())
        try {
            val lock = dir.obtainLock("test.lock")
            lock.ensureValid()

            try {
                dir.deleteFile("test.lock")
            } catch (e: Exception) {
                // we can't delete a file for some reason, just clean up and assume the test.
                IOUtils.closeWhileHandlingException(lock)
                assumeNoException("test requires the ability to delete a locked file", e)
            }

            expectThrows(IOException::class) {
                lock.ensureValid()
            }
            IOUtils.closeWhileHandlingException(lock)
        } finally {
            // Do this in finally clause in case the assumeNoException is false:
            dir.close()
        }
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
