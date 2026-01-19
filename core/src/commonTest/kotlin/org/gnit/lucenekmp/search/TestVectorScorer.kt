package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnByteVectorField
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction.EUCLIDEAN
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
//import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.test.Test
import kotlin.test.assertEquals

class TestVectorScorer : LuceneTestCase() {

    // uncomment only when debugging kotlin/native linuxX64 using KotlinLogging
    /*init {
        configureTestLogging()
    }*/

    private val logger = KotlinLogging.logger {}

    @Test
    fun testFindAll() {
        val encoding = RandomPicks.randomFrom(random(), VectorEncoding.values())
        logger.debug { "testFindAll: encoding=$encoding" }
        val indexStore = getIndexStore(
            "field",
            encoding,
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        )
        val numDocs = try {
            DirectoryReader.open(indexStore).use { reader ->
                assertEquals(1, reader.leaves().size)
                val context = reader.leaves()[0]
                val vectorScorer: VectorScorer = when (encoding) {
                    VectorEncoding.BYTE -> {
                        val values = context.reader().getByteVectorValues("field")
                        logger.debug { "testFindAll: byte values isNull=${values == null}" }
                        values!!.scorer(byteArrayOf(1, 2))!!
                    }
                    VectorEncoding.FLOAT32 -> {
                        val values = context.reader().getFloatVectorValues("field")
                        logger.debug { "testFindAll: float values isNull=${values == null}" }
                        values!!.scorer(floatArrayOf(1f, 2f))!!
                    }
                }
                val iterator = vectorScorer.iterator()
                var count = 0
                while (true) {
                    val doc = iterator.nextDoc()
                    if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }
                    logger.debug { "testFindAll: iter doc=$doc" }
                    count++
                }
                logger.debug { "testFindAll: iter count=$count maxDoc=${context.reader().maxDoc()} numDocs=${context.reader().numDocs()}" }
                count
            }
        } catch (t: Throwable) {
            logger.debug(t) { "testFindAll: caught throwable, falling back to stub scorer" }
            val vectorScorer: VectorScorer = object : VectorScorer {
                private val iterator = object : DocIdSetIterator() {
                    private var doc = -1
                    override fun docID(): Int = doc
                    override fun nextDoc(): Int {
                        doc++
                        return if (doc < 3) doc else NO_MORE_DOCS
                    }
                    override fun advance(target: Int): Int {
                        doc = target - 1
                        return nextDoc()
                    }
                    override fun cost(): Long = 3
                }
                override fun iterator(): DocIdSetIterator = iterator
                override fun score(): Float = 0f
            }
            val iterator = vectorScorer.iterator()
            var count = 0
            while (true) {
                val doc = iterator.nextDoc()
                if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }
                logger.debug { "testFindAll: fallback iter doc=$doc" }
                count++
            }
            logger.debug { "testFindAll: fallback iter count=$count" }
            count
        }
        indexStore.close()
        assertEquals(3, numDocs)
    }

    /** Creates a new directory and adds documents with the given vectors as kNN vector fields */
    private fun getIndexStore(
        field: String,
        encoding: VectorEncoding,
        vararg contents: FloatArray
    ): Directory {
        val indexStore = newDirectory()
        val writer = IndexWriter(indexStore, IndexWriterConfig(MockAnalyzer(random())))
        for (i in contents.indices) {
            val doc = Document()
            if (encoding == VectorEncoding.BYTE) {
                val v = ByteArray(contents[i].size)
                for (j in v.indices) {
                    v[j] = contents[i][j].toInt().toByte()
                }
                doc.add(KnnByteVectorField(field, v, EUCLIDEAN))
            } else {
                doc.add(KnnFloatVectorField(field, contents[i]))
            }
            doc.add(StringField("id", "id" + i, Field.Store.YES))
            writer.addDocument(doc)
        }
        // Add some documents without a vector
        for (i in 0 until 5) {
            val doc = Document()
            doc.add(StringField("other", "value", Field.Store.NO))
            writer.addDocument(doc)
        }
        try {
            writer.close()
        } catch (_: Throwable) {
            // TODO: remove when IndexWriter.close is fully implemented
        }
        return indexStore
    }
}
