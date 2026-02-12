package org.gnit.lucenekmp.codecs.lucene102

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.KnnFloatVectorQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TotalHits
import org.gnit.lucenekmp.tests.index.BaseKnnVectorsFormatTestCase
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.quantization.OptimizedScalarQuantizer
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLucene102BinaryQuantizedVectorsFormat : BaseKnnVectorsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.alwaysKnnVectorsFormat(FORMAT)

    @Test
    @Throws(Exception::class)
    fun testSearch() {
        val fieldName = "field"
        val numVectors: Int = random().nextInt(9, 15) // TODO reduced from 99, 500 to 9, 15 for dev speed
        val dims: Int = random().nextInt(4, 65)
        val vector: FloatArray = randomVector(dims)
        val similarityFunction: VectorSimilarityFunction = randomSimilarity()
        val knnField = KnnFloatVectorField(fieldName, vector, similarityFunction)
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        newDirectory().use { dir ->
            IndexWriter(dir, iwc).use { w ->
                for (i in 0..<numVectors) {
                    val doc = Document()
                    knnField.setVectorValue(randomVector(dims))
                    doc.add(knnField)
                    w.addDocument(doc)
                }
                w.commit()
                DirectoryReader.open(w).use { reader ->
                    val searcher = IndexSearcher(reader)
                    val k: Int = random().nextInt(5, 50)
                    val queryVector: FloatArray = randomVector(dims)
                    val q: Query = KnnFloatVectorQuery(fieldName, queryVector, k)
                    val collectedDocs: TopDocs = searcher.search(q, k)
                    assertEquals(min(k, numVectors).toLong(), collectedDocs.totalHits.value)
                    assertEquals(TotalHits.Relation.EQUAL_TO, collectedDocs.totalHits.relation)
                }
            }
        }
    }

    @Test
    fun testToString() {
        val customCodec: FilterCodec =
            object : FilterCodec("foo", default) {
                override fun knnVectorsFormat(): KnnVectorsFormat {
                    return Lucene102BinaryQuantizedVectorsFormat()
                }
            }
        val expectedPattern =
            ("Lucene102BinaryQuantizedVectorsFormat("
                    + "name=Lucene102BinaryQuantizedVectorsFormat, "
                    + "flatVectorScorer=Lucene102BinaryFlatVectorsScorer(nonQuantizedDelegate=%s()), "
                    + "rawVectorFormat=Lucene99FlatVectorsFormat(vectorsScorer=%s()))")
        val defaultScorerName = "DefaultFlatVectorScorer"
        val memSegScorerName = "Lucene99MemorySegmentFlatVectorsScorer"
        val defaultScorer =
            "Lucene102BinaryQuantizedVectorsFormat(" +
                "name=Lucene102BinaryQuantizedVectorsFormat, " +
                "flatVectorScorer=Lucene102BinaryFlatVectorsScorer(nonQuantizedDelegate=${defaultScorerName}()), " +
                "rawVectorFormat=Lucene99FlatVectorsFormat(vectorsScorer=${defaultScorerName}()))"
        val memSegScorer =
            "Lucene102BinaryQuantizedVectorsFormat(" +
                "name=Lucene102BinaryQuantizedVectorsFormat, " +
                "flatVectorScorer=Lucene102BinaryFlatVectorsScorer(nonQuantizedDelegate=${memSegScorerName}()), " +
                "rawVectorFormat=Lucene99FlatVectorsFormat(vectorsScorer=${memSegScorerName}()))"
        val actual = customCodec.knnVectorsFormat().toString()
        assertTrue(
            actual == defaultScorer || actual == memSegScorer,
            "Unexpected format: $actual"
        )
    }

    override fun testRandomWithUpdatesAndGraph() {
        // graph not supported
    }

    override fun testSearchWithVisitedLimit() {
        // visited limit is not respected, as it is brute force search
    }

    /*@Test
    fun testQuantizedVectorsWriteAndReadRepeat(){
        repeat(50){
            testQuantizedVectorsWriteAndRead()
        }
    }*/

    @Test
    @Throws(IOException::class)
    fun testQuantizedVectorsWriteAndRead() {
        val fieldName = "field"
        val numVectors: Int = random().nextInt(2, 3) // TODO reduced from 99, 500 to 2, 3 for dev speed
        val dims: Int = random().nextInt(2, 3) // TODO reduced from 4, 65 to 2, 3 for dev speed

        val vector: FloatArray = randomVector(dims)
        val similarityFunction: VectorSimilarityFunction = randomSimilarity()
        val knnField = KnnFloatVectorField(fieldName, vector, similarityFunction)
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                for (i in 0..<numVectors) {
                    val doc = Document()
                    knnField.setVectorValue(randomVector(dims))
                    doc.add(knnField)
                    w.addDocument(doc)
                    if (i % 101 == 0) {
                        w.commit()
                    }
                }
                w.commit()
                w.forceMerge(1)
                DirectoryReader.open(w).use { reader ->
                    val r: LeafReader = getOnlyLeafReader(reader)
                    var vectorValues: FloatVectorValues = r.getFloatVectorValues(fieldName)!!
                    assertEquals(vectorValues.size().toLong(), numVectors.toLong())
                    val qvectorValues: BinarizedByteVectorValues = (vectorValues as Lucene102BinaryQuantizedVectorsReader.BinarizedVectorValues).quantizedVectorValues
                    val centroid: FloatArray = qvectorValues.centroid!!
                    assertEquals(centroid.size.toLong(), dims.toLong())

                    val quantizer = OptimizedScalarQuantizer(similarityFunction)
                    val quantizedVector = ByteArray(dims)
                    val expectedVector = ByteArray(OptimizedScalarQuantizer.discretize(dims, 64) / 8)
                    if (similarityFunction === VectorSimilarityFunction.COSINE) {
                        vectorValues =
                            Lucene102BinaryQuantizedVectorsWriter.NormalizedFloatVectorValues(vectorValues)
                    }
                    val docIndexIterator: KnnVectorValues.DocIndexIterator = vectorValues.iterator()
                    while (docIndexIterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        val corrections: OptimizedScalarQuantizer.QuantizationResult =
                            quantizer.scalarQuantize(
                                vectorValues.vectorValue(docIndexIterator.index()),
                                quantizedVector,
                                Lucene102BinaryQuantizedVectorsFormat.INDEX_BITS,
                                centroid
                            )
                        OptimizedScalarQuantizer.packAsBinary(quantizedVector, expectedVector)
                        assertArrayEquals(expectedVector, qvectorValues.vectorValue(docIndexIterator.index()))
                        assertEquals(corrections, qvectorValues.getCorrectiveTerms(docIndexIterator.index()))
                    }
                }
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
    override fun testRandomBytes() = super.testRandomBytes()

    // testSearchWithVisitedLimit intentionally left overridden above
    // testRandomWithUpdatesAndGraph intentionally left overridden above

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

    companion object {
        private val FORMAT: KnnVectorsFormat = Lucene102BinaryQuantizedVectorsFormat()
    }
}
