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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMultiSliceMerge : LuceneTestCase() {
    private lateinit var dir1: Directory
    private lateinit var dir2: Directory
    private lateinit var reader1: IndexReader
    private lateinit var reader2: IndexReader

    @BeforeTest
    fun setUp() {
        dir1 = newDirectory()
        dir2 = newDirectory()
        val random: Random = random()
        val iw1 =
            RandomIndexWriter(
                random(),
                dir1,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy())
            )
        for (i in 0..<100) {
            val doc = Document()
            doc.add(newStringField("field", i.toString(), Field.Store.NO))
            doc.add(newStringField("field2", (i % 2 == 0).toString(), Field.Store.NO))
            doc.add(SortedDocValuesField("field2", BytesRef((i % 2 == 0).toString())))
            iw1.addDocument(doc)

            if (random.nextBoolean()) {
                iw1.getReader(true, false).close()
            }
        }
        reader1 = iw1.getReader(true, false)
        iw1.close()

        val iw2 =
            RandomIndexWriter(
                random(),
                dir2,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy())
            )
        for (i in 0..<100) {
            val doc = Document()
            doc.add(newStringField("field", i.toString(), Field.Store.NO))
            doc.add(newStringField("field2", (i % 2 == 0).toString(), Field.Store.NO))
            doc.add(SortedDocValuesField("field2", BytesRef((i % 2 == 0).toString())))
            iw2.addDocument(doc)

            if (random.nextBoolean()) {
                iw2.commit()
            }
        }
        reader2 = iw2.getReader(true, false)
        iw2.close()
    }

    @AfterTest
    fun tearDown() {
        reader1.close()
        reader2.close()
        dir1.close()
        dir2.close()
    }

    @Test
    fun testMultipleSlicesOfSameIndexSearcher() {
        val executor1 = Executor { command -> command.run() }
        val executor2 = Executor { command -> command.run() }

        val searchers =
            arrayOf(
                IndexSearcher(reader1, executor1),
                IndexSearcher(reader2, executor2)
            )

        val query: Query = MatchAllDocsQuery()

        val topDocs1 = searchers[0].search(query, Int.MAX_VALUE)
        val topDocs2 = searchers[1].search(query, Int.MAX_VALUE)

        CheckHits.checkEqual(query, topDocs1.scoreDocs, topDocs2.scoreDocs)
    }

    @Test
    fun testMultipleSlicesOfMultipleIndexSearchers() {
        val executor1 = Executor { command -> command.run() }
        val executor2 = Executor { command -> command.run() }

        val searchers =
            arrayOf(
                IndexSearcher(reader1, executor1),
                IndexSearcher(reader2, executor2)
            )

        val query: Query = MatchAllDocsQuery()

        val topDocs1 = searchers[0].search(query, Int.MAX_VALUE)
        val topDocs2 = searchers[1].search(query, Int.MAX_VALUE)

        assertEquals(topDocs1.scoreDocs.size, topDocs2.scoreDocs.size)

        for (i in topDocs1.scoreDocs.indices) {
            topDocs1.scoreDocs[i].shardIndex = 0
            topDocs2.scoreDocs[i].shardIndex = 1
        }

        val shardHits = arrayOf(topDocs1, topDocs2)

        val mergedHits1 = TopDocs.merge(0, topDocs1.scoreDocs.size, shardHits)
        val mergedHits2 = TopDocs.merge(0, topDocs1.scoreDocs.size, shardHits)

        CheckHits.checkEqual(query, mergedHits1.scoreDocs, mergedHits2.scoreDocs)
    }
}
