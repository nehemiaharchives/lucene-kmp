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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDocsWithFieldSet : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testDense() {
        val set = DocsWithFieldSet()
        var it = set.iterator()
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc())

        set.add(0)
        it = set.iterator()
        assertEquals(0, it.nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc())

        val ramBytesUsed = set.ramBytesUsed()
        for (i in 1..<1000) {
            set.add(i)
        }
        assertEquals(ramBytesUsed, set.ramBytesUsed())
        it = set.iterator()
        for (i in 0..<1000) {
            assertEquals(i, it.nextDoc())
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc())
    }

    @Test
    @Throws(IOException::class)
    fun testSparse() {
        val set = DocsWithFieldSet()
        val doc = random().nextInt(10000)
        set.add(doc)
        var it = set.iterator()
        assertEquals(doc, it.nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc())
        val doc2 = doc + TestUtil.nextInt(random(), 1, 100)
        set.add(doc2)
        it = set.iterator()
        assertEquals(doc, it.nextDoc())
        assertEquals(doc2, it.nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc())
    }

    @Test
    @Throws(IOException::class)
    fun testDenseThenSparse() {
        val denseCount = random().nextInt(10000)
        val nextDoc = denseCount + random().nextInt(10000)
        val set = DocsWithFieldSet()
        for (i in 0..<denseCount) {
            set.add(i)
        }
        set.add(nextDoc)
        val it = set.iterator()
        for (i in 0..<denseCount) {
            assertEquals(i, it.nextDoc())
        }
        assertEquals(nextDoc, it.nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, it.nextDoc())
    }
}
