package org.gnit.lucenekmp.store

import okio.IOException
import okio.Path
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.BaseLockFactoryTestCase
import kotlin.test.Test

/** Simple tests for SingleInstanceLockFactory */
class TestSingleInstanceLockFactory : BaseLockFactoryTestCase() {

    @Throws(Exception::class)
    override fun getDirectory(path: Path): Directory {
        return newDirectory(random(), SingleInstanceLockFactory())
    }

    // Verify: basic locking on single instance lock factory (can't create two IndexWriters)
    @Test
    fun testDefaultLockFactory() {
        val dir = ByteBuffersDirectory()

        val writer = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        // Create a 2nd IndexWriter.  This should fail.
        expectThrows(IOException::class) {
            IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
            )
        }

        writer.close()
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
