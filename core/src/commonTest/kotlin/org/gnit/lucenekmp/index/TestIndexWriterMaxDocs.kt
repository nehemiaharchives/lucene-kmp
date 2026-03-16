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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopScoreDocCollectorManager
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.NoLockFactory
import org.gnit.lucenekmp.tests.index.Test2BConstants
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.TestUtil
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TestIndexWriterMaxDocs : LuceneTestCase() {

    // @Monster("takes over two hours")
    @Test
    @Throws(Exception::class)
    fun testExactlyAtTrueLimit() {
        val dir = newFSDirectory(createTempDir("2BDocs3"))
        IndexWriter.setMaxDocs(Test2BConstants.MAX_DOCS) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to Test2BConstants.MAX_DOCS for dev speed
        try {
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { iw ->
                val doc = Document()
                doc.add(StringField("field", "text", Field.Store.NO))
                for (i in 0..<Test2BConstants.MAX_DOCS) { // TODO reduced maxDocs = IndexWriter.MAX_DOCS to Test2BConstants.MAX_DOCS for dev speed
                    iw.addDocument(doc)
                }
                iw.commit()

                // First unoptimized, then optimized:
                for (i in 0..<2) {
                    DirectoryReader.open(dir).use { ir ->
                        assertEquals(Test2BConstants.MAX_DOCS, ir.maxDoc()) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to Test2BConstants.MAX_DOCS for dev speed
                        assertEquals(Test2BConstants.MAX_DOCS, ir.numDocs()) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to Test2BConstants.MAX_DOCS for dev speed
                        val searcher = IndexSearcher(ir)
                        val collectorManager =
                            TopScoreDocCollectorManager(10, null, Int.MAX_VALUE)
                        var hits = searcher.search(
                            TermQuery(Term("field", "text")),
                            collectorManager
                        )
                        assertEquals(Test2BConstants.MAX_DOCS.toLong(), hits.totalHits.value) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to Test2BConstants.MAX_DOCS for dev speed

                        // Sort by docID reversed:
                        hits = searcher.search(
                            TermQuery(Term("field", "text")),
                            10,
                            Sort(SortField(null, SortField.Type.DOC, true))
                        )
                        assertEquals(Test2BConstants.MAX_DOCS.toLong(), hits.totalHits.value) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to Test2BConstants.MAX_DOCS for dev speed
                        assertEquals(10, hits.scoreDocs.size)
                        assertEquals(Test2BConstants.MAX_DOCS - 1, hits.scoreDocs[0].doc) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to Test2BConstants.MAX_DOCS for dev speed
                    }

                    iw.forceMerge(1)
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAddDocument() {
        IndexWriter.setMaxDocs(10)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<10) {
                        w.addDocument(Document())
                    }

                    // 11th document should fail:
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAddDocuments() {
        IndexWriter.setMaxDocs(10)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<10) {
                        w.addDocument(Document())
                    }

                    // 11th document should fail:
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocuments(listOf(Document()))
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateDocument() {
        IndexWriter.setMaxDocs(10)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<10) {
                        w.addDocument(Document())
                    }

                    // 11th document should fail:
                    expectThrows(IllegalArgumentException::class) {
                        w.updateDocument(Term("field", "foo"), Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateDocuments() {
        IndexWriter.setMaxDocs(10)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<10) {
                        w.addDocument(Document())
                    }

                    // 11th document should fail:
                    expectThrows(IllegalArgumentException::class) {
                        w.updateDocuments(Term("field", "foo"), listOf(Document()))
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testReclaimedDeletes() {
        IndexWriter.setMaxDocs(10)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<10) {
                        val doc = Document()
                        doc.add(StringField("id", "$i", Field.Store.NO))
                        w.addDocument(doc)
                    }

                    // Delete 5 of them:
                    for (i in 0..<5) {
                        w.deleteDocuments(Term("id", "$i"))
                    }

                    w.forceMerge(1)

                    assertEquals(5, w.getDocStats().maxDoc)

                    // Add 5 more docs
                    for (i in 0..<5) {
                        w.addDocument(Document())
                    }

                    // 11th document should fail:
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    // Tests that 100% deleted segments (which IW "specializes" by dropping entirely) are not
    // mis-counted
    @Test
    @Throws(Exception::class)
    fun testReclaimedDeletesWholeSegments() {
        IndexWriter.setMaxDocs(10)
        try {
            newDirectory().use { dir ->
                val iwc = IndexWriterConfig(MockAnalyzer(random()))
                iwc.setMergePolicy(NoMergePolicy.INSTANCE)
                IndexWriter(dir, iwc).use { w ->
                    for (i in 0..<10) {
                        val doc = Document()
                        doc.add(StringField("id", "$i", Field.Store.NO))
                        w.addDocument(doc)
                        if (i % 2 == 0) {
                            // Make a new segment every 2 docs:
                            w.commit()
                        }
                    }

                    // Delete 5 of them:
                    for (i in 0..<5) {
                        w.deleteDocuments(Term("id", "$i"))
                    }

                    w.forceMerge(1)

                    assertEquals(5, w.getDocStats().maxDoc)

                    // Add 5 more docs
                    for (i in 0..<5) {
                        w.addDocument(Document())
                    }

                    // 11th document should fail:
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAddIndexes() {
        IndexWriter.setMaxDocs(10)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<10) {
                        w.addDocument(Document())
                    }
                }

                newDirectory().use { dir2 ->
                    IndexWriter(dir2, IndexWriterConfig(MockAnalyzer(random()))).use { w2 ->
                        w2.addDocument(Document())
                        expectThrows(IllegalArgumentException::class) {
                            w2.addIndexes(dir)
                        }

                        assertEquals(1, w2.getDocStats().maxDoc)
                        DirectoryReader.open(dir).use { ir ->
                            expectThrows(IllegalArgumentException::class) {
                                TestUtil.addIndexesSlowly(w2, ir)
                            }
                        }
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    // Make sure MultiReader lets you search exactly the limit number of docs:
    @Test
    @Throws(Exception::class)
    fun testMultiReaderExactLimit() {
        newDirectory().use { dir ->
            val doc = Document()
            IndexWriter.setMaxDocs(10) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to 10 for dev speed
            try {
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<4) { // TODO reduced valueA = 100000 to 4, valueB = IndexWriter.MAX_DOCS to 10 for dev speed
                        w.addDocument(doc)
                    }
                }

                val remainder = 2 // TODO reduced valueA = IndexWriter.MAX_DOCS % 100000 to 2, valueB = 100000 to 4 for dev speed
                newDirectory().use { dir2 ->
                    IndexWriter(dir2, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                        for (i in 0..<remainder) {
                            w.addDocument(doc)
                        }
                    }

                    val copies = 2 // TODO reduced valueA = IndexWriter.MAX_DOCS / 100000 to 2, valueB = 100000 to 4 for dev speed

                    DirectoryReader.open(dir).use { ir ->
                        DirectoryReader.open(dir2).use { ir2 ->
                            val subReaders = Array<IndexReader>(copies + 1) { ir }
                            subReaders[subReaders.size - 1] = ir2

                            MultiReader(*subReaders).use { mr ->
                                assertEquals(10, mr.maxDoc()) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to 10 for dev speed
                                assertEquals(10, mr.numDocs()) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to 10 for dev speed
                            }
                        }
                    }
                }
            } finally {
                IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
            }
        }
    }

    // Make sure MultiReader is upset if you exceed the limit
    @Test
    @Throws(Exception::class)
    fun testMultiReaderBeyondLimit() {
        newDirectory().use { dir ->
            val doc = Document()
            IndexWriter.setMaxDocs(10) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to 10 for dev speed
            try {
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<4) { // TODO reduced valueA = 100000 to 4, valueB = IndexWriter.MAX_DOCS to 10 for dev speed
                        w.addDocument(doc)
                    }
                }

                var remainder = 2 // TODO reduced valueA = IndexWriter.MAX_DOCS % 100000 to 2, valueB = 100000 to 4 for dev speed

                // One too many:
                remainder++

                newDirectory().use { dir2 ->
                    IndexWriter(dir2, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                        for (i in 0..<remainder) {
                            w.addDocument(doc)
                        }
                    }

                    val copies = 2 // TODO reduced valueA = IndexWriter.MAX_DOCS / 100000 to 2, valueB = 100000 to 4 for dev speed

                    DirectoryReader.open(dir).use { ir ->
                        DirectoryReader.open(dir2).use { ir2 ->
                            val subReaders = Array<IndexReader>(copies + 1) { ir }
                            subReaders[subReaders.size - 1] = ir2

                            expectThrows(IllegalArgumentException::class) {
                                MultiReader(*subReaders)
                            }
                        }
                    }
                }
            } finally {
                IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
            }
        }
    }

    /** LUCENE-6299: Test if addindexes(Dir[]) prevents exceeding max docs. */
    // TODO: can we use the setter to lower the amount of docs to be written here?
    @Nightly
    @Test
    @Throws(Exception::class)
    fun testAddTooManyIndexesDir() {
        IndexWriter.setMaxDocs(10) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to 10 for dev speed
        try {
            // we cheat and add the same one over again... IW wants a write lock on each
            newDirectory(random(), NoLockFactory.INSTANCE).use { dir ->
                val doc = Document()
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<4) { // TODO reduced valueA = 100000 to 4, valueB = IndexWriter.MAX_DOCS to 10 for dev speed
                        w.addDocument(doc)
                    }
                    w.forceMerge(1)
                    w.commit()
                }

                // wrap this with disk full, so test fails faster and doesn't fill up real disks.
                newMockDirectory().use { dir2 ->
                    IndexWriter(dir2, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                        w.commit() // don't confuse checkindex
                        dir2.maxSizeInBytes = dir2.sizeInBytes() + 65536 // 64KB
                        val dirs = Array<Directory>(3) { // TODO reduced valueA = 1 + (IndexWriter.MAX_DOCS / 100000) to 3, valueB = 100000 to 4 for dev speed
                            // bypass iw check for duplicate dirs
                            object : FilterDirectory(dir) {}
                        }

                        try {
                            w.addIndexes(*dirs)
                            fail("didn't get expected exception")
                        } catch (_: IllegalArgumentException) {
                            // pass
                        } catch (fakeDiskFull: IOException) {
                            val e: Exception =
                                if (fakeDiskFull.message?.startsWith("fake disk full") == true) {
                                    RuntimeException(
                                        "test failed: IW checks aren't working and we are executing addIndexes"
                                    ).also {
                                        it.addSuppressed(fakeDiskFull)
                                    }
                                } else {
                                    fakeDiskFull
                                }
                            throw e
                        }
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    /** LUCENE-6299: Test if addindexes(CodecReader[]) prevents exceeding max docs. */
    @Test
    @Throws(Exception::class)
    fun testAddTooManyIndexesCodecReader() {
        IndexWriter.setMaxDocs(10) // TODO reduced maxDocs = IndexWriter.MAX_DOCS to 10 for dev speed
        try {
            // we cheat and add the same one over again... IW wants a write lock on each
            newDirectory(random(), NoLockFactory.INSTANCE).use { dir ->
                val doc = Document()
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    for (i in 0..<4) { // TODO reduced valueA = 100000 to 4, valueB = IndexWriter.MAX_DOCS to 10 for dev speed
                        w.addDocument(doc)
                    }
                    w.forceMerge(1)
                    w.commit()
                }

                // wrap this with disk full, so test fails faster and doesn't fill up real disks.
                newMockDirectory().use { dir2 ->
                    DirectoryReader.open(dir).use { r ->
                        IndexWriter(dir2, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                            w.commit() // don't confuse checkindex
                            dir2.maxSizeInBytes = dir2.sizeInBytes() + 65536 // 64KB
                            val segReader = r.leaves()[0].reader() as CodecReader

                            val readers = Array<CodecReader>(3) { segReader } // TODO reduced valueA = 1 + (IndexWriter.MAX_DOCS / 100000) to 3, valueB = 100000 to 4 for dev speed

                            try {
                                w.addIndexes(*readers)
                                fail("didn't get expected exception")
                            } catch (_: IllegalArgumentException) {
                                // pass
                            } catch (fakeDiskFull: IOException) {
                                val e: Exception =
                                    if (fakeDiskFull.message?.startsWith("fake disk full") == true) {
                                        RuntimeException(
                                            "test failed: IW checks aren't working and we are executing addIndexes"
                                        ).also {
                                            it.addSuppressed(fakeDiskFull)
                                        }
                                    } else {
                                        fakeDiskFull
                                    }
                                throw e
                            }
                        }
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    @Test
    fun testTooLargeMaxDocs() {
        expectThrows(IllegalArgumentException::class) {
            IndexWriter.setMaxDocs(Int.MAX_VALUE)
        }
    }

    // LUCENE-6299
    @Test
    @Throws(Exception::class)
    fun testDeleteAll() {
        IndexWriter.setMaxDocs(1)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    w.addDocument(Document())
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }

                    w.deleteAll()
                    w.addDocument(Document())
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    // LUCENE-6299
    @Test
    @Throws(Exception::class)
    fun testDeleteAllAfterFlush() {
        IndexWriter.setMaxDocs(2)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    w.addDocument(Document())
                    DirectoryReader.open(w).use { }
                    w.addDocument(Document())
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }

                    w.deleteAll()
                    w.addDocument(Document())
                    w.addDocument(Document())
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    // LUCENE-6299
    @Test
    @Throws(Exception::class)
    fun testDeleteAllAfterCommit() {
        IndexWriter.setMaxDocs(2)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    w.addDocument(Document())
                    w.commit()
                    w.addDocument(Document())
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }

                    w.deleteAll()
                    w.addDocument(Document())
                    w.addDocument(Document())
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    // LUCENE-6299
    @Test
    @Throws(Exception::class)
    fun testDeleteAllMultipleThreads() {
        val limit = TestUtil.nextInt(random(), 2, 10)
        IndexWriter.setMaxDocs(limit)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    val startingGun = CountDownLatch(1)
                    val threads = Array(limit) {
                        Thread {
                            try {
                                startingGun.await()
                                w.addDocument(Document())
                            } catch (e: Exception) {
                                throw RuntimeException(e)
                            }
                        }
                    }
                    for (thread in threads) {
                        thread.start()
                    }

                    startingGun.countDown()

                    for (thread in threads) {
                        thread.join()
                    }

                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }

                    w.deleteAll()
                    for (i in 0..<limit) {
                        w.addDocument(Document())
                    }
                    expectThrows(IllegalArgumentException::class) {
                        w.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    // LUCENE-6299
    @Test
    @Throws(Exception::class)
    fun testDeleteAllAfterClose() {
        IndexWriter.setMaxDocs(2)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    w.addDocument(Document())
                }

                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w2 ->
                    w2.addDocument(Document())
                    expectThrows(IllegalArgumentException::class) {
                        w2.addDocument(Document())
                    }

                    w2.deleteAll()
                    w2.addDocument(Document())
                    w2.addDocument(Document())
                    expectThrows(IllegalArgumentException::class) {
                        w2.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    // LUCENE-6299
    @Test
    @Throws(Exception::class)
    fun testAcrossTwoIndexWriters() {
        IndexWriter.setMaxDocs(1)
        try {
            newDirectory().use { dir ->
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                    w.addDocument(Document())
                }
                IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w2 ->
                    expectThrows(IllegalArgumentException::class) {
                        w2.addDocument(Document())
                    }
                }
            }
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }

    // LUCENE-6299
    @Test
    @Throws(Exception::class)
    fun testCorruptIndexExceptionTooLarge() {
        newDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                w.addDocument(Document())
                w.addDocument(Document())
            }

            IndexWriter.setMaxDocs(1)
            try {
                expectThrows(CorruptIndexException::class) {
                    DirectoryReader.open(dir)
                }
            } finally {
                IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
            }
        }
    }

    // LUCENE-6299
    @Test
    @Throws(Exception::class)
    fun testCorruptIndexExceptionTooLargeWriter() {
        newDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { w ->
                w.addDocument(Document())
                w.addDocument(Document())
            }

            IndexWriter.setMaxDocs(1)
            try {
                expectThrows(CorruptIndexException::class) {
                    IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random()))).use { }
                }
            } finally {
                IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
            }
        }
    }
}
