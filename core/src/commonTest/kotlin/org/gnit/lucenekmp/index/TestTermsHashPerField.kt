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
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.RandomStrings
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.IntBlockPool
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestTermsHashPerField : LuceneTestCase() {

    private fun createNewHash(newCalled: AtomicInteger, addCalled: AtomicInteger): TermsHashPerField {
        val intBlockPool = IntBlockPool()
        val byteBlockPool = ByteBlockPool(ByteBlockPool.DirectAllocator())
        val termBlockPool = ByteBlockPool(ByteBlockPool.DirectAllocator())

        val hash =
            object : TermsHashPerField(
                1,
                intBlockPool,
                byteBlockPool,
                termBlockPool,
                Counter.newCounter(),
                null,
                "testfield",
                IndexOptions.DOCS_AND_FREQS
            ) {

                private var freqProxPostingsArray: FreqProxTermsWriterPerField.FreqProxPostingsArray? = null

                override fun newTerm(termID: Int, docID: Int) {
                    newCalled.incrementAndFetch()
                    val postings = freqProxPostingsArray!!
                    postings.lastDocIDs[termID] = docID
                    postings.lastDocCodes[termID] = docID shl 1
                    postings.termFreqs!![termID] = 1
                }

                override fun addTerm(termID: Int, docID: Int) {
                    addCalled.incrementAndFetch()
                    val postings = freqProxPostingsArray!!
                    if (docID != postings.lastDocIDs[termID]) {
                        if (1 == postings.termFreqs!![termID]) {
                            writeVInt(0, postings.lastDocCodes[termID] or 1)
                        } else {
                            writeVInt(0, postings.lastDocCodes[termID])
                            writeVInt(0, postings.termFreqs!![termID])
                        }
                        postings.termFreqs!![termID] = 1
                        postings.lastDocCodes[termID] = (docID - postings.lastDocIDs[termID]) shl 1
                        postings.lastDocIDs[termID] = docID
                    } else {
                        postings.termFreqs!![termID] = Math.addExact(postings.termFreqs!![termID], 1)
                    }
                }

                override fun newPostingsArray() {
                    freqProxPostingsArray = postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray?
                }

                override fun createPostingsArray(size: Int): ParallelPostingsArray {
                    return FreqProxTermsWriterPerField.FreqProxPostingsArray(size, true, false, false)
                }
            }
        return hash
    }

    private fun assertDocAndFreq(
        reader: ByteSliceReader,
        postingsArray: FreqProxTermsWriterPerField.FreqProxPostingsArray,
        prevDoc: Int,
        termId: Int,
        doc: Int,
        frequency: Int
    ): Boolean {
        var docId = prevDoc
        val freq: Int
        val eof = reader.eof()
        if (eof) {
            docId = postingsArray.lastDocIDs[termId]
            freq = postingsArray.termFreqs!![termId]
        } else {
            val code = reader.readVInt()
            docId += code ushr 1
            freq =
                if ((code and 1) != 0) {
                    1
                } else {
                    reader.readVInt()
                }
        }
        assertEquals(doc, docId, "docID mismatch eof: $eof")
        assertEquals(frequency, freq, "freq mismatch eof: $eof")
        return eof
    }

    @Test
    @Throws(IOException::class)
    fun testAddAndUpdateTerm() {
        val newCalled = AtomicInteger(0)
        val addCalled = AtomicInteger(0)
        val hash = createNewHash(newCalled, addCalled)
        hash.start(null, true)

        hash.add(newBytesRef("start"), 0)
        hash.add(newBytesRef("foo"), 0)
        hash.add(newBytesRef("bar"), 0)
        hash.finish()
        hash.add(newBytesRef("bar"), 1)
        hash.add(newBytesRef("foobar"), 1)
        hash.add(newBytesRef("bar"), 1)
        hash.add(newBytesRef("bar"), 1)
        hash.add(newBytesRef("foobar"), 1)
        hash.add(newBytesRef("verylongfoobarbaz"), 1)
        hash.finish()
        hash.add(newBytesRef("verylongfoobarbaz"), 2)
        hash.add(newBytesRef("boom"), 2)
        hash.finish()
        hash.add(newBytesRef("verylongfoobarbaz"), 3)
        hash.add(newBytesRef("end"), 3)
        hash.finish()

        assertEquals(7, newCalled.load())
        assertEquals(6, addCalled.load())
        val reader = ByteSliceReader()
        hash.initReader(reader, 0, 0)
        assertTrue(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 0, 0, 0, 1))
        hash.initReader(reader, 1, 0)
        assertTrue(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 0, 1, 0, 1))
        hash.initReader(reader, 2, 0)
        assertFalse(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 0, 2, 0, 1))
        assertTrue(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 2, 2, 1, 3))
        hash.initReader(reader, 3, 0)
        assertTrue(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 0, 3, 1, 2))
        hash.initReader(reader, 4, 0)
        assertFalse(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 0, 4, 1, 1))
        assertFalse(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 1, 4, 2, 1))
        assertTrue(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 2, 4, 3, 1))
        hash.initReader(reader, 5, 0)
        assertTrue(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 0, 5, 2, 1))
        hash.initReader(reader, 6, 0)
        assertTrue(assertDocAndFreq(reader, hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray, 0, 6, 3, 1))
    }

    @Test
    @Throws(IOException::class)
    fun testAddAndUpdateRandom() {
        val newCalled = AtomicInteger(0)
        val addCalled = AtomicInteger(0)
        val hash = createNewHash(newCalled, addCalled)
        hash.start(null, true)

        class Posting {
            var termId = -1
            val docAndFreq = TreeMap<Int, Int>()
        }

        val postingMap = HashMap<BytesRef, Posting>()
        val numStrings = 1 + random().nextInt(200)
        for (i in 0..<numStrings) {
            val randomString =
                RandomStrings.randomRealisticUnicodeOfCodepointLengthBetween(random(), 1, 10)
            postingMap.getOrPut(newBytesRef(randomString)) { Posting() }
        }
        val bytesRefs = ArrayList(postingMap.keys)
        bytesRefs.sort()
        val numDocs = 1 + random().nextInt(200)
        var termOrd = 0
        for (doc in 0..<numDocs) {
            val numTerms = 1 + random().nextInt(200)
            for (j in 0..<numTerms) {
                val ref = RandomPicks.randomFrom(random(), bytesRefs)
                val posting = postingMap[ref]!!
                if (posting.termId == -1) {
                    posting.termId = termOrd++
                }
                posting.docAndFreq[doc] = (posting.docAndFreq[doc] ?: 0) + 1
                hash.add(ref, doc)
            }
            hash.finish()
        }
        val values = postingMap.values.filter { it.termId != -1 }.toMutableList()
        values.shuffle(random())
        val reader = ByteSliceReader()
        for (p in values) {
            hash.initReader(reader, p.termId, 0)
            var eof = false
            var prefDoc = 0
            for (entry in p.docAndFreq.entries) {
                assertFalse(eof, "the reader must not be EOF here")
                eof = assertDocAndFreq(
                    reader,
                    hash.postingsArray as FreqProxTermsWriterPerField.FreqProxPostingsArray,
                    prefDoc,
                    p.termId,
                    entry.key,
                    entry.value
                )
                prefDoc = entry.key
            }
            assertTrue(eof, "the last posting must be EOF on the reader")
        }
    }

    @Test
    @Throws(IOException::class)
    fun testWriteBytes() {
        for (i in 0..<100) {
            val newCalled = AtomicInteger(0)
            val addCalled = AtomicInteger(0)
            val hash = createNewHash(newCalled, addCalled)
            hash.start(null, true)
            hash.add(newBytesRef("start"), 0)
            val size = TestUtil.nextInt(random(), 50000, 100000)
            val randomData = ByteArray(size)
            random().nextBytes(randomData)
            var offset = 0
            while (offset < randomData.size) {
                val writeLength = minOf(randomData.size - offset, TestUtil.nextInt(random(), 1, 200))
                hash.writeBytes(0, randomData, offset, writeLength)
                offset += writeLength
            }
            val reader = ByteSliceReader()
            reader.init(hash.bytePool, 0, hash.bytePool.byteOffset + hash.bytePool.byteUpto)
            for (expected in randomData) {
                assertEquals(expected, reader.readByte())
            }
        }
    }
}
