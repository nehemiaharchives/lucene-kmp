package org.gnit.lucenekmp.search.similarities

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRawTFSimilarity : BaseSimilarityTestCase() {
    private var directory: Directory? = null
    private var indexReader: IndexReader? = null
    private var indexSearcher: IndexSearcher? = null

    override fun getSimilarity(random: Random): Similarity {
        return RawTFSimilarity()
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        directory = newDirectory()
        IndexWriter(directory!!, newIndexWriterConfig()).use { indexWriter ->
            val document1 = Document()
            val document2 = Document()
            val document3 = Document()
            document1.add(newTextField("test", "one", Field.Store.YES))
            document2.add(newTextField("test", "two two", Field.Store.YES))
            document3.add(newTextField("test", "three three three", Field.Store.YES))
            indexWriter.addDocument(document1)
            indexWriter.addDocument(document2)
            indexWriter.addDocument(document3)
            indexWriter.commit()
        }
        indexReader = DirectoryReader.open(directory!!)
        indexSearcher = newSearcher(indexReader!!)
        indexSearcher!!.similarity = RawTFSimilarity()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        IOUtils.close(indexReader, directory)
    }

    @Test
    @Throws(IOException::class)
    fun testOne() {
        implTest("one", 1f)
    }

    @Test
    @Throws(IOException::class)
    fun testTwo() {
        implTest("two", 2f)
    }

    @Test
    @Throws(IOException::class)
    fun testThree() {
        implTest("three", 3f)
    }

    @Throws(IOException::class)
    private fun implTest(text: String, expectedScore: Float) {
        val query: Query = TermQuery(Term("test", text))
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size)
        assertEquals(expectedScore, topDocs.scoreDocs[0].score, 0.0f)
    }

    @Test
    @Throws(IOException::class)
    fun testBoostQuery() {
        val query: Query = TermQuery(Term("test", "three"))
        val boost = 14f
        val topDocs: TopDocs = indexSearcher!!.search(BoostQuery(query, boost), 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size)
        assertEquals(42f, topDocs.scoreDocs[0].score, 0.0f)
    }

    // tests inherited from BaseSimilarityTestCase
    @Test
    override fun testRandomScoring() = super.testRandomScoring()
}
