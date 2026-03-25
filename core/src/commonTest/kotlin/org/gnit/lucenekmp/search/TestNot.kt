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
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Similarity unit test. */
class TestNot : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testNot() {
        val store = newDirectory()
        val writer = RandomIndexWriter(random(), store)

        val d1 = Document()
        d1.add(newTextField("field", "a b", Field.Store.YES))

        writer.addDocument(d1)
        val reader = writer.reader

        val searcher = newSearcher(reader)

        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term("field", "a")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term("field", "b")), BooleanClause.Occur.MUST_NOT)

        val hits = searcher.search(query.build(), 1000).scoreDocs
        assertEquals(0, hits.size)
        writer.close()
        reader.close()
        store.close()
    }
}
