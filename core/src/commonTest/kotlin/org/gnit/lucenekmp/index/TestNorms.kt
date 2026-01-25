package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.search.similarities.PerFieldSimilarityWrapper
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test that norms info is preserved during index life - including separate norms, addDocument,
 * addIndexes, forceMerge.
 */
class TestNorms : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testMaxByteNorms() {
        val dir: Directory =
            newFSDirectory(
                createTempDir("TestNorms.testMaxByteNorms")
            )
        buildIndex(dir)
        val open: DirectoryReader =
            DirectoryReader.open(dir)
        val normValues: NumericDocValues? =
            MultiDocValues.getNormValues(open, BYTE_TEST_FIELD)
        assertNotNull(normValues)
        val storedFields: StoredFields = open.storedFields()
        for (i in 0..<open.maxDoc()) {
            val document: Document = storedFields.document(i)
            val expected =
                document.get(BYTE_TEST_FIELD)!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[0].toInt()
            assertEquals(i.toLong(), normValues.nextDoc().toLong())
            assertEquals(expected.toLong(), normValues.longValue())
        }
        open.close()
        dir.close()
    }

    // TODO: create a testNormsNotPresent ourselves by adding/deleting/merging docs
    @Throws(IOException::class)
    fun buildIndex(dir: Directory) {
        val random: Random = random()
        val analyzer = MockAnalyzer(random())
        // we need at least 3 for maxTokenLength otherwise norms are messed up
        analyzer.setMaxTokenLength(
            TestUtil.nextInt(random(), 3, IndexWriter.MAX_TERM_LENGTH)
        )
        val config: IndexWriterConfig = newIndexWriterConfig(analyzer)
        val provider: Similarity = MySimProvider()
        config.setSimilarity(provider)
        val writer = RandomIndexWriter(random, dir, config)
        val num: Int = atLeast(100)
        for (i in 0..<num) {
            val doc = Document()
            val boost: Int = TestUtil.nextInt(random, 1, 255)
            val value: String = List(boost) { boost.toString() }.joinToString(" ")
            /*
                val value: String =
                java.util.stream.IntStream.range(0, boost)
                    .mapToObj<String>(java.util.function.IntFunction { `_`: Int -> boost.toString() })
                    .collect(java.util.stream.Collectors.joining(" "))
            */
            val f: Field = TextField(
                BYTE_TEST_FIELD, value, Store.YES
            )
            doc.add(f)
            writer.addDocument<IndexableField>(doc)
            doc.removeField(BYTE_TEST_FIELD)
        }
        writer.commit()
        writer.close()
    }

    class MySimProvider : PerFieldSimilarityWrapper() {
        var delegate: Similarity = ClassicSimilarity()

        override fun get(field: String): Similarity {
            if (BYTE_TEST_FIELD == field) {
                return ByteEncodingBoostSimilarity()
            } else {
                return delegate
            }
        }
    }

    class ByteEncodingBoostSimilarity : Similarity() {
        override fun computeNorm(state: FieldInvertState): Long {
            return state.length.toLong()
        }

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics
        ): SimScorer {
            throw UnsupportedOperationException()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyValueVsNoValue() {
        val dir: Directory = newDirectory()
        val cfg: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy())
        val w = IndexWriter(dir, cfg)
        val doc = Document()
        w.addDocument(doc)
        doc.add(newTextField("foo", "", Store.NO))
        w.addDocument(doc)
        w.forceMerge(1)
        val reader: IndexReader =
            DirectoryReader.open(w)
        w.close()
        val leafReader: LeafReader = getOnlyLeafReader(reader)
        val normValues: NumericDocValues? = leafReader.getNormValues("foo")
        assertNotNull(normValues)
        assertEquals(1, normValues.nextDoc().toLong()) // doc 0 does not have norms
        assertEquals(0, normValues.longValue())
        reader.close()
        dir.close()
    }

    companion object {
        const val BYTE_TEST_FIELD: String = "normsTestByte"
    }
}
