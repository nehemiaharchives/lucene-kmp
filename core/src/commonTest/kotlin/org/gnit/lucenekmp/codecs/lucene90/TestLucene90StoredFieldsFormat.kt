@file:OptIn(ExperimentalAtomicApi::class)

package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.incrementAndGet
import org.gnit.lucenekmp.jdkport.set
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.FilterIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.codecs.compressing.dummy.DummyCompressingCodec
import org.gnit.lucenekmp.tests.index.BaseStoredFieldsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLucene90StoredFieldsFormat : BaseStoredFieldsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

    private class CountingPrefetchDirectory(`in`: Directory, private val counter: AtomicInteger) : FilterDirectory(`in`) {

        @Throws(IOException::class)
        override fun openInput(
            name: String,
            context: IOContext
        ): IndexInput {
            return CountingPrefetchIndexInput(super.openInput(name, context), counter)
        }
    }

    private class CountingPrefetchIndexInput(
        input: IndexInput,
        private val counter: AtomicInteger
    ) : FilterIndexInput(input.toString(), input) {

        @Throws(IOException::class)
        override fun prefetch(offset: Long, length: Long) {
            `in`.prefetch(offset, length)
            counter.incrementAndGet()
        }

        override fun clone(): IndexInput {
            return CountingPrefetchIndexInput(`in`.clone(), counter)
        }

        @Throws(IOException::class)
        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            return CountingPrefetchIndexInput(`in`.slice(sliceDescription, offset, length), counter)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSkipRedundantPrefetches() {
        // Use the "dummy" codec, which has the same base class as Lucene90StoredFieldsFormat but allows
        // configuring the number of docs per chunk.
        val codec: Codec = DummyCompressingCodec(1 shl 10, 2, false, 16)
        newDirectory().use { origDir ->
            val counter: AtomicInteger = AtomicInteger(0)
            val dir: Directory = CountingPrefetchDirectory(origDir, counter)
            IndexWriter(dir, IndexWriterConfig().setCodec(codec)).use { w ->
                for (i in 0..99) {
                    val doc = Document()
                    doc.add(StoredField("content", TestUtil.randomSimpleString(random())))
                    w.addDocument(doc)
                }
                w.forceMerge(1)
            }
            DirectoryReader.open(dir).use { reader ->
                val storedFields: StoredFields = reader.storedFields()
                counter.set(0)
                assertEquals(0, counter.load().toLong())
                storedFields.prefetch(0)
                assertEquals(1, counter.load().toLong())
                storedFields.prefetch(1)
                // This format has 2 docs per block, so the second prefetch is skipped
                assertEquals(1, counter.load().toLong())
                storedFields.prefetch(15)
                assertEquals(2, counter.load().toLong())
                storedFields.prefetch(14)
                // 14 is in the same block as 15, so the prefetch was skipped
                assertEquals(2, counter.load().toLong())
                // Already prefetched in the past, so skipped again
                storedFields.prefetch(1)
                assertEquals(2, counter.load().toLong())
            }
        }
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
