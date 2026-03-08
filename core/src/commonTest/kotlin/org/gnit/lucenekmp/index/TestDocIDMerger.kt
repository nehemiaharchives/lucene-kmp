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

import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDocIDMerger : LuceneTestCase() {
    private class TestSubUnsorted(docMap: MergeState.DocMap, val maxDoc: Int, val valueStart: Int) :
        DocIDMerger.Sub(docMap) {
        private var docID = -1

        override fun nextDoc(): Int {
            docID++
            return if (docID == maxDoc) {
                NO_MORE_DOCS
            } else {
                docID
            }
        }

        fun getValue(): Int {
            return valueStart + docID
        }
    }

    @Test
    fun testNoSort() {
        val subCount = TestUtil.nextInt(random(), 1, 20)
        val subs = mutableListOf<TestSubUnsorted>()
        var valueStart = 0
        for (i in 0..<subCount) {
            val maxDoc = TestUtil.nextInt(random(), 1, 1000)
            val docBase = valueStart
            subs.add(TestSubUnsorted({ docID -> docBase + docID }, maxDoc, valueStart))
            valueStart += maxDoc
        }

        val merger = DocIDMerger.of(subs, false)

        var count = 0
        while (true) {
            val sub = merger.next()
            if (sub == null) {
                break
            }
            assertEquals(count, sub.mappedDocID)
            assertEquals(count, sub.getValue())
            count++
        }

        assertEquals(valueStart, count)
    }

    private class TestSubSorted(docMap: MergeState.DocMap, val maxDoc: Int, val index: Int) :
        DocIDMerger.Sub(docMap) {
        private var docID = -1

        override fun nextDoc(): Int {
            docID++
            return if (docID == maxDoc) {
                NO_MORE_DOCS
            } else {
                docID
            }
        }

        override fun toString(): String {
            return "TestSubSorted(index=$index, mappedDocID=$mappedDocID)"
        }
    }

    @Test
    fun testWithSort() {
        val subCount = TestUtil.nextInt(random(), 1, 20)
        val oldToNew = mutableListOf<IntArray>()
        // how many docs we've written to each sub:
        val uptos = mutableListOf<Int>()
        var totDocCount = 0
        for (i in 0..<subCount) {
            val maxDoc = TestUtil.nextInt(random(), 1, 1000)
            uptos.add(0)
            oldToNew.add(IntArray(maxDoc))
            totDocCount += maxDoc
        }

        val completedSubs = mutableListOf<IntArray>()

        // randomly distribute target docIDs into the segments:
        for (docID in 0..<totDocCount) {
            val sub = random().nextInt(oldToNew.size)
            var upto = uptos[sub]
            val subDocs = oldToNew[sub]
            subDocs[upto] = docID
            upto++
            if (upto == subDocs.size) {
                completedSubs.add(subDocs)
                oldToNew.removeAt(sub)
                uptos.removeAt(sub)
            } else {
                uptos[sub] = upto
            }
        }
        assertEquals(0, oldToNew.size)

        // sometimes do some deletions:
        val liveDocs: FixedBitSet? =
            if (random().nextBoolean()) {
                FixedBitSet(totDocCount).also {
                    it.set(0, totDocCount)
                    val deleteAttemptCount = TestUtil.nextInt(random(), 1, totDocCount)
                    for (i in 0..<deleteAttemptCount) {
                        it.clear(random().nextInt(totDocCount))
                    }
                }
            } else {
                null
            }

        val subs = mutableListOf<TestSubSorted>()
        for (i in 0..<subCount) {
            val docMap = completedSubs[i]
            subs.add(
                TestSubSorted(
                    { docID ->
                        val mapped = docMap[docID]
                        if (liveDocs == null || liveDocs.get(mapped)) {
                            mapped
                        } else {
                            -1
                        }
                    },
                    docMap.size,
                    i,
                ),
            )
        }

        val merger = DocIDMerger.of(subs, true)

        var count = 0
        while (true) {
            val sub = merger.next()
            if (sub == null) {
                break
            }
            if (liveDocs != null) {
                count = liveDocs.nextSetBit(count)
            }
            assertEquals(count, sub.mappedDocID)
            count++
        }

        if (liveDocs != null) {
            if (count < totDocCount) {
                assertEquals(NO_MORE_DOCS, liveDocs.nextSetBit(count))
            } else {
                assertEquals(totDocCount, count)
            }
        } else {
            assertEquals(totDocCount, count)
        }
    }
}
