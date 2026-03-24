/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.AssertingDirectoryReader
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper.FakeIOException
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomBoolean
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomLongBetween
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.Version
import kotlin.Comparator
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gnit.lucenekmp.jdkport.accumulateAndGet

@SuppressCodecs("SimpleText") // too slow here
class TestIndexWriterReader : LuceneTestCase() {
    private val numThreads = if (TEST_NIGHTLY) 5 else 2

    companion object {
        fun count(t: Term, r: IndexReader): Int {
            var count = 0
            val td = TestUtil.docs(random(), r, t.field(), BytesRef(t.text()), null, 0)

            if (td != null) {
                val liveDocs: Bits? = MultiBits.getLiveDocs(r)
                while (td.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    td.docID()
                    if (liveDocs == null || liveDocs.get(td.docID())) {
                        count++
                    }
                }
            }
            return count
        }

        fun createIndex(
            random: Random,
            dir1: Directory,
            indexName: String,
            multiSegment: Boolean
        ) {
            val w =
                IndexWriter(
                    dir1,
                    LuceneTestCase.newIndexWriterConfig(random, MockAnalyzer(random))
                        .setMergePolicy(LogDocMergePolicy())
                )
            for (i in 0 until 100) {
                w.addDocument(DocHelper.createDocument(i, indexName, 4))
            }
            if (!multiSegment) {
                w.forceMerge(1)
            }
            w.close()
        }

        fun createIndexNoClose(multiSegment: Boolean, indexName: String, w: IndexWriter) {
            for (i in 0 until 100) {
                w.addDocument(DocHelper.createDocument(i, indexName, 4))
            }
            if (!multiSegment) {
                w.forceMerge(1)
            }
        }

        private fun assertLeavesSorted(
            reader: DirectoryReader,
            leafSorter: Comparator<LeafReader>
        ) {
            val lrs = reader.leaves().map { it.reader() }
            val expectedSortedlrs = reader.leaves().map { it.reader() }.sortedWith(leafSorter)
            assertEquals(expectedSortedlrs, lrs)
        }
    }

    @Test
    fun testAddCloseOpen() {
        val dir1 = newDirectory()
        var iwc = newIndexWriterConfig(MockAnalyzer(random()))

        var writer = IndexWriter(dir1, iwc)
        for (i in 0 until 97) {
            val reader = DirectoryReader.open(writer)
            if (i == 0) {
                writer.addDocument(DocHelper.createDocument(i, "x", 1 + random().nextInt(5)))
            } else {
                val previous = random().nextInt(i)
                // a check if the reader is current here could fail since there might be
                // merges going on.
                when (random().nextInt(5)) {
                    0, 1, 2 -> writer.addDocument(DocHelper.createDocument(i, "x", 1 + random().nextInt(5)))
                    3 -> writer.updateDocument(
                        Term("id", previous.toString()),
                        DocHelper.createDocument(previous, "x", 1 + random().nextInt(5))
                    )
                    4 -> writer.deleteDocuments(Term("id", previous.toString()))
                }
            }
            assertFalse(reader.isCurrent)
            reader.close()
        }
        writer.forceMerge(1)
        var reader = DirectoryReader.open(writer)
        writer.commit()

        // A commit is now seen as a change to an NRT reader:
        assertFalse(reader.isCurrent)
        reader.close()
        reader = DirectoryReader.open(writer)
        assertTrue(reader.isCurrent)
        writer.close()

        assertTrue(reader.isCurrent)
        iwc = newIndexWriterConfig(MockAnalyzer(random()))
        writer = IndexWriter(dir1, iwc)
        assertTrue(reader.isCurrent)
        writer.addDocument(DocHelper.createDocument(1, "x", 1 + random().nextInt(5)))
        assertTrue(reader.isCurrent)
        writer.close()
        assertFalse(reader.isCurrent)
        reader.close()
        dir1.close()
    }

    @Test
    fun testUpdateDocument() {
        val doFullMerge = true

        val dir1 = newDirectory()
        var iwc = newIndexWriterConfig(MockAnalyzer(random()))
        if (iwc.maxBufferedDocs < 20) {
            iwc.setMaxBufferedDocs(20)
        }
        iwc.setMergePolicy(NoMergePolicy.INSTANCE)
        if (VERBOSE) {
            println("TEST: make index")
        }
        var writer = IndexWriter(dir1, iwc)

        createIndexNoClose(!doFullMerge, "index1", writer)

        var r1 = DirectoryReader.open(writer)
        assertTrue(r1.isCurrent)

        val id10 = r1.storedFields().document(10).getField("id")!!.stringValue()

        val newDoc = r1.storedFields().document(10)
        newDoc.removeField("id")
        newDoc.add(Field("id", "8000", DocHelper.STRING_TYPE_STORED_WITH_TVS))
        writer.updateDocument(Term("id", id10!!), newDoc)
        assertFalse(r1.isCurrent)

        println("TEST: now get reader")
        var r2 = DirectoryReader.open(writer)
        assertTrue(r2.isCurrent)
        assertEquals(0, count(Term("id", id10), r2))
        if (VERBOSE) {
            println("TEST: verify id")
        }
        assertEquals(1, count(Term("id", "8000"), r2))

        r1.close()
        assertTrue(r2.isCurrent)
        writer.close()
        assertFalse(r2.isCurrent)

        val r3 = DirectoryReader.open(dir1)
        assertTrue(r3.isCurrent)
        assertFalse(r2.isCurrent)
        assertEquals(0, count(Term("id", id10), r3))
        assertEquals(1, count(Term("id", "8000"), r3))

        writer = IndexWriter(dir1, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newTextField("field", "a b c", Field.Store.NO))
        writer.addDocument(doc)
        assertFalse(r2.isCurrent)
        assertTrue(r3.isCurrent)

        writer.close()

        assertFalse(r2.isCurrent)
        assertTrue(!r3.isCurrent)

        r2.close()
        r3.close()

        dir1.close()
    }

    @Test
    fun testIsCurrent() {
        val dir = newDirectory()
        var iwc = newIndexWriterConfig(MockAnalyzer(random()))

        var writer = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(newTextField("field", "a b c", Field.Store.NO))
        writer.addDocument(doc)
        writer.close()

        iwc = newIndexWriterConfig(MockAnalyzer(random()))
        writer = IndexWriter(dir, iwc)
        doc = Document()
        doc.add(newTextField("field", "a b c", Field.Store.NO))
        var nrtReader = DirectoryReader.open(writer)
        assertTrue(nrtReader.isCurrent)
        writer.addDocument(doc)
        assertFalse(nrtReader.isCurrent)
        writer.forceMerge(1)
        assertFalse(nrtReader.isCurrent)
        nrtReader.close()

        val dirReader = DirectoryReader.open(dir)
        nrtReader = DirectoryReader.open(writer)

        assertTrue(dirReader.isCurrent)
        assertTrue(nrtReader.isCurrent)
        assertEquals(2, nrtReader.maxDoc())
        assertEquals(1, dirReader.maxDoc())
        writer.close()
        assertFalse(nrtReader.isCurrent)
        assertFalse(dirReader.isCurrent)

        dirReader.close()
        nrtReader.close()
        dir.close()
    }

    /** Test using IW.addIndexes */
    @Test
    fun testAddIndexes() {
        val doFullMerge = false

        val dir1 = getAssertNoDeletesDirectory(newDirectory())
        val iwc =
            newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
        if (iwc.maxBufferedDocs < 20) {
            iwc.setMaxBufferedDocs(20)
        }
        iwc.setMergePolicy(NoMergePolicy.INSTANCE)
        val writer = IndexWriter(dir1, iwc)

        createIndexNoClose(!doFullMerge, "index1", writer)
        writer.flush(false, true)

        val dir2 = newDirectory()
        val writer2 = IndexWriter(dir2, newIndexWriterConfig(MockAnalyzer(random())))
        createIndexNoClose(!doFullMerge, "index2", writer2)
        writer2.close()

        var r0 = DirectoryReader.open(writer)
        assertTrue(r0.isCurrent)
        writer.addIndexes(dir2)
        assertFalse(r0.isCurrent)
        r0.close()

        val r1 = DirectoryReader.open(writer)
        assertTrue(r1.isCurrent)

        writer.commit()
        assertFalse(r1.isCurrent)

        assertEquals(200, r1.maxDoc())

        val index2df = r1.docFreq(Term("indexname", "index2"))

        assertEquals(100, index2df)

        val doc5 = r1.storedFields().document(5)
        assertEquals("index1", doc5.get("indexname"))
        val doc150 = r1.storedFields().document(150)
        assertEquals("index2", doc150.get("indexname"))
        r1.close()
        writer.close()
        dir1.close()
        dir2.close()
    }

    @Test
    fun testAddIndexes2() {
        val doFullMerge = false

        val dir1 = getAssertNoDeletesDirectory(newDirectory())
        val writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
            )

        val dir2 = newDirectory()
        val writer2 =
            IndexWriter(
                dir2,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
            )
        createIndexNoClose(!doFullMerge, "index2", writer2)
        writer2.close()

        writer.addIndexes(dir2)
        writer.addIndexes(dir2)
        writer.addIndexes(dir2)
        writer.addIndexes(dir2)
        writer.addIndexes(dir2)

        val r1 = DirectoryReader.open(writer)
        assertEquals(500, r1.maxDoc())

        r1.close()
        writer.close()
        dir1.close()
        dir2.close()
    }

    /** Deletes using IW.deleteDocuments */
    @Test
    fun testDeleteFromIndexWriter() {
        val doFullMerge = true

        val dir1 = getAssertNoDeletesDirectory(newDirectory())
        var writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
            )
        createIndexNoClose(!doFullMerge, "index1", writer)
        writer.flush(false, true)
        val r1 = DirectoryReader.open(writer)

        val id10 = r1.storedFields().document(10).getField("id")!!.stringValue()

        writer.deleteDocuments(Term("id", id10!!))
        val r2 = DirectoryReader.open(writer)
        assertEquals(1, count(Term("id", id10), r1))
        assertEquals(0, count(Term("id", id10), r2))

        val id50 = r1.storedFields().document(50).getField("id")!!.stringValue()
        assertEquals(1, count(Term("id", id50!!), r1))

        writer.deleteDocuments(Term("id", id50!!))

        val r3 = DirectoryReader.open(writer)
        assertEquals(0, count(Term("id", id10), r3))
        assertEquals(0, count(Term("id", id50), r3))

        val id75 = r1.storedFields().document(75).getField("id")!!.stringValue()
        writer.deleteDocuments(TermQuery(Term("id", id75!!)))
        val r4 = DirectoryReader.open(writer)
        assertEquals(1, count(Term("id", id75), r3))
        assertEquals(0, count(Term("id", id75), r4))

        r1.close()
        r2.close()
        r3.close()
        r4.close()
        writer.close()

        writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
            )
        val w2r1 = DirectoryReader.open(writer)
        assertEquals(0, count(Term("id", id10), w2r1))
        w2r1.close()
        writer.close()
        dir1.close()
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testAddIndexesAndDoDeletesThreads() {
        val numIter = if (TEST_NIGHTLY) 2 else 1
        val numDirs = if (TEST_NIGHTLY) 3 else 2

        val mainDir = getAssertNoDeletesDirectory(newDirectory())

        val mainWriter =
            IndexWriter(
                mainDir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(newLogMergePolicy())
                    .setMaxFullFlushMergeWaitMillis(0)
            )
        TestUtil.reduceOpenFiles(mainWriter)

        val addDirThreads = AddDirectoriesThreads(numIter, mainWriter)
        addDirThreads.launchThreads(numDirs)
        addDirThreads.joinThreads()

        assertEquals(addDirThreads.count.load(), addDirThreads.mainWriter.getDocStats().numDocs)

        addDirThreads.close(true)

        assertTrue(addDirThreads.failures.isEmpty())

        TestUtil.checkIndex(mainDir)

        val reader: IndexReader = DirectoryReader.open(mainDir)
        assertEquals(addDirThreads.count.load(), reader.numDocs())
        reader.close()

        addDirThreads.closeDir()
        mainDir.close()
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class AddDirectoriesThreads(
        numDirs: Int,
        mainWriter: IndexWriter
    ) {
        var addDir: Directory
        val NUM_INIT_DOCS = 100
        var numDirs: Int
        val threads = Array<org.gnit.lucenekmp.jdkport.Thread?>(if (TEST_NIGHTLY) 5 else 2) { null }
        var mainWriter: IndexWriter
        val failures: MutableList<Throwable> = ArrayList()
        private val failuresLock = ReentrantLock()
        var readers: Array<DirectoryReader?>
        var count = org.gnit.lucenekmp.jdkport.AtomicInteger(0)
        var numaddIndexes = org.gnit.lucenekmp.jdkport.AtomicInteger(0)

        init {
            this.numDirs = numDirs
            this.mainWriter = mainWriter
            addDir = newDirectory()
            val writer =
                IndexWriter(
                    addDir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxFullFlushMergeWaitMillis(0)
                        .setMaxBufferedDocs(2)
                )
            TestUtil.reduceOpenFiles(writer)
            for (i in 0 until NUM_INIT_DOCS) {
                val doc = DocHelper.createDocument(i, "addindex", 4)
                writer.addDocument(doc)
            }

            writer.close()

            readers = arrayOfNulls(numDirs)
            for (i in 0 until numDirs) {
                readers[i] = DirectoryReader.open(addDir)
            }
        }

        fun joinThreads() {
            for (i in threads.indices) {
                threads[i]!!.join()
            }
        }

        fun close(doWait: Boolean) {
            if (doWait) {
                mainWriter.close()
            } else {
                mainWriter.rollback()
            }
        }

        fun closeDir() {
            for (i in 0 until numDirs) {
                readers[i]!!.close()
            }
            addDir.close()
        }

        fun handle(t: Throwable) {
            t.printStackTrace()
            failuresLock.lock()
            try {
                failures.add(t)
            } finally {
                failuresLock.unlock()
            }
        }

        fun launchThreads(numIter: Int) {
            for (i in threads.indices) {
                threads[i] =
                    object : org.gnit.lucenekmp.jdkport.Thread() {
                        override fun run() {
                            try {
                                val dirs = arrayOfNulls<Directory>(numDirs)
                                for (k in 0 until numDirs) {
                                    dirs[k] = MockDirectoryWrapper(random(), TestUtil.ramCopyOf(addDir))
                                }
                                for (x in 0 until numIter) {
                                    doBody(x, dirs.requireNoNulls())
                                }
                            } catch (t: Throwable) {
                                handle(t)
                            }
                        }
                    }
            }
            for (i in threads.indices) {
                threads[i]!!.start()
            }
        }

        fun doBody(j: Int, dirs: Array<Directory>) {
            when (j % 4) {
                0 -> {
                    mainWriter.addIndexes(*dirs)
                    mainWriter.forceMerge(1)
                }
                1 -> {
                    mainWriter.addIndexes(*dirs)
                    numaddIndexes.incrementAndFetch()
                }
                2 -> TestUtil.addIndexesSlowly(mainWriter, *readers.requireNoNulls())
                3 -> mainWriter.commit()
            }
            count.accumulateAndGet(dirs.size * NUM_INIT_DOCS) { left, right -> left + right }
        }
    }

    @Test
    fun testIndexWriterReopenSegmentFullMerge() {
        doTestIndexWriterReopenSegment(true)
    }

    @Test
    fun testIndexWriterReopenSegment() {
        doTestIndexWriterReopenSegment(false)
    }

    fun doTestIndexWriterReopenSegment(doFullMerge: Boolean) {
        val dir1 = getAssertNoDeletesDirectory(newDirectory())
        var writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
            )
        val r1 = DirectoryReader.open(writer)
        assertEquals(0, r1.maxDoc())
        createIndexNoClose(false, "index1", writer)
        writer.flush(!doFullMerge, true)

        val iwr1 = DirectoryReader.open(writer)
        assertEquals(100, iwr1.maxDoc())

        val r2 = DirectoryReader.open(writer)
        assertEquals(100, r2.maxDoc())
        for (x in 10000 until 10100) {
            val d = DocHelper.createDocument(x, "index1", 5)
            writer.addDocument(d)
        }
        writer.flush(false, true)
        val iwr2 = DirectoryReader.open(writer)
        assertTrue(iwr2 !== r1)
        assertEquals(200, iwr2.maxDoc())
        val r3 = DirectoryReader.open(writer)
        assertTrue(r2 !== r3)
        assertEquals(200, r3.maxDoc())

        r1.close()
        iwr1.close()
        r2.close()
        r3.close()
        iwr2.close()
        writer.close()

        writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
            )
        val w2r1 = DirectoryReader.open(writer)
        assertEquals(200, w2r1.maxDoc())
        w2r1.close()
        writer.close()

        dir1.close()
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testMergeWarmer() {
        val dir1 = getAssertNoDeletesDirectory(newDirectory())
        val warmCount = org.gnit.lucenekmp.jdkport.AtomicInteger(0)
        val writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setMaxFullFlushMergeWaitMillis(0)
                    .setMergedSegmentWarmer { warmCount.incrementAndFetch() }
                    .setMergeScheduler(ConcurrentMergeScheduler())
                    .setMergePolicy(newLogMergePolicy())
            )

        createIndexNoClose(false, "test", writer)

        val r1 = DirectoryReader.open(writer)

        (writer.config.mergePolicy as LogMergePolicy).mergeFactor = 2

        val num = if (TEST_NIGHTLY) atLeast(100) else atLeast(10)
        for (i in 0 until num) {
            writer.addDocument(DocHelper.createDocument(i, "test", 4))
        }
        runBlocking { (writer.config.mergeScheduler as ConcurrentMergeScheduler).sync() }

        assertTrue(warmCount.load() > 0)
        val count = warmCount.load()

        writer.addDocument(DocHelper.createDocument(17, "test", 4))
        writer.forceMerge(1)
        assertTrue(warmCount.load() > count)

        writer.close()
        r1.close()
        dir1.close()
    }

    @Test
    fun testAfterCommit() {
        val dir1 = getAssertNoDeletesDirectory(newDirectory())
        val writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergeScheduler(ConcurrentMergeScheduler())
                    .setMaxFullFlushMergeWaitMillis(0)
            )
        writer.commit()

        createIndexNoClose(false, "test", writer)

        var r1 = DirectoryReader.open(writer)
        TestUtil.checkIndex(dir1)
        writer.commit()
        TestUtil.checkIndex(dir1)
        assertEquals(100, r1.numDocs())

        for (i in 0 until 10) {
            writer.addDocument(DocHelper.createDocument(i, "test", 4))
        }
        runBlocking { (writer.config.mergeScheduler as ConcurrentMergeScheduler).sync() }

        val r2 = DirectoryReader.openIfChanged(r1)
        if (r2 != null) {
            r1.close()
            r1 = r2
        }
        assertEquals(110, r1.numDocs())
        writer.close()
        r1.close()
        dir1.close()
    }

    // Make sure reader remains usable even if IndexWriter closes
    @Test
    fun testAfterClose() {
        val dir1 = getAssertNoDeletesDirectory(newDirectory())
        val writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
            )

        createIndexNoClose(false, "test", writer)

        val r = DirectoryReader.open(writer)
        writer.close()

        TestUtil.checkIndex(dir1)

        assertEquals(100, r.numDocs())
        val q: Query = TermQuery(Term("indexname", "test"))
        val searcher: IndexSearcher = newSearcher(r)
        assertEquals(100, searcher.count(q))

        expectThrows(AlreadyClosedException::class) { DirectoryReader.openIfChanged(r) }

        r.close()
        dir1.close()
    }

    // Stress test reopen during addIndexes
    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testDuringAddIndexes() {
        val dir1 = getAssertNoDeletesDirectory(newDirectory())
        val writer =
            IndexWriter(
                dir1,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxFullFlushMergeWaitMillis(0)
                    .setMergePolicy(newLogMergePolicy(2))
            )

        createIndexNoClose(false, "test", writer)
        writer.commit()

        val dirs = arrayOfNulls<Directory>(10)
        for (i in 0 until 10) {
            dirs[i] = MockDirectoryWrapper(random(), TestUtil.ramCopyOf(dir1))
        }

        var r = DirectoryReader.open(writer)

        val numIterations = 10
        val excs = mutableListOf<Throwable>()
        val excsLock = ReentrantLock()

        val threads = arrayOfNulls<org.gnit.lucenekmp.jdkport.Thread>(1)
        val threadDone = kotlin.concurrent.atomics.AtomicBoolean(false)
        for (i in threads.indices) {
            threads[i] =
                object : org.gnit.lucenekmp.jdkport.Thread() {
                    override fun run() {
                        var count = 0
                        do {
                            count++
                            try {
                                writer.addIndexes(*dirs.requireNoNulls())
                                writer.maybeMerge()
                            } catch (t: Throwable) {
                                excsLock.lock()
                                try {
                                    excs.add(t)
                                } finally {
                                    excsLock.unlock()
                                }
                                throw RuntimeException(t)
                            }
                        } while (count < numIterations)
                        threadDone.store(true)
                    }
                }
            threads[i]!!.setDaemon(true)
            threads[i]!!.start()
        }

        var lastCount = 0L
        while (threadDone.load() == false) {
            val r2 = DirectoryReader.openIfChanged(r)
            if (r2 != null) {
                r.close()
                r = r2
                val q: Query = TermQuery(Term("indexname", "test"))
                val searcher: IndexSearcher = newSearcher(r)
                val count = searcher.count(q).toLong()
                assertTrue(count >= lastCount)
                lastCount = count
            }
        }

        for (thread in threads) {
            thread!!.join()
        }
        val r2 = DirectoryReader.openIfChanged(r)
        if (r2 != null) {
            r.close()
            r = r2
        }
        val q: Query = TermQuery(Term("indexname", "test"))
        val searcher: IndexSearcher = newSearcher(r)
        val count = searcher.count(q)
        assertTrue(count >= lastCount)

        assertEquals(0, excs.size)
        r.close()
        if (dir1 is MockDirectoryWrapper) {
            val openDeletedFiles = dir1.openDeletedFiles
            assertEquals(0, openDeletedFiles.size, "openDeleted=$openDeletedFiles")
        }

        writer.close()

        dir1.close()
    }

    private fun getAssertNoDeletesDirectory(directory: org.gnit.lucenekmp.store.Directory): org.gnit.lucenekmp.store.Directory {
        if (directory is org.gnit.lucenekmp.tests.store.MockDirectoryWrapper) {
            directory.assertNoDeleteOpenFile = true
        }
        return directory
    }

    // Stress test reopen during add/delete
    @Test
    fun testDuringAddDelete() {
        val dir1 = newDirectory()
        val iwc =
            newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy(2))
        if (TEST_NIGHTLY) {
            iwc.setRAMBufferSizeMB(IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB)
            iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        }
        val writer = IndexWriter(dir1, iwc)

        createIndexNoClose(false, "test", writer)
        writer.commit()

        var r = DirectoryReader.open(writer)

        val iters = if (TEST_NIGHTLY) 1000 else 10
        val excs = mutableListOf<Throwable>()
        val excsLock = ReentrantLock()

        val threads = Array(numThreads) {
            object : org.gnit.lucenekmp.jdkport.Thread() {
                val random = Random(random().nextLong())

                override fun run() {
                    var count = 0
                    do {
                        try {
                            for (docUpto in 0 until 10) {
                                writer.addDocument(DocHelper.createDocument(10 * count + docUpto, "test", 4))
                            }
                            count++
                            val limit = count * 10
                            for (delUpto in 0 until 5) {
                                val x = random.nextInt(limit)
                                writer.deleteDocuments(Term("field3", "b$x"))
                            }
                        } catch (t: Throwable) {
                            excsLock.lock()
                            try {
                                excs.add(t)
                            } finally {
                                excsLock.unlock()
                            }
                            throw RuntimeException(t)
                        }
                    } while (count < iters)
                }
            }
        }
        for (thread in threads) {
            thread.setDaemon(true)
            thread.start()
        }

        var sum = 0
        for (thread in threads) {
            thread.join()
        }
        val r2 = DirectoryReader.openIfChanged(r)
        if (r2 != null) {
            r.close()
            r = r2
        }
        val q: Query = TermQuery(Term("indexname", "test"))
        val searcher: IndexSearcher = newSearcher(r)
        sum += searcher.count(q)
        assertTrue(sum > 0, "no documents found at all")

        assertEquals(0, excs.size)
        writer.close()

        r.close()
        dir1.close()
    }

    @Test
    fun testForceMergeDeletes() {
        val dir = newDirectory()
        val w =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy())
            )
        val doc = Document()
        doc.add(newTextField("field", "a b c", Field.Store.NO))
        val id = newStringField("id", "", Field.Store.NO)
        doc.add(id)
        id.setStringValue("0")
        w.addDocument(doc)
        id.setStringValue("1")
        w.addDocument(doc)
        w.deleteDocuments(Term("id", "0"))

        var r: IndexReader = DirectoryReader.open(w)
        w.forceMergeDeletes()
        w.close()
        r.close()
        r = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs())
        assertFalse(r.hasDeletions())
        r.close()
        dir.close()
    }

    @Test
    fun testDeletesNumDocs() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val doc = Document()
        doc.add(newTextField("field", "a b c", Field.Store.NO))
        val id = newStringField("id", "", Field.Store.NO)
        doc.add(id)
        id.setStringValue("0")
        w.addDocument(doc)
        id.setStringValue("1")
        w.addDocument(doc)
        var r: IndexReader = DirectoryReader.open(w)
        assertEquals(2, r.numDocs())
        r.close()

        w.deleteDocuments(Term("id", "0"))
        r = DirectoryReader.open(w)
        assertEquals(1, r.numDocs())
        r.close()

        w.deleteDocuments(Term("id", "1"))
        r = DirectoryReader.open(w)
        assertEquals(0, r.numDocs())
        r.close()

        w.close()
        dir.close()
    }

    @Test
    fun testEmptyIndex() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val r = DirectoryReader.open(w)
        assertEquals(0, r.numDocs())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testSegmentWarmer() {
        val dir = newDirectory()
        val didWarm = kotlin.concurrent.atomics.AtomicBoolean(false)
        val mp = newLogMergePolicy(10)
        mp.targetSearchConcurrency = 1
        val w =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setReaderPooling(true)
                    .setMergedSegmentWarmer { reader ->
                        val s: IndexSearcher = newSearcher(reader)
                        val count = s.count(TermQuery(Term("foo", "bar")))
                        assertEquals(20, count)
                        didWarm.store(true)
                    }
                    .setMergePolicy(mp)
            )

        val doc = Document()
        doc.add(newStringField("foo", "bar", Field.Store.NO))
        for (i in 0 until 20) {
            w.addDocument(doc)
        }
        w.waitForMerges()
        w.close()
        dir.close()
        assertTrue(didWarm.load())
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testSimpleMergedSegmentWarmer() {
        val dir = newDirectory()
        val didWarm = kotlin.concurrent.atomics.AtomicBoolean(false)
        val infoStream =
            object : InfoStream() {
                override fun close() {}

                override fun message(component: String, message: String) {
                    if ("SMSW" == component) {
                        didWarm.store(true)
                    }
                }

                override fun isEnabled(component: String): Boolean {
                    return true
                }
            }
        val mp = newLogMergePolicy(10)
        mp.targetSearchConcurrency = 1
        val w =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setReaderPooling(true)
                    .setInfoStream(infoStream)
                    .setMergedSegmentWarmer(SimpleMergedSegmentWarmer(infoStream))
                    .setMergePolicy(mp)
            )

        val doc = Document()
        doc.add(newStringField("foo", "bar", Field.Store.NO))
        for (i in 0 until 20) {
            w.addDocument(doc)
        }
        w.waitForMerges()
        w.close()
        dir.close()
        assertTrue(didWarm.load())
    }

    @Test
    fun testReopenAfterNoRealChange() {
        val d = getAssertNoDeletesDirectory(newDirectory())
        val w =
            IndexWriter(
                d, newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
            )

        val r = DirectoryReader.open(w)

        val r2 = DirectoryReader.openIfChanged(r)
        assertNull(r2)

        w.addDocument(Document())
        val r3 = DirectoryReader.openIfChanged(r)
        assertNotNull(r3)
        assertTrue(r3.version != r.version)
        assertTrue(r3.isCurrent)

        w.deleteDocuments(Term("foo", "bar"))

        assertFalse(r3.isCurrent)
        val r4 = DirectoryReader.openIfChanged(r3)
        assertNull(r4)

        w.deleteDocuments(Term("foo", "bar"))
        val r5 = DirectoryReader.openIfChanged(r3, w)
        assertNull(r5)

        r3.close()

        w.close()
        d.close()
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testNRTOpenExceptions() {
        val dir = getAssertNoDeletesDirectory(newMockDirectory()) as MockDirectoryWrapper
        val shouldFail = kotlin.concurrent.atomics.AtomicBoolean(false)
        dir.failOn(
            object : MockDirectoryWrapper.Failure() {
                override fun eval(dir: MockDirectoryWrapper) {
                    if (shouldFail.load()) {
                        if (callStackContainsAnyOf("getReadOnlyClone")) {
                            if (VERBOSE) {
                                println("TEST: now fail; exc:")
                                Throwable().printStackTrace()
                            }
                            shouldFail.store(false)
                            throw FakeIOException()
                        }
                    }
                }
            }
        )

        val conf =
            newIndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        val writer = IndexWriter(dir, conf)

        writer.addDocument(Document())
        DirectoryReader.open(writer).close()

        writer.addDocument(Document())

        for (i in 0 until 2) {
            shouldFail.store(true)
            expectThrows(FakeIOException::class) { DirectoryReader.open(writer).close() }
        }

        writer.close()
        dir.close()
    }

    /** Make sure if all we do is open NRT reader against writer, we don't see merge starvation. */
    @Test
    fun testTooManySegments() {
        val dir = getAssertNoDeletesDirectory(ByteBuffersDirectory())
        val iwc =
            IndexWriterConfig(MockAnalyzer(random())).setMaxFullFlushMergeWaitMillis(0)
        val w = IndexWriter(dir, iwc)
        for (i in 0 until 500) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            w.addDocument(doc)
            val r: IndexReader = DirectoryReader.open(w)
            assertTrue(r.leaves().size < 100)
            r.close()
        }
        w.close()
        dir.close()
    }

    // LUCENE-5912: make sure when you reopen an NRT reader using a commit point, the SegmentReaders
    // are in fact shared:
    @Test
    fun testReopenNRTReaderOnCommit() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w = IndexWriter(dir, iwc)
        w.addDocument(Document())

        val r1 = DirectoryReader.open(w)
        assertEquals(1, r1.leaves().size)
        w.addDocument(Document())
        w.commit()

        val commits = DirectoryReader.listCommits(dir)
        assertEquals(1, commits.size)
        val r2 = DirectoryReader.openIfChanged(r1, commits[0])!!
        assertEquals(2, r2.leaves().size)

        assertTrue(r1.leaves()[0].reader() === r2.leaves()[0].reader())
        r1.close()
        r2.close()
        w.close()
        dir.close()
    }

    @Test
    fun testIndexReaderWriterWithLeafSorter() {
        val FIELD_NAME = "field1"
        val ASC_SORT = randomBoolean()
        val MISSING_VALUE = if (ASC_SORT) Long.MAX_VALUE else Long.MIN_VALUE

        var leafSorter =
            Comparator<LeafReader> { reader1, reader2 ->
                fun leafValue(reader: LeafReader): Long {
                    return try {
                        val points = reader.getPointValues(FIELD_NAME)
                        if (points != null) {
                            val sortValue =
                                if (ASC_SORT) points.minPackedValue else points.maxPackedValue
                            if (sortValue != null) {
                                return LongPoint.decodeDimension(sortValue, 0)
                            }
                        }
                        MISSING_VALUE
                    } catch (_: okio.IOException) {
                        MISSING_VALUE
                    }
                }
                leafValue(reader1).compareTo(leafValue(reader2))
            }
        if (!ASC_SORT) {
            leafSorter = leafSorter.reversed()
        }

        val NUM_DOCS = atLeast(30)
        val dir = newDirectory()
        val iwc = IndexWriterConfig()
        iwc.setLeafSorter(leafSorter)
        val writer = IndexWriter(dir, iwc)
        for (i in 0 until NUM_DOCS) {
            val doc = Document()
            doc.add(LongPoint(FIELD_NAME, randomLongBetween(1, 99)))
            writer.addDocument(doc)
            if (i > 0 && i % 10 == 0) {
                writer.flush()
            }
        }

        DirectoryReader.open(writer).use { reader ->
            assertLeavesSorted(reader, leafSorter)

            val FIRST_VALUE = if (ASC_SORT) 0L else 100L
            for (i in 0 until 10) {
                val doc = Document()
                doc.add(LongPoint(FIELD_NAME, FIRST_VALUE))
                writer.addDocument(doc)
            }
            writer.commit()

            DirectoryReader.openIfChanged(reader)?.use { reader2 ->
                assertLeavesSorted(reader2, leafSorter)
            }
        }

        DirectoryReader.open(dir, leafSorter).use { reader ->
            assertLeavesSorted(reader, leafSorter)

            val FIRST_VALUE = if (ASC_SORT) 0L else 100L
            for (i in 0 until 10) {
                val doc = Document()
                doc.add(LongPoint(FIELD_NAME, FIRST_VALUE))
                writer.addDocument(doc)
            }
            writer.commit()

            DirectoryReader.openIfChanged(reader)?.use { reader2 ->
                assertLeavesSorted(reader2, leafSorter)
            }
        }

        AssertingDirectoryReader(DirectoryReader.open(dir, leafSorter)).use { reader ->
            assertLeavesSorted(reader, leafSorter)

            val FIRST_VALUE = if (ASC_SORT) 0L else 100L
            for (i in 0 until 10) {
                val doc = Document()
                doc.add(LongPoint(FIELD_NAME, FIRST_VALUE))
                writer.addDocument(doc)
            }
            writer.commit()

            DirectoryReader.openIfChanged(reader)?.use { reader2 ->
                assertLeavesSorted(reader2, leafSorter)
            }
        }

        val commits = DirectoryReader.listCommits(dir)
        val latestCommit = commits[commits.size - 1]
        DirectoryReader.open(latestCommit, Version.MIN_SUPPORTED_MAJOR, leafSorter).use { reader ->
            assertLeavesSorted(reader, leafSorter)

            val FIRST_VALUE = if (ASC_SORT) 0L else 100L
            for (i in 0 until 10) {
                val doc = Document()
                doc.add(LongPoint(FIELD_NAME, FIRST_VALUE))
                writer.addDocument(doc)
            }
            writer.commit()

            DirectoryReader.openIfChanged(reader)?.use { reader2 ->
                assertLeavesSorted(reader2, leafSorter)
            }
        }

        writer.close()
        dir.close()
    }
}
