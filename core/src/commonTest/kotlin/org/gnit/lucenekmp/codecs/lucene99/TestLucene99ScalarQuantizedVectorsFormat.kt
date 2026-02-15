package org.gnit.lucenekmp.codecs.lucene99

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TopKnnCollector
import org.gnit.lucenekmp.tests.index.BaseKnnVectorsFormatTestCase
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.quantization.QuantizedByteVectorValues
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TestLucene99ScalarQuantizedVectorsFormat : BaseKnnVectorsFormatTestCase() {
    private lateinit var format: KnnVectorsFormat
    private var confidenceInterval: Float? = null
    private var bits: Int = 7

    @BeforeTest
    fun setUpTestLucene99ScalarQuantizedVectorsFormat() {
        bits = if (random().nextBoolean()) 4 else 7
        confidenceInterval = if (random().nextBoolean()) 0.90f + (0.10f * random().nextFloat()) else null
        if (random().nextBoolean()) {
            confidenceInterval = 0f
        }
        format = Lucene99ScalarQuantizedVectorsFormat(
            confidenceInterval,
            bits,
            if (bits == 4) random().nextBoolean() else false
        )
    }

    override val codec: Codec
        get() {
            if (!::format.isInitialized) {
                setUpTestLucene99ScalarQuantizedVectorsFormat()
            }
            return TestUtil.alwaysKnnVectorsFormat(format)
        }

    @Test
    @Throws(Exception::class)
    fun testSearch() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                val doc = Document()
                doc.add(KnnFloatVectorField("f", floatArrayOf(0f, 1f), VectorSimilarityFunction.DOT_PRODUCT))
                w.addDocument(doc)
                w.commit()
                DirectoryReader.open(w).use { reader ->
                    val r: LeafReader = getOnlyLeafReader(reader)
                    if (r is CodecReader) {
                        val knnVectorsReader: KnnVectorsReader = r.vectorReader ?: fail("missing vectorReader")
                        val knnCollector = TopKnnCollector(1, Int.MAX_VALUE)
                        knnVectorsReader.search("f", floatArrayOf(1f, 0f), knnCollector, null)
                        assertTrue(knnCollector.topDocs().scoreDocs.isEmpty())
                    } else {
                        fail("reader is not CodecReader")
                    }
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testQuantizedVectorsWriteAndRead() {
        val numVectors = 1 + random().nextInt(50)
        val similarityFunction = randomSimilarity()
        val normalize = similarityFunction == VectorSimilarityFunction.COSINE
        var dim = random().nextInt(64) + 1
        if (dim % 2 == 1) {
            dim++
        }
        val vectors = ArrayList<FloatArray>(numVectors)
        for (i in 0 until numVectors) {
            vectors.add(randomVector(dim))
        }
        val expectedCorrections = FloatArray(numVectors)
        val expectedVectors = Array(numVectors) { ByteArray(dim) }
        val randomlyReusedVector = FloatArray(dim)

        newDirectory().use { dir ->
            IndexWriter(
                dir,
                IndexWriterConfig()
                    .setMaxBufferedDocs(numVectors + 1)
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                    .setMergePolicy(NoMergePolicy.INSTANCE)
            ).use { w ->
                for (i in 0 until numVectors) {
                    val doc = Document()
                    val v: FloatArray = if (random().nextBoolean()) {
                        vectors[i].copyInto(randomlyReusedVector)
                        randomlyReusedVector
                    } else {
                        vectors[i]
                    }
                    doc.add(KnnFloatVectorField("f", v, similarityFunction))
                    w.addDocument(doc)
                }
                w.commit()
                DirectoryReader.open(w).use { reader ->
                    val r: LeafReader = getOnlyLeafReader(reader)
                    if (r is CodecReader) {
                        var knnVectorsReader: KnnVectorsReader = r.vectorReader ?: fail("missing vectorReader")
                        if (knnVectorsReader is PerFieldKnnVectorsFormat.FieldsReader) {
                            knnVectorsReader = knnVectorsReader.getFieldReader("f") ?: fail("missing field reader for f")
                        }
                        if (knnVectorsReader is Lucene99ScalarQuantizedVectorsReader) {
                            val persistedQuantizer = assertNotNull(knnVectorsReader.getQuantizationState("f"))
                            for (i in 0 until numVectors) {
                                var vector = vectors[i]
                                if (normalize) {
                                    val copy = FloatArray(vector.size)
                                    vector.copyInto(copy)
                                    VectorUtil.l2normalize(copy)
                                    vector = copy
                                }
                                expectedCorrections[i] =
                                    persistedQuantizer.quantize(vector, expectedVectors[i], similarityFunction)
                            }
                            val quantizedByteVectorValues: QuantizedByteVectorValues =
                                knnVectorsReader.getQuantizedVectorValues("f")
                            val iter: KnnVectorValues.DocIndexIterator = quantizedByteVectorValues.iterator()
                            var docId = iter.nextDoc()
                            while (docId != DocIdSetIterator.NO_MORE_DOCS) {
                                val ord = iter.index()
                                val vector = quantizedByteVectorValues.vectorValue(ord)
                                val offset = quantizedByteVectorValues.getScoreCorrectionConstant(ord)
                                assertArrayEquals(expectedVectors[docId], vector)
                                assertEquals(expectedCorrections[docId], offset, 0.00001f)
                                docId = iter.nextDoc()
                            }
                        } else {
                            fail("reader is not Lucene99ScalarQuantizedVectorsReader")
                        }
                    } else {
                        fail("reader is not CodecReader")
                    }
                }
            }
        }
    }

    @Test
    fun testToString() {
        val customCodec: FilterCodec =
            object : FilterCodec("foo", default) {
                override fun knnVectorsFormat(): KnnVectorsFormat {
                    return Lucene99ScalarQuantizedVectorsFormat(0.9f, 4, false)
                }
            }
        val expectedPrefix =
            "Lucene99ScalarQuantizedVectorsFormat(name=Lucene99ScalarQuantizedVectorsFormat, confidenceInterval=0.9, bits=4, compress=false, flatVectorScorer=ScalarQuantizedVectorScorer(nonQuantizedDelegate="
        val expectedMiddle = "()), rawVectorFormat=Lucene99FlatVectorsFormat(vectorsScorer="
        val expectedSuffix = "()))"
        val defaultScorerName = "DefaultFlatVectorScorer"
        val memSegScorerName = "Lucene99MemorySegmentFlatVectorsScorer"
        val defaultScorer =
            "$expectedPrefix${defaultScorerName}$expectedMiddle${defaultScorerName}$expectedSuffix"
        val memSegScorer =
            "$expectedPrefix${memSegScorerName}$expectedMiddle${memSegScorerName}$expectedSuffix"
        val actual = customCodec.knnVectorsFormat().toString()
        assertTrue(actual == defaultScorer || actual == memSegScorer, "Unexpected format: $actual")
    }

    @Test
    fun testLimits() {
        assertFailsWith<IllegalArgumentException> { Lucene99ScalarQuantizedVectorsFormat(1.1f, 7, false) }
        assertFailsWith<IllegalArgumentException> { Lucene99ScalarQuantizedVectorsFormat(null, -1, false) }
        assertFailsWith<IllegalArgumentException> { Lucene99ScalarQuantizedVectorsFormat(null, 5, false) }
        assertFailsWith<IllegalArgumentException> { Lucene99ScalarQuantizedVectorsFormat(null, 9, false) }
    }

    override fun testRecall() {
        // ignore this test since this class always returns no results from search
    }

    override fun testRandomWithUpdatesAndGraph() {
        // graph not supported
    }

    override fun testSearchWithVisitedLimit() {
        // search not supported
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

    private class FloatVectorWrapper(private val vectors: List<FloatArray>) : FloatVectorValues() {
        override fun dimension(): Int {
            return if (vectors.isEmpty()) 0 else vectors[0].size
        }

        override fun size(): Int {
            return vectors.size
        }

        override fun vectorValue(ord: Int): FloatArray {
            return vectors[ord]
        }

        override fun copy(): FloatVectorValues {
            return FloatVectorWrapper(vectors)
        }

        override fun iterator(): KnnVectorValues.DocIndexIterator {
            return createDenseIterator()
        }
    }
}
