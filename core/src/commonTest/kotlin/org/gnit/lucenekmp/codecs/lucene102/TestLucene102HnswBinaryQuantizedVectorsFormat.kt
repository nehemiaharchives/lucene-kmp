package org.gnit.lucenekmp.codecs.lucene102

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.tests.index.BaseKnnVectorsFormatTestCase
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.SameThreadExecutorService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestLucene102HnswBinaryQuantizedVectorsFormat : BaseKnnVectorsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.alwaysKnnVectorsFormat(FORMAT)

    @Test
    fun testToString() {
        val customCodec: FilterCodec =
            object : FilterCodec("foo", default) {
                override fun knnVectorsFormat(): KnnVectorsFormat {
                    return Lucene102HnswBinaryQuantizedVectorsFormat(10, 20, 1, null)
                }
            }

        val defaultScorerName = "DefaultFlatVectorScorer"
        val memSegScorerName = "Lucene99MemorySegmentFlatVectorsScorer"
        val defaultScorer =
            "Lucene102HnswBinaryQuantizedVectorsFormat(name=Lucene102HnswBinaryQuantizedVectorsFormat, " +
                "maxConn=10, beamWidth=20, flatVectorFormat=" +
                "Lucene102BinaryQuantizedVectorsFormat(name=Lucene102BinaryQuantizedVectorsFormat, " +
                "flatVectorScorer=Lucene102BinaryFlatVectorsScorer(nonQuantizedDelegate=${defaultScorerName}()), " +
                "rawVectorFormat=Lucene99FlatVectorsFormat(vectorsScorer=${defaultScorerName}())))"
        val memSegScorer =
            "Lucene102HnswBinaryQuantizedVectorsFormat(name=Lucene102HnswBinaryQuantizedVectorsFormat, " +
                "maxConn=10, beamWidth=20, flatVectorFormat=" +
                "Lucene102BinaryQuantizedVectorsFormat(name=Lucene102BinaryQuantizedVectorsFormat, " +
                "flatVectorScorer=Lucene102BinaryFlatVectorsScorer(nonQuantizedDelegate=${memSegScorerName}()), " +
                "rawVectorFormat=Lucene99FlatVectorsFormat(vectorsScorer=${memSegScorerName}())))"
        val actual = customCodec.knnVectorsFormat().toString()
        assertTrue(actual == defaultScorer || actual == memSegScorer, "Unexpected format: $actual")
    }

    @Test
    @Throws(Exception::class)
    fun testSingleVectorCase() {
        val vector = randomVector(random().nextInt(12, 500))
        for (similarityFunction in VectorSimilarityFunction.entries) {
            newDirectory().use { dir ->
                newIndexWriterConfig().let { iwc ->
                    IndexWriter(dir, iwc).use { w ->
                        val doc = Document()
                        doc.add(KnnFloatVectorField("f", vector, similarityFunction))
                        w.addDocument(doc)
                        w.commit()
                        DirectoryReader.open(w).use { reader ->
                            val r: LeafReader = getOnlyLeafReader(reader)
                            val vectorValues: FloatVectorValues = r.getFloatVectorValues("f")!!
                            val docIndexIterator: KnnVectorValues.DocIndexIterator = vectorValues.iterator()
                            assertEquals(1, vectorValues.size())
                            while (docIndexIterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                assertArrayEquals(vector, vectorValues.vectorValue(docIndexIterator.index()), 0.00001f)
                            }
                            val randomVector = randomVector(vector.size)
                            val td: TopDocs = r.searchNearestVectors("f", randomVector, 1, null, Int.MAX_VALUE)
                            assertEquals(1, td.totalHits.value)
                            assertTrue(td.scoreDocs[0].score >= 0)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testLimits() {
        assertFailsWith<IllegalArgumentException> { Lucene102HnswBinaryQuantizedVectorsFormat(-1, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene102HnswBinaryQuantizedVectorsFormat(0, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene102HnswBinaryQuantizedVectorsFormat(20, 0) }
        assertFailsWith<IllegalArgumentException> { Lucene102HnswBinaryQuantizedVectorsFormat(20, -1) }
        assertFailsWith<IllegalArgumentException> { Lucene102HnswBinaryQuantizedVectorsFormat(513, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene102HnswBinaryQuantizedVectorsFormat(20, 3201) }
        assertFailsWith<IllegalArgumentException> {
            Lucene102HnswBinaryQuantizedVectorsFormat(20, 100, 1, SameThreadExecutorService())
        }
    }

    @Test
    fun testVectorSimilarityFuncs() {
        val expectedValues = VectorSimilarityFunction.entries.toList()
        assertEquals(Lucene99HnswVectorsReader.SIMILARITY_FUNCTIONS, expectedValues)
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


    companion object {
        private val FORMAT: KnnVectorsFormat = Lucene102HnswBinaryQuantizedVectorsFormat()
    }
}
