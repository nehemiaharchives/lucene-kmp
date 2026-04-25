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
import org.gnit.lucenekmp.document.KeywordField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Simple tests for SortedSetSortField, indexing the sortedset up front */
class TestSortedSetSortField : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testEmptyIndex() {
        val empty = newSearcher(MultiReader())
        val query = TermQuery(Term("contents", "foo"))
        var td =
            empty.search(query, 10, Sort(SortedSetSortField("sortedset", false)), true)
        assertEquals(0, td.totalHits.value)

        // for an empty index, any selector should work
        for (v in SortedSetSelector.Type.entries) {
            td = empty.search(query, 10, Sort(SortedSetSortField("sortedset", false, v)), true)
            assertEquals(0, td.totalHits.value)
        }
    }

    @Suppress("SelfComparison")
    @Test
    @Throws(Exception::class)
    fun testEquals() {
        val sf: SortField = SortedSetSortField("a", false)
        assertFalse(sf.equals(null))

        assertEquals(sf, sf)

        val sf2: SortField = SortedSetSortField("a", false)
        assertEquals(sf, sf2)
        assertEquals(sf.hashCode(), sf2.hashCode())

        assertFalse(sf.equals(SortedSetSortField("a", true)))
        assertFalse(sf.equals(SortedSetSortField("b", false)))
        assertFalse(sf.equals(SortedSetSortField("a", false, SortedSetSelector.Type.MAX)))
        assertFalse(sf.equals("foo"))
    }

    @Test
    @Throws(Exception::class)
    fun testForward() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(KeywordField("value", newBytesRef("baz"), Field.Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(KeywordField("value", newBytesRef("foo"), Field.Store.NO))
        doc.add(KeywordField("value", newBytesRef("bar"), Field.Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sort = Sort(SortedSetSortField("value", false))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'bar' comes before 'baz'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(KeywordField("value", newBytesRef("foo"), Field.Store.NO))
        doc.add(KeywordField("value", newBytesRef("bar"), Field.Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(KeywordField("value", newBytesRef("baz"), Field.Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)

        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sort = Sort(SortedSetSortField("value", true))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'bar' comes before 'baz'
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMissingFirst() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(KeywordField("value", newBytesRef("baz"), Field.Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(KeywordField("value", newBytesRef("foo"), Field.Store.NO))
        doc.add(KeywordField("value", newBytesRef("bar"), Field.Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sortField: SortField = SortedSetSortField("value", false)
        sortField.missingValue = SortField.STRING_FIRST
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // 'bar' comes before 'baz'
        // null comes first
        assertEquals("3", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[2].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMissingLast() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(KeywordField("value", newBytesRef("baz"), Field.Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(KeywordField("value", newBytesRef("foo"), Field.Store.NO))
        doc.add(KeywordField("value", newBytesRef("bar"), Field.Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sortField: SortField = SortedSetSortField("value", false)
        sortField.missingValue = SortField.STRING_LAST
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // 'bar' comes before 'baz'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))
        // null comes last
        assertEquals("3", searcher.storedFields().document(td.scoreDocs[2].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSingleton() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(KeywordField("value", newBytesRef("baz"), Field.Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(KeywordField("value", newBytesRef("bar"), Field.Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sort = Sort(SortedSetSortField("value", false))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'bar' comes before 'baz'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }
}
