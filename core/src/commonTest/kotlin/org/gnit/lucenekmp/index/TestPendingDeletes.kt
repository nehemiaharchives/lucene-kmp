package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.*

class TestPendingDeletes : LuceneTestCase() {
    protected open fun newPendingDeletes(commitInfo: SegmentCommitInfo): PendingDeletes {
        return PendingDeletes(commitInfo)
    }

    @Test
    fun testDeleteDoc() {
        val dir: Directory = ByteBuffersDirectory()
        val si = SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            "test",
            10,
            false,
            false,
            Codec.default,
            mutableMapOf(),
            StringHelper.randomId(),
            HashMap(),
            null
        )
        val commitInfo = SegmentCommitInfo(si, 0, 0, -1, -1, -1, StringHelper.randomId())
        val deletes = newPendingDeletes(commitInfo)
        assertNull(deletes.liveDocs)
        val docToDelete = TestUtil.nextInt(random(), 0, 7)
        assertTrue(deletes.delete(docToDelete))
        assertNotNull(deletes.liveDocs)
        assertEquals(1, deletes.numPendingDeletes())

        var liveDocs = deletes.liveDocs
        assertFalse(liveDocs!!.get(docToDelete))
        assertFalse(deletes.delete(docToDelete))

        assertTrue(liveDocs.get(8))
        assertTrue(deletes.delete(8))
        assertTrue(liveDocs.get(8))
        assertEquals(2, deletes.numPendingDeletes())

        assertTrue(liveDocs.get(9))
        assertTrue(deletes.delete(9))
        assertTrue(liveDocs.get(9))

        liveDocs = deletes.liveDocs
        assertFalse(liveDocs!!.get(9))
        assertFalse(liveDocs.get(8))
        assertFalse(liveDocs.get(docToDelete))
        assertEquals(3, deletes.numPendingDeletes())
        dir.close()
    }

    @Test
    fun testWriteLiveDocs() {
        val dir: Directory = ByteBuffersDirectory()
        val si = SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            "test",
            6,
            false,
            false,
            Codec.default,
            mutableMapOf(),
            StringHelper.randomId(),
            HashMap(),
            null
        )
        val commitInfo = SegmentCommitInfo(si, 0, 0, -1, -1, -1, StringHelper.randomId())
        val deletes = newPendingDeletes(commitInfo)
        assertFalse(deletes.writeLiveDocs(dir))
        assertEquals(0, dir.listAll().size)
        val secondDocDeletes = random().nextBoolean()
        deletes.delete(5)
        if (secondDocDeletes) {
            deletes.liveDocs
            deletes.delete(2)
        }
        assertEquals(-1L, commitInfo.delGen)
        assertEquals(0, commitInfo.delCount)

        assertEquals(if (secondDocDeletes) 2 else 1, deletes.numPendingDeletes())
        assertTrue(deletes.writeLiveDocs(dir))
        assertEquals(1, dir.listAll().size)
        var liveDocs = Codec.default.liveDocsFormat().readLiveDocs(dir, commitInfo, IOContext.DEFAULT)
        assertFalse(liveDocs.get(5))
        if (secondDocDeletes) {
            assertFalse(liveDocs.get(2))
        } else {
            assertTrue(liveDocs.get(2))
        }
        assertTrue(liveDocs.get(0))
        assertTrue(liveDocs.get(1))
        assertTrue(liveDocs.get(3))
        assertTrue(liveDocs.get(4))

        assertEquals(0, deletes.numPendingDeletes())
        assertEquals(if (secondDocDeletes) 2 else 1, commitInfo.delCount)
        assertEquals(1L, commitInfo.delGen)

        deletes.delete(0)
        assertTrue(deletes.writeLiveDocs(dir))
        assertEquals(2, dir.listAll().size)
        liveDocs = Codec.default.liveDocsFormat().readLiveDocs(dir, commitInfo, IOContext.DEFAULT)
        assertFalse(liveDocs.get(5))
        if (secondDocDeletes) {
            assertFalse(liveDocs.get(2))
        } else {
            assertTrue(liveDocs.get(2))
        }
        assertFalse(liveDocs.get(0))
        assertTrue(liveDocs.get(1))
        assertTrue(liveDocs.get(3))
        assertTrue(liveDocs.get(4))

        assertEquals(0, deletes.numPendingDeletes())
        assertEquals(if (secondDocDeletes) 3 else 2, commitInfo.delCount)
        assertEquals(2L, commitInfo.delGen)
        dir.close()
    }

    @Test
    fun testIsFullyDeleted() {
        val dir: Directory = ByteBuffersDirectory()
        val si = SegmentInfo(
            dir,
            Version.LATEST,
            Version.LATEST,
            "test",
            3,
            false,
            false,
            Codec.default,
            mutableMapOf(),
            StringHelper.randomId(),
            HashMap(),
            null
        )
        val commitInfo = SegmentCommitInfo(si, 0, 0, -1, -1, -1, StringHelper.randomId())
        val fieldInfos = FieldInfos.EMPTY
        si.codec.fieldInfosFormat().write(dir, si, "", fieldInfos, IOContext.DEFAULT)
        val deletes = newPendingDeletes(commitInfo)
        for (i in 0 until 3) {
            assertTrue(deletes.delete(i))
            if (random().nextBoolean()) {
                assertTrue(deletes.writeLiveDocs(dir))
            }
            assertEquals(i == 2, deletes.isFullyDeleted { null as CodecReader })
        }
    }
}

