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
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.DummyTotalHitCountCollector
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestSearchWithThreads : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun test() {
        val numThreads = if (TEST_NIGHTLY) 5 else 2
        val numSearches = if (TEST_NIGHTLY) atLeast(2000) else atLeast(500)
        val numDocs = if (TEST_NIGHTLY) atLeast(10000) else atLeast(200)

        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val doc = Document()
        val body = newTextField("body", "", Field.Store.NO)
        doc.add(body)
        val sb = StringBuilder()
        for (docCount in 0..<numDocs) {
            val numTerms = random().nextInt(10)
            for (termCount in 0..<numTerms) {
                sb.append(if (random().nextBoolean()) "aaa" else "bbb")
                sb.append(' ')
            }
            body.setStringValue(sb.toString())
            w.addDocument(doc)
            sb.setLength(0)
        }
        val r: IndexReader = w.reader
        w.close()

        val s = newSearcher(r)

        val failed = AtomicBoolean(false)
        val netSearch = AtomicLong(0L)
        val collectorManager: CollectorManager<*, Int> = DummyTotalHitCountCollector.createManager()
        val threads = arrayOfNulls<Thread>(numThreads)
        for (threadID in 0..<numThreads) {
            threads[threadID] =
                object : Thread() {
                    override fun run() {
                        try {
                            var totHits = 0L
                            var totSearch = 0L
                            while (totSearch < numSearches && !failed.load()) {
                                totHits += s.search(TermQuery(Term("body", "aaa")), collectorManager).toLong()
                                totHits += s.search(TermQuery(Term("body", "bbb")), collectorManager).toLong()
                                totSearch++
                            }
                            assertTrue(totSearch > 0L && totHits > 0L)
                            netSearch.addAndFetch(totSearch)
                        } catch (exc: Exception) {
                            failed.store(true)
                            throw RuntimeException(exc)
                        }
                    }
                }
            threads[threadID]!!.setDaemon(true)
        }

        for (t in threads) {
            t!!.start()
        }

        for (t in threads) {
            t!!.join()
        }

        if (VERBOSE) {
            println("$numThreads threads did ${netSearch.load()} searches")
        }

        r.close()
        dir.close()
    }
}
