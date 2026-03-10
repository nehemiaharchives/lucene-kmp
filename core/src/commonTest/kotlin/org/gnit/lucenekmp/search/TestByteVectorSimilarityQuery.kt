package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.KnnByteVectorField
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.util.TestVectorUtil
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestByteVectorSimilarityQuery :
    BaseVectorSimilarityQueryTestCase<ByteArray, KnnByteVectorField, ByteVectorSimilarityQuery>() {
    @BeforeTest
    fun setup() {
        vectorField = this::class.simpleName + ":VectorField"
        idField = this::class.simpleName + ":IdField"
        function = VectorSimilarityFunction.EUCLIDEAN
        numDocs = atLeast(100)
        dim = atLeast(5)
    }

    override fun getRandomVector(dim: Int): ByteArray {
        return TestVectorUtil.randomVectorBytes(dim)
    }

    override fun compare(vector1: ByteArray, vector2: ByteArray): Float {
        return function.compare(vector1, vector2)
    }

    override fun checkEquals(vector1: ByteArray, vector2: ByteArray): Boolean {
        return vector1.contentEquals(vector2)
    }

    override fun getVectorField(
        name: String,
        vector: ByteArray,
        function: VectorSimilarityFunction
    ): KnnByteVectorField {
        return KnnByteVectorField(name, vector, function)
    }

    override fun getVectorQuery(
        field: String,
        vector: ByteArray,
        traversalSimilarity: Float,
        resultSimilarity: Float,
        filter: Query?
    ): ByteVectorSimilarityQuery {
        return ByteVectorSimilarityQuery(field, vector, traversalSimilarity, resultSimilarity, filter)
    }

    override fun getThrowingVectorQuery(
        field: String,
        vector: ByteArray,
        traversalSimilarity: Float,
        resultSimilarity: Float,
        filter: Query?
    ): ByteVectorSimilarityQuery {
        return object : ByteVectorSimilarityQuery(field, vector, traversalSimilarity, resultSimilarity, filter) {
            override fun createVectorScorer(context: LeafReaderContext): VectorScorer {
                throw UnsupportedOperationException()
            }
        }
    }

    // tests inherited from BaseVectorSimilarityQueryTestCase

    @Test
    override fun testEquals() = super.testEquals()

    @Test
    @Throws(IOException::class)
    override fun testEmptyIndex() = super.testEmptyIndex()

    @Test
    @Throws(IOException::class)
    override fun testExtremes() = super.testExtremes()

    @Test
    @Throws(IOException::class)
    override fun testRandomFilter() = super.testRandomFilter()

    @Test
    @Throws(IOException::class)
    override fun testFilterWithNoMatches() = super.testFilterWithNoMatches()

    @Test
    @Throws(IOException::class)
    override fun testDimensionMismatch() = super.testDimensionMismatch()

    @Test
    @Throws(IOException::class)
    override fun testNonVectorsField() = super.testNonVectorsField()

    @Test
    @Throws(IOException::class)
    override fun testSomeDeletes() = super.testSomeDeletes()

    @Test
    @Throws(IOException::class)
    override fun testAllDeletes() = super.testAllDeletes()

    @Test
    @Throws(IOException::class)
    override fun testBoostQuery() = super.testBoostQuery()

    @Test
    @Throws(IOException::class)
    override fun testVectorsAboveSimilarity() = super.testVectorsAboveSimilarity()

    @Test
    @Throws(IOException::class)
    override fun testFallbackToExact() = super.testFallbackToExact()

    @Test
    @Throws(IOException::class)
    override fun testApproximate() = super.testApproximate()

    @Test
    @Throws(IOException::class)
    override fun testTimeout() = super.testTimeout()
}
