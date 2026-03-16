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
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestIndexWriterFromReader : LuceneTestCase() {

    // Pull NRT reader immediately after writer has committed
    @Test
    fun testRightAfterCommit() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        w.commit()

        val r = DirectoryReader.open(w)
        assertEquals(1, r.maxDoc())
        w.close()

        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r.indexCommit)

        val w2 = IndexWriter(dir, iwc)
        r.close()

        assertEquals(1, w2.getDocStats().maxDoc)
        w2.addDocument(Document())
        assertEquals(2, w2.getDocStats().maxDoc)
        w2.close()

        val r2 = DirectoryReader.open(dir)
        assertEquals(2, r2.maxDoc())
        r2.close()
        dir.close()
    }

    // Open from non-NRT reader
    @Test
    fun testFromNonNRTReader() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        w.close()

        val r = DirectoryReader.open(dir)
        assertEquals(1, r.maxDoc())
        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r.indexCommit)

        val w2 = IndexWriter(dir, iwc)
        assertEquals(1, r.maxDoc())
        r.close()

        assertEquals(1, w2.getDocStats().maxDoc)
        w2.addDocument(Document())
        assertEquals(2, w2.getDocStats().maxDoc)
        w2.close()

        val r2 = DirectoryReader.open(dir)
        assertEquals(2, r2.maxDoc())
        r2.close()
        dir.close()
    }

    // Pull NRT reader from a writer on a new index with no commit:
    @Test
    fun testWithNoFirstCommit() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        val r = DirectoryReader.open(w)
        w.rollback()

        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r.indexCommit)

        val expected = expectThrows(IllegalArgumentException::class) {
            IndexWriter(dir, iwc)
        }
        assertEquals(
            "cannot use IndexWriterConfig.setIndexCommit() when index has no commit",
            expected.message
        )

        r.close()
        dir.close()
    }

    // Pull NRT reader after writer has committed and then indexed another doc:
    @Test
    fun testAfterCommitThenIndex() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        w.commit()
        w.addDocument(Document())

        val r = DirectoryReader.open(w)
        assertEquals(2, r.maxDoc())
        w.close()

        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r.indexCommit)

        val expected = expectThrows(IllegalArgumentException::class) {
            IndexWriter(dir, iwc)
        }
        assertTrue(expected.message!!.contains("the provided reader is stale: its prior commit file"))

        r.close()
        dir.close()
    }

    // NRT rollback: pull NRT reader after writer has committed and then before indexing another doc
    @Test
    fun testNRTRollback() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        w.commit()

        val r = DirectoryReader.open(w)
        assertEquals(1, r.maxDoc())

        // Add another doc
        w.addDocument(Document())
        assertEquals(2, w.getDocStats().maxDoc)
        w.close()

        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r.indexCommit)
        val expected = expectThrows(IllegalArgumentException::class) {
            IndexWriter(dir, iwc)
        }
        assertTrue(expected.message!!.contains("the provided reader is stale: its prior commit file"))

        r.close()
        dir.close()
    }

    @Test
    fun testRandom() {
        val dir = newDirectory()

        val numOps = atLeast(100)

        var w = IndexWriter(dir, newIndexWriterConfig())

        // We must have a starting commit for this test because whenever we rollback with
        // an NRT reader, the commit before that NRT reader must exist
        w.commit()

        var r = DirectoryReader.open(w)
        var nrtReaderNumDocs = 0
        var writerNumDocs = 0

        var commitAfterNRT = false

        var liveIDs: MutableSet<Int> = HashSet()
        var nrtLiveIDs: MutableSet<Int> = HashSet()

        for (op in 0 until numOps) {
            if (VERBOSE) {
                println(
                    "\nITER op=$op nrtReaderNumDocs=$nrtReaderNumDocs writerNumDocs=$writerNumDocs r=$r r.numDocs()=${r.numDocs()}"
                )
            }

            assertEquals(nrtReaderNumDocs, r.numDocs())
            val x = random().nextInt(5)

            when (x) {
                0 -> {
                    if (VERBOSE) {
                        println("  add doc id=$op")
                    }
                    // add doc
                    val doc = Document()
                    doc.add(newStringField("id", "$op", Field.Store.NO))
                    w.addDocument(doc)
                    liveIDs.add(op)
                    writerNumDocs++
                }

                1 -> {
                    if (VERBOSE) {
                        println("  delete doc")
                    }
                    // delete docs
                    if (liveIDs.size > 0) {
                        val id = random().nextInt(op)
                        if (VERBOSE) {
                            println("    id=$id")
                        }
                        w.deleteDocuments(Term("id", "$id"))
                        if (liveIDs.remove(id)) {
                            if (VERBOSE) {
                                println("    really deleted")
                            }
                            writerNumDocs--
                        }
                    } else {
                        if (VERBOSE) {
                            println("    nothing to delete yet")
                        }
                    }
                }

                2 -> {
                    // reopen NRT reader
                    if (VERBOSE) {
                        println("  reopen NRT reader")
                    }
                    val r2 = DirectoryReader.openIfChanged(r)
                    if (r2 != null) {
                        r.close()
                        r = r2
                        if (VERBOSE) {
                            println("    got new reader oldNumDocs=$nrtReaderNumDocs newNumDocs=$writerNumDocs")
                        }
                        nrtReaderNumDocs = writerNumDocs
                        nrtLiveIDs = HashSet(liveIDs)
                    } else {
                        if (VERBOSE) {
                            println("    reader is unchanged")
                        }
                        assertEquals(nrtReaderNumDocs, r.numDocs())
                    }
                    commitAfterNRT = false
                }

                3 -> {
                    if (!commitAfterNRT) {
                        // rollback writer to last nrt reader
                        if (random().nextBoolean()) {
                            if (VERBOSE) {
                                println("  close writer and open new writer from non-NRT reader numDocs=${w.getDocStats().numDocs}")
                            }
                            w.close()
                            r.close()
                            r = DirectoryReader.open(dir)
                            assertEquals(writerNumDocs, r.numDocs())
                            nrtReaderNumDocs = writerNumDocs
                            nrtLiveIDs = HashSet(liveIDs)
                        } else {
                            if (VERBOSE) {
                                println("  rollback writer and open new writer from NRT reader numDocs=${w.getDocStats().numDocs}")
                            }
                            w.rollback()
                        }
                        val iwc = newIndexWriterConfig()
                        iwc.setIndexCommit(r.indexCommit)
                        w = IndexWriter(dir, iwc)
                        writerNumDocs = nrtReaderNumDocs
                        liveIDs = HashSet(nrtLiveIDs)
                        r.close()
                        r = DirectoryReader.open(w)
                    }
                }

                4 -> {
                    if (VERBOSE) {
                        println("    commit")
                    }
                    w.commit()
                    commitAfterNRT = true
                }
            }
        }

        IOUtils.close(w, r, dir)
    }

    @Test
    fun testConsistentFieldNumbers() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        // Empty first commit:
        w.commit()

        var doc = Document()
        doc.add(newStringField("f0", "foo", Field.Store.NO))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        assertEquals(1, r.maxDoc())

        doc = Document()
        doc.add(newStringField("f1", "foo", Field.Store.NO))
        w.addDocument(doc)

        val r2 = DirectoryReader.openIfChanged(r)
        assertNotNull(r2)
        r.close()
        assertEquals(2, r2.maxDoc())
        w.rollback()

        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r2.indexCommit)

        val w2 = IndexWriter(dir, iwc)
        r2.close()

        doc = Document()
        doc.add(newStringField("f1", "foo", Field.Store.NO))
        doc.add(newStringField("f0", "foo", Field.Store.NO))
        w2.addDocument(doc)
        w2.close()
        dir.close()
    }

    @Test
    fun testInvalidOpenMode() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        w.commit()

        val r = DirectoryReader.open(w)
        assertEquals(1, r.maxDoc())
        w.close()

        val iwc = newIndexWriterConfig()
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
        iwc.setIndexCommit(r.indexCommit)
        val expected = expectThrows(IllegalArgumentException::class) {
            IndexWriter(dir, iwc)
        }
        assertEquals(
            "cannot use IndexWriterConfig.setIndexCommit() with OpenMode.CREATE",
            expected.message
        )

        IOUtils.close(r, dir)
    }

    @Test
    fun testOnClosedReader() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        w.commit()

        val r = DirectoryReader.open(w)
        assertEquals(1, r.maxDoc())
        val commit = r.indexCommit
        r.close()
        w.close()

        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(commit)
        expectThrows(AlreadyClosedException::class) {
            IndexWriter(dir, iwc)
        }

        IOUtils.close(r, dir)
    }

    @Test
    fun testStaleNRTReader() {
        val dir = newDirectory()
        var w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        w.commit()

        val r = DirectoryReader.open(w)
        assertEquals(1, r.maxDoc())
        w.addDocument(Document())

        val r2 = DirectoryReader.openIfChanged(r)
        assertNotNull(r2)
        r2.close()
        w.rollback()

        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r.indexCommit)
        w = IndexWriter(dir, iwc)
        assertEquals(1, w.getDocStats().numDocs)

        r.close()
        val r3 = DirectoryReader.open(w)
        assertEquals(1, r3.numDocs())

        w.addDocument(Document())
        val r4 = DirectoryReader.openIfChanged(r3)
        r3.close()
        assertEquals(2, r4!!.numDocs())
        r4.close()
        w.close()

        IOUtils.close(r, dir)
    }

    @Test
    fun testAfterRollback() {
        val dir = newDirectory()
        var w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        w.commit()
        w.addDocument(Document())

        val r = DirectoryReader.open(w)
        assertEquals(2, r.maxDoc())
        w.rollback()

        val iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r.indexCommit)
        w = IndexWriter(dir, iwc)
        assertEquals(2, w.getDocStats().numDocs)

        r.close()
        w.close()

        val r2 = DirectoryReader.open(dir)
        assertEquals(2, r2.numDocs())
        IOUtils.close(r2, dir)
    }

    // Pull NRT reader after writer has committed and then indexed another doc:
    @Test
    fun testAfterCommitThenIndexKeepCommits() {
        val dir = newDirectory()
        var iwc = newIndexWriterConfig()

        // Keep all commits:
        iwc.setIndexDeletionPolicy(
            object : IndexDeletionPolicy() {
                override fun onInit(commits: MutableList<out IndexCommit>) {}

                override fun onCommit(commits: MutableList<out IndexCommit>) {}
            }
        )

        val w = IndexWriter(dir, iwc)
        w.addDocument(Document())
        w.commit()
        w.addDocument(Document())

        val r = DirectoryReader.open(w)
        assertEquals(2, r.maxDoc())
        w.addDocument(Document())

        val r2 = DirectoryReader.open(w)
        assertEquals(3, r2.maxDoc())
        IOUtils.close(r2, w)

        // r is not stale because, even though we've committed the original writer since it was open, we
        // are keeping all commit points:
        iwc = newIndexWriterConfig()
        iwc.setIndexCommit(r.indexCommit)
        val w2 = IndexWriter(dir, iwc)
        assertEquals(2, w2.getDocStats().maxDoc)
        IOUtils.close(r, w2, dir)
    }
}
