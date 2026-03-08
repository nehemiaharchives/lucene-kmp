package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.search.similarities.PerFieldSimilarityWrapper
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** */
class TestCustomNorms : LuceneTestCase() {

    @Test
    fun testFloatNorms() {
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 2, IndexWriter.MAX_TERM_LENGTH))

        val config = newIndexWriterConfig(analyzer)
        val provider: Similarity = MySimProvider()
        config.setSimilarity(provider)
        val writer = RandomIndexWriter(random(), dir, config)
        val docs = LineFileDocs(random())
        val num = atLeast(100)
        for (i in 0 until num) {
            val doc = docs.nextDoc()
            val boost = TestUtil.nextInt(random(), 1, 10)
            val value = List(boost) { boost.toString() }.joinToString(" ")
            val f = TextField(FLOAT_TEST_FIELD, value, Field.Store.YES)

            doc.add(f)
            writer.addDocument(doc)
            doc.removeField(FLOAT_TEST_FIELD)
            if (rarely()) {
                writer.commit()
            }
        }
        writer.commit()
        writer.close()
        val open = DirectoryReader.open(dir)
        val norms = MultiDocValues.getNormValues(open, FLOAT_TEST_FIELD)
        assertNotNull(norms)
        val storedFields = open.storedFields()
        for (i in 0 until open.maxDoc()) {
            val document = storedFields.document(i)
            val expected = document.get(FLOAT_TEST_FIELD)!!.split(" ")[0].toInt()
            assertEquals(i.toLong(), norms.nextDoc().toLong())
            assertEquals(expected.toLong(), norms.longValue())
        }
        open.close()
        dir.close()
        docs.close()
    }

    inner class MySimProvider : PerFieldSimilarityWrapper() {
        var delegate: Similarity = ClassicSimilarity()

        override fun get(field: String): Similarity {
            return if (FLOAT_TEST_FIELD == field) {
                FloatEncodingBoostSimilarity()
            } else {
                delegate
            }
        }
    }

    class FloatEncodingBoostSimilarity : Similarity() {
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

    companion object {
        const val FLOAT_TEST_FIELD: String = "normsTestFloat"
        const val EXCEPTION_TEST_FIELD: String = "normsTestExcp"
    }
}
