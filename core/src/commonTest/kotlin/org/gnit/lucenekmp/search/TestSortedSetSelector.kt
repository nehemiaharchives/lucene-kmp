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
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for SortedSetSortField selectors other than MIN, these require optional codec support
 * (random access to ordinals)
 */
@SuppressCodecs("SimpleText")
class TestSortedSetSelector : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testMax() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("foo")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("baz")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)

        val sort = Sort(SortedSetSortField("value", false, SortedSetSelector.Type.MAX))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'baz' comes before 'foo'
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaxReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("foo")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("baz")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)

        val sort = Sort(SortedSetSortField("value", true, SortedSetSelector.Type.MAX))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'baz' comes before 'foo'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaxMissingFirst() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("foo")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("baz")))
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)

        val sortField: SortField = SortedSetSortField("value", false, SortedSetSelector.Type.MAX)
        sortField.missingValue = SortField.STRING_FIRST
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null comes first
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        // 'baz' comes before 'foo'
        assertEquals("3", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[2].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaxMissingLast() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("foo")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("baz")))
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)

        val sortField: SortField = SortedSetSortField("value", false, SortedSetSelector.Type.MAX)
        sortField.missingValue = SortField.STRING_LAST
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // 'baz' comes before 'foo'
        assertEquals("3", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))
        // null comes last
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[2].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaxSingleton() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("baz")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sort = Sort(SortedSetSortField("value", false, SortedSetSelector.Type.MAX))

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
    fun testMiddleMin() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("a")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("d")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sort = Sort(SortedSetSortField("value", false, SortedSetSelector.Type.MIDDLE_MIN))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'b' comes before 'c'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMiddleMinReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("a")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("d")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sort = Sort(SortedSetSortField("value", true, SortedSetSelector.Type.MIDDLE_MIN))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'b' comes before 'c'
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMiddleMinMissingFirst() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("a")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("d")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sortField: SortField =
            SortedSetSortField("value", false, SortedSetSelector.Type.MIDDLE_MIN)
        sortField.missingValue = SortField.STRING_FIRST
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null comes first
        assertEquals("3", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        // 'b' comes before 'c'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[2].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMiddleMinMissingLast() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("a")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("d")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sortField: SortField =
            SortedSetSortField("value", false, SortedSetSelector.Type.MIDDLE_MIN)
        sortField.missingValue = SortField.STRING_LAST
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // 'b' comes before 'c'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))
        // null comes last
        assertEquals("3", searcher.storedFields().document(td.scoreDocs[2].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMiddleMinSingleton() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("baz")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sort = Sort(SortedSetSortField("value", false, SortedSetSelector.Type.MIDDLE_MIN))

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
    fun testMiddleMax() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("a")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("d")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sort = Sort(SortedSetSortField("value", false, SortedSetSelector.Type.MIDDLE_MAX))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'b' comes before 'c'
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMiddleMaxReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("a")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("d")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sort = Sort(SortedSetSortField("value", true, SortedSetSelector.Type.MIDDLE_MAX))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'b' comes before 'c'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMiddleMaxMissingFirst() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("a")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("d")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sortField: SortField =
            SortedSetSortField("value", false, SortedSetSelector.Type.MIDDLE_MAX)
        sortField.missingValue = SortField.STRING_FIRST
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null comes first
        assertEquals("3", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        // 'b' comes before 'c'
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[2].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMiddleMaxMissingLast() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(newStringField("id", "3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("a")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("c")))
        doc.add(SortedSetDocValuesField("value", newBytesRef("d")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("b")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sortField: SortField =
            SortedSetSortField("value", false, SortedSetSelector.Type.MIDDLE_MAX)
        sortField.missingValue = SortField.STRING_LAST
        val sort = Sort(sortField)

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // 'b' comes before 'c'
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))
        // null comes last
        assertEquals("3", searcher.storedFields().document(td.scoreDocs[2].doc).get("id"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMiddleMaxSingleton() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("baz")))
        doc.add(newStringField("id", "2", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedSetDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("id", "1", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        // slow wrapper does not support random access ordinals (there is no need for that!)
        val searcher = newSearcher(ir, false)
        val sort = Sort(SortedSetSortField("value", false, SortedSetSelector.Type.MIDDLE_MAX))

        val td = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'bar' comes before 'baz'
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[0].doc).get("id"))
        assertEquals("2", searcher.storedFields().document(td.scoreDocs[1].doc).get("id"))

        ir.close()
        dir.close()
    }

}
