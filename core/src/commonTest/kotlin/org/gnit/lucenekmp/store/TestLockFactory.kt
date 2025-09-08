package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import okio.IOException

/**
 * Tests for [LockFactory].
 */
class TestLockFactory : LuceneTestCase() {

    /**
     * Verify that a custom [LockFactory] is used and locks are created.
     */
    @Test
    @Throws(IOException::class)
    fun testCustomLockFactory() {
        val lf = MockLockFactory()
        val dir: Directory = MockDirectoryWrapper(random(), ByteBuffersDirectory(lf))

        val writer = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        // add 100 documents (so that commit lock is used)
        for (i in 0 until 100) {
            addDoc(writer)
        }

        assertEquals(
            1,
            lf.locksCreated.size,
            "# of unique locks created (after instantiating IndexWriter)"
        )
        writer.close()
    }

    /**
     * Verify that [NoLockFactory] can be used and allows two IndexWriters.
     */
    @Test
    @Throws(IOException::class)
    fun testDirectoryNoLocking() {
        val dir = MockDirectoryWrapper(random(), ByteBuffersDirectory(NoLockFactory.INSTANCE))

        val writer = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
        writer.commit() // required so the second open succeed

        // Create a 2nd IndexWriter. This is normally not allowed but it should run through
        // since we're not using any locks
        var writer2: IndexWriter? = null
        try {
            writer2 = IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Should not have hit an IOException with no locking")
        }

        writer.close()
        writer2?.close()
    }

    internal class MockLockFactory : LockFactory() {
        val locksCreated: MutableMap<String, Lock> = mutableMapOf()

        override fun obtainLock(dir: Directory, lockName: String): Lock {
            val lock: Lock = MockLock()
            locksCreated[lockName] = lock
            return lock
        }

        internal class MockLock : Lock() {
            override fun close() {
                // do nothing
            }

            @Throws(IOException::class)
            override fun ensureValid() {
                // do nothing
            }
        }
    }

    @Throws(IOException::class)
    private fun addDoc(writer: IndexWriter) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        writer.addDocument(doc)
    }

    private fun newTextField(name: String, value: String, store: Field.Store): Field {
        return TextField(name, value, store)
    }
}

