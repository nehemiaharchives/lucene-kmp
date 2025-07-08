package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.VectorScorer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue

/**
 * Partial port of Lucene's HnswGraphTestCase.
 * Only a subset of helper methods and tests are implemented so far.
 */
abstract class HnswGraphTestCase<T> : LuceneTestCase() {
    protected lateinit var similarityFunction: VectorSimilarityFunction
    protected val flatVectorScorer = DefaultFlatVectorScorer()

    abstract fun getVectorEncoding(): VectorEncoding
    abstract fun randomVector(dim: Int): T
    abstract fun vectorValues(size: Int, dimension: Int): KnnVectorValues
    abstract fun vectorValues(values: Array<FloatArray>): KnnVectorValues
    abstract fun vectorValues(reader: KnnVectorValues, fieldName: String): KnnVectorValues
    abstract fun vectorValues(
        size: Int,
        dimension: Int,
        pregeneratedVectorValues: KnnVectorValues,
        pregeneratedOffset: Int
    ): KnnVectorValues
    abstract fun knnVectorField(name: String, vector: T, similarityFunction: VectorSimilarityFunction): Any
    abstract fun circularVectorValues(nDoc: Int): KnnVectorValues
    abstract fun getTargetVector(): T

    protected fun buildScorerSupplier(vectors: KnnVectorValues) =
        flatVectorScorer.getRandomVectorScorerSupplier(similarityFunction, vectors)

    @Suppress("UNCHECKED_CAST")
    protected fun buildScorer(vectors: KnnVectorValues, query: T) = when (getVectorEncoding()) {
        VectorEncoding.BYTE ->
            flatVectorScorer.getRandomVectorScorer(similarityFunction, vectors.copy(), query as ByteArray)
        VectorEncoding.FLOAT32 ->
            flatVectorScorer.getRandomVectorScorer(similarityFunction, vectors.copy(), query as FloatArray)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun vectorValue(vectors: KnnVectorValues, ord: Int): T = when (vectors.encoding) {
        VectorEncoding.BYTE -> (vectors as ByteVectorValues).vectorValue(ord) as T
        VectorEncoding.FLOAT32 -> (vectors as FloatVectorValues).vectorValue(ord) as T
    }

    /** Generate a random accept-ords mask. */
    protected fun createRandomAcceptOrds(startIndex: Int, length: Int): Bits {
        val bits = FixedBitSet(length)
        for (i in 0 until startIndex) bits.set(i)
        for (i in startIndex until length) if (Random.nextFloat() < 0.667f) bits.set(i)
        return bits
    }

    /** Utility used by several tests to compare two sets. */
    protected fun computeOverlap(a: IntArray, b: IntArray): Int {
        a.sort(); b.sort()
        var overlap = 0
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { overlap++; i++; j++ }
                a[i] > b[j] -> j++
                else -> i++
            }
        }
        return overlap
    }

    /** Returns vectors evenly distributed around the upper unit semicircle. */
    protected inner class CircularFloatVectorValues(private val size: Int) : FloatVectorValues() {
        private val value = FloatArray(2)

        private var doc = -1

        override fun copy() = CircularFloatVectorValues(size)

        override fun dimension() = 2

        override fun size() = size

        fun vectorValue() = vectorValue(doc)

        fun docID() = doc

        fun nextDoc() = advance(doc + 1)

        fun advance(target: Int): Int {
            doc = if (target in 0 until size) target else DocIdSetIterator.NO_MORE_DOCS
            return doc
        }

        override fun vectorValue(ord: Int): FloatArray = this@HnswGraphTestCase.unitVector2d(ord.toDouble() / size, value)
    }

    /** Returns vectors evenly distributed around the upper unit semicircle. */
    protected inner class CircularByteVectorValues(private val size: Int) : ByteVectorValues() {
        private val value = FloatArray(2)
        private val bValue = ByteArray(2)

        private var doc = -1

        override fun copy() = CircularByteVectorValues(size)

        override fun dimension() = 2

        override fun size() = size

        fun vectorValue() = vectorValue(doc)

        fun docID() = doc

        fun nextDoc() = advance(doc + 1)

        fun advance(target: Int): Int {
            doc = if (target in 0 until size) target else DocIdSetIterator.NO_MORE_DOCS
            return doc
        }

        override fun vectorValue(ord: Int): ByteArray {
            this@HnswGraphTestCase.unitVector2d(ord.toDouble() / size, value)
            for (i in value.indices) bValue[i] = (value[i] * 127).toInt().toByte()
            return bValue
        }

        override fun scorer(target: ByteArray): VectorScorer {
            throw UnsupportedOperationException()
        }
    }

    protected fun unitVector2d(piRadians: Double, value: FloatArray = FloatArray(2)): FloatArray {
        value[0] = cos(kotlin.math.PI * piRadians).toFloat()
        value[1] = sin(kotlin.math.PI * piRadians).toFloat()
        return value
    }

    protected fun randomVector(random: Random, dim: Int): FloatArray {
        val vec = FloatArray(dim) { (if (random.nextBoolean()) -1 else 1) * random.nextFloat() }
        VectorUtil.l2normalize(vec)
        return vec
    }

    protected fun randomVector8(random: Random, dim: Int): ByteArray {
        val f = randomVector(random, dim)
        return ByteArray(dim) { (f[it] * 127).toInt().toByte() }
    }

    @Test
    open fun testRandom() {
        val size = atLeast(100)
        val dim = atLeast(10)
        val vectors = vectorValues(size, dim)
        val topK = 5
        val builder = HnswGraphBuilder.create(buildScorerSupplier(vectors), 10, 30, Random.nextLong())
        val hnsw = builder.build(vectors.size())
        val acceptOrds = if (Random.nextBoolean()) null else createRandomAcceptOrds(0, size)

        var totalMatches = 0
        repeat(100) {
            val query = randomVector(dim)
            val mask = acceptOrds ?: Bits.MatchAllBits(size)
            val actual = HnswGraphSearcher.search(buildScorer(vectors, query), 100, hnsw, mask, Int.MAX_VALUE)
            val topDocs = actual.topDocs()
            val expected = NeighborQueue(topK, false)
            for (j in 0 until size) {
                if (vectorValue(vectors, j) != null && (acceptOrds == null || acceptOrds.get(j))) {
                    if (getVectorEncoding() == VectorEncoding.BYTE) {
                        expected.add(j, similarityFunction.compare(query as ByteArray, vectorValue(vectors, j) as ByteArray))
                    } else {
                        expected.add(j, similarityFunction.compare(query as FloatArray, vectorValue(vectors, j) as FloatArray))
                    }
                    if (expected.size() > topK) expected.pop()
                }
            }
            val actualDocs = IntArray(topK) { topDocs.scoreDocs!![it].doc }
            totalMatches += computeOverlap(actualDocs, expected.nodes())
        }
        val overlap = totalMatches.toDouble() / (100.0 * topK)
        assertTrue(overlap > 0.9, "overlap=$overlap")
    }

    @Ignore
    @Test
    open fun testRandomReadWriteAndMerge() {
        // TODO implement after IndexWriter is fully ported
    }
}

