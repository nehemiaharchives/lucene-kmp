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
import org.gnit.lucenekmp.document.DoubleField
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FloatField
import org.gnit.lucenekmp.document.IntField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Simple tests for SortedNumericSortField */
class TestSortedNumericSortField : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testEmptyIndex() {
        val empty = newSearcher(MultiReader())
        val query = TermQuery(Term("contents", "foo"))

        var td =
            empty.search(
                query,
                10,
                Sort(SortedNumericSortField("sortednumeric", SortField.Type.LONG)),
                true,
            )
        assertEquals(0, td.totalHits.value)

        // for an empty index, any selector should work
        for (v in SortedNumericSelector.Type.entries) {
            td =
                empty.search(
                    query,
                    10,
                    Sort(SortedNumericSortField("sortednumeric", SortField.Type.LONG, false, v)),
                    true,
                )
            assertEquals(0, td.totalHits.value)
        }
    }

    @Suppress("SelfComparison")
    @Test
    @Throws(Exception::class)
    fun testEquals() {
        val sf: SortField = SortedNumericSortField("a", SortField.Type.LONG)
        assertFalse(sf.equals(null))

        assertEquals(sf, sf)

        val sf2: SortField = SortedNumericSortField("a", SortField.Type.LONG)
        assertEquals(sf, sf2)
        assertEquals(sf.hashCode(), sf2.hashCode())

        assertFalse(sf.equals(SortedNumericSortField("a", SortField.Type.LONG, true)))
        assertFalse(sf.equals(SortedNumericSortField("a", SortField.Type.FLOAT)))
        assertFalse(sf.equals(SortedNumericSortField("b", SortField.Type.LONG)))
        assertFalse(
            sf.equals(
                SortedNumericSortField(
                    "a",
                    SortField.Type.LONG,
                    false,
                    SortedNumericSelector.Type.MAX,
                )
            )
        )
        assertFalse(sf.equals("foo"))
    }

    @Test
    @Throws(Exception::class)
    fun testForward() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(IntField("value", 5, Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(IntField("value", 3, Store.NO))
        doc.add(IntField("value", 7, Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sort = Sort(SortedNumericSortField("value", SortField.Type.INT))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 3 comes before 5
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
        doc.add(IntField("value", 3, Store.NO))
        doc.add(IntField("value", 7, Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(IntField("value", 5, Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)

        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sort = Sort(SortedNumericSortField("value", SortField.Type.INT, true))

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
        doc.add(IntField("value", 5, Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(IntField("value", 3, Store.NO))
        doc.add(IntField("value", 7, Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sortField: SortField = SortedNumericSortField("value", SortField.Type.INT)
        sortField.missingValue = Int.MIN_VALUE
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // 3 comes before 5
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
        doc.add(IntField("value", 5, Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(IntField("value", 3, Store.NO))
        doc.add(IntField("value", 7, Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sortField: SortField = SortedNumericSortField("value", SortField.Type.INT)
        sortField.missingValue = Int.MAX_VALUE
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // 3 comes before 5
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
        doc.add(IntField("value", 5, Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(IntField("value", 3, Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sort = Sort(SortedNumericSortField("value", SortField.Type.INT))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 3 comes before 5
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFloat() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(FloatField("value", -3f, Store.NO))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatField("value", -5f, Store.NO))
        doc.add(FloatField("value", 7f, Store.NO))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sort = Sort(SortedNumericSortField("value", SortField.Type.FLOAT))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // -5 comes before -3
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDouble() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(DoubleField("value", -3.0, Field.Store.YES))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleField("value", -5.0, Field.Store.YES))
        doc.add(DoubleField("value", 7.0, Field.Store.YES))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher = newSearcher(ir)
        val sort = Sort(SortedNumericSortField("value", SortField.Type.DOUBLE))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // -5 comes before -3
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }
}
