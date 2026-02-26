package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.document.KnnByteVectorField
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.assert
import kotlin.test.BeforeTest

/** Tests HNSW KNN graphs */
class TestHnswByteVectorGraph : HnswGraphTestCase<ByteArray>() {

    @BeforeTest
    fun setup() {
        val values = VectorSimilarityFunction.entries.toTypedArray()
        similarityFunction = values[random().nextInt(values.size)]
    }

    override fun getVectorEncoding(): VectorEncoding {
        return VectorEncoding.BYTE
    }

    override fun randomVector(dim: Int): ByteArray {
        return randomVector8(random(), dim)
    }

    override fun vectorValues(size: Int, dimension: Int): KnnVectorValues {
        val vectors = Array(size) { randomVector8(random(), dimension) }
        return ByteVectorValues.fromBytes(vectors.toMutableList(), dimension)
    }

    private fun fitsInByte(v: Float): Boolean {
        return v <= 127 && v >= -128 && v % 1 == 0f
    }

    override fun vectorValues(values: Array<FloatArray>): KnnVectorValues {
        val bValues = Array(values.size) { ByteArray(values[it].size) }
        // The case when all floats fit within a byte already.
        val scaleSimple = fitsInByte(values[0][0])
        for (i in values.indices) {
            for (j in values[i].indices) {
                val v = if (scaleSimple) {
                    assert(fitsInByte(values[i][j]))
                    values[i][j]
                } else {
                    values[i][j] * 127
                }
                bValues[i][j] = v.toInt().toByte()
            }
        }
        return ByteVectorValues.fromBytes(bValues.toMutableList(), bValues[0].size)
    }

    override fun vectorValues(
        size: Int,
        dimension: Int,
        pregeneratedVectorValues: KnnVectorValues,
        pregeneratedOffset: Int
    ): KnnVectorValues {
        val pvv = pregeneratedVectorValues as ByteVectorValues
        val vectors = Array(size) { ByteArray(dimension) }
        val randomVectors = Array(size - pvv.size()) { randomVector8(random(), dimension) }

        for (i in 0 until pregeneratedOffset) {
            vectors[i] = randomVectors[i]
        }

        for (currentOrd in 0 until pvv.size()) {
            vectors[pregeneratedOffset + currentOrd] = pvv.vectorValue(currentOrd).copyOf()
        }

        for (i in pregeneratedOffset + pvv.size() until vectors.size) {
            vectors[i] = randomVectors[i - pvv.size()]
        }

        return ByteVectorValues.fromBytes(vectors.toMutableList(), dimension)
    }

    override fun vectorValues(reader: KnnVectorValues, fieldName: String): KnnVectorValues {
        val vectorValues = reader as ByteVectorValues
        val vectors = Array(vectorValues.size()) { ByteArray(vectorValues.dimension()) }
        for (i in 0 until vectorValues.size()) {
            vectors[i] = vectorValues.vectorValue(i).copyOf()
        }
        return ByteVectorValues.fromBytes(vectors.toMutableList(), vectorValues.dimension())
    }

    override fun knnVectorField(name: String, vector: ByteArray, similarityFunction: VectorSimilarityFunction): Any {
        return KnnByteVectorField(name, vector, similarityFunction)
    }

    override fun circularVectorValues(nDoc: Int): KnnVectorValues {
        return CircularByteVectorValues(nDoc)
    }

    override fun getTargetVector(): ByteArray {
        return byteArrayOf(1, 0)
    }
}
