package org.gnit.lucenekmp.internal.vectorization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.lucene95.OffHeapByteVectorValues
import org.gnit.lucenekmp.codecs.lucene95.OffHeapFloatVectorValues
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.index.VectorSimilarityFunction.COSINE
import org.gnit.lucenekmp.index.VectorSimilarityFunction.DOT_PRODUCT
import org.gnit.lucenekmp.index.VectorSimilarityFunction.EUCLIDEAN
import org.gnit.lucenekmp.index.VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestVectorScorer : LuceneTestCase() {

    @Test
    fun testSimpleScorer() {
        if (!hasMemSegScorer()) return
        testSimpleScorer(MMapDirectory.DEFAULT_MAX_CHUNK_SIZE)
    }

    @Test
    fun testSimpleScorerSmallChunkSize() {
        if (!hasMemSegScorer()) return
        val maxChunkSize = randomLongBetween(4, 16)
        testSimpleScorer(maxChunkSize)
    }

    @Test
    fun testSimpleScorerMedChunkSize() {
        if (!hasMemSegScorer()) return
        // a chunk size where in some vectors will be copied on-heap, while others remain off-heap
        testSimpleScorer(64)
    }

    private fun testSimpleScorer(maxChunkSize: Long) {
        MMapDirectory(createTempDir("testSimpleScorer"), maxChunkSize).use { dir: Directory ->
            for (dims in listOf(31, 32, 33)) {
                val vectors = Array(2) { ByteArray(dims) }
                val fileName = "bar-$dims"
                dir.createOutput(fileName, IOContext.DEFAULT).use { out: IndexOutput ->
                    for (i in 0..<dims) {
                        vectors[0][i] = i.toByte()
                        vectors[1][i] = (dims - i).toByte()
                    }
                    val bytes = concat(vectors[0], vectors[1])
                    out.writeBytes(bytes, 0, bytes.size)
                }
                dir.openInput(fileName, IOContext.DEFAULT).use { `in`: IndexInput ->
                    for (sim in listOf(COSINE, EUCLIDEAN, DOT_PRODUCT, MAXIMUM_INNER_PRODUCT)) {
                        val vectorValues = vectorValues(dims, 2, `in`, sim)
                        for (ords in listOf(listOf(0, 1), listOf(1, 0))) {
                            val idx0 = ords[0]
                            val idx1 = ords[1]

                            val scorer1 = DEFAULT_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                            var scorer1idx0 = scorer1.scorer()
                            scorer1idx0.setScoringOrdinal(idx0)
                            val expected = scorer1idx0.score(idx1)
                            val scorer2 = MEMSEG_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                            scorer1idx0 = scorer2.scorer()
                            scorer1idx0.setScoringOrdinal(idx0)
                            assertEquals(expected, scorer1idx0.score(idx1), DELTA)

                            val scorer3 = DEFAULT_SCORER.getRandomVectorScorer(sim, vectorValues, vectors[idx0])
                            assertEquals(expected, scorer3.score(idx1), DELTA)
                            val scorer4 = MEMSEG_SCORER.getRandomVectorScorer(sim, vectorValues, vectors[idx0])
                            assertEquals(expected, scorer4.score(idx1), DELTA)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testRandomScorer() {
        if (!hasMemSegScorer()) return
        testRandomScorer(MMapDirectory.DEFAULT_MAX_CHUNK_SIZE, BYTE_ARRAY_RANDOM_FUNC)
    }

    @Test
    fun testRandomScorerMax() {
        if (!hasMemSegScorer()) return
        testRandomScorer(MMapDirectory.DEFAULT_MAX_CHUNK_SIZE, BYTE_ARRAY_MAX_FUNC)
    }

    @Test
    fun testRandomScorerMin() {
        if (!hasMemSegScorer()) return
        testRandomScorer(MMapDirectory.DEFAULT_MAX_CHUNK_SIZE, BYTE_ARRAY_MIN_FUNC)
    }

    @Test
    fun testRandomSmallChunkSize() {
        if (!hasMemSegScorer()) return
        val maxChunkSize = randomLongBetween(32, 128)
        testRandomScorer(maxChunkSize, BYTE_ARRAY_RANDOM_FUNC)
    }

    private fun testRandomScorer(maxChunkSize: Long, byteArraySupplier: (Int) -> ByteArray) {
        MMapDirectory(createTempDir("testRandomScorer"), maxChunkSize).use { dir: Directory ->
            val dims = randomIntBetween(1, 4096)
            val size = randomIntBetween(2, 100)
            val vectors = Array(size) { ByteArray(0) }
            val fileName = "foo-$dims"
            dir.createOutput(fileName, IOContext.DEFAULT).use { out: IndexOutput ->
                for (i in 0..<size) {
                    val vec = byteArraySupplier(dims)
                    out.writeBytes(vec, 0, vec.size)
                    vectors[i] = vec
                }
            }

            dir.openInput(fileName, IOContext.DEFAULT).use { `in`: IndexInput ->
                for (times in 0..<TIMES) {
                    for (sim in listOf(COSINE, EUCLIDEAN, DOT_PRODUCT, MAXIMUM_INNER_PRODUCT)) {
                        val vectorValues = vectorValues(dims, size, `in`, sim)
                        val idx0 = randomIntBetween(0, size - 1)
                        val idx1 = randomIntBetween(0, size - 1)

                        val scorer1 = DEFAULT_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                        var scorer1idx0 = scorer1.scorer()
                        scorer1idx0.setScoringOrdinal(idx0)
                        val expected = scorer1idx0.score(idx1)
                        val scorer2 = MEMSEG_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                        scorer1idx0 = scorer2.scorer()
                        scorer1idx0.setScoringOrdinal(idx0)
                        assertEquals(expected, scorer1idx0.score(idx1), DELTA)

                        val scorer3 = DEFAULT_SCORER.getRandomVectorScorer(sim, vectorValues, vectors[idx0])
                        assertEquals(expected, scorer3.score(idx1), DELTA)
                        val scorer4 = MEMSEG_SCORER.getRandomVectorScorer(sim, vectorValues, vectors[idx0])
                        assertEquals(expected, scorer4.score(idx1), DELTA)
                    }
                }
            }
        }
    }

    @Test
    fun testRandomSliceSmall() {
        if (!hasMemSegScorer()) return
        testRandomSliceImpl(30, 64, 1, BYTE_ARRAY_RANDOM_FUNC)
    }

    @Test
    fun testRandomSlice() {
        if (!hasMemSegScorer()) return
        val dims = randomIntBetween(1, 4096)
        val maxChunkSize = randomLongBetween(32, 128)
        val initialOffset = randomIntBetween(1, 129)
        testRandomSliceImpl(dims, maxChunkSize, initialOffset, BYTE_ARRAY_RANDOM_FUNC)
    }

    private fun testRandomSliceImpl(
        dims: Int,
        maxChunkSize: Long,
        initialOffset: Int,
        byteArraySupplier: (Int) -> ByteArray
    ) {
        MMapDirectory(createTempDir("testRandomSliceImpl"), maxChunkSize).use { dir: Directory ->
            val size = randomIntBetween(2, 100)
            val vectors = Array(size) { ByteArray(0) }
            val fileName = "baz-$dims"
            dir.createOutput(fileName, IOContext.DEFAULT).use { out: IndexOutput ->
                out.writeBytes(ByteArray(initialOffset), 0, initialOffset)
                for (i in 0..<size) {
                    val vec = byteArraySupplier(dims)
                    out.writeBytes(vec, 0, vec.size)
                    vectors[i] = vec
                }
            }

            dir.openInput(fileName, IOContext.DEFAULT).use { outer ->
                outer.slice("slice", initialOffset.toLong(), outer.length() - initialOffset).use { `in` ->
                    for (times in 0..<TIMES) {
                        for (sim in listOf(COSINE, EUCLIDEAN, DOT_PRODUCT, MAXIMUM_INNER_PRODUCT)) {
                            val vectorValues = vectorValues(dims, size, `in`, sim)
                            val idx0 = randomIntBetween(0, size - 1)
                            val idx1 = randomIntBetween(0, size - 1)

                            val scorer1 = DEFAULT_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                            var scorer1idx0 = scorer1.scorer()
                            scorer1idx0.setScoringOrdinal(idx0)
                            val expected = scorer1idx0.score(idx1)
                            val scorer2 = MEMSEG_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                            scorer1idx0 = scorer2.scorer()
                            scorer1idx0.setScoringOrdinal(idx0)
                            assertEquals(expected, scorer1idx0.score(idx1), DELTA)

                            val scorer3 = DEFAULT_SCORER.getRandomVectorScorer(sim, vectorValues, vectors[idx0])
                            assertEquals(expected, scorer3.score(idx1), DELTA)
                            val scorer4 = MEMSEG_SCORER.getRandomVectorScorer(sim, vectorValues, vectors[idx0])
                            assertEquals(expected, scorer4.score(idx1), DELTA)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testCopiesAcrossThreads() {
        if (!hasMemSegScorer()) return
        val maxChunkSize = 32L
        val dims = 34
        val vec1 = ByteArray(dims) { 1 }
        val vec2 = ByteArray(dims) { 2 }

        MMapDirectory(createTempDir("testRace"), maxChunkSize).use { dir: Directory ->
            val fileName = "biz-$dims"
            dir.createOutput(fileName, IOContext.DEFAULT).use { out: IndexOutput ->
                val bytes = concat(vec1, vec1, vec2, vec2)
                out.writeBytes(bytes, 0, bytes.size)
            }
            dir.openInput(fileName, IOContext.DEFAULT).use { `in` ->
                for (sim in listOf(COSINE, EUCLIDEAN, DOT_PRODUCT, MAXIMUM_INNER_PRODUCT)) {
                    val vectorValues = vectorValues(dims, 4, `in`, sim)
                    val scoreSupplier = DEFAULT_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                    val scorer1 = scoreSupplier.scorer()
                    scorer1.setScoringOrdinal(0)
                    val expectedScore1 = scorer1.score(1)
                    scorer1.setScoringOrdinal(2)
                    val expectedScore2 = scorer1.score(3)

                    val scorer = MEMSEG_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                    val results = runBlocking {
                        listOf(
                            async(Dispatchers.Default) {
                                AssertingScoreCallable(scorer.copy().scorer(), 0, 1, expectedScore1).call()
                            },
                            async(Dispatchers.Default) {
                                AssertingScoreCallable(scorer.copy().scorer(), 2, 3, expectedScore2).call()
                            }
                        ).awaitAll()
                    }
                    for (res in results) {
                        assertTrue(res == null, "Unexpected exception $res")
                    }
                }
            }
        }
    }

    private data class AssertingScoreCallable(
        val scorer: UpdateableRandomVectorScorer,
        val target: Int,
        val ord: Int,
        val expectedScore: Float
    ) {
        fun call(): Throwable? {
            scorer.setScoringOrdinal(target)
            return try {
                for (i in 0..<100) {
                    assertEquals(expectedScore, scorer.score(ord), DELTA)
                }
                null
            } catch (t: Throwable) {
                t
            }
        }
    }

    @Test
    fun testLarge() {
        if (!hasMemSegScorer()) return
        MMapDirectory(createTempDir("testLarge")).use { dir: Directory ->
            val dims = 512 // TODO reduced valueA = 8192 to 512, valueB = 262500 to 2000 for dev speed
            val size = 2000
            val fileName = "large-$dims"
            dir.createOutput(fileName, IOContext.DEFAULT).use { out: IndexOutput ->
                for (i in 0..<size) {
                    val vec = vector(i, dims)
                    out.writeBytes(vec, 0, vec.size)
                }
            }

            dir.openInput(fileName, IOContext.DEFAULT).use { `in` ->
                for (times in 0..<TIMES) {
                    for (sim in listOf(COSINE, EUCLIDEAN, DOT_PRODUCT, MAXIMUM_INNER_PRODUCT)) {
                        val vectorValues = vectorValues(dims, size, `in`, sim)
                        val ord1 = randomIntBetween(0, size - 1)
                        val ord2 = size - 1
                        for (ords in listOf(listOf(ord1, ord2), listOf(ord2, ord1))) {
                            val idx0 = ords.first()
                            val idx1 = ords.last()

                            val scorer1 = DEFAULT_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                            var scorer1idx0 = scorer1.scorer()
                            scorer1idx0.setScoringOrdinal(idx0)
                            val expected = scorer1idx0.score(idx1)
                            val scorer2 = MEMSEG_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                            scorer1idx0 = scorer2.scorer()
                            scorer1idx0.setScoringOrdinal(idx0)
                            assertEquals(expected, scorer1idx0.score(idx1), DELTA)

                            val query = vector(idx0, dims)
                            val scorer3 = DEFAULT_SCORER.getRandomVectorScorer(sim, vectorValues, query)
                            assertEquals(expected, scorer3.score(idx1), DELTA)
                            val scorer4 = MEMSEG_SCORER.getRandomVectorScorer(sim, vectorValues, query)
                            assertEquals(expected, scorer4.score(idx1), DELTA)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testWithFloatValues() {
        if (!hasMemSegScorer()) return
        MMapDirectory(createTempDir("testWithFloatValues")).use { dir: Directory ->
            val fileName = "floatvalues"
            dir.createOutput(fileName, IOContext.DEFAULT).use { out: IndexOutput ->
                val vec = floatToByteArray(1f)
                out.writeBytes(vec, 0, vec.size)
            }

            dir.openInput(fileName, IOContext.DEFAULT).use { `in`: IndexInput ->
                for (times in 0..<TIMES) {
                    for (sim in listOf(COSINE, EUCLIDEAN, DOT_PRODUCT, MAXIMUM_INNER_PRODUCT)) {
                        val vectorValues = floatVectorValues(1, 1, `in`, sim)
                        assertEquals(4, vectorValues.encoding.byteSize)

                        val supplier1 = DEFAULT_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                        val supplier2 = MEMSEG_SCORER.getRandomVectorScorerSupplier(sim, vectorValues)
                        assertTrue(supplier1.toString().lowercase().contains("float"))
                        assertTrue(supplier2.toString().lowercase().contains("float"))
                        assertTrue(supplier1.scorer().toString().lowercase().contains("float"))
                        assertTrue(supplier2.scorer().toString().lowercase().contains("float"))
                        var scorer1idx0 = supplier1.scorer()
                        scorer1idx0.setScoringOrdinal(0)
                        var expected = scorer1idx0.score(0)
                        scorer1idx0 = supplier2.scorer()
                        scorer1idx0.setScoringOrdinal(0)
                        assertEquals(expected, scorer1idx0.score(0), DELTA)

                        val scorer1 = DEFAULT_SCORER.getRandomVectorScorer(sim, vectorValues, floatArrayOf(1f))
                        val scorer2 = MEMSEG_SCORER.getRandomVectorScorer(sim, vectorValues, floatArrayOf(1f))
                        assertTrue(scorer1.toString().lowercase().contains("float"))
                        assertTrue(scorer2.toString().lowercase().contains("float"))
                        expected = scorer1.score(0)
                        assertEquals(expected, scorer2.score(0), DELTA)

                        expectThrows(Throwable::class) {
                            DEFAULT_SCORER.getRandomVectorScorer(sim, vectorValues, byteArrayOf(1))
                        }
                        expectThrows(Throwable::class) {
                            MEMSEG_SCORER.getRandomVectorScorer(sim, vectorValues, byteArrayOf(1))
                        }
                    }
                }
            }
        }
    }

    private fun hasMemSegScorer(): Boolean {
        return MEMSEG_SCORER::class != DEFAULT_SCORER::class
    }

    private fun vectorValues(dims: Int, size: Int, `in`: IndexInput, sim: VectorSimilarityFunction): KnnVectorValues {
        return OffHeapByteVectorValues.DenseOffHeapVectorValues(
            dims,
            size,
            `in`.slice("byteValues", 0, `in`.length()),
            dims,
            MEMSEG_SCORER,
            sim
        )
    }

    private fun floatVectorValues(dims: Int, size: Int, `in`: IndexInput, sim: VectorSimilarityFunction): KnnVectorValues {
        return OffHeapFloatVectorValues.DenseOffHeapVectorValues(
            dims,
            size,
            `in`.slice("floatValues", 0, `in`.length()),
            dims * Float.SIZE_BYTES,
            MEMSEG_SCORER,
            sim
        )
    }

    companion object {
        private const val DELTA = 1e-5f
        private val DEFAULT_SCORER: FlatVectorsScorer = DefaultFlatVectorScorer.INSTANCE
        private val MEMSEG_SCORER: FlatVectorsScorer = VectorizationProvider.getInstance().lucene99FlatVectorsScorer

        fun vector(ord: Int, dims: Int): ByteArray {
            val random = Random(Objects.hash(ord, dims))
            val ba = ByteArray(dims)
            for (i in 0..<dims) {
                ba[i] = RandomNumbers.randomIntBetween(random, Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()
            }
            return ba
        }

        fun concat(vararg arrays: ByteArray): ByteArray {
            val baos = ByteArrayOutputStream()
            arrays.forEach { baos.write(it) }
            return baos.toByteArray()
        }

        fun floatToByteArray(value: Float): ByteArray {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toBits()).array()
        }

        fun randomIntBetween(minInclusive: Int, maxInclusive: Int): Int {
            return RandomNumbers.randomIntBetween(random(), minInclusive, maxInclusive)
        }

        fun randomLongBetween(minInclusive: Long, maxInclusive: Long): Long {
            return RandomNumbers.randomLongBetween(random(), minInclusive, maxInclusive)
        }

        private val BYTE_ARRAY_RANDOM_FUNC: (Int) -> ByteArray = { size ->
            val ba = ByteArray(size)
            for (i in 0..<size) {
                ba[i] = random().nextInt().toByte()
            }
            ba
        }

        private val BYTE_ARRAY_MAX_FUNC: (Int) -> ByteArray = { size ->
            val ba = ByteArray(size)
            Arrays.fill(ba, Byte.MAX_VALUE)
            ba
        }

        private val BYTE_ARRAY_MIN_FUNC: (Int) -> ByteArray = { size ->
            val ba = ByteArray(size)
            Arrays.fill(ba, Byte.MIN_VALUE)
            ba
        }

        private const val TIMES = 10 // TODO reduced from 100 to 10 for dev speed
    }
}
