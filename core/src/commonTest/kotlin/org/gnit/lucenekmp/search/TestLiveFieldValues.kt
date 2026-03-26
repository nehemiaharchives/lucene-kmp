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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.store.Directory
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLiveFieldValues : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    @OptIn(ExperimentalAtomicApi::class)
    fun test() {
        val dir: Directory = newFSDirectory(createTempDir("livefieldupdates"))
        val iwc: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(random()))

        val w = IndexWriter(dir, iwc)

        val mgr = SearcherManager(
            w,
            object : SearcherFactory() {
                override fun newSearcher(reader: IndexReader, previousReader: IndexReader?): IndexSearcher {
                    return IndexSearcher(reader)
                }
            }
        )

        val missing = -1

        val rt = object : LiveFieldValues<IndexSearcher, Int>(mgr, missing) {
            override fun lookupFromSearcher(s: IndexSearcher, id: String): Int? {
                val tq = TermQuery(Term("id", id))
                val hits = s.search(tq, 1)
                assertTrue(hits.totalHits.value <= 1L)
                return if (hits.totalHits.value == 0L) {
                    null
                } else {
                    val doc = s.storedFields().document(hits.scoreDocs[0].doc)
                    doc.getField("field")!!.numericValue()!!.toInt()
                }
            }
        }

        val numThreads = TestUtil.nextInt(random(), 2, 5)
        if (VERBOSE) {
            println("$numThreads threads")
        }

        val startingGun = CountDownLatch(1)
        val threads = mutableListOf<Thread>()
        val threadFailure = AtomicReference<Throwable?>(null)

        val iters = atLeast(1000)
        val idCount = TestUtil.nextInt(random(), 100, 10000)

        val reopenChance = random().nextDouble() * 0.01
        val deleteChance = random().nextDouble() * 0.25
        val addChance = random().nextDouble() * 0.5

        for (t in 0..<numThreads) {
            val threadID = t
            val threadRandom = Random(random().nextLong())
            val thread = Thread {
                try {
                    val values = hashMapOf<String, Int>()
                    val allIDs = mutableListOf<String>()

                    startingGun.await()
                    for (iter in 0..<iters) {
                        // Add/update a document
                        val doc = Document()
                        // Threads must not update the same id at the
                        // same time:
                        if (threadRandom.nextDouble() <= addChance) {
                            val id =
                                "${threadID}_${threadRandom.nextInt(idCount).toString(16).padStart(4, '0')}"
                            val field = threadRandom.nextInt(Int.MAX_VALUE)
                            doc.add(newStringField("id", id, Field.Store.YES))
                            doc.add(StoredField("field", field))
                            w.updateDocument(Term("id", id), doc)
                            rt.add(id, field)
                            if (values.put(id, field) == null) {
                                allIDs.add(id)
                            }
                        }

                        if (allIDs.isNotEmpty() && threadRandom.nextDouble() <= deleteChance) {
                            val randomID = allIDs[threadRandom.nextInt(allIDs.size)]
                            w.deleteDocuments(Term("id", randomID))
                            rt.delete(randomID)
                            values[randomID] = missing
                        }

                        if (threadRandom.nextDouble() <= reopenChance || rt.size() > 10000) {
                            // System.out.println("refresh @ " + rt.size());
                            mgr.maybeRefresh()
                            if (VERBOSE) {
                                val s = mgr.acquire()
                                try {
                                    println("TEST: reopen $s")
                                } finally {
                                    mgr.release(s)
                                }
                                println("TEST: ${values.size} values")
                            }
                        }

                        if (threadRandom.nextInt(10) == 7) {
                            assertEquals(null, rt["foo"])
                        }

                        if (allIDs.isNotEmpty()) {
                            val randomID = allIDs[threadRandom.nextInt(allIDs.size)]
                            var expected = values[randomID]
                            if (expected == missing) {
                                expected = null
                            }
                            assertEquals(expected, rt[randomID], "id=$randomID")
                        }
                    }
                } catch (t: Throwable) {
                    if (threadFailure.load() == null) {
                        threadFailure.store(t)
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }

        startingGun.countDown()

        for (thread in threads) {
            thread.join()
        }
        threadFailure.load()?.let { throw RuntimeException(it) }
        mgr.maybeRefresh()
        assertEquals(0, rt.size())

        rt.close()
        mgr.close()
        w.close()
        dir.close()
    }
}
