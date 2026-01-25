package org.gnit.lucenekmp.search.similarities

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Version
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBooleanSimilarity : BaseSimilarityTestCase() {

    @Test
    override fun testRandomScoring() = super.testRandomScoring()

    @Test
    @Throws(IOException::class)
    fun testTermScoreIsEqualToBoost() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())
        var doc = Document()
        doc.add(StringField("foo", "bar", Store.NO))
        doc.add(StringField("foo", "baz", Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(StringField("foo", "bar", Store.NO))
        doc.add(StringField("foo", "bar", Store.NO))
        w.addDocument(doc)

        val reader: DirectoryReader = w.reader
        w.close()
        val searcher: IndexSearcher = newSearcher(reader)
        searcher.similarity = BooleanSimilarity()
        var topDocs: TopDocs = searcher.search(TermQuery(Term("foo", "bar")), 2)
        assertEquals(2, topDocs.totalHits.value)
        assertEquals(1f, topDocs.scoreDocs[0].score, 0f)
        assertEquals(1f, topDocs.scoreDocs[1].score, 0f)

        topDocs = searcher.search(TermQuery(Term("foo", "baz")), 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1f, topDocs.scoreDocs[0].score, 0f)

        topDocs = searcher.search(BoostQuery(TermQuery(Term("foo", "baz")), 3f), 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(3f, topDocs.scoreDocs[0].score, 0f)

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testPhraseScoreIsEqualToBoost() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig().setSimilarity(BooleanSimilarity()))
        val doc = Document()
        doc.add(TextField("foo", "bar baz quux", Store.NO))
        w.addDocument(doc)

        val reader: DirectoryReader = w.reader
        w.close()
        val searcher: IndexSearcher = newSearcher(reader)
        searcher.similarity = BooleanSimilarity()

        val query = PhraseQuery(2, "foo", "bar", "quux")

        var topDocs: TopDocs = searcher.search(query, 2)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1f, topDocs.scoreDocs[0].score, 0f)

        topDocs = searcher.search(BoostQuery(query, 7f), 2)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(7f, topDocs.scoreDocs[0].score, 0f)

        reader.close()
        dir.close()
    }

    @Test
    fun testSameNormsAsBM25() {
        val sim1 = BooleanSimilarity()
        val sim2 = BM25Similarity()
        for (iter in 0..99) {
            val length: Int = TestUtil.nextInt(random(), 1, 100)
            val position: Int = random().nextInt(length)
            val numOverlaps: Int = random().nextInt(length)
            val maxTermFrequency = 1
            val uniqueTermCount = 1
            val state = FieldInvertState(
                    indexCreatedVersionMajor = Version.LATEST.major,
                    name = "foo",
                    indexOptions = IndexOptions.DOCS_AND_FREQS,
                    position = position,
                    length = length,
                    numOverlap = numOverlaps,
                    offset = 100,
                    maxTermFrequency = maxTermFrequency,
                    uniqueTermCount = uniqueTermCount
                )
            assertEquals(sim2.computeNorm(state), sim1.computeNorm(state))
        }
    }

    override fun getSimilarity(random: Random): Similarity {
        return BooleanSimilarity()
    }
}
