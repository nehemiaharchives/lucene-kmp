package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DocumentStoredFieldVisitor
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TestDirectoryReaderReopen : LuceneTestCase() {

    private val createReaderMutex = Any()

    @Ignore("IndexWriter commit/forceMerge not yet supported")
    @Test
    fun testReopen() {
        val dir1 = newDirectory()
        createIndex(random(), dir1, false)
        performDefaultTests(
            object : TestReopen() {
                override fun modifyIndex(i: Int) {
                    modifyIndex(i, dir1)
                }

                override fun openReader(): DirectoryReader {
                    return DirectoryReader.open(dir1)
                }
            }
        )
        dir1.close()

        val dir2 = newDirectory()
        createIndex(random(), dir2, true)
        performDefaultTests(
            object : TestReopen() {
                override fun modifyIndex(i: Int) {
                    modifyIndex(i, dir2)
                }

                override fun openReader(): DirectoryReader {
                    return DirectoryReader.open(dir2)
                }
            }
        )
        dir2.close()
    }

    @Ignore("IndexWriter commit/forceMerge not yet supported")
    @Test
    fun testCommitReopen() {
        val dir = newDirectory()
        doTestReopenWithCommit(random(), dir, true)
        dir.close()
    }

    @Ignore("IndexWriter commit/forceMerge not yet supported")
    @Test
    fun testCommitRecreate() {
        val dir = newDirectory()
        doTestReopenWithCommit(random(), dir, false)
        dir.close()
    }

    @Ignore("IndexWriter commit/forceMerge not yet supported")
    @Test
    fun testThreadSafety() {
        val dir = newDirectory()
        val n = TestUtil.nextInt(random(), 20, 40)

        val writer = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
        for (i in 0 until n) {
            writer.addDocument(createDocument(i, 3))
        }
        writer.forceMerge(1)
        writer.close()

        val test = object : TestReopen() {
            override fun modifyIndex(i: Int) {
                val modifier = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
                modifier.addDocument(createDocument(n + i, 6))
                modifier.close()
            }

            override fun openReader(): DirectoryReader {
                return DirectoryReader.open(dir)
            }
        }

        val readers = mutableListOf<ReaderCouple>()
        val readersLock = Any()
        val firstReader = DirectoryReader.open(dir)
        var reader = firstReader

        val threads = arrayOfNulls<ReaderThread>(n)
        val readersToClose = mutableSetOf<DirectoryReader>()
        val readersToCloseLock = Any()

        for (i in 0 until n) {
            if (i % 2 == 0) {
                val refreshed = DirectoryReader.openIfChanged(reader)
                if (refreshed != null) {
                    synchronized(readersToCloseLock) { readersToClose.add(reader) }
                    reader = refreshed
                }
            }
            val r = reader
            val index = i
            val task: ReaderThreadTask =
                if (i < 4 || (i >= 10 && i < 14) || i > 18) {
                    object : ReaderThreadTask() {
                        override fun run() {
                            val rnd = LuceneTestCase.random()
                            while (!stopped) {
                                if (index % 2 == 0) {
                                    val c = refreshReader(r, test, index, true)
                                    synchronized(readersToCloseLock) {
                                        readersToClose.add(c.newReader!!)
                                        readersToClose.add(c.refreshedReader)
                                    }
                                    synchronized(readersLock) { readers.add(c) }
                                    break
                                } else {
                                    var refreshed = DirectoryReader.openIfChanged(r)
                                    if (refreshed == null) {
                                        refreshed = r
                                    }
                                    val searcher = IndexSearcher(refreshed)
                                    val hits = searcher.search(
                                        TermQuery(Term("field1", "a" + rnd.nextInt(refreshed.maxDoc()))),
                                        1000
                                    ).scoreDocs
                                    if (hits.isNotEmpty()) {
                                        searcher.storedFields().document(hits[0].doc)
                                    }
                                    if (refreshed !== r) {
                                        refreshed.close()
                                    }
                                }
                                Thread.sleep(TestUtil.nextInt(random(), 1, 100).toLong())
                            }
                        }
                    }
                } else {
                    object : ReaderThreadTask() {
                        override fun run() {
                            val rnd = LuceneTestCase.random()
                            while (!stopped) {
                                val c: ReaderCouple? = synchronized(readersLock) {
                                    if (readers.isNotEmpty()) {
                                        readers[rnd.nextInt(readers.size)]
                                    } else {
                                        null
                                    }
                                }
                                if (c != null) {
                                    TestDirectoryReader.assertIndexEquals(c.newReader!!, c.refreshedReader)
                                }
                                Thread.sleep(TestUtil.nextInt(random(), 1, 100).toLong())
                            }
                        }
                    }
                }
            threads[i] = ReaderThread(task).apply { start() }
        }

        Thread.sleep(1000)

        for (i in 0 until n) {
            threads[i]?.stopThread()
        }
        for (i in 0 until n) {
            threads[i]?.join()
            threads[i]?.error?.let { e ->
                fail("Error occurred in thread ${threads[i]!!.name}:\n${e.message}")
            }
        }

        synchronized(readersToCloseLock) {
            for (readerToClose in readersToClose) {
                readerToClose.close()
            }
        }

        firstReader.close()
        reader.close()

        synchronized(readersToCloseLock) {
            for (readerToClose in readersToClose) {
                assertReaderClosed(readerToClose, true)
            }
        }

        assertReaderClosed(reader, true)
        assertReaderClosed(firstReader, true)

        dir.close()
    }

    @Ignore("IndexWriter commit/forceMerge not yet supported")
    @Test
    fun testReopenOnCommit() {
        val dir = newDirectory()
        val writer = IndexWriter(
            dir,
            IndexWriterConfig(MockAnalyzer(random()))
                .setIndexDeletionPolicy(KeepAllCommits())
                .setMaxBufferedDocs(-1)
        )
        for (i in 0 until 4) {
            val doc = Document()
            doc.add(StringField("id", "$i", Field.Store.NO))
            writer.addDocument(doc)
            val data = mutableMapOf<String, String>()
            data["index"] = "$i"
            writer.setLiveCommitData(data.entries)
            writer.flush()
        }
        for (i in 0 until 4) {
            writer.deleteDocuments(Term("id", "$i"))
            val data = mutableMapOf<String, String>()
            data["index"] = "${4 + i}"
            writer.setLiveCommitData(data.entries)
            writer.flush()
        }
        writer.close()

        var r = DirectoryReader.open(dir)
        assertEquals(0, r.numDocs())

        val commits = DirectoryReader.listCommits(dir)
        for (commit in commits) {
            val r2 = DirectoryReader.openIfChanged(r, commit)
            assertNotNull(r2)
            assertTrue(r2 !== r)
            val s = commit.userData
            val v = if (s.isEmpty()) -1 else s["index"]!!.toInt()
            if (v < 4) {
                assertEquals(1 + v, r2.numDocs())
            } else {
                assertEquals(7 - v, r2.numDocs())
            }
            r.close()
            r = r2
        }
        r.close()
        dir.close()
    }

    @Test
    fun testOpenIfChangedNRTToCommit() {
        // TODO: Port this test
    }

    @Test
    fun testOverDecRefDuringReopen() {
        // TODO: Port this test
    }

    @Test
    fun testNPEAfterInvalidReindex1() {
        // TODO: Port this test
    }

    @Test
    fun testNPEAfterInvalidReindex2() {
        // TODO: Port this test
    }

    @Test
    fun testNRTMdeletes() {
        // TODO: Port this test
    }

    @Test
    fun testNRTMdeletes2() {
        // TODO: Port this test
    }

    @Test
    fun testNRTMupdates() {
        // TODO: Port this test
    }

    @Test
    fun testNRTMupdates2() {
        // TODO: Port this test
    }

    @Test
    fun testDeleteIndexFilesWhileReaderStillOpen() {
        // TODO: Port this test
    }

    @Test
    fun testReuseUnchangedLeafReaderOnDVUpdate() {
        // TODO: Port this test
    }

    private fun performDefaultTests(test: TestReopen) {
        var index1 = test.openReader()
        var index2 = test.openReader()

        TestDirectoryReader.assertIndexEquals(index1, index2)

        var couple = refreshReader(index2, false)
        assertTrue(couple.refreshedReader === index2)

        couple = refreshReader(index2, test, 0, true)
        index1.close()
        index1 = couple.newReader!!

        val index2Refreshed = couple.refreshedReader
        index2.close()

        TestDirectoryReader.assertIndexEquals(index1, index2Refreshed)

        index2Refreshed.close()
        assertReaderClosed(index2, true)
        assertReaderClosed(index2Refreshed, true)

        index2 = test.openReader()

        for (i in 1 until 4) {
            index1.close()
            couple = refreshReader(index2, test, i, true)
            index2.close()

            index2 = couple.refreshedReader
            index1 = couple.newReader!!
            TestDirectoryReader.assertIndexEquals(index1, index2)
        }

        index1.close()
        index2.close()
        assertReaderClosed(index1, true)
        assertReaderClosed(index2, true)
    }

    private fun refreshReader(reader: DirectoryReader, hasChanges: Boolean): ReaderCouple {
        return refreshReader(reader, null, -1, hasChanges)
    }

    private fun refreshReader(
        reader: DirectoryReader,
        test: TestReopen?,
        modify: Int,
        hasChanges: Boolean
    ): ReaderCouple {
        synchronized(createReaderMutex) {
            var r: DirectoryReader? = null
            if (test != null) {
                test.modifyIndex(modify)
                r = test.openReader()
            }

            var refreshed: DirectoryReader? = null
            try {
                refreshed = DirectoryReader.openIfChanged(reader)
                if (refreshed == null) {
                    refreshed = reader
                }
            } finally {
                if (refreshed == null && r != null) {
                    r.close()
                }
            }

            if (hasChanges) {
                if (refreshed === reader) {
                    fail("No new DirectoryReader instance created during refresh.")
                }
            } else {
                if (refreshed !== reader) {
                    fail("New DirectoryReader instance created during refresh even though index had no changes.")
                }
            }

            return ReaderCouple(r, refreshed!!)
        }
    }

    private fun doTestReopenWithCommit(random: Random, dir: Directory, withReopen: Boolean) {
        val iwriter = IndexWriter(
            dir,
            IndexWriterConfig(MockAnalyzer(random))
                .setOpenMode(OpenMode.CREATE)
        )
        var reader = DirectoryReader.open(dir)
        try {
            val M = 3
            val customType = FieldType(TextField.TYPE_STORED).apply { setTokenized(false) }
            val customType2 = FieldType(TextField.TYPE_STORED).apply {
                setTokenized(false)
                setOmitNorms(true)
            }
            val customType3 = FieldType().apply { setStored(true) }
            for (i in 0 until 4) {
                for (j in 0 until M) {
                    val doc = Document()
                    doc.add(Field("id", "${i}_${j}", customType))
                    doc.add(Field("id2", "${i}_${j}", customType2))
                    doc.add(Field("id3", "${i}_${j}", customType3))
                    iwriter.addDocument(doc)
                    if (i > 0) {
                        val k = i - 1
                        val n = j + k * M
                        val visitor = DocumentStoredFieldVisitor()
                        reader.storedFields().document(n, visitor)
                        val prevDoc = visitor.document
                        val id = prevDoc.get("id")
                        assertEquals("${k}_${j}", id)
                    }
                }
                iwriter.flush()
                if (withReopen) {
                    val r2 = DirectoryReader.openIfChanged(reader)
                    if (r2 != null) {
                        reader.close()
                        reader = r2
                    }
                } else {
                    reader.close()
                    reader = DirectoryReader.open(dir)
                }
            }
        } finally {
            iwriter.close()
            reader.close()
        }
    }

    private fun createIndex(random: Random, dir: Directory, multiSegment: Boolean) {
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random)))
        for (i in 0 until 100) {
            w.addDocument(createDocument(i, 4))
            if (multiSegment && i % 10 == 0) {
                w.flush() // commit not yet supported
            }
        }
        // forceMerge/commit not yet supported in KMP port
        w.close()

        val r = DirectoryReader.open(dir)
        if (multiSegment) {
            assertTrue(r.leaves().size > 1)
        } else {
            assertTrue(r.leaves().size == 1)
        }
        r.close()
    }

    companion object {
        fun createDocument(n: Int, numFields: Int): Document {
            val sb = StringBuilder()
            val doc = Document()
            sb.append("a").append(n)
            val customType2 = FieldType(TextField.TYPE_STORED).apply {
                setTokenized(false)
                setOmitNorms(true)
            }
            val customType3 = FieldType().apply { setStored(true) }
            doc.add(TextField("field1", sb.toString(), Field.Store.YES))
            doc.add(Field("fielda", sb.toString(), customType2))
            doc.add(Field("fieldb", sb.toString(), customType3))
            sb.append(" b").append(n)
            for (i in 1 until numFields) {
                doc.add(TextField("field${i + 1}", sb.toString(), Field.Store.YES))
            }
            return doc
        }

        fun modifyIndex(i: Int, dir: Directory) {
            when (i) {
                0 -> {
                    val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
                    w.deleteDocuments(Term("field2", "a11"))
                    w.deleteDocuments(Term("field2", "b30"))
                    w.close()
                }
                1 -> {
                    val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
                    w.forceMerge(1)
                    w.close()
                }
                2 -> {
                    val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
                    w.addDocument(createDocument(101, 4))
                    w.forceMerge(1)
                    w.addDocument(createDocument(102, 4))
                    w.addDocument(createDocument(103, 4))
                    w.close()
                }
                3 -> {
                    val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
                    w.addDocument(createDocument(101, 4))
                    w.close()
                }
            }
        }

        fun assertReaderClosed(reader: IndexReader, checkSubReaders: Boolean) {
            assertEquals(0, reader.getRefCount())
            if (checkSubReaders && reader is CompositeReader) {
                for (r in reader.sequentialSubReaders) {
                    assertReaderClosed(r, checkSubReaders)
                }
            }
        }
    }

    private data class ReaderCouple(
        val newReader: DirectoryReader?,
        val refreshedReader: DirectoryReader
    )

    abstract class ReaderThreadTask {
        @Volatile
        var stopped = false
        fun stop() {
            stopped = true
        }
        abstract fun run()
    }

    private class ReaderThread(private val task: ReaderThreadTask) : Thread() {
        var error: Throwable? = null
        fun stopThread() {
            task.stop()
        }

        override fun run() {
            try {
                task.run()
            } catch (r: Throwable) {
                error = r
            }
        }
    }

    abstract class TestReopen {
        abstract fun openReader(): DirectoryReader
        abstract fun modifyIndex(i: Int)
    }

    private class KeepAllCommits : IndexDeletionPolicy() {
        override fun onInit(commits: MutableList<out IndexCommit>) {}
        override fun onCommit(commits: MutableList<out IndexCommit>) {}
    }

    private fun newDirectory(): Directory = ByteBuffersDirectory()
}

object TestDirectoryReader {
    fun assertIndexEquals(index1: DirectoryReader, index2: DirectoryReader) {
        assertEquals(index1.numDocs(), index2.numDocs())
        assertEquals(index1.maxDoc(), index2.maxDoc())
    }
}

