package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.junitport.assertEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestStressDeletes : LuceneTestCase() {

    /**
     * Make sure that order of adds/deletes across threads is respected as long as each ID is only
     * changed by one thread at a time.
     */
    @Test
    @Throws(Exception::class)
    fun test() {
        val numIDs = atLeast(100)
        val locks = Array(numIDs) { ReentrantLock() }

        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val w: IndexWriter = IndexWriter(dir, iwc)
        val iters = atLeast(2000)
        val exists: MutableMap<Int, Boolean> = mutableMapOf()
        val existsLock = ReentrantLock()
        val threads: Array<Thread> = kotlin.arrayOfNulls<Thread>(TestUtil.nextInt(random(), 2, 6)) as Array<Thread>
        val startingGun = CountDownLatch(1)
        val deleteMode = random().nextInt(3)
        for (i in threads.indices) {
            threads[i] =
                object : Thread() {
                    override fun run() {
                        try {
                            startingGun.await()
                            for (iter in 0..<iters) {
                                val id = random().nextInt(numIDs)
                                locks[id].withLock {
                                    val v = existsLock.withLock { exists.get(id) }
                                    if (v == null || v == false) {
                                        val doc = Document()
                                        doc.add(newStringField("id", "" + id, Field.Store.NO))
                                        w.addDocument(doc)
                                        existsLock.withLock { exists.put(id, true) }
                                    } else {
                                        if (deleteMode == 0) {
                                            // Always delete by term
                                            w.deleteDocuments(Term("id", "" + id))
                                        } else if (deleteMode == 1) {
                                            // Always delete by query
                                            w.deleteDocuments(TermQuery(Term("id", "" + id)))
                                        } else {
                                            // Mixed
                                            if (random().nextBoolean()) {
                                                w.deleteDocuments(Term("id", "" + id))
                                            } else {
                                                w.deleteDocuments(TermQuery(Term("id", "" + id)))
                                            }
                                        }
                                        existsLock.withLock { exists.put(id, false) }
                                    }
                                }
                                if (random().nextInt(500) === 2) {
                                    DirectoryReader.open(w, random().nextBoolean(), false).close()
                                }
                                if (random().nextInt(500) === 2) {
                                    w.commit()
                                }
                            }
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    }
                }
            threads[i].start()
        }

        startingGun.countDown()
        for (thread in threads) {
            thread.join()
        }

        val r: IndexReader = DirectoryReader.open(w)
        val s = newSearcher(r)
        for (ent in exists.entries) {
            val id: Int = ent.key!!
            val hits = s.search(TermQuery(Term("id", "" + id)), 1)
            if (ent.value) {
                assertEquals(1, hits.totalHits.value)
            } else {
                assertEquals(0, hits.totalHits.value)
            }
        }
        r.close()
        w.close()
        dir.close()
    }
}
