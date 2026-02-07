package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.lucene101.Lucene101Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.BaseStoredFieldsFormatTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLucene90StoredFieldsFormatHighCompression : BaseStoredFieldsFormatTestCase() {
    override val codec: Codec
        get() = Lucene101Codec(Lucene101Codec.Mode.BEST_COMPRESSION)

    /**
     * Change compression params (leaving it the same for old segments) and tests that nothing breaks.
     */
    @Test
    @Throws(Exception::class)
    fun testMixedCompressions() {
        val dir: Directory = newDirectory()
        for (i in 0..9) {
            val iwc: IndexWriterConfig = newIndexWriterConfig()
            iwc.setCodec(Lucene101Codec(RandomPicks.randomFrom<Lucene101Codec.Mode>(random(), Lucene101Codec.Mode.entries.toTypedArray())))
            val iw = IndexWriter(dir, newIndexWriterConfig())
            val doc = Document()
            doc.add(StoredField("field1", "value1"))
            doc.add(StoredField("field2", "value2"))
            iw.addDocument(doc)
            if (random().nextInt(4) == 0) {
                iw.forceMerge(1)
            }
            iw.commit()
            iw.close()
        }

        val ir: DirectoryReader = DirectoryReader.open(dir)
        assertEquals(10, ir.numDocs().toLong())
        val storedFields: StoredFields = ir.storedFields()
        for (i in 0..9) {
            val doc = storedFields.document(i)
            assertEquals("value1", doc.get("field1"))
            assertEquals("value2", doc.get("field2"))
        }
        ir.close()
        // checkindex
        dir.close()
    }

    @Test
    fun testInvalidOptions() {
        expectThrows(
            NullPointerException::class,
            {
                Lucene101Codec(/*null*/ null)
            })

        expectThrows(
            NullPointerException::class,
            {
                Lucene90StoredFieldsFormat(/*null*/ null)
            })
    }

    // tests inherited from BaseStoredFieldsFormatTestCase

    @Test
    override fun testRandomStoredFields() = super.testRandomStoredFields()

    @Test
    override fun testStoredFieldsOrder() = super.testStoredFieldsOrder()

    @Test
    override fun testBinaryFieldOffsetLength() = super.testBinaryFieldOffsetLength()

    @Test
    override fun testNumericField() = super.testNumericField()

    @Test
    override fun testIndexedBit() = super.testIndexedBit()

    @Test
    override fun testReadSkip() = super.testReadSkip()

    @Test
    override fun testEmptyDocs() = super.testEmptyDocs()

    @Test
    override fun testConcurrentReads() = super.testConcurrentReads()

    @Test
    override fun testWriteReadMerge() = super.testWriteReadMerge()

    @Test
    override fun testMergeFilterReader() = super.testMergeFilterReader()

    @Test
    override fun testBigDocuments() = super.testBigDocuments()

    @Test
    override fun testBulkMergeWithDeletes() = super.testBulkMergeWithDeletes()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

    @Test
    override fun testRandomStoredFieldsWithIndexSort() = super.testRandomStoredFieldsWithIndexSort()

    @Test
    override fun testLineFileDocs() = super.testLineFileDocs()
}
