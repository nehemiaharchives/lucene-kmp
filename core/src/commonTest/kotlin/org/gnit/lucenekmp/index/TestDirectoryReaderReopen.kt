package org.gnit.lucenekmp.index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DocumentStoredFieldVisitor
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper.FakeIOException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class TestDirectoryReaderReopen : LuceneTestCase() {
    private val createReaderMutex = ReentrantLock()

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
            },
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
            },
        )
        dir2.close()
    }

    @Test
    fun testCommitReopen() {
        val dir = newDirectory()
        doTestReopenWithCommit(random(), dir, true)
        dir.close()
    }

    @Test
    fun testCommitRecreate() {
        val dir = newDirectory()
        doTestReopenWithCommit(random(), dir, false)
        dir.close()
    }

    private fun doTestReopenWithCommit(random: Random, dir: Directory, withReopen: Boolean) {
        val iwriter =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random))
                    .setOpenMode(OpenMode.CREATE)
                    .setMergeScheduler(SerialMergeScheduler())
                    .setMergePolicy(newLogMergePolicy()),
            )
        iwriter.commit()
        var reader = DirectoryReader.open(dir)
        try {
            val m = 3
            val customType = FieldType(TextField.TYPE_STORED)
            customType.setTokenized(false)
            val customType2 = FieldType(TextField.TYPE_STORED)
            customType2.setTokenized(false)
            customType2.setOmitNorms(true)
            val customType3 = FieldType()
            customType3.setStored(true)
            for (i in 0..<4) {
                for (j in 0..<m) {
                    val doc = Document()
                    doc.add(newField("id", "${i}_${j}", customType))
                    doc.add(newField("id2", "${i}_${j}", customType2))
                    doc.add(newField("id3", "${i}_${j}", customType3))
                    iwriter.addDocument(doc)
                    if (i > 0) {
                        val k = i - 1
                        val n = j + k * m
                        val visitor = DocumentStoredFieldVisitor()
                        reader.storedFields().document(n, visitor)
                        val prevItereationDoc = visitor.document
                        assertNotNull(prevItereationDoc)
                        val id = prevItereationDoc.get("id")
                        assertEquals("${k}_${j}", id)
                    }
                }
                iwriter.commit()
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

        for (i in 1..<4) {
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

    @Test
    fun testThreadSafety() = runBlocking {
        val dir = newDirectory()
        val n = TestUtil.nextInt(random(), 20, 40)

        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        for (i in 0..<n) {
            writer.addDocument(createDocument(i, 3))
        }
        writer.forceMerge(1)
        writer.close()

        val test =
            object : TestReopen() {
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
        val firstReader = DirectoryReader.open(dir)
        var reader = firstReader

        val threads = arrayOfNulls<ReaderThread>(n)
        val readersToClose = mutableSetOf<DirectoryReader>()

        for (i in 0..<n) {
            if (i % 2 == 0) {
                val refreshed = DirectoryReader.openIfChanged(reader)
                if (refreshed != null) {
                    readersToClose.add(reader)
                    reader = refreshed
                }
            }
            val r = reader

            val index = i
            val task: ReaderThreadTask =
                if (i < 4 || (i >= 10 && i < 14) || i > 18) {
                    object : ReaderThreadTask() {
                        override suspend fun run() {
                            val rnd = random()
                            while (!stopped) {
                                if (index % 2 == 0) {
                                    val c = refreshReader(r, test, index, true)
                                    c.newReader?.let { readersToClose.add(it) }
                                    readersToClose.add(c.refreshedReader)
                                    readers.add(c)
                                    break
                                } else {
                                    var refreshed = DirectoryReader.openIfChanged(r)
                                    if (refreshed == null) {
                                        refreshed = r
                                    }

                                    val searcher = newSearcher(refreshed)
                                    val hits: Array<ScoreDoc> =
                                        searcher.search(
                                            TermQuery(Term("field1", "a${rnd.nextInt(refreshed.maxDoc())}")),
                                            1000,
                                        ).scoreDocs
                                    if (hits.isNotEmpty()) {
                                        searcher.storedFields().document(hits[0].doc)
                                    }
                                    if (refreshed !== r) {
                                        refreshed.close()
                                    }
                                }
                                delay(TestUtil.nextInt(random(), 1, 100).toLong())
                            }
                        }
                    }
                } else {
                    object : ReaderThreadTask() {
                        override suspend fun run() {
                            val rnd = random()
                            while (!stopped) {
                                val numReaders = readers.size
                                if (numReaders > 0) {
                                    val c = readers[rnd.nextInt(numReaders)]
                                    TestDirectoryReader.assertIndexEquals(c.newReader!!, c.refreshedReader)
                                }
                                delay(TestUtil.nextInt(random(), 1, 100).toLong())
                            }
                        }
                    }
                }

            threads[i] = ReaderThread(task)
            threads[i]!!.start()
        }

        delay(1000)

        for (i in 0..<n) {
            threads[i]?.stopThread()
        }

        for (i in 0..<n) {
            val thread = threads[i]
            if (thread != null) {
                thread.join()
                if (thread.error != null) {
                    val msg = "Error occurred in thread ${thread.name}:\n${thread.error!!.message}"
                    fail(msg)
                }
            }
        }

        for (readerToClose in readersToClose) {
            readerToClose.close()
        }

        firstReader.close()
        reader.close()

        for (readerToClose in readersToClose) {
            assertReaderClosed(readerToClose, true)
        }

        assertReaderClosed(reader, true)
        assertReaderClosed(firstReader, true)

        dir.close()
    }

    private class ReaderCouple(
        var newReader: DirectoryReader?,
        var refreshedReader: DirectoryReader,
    )

    abstract class ReaderThreadTask {
        @Volatile
        protected var stopped = false

        fun stop() {
            this.stopped = true
        }

        abstract suspend fun run()
    }

    private class ReaderThread(
        var task: ReaderThreadTask,
    ) {
        var error: Throwable? = null
        var name: String = "ReaderThread"
        private var job: Job? = null

        fun stopThread() {
            this.task.stop()
        }

        fun start() {
            job =
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        this@ReaderThread.task.run()
                    } catch (r: Throwable) {
                        r.printStackTrace()
                        this@ReaderThread.error = r
                    }
                }
        }

        suspend fun join() {
            job?.join()
        }
    }

    private fun refreshReader(reader: DirectoryReader, hasChanges: Boolean): ReaderCouple {
        return refreshReader(reader, null, -1, hasChanges)
    }

    private fun refreshReader(
        reader: DirectoryReader,
        test: TestReopen?,
        modify: Int,
        hasChanges: Boolean,
    ): ReaderCouple {
        createReaderMutex.lock()
        try {
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

            return ReaderCouple(r, refreshed)
        } finally {
            createReaderMutex.unlock()
        }
    }

    companion object {
        fun createIndex(random: Random, dir: Directory, multiSegment: Boolean) {
            val w =
                IndexWriter(
                    dir,
                    LuceneTestCase.newIndexWriterConfig(random, MockAnalyzer(random))
                        .setMergePolicy(LogDocMergePolicy()),
                )

            for (i in 0..<100) {
                w.addDocument(createDocument(i, 4))
                if (multiSegment && (i % 10) == 0) {
                    w.commit()
                }
            }

            if (!multiSegment) {
                w.forceMerge(1)
            }

            w.close()

            val r = DirectoryReader.open(dir)
            if (multiSegment) {
                assertTrue(r.leaves().size > 1)
            } else {
                assertTrue(r.leaves().size == 1)
            }
            r.close()
        }

        fun createDocument(n: Int, numFields: Int): Document {
            val sb = StringBuilder()
            val doc = Document()
            sb.append("a")
            sb.append(n)
            val customType2 = FieldType(TextField.TYPE_STORED)
            customType2.setTokenized(false)
            customType2.setOmitNorms(true)
            val customType3 = FieldType()
            customType3.setStored(true)
            doc.add(TextField("field1", sb.toString(), Field.Store.YES))
            doc.add(Field("fielda", sb.toString(), customType2))
            doc.add(Field("fieldb", sb.toString(), customType3))
            sb.append(" b")
            sb.append(n)
            for (i in 1..<numFields) {
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
                val subReaders = reader.sequentialSubReaders
                for (r in subReaders) {
                    assertReaderClosed(r, checkSubReaders)
                }
            }
        }
    }

    abstract class TestReopen {
        abstract fun openReader(): DirectoryReader

        abstract fun modifyIndex(i: Int)
    }

    class KeepAllCommits : IndexDeletionPolicy() {
        override fun onInit(commits: MutableList<out IndexCommit>) {}

        override fun onCommit(commits: MutableList<out IndexCommit>) {}
    }

    @Test
    fun testReopenOnCommit() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(KeepAllCommits())
                    .setMaxBufferedDocs(-1)
                    .setMergePolicy(newLogMergePolicy(10)),
            )
        for (i in 0..<4) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            writer.addDocument(doc)
            val data = HashMap<String, String>()
            data["index"] = "$i"
            writer.setLiveCommitData(data.entries)
            writer.commit()
        }
        for (i in 0..<4) {
            writer.deleteDocuments(Term("id", "$i"))
            val data = HashMap<String, String>()
            data["index"] = "${4 + i}"
            writer.setLiveCommitData(data.entries)
            writer.commit()
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
            val v =
                if (s.isEmpty()) {
                    -1
                } else {
                    s["index"]!!.toInt()
                }
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
        val dir = newDirectory()

        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newStringField("field", "value", Field.Store.NO))
        w.addDocument(doc)
        w.commit()
        val commits = DirectoryReader.listCommits(dir)
        assertEquals(1, commits.size)
        w.addDocument(doc)
        val r = DirectoryReader.open(w)

        assertEquals(2, r.numDocs())
        val r2 = DirectoryReader.openIfChanged(r, commits[0])
        assertNotNull(r2)
        r.close()
        assertEquals(1, r2.numDocs())
        w.close()
        r2.close()
        dir.close()
    }

    @Test
    fun testOverDecRefDuringReopen() {
        val dir = newMockDirectory()

        val iwc =
            IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        iwc.setCodec(TestUtil.getDefaultCodec())
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(newStringField("id", "id", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "id2", Field.Store.NO))
        w.addDocument(doc)
        w.commit()

        val r = DirectoryReader.open(dir)

        w.deleteDocuments(Term("id", "id"))
        w.commit()

        dir.failOn(
            object : MockDirectoryWrapper.Failure() {
                var failed = false

                override fun eval(dir: MockDirectoryWrapper) {
                    if (failed) {
                        return
                    }
                    if (callStackContainsAnyOf("readLiveDocs")) {
                        failed = true
                        throw FakeIOException()
                    }
                }
            },
        )

        expectThrows(FakeIOException::class) {
            DirectoryReader.openIfChanged(r)
        }

        val s = newSearcher(r)
        assertEquals(1, s.count(TermQuery(Term("id", "id"))))

        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testNPEAfterInvalidReindex1() {
        val dir = ByteBuffersDirectory()

        var w =
            IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE),
            )
        var doc = Document()
        doc.add(newStringField("id", "id", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "id2", Field.Store.NO))
        w.addDocument(doc)
        w.deleteDocuments(Term("id", "id"))
        w.commit()
        w.close()

        val r = DirectoryReader.open(dir)

        for (fileName in dir.listAll()) {
            dir.deleteFile(fileName)
        }

        w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
        doc = Document()
        doc.add(newStringField("id", "id", Field.Store.NO))
        doc.add(NumericDocValuesField("ndv", 13))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "id2", Field.Store.NO))
        w.addDocument(doc)
        w.commit()
        doc = Document()
        doc.add(newStringField("id", "id2", Field.Store.NO))
        w.addDocument(doc)
        w.updateNumericDocValue(Term("id", "id"), "ndv", 17L)
        w.commit()
        w.close()

        expectThrows(IllegalStateException::class) {
            DirectoryReader.openIfChanged(r)
        }

        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testNPEAfterInvalidReindex2() {
        val dir = ByteBuffersDirectory()

        var w =
            IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE),
            )
        var doc = Document()
        doc.add(newStringField("id", "id", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "id2", Field.Store.NO))
        w.addDocument(doc)
        w.deleteDocuments(Term("id", "id"))
        w.commit()
        w.close()

        val r = DirectoryReader.open(dir)

        for (name in dir.listAll()) {
            dir.deleteFile(name)
        }

        w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
        doc = Document()
        doc.add(newStringField("id", "id", Field.Store.NO))
        doc.add(NumericDocValuesField("ndv", 13))
        w.addDocument(doc)
        w.commit()
        doc = Document()
        doc.add(newStringField("id", "id2", Field.Store.NO))
        w.addDocument(doc)
        w.commit()
        w.close()

        expectThrows(IllegalStateException::class) {
            DirectoryReader.openIfChanged(r)
        }

        r.close()
        dir.close()
    }

    @Test
    fun testNRTMdeletes() {
        val dir = newDirectory()
        val iwc =
            IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        val snapshotter = SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy())
        iwc.setIndexDeletionPolicy(snapshotter)
        val writer = IndexWriter(dir, iwc)
        writer.commit()

        var doc = Document()
        doc.add(StringField("key", "value1", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(StringField("key", "value2", Field.Store.YES))
        writer.addDocument(doc)

        writer.commit()

        val ic1 = snapshotter.snapshot()

        doc = Document()
        doc.add(StringField("key", "value3", Field.Store.YES))
        writer.updateDocument(Term("key", "value1"), doc)

        writer.commit()

        val ic2 = snapshotter.snapshot()
        val latest = DirectoryReader.open(ic2)
        assertEquals(2, latest.leaves().size)

        val oldest = DirectoryReader.openIfChanged(latest, ic1)
        assertEquals(1, oldest!!.leaves().size)

        assertSame(
            latest.leaves()[0].reader().coreCacheHelper!!.key,
            oldest.leaves()[0].reader().coreCacheHelper!!.key,
        )

        latest.close()
        oldest.close()

        snapshotter.release(ic1)
        snapshotter.release(ic2)
        writer.close()
        dir.close()
    }

    @Test
    fun testNRTMdeletes2() {
        val dir = newDirectory()
        val iwc =
            IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        val snapshotter = SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy())
        iwc.setIndexDeletionPolicy(snapshotter)
        val writer = IndexWriter(dir, iwc)
        writer.commit()

        var doc = Document()
        doc.add(StringField("key", "value1", Field.Store.YES))
        writer.addDocument(doc)

        doc = Document()
        doc.add(StringField("key", "value2", Field.Store.YES))
        writer.addDocument(doc)

        writer.commit()

        val ic1 = snapshotter.snapshot()

        doc = Document()
        doc.add(StringField("key", "value3", Field.Store.YES))
        writer.updateDocument(Term("key", "value1"), doc)

        val latest = DirectoryReader.open(writer)
        assertEquals(2, latest.leaves().size)

        val oldest = DirectoryReader.openIfChanged(latest, ic1)!!

        assertEquals(2, oldest.numDocs())
        assertFalse(oldest.hasDeletions())

        snapshotter.release(ic1)
        assertEquals(1, oldest.leaves().size)

        assertSame(
            latest.leaves()[0].reader().coreCacheHelper!!.key,
            oldest.leaves()[0].reader().coreCacheHelper!!.key,
        )

        latest.close()
        oldest.close()

        writer.close()
        dir.close()
    }

    @Test
    fun testNRTMupdates() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val snapshotter = SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy())
        iwc.setIndexDeletionPolicy(snapshotter)
        val writer = IndexWriter(dir, iwc)
        writer.commit()

        val doc = Document()
        doc.add(StringField("key", "value1", Field.Store.YES))
        doc.add(NumericDocValuesField("dv", 1))
        writer.addDocument(doc)

        writer.commit()

        val ic1 = snapshotter.snapshot()

        writer.updateNumericDocValue(Term("key", "value1"), "dv", 2)

        writer.commit()

        val ic2 = snapshotter.snapshot()
        val latest = DirectoryReader.open(ic2)
        assertEquals(1, latest.leaves().size)

        val oldest = DirectoryReader.openIfChanged(latest, ic1)!!
        assertEquals(1, oldest.leaves().size)

        assertSame(
            latest.leaves()[0].reader().coreCacheHelper!!.key,
            oldest.leaves()[0].reader().coreCacheHelper!!.key,
        )

        var values = getOnlyLeafReader(oldest).getNumericDocValues("dv")!!
        assertEquals(0, values.nextDoc())
        assertEquals(1, values.longValue())

        values = getOnlyLeafReader(latest).getNumericDocValues("dv")!!
        assertEquals(0, values.nextDoc())
        assertEquals(2, values.longValue())

        latest.close()
        oldest.close()

        snapshotter.release(ic1)
        snapshotter.release(ic2)
        writer.close()
        dir.close()
    }

    @Test
    fun testNRTMupdates2() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val snapshotter = SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy())
        iwc.setIndexDeletionPolicy(snapshotter)
        val writer = IndexWriter(dir, iwc)
        writer.commit()

        val doc = Document()
        doc.add(StringField("key", "value1", Field.Store.YES))
        doc.add(NumericDocValuesField("dv", 1))
        writer.addDocument(doc)

        writer.commit()

        val ic1 = snapshotter.snapshot()

        writer.updateNumericDocValue(Term("key", "value1"), "dv", 2)

        val latest = DirectoryReader.open(writer)
        assertEquals(1, latest.leaves().size)

        val oldest = DirectoryReader.openIfChanged(latest, ic1)!!
        assertEquals(1, oldest.leaves().size)

        assertSame(
            latest.leaves()[0].reader().coreCacheHelper!!.key,
            oldest.leaves()[0].reader().coreCacheHelper!!.key,
        )

        var values = getOnlyLeafReader(oldest).getNumericDocValues("dv")!!
        assertEquals(0, values.nextDoc())
        assertEquals(1, values.longValue())

        values = getOnlyLeafReader(latest).getNumericDocValues("dv")!!
        assertEquals(0, values.nextDoc())
        assertEquals(2, values.longValue())

        latest.close()
        oldest.close()

        snapshotter.release(ic1)
        writer.close()
        dir.close()
    }

    @Test
    fun testDeleteIndexFilesWhileReaderStillOpen() {
        val dir = ByteBuffersDirectory()
        var w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))
        var doc = Document()
        doc.add(newStringField("field", "value", Field.Store.NO))
        w.addDocument(doc)
        w.close()

        val r = DirectoryReader.open(dir)

        for (file in dir.listAll()) {
            dir.deleteFile(file)
        }

        w =
            IndexWriter(
                dir,
                IndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE),
            )
        doc = Document()
        doc.add(newStringField("field", "value", Field.Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(newStringField("field", "value2", Field.Store.NO))
        w.addDocument(doc)

        w.commit()

        w.deleteDocuments(Term("field", "value2"))

        w.addDocument(doc)

        w.close()

        expectThrows(IllegalStateException::class) {
            DirectoryReader.openIfChanged(r)
        }
    }

    @Test
    fun testReuseUnchangedLeafReaderOnDVUpdate() {
        val dir = newDirectory()
        val indexWriterConfig = newIndexWriterConfig()
        indexWriterConfig.setMergePolicy(NoMergePolicy.INSTANCE)
        val writer = IndexWriter(dir, indexWriterConfig)

        var doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        doc.add(NumericDocValuesField("some_docvalue", 2))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        doc.add(StringField("version", "1", Field.Store.YES))
        writer.addDocument(doc)
        writer.commit()
        var reader = DirectoryReader.open(dir)
        assertEquals(2, reader.numDocs())
        assertEquals(2, reader.maxDoc())
        assertEquals(0, reader.numDeletedDocs())

        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        doc.add(StringField("version", "2", Field.Store.YES))
        writer.updateDocValues(Term("id", "1"), NumericDocValuesField("some_docvalue", 1))
        writer.commit()
        var newReader = DirectoryReader.openIfChanged(reader)
        assertNotSame(newReader, reader)
        reader.close()
        reader = newReader!!
        assertEquals(2, reader.numDocs())
        assertEquals(2, reader.maxDoc())
        assertEquals(0, reader.numDeletedDocs())

        doc = Document()
        doc.add(StringField("id", "3", Field.Store.YES))
        doc.add(StringField("version", "3", Field.Store.YES))
        writer.updateDocument(Term("id", "3"), doc)
        writer.commit()

        newReader = DirectoryReader.openIfChanged(reader)
        assertNotSame(newReader, reader)
        assertEquals(2, newReader!!.sequentialSubReaders.size)
        assertEquals(1, reader.sequentialSubReaders.size)
        assertSame(reader.sequentialSubReaders[0], newReader.sequentialSubReaders[0])
        reader.close()
        reader = newReader
        assertEquals(3, reader.numDocs())
        assertEquals(3, reader.maxDoc())
        assertEquals(0, reader.numDeletedDocs())
        IOUtils.close(reader, writer, dir)
    }
}
