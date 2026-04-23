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

import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestSameScoresWithThreads : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun test() {
        val dir: Directory = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val w = RandomIndexWriter(random(), dir, analyzer)
        val docs = LineFileDocs(random())
        val charsToIndex = atLeast(100000)
        var charsIndexed = 0
        // System.out.println("bytesToIndex=" + charsToIndex);
        while (charsIndexed < charsToIndex) {
            val doc = docs.nextDoc()
            charsIndexed += doc.get("body")!!.length
            w.addDocument(doc)
            // System.out.println("  bytes=" + charsIndexed + " add: " + doc);
        }
        val r = w.reader
        // System.out.println("numDocs=" + r.numDocs());
        w.close()

        val s = newSearcher(r)
        val terms = MultiTerms.getTerms(r, "body")!!
        var termCount = 0
        var termsEnum = terms.iterator()
        while (termsEnum.next() != null) {
            termCount++
        }
        assertTrue(termCount > 0)

        // Target ~10 terms to search:
        val chance = 10.0 / termCount
        termsEnum = terms.iterator()
        val answers: MutableMap<BytesRef, TopDocs> = HashMap()
        while (termsEnum.next() != null) {
            if (random().nextDouble() <= chance) {
                val term = BytesRef.deepCopyOf(termsEnum.term()!!)
                answers[term] = s.search(TermQuery(Term("body", term)), 100)
            }
        }

        if (answers.isNotEmpty()) {
            val startingGun = CountDownLatch(1)
            val threadFailure = AtomicReference<Throwable?>(null)
            val numThreads = if (TEST_NIGHTLY) TestUtil.nextInt(random(), 2, 5) else 2
            val threads = arrayOfNulls<Thread>(numThreads)
            for (threadID in 0..<numThreads) {
                val thread =
                    object : Thread() {
                        override fun run() {
                            try {
                                startingGun.await()
                                for (i in 0..<20) {
                                    val shuffled: MutableList<MutableMap.MutableEntry<BytesRef, TopDocs>> =
                                        ArrayList(answers.entries)
                                    shuffled.shuffle(random())
                                    for (ent in shuffled) {
                                        val actual = s.search(TermQuery(Term("body", ent.key)), 100)
                                        val expected = ent.value
                                        assertEquals(expected.totalHits.value, actual.totalHits.value)
                                        assertEquals(
                                            expected.scoreDocs.size,
                                            actual.scoreDocs.size,
                                            "query=${ent.key.utf8ToString()}",
                                        )
                                        for (hit in expected.scoreDocs.indices) {
                                            assertEquals(expected.scoreDocs[hit].doc, actual.scoreDocs[hit].doc)
                                            // Floats really should be identical:
                                            assertTrue(expected.scoreDocs[hit].score == actual.scoreDocs[hit].score)
                                        }
                                    }
                                }
                            } catch (e: Throwable) {
                                threadFailure.compareAndSet(null, e)
                                throw e
                            }
                        }
                    }
                threads[threadID] = thread
                thread.start()
            }
            startingGun.countDown()
            for (thread in threads) {
                thread!!.join()
            }
            threadFailure.load()?.let { throw it }
        }
        docs.close()
        r.close()
        dir.close()
    }
}
