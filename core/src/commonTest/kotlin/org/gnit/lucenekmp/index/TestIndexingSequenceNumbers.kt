package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestIndexingSequenceNumbers : LuceneTestCase() {
    private class Operation {
        // 0 = update, 1 = delete, 2 = commit, 3 = add
        var what: Byte = 0
        var id: Int = 0
        var threadID: Int = 0
        var seqNo: Long = 0
    }

    @Test
    fun testBasic() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val a = w.addDocument(Document())
        val b = w.addDocument(Document())
        assertTrue(b > a)
        w.close()
        dir.close()
    }

    @Test
    fun testAfterRefresh() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val a = w.addDocument(Document())
        DirectoryReader.open(w).close()
        val b = w.addDocument(Document())
        assertTrue(b > a)
        w.close()
        dir.close()
    }

    @Test
    fun testAfterCommit() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val a = w.addDocument(Document())
        w.commit()
        val b = w.addDocument(Document())
        assertTrue(b > a)
        w.close()
        dir.close()
    }

    @Nightly
    @Test
    fun testStressUpdateSameID() {
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val dir = newDirectory()
            val w = RandomIndexWriter(random(), dir)
            val threads = arrayOfNulls<Thread>(TestUtil.nextInt(random(), 2, 5))
            val startingGun = CountDownLatch(1)
            val seqNos = LongArray(threads.size)
            val id = Term("id", "id")
            for (i in threads.indices) {
                val threadID = i
                threads[i] =
                    Thread {
                        try {
                            val doc = Document()
                            doc.add(StoredField("thread", threadID))
                            doc.add(StringField("id", "id", Field.Store.NO))
                            startingGun.await()
                            for (j in 0 until 100) {
                                if (random().nextBoolean()) {
                                    seqNos[threadID] = w.updateDocument(id, doc)
                                } else {
                                    seqNos[threadID] = w.updateDocuments(id, listOf(doc))
                                }
                            }
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    }
                threads[i]!!.start()
            }
            startingGun.countDown()
            for (thread in threads) {
                thread!!.join()
            }

            var maxThread = 0
            val allSeqNos = mutableSetOf<Long>()
            for (i in threads.indices) {
                allSeqNos.add(seqNos[i])
                if (seqNos[i] > seqNos[maxThread]) {
                    maxThread = i
                }
            }
            assertEquals(threads.size, allSeqNos.size)
            val r = w.reader
            val s = newSearcher(r)
            val hits = s.search(TermQuery(id), 1)
            assertEquals(1L, hits.totalHits.value)
            val doc = r.storedFields().document(hits.scoreDocs[0].doc)
            assertEquals(maxThread, doc.getField("thread")!!.numericValue()!!.toInt())
            r.close()
            w.close()
            dir.close()
        }
    }

    @Nightly
    @Test
    fun testStressConcurrentCommit() {
        val opCount = atLeast(10000)
        val idCount = TestUtil.nextInt(random(), 10, 1000)

        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)

        val w = IndexWriter(dir, iwc)

        val numThreads = TestUtil.nextInt(random(), 2, 10)
        val threads = arrayOfNulls<Thread>(numThreads)
        val startingGun = CountDownLatch(1)
        val threadOps = mutableListOf<MutableList<Operation>>()

        val commitLock = ReentrantLock()
        val commits = mutableListOf<Operation>()

        for (i in threads.indices) {
            val ops = mutableListOf<Operation>()
            threadOps.add(ops)
            val threadID = i
            threads[i] =
                Thread {
                    try {
                        startingGun.await()
                        for (j in 0 until opCount) {
                            val op = Operation()
                            op.threadID = threadID
                            if (random().nextInt(500) == 17) {
                                op.what = 2
                                commitLock.withLock {
                                    op.seqNo = w.commit()
                                    if (op.seqNo != -1L) {
                                        commits.add(op)
                                    }
                                }
                            } else {
                                op.id = random().nextInt(idCount)
                                val idTerm = Term("id", "${op.id}")
                                if (random().nextInt(10) == 1) {
                                    op.what = 1
                                    if (random().nextBoolean()) {
                                        op.seqNo = w.deleteDocuments(idTerm)
                                    } else {
                                        op.seqNo = w.deleteDocuments(TermQuery(idTerm))
                                    }
                                } else {
                                    val doc = Document()
                                    doc.add(StoredField("thread", threadID))
                                    doc.add(StringField("id", "${op.id}", Field.Store.NO))
                                    if (random().nextBoolean()) {
                                        val docs = mutableListOf<Document>()
                                        docs.add(doc)
                                        op.seqNo = w.updateDocuments(idTerm, docs)
                                    } else {
                                        op.seqNo = w.updateDocument(idTerm, doc)
                                    }
                                    op.what = 0
                                }
                                ops.add(op)
                            }
                        }
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            threads[i]!!.setName("thread$threadID")
            threads[i]!!.start()
        }
        startingGun.countDown()
        for (thread in threads) {
            thread!!.join()
        }

        val commitOp = Operation()
        commitOp.seqNo = w.commit()
        if (commitOp.seqNo != -1L) {
            commits.add(commitOp)
        }

        val indexCommits = DirectoryReader.listCommits(dir)
        assertEquals(commits.size, indexCommits.size)

        val expectedThreadIDs = IntArray(idCount)
        val seqNos = LongArray(idCount)

        for (i in commits.indices) {
            val commitSeqNo = commits[i].seqNo
            Arrays.fill(expectedThreadIDs, -1)
            Arrays.fill(seqNos, 0)

            for (threadID in threadOps.indices) {
                var lastSeqNo = 0L
                for (op in threadOps[threadID]) {
                    if (op.seqNo <= commitSeqNo && op.seqNo > seqNos[op.id]) {
                        seqNos[op.id] = op.seqNo
                        if (op.what == 0.toByte()) {
                            expectedThreadIDs[op.id] = threadID
                        } else {
                            expectedThreadIDs[op.id] = -1
                        }
                    }

                    assertTrue(op.seqNo > lastSeqNo)
                    lastSeqNo = op.seqNo
                }
            }

            val r = DirectoryReader.open(indexCommits[i])
            val s = IndexSearcher(r)

            for (id in 0 until idCount) {
                val hits = s.search(TermQuery(Term("id", "$id")), 1)

                if (expectedThreadIDs[id] != -1) {
                    assertEquals(1L, hits.totalHits.value)
                    val doc = r.storedFields().document(hits.scoreDocs[0].doc)
                    val actualThreadID = doc.getField("thread")!!.numericValue()!!.toInt()
                    if (expectedThreadIDs[id] != actualThreadID) {
                        println(
                            "FAIL: id=$id expectedThreadID=${expectedThreadIDs[id]} vs actualThreadID=$actualThreadID commitSeqNo=$commitSeqNo numThreads=$numThreads"
                        )
                        for (threadID in threadOps.indices) {
                            for (op in threadOps[threadID]) {
                                if (id == op.id) {
                                    println(
                                        "  threadID=$threadID seqNo=${op.seqNo} ${if (op.what == 2.toByte()) "updated" else "deleted"}"
                                    )
                                }
                            }
                        }
                        assertEquals(expectedThreadIDs[id], actualThreadID, "id=$id")
                    }
                } else if (hits.totalHits.value != 0L) {
                    println(
                        "FAIL: id=$id expectedThreadID=${expectedThreadIDs[id]} vs totalHits=${hits.totalHits.value} commitSeqNo=$commitSeqNo numThreads=$numThreads"
                    )
                    for (threadID in threadOps.indices) {
                        for (op in threadOps[threadID]) {
                            if (id == op.id) {
                                println(
                                    "  threadID=$threadID seqNo=${op.seqNo} ${if (op.what == 2.toByte()) "updated" else "del"}"
                                )
                            }
                        }
                    }
                    assertEquals(0L, hits.totalHits.value)
                }
            }
            w.close()
            r.close()
        }

        dir.close()
    }

    @Nightly
    @Test
    fun testStressConcurrentDocValuesUpdatesCommit() {
        val opCount = atLeast(10000)
        val idCount = TestUtil.nextInt(random(), 10, 1000)

        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)

        val w = IndexWriter(dir, iwc)

        val numThreads = TestUtil.nextInt(random(), 2, 10)
        if (VERBOSE) {
            println("TEST: numThreads=$numThreads")
        }
        val threads = arrayOfNulls<Thread>(numThreads)
        val startingGun = CountDownLatch(1)
        val threadOps = mutableListOf<MutableList<Operation>>()

        val commitLock = ReentrantLock()
        val commits = mutableListOf<Operation>()

        val ops1 = mutableListOf<Operation>()
        threadOps.add(ops1)

        for (id in 0 until idCount) {
            val threadID = 0
            val op = Operation()
            op.threadID = threadID
            op.id = id

            val doc = Document()
            doc.add(StoredField("thread", threadID))
            doc.add(NumericDocValuesField("thread", threadID.toLong()))
            doc.add(StringField("id", "$id", Field.Store.NO))
            op.seqNo = w.addDocument(doc)
            ops1.add(op)
        }

        for (i in threads.indices) {
            val ops: MutableList<Operation>
            if (i == 0) {
                ops = threadOps[0]
            } else {
                ops = mutableListOf()
                threadOps.add(ops)
            }

            val threadID = i
            threads[i] =
                Thread {
                    try {
                        startingGun.await()
                        for (j in 0 until opCount) {
                            val op = Operation()
                            op.threadID = threadID
                            if (random().nextInt(500) == 17) {
                                op.what = 2
                                commitLock.withLock {
                                    op.seqNo = w.commit()
                                    if (op.seqNo != -1L) {
                                        commits.add(op)
                                    }
                                }
                            } else {
                                op.id = random().nextInt(idCount)
                                val idTerm = Term("id", "${op.id}")
                                op.seqNo = w.updateNumericDocValue(idTerm, "thread", threadID.toLong())
                                op.what = 0
                                ops.add(op)
                            }
                        }
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            threads[i]!!.setName("thread$i")
            threads[i]!!.start()
        }
        startingGun.countDown()
        for (thread in threads) {
            thread!!.join()
        }

        val commitOp = Operation()
        commitOp.seqNo = w.commit()
        if (commitOp.seqNo != -1L) {
            commits.add(commitOp)
        }

        val indexCommits = DirectoryReader.listCommits(dir)
        assertEquals(commits.size, indexCommits.size)

        val expectedThreadIDs = IntArray(idCount)
        val seqNos = LongArray(idCount)

        for (i in commits.indices) {
            val commitSeqNo = commits[i].seqNo
            Arrays.fill(expectedThreadIDs, -1)
            Arrays.fill(seqNos, 0)

            for (threadID in threadOps.indices) {
                var lastSeqNo = 0L
                for (op in threadOps[threadID]) {
                    if (op.what == 0.toByte() && op.seqNo <= commitSeqNo && op.seqNo > seqNos[op.id]) {
                        seqNos[op.id] = op.seqNo
                        expectedThreadIDs[op.id] = threadID
                    }

                    assertTrue(op.seqNo > lastSeqNo)
                    lastSeqNo = op.seqNo
                }
            }

            val r = DirectoryReader.open(indexCommits[i])
            val s = IndexSearcher(r)

            for (id in 0 until idCount) {
                val hits = s.search(TermQuery(Term("id", "$id")), 1)
                val docValues = MultiDocValues.getNumericValues(r, "thread")!!

                assert(expectedThreadIDs[id] != -1)
                assertEquals(1L, hits.totalHits.value)
                val hitDoc = hits.scoreDocs[0].doc
                assertEquals(hitDoc, docValues.advance(hitDoc))
                val actualThreadID = docValues.longValue().toInt()
                if (expectedThreadIDs[id] != actualThreadID) {
                    println(
                        "TEST: FAIL commit=$i (of ${commits.size}) id=$id expectedThreadID=${expectedThreadIDs[id]} vs actualThreadID=$actualThreadID commitSeqNo=$commitSeqNo numThreads=$numThreads reader=$r commit=${indexCommits[i]}"
                    )
                    for (threadID in threadOps.indices) {
                        for (op in threadOps[threadID]) {
                            if (id == op.id) {
                                println("  threadID=$threadID seqNo=${op.seqNo}")
                            }
                        }
                    }
                    assertEquals(
                        expectedThreadIDs[id],
                        actualThreadID,
                        "id=$id docID=${hits.scoreDocs[0].doc}",
                    )
                }
            }
            w.close()
            r.close()
        }

        dir.close()
    }

    @Test
    fun testDeleteAll() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val a = w.addDocument(Document())
        val b = w.deleteAll()
        assertTrue(a < b)
        val c = w.commit()
        assertTrue(b < c)
        w.close()
        dir.close()
    }
}
