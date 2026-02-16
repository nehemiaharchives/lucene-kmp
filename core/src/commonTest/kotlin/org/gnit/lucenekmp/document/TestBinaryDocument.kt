package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.jdkport.toByteArray
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tests [Document] class.  */
class TestBinaryDocument : LuceneTestCase() {
    var binaryValStored: String = "this text will be stored as a byte array in the index"
    var binaryValCompressed: String = "this text will be also stored and compressed as a byte array in the index"

    @Test
    @Throws(Exception::class)
    fun testBinaryFieldInIndex() {
        val ft = FieldType()
        ft.setStored(true)
        val binaryFldStored =
            StoredField("binaryStored", binaryValStored.toByteArray(StandardCharsets.UTF_8))
        val stringFldStored = Field("stringStored", binaryValStored, ft)

        val doc = Document()

        doc.add(binaryFldStored)

        doc.add(stringFldStored)

        /* test for field count */
        assertEquals(2, doc.getFields().size.toLong())

        /* add the doc to a ram index */
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(doc)

        /* open a reader and fetch the document */
        val reader: IndexReader = writer.reader
        val docFromReader = reader.storedFields().document(0)
        assertTrue(docFromReader != null)

        /* fetch the binary stored field and compare its content with the original one */
        val bytes: BytesRef? = docFromReader.getBinaryValue("binaryStored")
        assertNotNull(bytes)
        val binaryFldStoredTest: String = String.fromByteArray(bytes.bytes, bytes.offset, bytes.length, StandardCharsets.UTF_8)
        assertTrue(binaryFldStoredTest == binaryValStored)

        /* fetch the string field and compare its content with the original one */
        val stringFldStoredTest: String? = docFromReader.get("stringStored")
        assertTrue(stringFldStoredTest == binaryValStored)

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBinaryFieldFromDataInputInIndex() {
        val ft = FieldType()
        ft.setStored(true)
        val byteArray: ByteArray = binaryValStored.toByteArray(StandardCharsets.UTF_8)
        val storedFieldDataInput =
            StoredFieldDataInput(ByteArrayDataInput(byteArray))
        val binaryFldStored = StoredField("binaryStored", storedFieldDataInput)
        val stringFldStored = Field("stringStored", binaryValStored, ft)

        val doc = Document()

        doc.add(binaryFldStored)

        doc.add(stringFldStored)

        /* test for field count */
        assertEquals(2, doc.getFields().size.toLong())

        /* add the doc to a ram index */
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(doc)

        /* open a reader and fetch the document */
        val reader: IndexReader = writer.reader
        val docFromReader = reader.storedFields().document(0)
        assertTrue(docFromReader != null)

        /* fetch the binary stored field and compare its content with the original one */
        val bytes: BytesRef? = docFromReader.getBinaryValue("binaryStored")
        assertNotNull(bytes)
        val binaryFldStoredTest: String = String.fromByteArray(bytes.bytes, bytes.offset, bytes.length, StandardCharsets.UTF_8)
        assertTrue(binaryFldStoredTest == binaryValStored)

        /* fetch the string field and compare its content with the original one */
        val stringFldStoredTest: String? = docFromReader.get("stringStored")
        assertTrue(stringFldStoredTest == binaryValStored)

        writer.close()
        reader.close()
        dir.close()
    }
}
