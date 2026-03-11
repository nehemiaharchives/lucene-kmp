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
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import okio.IOException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalAtomicApi::class)
class TestIndexTooManyDocs : LuceneTestCase() {

    /*
     * This test produces a boat load of very small segments with lot of deletes which are likely deleting
     * the entire segment. see https://issues.apache.org/jira/browse/LUCENE-8043
     */
    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testIndexTooManyDocs() {
        val dir: Directory = newDirectory()
        val numMaxDoc = 25
        val config = IndexWriterConfig()
        config.setRAMBufferSizeMB(
            0.000001
        ) // force lots of small segments and logs of concurrent deletes
        val writer = IndexWriter(dir, config)
        try {
            IndexWriter.setMaxDocs(numMaxDoc)
            val numThreads = 5 + random().nextInt(5)
            val threads = arrayOfNulls<PlatformTestThread>(numThreads)
            val latch = CountDownLatch(numThreads)
            val indexingDone = CountDownLatch(numThreads - 2)
            val done = AtomicBoolean(false)
            for (i in 0..<numThreads) {
                if (i >= 2) {
                    threads[i] =
                        PlatformTestThread {
                            latch.countDown()
                            try {
                                try {
                                    latch.await()
                                } catch (e: InterruptedException) {
                                    throw AssertionError(e)
                                }
                                for (d in 0..<100) {
                                    val doc = Document()
                                    val id = random().nextInt(numMaxDoc * 2).toString()
                                    doc.add(StringField("id", id, Field.Store.NO))
                                    try {
                                        val t = Term("id", id)
                                        if (random().nextInt(5) == 0) {
                                            writer.deleteDocuments(TermQuery(t))
                                        }
                                        writer.updateDocument(t, doc)
                                    } catch (e: IOException) {
                                        throw AssertionError(e)
                                    } catch (e: IllegalArgumentException) {
                                        assertEquals(
                                            "number of documents in the index cannot exceed ${IndexWriter.actualMaxDocs}",
                                            e.message
                                        )
                                    }
                                }
                            } finally {
                                indexingDone.countDown()
                            }
                        }
                } else {
                    threads[i] =
                        PlatformTestThread {
                            try {
                                latch.countDown()
                                latch.await()
                                var open = DirectoryReader.open(writer, true, true)
                                while (done.load() == false) {
                                    val directoryReader = DirectoryReader.openIfChanged(open)
                                    if (directoryReader != null) {
                                        open.close()
                                        open = directoryReader
                                    }
                                }
                                IOUtils.closeWhileHandlingException(open)
                            } catch (e: Exception) {
                                throw AssertionError(e)
                            }
                        }
                }
                threads[i]!!.start()
            }

            indexingDone.await()
            done.store(true)

            for (i in 0..<numThreads) {
                threads[i]!!.join()
            }
            writer.close()
            dir.close()
        } finally {
            IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS)
        }
    }
}
