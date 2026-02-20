package org.gnit.lucenekmp.codecs.lucene99

import okio.IOException
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLucene99ScalarQuantizedVectorsWriter : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testBuildScalarQuantizerCosine() {
        assertScalarQuantizer(floatArrayOf(0.3234983f, 0.6236096f), 0.9f, 7, VectorSimilarityFunction.COSINE)
        assertScalarQuantizer(floatArrayOf(0.28759837f, 0.62449116f), 0f, 7, VectorSimilarityFunction.COSINE)
        assertScalarQuantizer(floatArrayOf(0.3234983f, 0.6236096f), 0.9f, 4, VectorSimilarityFunction.COSINE)
        assertScalarQuantizer(floatArrayOf(0.37247902f, 0.58848244f), 0f, 4, VectorSimilarityFunction.COSINE)
    }

    @Test
    @Throws(IOException::class)
    fun testBuildScalarQuantizerDotProduct() {
        assertScalarQuantizer(floatArrayOf(0.3234983f, 0.6236096f), 0.9f, 7, VectorSimilarityFunction.DOT_PRODUCT)
        assertScalarQuantizer(floatArrayOf(0.28759837f, 0.62449116f), 0f, 7, VectorSimilarityFunction.DOT_PRODUCT)
        assertScalarQuantizer(floatArrayOf(0.3234983f, 0.6236096f), 0.9f, 4, VectorSimilarityFunction.DOT_PRODUCT)
        assertScalarQuantizer(floatArrayOf(0.37247902f, 0.58848244f), 0f, 4, VectorSimilarityFunction.DOT_PRODUCT)
    }

    @Test
    @Throws(IOException::class)
    fun testBuildScalarQuantizerMIP() {
        assertScalarQuantizer(floatArrayOf(2.0f, 20.0f), 0.9f, 7, VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT)
        assertScalarQuantizer(floatArrayOf(2.4375f, 19.0625f), 0f, 7, VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT)
        assertScalarQuantizer(floatArrayOf(2.0f, 20.0f), 0.9f, 4, VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT)
        assertScalarQuantizer(floatArrayOf(2.6875f, 19.0625f), 0f, 4, VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT)
    }

    @Test
    @Throws(IOException::class)
    fun testBuildScalarQuantizerEuclidean() {
        assertScalarQuantizer(floatArrayOf(2.0f, 20.0f), 0.9f, 7, VectorSimilarityFunction.EUCLIDEAN)
        assertScalarQuantizer(floatArrayOf(2.125f, 19.375f), 0f, 7, VectorSimilarityFunction.EUCLIDEAN)
        assertScalarQuantizer(floatArrayOf(2.0f, 20.0f), 0.9f, 4, VectorSimilarityFunction.EUCLIDEAN)
        assertScalarQuantizer(floatArrayOf(2.1875f, 19.0625f), 0f, 4, VectorSimilarityFunction.EUCLIDEAN)
    }

    @Throws(IOException::class)
    private fun assertScalarQuantizer(
        expectedQuantiles: FloatArray,
        confidenceInterval: Float,
        bits: Int,
        vectorSimilarityFunction: VectorSimilarityFunction
    ) {
        val vectors = ArrayList<FloatArray>(30)
        for (i in 0..<30) {
            val vector = floatArrayOf(i.toFloat(), i + 1f, i + 2f, i + 3f)
            vectors.add(vector)
            if (vectorSimilarityFunction == VectorSimilarityFunction.DOT_PRODUCT) {
                VectorUtil.l2normalize(vector)
            }
        }
        val vectorValues: FloatVectorValues = FloatVectorValues.fromFloats(vectors, 4)
        val scalarQuantizer: ScalarQuantizer =
            Lucene99ScalarQuantizedVectorsWriter.buildScalarQuantizer(
                vectorValues,
                30,
                vectorSimilarityFunction,
                confidenceInterval,
                bits.toByte()
            )
        val context =
            "sim=$vectorSimilarityFunction bits=$bits confidenceInterval=$confidenceInterval " +
                "actualLower=${scalarQuantizer.lowerQuantile} actualUpper=${scalarQuantizer.upperQuantile}"
        assertEquals(expectedQuantiles[0], scalarQuantizer.lowerQuantile, 0.0001f, context)
        assertEquals(expectedQuantiles[1], scalarQuantizer.upperQuantile, 0.0001f, context)
    }
}
