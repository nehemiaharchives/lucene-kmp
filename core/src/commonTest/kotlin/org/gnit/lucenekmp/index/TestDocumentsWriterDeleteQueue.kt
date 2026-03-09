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

import org.gnit.lucenekmp.index.DocumentsWriterDeleteQueue.DeleteSlice
import org.gnit.lucenekmp.index.PrefixCodedTerms.TermIterator
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit test for [DocumentsWriterDeleteQueue] */
@OptIn(ExperimentalAtomicApi::class)
class TestDocumentsWriterDeleteQueue : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testUpdateDeleteSlices() {
        val queue = DocumentsWriterDeleteQueue(InfoStream.default)
        val size = 200 + random().nextInt(500) * RANDOM_MULTIPLIER
        val ids = Array(size) { random().nextInt() }
        val slice1 = queue.newSlice()
        val slice2 = queue.newSlice()
        val bd1 = BufferedUpdates("bd1")
        val bd2 = BufferedUpdates("bd2")
        var last1 = 0
        var last2 = 0
        val uniqueValues = HashSet<Term>()
        for (j in ids.indices) {
            val i = ids[j]
            val term = Term("id", i.toString())
            uniqueValues.add(term)
            queue.add(DocumentsWriterDeleteQueue.newNode(term))
            queue.tryApplyGlobalSlice()
            if (random().nextInt(20) == 0 || j == ids.size - 1) {
                queue.updateSlice(slice1)
                assertTrue(slice1.isTailItem(term))
                slice1.apply(bd1, j)
                assertAllBetween(last1, j, bd1, ids)
                last1 = j + 1
            }
            if (random().nextInt(10) == 5 || j == ids.size - 1) {
                queue.updateSlice(slice2)
                assertTrue(slice2.isTailItem(term))
                slice2.apply(bd2, j)
                assertAllBetween(last2, j, bd2, ids)
                last2 = j + 1
            }
            assertEquals(uniqueValues.size, queue.numGlobalTermDeletes())
        }
        assertEquals(uniqueValues, bd1.deleteTerms.keySet())
        assertEquals(uniqueValues, bd2.deleteTerms.keySet())
        val frozenSet = HashSet<Term>()
        val bytesRef = BytesRefBuilder()
        val iter: TermIterator = assertNotNull(queue.freezeGlobalBuffer(queue.newSlice())).deleteTerms.iterator()
        while (iter.next() != null) {
            bytesRef.copyBytes(iter.bytes)
            frozenSet.add(Term(iter.field()!!, bytesRef.toBytesRef()))
        }
        assertEquals(0, queue.numGlobalTermDeletes(), "num deletes must be 0 after freeze")
        assertEquals(uniqueValues, frozenSet)
    }

    private fun assertAllBetween(start: Int, end: Int, deletes: BufferedUpdates, ids: Array<Int>) {
        for (i in start..end) {
            assertEquals(end, deletes.deleteTerms.get(Term("id", ids[i].toString())))
        }
    }

    @Test
    fun testClear() {
        val queue = DocumentsWriterDeleteQueue(InfoStream.default)
        assertFalse(queue.anyChanges())
        queue.clear()
        assertFalse(queue.anyChanges())
        val size = 200 + random().nextInt(500) * RANDOM_MULTIPLIER
        for (i in 0..<size) {
            val term = Term("id", "$i")
            if (random().nextInt(10) == 0) {
                queue.addDelete(TermQuery(term))
            } else {
                queue.addDelete(term)
            }
            assertTrue(queue.anyChanges())
            if (random().nextInt(10) == 0) {
                queue.clear()
                queue.tryApplyGlobalSlice()
                assertFalse(queue.anyChanges())
            }
        }
    }

    @Test
    fun testAnyChanges() {
        val queue = DocumentsWriterDeleteQueue(InfoStream.default)
        val size = 200 + random().nextInt(500) * RANDOM_MULTIPLIER
        var termsSinceFreeze = 0
        var queriesSinceFreeze = 0
        for (i in 0..<size) {
            val term = Term("id", "$i")
            if (random().nextInt(10) == 0) {
                queue.addDelete(TermQuery(term))
                queriesSinceFreeze++
            } else {
                queue.addDelete(term)
                termsSinceFreeze++
            }
            assertTrue(queue.anyChanges())
            if (random().nextInt(5) == 0) {
                val freezeGlobalBuffer = assertNotNull(queue.freezeGlobalBuffer(queue.newSlice()))
                val deleteTermsSize = freezeGlobalBuffer.deleteTerms.size()
                assertEquals(termsSinceFreeze.toLong(), deleteTermsSize)
                assertEquals(queriesSinceFreeze, freezeGlobalBuffer.deleteQueries.size)
                queriesSinceFreeze = 0
                termsSinceFreeze = 0
                assertFalse(queue.anyChanges())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testPartiallyAppliedGlobalSlice() {
        val queue = DocumentsWriterDeleteQueue(InfoStream.default)
        val lock: ReentrantLock = queue.globalBufferLock
        lock.lock()
        val threadFactory = NamedThreadFactory("TestDocumentsWriterDeleteQueue")
        val t = threadFactory.newThread { queue.addDelete(Term("foo", "bar")) }
        runBlocking { t.join() }
        lock.unlock()
        assertTrue(queue.anyChanges(), "changes in del queue but not in slice yet")
        queue.tryApplyGlobalSlice()
        assertTrue(queue.anyChanges(), "changes in global buffer")
        val freezeGlobalBuffer = assertNotNull(queue.freezeGlobalBuffer(queue.newSlice()))
        assertTrue(freezeGlobalBuffer.any())
        assertEquals(1, freezeGlobalBuffer.deleteTerms.size())
        assertFalse(queue.anyChanges(), "all changes applied")
    }

    @Test
    @Throws(Exception::class)
    fun testStressDeleteQueue() {
        val queue = DocumentsWriterDeleteQueue(InfoStream.default)
        val uniqueValues = HashSet<Term>()
        val size = 10000 + random().nextInt(500) * RANDOM_MULTIPLIER
        val ids = Array(size) { random().nextInt() }
        for (id in ids) {
            uniqueValues.add(Term("id", id.toString()))
        }
        val latch = CountDownLatch(1)
        val index = AtomicInteger(0)
        val numThreads = 2 + random().nextInt(5)
        val threadFactory = NamedThreadFactory("TestDocumentsWriterDeleteQueue")
        val threads = Array(numThreads) { UpdateThread(queue, index, ids, latch, threadFactory) }
        for (thread in threads) {
            thread.start()
        }
        latch.countDown()
        for (thread in threads) {
            thread.join()
        }

        for (updateThread in threads) {
            val slice = updateThread.slice
            queue.updateSlice(slice)
            val deletes = updateThread.deletes
            slice.apply(deletes, BufferedUpdates.MAX_INT)
            assertEquals(uniqueValues, deletes.deleteTerms.keySet())
        }
        queue.tryApplyGlobalSlice()
        val frozenSet = HashSet<Term>()
        val builder = BytesRefBuilder()

        val iter = assertNotNull(queue.freezeGlobalBuffer(queue.newSlice())).deleteTerms.iterator()
        while (iter.next() != null) {
            builder.copyBytes(iter.bytes)
            frozenSet.add(Term(iter.field()!!, builder.toBytesRef()))
        }

        assertEquals(0, queue.numGlobalTermDeletes(), "num deletes must be 0 after freeze")
        assertEquals(uniqueValues.size, frozenSet.size)
        assertEquals(uniqueValues, frozenSet)
    }

    @Test
    fun testClose() {
        run {
            val queue = DocumentsWriterDeleteQueue(InfoStream.default)
            assertTrue(queue.isOpen)
            queue.close()
            if (random().nextBoolean()) {
                queue.close()
            }
            expectThrows(AlreadyClosedException::class) { queue.addDelete(Term("foo", "bar")) }
            expectThrows(AlreadyClosedException::class) { queue.freezeGlobalBuffer(queue.newSlice()) }
            expectThrows(AlreadyClosedException::class) { queue.addDelete(MatchNoDocsQuery()) }
            expectThrows(AlreadyClosedException::class) {
                queue.addDocValuesUpdates(
                    DocValuesUpdate.NumericDocValuesUpdate(Term("foo", "bar"), "foo", 1),
                )
            }
            expectThrows(AlreadyClosedException::class) { queue.add(null) }
            assertNull(queue.maybeFreezeGlobalBuffer())
            assertFalse(queue.isOpen)
        }
        run {
            val queue = DocumentsWriterDeleteQueue(InfoStream.default)
            queue.addDelete(Term("foo", "bar"))
            expectThrows(IllegalStateException::class) { queue.close() }
            assertTrue(queue.isOpen)
            queue.tryApplyGlobalSlice()
            queue.freezeGlobalBuffer(queue.newSlice())
            queue.close()
            assertFalse(queue.isOpen)
        }
    }

    private class UpdateThread(
        val queue: DocumentsWriterDeleteQueue,
        val index: AtomicInteger,
        val ids: Array<Int>,
        val latch: CountDownLatch,
        val threadFactory: NamedThreadFactory,
    ) {
        val slice: DeleteSlice = queue.newSlice()
        val deletes: BufferedUpdates = BufferedUpdates("deletes")
        private var job: Job? = null

        fun start() {
            job =
                threadFactory.newThread {
                    latch.await()
                    var i: Int
                    while (index.fetchAndIncrement().also { i = it } < ids.size) {
                        val term = Term("id", ids[i].toString())
                        val termNode = DocumentsWriterDeleteQueue.newNode(term)
                        queue.add(termNode, slice)
                        assertTrue(slice.isTail(termNode))
                        slice.apply(deletes, BufferedUpdates.MAX_INT)
                    }
                }
        }

        fun join() {
            runBlocking {
                job!!.join()
            }
        }
    }
}
