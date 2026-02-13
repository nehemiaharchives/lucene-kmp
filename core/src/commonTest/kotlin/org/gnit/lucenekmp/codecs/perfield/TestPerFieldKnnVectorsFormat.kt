package org.gnit.lucenekmp.codecs.perfield

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.KnnFloatVectorQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.index.BaseKnnVectorsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPerFieldKnnVectorsFormat : BaseKnnVectorsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.getDefaultCodec()

    @Test
    @Throws(IOException::class)
    fun testMissingFieldReturnsNoResults() {
        newDirectory().use { directory ->
            val iwc = newIndexWriterConfig(MockAnalyzer(random()))
            iwc.setCodec(
                object : AssertingCodec() {
                    override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
                        return TestUtil.getDefaultKnnVectorsFormat()
                    }
                }
            )
            IndexWriter(directory, iwc).use { iwriter ->
                val doc = Document()
                doc.add(newTextField("id", "1", Field.Store.YES))
                iwriter.addDocument(doc)
            }

            DirectoryReader.open(directory).use { ireader ->
                val reader: LeafReader = ireader.leaves()[0].reader()
                var hits =
                    reader.searchNearestVectors(
                        "missing_field",
                        floatArrayOf(1f, 2f, 3f),
                        10,
                        reader.liveDocs,
                        Int.MAX_VALUE
                    )
                assertEquals(0, hits.scoreDocs.size)
                hits =
                    reader.searchNearestVectors(
                        "id",
                        floatArrayOf(1f, 2f, 3f),
                        10,
                        reader.liveDocs,
                        Int.MAX_VALUE
                    )
                assertEquals(0, hits.scoreDocs.size)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testTwoFieldsTwoFormats() {
        newDirectory().use { directory ->
            val iwc = newIndexWriterConfig(MockAnalyzer(random()))
            val format1 = WriteRecordingKnnVectorsFormat(TestUtil.getDefaultKnnVectorsFormat())
            val format2 = WriteRecordingKnnVectorsFormat(TestUtil.getDefaultKnnVectorsFormat())
            iwc.setCodec(
                object : AssertingCodec() {
                    override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
                        return if (field == "field1") format1 else format2
                    }
                }
            )

            IndexWriter(directory, iwc).use { iwriter ->
                val doc = Document()
                doc.add(newTextField("id", "1", Field.Store.YES))
                doc.add(KnnFloatVectorField("field1", floatArrayOf(1f, 2f, 3f)))
                iwriter.addDocument(doc)

                doc.clear()
                doc.add(newTextField("id", "2", Field.Store.YES))
                doc.add(KnnFloatVectorField("field2", floatArrayOf(4f, 5f, 6f)))
                iwriter.addDocument(doc)
            }

            assertEquals(setOf("field1"), format1.fieldsWritten)
            assertEquals(setOf("field2"), format2.fieldsWritten)

            DirectoryReader.open(directory).use { ireader ->
                val reader: LeafReader = ireader.leaves()[0].reader()
                val hits1 =
                    reader.searchNearestVectors(
                        "field1",
                        floatArrayOf(1f, 2f, 3f),
                        10,
                        reader.liveDocs,
                        Int.MAX_VALUE
                    )
                assertEquals(1, hits1.scoreDocs.size)

                val hits2 =
                    reader.searchNearestVectors(
                        "field2",
                        floatArrayOf(1f, 2f, 3f),
                        10,
                        reader.liveDocs,
                        Int.MAX_VALUE
                    )
                assertEquals(1, hits2.scoreDocs.size)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMergeUsesNewFormat() {
        newDirectory().use { directory ->
            val initialConfig = newIndexWriterConfig(MockAnalyzer(random()))
            initialConfig.setMergePolicy(NoMergePolicy.INSTANCE)

            IndexWriter(directory, initialConfig).use { iw ->
                for (i in 0 until 3) {
                    val doc = Document()
                    doc.add(newTextField("id", "1", Field.Store.YES))
                    doc.add(KnnFloatVectorField("field1", floatArrayOf(1f, 2f, 3f)))
                    doc.add(KnnFloatVectorField("field2", floatArrayOf(1f, 2f, 3f)))
                    iw.addDocument(doc)
                    iw.commit()
                }
            }

            val newConfig = newIndexWriterConfig(MockAnalyzer(random()))
            val format1 = WriteRecordingKnnVectorsFormat(TestUtil.getDefaultKnnVectorsFormat())
            val format2 = WriteRecordingKnnVectorsFormat(TestUtil.getDefaultKnnVectorsFormat())
            newConfig.setCodec(
                object : AssertingCodec() {
                    override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
                        return if (field == "field1") format1 else format2
                    }
                }
            )

            IndexWriter(directory, newConfig).use { iw ->
                iw.forceMerge(1)
            }

            assertEquals(setOf("field1"), format1.fieldsWritten)
            assertEquals(setOf("field2"), format2.fieldsWritten)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMaxDimensionsPerFieldFormat() {
        newDirectory().use { directory ->
            val iwc = newIndexWriterConfig(MockAnalyzer(random()))
            val format1: KnnVectorsFormat = KnnVectorsFormatMaxDims32(Lucene99HnswVectorsFormat(16, 100))
            val format2: KnnVectorsFormat = Lucene99HnswVectorsFormat(16, 100)
            iwc.setCodec(
                object : AssertingCodec() {
                    override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
                        return if (field == "field1") format1 else format2
                    }
                }
            )
            IndexWriter(directory, iwc).use { writer ->
                val doc1 = Document()
                doc1.add(KnnFloatVectorField("field1", FloatArray(33)))
                val exc = expectThrows(IllegalArgumentException::class) { writer.addDocument(doc1) }
                assertTrue(exc.message!!.contains("vector's dimensions must be <= [32]"))

                val doc2 = Document()
                doc2.add(KnnFloatVectorField("field1", FloatArray(32)))
                doc2.add(KnnFloatVectorField("field2", FloatArray(33)))
                writer.addDocument(doc2)
            }

            DirectoryReader.open(directory).use { reader: IndexReader ->
                val searcher = IndexSearcher(reader)
                val query1: Query = KnnFloatVectorQuery("field1", FloatArray(32), 10)
                val topDocs1: TopDocs = searcher.search(query1, 1)
                assertEquals(1, topDocs1.scoreDocs.size)

                val query2: Query = KnnFloatVectorQuery("field2", FloatArray(33), 10)
                val topDocs2: TopDocs = searcher.search(query2, 1)
                assertEquals(1, topDocs2.scoreDocs.size)
            }
        }
    }

    // tests inherited from BaseKnnVectorsFormatTestCase

    @Test
    override fun testFieldConstructor() = super.testFieldConstructor()

    @Test
    override fun testFieldConstructorExceptions() = super.testFieldConstructorExceptions()

    @Test
    override fun testFieldSetValue() = super.testFieldSetValue()

    @Test
    override fun testIllegalDimChangeTwoDocs() = super.testIllegalDimChangeTwoDocs()

    @Test
    override fun testIllegalSimilarityFunctionChange() = super.testIllegalSimilarityFunctionChange()

    @Test
    override fun testIllegalDimChangeTwoWriters() = super.testIllegalDimChangeTwoWriters()

    @Test
    override fun testMergingWithDifferentKnnFields() = super.testMergingWithDifferentKnnFields()

    @Test
    override fun testMergingWithDifferentByteKnnFields() = super.testMergingWithDifferentByteKnnFields()

    @Test
    override fun testWriterRamEstimate() = super.testWriterRamEstimate()

    @Test
    override fun testIllegalSimilarityFunctionChangeTwoWriters() = super.testIllegalSimilarityFunctionChangeTwoWriters()

    @Test
    override fun testAddIndexesDirectory0() = super.testAddIndexesDirectory0()

    @Test
    override fun testAddIndexesDirectory1() = super.testAddIndexesDirectory1()

    @Test
    override fun testAddIndexesDirectory01() = super.testAddIndexesDirectory01()

    @Test
    override fun testIllegalDimChangeViaAddIndexesDirectory() = super.testIllegalDimChangeViaAddIndexesDirectory()

    @Test
    override fun testIllegalSimilarityFunctionChangeViaAddIndexesDirectory() = super.testIllegalSimilarityFunctionChangeViaAddIndexesDirectory()

    @Test
    override fun testIllegalDimChangeViaAddIndexesCodecReader() = super.testIllegalDimChangeViaAddIndexesCodecReader()

    @Test
    override fun testIllegalSimilarityFunctionChangeViaAddIndexesCodecReader() = super.testIllegalSimilarityFunctionChangeViaAddIndexesCodecReader()

    @Test
    override fun testIllegalDimChangeViaAddIndexesSlowCodecReader() = super.testIllegalDimChangeViaAddIndexesSlowCodecReader()

    @Test
    override fun testIllegalSimilarityFunctionChangeViaAddIndexesSlowCodecReader() = super.testIllegalSimilarityFunctionChangeViaAddIndexesSlowCodecReader()

    @Test
    override fun testIllegalMultipleValues() = super.testIllegalMultipleValues()

    @Test
    override fun testIllegalDimensionTooLarge() = super.testIllegalDimensionTooLarge()

    @Test
    override fun testIllegalEmptyVector() = super.testIllegalEmptyVector()

    @Test
    override fun testDifferentCodecs1() = super.testDifferentCodecs1()

    @Test
    override fun testDifferentCodecs2() = super.testDifferentCodecs2()

    @Test
    override fun testInvalidKnnVectorFieldUsage() = super.testInvalidKnnVectorFieldUsage()

    @Test
    override fun testDeleteAllVectorDocs() = super.testDeleteAllVectorDocs()

    @Test
    override fun testKnnVectorFieldMissingFromOneSegment() = super.testKnnVectorFieldMissingFromOneSegment()

    @Test
    override fun testSparseVectors() = super.testSparseVectors()

    @Test
    override fun testFloatVectorScorerIteration() = super.testFloatVectorScorerIteration()

    @Test
    override fun testByteVectorScorerIteration() = super.testByteVectorScorerIteration()

    @Test
    override fun testEmptyFloatVectorData() = super.testEmptyFloatVectorData()

    @Test
    override fun testEmptyByteVectorData() = super.testEmptyByteVectorData()

    @Test
    override fun testIndexedValueNotAliased() = super.testIndexedValueNotAliased()

    @Test
    override fun testSortedIndex() = super.testSortedIndex()

    @Test
    override fun testSortedIndexBytes() = super.testSortedIndexBytes()

    @Test
    override fun testIndexMultipleKnnVectorFields() = super.testIndexMultipleKnnVectorFields()

    @Test
    override fun testRandom() = super.testRandom()

    @Test
    override fun testSearchWithVisitedLimit() = super.testSearchWithVisitedLimit()

    @Test
    override fun testRandomWithUpdatesAndGraph() = super.testRandomWithUpdatesAndGraph()

    @Test
    override fun testRandomBytes() = super.testRandomBytes()

    @Test
    override fun testCheckIndexIncludesVectors() = super.testCheckIndexIncludesVectors()

    @Test
    override fun testSimilarityFunctionIdentifiers() = super.testSimilarityFunctionIdentifiers()

    @Test
    override fun testVectorEncodingOrdinals() = super.testVectorEncodingOrdinals()

    @Test
    override fun testAdvance() = super.testAdvance()

    @Test
    override fun testVectorValuesReportCorrectDocs() = super.testVectorValuesReportCorrectDocs()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

    @Test
    override fun testRecall() = super.testRecall()

    private class WriteRecordingKnnVectorsFormat(private val delegate: KnnVectorsFormat) : KnnVectorsFormat(delegate.name) {
        val fieldsWritten: MutableSet<String> = HashSet()

        @Throws(IOException::class)
        override fun fieldsWriter(state: SegmentWriteState): KnnVectorsWriter {
            val writer = delegate.fieldsWriter(state)
            return object : KnnVectorsWriter() {
                @Throws(IOException::class)
                override fun addField(fieldInfo: FieldInfo): KnnFieldVectorsWriter<*> {
                    fieldsWritten.add(fieldInfo.name)
                    return writer.addField(fieldInfo)
                }

                @Throws(IOException::class)
                override fun flush(maxDoc: Int, sortMap: Sorter.DocMap?) {
                    writer.flush(maxDoc, sortMap)
                }

                @Throws(IOException::class)
                override fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
                    fieldsWritten.add(fieldInfo.name)
                    writer.mergeOneField(fieldInfo, mergeState)
                }

                @Throws(IOException::class)
                override fun finish() {
                    writer.finish()
                }

                @Throws(IOException::class)
                override fun close() {
                    writer.close()
                }

                override fun ramBytesUsed(): Long {
                    return writer.ramBytesUsed()
                }
            }
        }

        @Throws(IOException::class)
        override fun fieldsReader(state: SegmentReadState): KnnVectorsReader {
            return delegate.fieldsReader(state)
        }

        override fun getMaxDimensions(fieldName: String): Int {
            return DEFAULT_MAX_DIMENSIONS
        }
    }

    private class KnnVectorsFormatMaxDims32(private val delegate: KnnVectorsFormat) : KnnVectorsFormat(delegate.name) {
        @Throws(IOException::class)
        override fun fieldsWriter(state: SegmentWriteState): KnnVectorsWriter {
            return delegate.fieldsWriter(state)
        }

        @Throws(IOException::class)
        override fun fieldsReader(state: SegmentReadState): KnnVectorsReader {
            return delegate.fieldsReader(state)
        }

        override fun getMaxDimensions(fieldName: String): Int {
            return 32
        }
    }
}
