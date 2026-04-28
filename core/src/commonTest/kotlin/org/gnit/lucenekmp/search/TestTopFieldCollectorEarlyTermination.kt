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
package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.MockRandomMergePolicy
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestTopFieldCollectorEarlyTermination : LuceneTestCase() {
    private var numDocs = 0
    private lateinit var terms: MutableList<String>
    private lateinit var dir: Directory
    private val sort = Sort(SortField("ndv1", SortField.Type.LONG))
    private lateinit var iw: RandomIndexWriter
    private lateinit var reader: IndexReader

    private fun randomDocument(): Document {
        val doc = Document()
        doc.add(NumericDocValuesField("ndv1", random().nextInt(10).toLong()))
        doc.add(NumericDocValuesField("ndv2", random().nextInt(10).toLong()))
        doc.add(StringField("s", RandomPicks.randomFrom(random(), terms), Field.Store.YES))
        return doc
    }

    @Throws(IOException::class)
    private fun createRandomIndex(singleSortedSegment: Boolean) {
        dir = newDirectory()
        numDocs = atLeast(150)
        val numTerms = TestUtil.nextInt(random(), 1, numDocs / 5)
        val randomTerms = mutableSetOf<String>()
        while (randomTerms.size < numTerms) {
            randomTerms.add(TestUtil.randomSimpleString(random()))
        }
        terms = ArrayList(randomTerms)
        val seed = random().nextLong()
        val iwc: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(Random(seed)))
        if (iwc.mergePolicy is MockRandomMergePolicy) {
            // MockRandomMP randomly wraps the leaf readers which makes merging angry
            iwc.setMergePolicy(newTieredMergePolicy())
        }
        iwc.setMergeScheduler(SerialMergeScheduler()) // for reproducible tests
        iwc.setIndexSort(sort)
        iw = RandomIndexWriter(Random(seed), dir, iwc)
        iw.setDoRandomForceMerge(false) // don't do this, it may happen anyway with MockRandomMP
        for (i in 0 until numDocs) {
            val doc = randomDocument()
            iw.addDocument(doc)
            if (i == numDocs / 2 || (i != numDocs - 1 && random().nextInt(8) == 0)) {
                iw.commit()
            }
            if (random().nextInt(15) == 0) {
                val term = RandomPicks.randomFrom(random(), terms)
                iw.deleteDocuments(Term("s", term))
            }
        }
        if (singleSortedSegment) {
            iw.forceMerge(1)
        } else if (random().nextBoolean()) {
            iw.forceMerge(FORCE_MERGE_MAX_SEGMENT_COUNT)
        }
        reader = iw.reader
        if (reader.numDocs() == 0) {
            iw.addDocument(Document())
            reader.close()
            reader = iw.reader
        }
    }

    @Throws(IOException::class)
    private fun closeIndex() {
        reader.close()
        iw.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEarlyTermination() {
        doTestEarlyTermination(false)
    }

    @Test
    @Throws(IOException::class)
    fun testEarlyTerminationWhenPaging() {
        doTestEarlyTermination(true)
    }

    @Throws(IOException::class)
    private fun doTestEarlyTermination(paging: Boolean) {
        val iters = atLeast(1)
        for (i in 0 until iters) {
            createRandomIndex(false)
            for (j in 0 until iters) {
                val searcher = newSearcher(reader)
                var maxSliceSize = 0
                for (slice in searcher.slices) {
                    var numDocs = 0 // number of live docs in the slice
                    for (partition in slice.partitions) {
                        val liveDocs: Bits? = partition.ctx.reader().liveDocs
                        val maxDoc = minOf(partition.maxDocId, partition.ctx.reader().maxDoc())
                        for (doc in partition.minDocId until maxDoc) {
                            if (liveDocs == null || liveDocs.get(doc)) {
                                numDocs++
                            }
                        }
                    }
                    maxSliceSize = maxOf(maxSliceSize, numDocs)
                }
                val numHits = TestUtil.nextInt(random(), 1, numDocs)
                val after: FieldDoc?
                if (paging) {
                    assert(searcher.indexReader.numDocs() > 0)
                    val td = searcher.search(MatchAllDocsQuery(), 10, sort)
                    after = td.scoreDocs[td.scoreDocs.size - 1] as FieldDoc
                } else {
                    after = null
                }
                val manager1 = TopFieldCollectorManager(sort, numHits, after, Int.MAX_VALUE)
                val manager2 = TopFieldCollectorManager(sort, numHits, after, 1)

                val query: Query =
                    if (random().nextBoolean()) {
                        TermQuery(Term("s", RandomPicks.randomFrom(random(), terms)))
                    } else {
                        MatchAllDocsQuery()
                    }
                val td1 = searcher.search(query, manager1)
                val td2 = searcher.search(query, manager2)

                assertNotEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, td1.totalHits.relation)
                if (!paging && maxSliceSize > numHits && query is MatchAllDocsQuery) {
                    // Make sure that we sometimes early terminate
                    assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, td2.totalHits.relation)
                }
                if (td2.totalHits.relation == TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO) {
                    assertTrue(td2.totalHits.value >= td1.scoreDocs.size.toLong())
                    assertTrue(td2.totalHits.value <= reader.maxDoc().toLong())
                } else {
                    assertEquals(td1.totalHits.value, td2.totalHits.value)
                }
                CheckHits.checkEqual(query, td1.scoreDocs, td2.scoreDocs)
            }
            closeIndex()
        }
    }

    @Test
    fun testCanEarlyTerminateOnDocId() {
        assertTrue(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField.FIELD_DOC),
                Sort(SortField.FIELD_DOC),
            )
        )

        assertTrue(TopFieldCollector.canEarlyTerminate(Sort(SortField.FIELD_DOC), null))

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField("a", SortField.Type.LONG)),
                null,
            )
        )

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField("a", SortField.Type.LONG)),
                Sort(SortField("b", SortField.Type.LONG)),
            )
        )

        assertTrue(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField.FIELD_DOC),
                Sort(SortField("b", SortField.Type.LONG)),
            )
        )

        assertTrue(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField.FIELD_DOC),
                Sort(SortField("b", SortField.Type.LONG), SortField.FIELD_DOC),
            )
        )

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField("a", SortField.Type.LONG)),
                Sort(SortField.FIELD_DOC),
            )
        )

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField("a", SortField.Type.LONG), SortField.FIELD_DOC),
                Sort(SortField.FIELD_DOC),
            )
        )
    }

    @Test
    fun testCanEarlyTerminateOnPrefix() {
        assertTrue(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField("a", SortField.Type.LONG)),
                Sort(SortField("a", SortField.Type.LONG)),
            )
        )

        assertTrue(
            TopFieldCollector.canEarlyTerminate(
                Sort(
                    SortField("a", SortField.Type.LONG),
                    SortField("b", SortField.Type.STRING),
                ),
                Sort(
                    SortField("a", SortField.Type.LONG),
                    SortField("b", SortField.Type.STRING),
                ),
            )
        )

        assertTrue(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField("a", SortField.Type.LONG)),
                Sort(
                    SortField("a", SortField.Type.LONG),
                    SortField("b", SortField.Type.STRING),
                ),
            )
        )

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField("a", SortField.Type.LONG, true)),
                null,
            )
        )

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(SortField("a", SortField.Type.LONG, true)),
                Sort(SortField("a", SortField.Type.LONG, false)),
            )
        )

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(
                    SortField("a", SortField.Type.LONG),
                    SortField("b", SortField.Type.STRING),
                ),
                Sort(SortField("a", SortField.Type.LONG)),
            )
        )

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(
                    SortField("a", SortField.Type.LONG),
                    SortField("b", SortField.Type.STRING),
                ),
                Sort(
                    SortField("a", SortField.Type.LONG),
                    SortField("c", SortField.Type.STRING),
                ),
            )
        )

        assertFalse(
            TopFieldCollector.canEarlyTerminate(
                Sort(
                    SortField("a", SortField.Type.LONG),
                    SortField("b", SortField.Type.STRING),
                ),
                Sort(
                    SortField("c", SortField.Type.LONG),
                    SortField("b", SortField.Type.STRING),
                ),
            )
        )
    }

    companion object {
        private const val FORCE_MERGE_MAX_SEGMENT_COUNT = 5
    }
}

