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

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexSearcher
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.similarities.RawTFSimilarity
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils

class TestConjunctions : LuceneTestCase() {
    private lateinit var analyzer: Analyzer
    private lateinit var dir: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    companion object {
        const val F1 = "title"
        const val F2 = "body"

        fun doc(v1: String, v2: String): Document {
            val doc = Document()
            doc.add(StringField(F1, v1, Field.Store.YES))
            doc.add(TextField(F2, v2, Field.Store.YES))
            return doc
        }
    }

    @BeforeTest
    fun setUp() {
        analyzer = MockAnalyzer(random())
        dir = ByteBuffersDirectory()
        val config = IndexWriterConfig(analyzer)
        val writer = IndexWriter(dir, config)
        writer.addDocument(doc("lucene", "lucene is a very popular search engine library"))
        writer.addDocument(doc("solr", "solr is a very popular search server and is using lucene"))
        writer.addDocument(
            doc(
                "nutch",
                "nutch is an internet search engine with web crawler and is using lucene and hadoop"
            )
        )
        reader = DirectoryReader.open(writer)
        writer.close()
        searcher = IndexSearcher(reader)
        searcher.similarity = RawTFSimilarity()
    }

    @AfterTest
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    fun testTermConjunctionsWithOmitTF() {
        val bq = BooleanQuery.Builder()
        bq.add(TermQuery(Term(F1, "nutch")), BooleanClause.Occur.MUST)
        bq.add(TermQuery(Term(F2, "is")), BooleanClause.Occur.MUST)
        val td = searcher.search(bq.build(), 3)
        assertEquals(1, td.totalHits.value)
        assertEquals(3f, td.scoreDocs[0].score, 0.001f) // f1:nutch + f2:is + f2:is
    }

    @Test
    fun testScorerGetChildren() {
        val dir = ByteBuffersDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        doc.add(TextField("field", "a b", Field.Store.NO))
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        val b = BooleanQuery.Builder()
        b.add(TermQuery(Term("field", "a")), BooleanClause.Occur.MUST)
        b.add(TermQuery(Term("field", "b")), BooleanClause.Occur.FILTER)
        val q = b.build()
        val s = IndexSearcher(r)
        s.search(
            q,
            object : CollectorManager<TestCollector, Void?> {
                override fun newCollector(): TestCollector {
                    return TestCollector()
                }

                override fun reduce(collectors: MutableCollection<TestCollector>): Void? {
                    for (collector in collectors) {
                        assertTrue(collector.setScorerCalled.load())
                    }
                    return null
                }
            }
        )
        IOUtils.close(r, w, dir)
    }

    private class TestCollector : SimpleCollector() {
        @OptIn(ExperimentalAtomicApi::class)
        val setScorerCalled = AtomicBoolean(false)

        override var weight: Weight?
            get() = super.weight
            set(value) {
                super.weight = value
                val query = value!!.query as BooleanQuery
                val clauseList = query.clauses()
                assertEquals(2, clauseList.size)
                val terms = mutableSetOf<String>()
                for (clause in clauseList) {
                    assert(clause.query is TermQuery)
                    val term = (clause.query as TermQuery).getTerm()
                    assertEquals("field", term.field())
                    terms.add(term.text())
                }
                assertEquals(2, terms.size)
                assertTrue(terms.contains("a"))
                assertTrue(terms.contains("b"))
            }

        @Throws(IOException::class)
        override fun setScorer(s: Scorable) {
            val childScorers = s.children
            setScorerCalled.store(true)
            assertEquals(2, childScorers.size)
        }

        @Throws(IOException::class)
        override fun collect(doc: Int) {
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }
    }
}
