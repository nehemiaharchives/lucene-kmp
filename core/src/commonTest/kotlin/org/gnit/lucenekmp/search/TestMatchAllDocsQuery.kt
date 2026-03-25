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
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests MatchAllDocsQuery. */
class TestMatchAllDocsQuery : LuceneTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        analyzer = MockAnalyzer(random())
    }

    @Test
    @Throws(Exception::class)
    fun testQuery() {
        val dir = newDirectory()
        val iw =
            IndexWriter(
                dir,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy())
            )
        addDoc("one", iw)
        addDoc("two", iw)
        addDoc("three four", iw)
        var ir: IndexReader = DirectoryReader.open(iw)

        var `is` = newSearcher(ir)
        var hits: Array<ScoreDoc>

        hits = `is`.search(MatchAllDocsQuery(), 1000).scoreDocs
        assertEquals(3, hits.size)
        assertEquals("one", `is`.storedFields().document(hits[0].doc).get("key"))
        assertEquals("two", `is`.storedFields().document(hits[1].doc).get("key"))
        assertEquals("three four", `is`.storedFields().document(hits[2].doc).get("key"))

        // some artificial queries to trigger the use of skipTo():

        var bq = BooleanQuery.Builder()
        bq.add(MatchAllDocsQuery(), BooleanClause.Occur.MUST)
        bq.add(MatchAllDocsQuery(), BooleanClause.Occur.MUST)
        hits = `is`.search(bq.build(), 1000).scoreDocs
        assertEquals(3, hits.size)

        bq = BooleanQuery.Builder()
        bq.add(MatchAllDocsQuery(), BooleanClause.Occur.MUST)
        bq.add(TermQuery(Term("key", "three")), BooleanClause.Occur.MUST)
        hits = `is`.search(bq.build(), 1000).scoreDocs
        assertEquals(1, hits.size)

        iw.deleteDocuments(Term("key", "one"))
        ir.close()
        ir = DirectoryReader.open(iw)
        `is` = newSearcher(ir)

        hits = `is`.search(MatchAllDocsQuery(), 1000).scoreDocs
        assertEquals(2, hits.size)

        iw.close()
        ir.close()
        dir.close()
    }

    @Test
    fun testEquals() {
        val q1: Query = MatchAllDocsQuery()
        val q2: Query = MatchAllDocsQuery()
        assertTrue(q1 == q2)
    }

    @Throws(IOException::class)
    private fun addDoc(text: String, iw: IndexWriter) {
        val doc = Document()
        val f = newTextField("key", text, Field.Store.YES)
        doc.add(f)
        iw.addDocument(doc)
    }

    @Test
    @Throws(IOException::class)
    fun testEarlyTermination() {
        val dir = newDirectory()
        val iw =
            IndexWriter(
                dir,
                newIndexWriterConfig(analyzer)
                    .setMaxBufferedDocs(2)
                    .setMergePolicy(newLogMergePolicy())
            )
        val numDocs = 500
        for (i in 0 until numDocs) {
            addDoc("doc$i", iw)
        }
        val ir: IndexReader = DirectoryReader.open(iw)

        val singleThreadedSearcher = newSearcher(ir, true, true, false)
        val totalHitsThreshold = 200
        var collectorManager = TopScoreDocCollectorManager(10, totalHitsThreshold)

        var topDocs = singleThreadedSearcher.search(MatchAllDocsQuery(), collectorManager)
        assertEquals((totalHitsThreshold + 1).toLong(), topDocs.totalHits.value)
        assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)

        val `is` = newSearcher(ir)
        collectorManager = TopScoreDocCollectorManager(10, numDocs)

        topDocs = `is`.search(MatchAllDocsQuery(), collectorManager)
        assertEquals(numDocs.toLong(), topDocs.totalHits.value)
        assertEquals(TotalHits.Relation.EQUAL_TO, topDocs.totalHits.relation)

        iw.close()
        ir.close()
        dir.close()
    }
}
