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
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.StringHelper
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [PrefixQuery] class. */
class TestPrefixQuery : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testPrefixQuery() {
        val directory: Directory = newDirectory()

        val categories = arrayOf("/Computers", "/Computers/Mac", "/Computers/Windows")
        val writer = RandomIndexWriter(random(), directory)
        for (i in categories.indices) {
            val doc = Document()
            doc.add(newStringField("category", categories[i], Field.Store.YES))
            writer.addDocument(doc)
        }
        val reader: IndexReader = writer.getReader(true, false)

        var query = PrefixQuery(Term("category", "/Computers"))
        val searcher = newSearcher(reader)
        var hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size, "All documents in /Computers category and below")

        query = PrefixQuery(Term("category", "/Computers/Mac"))
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(1, hits.size, "One in /Computers/Mac")

        query = PrefixQuery(Term("category", ""))
        hits = searcher.search(query, 1000).scoreDocs
        assertEquals(3, hits.size, "everything")
        writer.close()
        reader.close()
        directory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMatchAll() {
        val directory: Directory = newDirectory()

        val writer = RandomIndexWriter(random(), directory)
        val doc = Document()
        doc.add(newStringField("field", "field", Field.Store.YES))
        writer.addDocument(doc)

        val reader: IndexReader = writer.getReader(true, false)

        val query = PrefixQuery(Term("field", ""))
        val searcher = newSearcher(reader)

        assertEquals(1L, searcher.search(query, 1000).totalHits.value)
        writer.close()
        reader.close()
        directory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomBinaryPrefix() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val numTerms = atLeast(1000)
        val terms = HashSet<BytesRef>()
        while (terms.size < numTerms) {
            val bytes = ByteArray(TestUtil.nextInt(random(), 1, 10))
            random().nextBytes(bytes)
            terms.add(BytesRef(bytes))
        }

        val termsList = ArrayList(terms)
        termsList.shuffle(random())
        for (term in termsList) {
            val doc = Document()
            doc.add(newStringField("field", term, Field.Store.NO))
            w.addDocument(doc)
        }

        val r = w.getReader(true, false)
        val s = newSearcher(r)

        val iters = atLeast(100)
        for (iter in 0..<iters) {
            val bytes = ByteArray(random().nextInt(3))
            random().nextBytes(bytes)
            val prefix = BytesRef(bytes)
            val q = PrefixQuery(Term("field", prefix))
            var count = 0
            for (term in termsList) {
                if (StringHelper.startsWith(term, prefix)) {
                    count++
                }
            }
            assertEquals(count, s.count(q))
        }
        r.close()
        w.close()
        dir.close()
    }
}
