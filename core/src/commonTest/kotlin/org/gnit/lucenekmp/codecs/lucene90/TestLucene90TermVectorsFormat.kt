@file:OptIn(ExperimentalAtomicApi::class)

package org.gnit.lucenekmp.codecs.lucene90

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.TermVectors
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.FilterIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.codecs.compressing.dummy.DummyCompressingCodec
import org.gnit.lucenekmp.tests.index.BaseTermVectorsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLucene90TermVectorsFormat : BaseTermVectorsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

    private class CountingPrefetchDirectory(`in`: Directory, private val counter: AtomicInteger) : FilterDirectory(`in`) {

        @Throws(IOException::class)
        override fun openInput(name: String, context: IOContext): IndexInput {
            return CountingPrefetchIndexInput(super.openInput(name, context), counter)
        }
    }

    private class CountingPrefetchIndexInput(input: IndexInput, private val counter: AtomicInteger) : FilterIndexInput(input.toString(), input) {

        @Throws(IOException::class)
        override fun prefetch(offset: Long, length: Long) {
            `in`.prefetch(offset, length)
            counter.incrementAndFetch()
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
                val ft = FieldType(TextField.TYPE_NOT_STORED)
                ft.setStoreTermVectors(true)
                for (i in 0..99) {
                    val doc = Document()
                    doc.add(Field("content", i.toString(), ft))
                    w.addDocument(doc)
                }
                w.forceMerge(1)
            }
            DirectoryReader.open(dir).use { reader ->
                val termVectors: TermVectors = reader.termVectors()
                counter.store(0)
                assertEquals(0, counter.load().toLong())
                termVectors.prefetch(0)
                assertEquals(1, counter.load().toLong())
                termVectors.prefetch(1)
                // This format has 2 docs per block, so the second prefetch is skipped
                assertEquals(1, counter.load().toLong())
                termVectors.prefetch(15)
                assertEquals(2, counter.load().toLong())
                termVectors.prefetch(14)
                // 14 is in the same block as 15, so the prefetch was skipped
                assertEquals(2, counter.load().toLong())
                // Already prefetched in the past, so skipped again
                termVectors.prefetch(1)
                assertEquals(2, counter.load().toLong())
            }
        }
    }

    // tests inherited from BaseTermVectorsFormatTestCase

    @Test
    override fun testRareVectors() = super.testRareVectors()

    @Test
    override fun testHighFreqs() = super.testHighFreqs()

    @Test
    override fun testLotsOfFields() = super.testLotsOfFields()

    @Test
    override fun testMixedOptions() = super.testMixedOptions()

    @Test
    override fun testRandom() = super.testRandom()

    @Test
    override fun testMerge() = super.testMerge()

    @Test
    override fun testMergeWithDeletes() = super.testMergeWithDeletes()

    @Test
    override fun testMergeWithIndexSort() = super.testMergeWithIndexSort()

    @Test
    override fun testMergeWithIndexSortAndDeletes() = super.testMergeWithIndexSortAndDeletes()

    @Test
    override fun testClone() = super.testClone()

    @Test
    override fun testPostingsEnumFreqs() = super.testPostingsEnumFreqs()

    @Test
    override fun testPostingsEnumPositions() = super.testPostingsEnumPositions()

    @Test
    override fun testPostingsEnumOffsets() = super.testPostingsEnumOffsets()

    @Test
    override fun testPostingsEnumOffsetsWithoutPositions() = super.testPostingsEnumOffsetsWithoutPositions()

    @Test
    override fun testPostingsEnumPayloads() = super.testPostingsEnumPayloads()

    @Test
    override fun testPostingsEnumAll() = super.testPostingsEnumAll()

}
