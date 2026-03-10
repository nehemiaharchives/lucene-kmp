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

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.AssertingLeafReader
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** */
@OptIn(ExperimentalAtomicApi::class)
class TestIndexReaderClose : LuceneTestCase() {

    @Test
    fun testCloseUnderException() {
        val dir = newDirectory()
        val writer =
            IndexWriter(dir, newIndexWriterConfig(random(), MockAnalyzer(random())))
        writer.addDocument(Document())
        writer.commit()
        writer.close()
        val iters = 1000 + 1 + random().nextInt(20)
        for (j in 0..<iters) {
            val open = DirectoryReader.open(dir)
            val throwOnClose = !rarely()
            val leaf = getOnlyLeafReader(open)
            val reader =
                object : FilterLeafReader(leaf) {
                    override val coreCacheHelper: CacheHelper?
                        get() = `in`.coreCacheHelper

                    override val readerCacheHelper: CacheHelper?
                        get() = `in`.readerCacheHelper

                    override fun doClose() {
                        try {
                            super.doClose()
                        } finally {
                            if (throwOnClose) {
                                throw IllegalStateException("BOOM!")
                            }
                        }
                    }
                }
            val listenerCount = random().nextInt(20)
            val count = AtomicInteger(0)
            var faultySet = false
            for (i in 0..<listenerCount) {
                if (rarely()) {
                    faultySet = true
                    runBlocking {
                        reader.readerCacheHelper!!.addClosedListener(FaultyListener())
                    }
                } else {
                    count.incrementAndFetch()
                    runBlocking {
                        reader.readerCacheHelper!!.addClosedListener(
                            CountListener(count, reader.readerCacheHelper!!.key)
                        )
                    }
                }
            }
            if (!faultySet && !throwOnClose) {
                runBlocking {
                    reader.readerCacheHelper!!.addClosedListener(FaultyListener())
                }
            }

            val expected = expectThrows(IllegalStateException::class) { reader.close() }

            if (throwOnClose) {
                assertEquals("BOOM!", expected.message)
            } else {
                assertEquals("GRRRRRRRRRRRR!", expected.message)
            }

            expectThrows(AlreadyClosedException::class) { reader.terms("someField") }

            if (random().nextBoolean()) {
                reader.close() // call it again
            }
            assertEquals(0, count.load())
        }
        dir.close()
    }

    @Test
    fun testCoreListenerOnWrapperWithDifferentCacheKey() {
        val w = RandomIndexWriter(random(), newDirectory())
        val numDocs = TestUtil.nextInt(random(), 1, 5)
        for (i in 0..<numDocs) {
            w.addDocument(Document())
            if (random().nextBoolean()) {
                w.commit()
            }
        }
        w.forceMerge(1)
        w.commit()
        w.close()

        val reader: IndexReader = DirectoryReader.open(w.w.getDirectory())
        val leafReader: LeafReader = AssertingLeafReader(getOnlyLeafReader(reader))

        val numListeners = TestUtil.nextInt(random(), 1, 10)
        val listeners: MutableList<IndexReader.ClosedListener> = mutableListOf()
        val counter = AtomicInteger(numListeners)

        for (i in 0..<numListeners) {
            val listener = CountListener(counter, leafReader.coreCacheHelper!!.key)
            listeners.add(listener)
            runBlocking {
                leafReader.coreCacheHelper!!.addClosedListener(listener)
            }
        }
        for (i in 0..<100) {
            runBlocking {
                leafReader.coreCacheHelper!!.addClosedListener(
                    listeners[random().nextInt(listeners.size)]
                )
            }
        }
        assertEquals(numListeners, counter.load())
        // make sure listeners are registered on the wrapped reader and that closing any of them has the
        // same effect
        if (random().nextBoolean()) {
            reader.close()
        } else {
            leafReader.close()
        }
        assertEquals(0, counter.load())
        w.w.getDirectory().close()
    }

    private class CountListener(
        private val count: AtomicInteger,
        private val coreCacheKey: Any
    ) : IndexReader.ClosedListener {

        override fun onClose(coreCacheKey: IndexReader.CacheKey) {
            assertSame(this.coreCacheKey, coreCacheKey)
            count.decrementAndFetch()
        }
    }

    private class FaultyListener : IndexReader.ClosedListener {

        override fun onClose(cacheKey: IndexReader.CacheKey) {
            throw IllegalStateException("GRRRRRRRRRRRR!")
        }
    }

    @Test
    fun testRegisterListenerOnClosedReader() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        val r = DirectoryReader.open(w)
        w.close()

        // The reader is open, everything should work
        runBlocking {
            r.readerCacheHelper!!.addClosedListener { }
            r.leaves()[0].reader().readerCacheHelper!!.addClosedListener { }
            r.leaves()[0].reader().coreCacheHelper!!.addClosedListener { }
        }

        // But now we close
        r.close()
        expectThrows(AlreadyClosedException::class) {
            runBlocking { r.readerCacheHelper!!.addClosedListener { } }
        }
        expectThrows(AlreadyClosedException::class) {
            runBlocking { r.leaves()[0].reader().readerCacheHelper!!.addClosedListener { } }
        }
        expectThrows(AlreadyClosedException::class) {
            runBlocking { r.leaves()[0].reader().coreCacheHelper!!.addClosedListener { } }
        }

        dir.close()
    }
}
