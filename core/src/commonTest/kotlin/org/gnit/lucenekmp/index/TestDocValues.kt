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
package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Tests helper methods in DocValues */
class TestDocValues : LuceneTestCase() {
    /**
     * If the field doesn't exist, we return empty instances: it can easily happen that a segment
     * just doesn't have any docs with the field.
     */
    @Test
    fun testEmptyIndex() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        iw.addDocument(Document())
        val dr = DirectoryReader.open(iw)
        val r = getOnlyLeafReader(dr)

        // ok
        assertNotNull(DocValues.getBinary(r, "bogus"))
        assertNotNull(DocValues.getNumeric(r, "bogus"))
        assertNotNull(DocValues.getSorted(r, "bogus"))
        assertNotNull(DocValues.getSortedSet(r, "bogus"))
        assertNotNull(DocValues.getSortedNumeric(r, "bogus"))

        dr.close()
        iw.close()
        dir.close()
    }

    /** field just doesnt have any docvalues at all: exception */
    @Test
    fun testMisconfiguredField() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.NO))
        iw.addDocument(doc)
        val dr = DirectoryReader.open(iw)
        val r = getOnlyLeafReader(dr)

        expectThrows(IllegalStateException::class) { DocValues.getBinary(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getNumeric(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSorted(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSortedSet(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSortedNumeric(r, "foo") }

        dr.close()
        iw.close()
        dir.close()
    }

    /** field with numeric docvalues */
    @Test
    fun testNumericField() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        doc.add(NumericDocValuesField("foo", 3))
        iw.addDocument(doc)
        val dr = DirectoryReader.open(iw)
        val r = getOnlyLeafReader(dr)

        assertNotNull(DocValues.getNumeric(r, "foo"))
        assertNotNull(DocValues.getSortedNumeric(r, "foo"))

        expectThrows(IllegalStateException::class) { DocValues.getBinary(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSorted(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSortedSet(r, "foo") }

        dr.close()
        iw.close()
        dir.close()
    }

    /** field with binary docvalues */
    @Test
    fun testBinaryField() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        doc.add(BinaryDocValuesField("foo", BytesRef("bar")))
        iw.addDocument(doc)
        val dr = DirectoryReader.open(iw)
        val r = getOnlyLeafReader(dr)

        assertNotNull(DocValues.getBinary(r, "foo"))

        expectThrows(IllegalStateException::class) { DocValues.getNumeric(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSorted(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSortedSet(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSortedNumeric(r, "foo") }

        dr.close()
        iw.close()
        dir.close()
    }

    /** field with sorted docvalues */
    @Test
    fun testSortedField() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        doc.add(SortedDocValuesField("foo", BytesRef("bar")))
        iw.addDocument(doc)
        val dr = DirectoryReader.open(iw)
        val r = getOnlyLeafReader(dr)

        assertNotNull(DocValues.getSorted(r, "foo"))
        assertNotNull(DocValues.getSortedSet(r, "foo"))

        expectThrows(IllegalStateException::class) { DocValues.getBinary(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getNumeric(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSortedNumeric(r, "foo") }

        dr.close()
        iw.close()
        dir.close()
    }

    /** field with sortedset docvalues */
    @Test
    fun testSortedSetField() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        doc.add(SortedSetDocValuesField("foo", BytesRef("bar")))
        iw.addDocument(doc)
        val dr = DirectoryReader.open(iw)
        val r = getOnlyLeafReader(dr)

        assertNotNull(DocValues.getSortedSet(r, "foo"))

        expectThrows(IllegalStateException::class) { DocValues.getBinary(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getNumeric(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSorted(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSortedNumeric(r, "foo") }

        dr.close()
        iw.close()
        dir.close()
    }

    /** field with sortednumeric docvalues */
    @Test
    fun testSortedNumericField() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        doc.add(SortedNumericDocValuesField("foo", 3))
        iw.addDocument(doc)
        val dr = DirectoryReader.open(iw)
        val r = getOnlyLeafReader(dr)

        assertNotNull(DocValues.getSortedNumeric(r, "foo"))

        expectThrows(IllegalStateException::class) { DocValues.getBinary(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getNumeric(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSorted(r, "foo") }
        expectThrows(IllegalStateException::class) { DocValues.getSortedSet(r, "foo") }

        dr.close()
        iw.close()
        dir.close()
    }

    @Test
    fun testAddNullNumericDocValues() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        if (random().nextBoolean()) {
            doc.add(NumericDocValuesField("foo", null))
        } else {
            doc.add(BinaryDocValuesField("foo", null as BytesRef?))
        }
        val iae: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) { iw.addDocument(doc) }
        assertEquals("field=\"foo\": null value not allowed", iae.message)
        IOUtils.close(iw, dir)
    }
}
