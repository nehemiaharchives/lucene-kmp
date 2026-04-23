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
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.DocHelper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.test.Test

@OptIn(ExperimentalAtomicApi::class)
class TestNRTReaderWithThreads : LuceneTestCase() {
    var seq: AtomicInteger = AtomicInteger(1)

    @Test
    @Throws(Exception::class)
    fun testIndexing() {
        val mainDir = newDirectory()
        if (mainDir is MockDirectoryWrapper) {
            mainDir.assertNoDeleteOpenFile = true
        }
        val writer =
            IndexWriter(
                mainDir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(10)
                    .setMergePolicy(newLogMergePolicy(false, 2))
            )
        val reader = DirectoryReader.open(writer) // start pooling readers
        reader.close()
        val numThreads = if (TEST_NIGHTLY) 4 else 2
        val numIterations = if (TEST_NIGHTLY) 2000 else 50
        val indexThreads = arrayOfNulls<RunThread>(numThreads)
        for (x in indexThreads.indices) {
            indexThreads[x] = RunThread(x % 2, writer, numIterations)
            indexThreads[x]!!.setName("Thread $x")
            indexThreads[x]!!.start()
        }

        for (thread in indexThreads) {
            thread!!.join()
        }

        writer.close()
        mainDir.close()

        for (thread in indexThreads) {
            if (thread!!.failure != null) {
                throw RuntimeException("hit exception from $thread", thread.failure)
            }
        }
    }

    inner class RunThread(
        var type: Int,
        var writer: IndexWriter,
        var numIterations: Int
    ) : Thread() {
        @Volatile
        var failure: Throwable? = null
        var delCount: Int = 0
        var addCount: Int = 0
        val r: Random = Random(random().nextLong())

        override fun run() {
            try {
                for (iter in 0 until numIterations) {
                    // int n = random.nextInt(2);
                    if (type == 0) {
                        val i = seq.addAndFetch(1)
                        val doc: Document = DocHelper.createDocument(i, "index1", 10)
                        writer.addDocument(doc)
                        addCount++
                    } else if (type == 1) {
                        // we may or may not delete because the term may not exist,
                        // however we're opening and closing the reader rapidly
                        val reader: IndexReader = DirectoryReader.open(writer)
                        val id = r.nextInt(seq.load())
                        val term = Term("id", id.toString())
                        val count = TestIndexWriterReader.count(term, reader)
                        writer.deleteDocuments(term)
                        reader.close()
                        delCount += count
                    }
                }
            } catch (ex: Throwable) {
                ex.printStackTrace()
                this.failure = ex
                throw RuntimeException(ex)
            }
        }
    }
}
