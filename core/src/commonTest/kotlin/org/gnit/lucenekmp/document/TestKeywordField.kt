package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestKeywordField : LuceneTestCase() {

    @Test
    fun testSetBytesValue() {
        val fields =
            arrayOf<Field>(
                KeywordField("name", newBytesRef("value"), Field.Store.NO),
                KeywordField("name", newBytesRef("value"), Field.Store.YES)
            )
        for (field in fields) {
            assertEquals(newBytesRef("value"), field.binaryValue())
            assertNull(field.stringValue())
            if (field.fieldType().stored()) {
                assertEquals(newBytesRef("value"), field.storedValue()!!.binaryValue)
            } else {
                assertNull(field.storedValue())
            }
            field.setBytesValue(newBytesRef("value2"))
            assertEquals(newBytesRef("value2"), field.binaryValue())
            assertNull(field.stringValue())
            if (field.fieldType().stored()) {
                assertEquals(newBytesRef("value2"), field.storedValue()!!.binaryValue)
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    fun testSetStringValue() {
        val fields =
            arrayOf<Field>(
                KeywordField("name", "value", Field.Store.NO),
                KeywordField("name", "value", Field.Store.YES)
            )
        for (field in fields) {
            assertEquals("value", field.stringValue())
            assertEquals(newBytesRef("value"), field.binaryValue())
            if (field.fieldType().stored()) {
                assertEquals("value", field.storedValue()!!.stringValue)
            } else {
                assertNull(field.storedValue())
            }
            field.setStringValue("value2")
            assertEquals("value2", field.stringValue())
            assertEquals(newBytesRef("value2"), field.binaryValue())
            if (field.fieldType().stored()) {
                assertEquals("value2", field.storedValue()!!.stringValue)
            } else {
                assertNull(field.storedValue())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testIndexBytesValue() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(setOf(KeywordField("field", newBytesRef("value"), Field.Store.YES)))
        val reader: IndexReader = DirectoryReader.open(w)
        w.close()
        val leaf: LeafReader = getOnlyLeafReader(reader)
        val terms: TermsEnum = leaf.terms("field")!!.iterator()
        assertEquals(BytesRef("value"), terms.next())
        assertNull(terms.next())
        val values: SortedSetDocValues = leaf.getSortedSetDocValues("field")!!
        assertTrue(values.advanceExact(0))
        assertEquals(1, values.docValueCount().toLong())
        assertEquals(0L, values.nextOrd())
        assertEquals(BytesRef("value"), values.lookupOrd(0))
        val storedDoc: Document = leaf.storedFields().document(0)
        assertEquals(BytesRef("value"), storedDoc.getBinaryValue("field"))
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testIndexStringValue() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(setOf(KeywordField("field", "value", Field.Store.YES)))
        val reader: IndexReader = DirectoryReader.open(w)
        w.close()
        val leaf: LeafReader = getOnlyLeafReader(reader)
        val terms: TermsEnum = leaf.terms("field")!!.iterator()
        assertEquals(BytesRef("value"), terms.next())
        assertNull(terms.next())
        val values: SortedSetDocValues = leaf.getSortedSetDocValues("field")!!
        assertTrue(values.advanceExact(0))
        assertEquals(1, values.docValueCount().toLong())
        assertEquals(0L, values.nextOrd())
        assertEquals(BytesRef("value"), values.lookupOrd(0))
        val storedDoc: Document = leaf.storedFields().document(0)
        assertEquals("value", storedDoc.get("field"))
        reader.close()
        dir.close()
    }

    @Test
    fun testValueClone() {
        val values = ArrayList<BytesRef>(100)
        for (i in 0..<100) {
            val s = TestUtil.randomSimpleString(random(), 10, 20)
            values.add(BytesRef(s))
        }

        // Make sure we don't modify the input values array.
        val expected = ArrayList<BytesRef>(values)
        KeywordField.newSetQuery("f", values)
        assertEquals(expected, values)
    }
}
