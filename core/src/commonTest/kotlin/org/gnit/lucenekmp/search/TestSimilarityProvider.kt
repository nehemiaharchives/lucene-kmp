package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.similarities.PerFieldSimilarityWrapper
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSimilarityProvider : LuceneTestCase() {
    private lateinit var directory: Directory
    private lateinit var reader: DirectoryReader
    private lateinit var searcher: IndexSearcher

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        directory = newDirectory()
        val sim: PerFieldSimilarityWrapper = ExampleSimilarityProvider()
        val iwc: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(random())).setSimilarity(sim)
        val iw = RandomIndexWriter(random(), directory, iwc)
        val doc = Document()
        val field = newTextField("foo", "", Field.Store.NO)
        doc.add(field)
        val field2 = newTextField("bar", "", Field.Store.NO)
        doc.add(field2)

        field.setStringValue("quick brown fox")
        field2.setStringValue("quick brown fox")
        iw.addDocument(doc)
        field.setStringValue("jumps over lazy brown dog")
        field2.setStringValue("jumps over lazy brown dog")
        iw.addDocument(doc)
        reader = iw.reader
        iw.close()
        searcher = newSearcher(reader)
        searcher.similarity = sim
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        directory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        // sanity check of norms writer
        // TODO: generalize
        val fooNorms: NumericDocValues = MultiDocValues.getNormValues(reader, "foo")!!
        val barNorms: NumericDocValues = MultiDocValues.getNormValues(reader, "bar")!!
        for (i in 0..<reader.maxDoc()) {
            assertEquals(i.toLong(), fooNorms.nextDoc().toLong())
            assertEquals(i.toLong(), barNorms.nextDoc().toLong())
            assertFalse(fooNorms.longValue() == barNorms.longValue())
        }

        // sanity check of searching
        val foodocs = searcher.search(TermQuery(Term("foo", "brown")), 10)
        assertTrue(foodocs.totalHits.value > 0)
        val bardocs = searcher.search(TermQuery(Term("bar", "brown")), 10)
        assertTrue(bardocs.totalHits.value > 0)
        assertTrue(foodocs.scoreDocs[0].score < bardocs.scoreDocs[0].score)
    }

    private class ExampleSimilarityProvider : PerFieldSimilarityWrapper() {
        private val sim1: Similarity = Sim1()
        private val sim2: Similarity = Sim2()

        override fun get(name: String): Similarity {
            return if (name == "foo") {
                sim1
            } else {
                sim2
            }
        }
    }

    private class Sim1 : Similarity() {
        override fun computeNorm(state: FieldInvertState): Long {
            return 1
        }

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics
        ): SimScorer {
            return object : SimScorer() {
                override fun score(freq: Float, norm: Long): Float {
                    return 1f
                }
            }
        }
    }

    private class Sim2 : Similarity() {
        override fun computeNorm(state: FieldInvertState): Long {
            return 10
        }

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics
        ): SimScorer {
            return object : SimScorer() {
                override fun score(freq: Float, norm: Long): Float {
                    return 10f
                }
            }
        }
    }
}
