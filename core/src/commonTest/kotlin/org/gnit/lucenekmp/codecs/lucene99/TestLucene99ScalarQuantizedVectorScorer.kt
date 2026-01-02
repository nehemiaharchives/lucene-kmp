package org.gnit.lucenekmp.codecs.lucene99

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.codecs.lucene101.Lucene101Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.quantization.QuantizedByteVectorValues
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLucene99ScalarQuantizedVectorScorer : LuceneTestCase() {

    private fun getCodec(bits: Int, compress: Boolean): Codec {
        val vectorsFormat = Lucene99HnswScalarQuantizedVectorsFormat(
            Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN,
            Lucene99HnswVectorsFormat.DEFAULT_BEAM_WIDTH,
            1,
            bits,
            compress,
            0f,
            null
        )
        return object : Codec("TestLucene99ScalarQuantizedVectorsCodec") {
            private val delegate = Lucene101Codec()
            override fun postingsFormat() = delegate.postingsFormat()
            override fun docValuesFormat() = delegate.docValuesFormat()
            override fun storedFieldsFormat() = delegate.storedFieldsFormat()
            override fun termVectorsFormat() = delegate.termVectorsFormat()
            override fun fieldInfosFormat() = delegate.fieldInfosFormat()
            override fun segmentInfoFormat() = delegate.segmentInfoFormat()
            override fun normsFormat() = delegate.normsFormat()
            override fun liveDocsFormat() = delegate.liveDocsFormat()
            override fun compoundFormat() = delegate.compoundFormat()
            override fun pointsFormat() = delegate.pointsFormat()
            override fun knnVectorsFormat() = vectorsFormat
        }
    }

    @Test
    fun testNonZeroScores() {
        for (bits in intArrayOf(4, 7)) {
            for (compress in booleanArrayOf(true, false)) {
                vectorNonZeroScoringTest(bits, compress)
            }
        }
    }

    private fun vectorNonZeroScoringTest(bits: Int, compress: Boolean) {
        ByteBuffersDirectory().use { dir ->
            var vec1 = ByteArray(32)
            var vec2 = ByteArray(32)
            if (compress && bits == 4) {
                val vec1Compressed = ByteArray(16)
                val vec2Compressed = ByteArray(16)
                compressBytes(vec1, vec1Compressed)
                compressBytes(vec2, vec2Compressed)
                vec1 = vec1Compressed
                vec2 = vec2Compressed
            }
            val fileName = "scalar-32"
            dir.createOutput(fileName, IOContext.DEFAULT).use { out ->
                val negativeOffset = floatToByteArray(-50f)
                val bytes = concat(vec1, negativeOffset, vec2, negativeOffset)
                out.writeBytes(bytes, 0, bytes.size)
            }
            val scalarQuantizer = ScalarQuantizer(0.1f, 0.9f, bits.toByte())
            dir.openInput(fileName, IOContext.DEFAULT).use { input ->
                val scorer = Lucene99ScalarQuantizedVectorScorer(DefaultFlatVectorScorer())
                val values = object : QuantizedByteVectorValues() {
                    override fun dimension(): Int = 32
                    override fun size(): Int = 2
                    override val vectorByteLength: Int
                        get() = if (compress && bits == 4) 16 else 32

                    override fun vectorValue(ord: Int): ByteArray = ByteArray(32)

                    override fun getScoreCorrectionConstant(ord: Int): Float = -50f

                    override fun copy(): QuantizedByteVectorValues = this

                    override val slice: IndexInput?
                        get() = input

                    override val scalarQuantizer: ScalarQuantizer
                        get() = scalarQuantizer
                }
                val queryVector = FloatArray(32) { it * 0.1f }
                for (function in VectorSimilarityFunction.entries) {
                    val randomScorer = scorer.getRandomVectorScorer(function, values, queryVector)
                    assertTrue(randomScorer.score(0) >= 0f)
                    assertTrue(randomScorer.score(1) >= 0f)
                }
            }
        }
    }

    @Test
    fun testScoringCompressedInt4() {
        vectorScoringTest(4, true)
    }

    @Test
    fun testScoringUncompressedInt4() {
        vectorScoringTest(4, false)
    }

    @Test
    fun testScoringInt7() {
        vectorScoringTest(7, false)
    }

    @Test
    fun testSingleVectorPerSegmentCosine() {
        testSingleVectorPerSegment(VectorSimilarityFunction.COSINE)
    }

    @Test
    fun testSingleVectorPerSegmentDot() {
        testSingleVectorPerSegment(VectorSimilarityFunction.DOT_PRODUCT)
    }

    @Test
    fun testSingleVectorPerSegmentEuclidean() {
        testSingleVectorPerSegment(VectorSimilarityFunction.EUCLIDEAN)
    }

    @Test
    fun testSingleVectorPerSegmentMIP() {
        testSingleVectorPerSegment(VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT)
    }

    private fun vectorScoringTest(bits: Int, compress: Boolean) {
        val storedVectors = Array(10) { FloatArray(0) }
        val numVectors = 10
        var vectorDimensions = random().nextInt(10) + 4
        if (bits == 4 && vectorDimensions % 2 == 1) {
            vectorDimensions++
        }
        for (i in 0 until numVectors) {
            val vector = FloatArray(vectorDimensions)
            for (j in 0 until vectorDimensions) {
                vector[j] = i + j.toFloat()
            }
            VectorUtil.l2normalize(vector)
            storedVectors[i] = vector
        }

        val similarities = arrayOf(
            VectorSimilarityFunction.DOT_PRODUCT,
            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT,
            VectorSimilarityFunction.EUCLIDEAN
        )
        for (similarityFunction in similarities) {
            newDirectory().use { dir ->
                indexVectors(dir, storedVectors, similarityFunction, bits, compress)
                DirectoryReader.open(dir).use { reader ->
                    val leafReader = reader.leaves()[0].reader()
                    val vector = FloatArray(vectorDimensions)
                    for (i in 0 until vectorDimensions) {
                        vector[i] = i + 1f
                    }
                    VectorUtil.l2normalize(vector)
                    val randomScorer = getRandomVectorScorer(similarityFunction, leafReader, vector)
                    val rawScores = FloatArray(10)
                    for (i in 0 until 10) {
                        rawScores[i] = similarityFunction.compare(vector, storedVectors[i])
                    }
                    for (i in 0 until 10) {
                        assertEquals(rawScores[i], randomScorer.score(i), 0.05f)
                    }
                }
            }
        }
    }

    private fun getRandomVectorScorer(
        function: VectorSimilarityFunction,
        leafReader: LeafReader,
        vector: FloatArray
    ): RandomVectorScorer {
        if (leafReader is CodecReader) {
            var format: KnnVectorsReader? = leafReader.vectorReader
            if (format is PerFieldKnnVectorsFormat.FieldsReader) {
                format = format.getFieldReader("field")
            }
            if (format is Lucene99HnswVectorsReader) {
                val quantized = format.getQuantizedVectorValues("field")
                    ?: throw IllegalArgumentException("Missing quantized values")
                return Lucene99ScalarQuantizedVectorScorer(DefaultFlatVectorScorer())
                    .getRandomVectorScorer(function, quantized, vector)
            }
        }
        throw IllegalArgumentException("Unsupported reader")
    }

    private fun testSingleVectorPerSegment(sim: VectorSimilarityFunction) {
        val codec = getCodec(7, false)
        newDirectory().use { dir ->
            IndexWriter(
                dir,
                IndexWriterConfig().setCodec(codec).setMergeScheduler(SerialMergeScheduler())
            ).use { writer ->
                val doc2 = Document()
                doc2.add(KnnFloatVectorField("field", floatArrayOf(0.8f, 0.6f), sim))
                doc2.add(TextField("id", "A", Field.Store.YES))
                writer.addDocument(doc2)
                writer.commit()

                val doc1 = Document()
                doc1.add(KnnFloatVectorField("field", floatArrayOf(0.6f, 0.8f), sim))
                doc1.add(TextField("id", "B", Field.Store.YES))
                writer.addDocument(doc1)
                writer.commit()

                val doc3 = Document()
                doc3.add(KnnFloatVectorField("field", floatArrayOf(-0.6f, -0.8f), sim))
                doc3.add(TextField("id", "C", Field.Store.YES))
                writer.addDocument(doc3)
                writer.commit()

                writer.forceMerge(1)
            }
            DirectoryReader.open(dir).use { reader ->
                val leafReader = reader.leaves()[0].reader()
                val storedFields: StoredFields = reader.storedFields()
                val queryVector = floatArrayOf(0.6f, 0.8f)
                val hits = leafReader.searchNearestVectors(
                    "field",
                    queryVector,
                    3,
                    Bits.MatchAllBits(leafReader.maxDoc()),
                    100
                )
                assertEquals(3, hits.scoreDocs.size)
                assertEquals("B", storedFields.document(hits.scoreDocs[0].doc).get("id"))
                assertEquals("A", storedFields.document(hits.scoreDocs[1].doc).get("id"))
                assertEquals("C", storedFields.document(hits.scoreDocs[2].doc).get("id"))
            }
        }
    }

    private fun indexVectors(
        dir: ByteBuffersDirectory,
        vectors: Array<FloatArray>,
        function: VectorSimilarityFunction,
        bits: Int,
        compress: Boolean
    ) {
        IndexWriter(
            dir,
            IndexWriterConfig()
                .setCodec(getCodec(bits, compress))
                .setMergeScheduler(SerialMergeScheduler())
        ).use { writer ->
            for (i in vectors.indices) {
                val doc = Document()
                if (random().nextBoolean()) {
                    writer.addDocument(doc)
                }
                writer.addDocument(doc)
                doc.add(KnnFloatVectorField("field", vectors[i], function))
                writer.addDocument(doc)
            }
            writer.commit()
            writer.forceMerge(1)
        }
    }

    private fun floatToByteArray(value: Float): ByteArray {
        val bits = value.toBits()
        return byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits ushr 8) and 0xFF).toByte(),
            ((bits ushr 16) and 0xFF).toByte(),
            ((bits ushr 24) and 0xFF).toByte()
        )
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        var total = 0
        for (arr in arrays) total += arr.size
        val out = ByteArray(total)
        var pos = 0
        for (arr in arrays) {
            System.arraycopy(arr, 0, out, pos, arr.size)
            pos += arr.size
        }
        return out
    }

    private fun compressBytes(raw: ByteArray, compressed: ByteArray) {
        require(compressed.size == ((raw.size + 1) shr 1)) {
            "compressed length: ${compressed.size} does not match raw length: ${raw.size}"
        }
        for (i in compressed.indices) {
            val v = (raw[i].toInt() shl 4) or (raw[compressed.size + i].toInt() and 0x0F)
            compressed[i] = v.toByte()
        }
    }

    private fun newDirectory(): ByteBuffersDirectory {
        return ByteBuffersDirectory()
    }
}
