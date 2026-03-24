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

import kotlinx.coroutines.Runnable
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.CyclicBarrier
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.set
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
@SuppressCodecs("SimpleText", "Direct")
class TestIndexWriterThreadsToSegments : LuceneTestCase() {

    // LUCENE-5644: for first segment, two threads each indexed one doc (likely concurrently), but for
    // second segment, each thread indexed the
    // doc NOT at the same time, and should have shared the same thread state / segment
    @Test
    fun testSegmentCountOnFlushBasic() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val startingGun = CountDownLatch(1)
        val startDone = CountDownLatch(2)
        val middleGun = CountDownLatch(1)
        val finalGun = CountDownLatch(1)
        val threads = arrayOfNulls<Thread>(2)
        for (i in threads.indices) {
            val threadID = i
            threads[i] =
                Thread {
                    try {
                        startingGun.await()
                        val doc = Document()
                        doc.add(newTextField("field", "here is some text", Field.Store.NO))
                        w.addDocument(doc)
                        startDone.countDown()

                        middleGun.await()
                        if (threadID == 0) {
                            w.addDocument(doc)
                        } else {
                            finalGun.await()
                            w.addDocument(doc)
                        }
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            threads[i]!!.start()
        }

        startingGun.countDown()
        startDone.await()

        var r = DirectoryReader.open(w)
        assertEquals(2, r.numDocs())
        val numSegments = r.leaves().size
        // 1 segment if the threads ran sequentially, else 2:
        assertTrue(numSegments <= 2)
        r.close()

        middleGun.countDown()
        threads[0]!!.join()

        finalGun.countDown()
        threads[1]!!.join()

        r = DirectoryReader.open(w)
        assertEquals(4, r.numDocs())
        // Both threads should have shared a single thread state since they did not try to index
        // concurrently:
        assertEquals(1 + numSegments, r.leaves().size)
        r.close()

        w.close()
        dir.close()
    }

    /** Maximum number of simultaneous threads to use for each iteration. */
    private val MAX_THREADS_AT_ONCE = 10

    @OptIn(ExperimentalAtomicApi::class)
    inner class CheckSegmentCount(
        private val w: IndexWriter,
        private val maxThreadCountPerIter: AtomicInteger,
        private val indexingCount: AtomicInteger
    ) : Runnable, AutoCloseable {
        private var r: DirectoryReader? = null

        init {
            r = DirectoryReader.open(w)
            assertEquals(0, requireNotNull(r).leaves().size)
            setNextIterThreadCount()
        }

        override fun run() {
            try {
                val reader = requireNotNull(r)
                val oldSegmentCount = reader.leaves().size
                val r2 = DirectoryReader.openIfChanged(reader)
                assertNotNull(r2)
                reader.close()
                r = r2
                val maxExpectedSegments = oldSegmentCount + maxThreadCountPerIter.get()
                if (VERBOSE) {
                    println(
                        "TEST: iter done; now verify oldSegCount="
                            + oldSegmentCount
                            + " newSegCount="
                            + r2.leaves().size
                            + " maxExpected="
                            + maxExpectedSegments
                    )
                }
                // NOTE: it won't necessarily be ==, in case some threads were strangely scheduled and never
                // conflicted with one another (should be uncommon...?):
                assertTrue(r2.leaves().size <= maxExpectedSegments)
                setNextIterThreadCount()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        private fun setNextIterThreadCount() {
            indexingCount.set(0)
            maxThreadCountPerIter.set(TestUtil.nextInt(random(), 1, MAX_THREADS_AT_ONCE))
            if (VERBOSE) {
                println("TEST: iter set maxThreadCount=" + maxThreadCountPerIter.get())
            }
        }

        override fun close() {
            r?.close()
            r = null
        }
    }

    // LUCENE-5644: index docs w/ multiple threads but in between flushes we limit how many threads
    // can index concurrently in the next
    // iteration, and then verify that no more segments were flushed than number of threads:
    @Test
    fun testSegmentCountOnFlushRandom() {
        val dir = newFSDirectory(createTempDir())
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))

        // Never trigger flushes (so we only flush on getReader):
        iwc.setMaxBufferedDocs(100000000)
        iwc.setRAMBufferSizeMB(-1.0)

        // Never trigger merges (so we can simplistically count flushed segments):
        iwc.mergePolicy = NoMergePolicy.INSTANCE

        val w = IndexWriter(dir, iwc)

        // How many threads are indexing in the current cycle:
        val indexingCount = AtomicInteger(0)

        // How many threads we will use on each cycle:
        val maxThreadCount = AtomicInteger(0)

        val checker = CheckSegmentCount(w, maxThreadCount, indexingCount)

        // We spin up 10 threads up front, but then in between flushes we limit how many can run on each
        // iteration
        val ITERS = if (TEST_NIGHTLY) 300 else 10
        val threads = arrayOfNulls<Thread>(MAX_THREADS_AT_ONCE)

        // We use this to stop all threads once they've indexed their docs in the current iter, and pull
        // a new NRT reader, and verify the
        // segment count:
        val barrier = CyclicBarrier(MAX_THREADS_AT_ONCE, Runnable { checker.run() })

        for (i in threads.indices) {
            threads[i] =
                Thread {
                    try {
                        for (iter in 0 until ITERS) {
                            if (indexingCount.incrementAndFetch() <= maxThreadCount.get()) {
                                if (VERBOSE) {
                                    println("TEST: " + Thread.currentThread().getName() + ": do index")
                                }

                                // We get to index on this cycle:
                                val doc = Document()
                                doc.add(
                                    TextField(
                                        "field",
                                        "here is some text that is a bit longer than normal trivial text",
                                        Field.Store.NO
                                    )
                                )
                                for (j in 0 until 200) {
                                    w.addDocument(doc)
                                }
                            } else {
                                // We lose: no indexing for us on this cycle
                                if (VERBOSE) {
                                    println("TEST: " + Thread.currentThread().getName() + ": don't index")
                                }
                            }
                            barrier.await()
                        }
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            threads[i]!!.start()
        }

        for (t in threads) {
            requireNotNull(t).join()
        }

        IOUtils.close(checker, w, dir)
    }

    @Test
    fun testManyThreadsClose() {
        val dir = newDirectory()
        val r = random()
        val iwc = newIndexWriterConfig(r, MockAnalyzer(r))
        iwc.setCommitOnClose(false)
        val w = RandomIndexWriter(r, dir, iwc)
        TestUtil.reduceOpenFiles(w.w)
        w.setDoRandomForceMerge(false)
        val threads = arrayOfNulls<Thread>(TestUtil.nextInt(random(), 4, 30))
        val startingGun = CountDownLatch(1)
        for (i in threads.indices) {
            threads[i] =
                Thread {
                    try {
                        startingGun.await()
                        val doc = Document()
                        doc.add(newTextField("field", "here is some text that is a bit longer than normal trivial text", Field.Store.NO))
                        for (j in 0 until 1000) {
                            w.addDocument(doc)
                        }
                    } catch (_: AlreadyClosedException) {
                        // ok
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            threads[i]!!.start()
        }

        startingGun.countDown()

        Thread.sleep(100L)
        try {
            w.close()
        } catch (_: IllegalStateException) {
            // OK but not required
        }
        for (t in threads) {
            requireNotNull(t).join()
        }
        w.close()
        dir.close()
    }

    // TODO: can we make this test more efficient so it doesn't need to be nightly?
    @Nightly
    @Test
    fun testDocsStuckInRAMForever() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setRAMBufferSizeMB(0.2)
        val codec: Codec = TestUtil.getDefaultCodec()
        iwc.codec = codec
        iwc.mergePolicy = NoMergePolicy.INSTANCE
        val w = IndexWriter(dir, iwc)
        val startingGun = CountDownLatch(1)
        val threads = arrayOfNulls<Thread>(2)
        for (i in threads.indices) {
            val threadID = i
            threads[i] =
                Thread {
                    try {
                        startingGun.await()
                        for (j in 0 until 1000) {
                            val doc = Document()
                            doc.add(newStringField("field", "threadID$threadID", Field.Store.NO))
                            w.addDocument(doc)
                        }
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            threads[i]!!.start()
        }

        startingGun.countDown()
        for (t in threads) {
            requireNotNull(t).join()
        }

        val segSeen = HashSet<String>()
        var thread0Count = 0
        var thread1Count = 0

        // At this point the writer should have 2 thread states w/ docs; now we index with only 1 thread
        // until we see all 1000 thread0 & thread1
        // docs flushed.  If the writer incorrectly holds onto previously indexed docs forever then this
        // will run forever:
        var counter = 0L
        var checkAt = 100L
        while (thread0Count < 1000 || thread1Count < 1000) {
            val doc = Document()
            doc.add(newStringField("field", "threadIDmain", Field.Store.NO))
            w.addDocument(doc)
            if (counter++ == checkAt) {
                for (fileName in dir.listAll()) {
                    if (fileName.endsWith(".si")) {
                        val segName = IndexFileNames.parseSegmentName(fileName)
                        if (!segSeen.contains(segName)) {
                            segSeen.add(segName)
                            val id = readSegmentInfoID(dir, fileName)
                            val si =
                                TestUtil.getDefaultCodec()
                                    .segmentInfoFormat()
                                    .read(dir, segName, id, IOContext.DEFAULT)
                            si.codec = codec
                            val sci =
                                SegmentCommitInfo(si, 0, 0, -1, -1, -1, StringHelper.randomId())
                            val sr = SegmentReader(sci, Version.LATEST.major, IOContext.DEFAULT)
                            try {
                                thread0Count += sr.docFreq(Term("field", "threadID0"))
                                thread1Count += sr.docFreq(Term("field", "threadID1"))
                            } finally {
                                sr.close()
                            }
                        }
                    }
                }

                checkAt = (checkAt * 1.25).toLong()
                counter = 0
            }
        }

        w.close()
        dir.close()
    }

    // TODO: remove this hack and fix this test to be better?
    // the whole thing relies on default codec too...
    private fun readSegmentInfoID(dir: Directory, file: String): ByteArray {
        val input: IndexInput = dir.openInput(file, IOContext.DEFAULT)
        return try {
            input.readInt() // magic
            input.readString() // codec name
            input.readInt() // version
            val id = ByteArray(StringHelper.ID_LENGTH)
            input.readBytes(id, 0, id.size)
            id
        } finally {
            input.close()
        }
    }
}
