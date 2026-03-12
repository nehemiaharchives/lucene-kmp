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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.MockRandomMergePolicy
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

// @SuppressCodecs("SimpleText") // too slow here
@OptIn(ExperimentalAtomicApi::class)
class TestIndexWriterDelete : LuceneTestCase() {

    // test the simple case
    @Test
    @Throws(IOException::class)
    fun testSimpleCase() {
        val keywords = arrayOf("1", "2")
        val unindexed = arrayOf("Netherlands", "Italy")
        val unstored = arrayOf("Amsterdam has lots of bridges", "Venice has lots of canals")
        val text = arrayOf("Amsterdam", "Venice")

        val dir = newDirectory()
        val modifier = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        )

        val custom1 = FieldType()
        custom1.setStored(true)
        for (i in keywords.indices) {
            val doc = Document()
            doc.add(newStringField("id", keywords[i], Field.Store.YES))
            doc.add(newField("country", unindexed[i], custom1))
            doc.add(newTextField("contents", unstored[i], Field.Store.NO))
            doc.add(newTextField("city", text[i], Field.Store.YES))
            modifier.addDocument(doc)
        }
        modifier.forceMerge(1)
        modifier.commit()

        val term = Term("city", "Amsterdam")
        var hitCount = getHitCount(dir, term)
        assertEquals(1, hitCount)
        if (VERBOSE) {
            println("\nTEST: now delete by term=$term")
        }
        modifier.deleteDocuments(term)
        modifier.commit()

        if (VERBOSE) {
            println("\nTEST: now getHitCount")
        }
        hitCount = getHitCount(dir, term)
        assertEquals(0, hitCount)

        modifier.close()
        dir.close()
    }

    // test when delete terms only apply to disk segments
    @Test
    @Throws(IOException::class)
    fun testNonRAMDelete() {
        val dir = newDirectory()
        val modifier = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                .setMaxBufferedDocs(2)
        )
        var id = 0
        val value = 100

        for (i in 0 until 7) {
            addDoc(modifier, ++id, value)
        }
        modifier.commit()

        assertEquals(0, modifier.getNumBufferedDocuments())
        assertTrue(0 < modifier.getSegmentCount())

        modifier.commit()

        var reader = DirectoryReader.open(dir)
        assertEquals(7, reader.numDocs())
        reader.close()

        modifier.deleteDocuments(Term("value", value.toString()))

        modifier.commit()

        reader = DirectoryReader.open(dir)
        assertEquals(0, reader.numDocs())
        reader.close()
        modifier.close()
        dir.close()
    }

    // test when delete terms only apply to ram segments
    @Test
    @Throws(IOException::class)
    fun testRAMDeletes() {
        for (t in 0 until 2) {
            if (VERBOSE) {
                println("TEST: t=$t")
            }
            val dir = newDirectory()
            val modifier = IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                    .setMaxBufferedDocs(4)
            )
            var id = 0
            val value = 100

            addDoc(modifier, ++id, value)
            if (0 == t) modifier.deleteDocuments(Term("value", value.toString()))
            else modifier.deleteDocuments(TermQuery(Term("value", value.toString())))
            addDoc(modifier, ++id, value)
            if (0 == t) {
                modifier.deleteDocuments(Term("value", value.toString()))
                assertEquals(1, modifier.getBufferedDeleteTermsSize())
            } else modifier.deleteDocuments(TermQuery(Term("value", value.toString())))

            addDoc(modifier, ++id, value)
            assertEquals(0, modifier.getSegmentCount())
            modifier.commit()

            val reader = DirectoryReader.open(dir)
            assertEquals(1, reader.numDocs())

            val hitCount = getHitCount(dir, Term("id", id.toString()))
            assertEquals(1, hitCount)
            reader.close()
            modifier.close()
            dir.close()
        }
    }

    // test when delete terms apply to both disk and ram segments
    @Test
    @Throws(IOException::class)
    fun testBothDeletes() {
        val dir = newDirectory()
        val modifier = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                .setMaxBufferedDocs(100)
        )

        var id = 0
        var value = 100

        for (i in 0 until 5) {
            addDoc(modifier, ++id, value)
        }

        value = 200
        for (i in 0 until 5) {
            addDoc(modifier, ++id, value)
        }
        modifier.commit()

        for (i in 0 until 5) {
            addDoc(modifier, ++id, value)
        }
        modifier.deleteDocuments(Term("value", value.toString()))

        modifier.commit()

        val reader = DirectoryReader.open(dir)
        assertEquals(5, reader.numDocs())
        modifier.close()
        reader.close()
        dir.close()
    }

    // test that batched delete terms are flushed together
    @Test
    @Throws(IOException::class)
    fun testBatchDeletes() {
        val dir = newDirectory()
        val modifier = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                .setMaxBufferedDocs(2)
        )

        var id = 0
        val value = 100

        for (i in 0 until 7) {
            addDoc(modifier, ++id, value)
        }
        modifier.commit()

        var reader = DirectoryReader.open(dir)
        assertEquals(7, reader.numDocs())
        reader.close()

        id = 0
        modifier.deleteDocuments(Term("id", (++id).toString()))
        modifier.deleteDocuments(Term("id", (++id).toString()))

        modifier.commit()

        reader = DirectoryReader.open(dir)
        assertEquals(5, reader.numDocs())
        reader.close()

        val terms = Array(3) { Term("id", "") }
        for (i in terms.indices) {
            terms[i] = Term("id", (++id).toString())
        }
        modifier.deleteDocuments(*terms)
        modifier.commit()
        reader = DirectoryReader.open(dir)
        assertEquals(2, reader.numDocs())
        reader.close()

        modifier.close()
        dir.close()
    }

    // test deleteAll()
    @Test
    @Throws(IOException::class)
    fun testDeleteAllSimple() {
        if (VERBOSE) {
            println("TEST: now start")
        }
        val dir = newDirectory()
        val modifier = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                .setMaxBufferedDocs(2)
        )

        var id = 0
        val value = 100

        for (i in 0 until 7) {
            addDoc(modifier, ++id, value)
        }
        if (VERBOSE) {
            println("TEST: now commit")
        }
        modifier.commit()

        var reader = DirectoryReader.open(dir)
        assertEquals(7, reader.numDocs())
        reader.close()

        // Add 1 doc (so we will have something buffered)
        addDoc(modifier, 99, value)

        // Delete all
        if (VERBOSE) {
            println("TEST: now delete all")
        }
        modifier.deleteAll()

        // Delete all shouldn't be on disk yet
        reader = DirectoryReader.open(dir)
        assertEquals(7, reader.numDocs())
        reader.close()

        // Add a doc and update a doc (after the deleteAll, before the commit)
        addDoc(modifier, 101, value)
        updateDoc(modifier, 102, value)
        if (VERBOSE) {
            println("TEST: now 2nd commit")
        }

        // commit the delete all
        modifier.commit()

        // Validate there are no docs left
        reader = DirectoryReader.open(dir)
        assertEquals(2, reader.numDocs())
        reader.close()

        modifier.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDeleteAllNoDeadLock() {
        val dir = newDirectory()
        val modifier = RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig().setMergePolicy(MockRandomMergePolicy(random()))
        )
        val numThreads = atLeast(2)
        val latch = CountDownLatch(1)
        val doneLatch = CountDownLatch(numThreads)
        val threads = Array(numThreads) { i ->
            val offset = i
            Thread {
                var id = offset * 1000
                val value = 100
                try {
                    latch.await()
                    for (j in 0 until 1000) {
                        val doc = Document()
                        doc.add(newTextField("content", "aaa", Field.Store.NO))
                        doc.add(newStringField("id", (id++).toString(), Field.Store.YES))
                        doc.add(newStringField("value", value.toString(), Field.Store.NO))
                        doc.add(NumericDocValuesField("dv", value.toLong()))
                        modifier.addDocument(doc)
                        if (VERBOSE) {
                            println("\tThread[$offset]: add doc: $id")
                        }
                    }
                } catch (e: Exception) {
                    throw RuntimeException(e)
                } finally {
                    doneLatch.countDown()
                    if (VERBOSE) {
                        println("\tThread[$offset]: done indexing")
                    }
                }
            }
        }
        for (t in threads) t.start()
        latch.countDown()
        while (!doneLatch.await(1, TimeUnit.MILLISECONDS)) {
            if (VERBOSE) {
                println("\nTEST: now deleteAll")
            }
            modifier.deleteAll()
            if (VERBOSE) {
                println("del all")
            }
        }

        if (VERBOSE) {
            println("\nTEST: now final deleteAll")
        }

        modifier.deleteAll()
        for (thread in threads) {
            thread.join()
        }

        if (VERBOSE) {
            println("\nTEST: now close")
        }
        modifier.close()

        val reader = DirectoryReader.open(dir)
        if (VERBOSE) {
            println("\nTEST: got reader=$reader")
        }
        assertEquals(0, reader.maxDoc())
        assertEquals(0, reader.numDocs())
        assertEquals(0, reader.numDeletedDocs())
        reader.close()

        dir.close()
    }

    // test rollback of deleteAll()
    @Test
    @Throws(IOException::class)
    fun testDeleteAllRollback() {
        val dir = newDirectory()
        val modifier = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                .setMaxBufferedDocs(2)
        )

        var id = 0
        val value = 100

        for (i in 0 until 7) {
            addDoc(modifier, ++id, value)
        }
        modifier.commit()

        addDoc(modifier, ++id, value)

        var reader = DirectoryReader.open(dir)
        assertEquals(7, reader.numDocs())
        reader.close()

        // Delete all
        modifier.deleteAll()

        // Roll it back
        modifier.rollback()

        // Validate that the docs are still there
        reader = DirectoryReader.open(dir)
        assertEquals(7, reader.numDocs())
        reader.close()

        dir.close()
    }

    // test deleteAll() w/ near real-time reader
    @Test
    @Throws(IOException::class)
    fun testDeleteAllNRT() {
        val dir = newDirectory()
        val modifier = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                .setMaxBufferedDocs(2)
        )

        var id = 0
        val value = 100

        for (i in 0 until 7) {
            addDoc(modifier, ++id, value)
        }
        modifier.commit()

        var reader = DirectoryReader.open(modifier)
        assertEquals(7, reader.numDocs())
        reader.close()

        addDoc(modifier, ++id, value)
        addDoc(modifier, ++id, value)

        // Delete all
        modifier.deleteAll()

        reader = DirectoryReader.open(modifier)
        assertEquals(0, reader.numDocs())
        reader.close()

        // Roll it back
        modifier.rollback()

        // Validate that the docs are still there
        reader = DirectoryReader.open(dir)
        assertEquals(7, reader.numDocs())
        reader.close()

        dir.close()
    }

    // Verify that we can call deleteAll repeatedly without leaking field numbers such that we trigger
    // OOME
    // on creation of FieldInfos. See https://issues.apache.org/jira/browse/LUCENE-9617
    // @Nightly // Takes 1-2 minutes to run on a 16-core machine
    @Test
    @Throws(IOException::class)
    fun testDeleteAllRepeated() {
        val breakingFieldCount = 50_000 // TODO reduced breakingFieldCount = 50_000_000 to 50_000 for dev speed
        val dir = newDirectory()
        // Avoid flushing until the end of the test to save time.
        val conf = newIndexWriterConfig()
            .setMaxBufferedDocs(1000)
            .setRAMBufferSizeMB(1000.0)
            .setRAMPerThreadHardLimitMB(1000)
            .setCheckPendingFlushUpdate(false)
        val modifier = IndexWriter(dir, conf)
        val document = Document()
        val fieldsPerDoc = 1_000
        for (i in 0 until fieldsPerDoc) {
            document.add(StoredField("field$i", ""))
        }
        // Note: Java uses multi-threaded concurrent deleteAll; running sequentially here because
        // IndexWriter.deleteAll() synchronized block is not yet fully ported (known TODO in KMP).
        var numFields = 0L
        while (numFields < breakingFieldCount) {
            modifier.addDocument(document)
            modifier.deleteAll()
            numFields += fieldsPerDoc
        }
        // Add one last document and flush to build FieldInfos.
        modifier.addDocument(document)
        modifier.flush()
        modifier.close()
        dir.close()
    }

    private fun updateDoc(modifier: IndexWriter, id: Int, value: Int) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        doc.add(newStringField("id", id.toString(), Field.Store.YES))
        doc.add(newStringField("value", value.toString(), Field.Store.NO))
        doc.add(NumericDocValuesField("dv", value.toLong()))
        modifier.updateDocument(Term("id", id.toString()), doc)
    }

    private fun addDoc(modifier: IndexWriter, id: Int, value: Int) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        doc.add(newStringField("id", id.toString(), Field.Store.YES))
        doc.add(newStringField("value", value.toString(), Field.Store.NO))
        doc.add(NumericDocValuesField("dv", value.toLong()))
        modifier.addDocument(doc)
    }

    private fun getHitCount(dir: Directory, term: Term): Long {
        val reader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)
        val hitCount = searcher.search(TermQuery(term), 1000).totalHits.value
        reader.close()
        return hitCount
    }

    // TODO: can we fix MockDirectoryWrapper disk full checking to be more efficient (not recompute on
    // every write)?
    // @Nightly
    @Test
    @Throws(IOException::class)
    fun testDeletesOnDiskFull() {
        doTestOperationsOnDiskFull(false)
    }

    // TODO: can we fix MockDirectoryWrapper disk full checking to be more efficient (not recompute on
    // every write)?
    // @Nightly
    @Test
    @Throws(IOException::class)
    fun testUpdatesOnDiskFull() {
        doTestOperationsOnDiskFull(true)
    }

    /**
     * Make sure if modifier tries to commit but hits disk full that modifier remains consistent and
     * usable. Similar to TestIndexReader.testDiskFull().
     */
    private fun doTestOperationsOnDiskFull(updates: Boolean) {
        val searchTerm = Term("content", "aaa")
        val START_COUNT = 157
        val END_COUNT = 144

        // First build up a starting index:
        val startDir = newMockDirectory()

        val writer = IndexWriter(
            startDir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        )
        for (i in 0 until 157) {
            val d = Document()
            d.add(newStringField("id", i.toString(), Field.Store.YES))
            d.add(newTextField("content", "aaa $i", Field.Store.NO))
            d.add(NumericDocValuesField("dv", i.toLong()))
            writer.addDocument(d)
        }
        writer.close()

        var diskUsage = startDir.sizeInBytes()
        var diskFree = diskUsage + 10

        var err: IOException? = null

        var done = false

        // Iterate w/ ever-increasing free disk space:
        while (!done) {
            if (VERBOSE) {
                println("TEST: cycle")
            }
            val dir = MockDirectoryWrapper(random(), TestUtil.ramCopyOf(startDir))
            dir.allowRandomFileNotFoundException = false
            val modifier = IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                    .setMaxBufferedDocs(1000)
                    .setMergeScheduler(ConcurrentMergeScheduler())
            )
            (modifier.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()

            // For each disk size, first try to commit against
            // dir that will hit random IOExceptions & disk
            // full; after, give it infinite disk space & turn
            // off random IOExceptions & retry w/ same reader:
            var success = false

            for (x in 0 until 2) {
                if (VERBOSE) {
                    println("TEST: x=$x")
                }

                var rate = 0.1
                val diskRatio = diskFree.toDouble() / diskUsage.toDouble()
                val thisDiskFree: Long
                val testName: String

                if (0 == x) {
                    thisDiskFree = diskFree
                    if (diskRatio >= 2.0) {
                        rate /= 2
                    }
                    if (diskRatio >= 4.0) {
                        rate /= 2
                    }
                    if (diskRatio >= 6.0) {
                        rate = 0.0
                    }
                    if (VERBOSE) {
                        println("\ncycle: $diskFree bytes")
                    }
                    testName = "disk full during reader.close() @ $thisDiskFree bytes"
                    dir.randomIOExceptionRateOnOpen = random().nextDouble() * 0.01
                } else {
                    thisDiskFree = 0
                    rate = 0.0
                    if (VERBOSE) {
                        println("\ncycle: same writer: unlimited disk space")
                    }
                    testName = "reader re-use after disk full"
                    dir.randomIOExceptionRateOnOpen = 0.0
                }

                dir.maxSizeInBytes = thisDiskFree
                dir.randomIOExceptionRate = rate

                try {
                    if (0 == x) {
                        var docId = 12
                        for (i in 0 until 13) {
                            if (updates) {
                                val d = Document()
                                d.add(newStringField("id", i.toString(), Field.Store.YES))
                                d.add(newTextField("content", "bbb $i", Field.Store.NO))
                                d.add(NumericDocValuesField("dv", i.toLong()))
                                modifier.updateDocument(Term("id", docId.toString()), d)
                            } else { // deletes
                                modifier.deleteDocuments(Term("id", docId.toString()))
                                // modifier.setNorm(docId, "contents", (float)2.0);
                            }
                            docId += 12
                        }
                        try {
                            modifier.close()
                        } catch (ise: IllegalStateException) {
                            // ok
                            throw ise.cause as? IOException ?: IOException(ise.message)
                        }
                    }
                    success = true
                    if (0 == x) {
                        done = true
                    }
                } catch (e: IOException) {
                    if (VERBOSE) {
                        println("  hit IOException: $e")
                        e.printStackTrace()
                    }
                    err = e
                    if (1 == x) {
                        e.printStackTrace()
                        fail("$testName hit IOException after disk space was freed up")
                    }
                }
                // prevent throwing a random exception here!!
                val randomIOExceptionRate = dir.randomIOExceptionRate
                val maxSizeInBytes = dir.maxSizeInBytes
                dir.randomIOExceptionRate = 0.0
                dir.randomIOExceptionRateOnOpen = 0.0
                dir.maxSizeInBytes = 0
                if (!success) {
                    // Must force the close else the writer can have
                    // open files which cause exc in MockRAMDir.close
                    if (VERBOSE) {
                        println("TEST: now rollback")
                    }
                    modifier.rollback()
                }

                // If the close() succeeded, make sure index is OK:
                if (success) {
                    TestUtil.checkIndex(dir)
                }
                dir.randomIOExceptionRate = randomIOExceptionRate
                dir.maxSizeInBytes = maxSizeInBytes

                // Finally, verify index is not corrupt, and, if
                // we succeeded, we see all docs changed, and if
                // we failed, we see either all docs or no docs
                // changed (transactional semantics):
                val newReader: IndexReader?
                try {
                    newReader = DirectoryReader.open(dir)
                } catch (e: IOException) {
                    e.printStackTrace()
                    fail("$testName:exception when creating IndexReader after disk full during close: $e")
                    return
                }

                val searcher = newSearcher(newReader)
                val hits: Array<ScoreDoc>?
                try {
                    hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
                } catch (e: IOException) {
                    e.printStackTrace()
                    fail("$testName: exception when searching: $e")
                    newReader.close()
                    break
                }
                val result2 = hits.size
                if (success) {
                    if (x == 0 && result2 != END_COUNT) {
                        fail(
                            "$testName: method did not throw exception but hits.length for search on term 'aaa' is " +
                                "$result2 instead of expected $END_COUNT"
                        )
                    } else if (x == 1 && result2 != START_COUNT && result2 != END_COUNT) {
                        // It's possible that the first exception was
                        // "recoverable" wrt pending deletes, in which
                        // case the pending deletes are retained and
                        // then re-flushing (with plenty of disk
                        // space) will succeed in flushing the
                        // deletes:
                        fail(
                            "$testName: method did not throw exception but hits.length for search on term 'aaa' is " +
                                "$result2 instead of expected $START_COUNT or $END_COUNT"
                        )
                    }
                } else {
                    // On hitting exception we still may have added
                    // all docs:
                    if (result2 != START_COUNT && result2 != END_COUNT) {
                        err!!.printStackTrace()
                        fail(
                            "$testName: method did throw exception but hits.length for search on term 'aaa' is " +
                                "$result2 instead of expected $START_COUNT or $END_COUNT"
                        )
                    }
                }
                newReader.close()
                if (result2 == END_COUNT) {
                    break
                }
            }
            dir.close()

            // Try again with more bytes of free space:
            diskFree += maxOf(10, diskFree ushr 3)
        }
        startDir.close()
    }

    // @Ignore
    // This test tests that buffered deletes are cleared when
    // an Exception is hit during flush.
    // (not annotated with @Test — upstream uses @Ignore)
    @Throws(IOException::class)
    fun testErrorAfterApplyDeletes() {

        val failure = object : MockDirectoryWrapper.Failure() {
            var sawMaybe = false
            var failed = false

            override fun reset(): MockDirectoryWrapper.Failure {
                sawMaybe = false
                failed = false
                return this
            }

            override fun eval(dir: MockDirectoryWrapper) {
                if (VERBOSE) {
                    println("FAIL EVAL:")
                }
                if (sawMaybe && !failed) {
                    val seen = callStackContainsAnyOf("applyDeletesAndUpdates", "slowFileExists")
                    if (!seen) {
                        // Only fail once we are no longer in applyDeletes
                        failed = true
                        if (VERBOSE) {
                            println("TEST: mock failure: now fail")
                        }
                        throw RuntimeException("fail after applyDeletes")
                    }
                }
                if (!failed) {
                    if (callStackContainsAnyOf("applyDeletesAndUpdates")) {
                        if (VERBOSE) {
                            println("TEST: mock failure: saw applyDeletes")
                        }
                        sawMaybe = true
                    }
                }
            }
        }

        // create a couple of files

        val keywords = arrayOf("1", "2")
        val unindexed = arrayOf("Netherlands", "Italy")
        val unstored = arrayOf("Amsterdam has lots of bridges", "Venice has lots of canals")
        val text = arrayOf("Amsterdam", "Venice")

        val dir = newMockDirectory()
        val modifier = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                .setReaderPooling(false)
                .setMergePolicy(newLogMergePolicy())
        )

        val lmp = modifier.config.mergePolicy
        lmp.noCFSRatio = 1.0

        dir.failOn(failure.reset())

        val custom1 = FieldType()
        custom1.setStored(true)
        for (i in keywords.indices) {
            val doc = Document()
            doc.add(newStringField("id", keywords[i], Field.Store.YES))
            doc.add(newField("country", unindexed[i], custom1))
            doc.add(newTextField("contents", unstored[i], Field.Store.NO))
            doc.add(newTextField("city", text[i], Field.Store.YES))
            modifier.addDocument(doc)
        }
        // flush

        if (VERBOSE) {
            println("TEST: now full merge")
        }

        modifier.forceMerge(1)
        if (VERBOSE) {
            println("TEST: now commit")
        }
        modifier.commit()

        // one of the two files hits

        val term = Term("city", "Amsterdam")
        var hitCount = getHitCount(dir, term)
        assertEquals(1, hitCount)

        // open the writer again (closed above)

        // delete the doc
        // max buf del terms is two, so this is buffered

        if (VERBOSE) {
            println("TEST: delete term=$term")
        }

        modifier.deleteDocuments(term)

        // add a doc,
        // doc remains buffered

        if (VERBOSE) {
            println("TEST: add empty doc")
        }
        val doc = Document()
        modifier.addDocument(doc)

        // commit the changes, the buffered deletes, and the new doc

        // The failure object will fail on the first write after the del
        // file gets created when processing the buffered delete

        // in the ac case, this will be when writing the new segments
        // files so we really don't need the new doc, but it's harmless

        // a new segments file won't be created but in this
        // case, creation of the cfs file happens next so we
        // need the doc (to test that it's okay that we don't
        // lose deletes if failing while creating the cfs file)

        if (VERBOSE) {
            println("TEST: now commit for failure")
        }
        val expected = assertFailsWith<RuntimeException> { modifier.commit() }
        if (VERBOSE) {
            println("TEST: hit exc:")
            expected.printStackTrace()
        }

        // The commit above failed, so we need to retry it (which will
        // succeed, because the failure is a one-shot)

        var writerClosed = false
        try {
            modifier.commit()
            writerClosed = false
        } catch (ise: IllegalStateException) {
            // The above exc struck during merge, and closed the writer
            writerClosed = true
        }

        if (!writerClosed) {
            hitCount = getHitCount(dir, term)

            // Make sure the delete was successfully flushed:
            assertEquals(0, hitCount)

            modifier.close()
        }
        dir.close()
    }

    // This test tests that the files created by the docs writer before
    // a segment is written are cleaned up if there's an i/o error

    @Test
    @Throws(IOException::class)
    fun testErrorInDocsWriterAdd() {

        val failure = object : MockDirectoryWrapper.Failure() {
            var failed = false

            override fun reset(): MockDirectoryWrapper.Failure {
                failed = false
                return this
            }

            override fun eval(dir: MockDirectoryWrapper) {
                if (!failed) {
                    failed = true
                    throw IOException("fail in add doc")
                }
            }
        }

        // create a couple of files

        val keywords = arrayOf("1", "2")
        val unindexed = arrayOf("Netherlands", "Italy")
        val unstored = arrayOf("Amsterdam has lots of bridges", "Venice has lots of canals")
        val text = arrayOf("Amsterdam", "Venice")

        val dir = newMockDirectory()
        val modifier = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        )
        modifier.commit()
        dir.failOn(failure.reset())

        val custom1 = FieldType()
        custom1.setStored(true)
        for (i in keywords.indices) {
            val doc = Document()
            doc.add(newStringField("id", keywords[i], Field.Store.YES))
            doc.add(newField("country", unindexed[i], custom1))
            doc.add(newTextField("contents", unstored[i], Field.Store.NO))
            doc.add(newTextField("city", text[i], Field.Store.YES))
            try {
                modifier.addDocument(doc)
            } catch (io: IOException) {
                if (VERBOSE) {
                    println("TEST: got expected exc:")
                    io.printStackTrace()
                }
                break
            }
        }
        assertTrue(modifier.isDeleterClosed())

        TestIndexWriter.assertNoUnreferencedFiles(
            dir, "docsWriter.abort() failed to delete unreferenced files"
        )
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDeleteNullQuery() {
        val dir = newDirectory()
        val modifier = IndexWriter(
            dir,
            IndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        )

        for (i in 0 until 5) {
            addDoc(modifier, i, 2 * i)
        }

        modifier.deleteDocuments(TermQuery(Term("nada", "nada")))
        modifier.commit()
        assertEquals(5, modifier.getDocStats().numDocs)
        modifier.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteAllSlowly() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val NUM_DOCS = atLeast(1000)
        val ids = ArrayList<Int>(NUM_DOCS)
        for (id in 0 until NUM_DOCS) {
            ids.add(id)
        }
        ids.shuffle(random())
        for (id in ids) {
            val doc = Document()
            doc.add(newStringField("id", "$id", Field.Store.NO))
            w.addDocument(doc)
        }
        ids.shuffle(random())
        var upto = 0
        while (upto < ids.size) {
            val left = ids.size - upto
            val inc = minOf(left, TestUtil.nextInt(random(), 1, 20))
            val limit = upto + inc
            while (upto < limit) {
                if (VERBOSE) {
                    println("TEST: delete id=${ids[upto]}")
                }
                w.deleteDocuments(Term("id", "${ids[upto++]}"))
            }
            if (VERBOSE) {
                println("\nTEST: now open reader")
            }
            val r = w.getReader(true, true)
            assertEquals(NUM_DOCS - upto, r.numDocs())
            r.close()
        }

        w.close()
        dir.close()
    }

    // TODO: this test can hit pathological cases (IW settings?) where it runs for far too long
    // @Nightly
    @Test
    @Throws(Exception::class)
    fun testIndexingThenDeleting() {
        // TODO: move this test to its own class and just @SuppressCodecs?
        // TODO: is it enough to just use newFSDirectory?
        val fieldFormat = TestUtil.getPostingsFormat("field")
        assumeFalse("This test cannot run with SimpleText codec", fieldFormat == "SimpleText")
        assumeFalse("This test cannot run with Direct codec", fieldFormat == "Direct")
        val r = random()
        val dir = newDirectory()
        // note this test explicitly disables payloads
        val analyzer = object : org.gnit.lucenekmp.analysis.Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, true))
            }
        }
        val w = IndexWriter(
            dir,
            newIndexWriterConfig(analyzer)
                .setRAMBufferSizeMB(4.0)
                .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        )
        val doc = Document()
        doc.add(
            newTextField(
                "field", "go 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20", Field.Store.NO
            )
        )
        val num = atLeast(1)
        for (iter in 0 until num) {
            var count = 0

            val doIndexing = r.nextBoolean()
            if (VERBOSE) {
                println("TEST: iter doIndexing=$doIndexing")
            }
            if (doIndexing) {
                // Add docs until a flush is triggered
                val startFlushCount = w.getFlushCount()
                while (w.getFlushCount() == startFlushCount) {
                    w.addDocument(doc)
                    count++
                }
            } else {
                // Delete docs until a flush is triggered
                val startFlushCount = w.getFlushCount()
                while (w.getFlushCount() == startFlushCount) {
                    w.deleteDocuments(Term("foo", "" + count))
                    count++
                }
            }
            assertTrue(
                count > 2500,
                "flush happened too quickly during ${if (doIndexing) "indexing" else "deleting"} count=$count"
            )
        }
        w.close()
        dir.close()
    }

    // LUCENE-3340: make sure deletes that we don't apply
    // during flush (ie are just pushed into the stream) are
    // in fact later flushed due to their RAM usage:
    @Test
    @Throws(Exception::class)
    fun testFlushPushedDeletesByRAM() {
        val dir = newDirectory()
        // Cannot use RandomIndexWriter because we don't want to
        // ever call commit() for this test:
        // note: tiny RAM buffer used, as with a 1MB buffer the test is too slow (flush @ 128,999)
        val w = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setRAMBufferSizeMB(0.5)
                .setMaxBufferedDocs(1000)
                .setMergePolicy(NoMergePolicy.INSTANCE)
                .setReaderPooling(false)
        )
        var count = 0
        while (true) {
            val doc = Document()
            doc.add(StringField("id", "$count", Field.Store.NO))
            val delTerm: Term
            if (count == 1010) {
                // This is the only delete that applies
                delTerm = Term("id", "0")
            } else {
                // These get buffered, taking up RAM, but delete
                // nothing when applied:
                delTerm = Term("id", "x$count")
            }
            w.updateDocument(delTerm, doc)
            // Eventually segment 0 should get a del docs:
            // TODO: fix this test
            if (slowFileExists(dir, "_0_1.del") || slowFileExists(dir, "_0_1.liv")) {
                if (VERBOSE) {
                    println("TEST: deletes created @ count=$count")
                }
                break
            }
            count++

            // Today we applyDeletes @ count=21553; even if we make
            // sizable improvements to RAM efficiency of buffered
            // del term we're unlikely to go over 100K:
            if (count > 100000) {
                fail("delete's were not applied")
            }
        }
        w.close()
        dir.close()
    }

    // Make sure buffered (pushed) deletes don't use up so
    // much RAM that it forces long tail of tiny segments:
    // @Nightly
    @Test
    @Throws(Exception::class)
    fun testApplyDeletesOnFlush() {
        val dir = newDirectory()
        // Cannot use RandomIndexWriter because we don't want to
        // ever call commit() for this test:
        val docsInSegment = AtomicInt(0)
        val closing = AtomicBoolean(false)
        val sawAfterFlush = AtomicBoolean(false)
        val w = object : IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setRAMBufferSizeMB(0.5)
                .setMaxBufferedDocs(-1)
                .setMergePolicy(NoMergePolicy.INSTANCE)
                .setReaderPooling(false)
                // always use CFS so we don't use tons of file handles in the test
                .setUseCompoundFile(true)
        ) {
            override fun doAfterFlush() {
                assertTrue(
                    closing.load() || docsInSegment.load() >= 7,
                    "only ${docsInSegment.load()} in segment"
                )
                docsInSegment.store(0)
                sawAfterFlush.store(true)
            }
        }
        var id = 0
        while (true) {
            val sb = StringBuilder()
            for (termIDX in 0 until 100) {
                sb.append(' ').append(TestUtil.randomRealisticUnicodeString(random()))
            }
            if (id == 500) {
                w.deleteDocuments(Term("id", "0"))
            }
            val doc = Document()
            doc.add(newStringField("id", "$id", Field.Store.NO))
            doc.add(newTextField("body", sb.toString(), Field.Store.NO))
            w.updateDocument(Term("id", "$id"), doc)
            docsInSegment.fetchAndAdd(1)
            // TODO: fix this test
            if (slowFileExists(dir, "_0_1.del") || slowFileExists(dir, "_0_1.liv")) {
                if (VERBOSE) {
                    println("TEST: deletes created @ id=$id")
                }
                break
            }
            id++
        }
        closing.store(true)
        assertTrue(sawAfterFlush.load())
        w.close()
        dir.close()
    }

    // LUCENE-4455
    @Test
    @Throws(Exception::class)
    fun testDeletesCheckIndexOutput() {
        val dir = newDirectory()
        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergePolicy(NoMergePolicy.INSTANCE)
        iwc.setMaxBufferedDocs(2)
        var w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(newField("field", "0", StringField.TYPE_NOT_STORED))
        w.addDocument(doc)

        doc = Document()
        doc.add(newField("field", "1", StringField.TYPE_NOT_STORED))
        w.addDocument(doc)
        w.commit()
        assertEquals(1, w.getSegmentCount())

        w.deleteDocuments(Term("field", "0"))
        w.commit()
        assertEquals(1, w.getSegmentCount())
        w.close()

        var bos = ByteArrayOutputStream(1024)
        val checker = CheckIndex(dir)
        checker.setInfoStream(PrintStream(false, bos), false)
        var indexStatus = checker.checkIndex(null)
        assertTrue(indexStatus.clean)
        checker.close()
        var s = bos.toString()

        // Segment should have deletions:
        assertTrue(s.contains("has deletions"))
        iwc = IndexWriterConfig(MockAnalyzer(random()))
        w = IndexWriter(dir, iwc)
        w.forceMerge(1)
        w.close()

        bos = ByteArrayOutputStream(1024)
        val checker2 = CheckIndex(dir)
        checker2.setInfoStream(PrintStream(false, bos), false)
        indexStatus = checker2.checkIndex(null)
        assertTrue(indexStatus.clean)
        checker2.close()
        s = bos.toString()
        assertFalse(s.contains("has deletions"))
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTryDeleteDocument() {
        val d = newDirectory()

        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        var w = IndexWriter(d, iwc)
        val doc = Document()
        w.addDocument(doc)
        w.addDocument(doc)
        w.addDocument(doc)
        w.close()

        iwc = IndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND)
        w = IndexWriter(d, iwc)
        val r = DirectoryReader.open(w, false, false)
        assertTrue(w.tryDeleteDocument(r, 1) != -1L)
        assertFalse((r as StandardDirectoryReader).isCurrent)
        assertTrue(w.tryDeleteDocument(r.leaves()[0].reader(), 0) != -1L)
        assertFalse((r as StandardDirectoryReader).isCurrent)
        r.close()
        w.close()

        val r2 = DirectoryReader.open(d)
        assertEquals(2, r2.numDeletedDocs())
        assertNotNull(MultiBits.getLiveDocs(r2))
        r2.close()
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNRTIsCurrentAfterDelete() {
        val d = newDirectory()
        var iwc = IndexWriterConfig(MockAnalyzer(random()))
        var w = IndexWriter(d, iwc)
        val doc = Document()
        w.addDocument(doc)
        w.addDocument(doc)
        w.addDocument(doc)
        w.addDocument(doc)
        w.addDocument(doc)
        val docWithId = Document()
        docWithId.add(StringField("id", "1", Field.Store.YES))
        w.addDocument(docWithId)
        w.close()
        iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND)
        w = IndexWriter(d, iwc)
        val r = DirectoryReader.open(w, false, false)
        w.deleteDocuments(Term("id", "1"))
        val r2 = DirectoryReader.open(w, true, true)
        assertFalse((r as StandardDirectoryReader).isCurrent)
        assertTrue((r2 as StandardDirectoryReader).isCurrent)
        IOUtils.close(r, r2, w, d)
    }

    @Test
    @Throws(Exception::class)
    fun testOnlyDeletesTriggersMergeOnClose() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(2)
        val mp = LogDocMergePolicy()
        mp.minMergeDocs = 1
        iwc.setMergePolicy(mp)
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = IndexWriter(dir, iwc)
        for (i in 0 until 38) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            w.addDocument(doc)
        }
        w.commit()

        for (i in 0 until 18) {
            w.deleteDocuments(Term("id", "$i"))
        }

        w.close()
        val r = DirectoryReader.open(dir)
        assertEquals(1, r.leaves().size)
        r.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testOnlyDeletesTriggersMergeOnGetReader() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(2)
        val mp = LogDocMergePolicy()
        mp.minMergeDocs = 1
        iwc.setMergePolicy(mp)
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = IndexWriter(dir, iwc)
        for (i in 0 until 38) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            w.addDocument(doc)
        }
        w.commit()

        for (i in 0 until 18) {
            w.deleteDocuments(Term("id", "$i"))
        }

        // First one triggers, but does not reflect, the merge:
        if (VERBOSE) {
            println("TEST: now get reader")
        }
        DirectoryReader.open(w).close()
        val r = DirectoryReader.open(w)
        assertEquals(1, r.leaves().size)
        r.close()

        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testOnlyDeletesTriggersMergeOnFlush() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(2)
        val mp = LogDocMergePolicy()
        mp.minMergeDocs = 1
        iwc.setMergePolicy(mp)
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = IndexWriter(dir, iwc)
        for (i in 0 until 38) {
            if (VERBOSE) {
                println("TEST: add doc $i")
            }
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            w.addDocument(doc)
        }
        if (VERBOSE) {
            println("TEST: commit1")
        }
        w.commit()

        // Deleting 18 out of the 20 docs in the first segment make it the same "level" as the other 9
        // which should cause a merge to kick off:
        for (i in 0 until 18) {
            w.deleteDocuments(Term("id", "$i"))
        }
        if (VERBOSE) {
            println("TEST: commit2")
        }
        w.close()

        val r = DirectoryReader.open(dir)
        assertEquals(1, r.leaves().size)
        r.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testOnlyDeletesDeleteAllDocs() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(2)
        val mp = LogDocMergePolicy()
        mp.minMergeDocs = 1
        iwc.setMergePolicy(mp)
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = IndexWriter(dir, iwc)
        for (i in 0 until 38) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            w.addDocument(doc)
        }
        w.commit()

        for (i in 0 until 38) {
            w.deleteDocuments(Term("id", "$i"))
        }

        val r = DirectoryReader.open(w)
        assertEquals(0, r.leaves().size)
        assertEquals(0, r.maxDoc())
        r.close()

        w.close()
        dir.close()
    }

    // Make sure merges still kick off after IW.deleteAll!
    @Test
    @Throws(Exception::class)
    fun testMergingAfterDeleteAll() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(2)
        val mp = LogDocMergePolicy()
        mp.minMergeDocs = 1
        iwc.setMergePolicy(mp)
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = IndexWriter(dir, iwc)
        for (i in 0 until 10) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            w.addDocument(doc)
        }
        w.commit()
        w.deleteAll()

        for (i in 0 until 100) {
            val doc = Document()
            doc.add(newStringField("id", "$i", Field.Store.NO))
            w.addDocument(doc)
        }

        w.forceMerge(1)

        val r = DirectoryReader.open(w)
        assertEquals(1, r.leaves().size)
        r.close()

        w.close()
        dir.close()
    }
}
