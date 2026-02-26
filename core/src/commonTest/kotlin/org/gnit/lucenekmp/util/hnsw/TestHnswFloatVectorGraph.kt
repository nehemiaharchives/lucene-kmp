package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.KnnFloatVectorQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests HNSW KNN graphs */
class TestHnswFloatVectorGraph : HnswGraphTestCase<FloatArray>() {

    @BeforeTest
    fun setup() {
        val values = VectorSimilarityFunction.entries.toTypedArray()
        similarityFunction = values[random().nextInt(values.size)]
    }

    override fun getVectorEncoding(): VectorEncoding {
        return VectorEncoding.FLOAT32
    }

    override fun randomVector(dim: Int): FloatArray {
        return randomVector(random(), dim)
    }

    override fun vectorValues(size: Int, dimension: Int): KnnVectorValues {
        val vectors = Array(size) { randomVector(random(), dimension) }
        return FloatVectorValues.fromFloats(vectors.toMutableList(), dimension)
    }

    override fun vectorValues(values: Array<FloatArray>): KnnVectorValues {
        return FloatVectorValues.fromFloats(values.toMutableList(), values[0].size)
    }

    override fun vectorValues(
        size: Int,
        dimension: Int,
        pregeneratedVectorValues: KnnVectorValues,
        pregeneratedOffset: Int
    ): KnnVectorValues {
        val pvv = pregeneratedVectorValues as FloatVectorValues
        val vectors = Array(size) { FloatArray(dimension) }
        val randomVectors = Array(size - pvv.size()) { randomVector(random(), dimension) }

        for (i in 0 until pregeneratedOffset) {
            vectors[i] = randomVectors[i]
        }

        for (currentOrd in 0 until pvv.size()) {
            vectors[pregeneratedOffset + currentOrd] = pvv.vectorValue(currentOrd).copyOf()
        }

        for (i in pregeneratedOffset + pvv.size() until vectors.size) {
            vectors[i] = randomVectors[i - pvv.size()]
        }

        return FloatVectorValues.fromFloats(vectors.toMutableList(), dimension)
    }

    override fun vectorValues(reader: LeafReader, fieldName: String): KnnVectorValues {
        val vectorValues = reader.getFloatVectorValues(fieldName)!!
        val vectors = Array(vectorValues.size()) { FloatArray(vectorValues.dimension()) }
        for (i in 0 until vectorValues.size()) {
            vectors[i] = vectorValues.vectorValue(i).copyOf()
        }
        return FloatVectorValues.fromFloats(vectors.toMutableList(), vectorValues.dimension())
    }

    override fun knnVectorField(name: String, vector: FloatArray, similarityFunction: VectorSimilarityFunction): Field {
        return KnnFloatVectorField(name, vector, similarityFunction)
    }

    override fun knnQuery(field: String, vector: FloatArray, k: Int): Query {
        return KnnFloatVectorQuery(field, vector, k)
    }

    override fun circularVectorValues(nDoc: Int): KnnVectorValues {
        return CircularFloatVectorValues(nDoc)
    }

    override fun getTargetVector(): FloatArray {
        return floatArrayOf(1f, 0f)
    }

    @Test
    fun testSearchWithSkewedAcceptOrds() {
        val nDoc = 1000
        similarityFunction = VectorSimilarityFunction.EUCLIDEAN
        val vectors = circularVectorValues(nDoc) as FloatVectorValues
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 16, 100, random().nextLong())
        val hnsw = builder.build(vectors.size())

        // Skip over half of the documents that are closest to the query vector
        val acceptOrds = FixedBitSet(nDoc)
        for (i in 500 until nDoc) {
            acceptOrds.set(i)
        }
        val nn = HnswGraphSearcher.search(buildScorer(vectors, getTargetVector()), 10, hnsw, acceptOrds, Int.MAX_VALUE)

        val nodes = nn.topDocs()
        assertEquals(10, nodes.scoreDocs!!.size, "Number of found results is not equal to [10].")
        var sum = 0
        for (node in nodes.scoreDocs!!) {
            assertTrue(acceptOrds.get(node.doc), "the results include a deleted document: $node")
            sum += node.doc
        }
        // We still expect to get reasonable recall. The lowest non-skipped docIds
        // are closest to the query vector: sum(500,509) = 5045
        assertTrue(sum < 5100, "sum(result docs)=$sum")
    }

    // tests inherited from HnswGraphTestCase
    @Test override fun testRandomReadWriteAndMerge() = super.testRandomReadWriteAndMerge()
    @Test override fun testReadWrite() = super.testReadWrite()
    @Test override fun testRandom() = super.testRandom()
    @Test override fun testAknnDiverse() = super.testAknnDiverse()
    @Test override fun testSearchWithAcceptOrds() = super.testSearchWithAcceptOrds()
    @Test override fun testSearchWithSelectiveAcceptOrds() = super.testSearchWithSelectiveAcceptOrds()
    @Test override fun testHnswGraphBuilderInvalid() = super.testHnswGraphBuilderInvalid()
    @Test override fun testRamUsageEstimate() = super.testRamUsageEstimate()
    @Test override fun testSortedAndUnsortedIndicesReturnSameResults() = super.testSortedAndUnsortedIndicesReturnSameResults()
    @Test override fun testHnswGraphBuilderInitializationFromGraph_withOffsetZero() = super.testHnswGraphBuilderInitializationFromGraph_withOffsetZero()
    @Test override fun testHnswGraphBuilderInitializationFromGraph_withNonZeroOffset() = super.testHnswGraphBuilderInitializationFromGraph_withNonZeroOffset()
    @Test override fun testVisitedLimit() = super.testVisitedLimit()
    @Test override fun testFindAll() = super.testFindAll()
    @Test override fun testDiversity() = super.testDiversity()
    @Test override fun testDiversityFallback() = super.testDiversityFallback()
    @Test override fun testDiversity3d() = super.testDiversity3d()
    @Test override fun testOnHeapHnswGraphSearch() = super.testOnHeapHnswGraphSearch()
    @Test override fun testConcurrentMergeBuilder() = super.testConcurrentMergeBuilder()
    @Test override fun testAllNodesVisitedInSingleLevel() = super.testAllNodesVisitedInSingleLevel()
}
