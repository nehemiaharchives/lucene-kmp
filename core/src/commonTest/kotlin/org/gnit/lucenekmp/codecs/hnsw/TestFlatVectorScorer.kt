package org.gnit.lucenekmp.codecs.hnsw

import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorScorerUtil
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.lucene95.OffHeapByteVectorValues
import org.gnit.lucenekmp.codecs.lucene95.OffHeapFloatVectorValues
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.expectThrows
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestFlatVectorScorer : LuceneTestCase() {
    private val scorers: List<FlatVectorsScorer> = listOf(
        DefaultFlatVectorScorer.INSTANCE,
        // TODO: Lucene99ScalarQuantizedVectorScorer when available
        FlatVectorScorerUtil.getLucene99FlatVectorsScorer()
    )

    private val dirSuppliers: List<() -> Directory> = listOf(
        { newDirectory() }
        // MMapDirectory is not supported in lucene-kmp
    )

    private fun newDirectory(): Directory {
        return ByteBuffersDirectory()
    }

    @Test
    fun testDefaultOrMemSegScorer() {
        val scorer = FlatVectorScorerUtil.getLucene99FlatVectorsScorer()
        val name = scorer.toString()
        assertTrue(
            name == "DefaultFlatVectorScorer()" ||
                name == "Lucene99MemorySegmentFlatVectorsScorer()"
        )
    }

    @Test
    fun testMultipleByteScorers() {
        val vec0 = byteArrayOf(0, 0, 0, 0)
        val vec1 = byteArrayOf(1, 1, 1, 1)
        val vec2 = byteArrayOf(15, 15, 15, 15)
        val fileName = "testMultipleByteScorers"

        for (scorer in scorers) {
            for (dirSupplier in dirSuppliers) {
                val dir = dirSupplier()
                try {
                    dir.createOutput(fileName, IOContext.DEFAULT).use { out ->
                        out.writeBytes(concat(vec0, vec1, vec2), 0, vec0.size * 3)
                    }
                    dir.openInput(fileName, IOContext.DEFAULT).use { input ->
                        val vectorValues = byteVectorValues(4, 3, input, VectorSimilarityFunction.EUCLIDEAN, scorer)
                        val ss = scorer.getRandomVectorScorerSupplier(VectorSimilarityFunction.EUCLIDEAN, vectorValues)
                        val scorerAgainstOrd0 = ss.scorer()
                        scorerAgainstOrd0.setScoringOrdinal(0)
                        val firstScore = scorerAgainstOrd0.score(1)
                        val scorerAgainstOrd2 = ss.scorer()
                        scorerAgainstOrd2.setScoringOrdinal(2)
                        val scoreAgain = scorerAgainstOrd0.score(1)
                        assertEquals(firstScore, scoreAgain)
                    }
                } finally {
                    dir.close()
                }
            }
        }
    }

    @Test
    fun testMultipleFloatScorers() {
        val vec0 = floatArrayOf(0f, 0f, 0f, 0f)
        val vec1 = floatArrayOf(1f, 1f, 1f, 1f)
        val vec2 = floatArrayOf(15f, 15f, 15f, 15f)
        val fileName = "testMultipleFloatScorers"

        for (scorer in scorers) {
            for (dirSupplier in dirSuppliers) {
                val dir = dirSupplier()
                try {
                    dir.createOutput(fileName, IOContext.DEFAULT).use { out ->
                        out.writeBytes(concat(vec0, vec1, vec2), 0, vec0.size * Float.SIZE_BYTES * 3)
                    }
                    dir.openInput(fileName, IOContext.DEFAULT).use { input ->
                        val vectorValues = floatVectorValues(4, 3, input, VectorSimilarityFunction.EUCLIDEAN, scorer)
                        val ss = scorer.getRandomVectorScorerSupplier(VectorSimilarityFunction.EUCLIDEAN, vectorValues)
                        val scorerAgainstOrd0 = ss.scorer()
                        scorerAgainstOrd0.setScoringOrdinal(0)
                        val firstScore = scorerAgainstOrd0.score(1)
                        val scorerAgainstOrd2 = ss.scorer()
                        scorerAgainstOrd2.setScoringOrdinal(2)
                        val scoreAgain = scorerAgainstOrd0.score(1)
                        assertEquals(firstScore, scoreAgain)
                    }
                } finally {
                    dir.close()
                }
            }
        }
    }

    @Test
    fun testCheckByteDimensions() {
        val vec0 = ByteArray(4)
        val fileName = "testCheckByteDimensions"

        for (scorer in scorers) {
            for (dirSupplier in dirSuppliers) {
                val dir = dirSupplier()
                try {
                    dir.createOutput(fileName, IOContext.DEFAULT).use { out ->
                        out.writeBytes(vec0, 0, vec0.size)
                    }
                    dir.openInput(fileName, IOContext.DEFAULT).use { input ->
                        for (sim in listOf(
                            VectorSimilarityFunction.COSINE,
                            VectorSimilarityFunction.DOT_PRODUCT,
                            VectorSimilarityFunction.EUCLIDEAN,
                            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
                        )) {
                            val vectorValues = byteVectorValues(4, 1, input, sim, scorer)
                            expectThrows(IllegalArgumentException::class) {
                                scorer.getRandomVectorScorer(sim, vectorValues, ByteArray(5))
                            }
                        }
                    }
                } finally {
                    dir.close()
                }
            }
        }
    }

    @Test
    fun testCheckFloatDimensions() {
        val vec0 = FloatArray(4)
        val fileName = "testCheckFloatDimensions"

        for (scorer in scorers) {
            for (dirSupplier in dirSuppliers) {
                val dir = dirSupplier()
                try {
                    dir.createOutput(fileName, IOContext.DEFAULT).use { out ->
                        out.writeBytes(concat(vec0), 0, vec0.size * Float.SIZE_BYTES)
                    }
                    dir.openInput(fileName, IOContext.DEFAULT).use { input ->
                        for (sim in listOf(
                            VectorSimilarityFunction.COSINE,
                            VectorSimilarityFunction.DOT_PRODUCT,
                            VectorSimilarityFunction.EUCLIDEAN,
                            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
                        )) {
                            val vectorValues = floatVectorValues(4, 1, input, sim, scorer)
                            expectThrows(IllegalArgumentException::class) {
                                scorer.getRandomVectorScorer(sim, vectorValues, FloatArray(5))
                            }
                        }
                    }
                } finally {
                    dir.close()
                }
            }
        }
    }

    private fun byteVectorValues(
        dims: Int,
        size: Int,
        input: IndexInput,
        sim: VectorSimilarityFunction,
        scorer: FlatVectorsScorer
    ): ByteVectorValues {
        return OffHeapByteVectorValues.DenseOffHeapVectorValues(
            dims,
            size,
            input.slice("byteValues", 0, input.length()),
            dims,
            scorer,
            sim
        )
    }

    private fun floatVectorValues(
        dims: Int,
        size: Int,
        input: IndexInput,
        sim: VectorSimilarityFunction,
        scorer: FlatVectorsScorer
    ): FloatVectorValues {
        return OffHeapFloatVectorValues.DenseOffHeapVectorValues(
            dims,
            size,
            input.slice("floatValues", 0, input.length()),
            dims * Float.SIZE_BYTES,
            scorer,
            sim
        )
    }

    companion object {
        private fun concat(vararg arrays: FloatArray): ByteArray {
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(4)
            for (fa in arrays) {
                for (f in fa) {
                    val bits = f.toBits()
                    buffer[0] = (bits and 0xFF).toByte()
                    buffer[1] = ((bits ushr 8) and 0xFF).toByte()
                    buffer[2] = ((bits ushr 16) and 0xFF).toByte()
                    buffer[3] = ((bits ushr 24) and 0xFF).toByte()
                    baos.write(buffer, 0, 4)
                }
            }
            return baos.toByteArray()
        }

        private fun concat(vararg arrays: ByteArray): ByteArray {
            val baos = ByteArrayOutputStream()
            for (ba in arrays) {
                baos.writeBytes(ba)
            }
            return baos.toByteArray()
        }
    }
}

