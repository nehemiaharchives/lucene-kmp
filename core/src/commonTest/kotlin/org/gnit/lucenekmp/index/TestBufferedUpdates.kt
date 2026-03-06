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

import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit test for [BufferedUpdates] */
class TestBufferedUpdates : LuceneTestCase() {

    @Test
    fun testRamBytesUsed() {
        val bu = BufferedUpdates("seg1")
        assertEquals(bu.ramBytesUsed(), 0L)
        assertFalse(bu.any())
        val queries = atLeast(1)
        repeat(queries) {
            val docIDUpto = if (random().nextBoolean()) Int.MAX_VALUE else random().nextInt(100000)
            val term = Term("id", random().nextInt(100).toString())
            bu.addQuery(TermQuery(term), docIDUpto)
        }

        val terms = atLeast(1)
        repeat(terms) {
            val docIDUpto = if (random().nextBoolean()) Int.MAX_VALUE else random().nextInt(100000)
            val term = Term("id", random().nextInt(100).toString())
            bu.addTerm(term, docIDUpto)
        }
        assertTrue(bu.any(), "we have added tons of docIds, terms and queries")

        val totalUsed = bu.ramBytesUsed()
        assertTrue(totalUsed > 0)

        bu.clearDeleteTerms()
        assertTrue(bu.any(), "only terms and docIds are cleaned, the queries are still in memory")
        assertTrue(totalUsed > bu.ramBytesUsed(), "terms are cleaned, ram in used should decrease")

        bu.clear()
        assertFalse(bu.any())
        assertEquals(bu.ramBytesUsed(), 0L)
    }

    @Test
    fun testDeletedTerms() {
        val iters = atLeast(10)
        val fields = arrayOf("a", "b", "c")
        val actual = BufferedUpdates.DeletedTerms()
        repeat(iters) {
            val expected = mutableMapOf<Term, Int>()
            assertTrue(actual.isEmpty)

            val termCount = atLeast(5000)
            val maxBytesNum = random().nextInt(3) + 1
            repeat(termCount) {
                val byteNum = random().nextInt(maxBytesNum) + 1
                val bytes = ByteArray(byteNum)
                random().nextBytes(bytes)
                val term = Term(fields[random().nextInt(fields.size)], BytesRef(bytes))
                val value = random().nextInt(10000000)
                expected[term] = value
                actual.put(term, value)
            }

            assertEquals(expected.size, actual.size())

            for ((term, value) in expected) {
                assertEquals(value, actual.get(term))
            }

            val expectedSorted =
                expected.entries.sortedBy { it.key }.map { entry ->
                    Term(entry.key.field, BytesRef.deepCopyOf(entry.key.bytes)) to entry.value
                }
            val actualSorted = mutableListOf<Pair<Term, Int>>()
            actual.forEachOrdered(object : BufferedUpdates.DeletedTerms.DeletedTermConsumer<Exception> {
                override fun accept(term: Term, docId: Int) {
                    val copy = Term(term.field, BytesRef.deepCopyOf(term.bytes))
                    actualSorted.add(copy to docId)
                }
            })

            assertEquals(expectedSorted, actualSorted)

            actual.clear()
            assertEquals(0, actual.size())
            assertEquals(0, actual.ramBytesUsed())
            assertNull(actual.getPool().buffer)
        }
    }
}
