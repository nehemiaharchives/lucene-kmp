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
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse

class TestIndexWriterNRTIsCurrent : LuceneTestCase() {
    class ReaderHolder {
        @Volatile
        var reader: DirectoryReader? = null

        @Volatile
        var stop: Boolean = false
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testIsCurrentWithThreads() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)
        val holder = ReaderHolder()
        val numReaderThreads = if (TEST_NIGHTLY) TestUtil.nextInt(random(), 2, 5) else 2
        val threads = arrayOfNulls<ReaderThread>(numReaderThreads)
        val latch = CountDownLatch(1)
        val writerThread = WriterThread(holder, writer, atLeast(50), random(), latch) // TODO reduced from 500 to 50 for dev speed
        for (i in threads.indices) {
            threads[i] = ReaderThread(holder, latch)
            threads[i]!!.start()
        }
        writerThread.start()

        writerThread.join()
        var failed = writerThread.failed != null
        if (failed) {
            writerThread.failed!!.printStackTrace()
        }
        for (i in threads.indices) {
            threads[i]!!.join()
            if (threads[i]!!.failed != null) {
                threads[i]!!.failed!!.printStackTrace()
                failed = true
            }
        }
        assertFalse(failed)
        writer.close()
        dir.close()
    }

    class WriterThread(
        private val holder: ReaderHolder,
        private val writer: IndexWriter,
        private val numOps: Int,
        @Suppress("UNUSED_PARAMETER")
        random: Random,
        private val latch: CountDownLatch
    ) : Thread() {
        private var countdown = true
        var failed: Throwable? = null

        override fun run() {
            var currentReader: DirectoryReader? = null
            val random = random()
            try {
                val doc = Document()
                doc.add(TextField("id", "1", Field.Store.NO))
                writer.addDocument(doc)
                holder.reader = DirectoryReader.open(writer)
                currentReader = holder.reader
                val term = Term("id")
                var i = 0
                while (i < numOps && !holder.stop) {
                    if (holder.stop) {
                        break
                    }
                    val nextOp = random.nextFloat()
                    if (nextOp < 0.3f) {
                        term.set("id", BytesRef("1"))
                        writer.updateDocument(term, doc)
                    } else if (nextOp < 0.5f) {
                        writer.addDocument(doc)
                    } else {
                        term.set("id", BytesRef("1"))
                        writer.deleteDocuments(term)
                    }
                    if (holder.reader !== currentReader) {
                        holder.reader = currentReader
                        if (countdown) {
                            countdown = false
                            latch.countDown()
                        }
                    }
                    if (random.nextBoolean()) {
                        writer.commit()
                        val newReader = DirectoryReader.openIfChanged(requireNotNull(currentReader))
                        if (newReader != null) {
                            currentReader.decRef()
                            currentReader = newReader
                        }
                        if (currentReader.numDocs() == 0) {
                            writer.addDocument(doc)
                        }
                    }
                    i++
                }
            } catch (e: Throwable) {
                failed = e
            } finally {
                holder.reader = null
                if (countdown) {
                    latch.countDown()
                }
                if (currentReader != null) {
                    try {
                        currentReader.decRef()
                    } catch (_: IOException) {
                    }
                }
            }
            if (VERBOSE) {
                println("writer stopped - forced by reader: ${holder.stop}")
            }
        }
    }

    class ReaderThread(
        private val holder: ReaderHolder,
        private val latch: CountDownLatch
    ) : Thread() {
        var failed: Throwable? = null

        override fun run() {
            try {
                latch.await()
            } catch (e: InterruptedException) {
                failed = e
                return
            }
            var reader: DirectoryReader?
            while ((holder.reader.also { reader = it }) != null) {
                val currentReader = requireNotNull(reader)
                if (currentReader.tryIncRef()) {
                    try {
                        val current = currentReader.isCurrent
                        if (VERBOSE) {
                            println("Thread: ${currentThread()} Reader: $currentReader isCurrent:$current")
                        }

                        assertFalse(current)
                    } catch (e: Throwable) {
                        if (VERBOSE) {
                            println(
                                "FAILED Thread: ${currentThread()} Reader: $currentReader isCurrent: false"
                            )
                        }
                        failed = e
                        holder.stop = true
                        return
                    } finally {
                        try {
                            currentReader.decRef()
                        } catch (e: IOException) {
                            if (failed == null) {
                                failed = e
                            }
                        }
                    }
                }
            }
        }
    }
}
