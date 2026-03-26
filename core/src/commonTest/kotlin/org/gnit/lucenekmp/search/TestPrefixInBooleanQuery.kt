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
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * https://issues.apache.org/jira/browse/LUCENE-1974
 *
 * represent the bug of
 *
 * BooleanScorer.score(Collector collector, int max, int firstDocID)
 *
 * Line 273, end=8192, subScorerDocID=11378, then more got false?
 */
class TestPrefixInBooleanQuery : LuceneTestCase() {
    companion object {
        private const val FIELD = "name"
    }

    private lateinit var directory: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    @BeforeTest
    @Throws(Exception::class)
    fun beforeClass() {
        directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)

        val doc = Document()
        val field = newStringField(FIELD, "meaninglessnames", Field.Store.NO)
        doc.add(field)

        for (i in 0..<5137) {
            writer.addDocument(doc)
        }

        field.setStringValue("tangfulin")
        writer.addDocument(doc)

        field.setStringValue("meaninglessnames")
        for (i in 5138..<11377) {
            writer.addDocument(doc)
        }

        field.setStringValue("tangfulin")
        writer.addDocument(doc)

        reader = writer.getReader(applyDeletions = true, writeAllDeletes = false)
        searcher = newSearcher(reader)
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        reader.close()
        directory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPrefixQuery() {
        val query = PrefixQuery(Term(FIELD, "tang"))
        assertEquals(2L, searcher.search(query, 1000).totalHits.value, "Number of matched documents")
    }

    @Test
    @Throws(Exception::class)
    fun testTermQuery() {
        val query = TermQuery(Term(FIELD, "tangfulin"))
        assertEquals(2L, searcher.search(query, 1000).totalHits.value, "Number of matched documents")
    }

    @Test
    @Throws(Exception::class)
    fun testTermBooleanQuery() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "tangfulin")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term(FIELD, "notexistnames")), BooleanClause.Occur.SHOULD)
        assertEquals(2L, searcher.search(query.build(), 1000).totalHits.value, "Number of matched documents")
    }

    @Test
    @Throws(Exception::class)
    fun testPrefixBooleanQuery() {
        val query = BooleanQuery.Builder()
        query.add(PrefixQuery(Term(FIELD, "tang")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term(FIELD, "notexistnames")), BooleanClause.Occur.SHOULD)
        assertEquals(2L, searcher.search(query.build(), 1000).totalHits.value, "Number of matched documents")
    }
}
