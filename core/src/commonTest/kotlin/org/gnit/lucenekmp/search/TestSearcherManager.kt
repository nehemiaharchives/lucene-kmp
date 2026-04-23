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

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.ConcurrentMergeScheduler
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.Executors
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.index.ThreadedIndexingAndSearchingTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestSearcherManager : ThreadedIndexingAndSearchingTestCase() {

    var warmCalled = false

    private var pruner: SearcherLifetimeManager.Pruner? = null

    @Test
    @Throws(Exception::class)
    fun testSearcherManager() {
        pruner =
            SearcherLifetimeManager.PruneByAge(
                if (TEST_NIGHTLY) TestUtil.nextInt(random(), 1, 20).toDouble() else 1.0
            )
        runTest("TestSearcherManager")
    }

    @Throws(Exception::class)
    override fun getFinalSearcher(): IndexSearcher {
        if (!isNRT) {
            writer.commit()
        }
        assertTrue(mgr.maybeRefresh() || mgr.isSearcherCurrent())
        return mgr.acquire()
    }

    private lateinit var mgr: SearcherManager
    private lateinit var lifetimeMGR: SearcherLifetimeManager
    private val pastSearchers = mutableListOf<Long>()
    private val pastSearchersLock = ReentrantLock()
    private var isNRT = false

    @Throws(Exception::class)
    override fun doAfterWriter(es: ExecutorService?) {
        val factory =
            object : SearcherFactory() {
                @Throws(IOException::class)
                override fun newSearcher(r: IndexReader, previousReader: IndexReader?): IndexSearcher {
                    val s = IndexSearcher(r, es)
                    this@TestSearcherManager.warmCalled = true
                    s.search(TermQuery(Term("body", "united")), 10)
                    return s
                }
            }
        if (random().nextBoolean()) {
            // TODO: can we randomize the applyAllDeletes?  But
            // somehow for final searcher we must apply
            // deletes...
            mgr = SearcherManager(writer, factory)
            isNRT = true
        } else {
            // SearcherManager needs to see empty commit:
            writer.commit()
            mgr = SearcherManager(dir, factory)
            isNRT = false
            assertMergedSegmentsWarmed = false
        }

        lifetimeMGR = SearcherLifetimeManager()
    }

    @Throws(Exception::class)
    override fun doSearching(es: ExecutorService?, maxIterations: Int) {
        val reopenThread =
            object : Thread() {
                override fun run() {
                    try {
                        if (VERBOSE) {
                            println("[${Thread.currentThread().getName()}]: launch reopen thread")
                        }

                        var iterations = 0
                        while (++iterations < maxIterations) {
                            Thread.sleep(TestUtil.nextInt(random(), 1, 100).toLong())
                            writer.commit()
                            Thread.sleep(TestUtil.nextInt(random(), 1, 5).toLong())
                            val block = random().nextBoolean()
                            if (block) {
                                mgr.maybeRefreshBlocking()
                                lifetimeMGR.prune(checkNotNull(pruner))
                            } else if (mgr.maybeRefresh()) {
                                lifetimeMGR.prune(checkNotNull(pruner))
                            }
                        }
                    } catch (t: Throwable) {
                        if (VERBOSE) {
                            println("TEST: reopen thread hit exc")
                            t.printStackTrace()
                        }
                        failed.store(true)
                        throw RuntimeException(t)
                    }
                }
            }
        reopenThread.setDaemon(true)
        reopenThread.start()

        runSearchThreads(maxIterations)

        reopenThread.join()
    }

    @Throws(Exception::class)
    override fun getCurrentSearcher(): IndexSearcher {
        if (random().nextInt(10) == 7) {
            // NOTE: not best practice to call maybeRefresh
            // synchronous to your search threads, but still we
            // test as apps will presumably do this for
            // simplicity:
            if (mgr.maybeRefresh()) {
                lifetimeMGR.prune(checkNotNull(pruner))
            }
        }

        var s: IndexSearcher? = null

        pastSearchersLock.withLock {
            while (pastSearchers.size != 0 && random().nextDouble() < 0.25) {
                // 1/4 of the time pull an old searcher, ie, simulate
                // a user doing a follow-on action on a previous
                // search (drilling down/up, clicking next/prev page,
                // etc.)
                val token = pastSearchers[random().nextInt(pastSearchers.size)]
                s = lifetimeMGR.acquire(token)
                if (s == null) {
                    // Searcher was pruned
                    pastSearchers.remove(token)
                } else {
                    break
                }
            }
        }

        if (s == null) {
            s = mgr.acquire()
            if (s!!.indexReader.numDocs() != 0) {
                val token = lifetimeMGR.record(s!!)
                pastSearchersLock.withLock {
                    if (!pastSearchers.contains(token)) {
                        pastSearchers.add(token)
                    }
                }
            }
        }

        return s!!
    }

    @Throws(Exception::class)
    override fun releaseSearcher(s: IndexSearcher) {
        s.indexReader.decRef()
    }

    @Throws(Exception::class)
    override fun doClose() {
        assertTrue(warmCalled)
        if (VERBOSE) {
            println("TEST: now close SearcherManager")
        }
        mgr.close()
        lifetimeMGR.close()
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testIntermediateClose() {
        val dir = newDirectory()
        // Test can deadlock if we use SMS:
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMergeScheduler(ConcurrentMergeScheduler())
            )
        writer.addDocument(Document())
        writer.commit()
        val awaitEnterWarm = CountDownLatch(1)
        val awaitClose = CountDownLatch(1)
        val triedReopen = AtomicBoolean(false)
        val es =
            if (random().nextBoolean()) {
                null
            } else {
                Executors.newCachedThreadPool(NamedThreadFactory("testIntermediateClose"))
            }
        val factory =
            object : SearcherFactory() {
                override fun newSearcher(r: IndexReader, previousReader: IndexReader?): IndexSearcher {
                    try {
                        if (triedReopen.load()) {
                            awaitEnterWarm.countDown()
                            awaitClose.await()
                        }
                    } catch (_: InterruptedException) {
                        //
                    }
                    return IndexSearcher(r, es)
                }
            }
        val searcherManager =
            if (random().nextBoolean()) {
                SearcherManager(dir, factory)
            } else {
                SearcherManager(writer, random().nextBoolean(), false, factory)
            }
        if (VERBOSE) {
            println("sm created")
        }
        var searcher = searcherManager.acquire()
        try {
            assertEquals(1, searcher.indexReader.numDocs())
        } finally {
            searcherManager.release(searcher)
        }
        writer.addDocument(Document())
        writer.commit()
        val success = AtomicBoolean(false)
        val exc = arrayOfNulls<Throwable>(1)
        val thread =
            object : Thread() {
                override fun run() {
                    try {
                        triedReopen.store(true)
                        if (VERBOSE) {
                            println("NOW call maybeRefresh")
                        }
                        searcherManager.maybeRefresh()
                        success.store(true)
                    } catch (_: AlreadyClosedException) {
                        // expected
                    } catch (e: Throwable) {
                        if (VERBOSE) {
                            println("FAIL: unexpected exc")
                            e.printStackTrace()
                        }
                        exc[0] = e
                        // use success as the barrier here to make sure we see the write
                        success.store(false)
                    }
                }
            }
        thread.start()
        if (VERBOSE) {
            println("THREAD started")
        }
        awaitEnterWarm.await()
        if (VERBOSE) {
            println("NOW call close")
        }
        searcherManager.close()
        awaitClose.countDown()
        thread.join()
        expectThrows(AlreadyClosedException::class) {
            searcherManager.acquire()
        }
        assertFalse(success.load())
        assertTrue(triedReopen.load())
        assertNull(exc[0], "${exc[0]}")
        writer.close()
        dir.close()
        if (es != null) {
            es.shutdown()
            runBlocking {
                es.awaitTermination(1, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testCloseTwice() {
        // test that we can close SM twice (per Closeable's contract).
        val dir = newDirectory()
        IndexWriter(dir, newIndexWriterConfig()).close()
        val sm = SearcherManager(dir, null)
        sm.close()
        sm.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReferenceDecrementIllegally() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random())).setMergeScheduler(ConcurrentMergeScheduler())
            )
        val sm = SearcherManager(writer, false, false, SearcherFactory())
        writer.addDocument(Document())
        writer.commit()
        sm.maybeRefreshBlocking()

        var acquire = sm.acquire()
        var acquire2 = sm.acquire()
        sm.release(acquire)
        sm.release(acquire2)

        acquire = sm.acquire()
        acquire.indexReader.decRef()
        sm.release(acquire)
        expectThrows(IllegalStateException::class) {
            sm.acquire()
        }

        // sm.close(); -- already closed
        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testEnsureOpen() {
        val dir = newDirectory()
        IndexWriter(dir, newIndexWriterConfig()).close()
        val sm = SearcherManager(dir, null)
        val s = sm.acquire()
        sm.close()

        // this should succeed;
        sm.release(s)

        // this should fail
        expectThrows(AlreadyClosedException::class) {
            sm.acquire()
        }

        // this should fail
        expectThrows(AlreadyClosedException::class) {
            sm.maybeRefresh()
        }

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testListenerCalled() {
        val dir = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val afterRefreshCalled = AtomicBoolean(false)
        val sm = SearcherManager(iw, false, false, SearcherFactory())
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

    @Test
    @Throws(Exception::class)
    fun testEvilSearcherFactory() {
        val random = random()
        val dir = newDirectory()
        val w = RandomIndexWriter(random, dir)
        w.commit()

        val other = DirectoryReader.open(dir)

        val theEvilOne =
            object : SearcherFactory() {
                override fun newSearcher(ignored: IndexReader, previousReader: IndexReader?): IndexSearcher {
                    return newSearcher(other)
                }
            }

        expectThrows(IllegalStateException::class) {
            SearcherManager(dir, theEvilOne)
        }
        expectThrows(IllegalStateException::class) {
            SearcherManager(w.w, random.nextBoolean(), false, theEvilOne)
        }
        w.close()
        other.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaybeRefreshBlockingLock() {
        // make sure that maybeRefreshBlocking releases the lock, otherwise other
        // threads cannot obtain it.
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.close()

        val sm = SearcherManager(dir, null)

        val t =
            object : Thread() {
                override fun run() {
                    try {
                        // this used to not release the lock, preventing other threads from obtaining it.
                        sm.maybeRefreshBlocking()
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            }
        t.start()
        t.join()

        // if maybeRefreshBlocking didn't release the lock, this will fail.
        assertTrue(sm.maybeRefresh(), "failde to obtain the refreshLock!")

        sm.close()
        dir.close()
    }

    private class MyFilterLeafReader(`in`: LeafReader) : FilterLeafReader(`in`) {
        override val coreCacheHelper: IndexReader.CacheHelper?
            get() = delegate.coreCacheHelper

        override val readerCacheHelper: IndexReader.CacheHelper?
            get() = delegate.readerCacheHelper
    }

    private class MyFilterDirectoryReader(`in`: DirectoryReader) :
        FilterDirectoryReader(
            `in`,
            object : FilterDirectoryReader.SubReaderWrapper() {
                override fun wrap(reader: LeafReader): LeafReader {
                    val wrapped = MyFilterLeafReader(reader)
                    assertEquals(reader, wrapped.delegate)
                    return wrapped
                }
            }
        ) {
        @Throws(IOException::class)
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return MyFilterDirectoryReader(`in`)
        }

        override val readerCacheHelper: IndexReader.CacheHelper?
            get() = `in`.readerCacheHelper
    }

    // LUCENE-6087
    @Test
    @Throws(Exception::class)
    fun testCustomDirectoryReader() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val nrtReader = w.reader

        val reader = MyFilterDirectoryReader(nrtReader)
        assertEquals(nrtReader, reader.getDelegate())
        assertEquals(FilterDirectoryReader.unwrap(nrtReader), FilterDirectoryReader.unwrap(reader))

        val mgr = SearcherManager(reader, null)
        for (i in 0..<10) {
            w.addDocument(Document())
            mgr.maybeRefresh()
            val s = mgr.acquire()
            try {
                assertTrue(s.indexReader is MyFilterDirectoryReader)
                for (ctx in s.indexReader.leaves()) {
                    assertTrue(ctx.reader() is MyFilterLeafReader)
                }
            } finally {
                mgr.release(s)
            }
        }
        mgr.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testPreviousReaderIsPassed() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        class MySearcherFactory : SearcherFactory() {
            var lastReader: IndexReader? = null
            var lastPreviousReader: IndexReader? = null
            var called = 0

            @Throws(IOException::class)
            override fun newSearcher(reader: IndexReader, previousReader: IndexReader?): IndexSearcher {
                called++
                lastReader = reader
                lastPreviousReader = previousReader
                return super.newSearcher(reader, previousReader)
            }
        }

        val factory = MySearcherFactory()
        val sm = SearcherManager(w, random().nextBoolean(), false, factory)
        assertEquals(1, factory.called)
        assertNull(factory.lastPreviousReader)
        assertNotNull(factory.lastReader)
        var acquire = sm.acquire()
        assertSame(factory.lastReader, acquire.indexReader)
        sm.release(acquire)

        val lastReader = factory.lastReader
        // refresh
        w.addDocument(Document())
        assertTrue(sm.maybeRefresh())

        acquire = sm.acquire()
        assertSame(factory.lastReader, acquire.indexReader)
        sm.release(acquire)
        assertNotNull(factory.lastPreviousReader)
        assertSame(lastReader, factory.lastPreviousReader)
        assertNotSame(factory.lastReader, lastReader)
        assertEquals(2, factory.called)
        w.close()
        sm.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testConcurrentIndexCloseSearchAndRefresh() {
        val dir = newFSDirectory(createTempDir())
        val writerRef = AtomicReference<IndexWriter?>(null)
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(IndexWriter.MAX_TERM_LENGTH)
        writerRef.store(IndexWriter(dir, newIndexWriterConfig(analyzer)))

        val mgrRef = AtomicReference<SearcherManager?>(null)
        mgrRef.store(SearcherManager(writerRef.load()!!, null))
        val stop = AtomicBoolean(false)

        val indexThread =
            object : Thread() {
                override fun run() {
                    try {
                        val numDocs = if (TEST_NIGHTLY) atLeast(20000) else atLeast(200)
                        for (i in 0..<numDocs) {
                            val w = writerRef.load()!!
                            val doc = Document()
                            doc.add(
                                newTextField(
                                    "field",
                                    TestUtil.randomAnalysisString(random(), 256, false),
                                    Field.Store.YES
                                )
                            )
                            w.addDocument(doc)
                            if (random().nextInt(1000) == 17) {
                                if (random().nextBoolean()) {
                                    w.close()
                                } else {
                                    w.rollback()
                                }
                                writerRef.store(IndexWriter(dir, newIndexWriterConfig(analyzer)))
                            }
                        }
                        if (VERBOSE) {
                            println("TEST: index count=${writerRef.load()!!.getDocStats().maxDoc}")
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(ioe)
                    } finally {
                        stop.store(true)
                    }
                }
            }

        val searchThread =
            object : Thread() {
                override fun run() {
                    try {
                        var totCount = 0L
                        while (!stop.load()) {
                            val mgr = mgrRef.load()
                            if (mgr != null) {
                                val searcher =
                                    try {
                                        mgr.acquire()
                                    } catch (_: AlreadyClosedException) {
                                        // ok
                                        continue
                                    }
                                totCount += searcher.indexReader.maxDoc().toLong()
                                mgr.release(searcher)
                            }
                        }
                        if (VERBOSE) {
                            println("TEST: search totCount=$totCount")
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(ioe)
                    }
                }
            }

        val refreshThread =
            object : Thread() {
                override fun run() {
                    try {
                        var refreshCount = 0
                        var aceCount = 0
                        while (!stop.load()) {
                            val mgr = mgrRef.load()
                            if (mgr != null) {
                                refreshCount++
                                try {
                                    mgr.maybeRefreshBlocking()
                                } catch (_: AlreadyClosedException) {
                                    // ok
                                    aceCount++
                                    continue
                                }
                            }
                        }
                        if (VERBOSE) {
                            println("TEST: refresh count=$refreshCount aceCount=$aceCount")
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(ioe)
                    }
                }
            }

        val closeThread =
            object : Thread() {
                override fun run() {
                    try {
                        var closeCount = 0
                        var aceCount = 0
                        while (!stop.load()) {
                            val mgr = mgrRef.load()
                            assertNotNull(mgr)
                            mgr.close()
                            closeCount++
                            while (!stop.load()) {
                                try {
                                    mgrRef.store(SearcherManager(writerRef.load()!!, null))
                                    break
                                } catch (_: AlreadyClosedException) {
                                    // ok
                                    aceCount++
                                }
                            }
                        }
                        if (VERBOSE) {
                            println("TEST: close count=$closeCount aceCount=$aceCount")
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(ioe)
                    }
                }
            }

        indexThread.start()
        searchThread.start()
        refreshThread.start()
        closeThread.start()

        indexThread.join()
        searchThread.join()
        refreshThread.join()
        closeThread.join()

        mgrRef.load()!!.close()
        writerRef.load()!!.close()
        dir.close()
    }
}
