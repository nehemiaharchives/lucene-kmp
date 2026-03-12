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
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockFixedLengthPayloadFilter
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestIndexWriterCommit : LuceneTestCase() {
    /*
     * Simple test for "commit on close": open writer then
     * add a bunch of docs, making sure reader does not see
     * these docs until writer is closed.
     */
    @Test
    @Throws(IOException::class)
    fun testCommitOnClose() {
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        for (i in 0 until 14) {
            TestIndexWriter.addDoc(writer)
        }
        writer.close()

        val searchTerm = Term("content", "aaa")
        var reader: DirectoryReader = DirectoryReader.open(dir)
        var searcher: IndexSearcher = newSearcher(reader)
        var hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(14, hits.size, "first number of hits")
        reader.close()

        reader = DirectoryReader.open(dir)

        val writer2 = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        for (i in 0 until 3) {
            for (j in 0 until 11) {
                TestIndexWriter.addDoc(writer2)
            }
            val r: IndexReader = DirectoryReader.open(dir)
            searcher = newSearcher(r)
            hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
            assertEquals(14, hits.size, "reader incorrectly sees changes from writer")
            r.close()
            assertTrue(reader.isCurrent, "reader should have still been current")
        }

        // Now, close the writer:
        writer2.close()
        assertFalse(reader.isCurrent, "reader should not be current now")

        val r: IndexReader = DirectoryReader.open(dir)
        searcher = newSearcher(r)
        hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(47, hits.size, "reader did not see changes after writer was closed")
        r.close()
        reader.close()
        dir.close()
    }

    /*
     * Simple test for "commit on close": open writer, then
     * add a bunch of docs, making sure reader does not see
     * them until writer has closed.  Then instead of
     * closing the writer, call abort and verify reader sees
     * nothing was added.  Then verify we can open the index
     * and add docs to it.
     */
    @Test
    @Throws(IOException::class)
    fun testCommitOnCloseAbort() {
        val dir: Directory = newDirectory()
        var writer = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(10)
        )
        for (i in 0 until 14) {
            TestIndexWriter.addDoc(writer)
        }
        writer.close()

        val searchTerm = Term("content", "aaa")
        var reader: IndexReader = DirectoryReader.open(dir)
        var searcher: IndexSearcher = newSearcher(reader)
        var hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(14, hits.size, "first number of hits")
        reader.close()

        writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(OpenMode.APPEND)
                .setMaxBufferedDocs(10)
        )
        for (j in 0 until 17) {
            TestIndexWriter.addDoc(writer)
        }
        // Delete all docs:
        writer.deleteDocuments(searchTerm)

        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(14, hits.size, "reader incorrectly sees changes from writer")
        reader.close()

        // Now, close the writer:
        writer.rollback()

        TestIndexWriter.assertNoUnreferencedFiles(dir, "unreferenced files remain after rollback()")

        reader = DirectoryReader.open(dir)
        searcher = newSearcher(reader)
        hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(14, hits.size, "saw changes after writer.abort")
        reader.close()

        // Now make sure we can re-open the index, add docs,
        // and all is good:
        writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setOpenMode(OpenMode.APPEND)
                .setMaxBufferedDocs(10)
        )

        for (i in 0 until 12) {
            for (j in 0 until 17) {
                TestIndexWriter.addDoc(writer)
            }
            val r: IndexReader = DirectoryReader.open(dir)
            searcher = newSearcher(r)
            hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
            assertEquals(14, hits.size, "reader incorrectly sees changes from writer")
            r.close()
        }

        writer.close()
        val r: IndexReader = DirectoryReader.open(dir)
        searcher = newSearcher(r)
        hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(218, hits.size, "didn't see changes after close")
        r.close()

        dir.close()
    }

    /*
     * Verify that a writer with "commit on close" indeed
     * cleans up the temp segments created after opening
     * that are not referenced by the starting segments
     * file.  We check this by using MockDirectoryWrapper to
     * measure max temp disk space used.
     */
    // TODO: can this write less docs/indexes?
    @Nightly
    @Test
    @Throws(IOException::class)
    fun testCommitOnCloseDiskUsage() {
        // MemoryCodec, since it uses FST, is not necessarily
        // "additive", ie if you add up N small FSTs, then merge
        // them, the merged result can easily be larger than the
        // sum because the merged FST may use array encoding for
        // some arcs (which uses more space):

        val dir: MockDirectoryWrapper = newMockDirectory()
        val analyzer = if (random().nextBoolean()) {
            // no payloads
            object : org.gnit.lucenekmp.analysis.Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(MockTokenizer(MockTokenizer.WHITESPACE, true))
                }
            }
        } else {
            // fixed length payloads
            val length = random().nextInt(200)
            object : org.gnit.lucenekmp.analysis.Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
                    return TokenStreamComponents(
                        tokenizer, MockFixedLengthPayloadFilter(random(), tokenizer, length)
                    )
                }
            }
        }

        var writer = IndexWriter(
            dir,
            newIndexWriterConfig(analyzer)
                .setMaxBufferedDocs(10)
                .setReaderPooling(false)
                .setMergePolicy(newLogMergePolicy(10))
        )
        for (j in 0 until 30) {
            TestIndexWriter.addDocWithIndex(writer, j)
        }
        writer.close()
        dir.resetMaxUsedSizeInBytes()

        dir.trackDiskUsage = true
        val startDiskUsage: Long = dir.maxUsedSizeInBytes
        writer = IndexWriter(
            dir,
            newIndexWriterConfig(analyzer)
                .setOpenMode(OpenMode.APPEND)
                .setMaxBufferedDocs(10)
                .setMergeScheduler(SerialMergeScheduler())
                .setReaderPooling(false)
                .setMergePolicy(newLogMergePolicy(10))
        )

        for (j in 0 until 1470) {
            TestIndexWriter.addDocWithIndex(writer, j)
        }
        val midDiskUsage: Long = dir.maxUsedSizeInBytes
        dir.resetMaxUsedSizeInBytes()
        writer.forceMerge(1)
        writer.close()

        DirectoryReader.open(dir).close()

        val endDiskUsage: Long = dir.maxUsedSizeInBytes

        // Ending index is 50X as large as starting index; due
        // to 3X disk usage normally we allow 150X max
        // transient usage.  If something is wrong w/ deleter
        // and it doesn't delete intermediate segments then it
        // will exceed this 150X:
        // System.out.println("start " + startDiskUsage + "; mid " + midDiskUsage + ";end " +
        // endDiskUsage);
        assertTrue(
            midDiskUsage < 150 * startDiskUsage,
            "writer used too much space while adding documents: mid=" +
                midDiskUsage + " start=" + startDiskUsage + " end=" + endDiskUsage +
                " max=" + (startDiskUsage * 150)
        )
        assertTrue(
            endDiskUsage < 150 * startDiskUsage,
            "writer used too much space after close: endDiskUsage=" +
                endDiskUsage + " startDiskUsage=" + startDiskUsage +
                " max=" + (startDiskUsage * 150)
        )
        dir.close()
    }

    /*
     * Verify that calling forceMerge when writer is open for
     * "commit on close" works correctly both for rollback()
     * and close().
     */
    @Test
    @Throws(IOException::class)
    fun testCommitOnCloseForceMerge() {
        val dir: Directory = newDirectory()
        var writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(10)
                .setMergePolicy(newLogMergePolicy(10))
        )
        for (j in 0 until 17) {
            TestIndexWriter.addDocWithIndex(writer, j)
        }
        writer.close()

        writer = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
        )
        writer.forceMerge(1)

        // Open a reader before closing (commiting) the writer:
        var reader: DirectoryReader = DirectoryReader.open(dir)

        // Reader should see index as multi-seg at this
        // point:
        assertTrue(reader.leaves().size > 1, "Reader incorrectly sees one segment")
        reader.close()

        // Abort the writer:
        writer.rollback()
        TestIndexWriter.assertNoUnreferencedFiles(dir, "aborted writer after forceMerge")

        // Open a reader after aborting writer:
        reader = DirectoryReader.open(dir)

        // Reader should still see index as multi-segment
        assertTrue(reader.leaves().size > 1, "Reader incorrectly sees one segment")
        reader.close()

        if (VERBOSE) {
            println("TEST: do real full merge")
        }
        writer = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.APPEND)
        )
        writer.forceMerge(1)
        writer.close()

        if (VERBOSE) {
            println("TEST: writer closed")
        }
        TestIndexWriter.assertNoUnreferencedFiles(dir, "aborted writer after forceMerge")

        // Open a reader after aborting writer:
        reader = DirectoryReader.open(dir)

        // Reader should see index as one segment
        assertEquals(1, reader.leaves().size, "Reader incorrectly sees more than one segment")
        reader.close()
        dir.close()
    }

    // LUCENE-2095: make sure with multiple threads commit
    // doesn't return until all changes are in fact in the
    // index
    @Test
    @Throws(Throwable::class)
    fun testCommitThreadSafety() {
        val NUM_THREADS = 5
        val maxIterations = 10 // TODO reduced from original for dev speed
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy())
        )
        TestUtil.reduceOpenFiles(w.w)
        w.commit()
        val failed = AtomicBoolean(false)
        val threads = Array(NUM_THREADS) { i ->
            Thread {
                try {
                    val doc = Document()
                    var r: DirectoryReader = DirectoryReader.open(dir)
                    val f: Field = newStringField("f", "", Field.Store.NO)
                    doc.add(f)
                    var iterations = 0
                    var count = 0
                    do {
                        if (failed.load()) break
                        for (j in 0 until 10) {
                            val s = "${i}_$count"
                            count++
                            f.setStringValue(s)
                            w.addDocument(doc)
                            w.commit()
                            val r2: DirectoryReader? = DirectoryReader.openIfChanged(r)
                            assertNotNull(r2)
                            assertTrue(r2 !== r)
                            r.close()
                            r = r2
                            assertEquals(
                                1,
                                r.docFreq(Term("f", s)),
                                "term=f:$s; r=$r"
                            )
                        }
                    } while (++iterations < maxIterations)
                    r.close()
                } catch (t: Throwable) {
                    failed.store(true)
                    throw RuntimeException(t)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertFalse(failed.load())
        w.close()
        dir.close()
    }

    // LUCENE-1044: test writer.commit() when ac=false
    @Test
    @Throws(IOException::class)
    fun testForceCommit() {
        val dir: Directory = newDirectory()

        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergePolicy(newLogMergePolicy(5))
        )
        writer.commit()

        for (i in 0 until 23) TestIndexWriter.addDoc(writer)

        var reader: DirectoryReader = DirectoryReader.open(dir)
        assertEquals(0, reader.numDocs())
        writer.commit()
        val reader2: DirectoryReader? = DirectoryReader.openIfChanged(reader)
        assertNotNull(reader2)
        assertEquals(0, reader.numDocs())
        assertEquals(23, reader2.numDocs())
        reader.close()

        for (i in 0 until 17) TestIndexWriter.addDoc(writer)
        assertEquals(23, reader2.numDocs())
        reader2.close()
        reader = DirectoryReader.open(dir)
        assertEquals(23, reader.numDocs())
        reader.close()
        writer.commit()

        reader = DirectoryReader.open(dir)
        assertEquals(40, reader.numDocs())
        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFutureCommit() {
        val dir: Directory = newDirectory()

        var w = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)
        )
        val doc = Document()
        w.addDocument(doc)

        // commit to "first"
        val commitData = HashMap<String, String>()
        commitData["tag"] = "first"
        w.setLiveCommitData(commitData.entries)
        w.commit()

        // commit to "second"
        w.addDocument(doc)
        commitData["tag"] = "second"
        w.setLiveCommitData(commitData.entries)
        w.close()

        // open "first" with IndexWriter
        var commit: IndexCommit? = null
        for (c in DirectoryReader.listCommits(dir)) {
            if (c.userData["tag"] == "first") {
                commit = c
                break
            }
        }

        assertNotNull(commit)

        w = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)
                .setIndexCommit(commit)
        )

        assertEquals(1, w.getDocStats().numDocs)

        // commit IndexWriter to "third"
        w.addDocument(doc)
        commitData["tag"] = "third"
        w.setLiveCommitData(commitData.entries)
        w.close()

        // make sure "second" commit is still there
        commit = null
        for (c in DirectoryReader.listCommits(dir)) {
            if (c.userData["tag"] == "second") {
                commit = c
                break
            }
        }

        assertNotNull(commit)

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testZeroCommits() {
        // Tests that if we don't call commit(), the directory has 0 commits. This has
        // changed since LUCENE-2386, where before IW would always commit on a fresh
        // new index.
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        assertFailsWith<IndexNotFoundException> {
            DirectoryReader.listCommits(dir)
        }

        // No changes still should generate a commit, because it's a new index.
        writer.close()
        assertEquals(1, DirectoryReader.listCommits(dir).size, "expected 1 commits!")
        dir.close()
    }

    // LUCENE-1274: test writer.prepareCommit()
    @Test
    @Throws(IOException::class)
    fun testPrepareCommit() {
        val dir: Directory = newDirectory()

        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergePolicy(newLogMergePolicy(5))
        )
        writer.commit()

        for (i in 0 until 23) TestIndexWriter.addDoc(writer)

        var reader: DirectoryReader = DirectoryReader.open(dir)
        assertEquals(0, reader.numDocs())

        writer.prepareCommit()

        val reader2: IndexReader = DirectoryReader.open(dir)
        assertEquals(0, reader2.numDocs())

        writer.commit()

        val reader3: IndexReader? = DirectoryReader.openIfChanged(reader)
        assertNotNull(reader3)
        assertEquals(0, reader.numDocs())
        assertEquals(0, reader2.numDocs())
        assertEquals(23, reader3.numDocs())
        reader.close()
        reader2.close()

        for (i in 0 until 17) TestIndexWriter.addDoc(writer)

        assertEquals(23, reader3.numDocs())
        reader3.close()
        reader = DirectoryReader.open(dir)
        assertEquals(23, reader.numDocs())
        reader.close()

        writer.prepareCommit()

        reader = DirectoryReader.open(dir)
        assertEquals(23, reader.numDocs())
        reader.close()

        writer.commit()
        reader = DirectoryReader.open(dir)
        assertEquals(40, reader.numDocs())
        reader.close()
        writer.close()
        dir.close()
    }

    // LUCENE-1274: test writer.prepareCommit()
    @Test
    @Throws(IOException::class)
    fun testPrepareCommitRollback() {
        val dir: Directory = newDirectory()

        var writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergePolicy(newLogMergePolicy(5))
        )
        writer.commit()

        for (i in 0 until 23) {
            TestIndexWriter.addDoc(writer)
        }

        var reader: DirectoryReader = DirectoryReader.open(dir)
        assertEquals(0, reader.numDocs())

        writer.prepareCommit()

        val reader2: IndexReader = DirectoryReader.open(dir)
        assertEquals(0, reader2.numDocs())

        writer.rollback()

        val reader3: IndexReader? = DirectoryReader.openIfChanged(reader)
        assertNull(reader3)
        assertEquals(0, reader.numDocs())
        assertEquals(0, reader2.numDocs())
        reader.close()
        reader2.close()

        // System.out.println("TEST: after rollback: " + Arrays.toString(dir.listAll()));

        writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        for (i in 0 until 17) {
            TestIndexWriter.addDoc(writer)
        }

        reader = DirectoryReader.open(dir)
        assertEquals(0, reader.numDocs())
        reader.close()

        writer.prepareCommit()

        reader = DirectoryReader.open(dir)
        assertEquals(0, reader.numDocs())
        reader.close()

        writer.commit()
        reader = DirectoryReader.open(dir)
        assertEquals(17, reader.numDocs())
        reader.close()
        writer.close()
        dir.close()
    }

    // LUCENE-1274
    @Test
    @Throws(IOException::class)
    fun testPrepareCommitNoChanges() {
        val dir: Directory = newDirectory()

        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        writer.prepareCommit()
        writer.commit()
        writer.close()

        val reader: IndexReader = DirectoryReader.open(dir)
        assertEquals(0, reader.numDocs())
        reader.close()
        dir.close()
    }

    // LUCENE-1382
    @Test
    @Throws(IOException::class)
    fun testCommitUserData() {
        val dir: Directory = newDirectory()
        var w = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
        )
        for (j in 0 until 17) TestIndexWriter.addDoc(w)
        w.close()

        var r: DirectoryReader = DirectoryReader.open(dir)
        // commit(Map) never called for this index
        assertEquals(0, r.indexCommit.userData.size)
        r.close()

        w = IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
        )
        for (j in 0 until 17) TestIndexWriter.addDoc(w)
        val data = HashMap<String, String>()
        data["label"] = "test1"
        w.setLiveCommitData(data.entries)
        w.close()

        r = DirectoryReader.open(dir)
        assertEquals("test1", r.indexCommit.userData["label"])
        r.close()

        w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        w.forceMerge(1)
        w.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPrepareCommitThenClose() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        w.addDocument(Document())

        w.prepareCommit()
        assertFailsWith<IllegalStateException> {
            w.close()
        }
        w.commit()
        w.close()

        val r: DirectoryReader = DirectoryReader.open(dir)
        assertEquals(1, r.maxDoc())
        r.close()
        dir.close()
    }

    // LUCENE-7335: make sure commit data is late binding
    @Test
    @Throws(Exception::class)
    fun testCommitDataIsLive() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        w.addDocument(Document())

        val commitData = HashMap<String, String>()
        commitData["foo"] = "bar"

        // make sure "foo" / "bar" doesn't take
        w.setLiveCommitData(commitData.entries)

        commitData.clear()
        commitData["boo"] = "baz"

        // this finally does the commit, and should burn "boo" / "baz"
        w.close()

        val commits: List<IndexCommit> = DirectoryReader.listCommits(dir)
        assertEquals(1, commits.size)

        val commit: IndexCommit = commits[0]
        val data: Map<String, String> = commit.userData
        assertEquals(1, data.size)
        assertEquals("baz", data["boo"])
        dir.close()
    }
}
