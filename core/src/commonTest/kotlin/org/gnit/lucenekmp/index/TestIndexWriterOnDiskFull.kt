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
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOSupplier
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Tests for IndexWriter when the disk runs out of space */
class TestIndexWriterOnDiskFull : LuceneTestCase() {

    /*
     * Make sure IndexWriter cleans up on hitting a disk
     * full exception in addDocument.
     * TODO: how to do this on windows with FSDirectory?
     */
    @Test
    @Throws(IOException::class)
    fun testAddDocumentOnDiskFull() {
        for (pass in 0 until 2) {
            if (VERBOSE) {
                println("TEST: pass=$pass")
            }
            val doAbort = pass == 1
            var diskFree = TestUtil.nextInt(random(), 100, 300).toLong()
            var indexExists = false
            while (true) {
                if (VERBOSE) {
                    println("TEST: cycle: diskFree=$diskFree")
                }
                val dir = MockDirectoryWrapper(random(), ByteBuffersDirectory())
                dir.maxSizeInBytes = diskFree
                val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
                val ms = writer.config.mergeScheduler
                if (ms is ConcurrentMergeScheduler) {
                    // This test intentionally produces exceptions
                    // in the threads that CMS launches; we don't
                    // want to pollute test output with these.
                    ms.setSuppressExceptions()
                }

                var hitError = false
                try {
                    repeat(200) {
                        addDoc(writer)
                    }
                    if (VERBOSE) {
                        println("TEST: done adding docs; now commit")
                    }
                    try {
                        // when calling commit(), if the writer is asynchronously closed
                        // by a fatal tragedy (e.g. from disk-full-on-merge with CMS),
                        // then we may receive either AlreadyClosedException OR IllegalStateException,
                        // depending on when it happens.
                        writer.commit()
                        indexExists = true
                    } catch (_: IOException) {
                        if (VERBOSE) {
                            println("TEST: exception on commit")
                        }
                        hitError = true
                    } catch (_: IllegalStateException) {
                        if (VERBOSE) {
                            println("TEST: exception on commit")
                        }
                        hitError = true
                    }
                    } catch (e: IOException) {
                        if (VERBOSE) {
                            println("TEST: exception on addDoc")
                            e.printStackTrace()
                        }
                        hitError = true
                    }

                if (hitError) {
                    if (doAbort) {
                        if (VERBOSE) {
                            println("TEST: now rollback")
                        }
                        writer.rollback()
                    } else {
                        try {
                            if (VERBOSE) {
                                println("TEST: now close")
                            }
                            writer.close()
                        } catch (_: IOException) {
                            if (VERBOSE) {
                                println("TEST: exception on close; retry w/ no disk space limit")
                            }
                            dir.maxSizeInBytes = 0
                            try {
                                writer.close()
                            } catch (_: AlreadyClosedException) {
                                // OK
                            }
                        } catch (_: IllegalStateException) {
                            if (VERBOSE) {
                                println("TEST: exception on close; retry w/ no disk space limit")
                            }
                            dir.maxSizeInBytes = 0
                            try {
                                writer.close()
                            } catch (_: AlreadyClosedException) {
                                // OK
                            }
                        }
                    }

                    // _TestUtil.syncConcurrentMerges(ms);

                    if (indexExists) {
                        // Make sure reader can open the index:
                        DirectoryReader.open(dir).close()
                    }

                    dir.close()
                    // Now try again w/ more space:

                    diskFree += if (TEST_NIGHTLY) {
                        TestUtil.nextInt(random(), 400, 600).toLong()
                    } else {
                        TestUtil.nextInt(random(), 3000, 5000).toLong()
                    }
                } else {
                    // _TestUtil.syncConcurrentMerges(writer);
                    dir.maxSizeInBytes = 0
                    writer.close()
                    dir.close()
                    break
                }
            }
        }
    }

    // TODO: make @Nightly variant that provokes more disk
    // fulls

    // TODO: have test fail if on any given top
    // iter there was not a single IOE hit

    /*
    Test: make sure when we run out of disk space or hit
    random IOExceptions in any of the addIndexes(*) calls
    that 1) index is not corrupt (searcher can open/search
    it) and 2) transactional semantics are followed:
    either all or none of the incoming documents were in
    fact added.
     */
    @Test
    @Throws(IOException::class)
    fun testAddIndexOnDiskFull() {
        // MemoryCodec, since it uses FST, is not necessarily
        // "additive", ie if you add up N small FSTs, then merge
        // them, the merged result can easily be larger than the
        // sum because the merged FST may use array encoding for
        // some arcs (which uses more space):

        val START_COUNT = 57
        val NUM_DIR = if (TEST_NIGHTLY) 50 else 5
        val END_COUNT = START_COUNT + NUM_DIR * if (TEST_NIGHTLY) 25 else 5

        // Build up a bunch of dirs that have indexes which we
        // will then merge together by calling addIndexes(*):
        val dirs = Array<Directory>(NUM_DIR) { newDirectory() }
        var inputDiskUsage = 0L
        for (i in 0 until NUM_DIR) {
            val writer = IndexWriter(dirs[i], newIndexWriterConfig(MockAnalyzer(random())))
            for (j in 0 until 25) {
                addDocWithIndex(writer, 25 * i + j)
            }
            writer.close()
            for (file in dirs[i].listAll()) {
                inputDiskUsage += dirs[i].fileLength(file)
            }
        }

        // Now, build a starting index that has START_COUNT docs.  We
        // will then try to addIndexes into a copy of this:
        val startDir = newMockDirectory()
        var writer = IndexWriter(startDir, newIndexWriterConfig(MockAnalyzer(random())))
        for (j in 0 until START_COUNT) {
            addDocWithIndex(writer, j)
        }
        writer.close()

        // Make sure starting index seems to be working properly:
        val searchTerm = Term("content", "aaa")
        var reader = DirectoryReader.open(startDir)
        assertEquals(57, reader.docFreq(searchTerm))

        var searcher = newSearcher(reader)
        var hits = searcher.search(TermQuery(searchTerm), 1000).scoreDocs
        assertEquals(57, hits.size)
        reader.close()

        // Iterate with larger and larger amounts of free
        // disk space.  With little free disk space,
        // addIndexes will certainly run out of space &
        // fail.  Verify that when this happens, index is
        // not corrupt and index in fact has added no
        // documents.  Then, we increase disk space by 2000
        // bytes each iteration.  At some point there is
        // enough free disk space and addIndexes should
        // succeed and index should show all documents were
        // added.

        // String[] files = startDir.listAll();
        val diskUsage = startDir.sizeInBytes()

        var startDiskUsage = 0L
        val files = startDir.listAll()
        for (file in files) {
            startDiskUsage += startDir.fileLength(file)
        }

        for (iter in 0 until 3) {
            if (VERBOSE) {
                println("TEST: iter=$iter")
            }

            // Start with 100 bytes more than we are currently using:
            var diskFree = diskUsage + TestUtil.nextInt(random(), 50, 200)

            val method = iter

            var success = false
            var done = false

            val methodName = when (method) {
                0 -> "addIndexes(Directory[]) + forceMerge(1)"
                1 -> "addIndexes(IndexReader[])"
                else -> "addIndexes(Directory[])"
            }

            while (!done) {
                if (VERBOSE) {
                    println("TEST: cycle...")
                }

                // Make a new dir that will enforce disk usage:
                val dir = MockDirectoryWrapper(random(), TestUtil.ramCopyOf(startDir))
                val iwc = newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.APPEND)
                    .setMergePolicy(newLogMergePolicy(false))
                writer = IndexWriter(dir, iwc)
                var err: Exception? = null

                for (x in 0 until 2) {
                    val ms = writer.config.mergeScheduler
                    if (ms is ConcurrentMergeScheduler) {
                        // This test intentionally produces exceptions
                        // in the threads that CMS launches; we don't
                        // want to pollute test output with these.
                        if (x == 0) {
                            ms.setSuppressExceptions()
                        } else {
                            ms.clearSuppressExceptions()
                        }
                    }

                    // Two loops: first time, limit disk space &
                    // throw random IOExceptions; second time, no
                    // disk space limit:

                    var rate = 0.05
                    val diskRatio = diskFree.toDouble() / diskUsage
                    val thisDiskFree: Long

                    var testName: String? = null

                    if (x == 0) {
                        dir.randomIOExceptionRateOnOpen = random().nextDouble() * 0.01
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
                            testName = "disk full test $methodName with disk full at $diskFree bytes"
                        }
                    } else {
                        dir.randomIOExceptionRateOnOpen = 0.0
                        thisDiskFree = 0
                        rate = 0.0
                        if (VERBOSE) {
                            testName = "disk full test $methodName with unlimited disk space"
                        }
                    }

                    if (VERBOSE) {
                        println("\ncycle: $testName")
                    }

                    dir.trackDiskUsage = true
                    dir.maxSizeInBytes = thisDiskFree
                    dir.randomIOExceptionRate = rate

                    try {
                        if (x == 0) {
                            if (VERBOSE) {
                                println("TEST: now addIndexes count=${dirs.size}")
                            }
                            writer.addIndexes(*dirs)
                            if (VERBOSE) {
                                println("TEST: now forceMerge")
                            }
                            writer.forceMerge(1)
                        } else if (method == 1) {
                            val readers = Array(dirs.size) { i -> DirectoryReader.open(dirs[i]) }
                            try {
                                TestUtil.addIndexesSlowly(writer, *readers)
                            } finally {
                                for (r in readers) {
                                    r.close()
                                }
                            }
                        } else {
                            writer.addIndexes(*dirs)
                        }

                        success = true
                        if (VERBOSE) {
                            println("  success!")
                        }

                        if (x == 0) {
                            done = true
                        }
                    } catch (e: IllegalStateException) {
                        success = false
                        err = e
                        if (VERBOSE) {
                            println("  hit Exception: $e")
                            e.printStackTrace()
                        }
                    } catch (e: IOException) {
                        success = false
                        err = e
                        if (VERBOSE) {
                            println("  hit Exception: $e")
                            e.printStackTrace()
                        }
                        if (x == 1) {
                            e.printStackTrace()
                            fail("$methodName hit IOException after disk space was freed up")
                        }
                    } catch (e: MergePolicy.MergeException) {
                        success = false
                        err = e
                        if (VERBOSE) {
                            println("  hit Exception: $e")
                            e.printStackTrace()
                        }
                    }

                    if (x == 1) {
                        // Make sure all threads from ConcurrentMergeScheduler are done
                        // TestUtil.syncConcurrentMerges(writer)
                    } else {
                        dir.randomIOExceptionRateOnOpen = 0.0
                        writer.rollback()
                        writer = IndexWriter(
                            dir,
                            newIndexWriterConfig(MockAnalyzer(random()))
                                .setOpenMode(OpenMode.APPEND)
                                .setMergePolicy(newLogMergePolicy(false))
                        )
                    }

                    if (VERBOSE) {
                        println("  now test readers")
                    }

                    // Finally, verify index is not corrupt, and, if
                    // we succeeded, we see all docs added, and if we
                    // failed, we see either all docs or no docs added
                    // (transactional semantics):
                    dir.randomIOExceptionRateOnOpen = 0.0
                    try {
                        reader = DirectoryReader.open(dir)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        fail("$testName: exception when creating IndexReader: $e")
                    }
                    val result = reader.docFreq(searchTerm)
                    if (success) {
                        if (result != START_COUNT) {
                            fail(
                                "$testName: method did not throw exception but docFreq('aaa') is " +
                                    "$result instead of expected $START_COUNT"
                            )
                        }
                    } else {
                        if (result != START_COUNT && result != END_COUNT) {
                            err?.printStackTrace()
                            fail(
                                "$testName: method did throw exception but docFreq('aaa') is " +
                                    "$result instead of expected $START_COUNT or $END_COUNT"
                            )
                        }
                    }

                    searcher = newSearcher(reader)
                    try {
                        hits = searcher.search(TermQuery(searchTerm), END_COUNT).scoreDocs
                    } catch (e: IOException) {
                        e.printStackTrace()
                        fail("$testName: exception when searching: $e")
                    }
                    val result2 = hits.size
                    if (success) {
                        if (result2 != result) {
                            fail(
                                "$testName: method did not throw exception but hits.length for search on term 'aaa' is " +
                                    "$result2 instead of expected $result"
                            )
                        }
                    } else {
                        if (result2 != result) {
                            err?.printStackTrace()
                            fail(
                                "$testName: method did throw exception but hits.length for search on term 'aaa' is " +
                                    "$result2 instead of expected $result"
                            )
                        }
                    }

                    reader.close()
                    if (VERBOSE) {
                        println("  count is $result")
                    }

                    if (done || result == END_COUNT) {
                        break
                    }
                }

                if (VERBOSE) {
                    println(
                        "  start disk = $startDiskUsage; input disk = $inputDiskUsage; max used = ${dir.maxUsedSizeInBytes}"
                    )
                }

                    if (done) {
                    // Javadocs state that temp free Directory space
                    // required is at most 2X total input size of
                    // indices so let's make sure:
                    assertTrue(
                        (dir.maxUsedSizeInBytes - startDiskUsage) < 2 * (startDiskUsage + inputDiskUsage),
                        "max free Directory space required exceeded 1X the total input index sizes during " +
                            "$methodName: max temp usage = ${dir.maxUsedSizeInBytes - startDiskUsage} bytes " +
                            "vs limit=${2 * (startDiskUsage + inputDiskUsage)}; starting disk usage = " +
                            "$startDiskUsage bytes; input index disk usage = $inputDiskUsage bytes"
                    )
                }

                // Make sure we don't hit disk full during close below:
                dir.maxSizeInBytes = 0
                dir.randomIOExceptionRate = 0.0
                dir.randomIOExceptionRateOnOpen = 0.0

                writer.close()

                dir.close()

                // Try again with more free space:
                diskFree += if (TEST_NIGHTLY) {
                    TestUtil.nextInt(random(), 4000, 8000).toLong()
                } else {
                    TestUtil.nextInt(random(), 40000, 80000).toLong()
                }
            }
        }

        startDir.close()
        for (dir in dirs) {
            dir.close()
        }
    }

    private class FailTwiceDuringMerge : MockDirectoryWrapper.Failure() {
        var didFail1 = false
        var didFail2 = false

        override fun eval(dir: MockDirectoryWrapper) {
            if (!doFail) {
                return
            }
            if (callStackContains(SegmentMerger::class, "mergeTerms") && !didFail1) {
                didFail1 = true
                throw IOException("fake disk full during mergeTerms")
            }
            if (callStackContains(LiveDocsFormat::class, "writeLiveDocs") && !didFail2) {
                didFail2 = true
                throw IOException("fake disk full while writing LiveDocs")
            }
        }
    }


    // LUCENE-2593
    @Ignore
    @Test
    @Throws(IOException::class)
    fun testCorruptionAfterDiskFullDuringMerge() {
        val dir = newMockDirectory()
        // IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new
        // MockAnalyzer(random)).setReaderPooling(true));
        val mp = LogDocMergePolicy()
        mp.mergeFactor = 2
        val w = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergeScheduler(SerialMergeScheduler())
                .setReaderPooling(true)
                .setMergePolicy(
                    object : FilterMergePolicy(mp) {
                        override fun keepFullyDeletedSegment(
                            readerIOSupplier: IOSupplier<CodecReader>
                        ): Boolean {
                            // we can do this because we add/delete/add (and dont merge to "nothing")
                            return true
                        }
                    }
                )
        )
        val doc = Document()

        doc.add(newTextField("f", "doctor who", Field.Store.NO))
        w.addDocument(doc)
        w.commit()

        w.deleteDocuments(Term("f", "who"))
        w.addDocument(doc)

        // disk fills up!
        val ftdm = FailTwiceDuringMerge()
        ftdm.setDoFail()
        dir.failOn(ftdm)

        expectThrows(IOException::class) {
            w.commit()
        }
        assertTrue(ftdm.didFail1 || ftdm.didFail2)

        TestUtil.checkIndex(dir)
        ftdm.clearDoFail()
        expectThrows(AlreadyClosedException::class) {
            w.addDocument(doc)
        }
        dir.close()
    }

    // LUCENE-1130: make sure immediate disk full on creating
    // an IndexWriter (hit during DWPT#updateDocuments()) is
    // OK:
    @Test
    @Throws(IOException::class)
    fun testImmediateDiskFull() {
        val dir = newMockDirectory()
        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergeScheduler(ConcurrentMergeScheduler())
                .setCommitOnClose(false)
        )
        writer.commit() // empty commit, to not create confusing situation with first commit
        dir.maxSizeInBytes = maxOf(1L, dir.sizeInBytes())
        val doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        doc.add(newField("field", "aaa bbb ccc ddd eee fff ggg hhh iii jjj", customType))
        expectThrows(IOException::class) {
            writer.addDocument(doc)
        }
        assertTrue(writer.isDeleterClosed())
        assertTrue(writer.isClosed())

        dir.close()
    }

    // TODO: these are also in TestIndexWriter... add a simple doc-writing method
    // like this to LuceneTestCase?
    private fun addDoc(writer: IndexWriter) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        doc.add(NumericDocValuesField("numericdv", 1))
        doc.add(IntPoint("point", 1))
        doc.add(IntPoint("point2d", 1, 1))
        writer.addDocument(doc)
    }

    private fun addDocWithIndex(writer: IndexWriter, index: Int) {
        val doc = Document()
        doc.add(newTextField("content", "aaa $index", Field.Store.NO))
        doc.add(newTextField("id", "$index", Field.Store.NO))
        doc.add(NumericDocValuesField("numericdv", 1))
        doc.add(IntPoint("point", 1))
        doc.add(IntPoint("point2d", 1, 1))
        writer.addDocument(doc)
    }
}
