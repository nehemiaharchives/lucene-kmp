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
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests MatchNoDocsQuery. */
class TestMatchNoDocsQuery : LuceneTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        analyzer = MockAnalyzer(random())
    }

    @Test
    @Throws(Exception::class)
    fun testSimple() {
        var query = MatchNoDocsQuery()
        assertEquals("MatchNoDocsQuery(\"\")", query.toString())
        query = MatchNoDocsQuery("field 'title' not found")
        assertEquals("MatchNoDocsQuery(\"field 'title' not found\")", query.toString())
        val dir = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig(analyzer))
        val searcher = newSearcher(DirectoryReader.open(iw))
        val rewrite = query.rewrite(searcher)
        searcher.indexReader.close()
        iw.close()
        dir.close()
        assertEquals("MatchNoDocsQuery(\"field 'title' not found\")", rewrite.toString())
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
        addDoc("three", iw)
        val ir: IndexReader = DirectoryReader.open(iw)
        val searcher = IndexSearcher(ir)

        var query: Query = MatchNoDocsQuery("field not found")
        assertEquals(0, searcher.count(query))

        var hits: Array<ScoreDoc>
        hits = searcher.search(MatchNoDocsQuery(), 1000).scoreDocs
        assertEquals(0, hits.size)
        assertEquals("MatchNoDocsQuery(\"field not found\")", query.toString())

        var bq = BooleanQuery.Builder()
        bq.add(BooleanClause(TermQuery(Term("key", "five")), BooleanClause.Occur.SHOULD))
        bq.add(BooleanClause(MatchNoDocsQuery("field not found"), BooleanClause.Occur.MUST))
        query = bq.build()
        assertEquals(0, searcher.count(query))
        hits = searcher.search(MatchNoDocsQuery(), 1000).scoreDocs
        assertEquals(0, hits.size)
        assertEquals("key:five +MatchNoDocsQuery(\"field not found\")", query.toString())

        bq = BooleanQuery.Builder()
        bq.add(BooleanClause(TermQuery(Term("key", "one")), BooleanClause.Occur.SHOULD))
        bq.add(BooleanClause(MatchNoDocsQuery("field not found"), BooleanClause.Occur.SHOULD))
        query = bq.build()
        assertEquals("key:one MatchNoDocsQuery(\"field not found\")", query.toString())
        assertEquals(1, searcher.count(query))
        hits = searcher.search(query, 1000).scoreDocs
        val rewrite = query.rewrite(searcher)
        assertEquals(1, hits.size)
        assertEquals("key:one", rewrite.toString())

        iw.close()
        ir.close()
        dir.close()
    }

    @Test
    fun testEquals() {
        val q1: Query = MatchNoDocsQuery()
        val q2: Query = MatchNoDocsQuery()
        assertEquals(q1, q2)
        QueryUtils.check(q1)
    }

    @Throws(IOException::class)
    private fun addDoc(text: String, iw: IndexWriter) {
        val doc = Document()
        val f = newTextField("key", text, Field.Store.YES)
        doc.add(f)
        iw.addDocument(doc)
    }
}
