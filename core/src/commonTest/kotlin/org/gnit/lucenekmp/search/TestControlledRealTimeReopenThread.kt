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
package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexCommit
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.KeepOnlyLastCommitDeletionPolicy
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.SnapshotDeletionPolicy
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.ThreadLocal
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.NRTCachingDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.index.ThreadedIndexingAndSearchingTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalAtomicApi::class)
class TestControlledRealTimeReopenThread : ThreadedIndexingAndSearchingTestCase() {

    // Not guaranteed to reflect deletes:
    private lateinit var nrtNoDeletes: SearcherManager

    // Is guaranteed to reflect deletes:
    private lateinit var nrtDeletes: SearcherManager

    private lateinit var genWriter: IndexWriter

    private lateinit var nrtDeletesThread: ControlledRealTimeReopenThread<IndexSearcher>
    private lateinit var nrtNoDeletesThread: ControlledRealTimeReopenThread<IndexSearcher>

    private val lastGens = ThreadLocal<Long>()
    private var warmCalled = false

    @Test
    fun testControlledRealTimeReopenThread() {
        runTest("TestControlledRealTimeReopenThread")
    }

    override fun getFinalSearcher(): IndexSearcher {
        if (VERBOSE) {
            println("TEST: finalSearcher maxGen=$maxGen")
        }
        nrtDeletesThread.waitForGeneration(maxGen)
        return nrtDeletes.acquire()
    }

    override fun getDirectory(inp: Directory): Directory {
        // Randomly swap in NRTCachingDir
        return if (random().nextBoolean()) {
            if (VERBOSE) {
                println("TEST: wrap NRTCachingDir")
            }
            NRTCachingDirectory(inp, 5.0, 60.0)
        } else {
            inp
        }
    }

    override fun updateDocuments(id: Term, docs: List<Iterable<IndexableField>>) {
        val gen = genWriter.updateDocuments(id, docs)

        // Randomly verify the update "took":
        if (random().nextInt(20) == 2) {
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: verify updateDocuments $id gen=$gen")
            }
            nrtDeletesThread.waitForGeneration(gen)
            assertTrue(gen <= nrtDeletesThread.getSearchingGen())
            val s = nrtDeletes.acquire()
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: got deletes searcher=$s")
            }
            try {
                assertEquals(
                    docs.size, s.search(TermQuery(id), 10).totalHits.value.toInt(),
                    "generation: $gen"
                )
            } finally {
                nrtDeletes.release(s)
            }
        }

        lastGens.set(gen)
    }

    override fun addDocuments(id: Term, docs: List<Iterable<IndexableField>>) {
        val gen = genWriter.addDocuments(docs)
        // Randomly verify the add "took":
        if (random().nextInt(20) == 2) {
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: verify addDocuments $id gen=$gen")
            }
            nrtNoDeletesThread.waitForGeneration(gen)
            assertTrue(gen <= nrtNoDeletesThread.getSearchingGen())
            val s = nrtNoDeletes.acquire()
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: got noDeletes searcher=$s")
            }
            try {
                assertEquals(
                    docs.size, s.search(TermQuery(id), 10).totalHits.value.toInt(),
                    "generation: $gen"
                )
            } finally {
                nrtNoDeletes.release(s)
            }
        }
        lastGens.set(gen)
    }

    override fun addDocument(id: Term, doc: Iterable<IndexableField>) {
        val gen = genWriter.addDocument(doc)

        // Randomly verify the add "took":
        if (random().nextInt(20) == 2) {
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: verify addDocument $id gen=$gen")
            }
            nrtNoDeletesThread.waitForGeneration(gen)
            assertTrue(gen <= nrtNoDeletesThread.getSearchingGen())
            val s = nrtNoDeletes.acquire()
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: got noDeletes searcher=$s")
            }
            try {
                assertEquals(1, s.search(TermQuery(id), 10).totalHits.value.toInt(), "generation: $gen")
            } finally {
                nrtNoDeletes.release(s)
            }
        }
        lastGens.set(gen)
    }

    override fun updateDocument(term: Term, doc: Iterable<IndexableField>) {
        val gen = genWriter.updateDocument(term, doc)
        // Randomly verify the update "took":
        if (random().nextInt(20) == 2) {
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: verify updateDocument $term gen=$gen")
            }
            nrtDeletesThread.waitForGeneration(gen)
            assertTrue(gen <= nrtDeletesThread.getSearchingGen())
            val s = nrtDeletes.acquire()
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: got deletes searcher=$s")
            }
            try {
                assertEquals(1, s.search(TermQuery(term), 10).totalHits.value.toInt(), "generation: $gen")
            } finally {
                nrtDeletes.release(s)
            }
        }
        lastGens.set(gen)
    }

    override fun deleteDocuments(term: Term) {
        val gen = genWriter.deleteDocuments(term)
        // randomly verify the delete "took":
        if (random().nextInt(20) == 7) {
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: verify deleteDocuments $term gen=$gen")
            }
            nrtDeletesThread.waitForGeneration(gen)
            assertTrue(gen <= nrtDeletesThread.getSearchingGen())
            val s = nrtDeletes.acquire()
            if (VERBOSE) {
                println(Thread.currentThread().getName() + ": nrt: got deletes searcher=$s")
            }
            try {
                assertEquals(0, s.search(TermQuery(term), 10).totalHits.value.toInt())
            } finally {
                nrtDeletes.release(s)
            }
        }
        lastGens.set(gen)
    }

    override fun doAfterWriter(es: ExecutorService?) {
        val minReopenSec = 0.01 + 0.05 * random().nextDouble()
        val maxReopenSec = minReopenSec * (1.0 + 10 * random().nextDouble())

        if (VERBOSE) {
            println("TEST: make SearcherManager maxReopenSec=$maxReopenSec minReopenSec=$minReopenSec")
        }

        genWriter = writer

        val sf = object : SearcherFactory() {
            @Throws(IOException::class)
            override fun newSearcher(r: IndexReader, previous: IndexReader?): IndexSearcher {
                this@TestControlledRealTimeReopenThread.warmCalled = true
                val s = IndexSearcher(r, es)
                s.search(TermQuery(Term("body", "united")), 10)
                return s
            }
        }

        nrtNoDeletes = SearcherManager(writer, false, false, sf)
        nrtDeletes = SearcherManager(writer, sf)

        nrtDeletesThread =
            ControlledRealTimeReopenThread(genWriter, nrtDeletes, maxReopenSec, minReopenSec)
        nrtDeletesThread.setName("NRTDeletes Reopen Thread")
        nrtDeletesThread.setPriority(
            minOf(Thread.currentThread().getPriority() + 2, Thread.MAX_PRIORITY)
        )
        nrtDeletesThread.setDaemon(true)
        nrtDeletesThread.start()

        nrtNoDeletesThread =
            ControlledRealTimeReopenThread(genWriter, nrtNoDeletes, maxReopenSec, minReopenSec)
        nrtNoDeletesThread.setName("NRTNoDeletes Reopen Thread")
        nrtNoDeletesThread.setPriority(
            minOf(Thread.currentThread().getPriority() + 2, Thread.MAX_PRIORITY)
        )
        nrtNoDeletesThread.setDaemon(true)
        nrtNoDeletesThread.start()
    }

    override fun doAfterIndexingThreadDone() {
        val gen = lastGens.get()
        if (gen != null) {
            addMaxGen(gen)
        }
    }

    private var maxGen = -1L
    private val maxGenLock = ReentrantLock()

    private fun addMaxGen(gen: Long) {
        maxGenLock.withLock {
            maxGen = maxOf(gen, maxGen)
        }
    }

    override fun doSearching(es: ExecutorService?, maxIterations: Int) {
        runSearchThreads(maxIterations)
    }

    override fun getCurrentSearcher(): IndexSearcher {
        // Test doesn't assert deletions until the end, so we
        // can randomize whether dels must be applied
        val nrt: SearcherManager = if (random().nextBoolean()) {
            nrtDeletes
        } else {
            nrtNoDeletes
        }
        return nrt.acquire()
    }

    override fun releaseSearcher(s: IndexSearcher) {
        // NOTE: a bit iffy... technically you should release
        // against the same SearcherManager you acquired from... but
        // both impls just decRef the underlying reader so we
        // can get away w/ cheating:
        nrtNoDeletes.release(s)
    }

    override fun doClose() {
        assertTrue(warmCalled)
        if (VERBOSE) {
            println("TEST: now close SearcherManagers")
        }
        nrtDeletesThread.close()
        nrtDeletes.close()
        nrtNoDeletesThread.close()
        nrtNoDeletes.close()
    }

    /*
     * LUCENE-3528 - NRTManager hangs in certain situations
     */
    @Test
    fun testThreadStarvationNoDeleteNRTReader() {
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        val d = newDirectory()
        val latch = CountDownLatch(1)
        val signal = CountDownLatch(1)

        val writer = LatchedIndexWriter(d, conf, latch, signal)
        val manager = SearcherManager(writer, false, false, null)
        val doc = Document()
        doc.add(newTextField("test", "test", Field.Store.YES))
        writer.addDocument(doc)
        manager.maybeRefresh()
        val t = object : Thread() {
            override fun run() {
                try {
                    signal.await()
                    manager.maybeRefresh()
                    writer.deleteDocuments(TermQuery(Term("foo", "barista")))
                    manager.maybeRefresh() // kick off another reopen so we inc. the internal gen
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown() // let the add below finish
                }
            }
        }
        t.start()
        writer.waitAfterUpdate = true // wait in addDocument to let some reopens go through

        val lastGen =
            writer.updateDocumentBlocking(
                Term("foo", "bar"),
                doc
            ) // once this returns the doc is already reflected in the last reopen

        // We now eagerly resolve deletes so the manager should see it after update:
        assertTrue(manager.isSearcherCurrent())

        val searcher = manager.acquire()
        try {
            assertEquals(2, searcher.indexReader.numDocs())
        } finally {
            manager.release(searcher)
        }
        val thread =
            ControlledRealTimeReopenThread(writer, manager, 0.01, 0.01)
        thread.start() // start reopening
        if (VERBOSE) {
            println("waiting now for generation $lastGen")
        }

        val finished = AtomicBoolean(false)
        val waiter = object : Thread() {
            override fun run() {
                try {
                    thread.waitForGeneration(lastGen)
                } catch (ie: Exception) {
                    Thread.currentThread().interrupt()
                    throw RuntimeException(ie)
                }
                finished.store(true)
            }
        }
        waiter.start()
        manager.maybeRefresh()
        waiter.join(1000)
        if (!finished.load()) {
            waiter.interrupt()
            fail("thread deadlocked on waitForGeneration")
        }
        thread.close()
        thread.join()
        writer.close()
        IOUtils.close(manager, d)
    }

    class LatchedIndexWriter(
        d: Directory,
        conf: IndexWriterConfig,
        private val latch: CountDownLatch,
        private val signal: CountDownLatch
    ) : IndexWriter(d, conf) {

        var waitAfterUpdate = false

        @Throws(IOException::class)
        fun updateDocumentBlocking(term: Term, doc: Iterable<IndexableField>): Long {
            val result = super.updateDocument(term, doc)
            try {
                if (waitAfterUpdate) {
                    signal.countDown()
                    latch.await()
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            return result
        }
    }

    @Test
    fun testEvilSearcherFactory() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.commit()

        val other = DirectoryReader.open(dir)

        val theEvilOne = object : SearcherFactory() {
            override fun newSearcher(ignored: IndexReader, previous: IndexReader?): IndexSearcher {
                return LuceneTestCase.newSearcher(other)
            }
        }

        expectThrows(IllegalStateException::class) {
            SearcherManager(w.w, false, false, theEvilOne)
        }

        w.close()
        other.close()
        dir.close()
    }

    @Test
    fun testListenerCalled() {
        val dir = newDirectory()
        val iw = IndexWriter(dir, IndexWriterConfig())
        val afterRefreshCalled = AtomicBoolean(false)
        val sm = SearcherManager(iw, SearcherFactory())
        sm.addListener(
            object : ReferenceManager.RefreshListener {
                override fun beforeRefresh() {}

                override fun afterRefresh(didRefresh: Boolean) {
                    if (didRefresh) {
                        afterRefreshCalled.store(true)
                    }
                }
            }
        )
        iw.addDocument(Document())
        iw.commit()
        assertFalse(afterRefreshCalled.load())
        sm.maybeRefreshBlocking()
        assertTrue(afterRefreshCalled.load())
        sm.close()
        iw.close()
        dir.close()
    }

    // Relies on wall clock time, so it can easily false-fail when the machine is otherwise busy:
    // LUCENE-5461
    @Test
    fun testCRTReopen() {
        // test behaving badly

        // should be high enough
        val maxStaleSecs = 20

        // build crap data just to store it.
        val s = "        abcdefghijklmnopqrstuvwxyz     "
        val chars = s.toCharArray()
        val builder = StringBuilder(2048)
        for (i in 0 until 2048) {
            builder.append(chars[random().nextInt(chars.size)])
        }
        val content = builder.toString()

        val sdp =
            SnapshotDeletionPolicy(KeepOnlyLastCommitDeletionPolicy())
        val dir = NRTCachingDirectory(newFSDirectory(createTempDir("nrt")), 5.0, 128.0)
        val config = IndexWriterConfig(MockAnalyzer(random()))
        config.setCommitOnClose(true)
        config.setIndexDeletionPolicy(sdp)
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
        val iw = IndexWriter(dir, config)
        val sm = SearcherManager(iw, SearcherFactory())
        val controlledRealTimeReopenThread =
            ControlledRealTimeReopenThread(iw, sm, maxStaleSecs.toDouble(), 0.0)

        controlledRealTimeReopenThread.setDaemon(true)
        controlledRealTimeReopenThread.start()

        val commitThreads = mutableListOf<Thread>()

        for (i in 0 until 500) { // TODO reduced 500 to 500 for dev speed
            if (i > 0 && i % 50 == 0) {
                val commitThread = object : Thread() {
                    override fun run() {
                        try {
                            iw.commit()
                            val ic: IndexCommit = sdp.snapshot()
                            for (name in ic.fileNames) {
                                // distribute, and backup
                                // println(name)
                                assertTrue(slowFileExists(dir, name))
                            }
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    }
                }
                commitThread.start()
                commitThreads.add(commitThread)
            }
            val d = Document()
            d.add(TextField("count", "$i", Field.Store.NO))
            d.add(TextField("content", content, Field.Store.YES))
            val start = System.nanoTime()
            val l = iw.addDocument(d)
            controlledRealTimeReopenThread.waitForGeneration(l)
            val wait = System.nanoTime() - start
            assertTrue(wait < maxStaleSecs * 1_000_000_000L, "waited too long for generation $wait")
            val searcher = sm.acquire()
            val td = searcher.search(TermQuery(Term("count", "$i")), 10)
            sm.release(searcher)
            assertEquals(1, td.totalHits.value.toInt())
        }

        for (commitThread in commitThreads) {
            commitThread.join()
        }

        controlledRealTimeReopenThread.close()
        sm.close()
        iw.close()
        dir.close()
    }

    @Test
    fun testDeleteAll() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val mgr = SearcherManager(w, SearcherFactory())
        nrtDeletesThread = ControlledRealTimeReopenThread(w, mgr, 0.1, 0.01)
        nrtDeletesThread.setName("NRTDeletes Reopen Thread")
        nrtDeletesThread.setDaemon(true)
        nrtDeletesThread.start()

        w.addDocument(Document())
        val gen2 = w.deleteAll()
        nrtDeletesThread.waitForGeneration(gen2)
        // nrtDeletes is not initialized in this test, close only the thread, mgr, writer, dir
        nrtDeletesThread.close()
        IOUtils.close(mgr, w, dir)
    }
}
