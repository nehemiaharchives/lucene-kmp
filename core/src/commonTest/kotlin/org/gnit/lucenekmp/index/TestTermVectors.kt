package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.BaseTermVectorsFormatTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomizedTest
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestTermVectors : LuceneTestCase() {

    @Throws(IOException::class)
    private fun createWriter(dir: Directory): IndexWriter {
        return IndexWriter(
            dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
        )
    }

    @Throws(IOException::class)
    private fun createDir(dir: Directory) {
        val writer: IndexWriter = createWriter(dir)
        writer.addDocument(createDoc())
        writer.close()
    }

    private fun createDoc(): Document {
        val doc = Document()
        val ft = FieldType(TextField.TYPE_STORED)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorOffsets(true)
        ft.setStoreTermVectorPositions(true)
        doc.add(newField("c", "aaa", ft))
        return doc
    }

    @Throws(IOException::class)
    private fun verifyIndex(dir: Directory) {
        val r: IndexReader = DirectoryReader.open(dir)
        val termVectors: TermVectors = r.termVectors()
        val numDocs: Int = r.numDocs()
        for (i in 0..<numDocs) {
            assertNotNull(
                termVectors.get(i)!!.terms("c"), "term vectors should not have been null for document $i"
            )
        }
        r.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFullMergeAddDocs() {
        val target: Directory = newDirectory()
        val writer: IndexWriter = createWriter(target)
        // with maxBufferedDocs=2, this results in two segments, so that forceMerge
        // actually does something.
        for (i in 0..3) {
            writer.addDocument(createDoc())
        }
        writer.forceMerge(1)
        writer.close()

        verifyIndex(target)
        target.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFullMergeAddIndexesDir() {
        val input = arrayOf<Directory>(newDirectory(), newDirectory())
        val target: Directory = newDirectory()

        for (dir in input) {
            createDir(dir)
        }

        val writer: IndexWriter = createWriter(target)
        writer.addIndexes(*input)
        writer.forceMerge(1)
        writer.close()

        verifyIndex(target)

        IOUtils.close(target, input[0], input[1])
    }

    @Test
    @Throws(Exception::class)
    fun testFullMergeAddIndexesReader() {
        val input = arrayOf<Directory>(newDirectory(), newDirectory())
        val target: Directory = newDirectory()

        for (dir in input) {
            createDir(dir)
        }

        val writer: IndexWriter = createWriter(target)
        for (dir in input) {
            val r: DirectoryReader = DirectoryReader.open(dir)
            TestUtil.addIndexesSlowly(writer, r)
            r.close()
        }
        writer.forceMerge(1)
        writer.close()

        verifyIndex(target)
        IOUtils.close(target, input[0], input[1])
    }

    /**
     * Assert that a merged segment has payloads set up in fieldInfo, if at least 1 segment has
     * payloads for this field.
     */
    @Test
    @Throws(Exception::class)
    fun testMergeWithPayloads() {
        val ft1 = FieldType(TextField.TYPE_NOT_STORED)
        ft1.setStoreTermVectors(true)
        ft1.setStoreTermVectorOffsets(true)
        ft1.setStoreTermVectorPositions(true)
        ft1.setStoreTermVectorPayloads(true)
        ft1.freeze()

        val numDocsInSegment = 10
        for (hasPayloads in booleanArrayOf(false, true)) {
            val dir: Directory = newDirectory()
            val indexWriterConfig: IndexWriterConfig =
                IndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(numDocsInSegment)
            val writer: IndexWriter = IndexWriter(dir, indexWriterConfig)
            val tkg1 = TokenStreamGenerator(hasPayloads)
            val tkg2 = TokenStreamGenerator(!hasPayloads)

            // create one segment with payloads, and another without payloads
            for (i in 0..<numDocsInSegment) {
                val doc = Document()
                doc.add(Field("c", tkg1.newTokenStream(), ft1))
                writer.addDocument(doc)
            }
            for (i in 0..<numDocsInSegment) {
                val doc = Document()
                doc.add(Field("c", tkg2.newTokenStream(), ft1))
                writer.addDocument(doc)
            }

            val reader1: IndexReader = DirectoryReader.open(writer)
            assertEquals(2, reader1.leaves().size)
            assertEquals(
                hasPayloads,
                reader1.leaves().get(0).reader().fieldInfos.fieldInfo("c")!!.hasPayloads()
            )
            assertNotEquals(
                hasPayloads,
                reader1.leaves().get(1).reader().fieldInfos.fieldInfo("c")!!.hasPayloads()
            )

            writer.forceMerge(1)
            val reader2: IndexReader = DirectoryReader.open(writer)
            assertEquals(1, reader2.leaves().size)
            // assert that in the merged segments payloads set up for the field
            assertTrue(reader2.leaves().get(0).reader().fieldInfos.fieldInfo("c")!!.hasPayloads())

            IOUtils.close(writer, reader1, reader2, dir)
        }
    }

    /** A generator for token streams with optional null payloads  */
    private class TokenStreamGenerator(private val hasPayloads: Boolean) {
        private val terms: Array<String>
        private val termBytes: Array<BytesRef>

        init {
            val termsCount = 10
            terms = arrayOfNulls<String>(termsCount) as Array<String>
            termBytes = arrayOfNulls<BytesRef>(termsCount) as Array<BytesRef>
            for (i in 0..<termsCount) {
                terms[i] = TestUtil.randomRealisticUnicodeString(random())
                termBytes[i] = BytesRef(terms[i])
            }
        }

        fun newTokenStream(): TokenStream {
            return OptionalNullPayloadTokenStream(TestUtil.nextInt(random(), 1, 5), terms, termBytes)
        }

        private inner class OptionalNullPayloadTokenStream
            (len: Int, sampleTerms: Array<String>, sampleTermBytes: Array<BytesRef>) : BaseTermVectorsFormatTestCase.RandomTokenStream(len, sampleTerms, sampleTermBytes) {
            override fun randomPayload(): BytesRef? {
                if (hasPayloads == false) {
                    return null
                }
                val len: Int = RandomizedTest.randomIntBetween(1, 5)
                val payload = BytesRef(len)
                random().nextBytes(payload.bytes)
                payload.length = len
                return payload
            }
        }
    }
}
