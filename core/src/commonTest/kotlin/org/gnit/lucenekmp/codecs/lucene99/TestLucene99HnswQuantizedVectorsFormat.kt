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
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.KnnFloatVectorQuery
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TopKnnCollector
import org.gnit.lucenekmp.tests.index.BaseKnnVectorsFormatTestCase
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.SameThreadExecutorService
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

class TestLucene99HnswQuantizedVectorsFormat : BaseKnnVectorsFormatTestCase() {
    private lateinit var format: KnnVectorsFormat
    private var confidenceInterval: Float? = null
    private var bits: Int = 7

    @BeforeTest
    fun setUpTestLucene99HnswQuantizedVectorsFormat() {
        initRandomizedFormat()
    }

    private fun initRandomizedFormat() {
        bits = if (random().nextBoolean()) 4 else 7
        confidenceInterval = if (random().nextBoolean()) 0.90f + (0.10f * random().nextFloat()) else null
        if (random().nextBoolean()) {
            confidenceInterval = 0f
        }
        format = getKnnFormat(bits)
    }

    override val codec: Codec
        get() {
            if (!::format.isInitialized) {
                initRandomizedFormat()
            }
            return TestUtil.alwaysKnnVectorsFormat(format)
        }

    private fun getKnnFormat(bits: Int): KnnVectorsFormat {
        return Lucene99HnswScalarQuantizedVectorsFormat(
            Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN,
            Lucene99HnswVectorsFormat.DEFAULT_BEAM_WIDTH,
            1,
            bits,
            if (bits == 4) random().nextBoolean() else false,
            confidenceInterval,
            null
        )
    }

    @Test
    @Throws(Exception::class)
    fun testMixedQuantizedBits() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.alwaysKnnVectorsFormat(getKnnFormat(4)))).use { w ->
                val doc = Document()
                doc.add(KnnFloatVectorField("f", floatArrayOf(0.6f, 0.8f), VectorSimilarityFunction.DOT_PRODUCT))
                w.addDocument(doc)
            }

            IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.alwaysKnnVectorsFormat(getKnnFormat(7)))).use { w ->
                val doc = Document()
                doc.add(KnnFloatVectorField("f", floatArrayOf(0.8f, 0.6f), VectorSimilarityFunction.DOT_PRODUCT))
                w.addDocument(doc)
                w.forceMerge(1)
            }

            DirectoryReader.open(dir).use { reader ->
                val searcher = newSearcher(reader)
                val q = KnnFloatVectorQuery("f", floatArrayOf(0.7f, 0.7f), 10)
                val topDocs = searcher.search(q, 100)
                assertEquals(2, topDocs.totalHits.value)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMixedQuantizedUnQuantized() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.alwaysKnnVectorsFormat(Lucene99HnswVectorsFormat()))).use { w ->
                val doc = Document()
                doc.add(KnnFloatVectorField("f", floatArrayOf(0.6f, 0.8f), VectorSimilarityFunction.DOT_PRODUCT))
                w.addDocument(doc)
            }

            IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.alwaysKnnVectorsFormat(getKnnFormat(7)))).use { w ->
                val doc = Document()
                doc.add(KnnFloatVectorField("f", floatArrayOf(0.8f, 0.6f), VectorSimilarityFunction.DOT_PRODUCT))
                w.addDocument(doc)
                w.forceMerge(1)
            }

            DirectoryReader.open(dir).use { reader ->
                val searcher = newSearcher(reader)
                val q = KnnFloatVectorQuery("f", floatArrayOf(0.7f, 0.7f), 10)
                val topDocs = searcher.search(q, 100)
                assertEquals(2, topDocs.totalHits.value)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testQuantizationScoringEdgeCase() {
        val vectors = arrayOf(floatArrayOf(0.6f, 0.8f), floatArrayOf(0.8f, 0.6f), floatArrayOf(-0.6f, -0.8f))
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                newIndexWriterConfig().setCodec(
                    TestUtil.alwaysKnnVectorsFormat(
                        Lucene99HnswScalarQuantizedVectorsFormat(16, 100, 1, 7, false, 0.9f, null)
                    )
                )
            ).use { w ->
                for (vector in vectors) {
                    val doc = Document()
                    doc.add(KnnFloatVectorField("f", vector, VectorSimilarityFunction.DOT_PRODUCT))
                    w.addDocument(doc)
                    w.commit()
                }
                w.forceMerge(1)
                DirectoryReader.open(w).use { reader ->
                    val r: LeafReader = getOnlyLeafReader(reader)
                    val topKnnCollector = TopKnnCollector(5, Int.MAX_VALUE)
                    r.searchNearestVectors("f", floatArrayOf(0.6f, 0.8f), topKnnCollector, null)
                    val topDocs = topKnnCollector.topDocs()
                    assertEquals(3, topDocs.totalHits.value)
                    for (scoreDoc: ScoreDoc in topDocs.scoreDocs) {
                        assertTrue(scoreDoc.score >= 0f)
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
        var dim = random().nextInt(64) + 1
        if (dim % 2 == 1) {
            dim++
        }
        val vectors = ArrayList<FloatArray>(numVectors)
        for (i in 0 until numVectors) {
            vectors.add(randomVector(dim))
        }
        val toQuantize: FloatVectorValues = FloatVectorWrapper(vectors)
        val scalarQuantizer: ScalarQuantizer = Lucene99ScalarQuantizedVectorsWriter.buildScalarQuantizer(
            toQuantize,
            numVectors,
            similarityFunction,
            confidenceInterval,
            bits.toByte()
        )
        val expectedCorrections = FloatArray(numVectors)
        val expectedVectors = Array(numVectors) { ByteArray(dim) }
        for (i in 0 until numVectors) {
            var vector = vectors[i]
            if (similarityFunction == VectorSimilarityFunction.COSINE) {
                val copy = FloatArray(vector.size)
                vector.copyInto(copy)
                VectorUtil.l2normalize(copy)
                vector = copy
            }
            expectedCorrections[i] = scalarQuantizer.quantize(vector, expectedVectors[i], similarityFunction)
        }
        val randomlyReusedVector = FloatArray(dim)

        newDirectory().use { dir ->
            IndexWriter(
                dir,
                IndexWriterConfig()
                    .setMaxBufferedDocs(numVectors + 1)
                    .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setCodec(TestUtil.alwaysKnnVectorsFormat(getKnnFormat(bits)))
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
                        if (knnVectorsReader is Lucene99HnswVectorsReader) {
                            assertNotNull(knnVectorsReader.getQuantizationState("f"))
                            val quantizedByteVectorValues: QuantizedByteVectorValues =
                                knnVectorsReader.getQuantizedVectorValues("f") ?: fail("missing quantized vector values")
                            for (ord in 0 until quantizedByteVectorValues.size()) {
                                val vector = quantizedByteVectorValues.vectorValue(ord)
                                val offset = quantizedByteVectorValues.getScoreCorrectionConstant(ord)
                                assertArrayEquals(expectedVectors[ord], vector)
                                assertEquals(expectedCorrections[ord], offset, 0.00001f)
                            }
                        } else {
                            fail("reader is not Lucene99HnswVectorsReader")
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
                    return Lucene99HnswScalarQuantizedVectorsFormat(10, 20, 1, 4, false, 0.9f, null)
                }
            }

        val expectedPrefix =
            "Lucene99HnswScalarQuantizedVectorsFormat(name=Lucene99HnswScalarQuantizedVectorsFormat, maxConn=10, beamWidth=20, flatVectorFormat=Lucene99ScalarQuantizedVectorsFormat(name=Lucene99ScalarQuantizedVectorsFormat, confidenceInterval=0.9, bits=4, compress=false, flatVectorScorer=ScalarQuantizedVectorScorer(nonQuantizedDelegate="
        val expectedMiddle = "()), rawVectorFormat=Lucene99FlatVectorsFormat(vectorsScorer="
        val expectedSuffix = "())))"
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
        assertFailsWith<IllegalArgumentException> { Lucene99HnswScalarQuantizedVectorsFormat(-1, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswScalarQuantizedVectorsFormat(0, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswScalarQuantizedVectorsFormat(20, 0) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswScalarQuantizedVectorsFormat(20, -1) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswScalarQuantizedVectorsFormat(513, 20) }
        assertFailsWith<IllegalArgumentException> { Lucene99HnswScalarQuantizedVectorsFormat(20, 3201) }
        assertFailsWith<IllegalArgumentException> {
            Lucene99HnswScalarQuantizedVectorsFormat(20, 100, 0, 7, false, 1.1f, null)
        }
        assertFailsWith<IllegalArgumentException> {
            Lucene99HnswScalarQuantizedVectorsFormat(20, 100, 0, -1, false, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            Lucene99HnswScalarQuantizedVectorsFormat(20, 100, 0, 5, false, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            Lucene99HnswScalarQuantizedVectorsFormat(20, 100, 0, 9, false, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            Lucene99HnswScalarQuantizedVectorsFormat(20, 100, 0, 7, false, 0.8f, null)
        }
        assertFailsWith<IllegalArgumentException> {
            Lucene99HnswScalarQuantizedVectorsFormat(20, 100, 1, 7, false, null, SameThreadExecutorService())
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

    @Test
    override fun testRecall() = super.testRecall()

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

        override fun iterator(): DocIndexIterator {
            return createDenseIterator()
        }
    }
}
