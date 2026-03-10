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
import org.gnit.lucenekmp.index.FrozenBufferedUpdates.TermDocsIterator
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefArray
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.comparisons.naturalOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class TestFrozenBufferedUpdates : LuceneTestCase() {

    @Test
    fun testTermDocsIterator() {
        repeat(5) {
            newDirectory().use { dir: Directory ->
                IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                    val duplicates = random().nextBoolean()
                    val nonMatches = random().nextBoolean()
                    val array = BytesRefArray(Counter.newCounter())
                    val numDocs = 10 + random().nextInt(1000)
                    val randomIds = HashSet<BytesRef>()
                    repeat(numDocs) {
                        var id: BytesRef
                        do {
                            id = BytesRef(TestUtil.randomRealisticUnicodeString(random()))
                        } while (!randomIds.add(id))
                    }
                    val asList = ArrayList(randomIds)
                    for (ref in randomIds) {
                        val doc = Document()
                        doc.add(StringField("field", ref, Field.Store.NO))
                        array.append(ref)
                        if (duplicates && rarely()) {
                            array.append(RandomPicks.randomFrom(random(), asList))
                        }
                        if (nonMatches && rarely()) {
                            var id: BytesRef
                            do {
                                id = BytesRef(TestUtil.randomRealisticUnicodeString(random()))
                            } while (randomIds.contains(id))
                            array.append(id)
                        }
                        writer.addDocument(doc)
                    }
                    writer.forceMerge(1)
                    writer.commit()
                    DirectoryReader.open(dir).use { reader ->
                        val sorted = random().nextBoolean()
                        val values = if (sorted) array.iterator(naturalOrder()) else array.iterator()
                        assertEquals(1, reader.leaves().size)
                        val iterator = TermDocsIterator(reader.leaves()[0].reader(), sorted)
                        val bitSet = FixedBitSet(reader.maxDoc())
                        var ref: BytesRef?
                        while ((values.next().also { ref = it }) != null) {
                            val docIdSetIterator = iterator.nextTerm("field", ref!!)
                            if (!nonMatches) {
                                assertNotNull(docIdSetIterator)
                            }
                            if (docIdSetIterator != null) {
                                var doc: Int
                                while ((docIdSetIterator.nextDoc().also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                                    if (!duplicates) {
                                        assertFalse(bitSet.get(doc))
                                    }
                                    bitSet.set(doc)
                                }
                            }
                        }
                        assertEquals(reader.maxDoc(), bitSet.cardinality())
                    }
                }
            }
        }
    }
}
