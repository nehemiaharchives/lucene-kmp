package org.gnit.lucenekmp.search.similarities

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.isInfinite
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.DisjunctionMaxQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.Version
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestClassicSimilarity : BaseSimilarityTestCase() {
    private var directory: Directory? = null
    private var indexReader: IndexReader? = null
    private var indexSearcher: IndexSearcher? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        // TODO implement if needed
        //super.setUp()

        directory = newDirectory()
        IndexWriter(
            directory!!,
            newIndexWriterConfig()
        ).use { indexWriter ->
            val document = Document()
            document.add(StringField("test", "hit", Field.Store.NO))
            indexWriter.addDocument(document)
            indexWriter.commit()
        }
        indexReader = DirectoryReader.open(directory!!)
        indexSearcher = newSearcher(indexReader!!)
        indexSearcher!!.similarity = ClassicSimilarity()
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        IOUtils.close(indexReader, directory)

        //TODO implement if needed
        //super.tearDown()
    }

    @Test
    override fun testRandomScoring() {
        super.testRandomScoring()
    }

    @Test
    @Throws(IOException::class)
    fun testHit() {
        val query: Query = TermQuery(Term("test", "hit"))
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size.toLong())
        assertTrue(topDocs.scoreDocs[0].score != 0f)
    }

    @Test
    @Throws(IOException::class)
    fun testMiss() {
        val query: Query = TermQuery(Term("test", "miss"))
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(0, topDocs.totalHits.value)
    }

    @Test
    @Throws(IOException::class)
    fun testEmpty() {
        val query: Query = TermQuery(Term("empty", "miss"))
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(0, topDocs.totalHits.value)
    }

    @Test
    @Throws(IOException::class)
    fun testBQHit() {
        val query: Query = BooleanQuery.Builder().add(TermQuery(Term("test", "hit")), Occur.SHOULD).build()
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size.toLong())
        assertTrue(topDocs.scoreDocs[0].score != 0f)
    }

    @Test
    @Throws(IOException::class)
    fun testBQHitOrMiss() {
        val query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("test", "hit")), Occur.SHOULD)
                .add(TermQuery(Term("test", "miss")), Occur.SHOULD)
                .build()
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size.toLong())
        assertTrue(topDocs.scoreDocs[0].score != 0f)
    }

    @Test
    @Throws(IOException::class)
    fun testBQHitOrEmpty() {
        val query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("test", "hit")), Occur.SHOULD)
                .add(TermQuery(Term("empty", "miss")), Occur.SHOULD)
                .build()
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size.toLong())
        assertTrue(topDocs.scoreDocs[0].score != 0f)
    }

    @Test
    @Throws(IOException::class)
    fun testDMQHit() {
        val query: Query = DisjunctionMaxQuery(mutableListOf(TermQuery(Term("test", "hit"))), 0f)
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size.toLong())
        assertTrue(topDocs.scoreDocs[0].score != 0f)
    }

    @Test
    @Throws(IOException::class)
    fun testDMQHitOrMiss() {
        val query: Query =
            DisjunctionMaxQuery(
                mutableListOf(
                    TermQuery(Term("test", "hit")),
                    TermQuery(Term("test", "miss"))
                ),
                0f
            )
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size.toLong())
        assertTrue(topDocs.scoreDocs[0].score != 0f)
    }

    @Test
    @Throws(IOException::class)
    fun testDMQHitOrEmpty() {
        val query: Query =
            DisjunctionMaxQuery(
                mutableListOf(
                    TermQuery(Term("test", "hit")),
                    TermQuery(Term("empty", "miss"))
                ),
                0f
            )
        val topDocs: TopDocs = indexSearcher!!.search(query, 1)
        assertEquals(1, topDocs.totalHits.value)
        assertEquals(1, topDocs.scoreDocs.size.toLong())
        assertTrue(topDocs.scoreDocs[0].score != 0f)
    }

    @Test
    @Throws(IOException::class)
    fun testSaneNormValues() {
        val sim = ClassicSimilarity()
        val stats: TFIDFSimilarity.TFIDFScorer = sim.scorer(1f, indexSearcher!!.collectionStatistics("test")!!) as TFIDFSimilarity.TFIDFScorer
        for (i in 0..255) {
            val boost: Float = stats.normTable[i]
            assertFalse(boost < 0.0f, "negative boost: $boost, byte=$i")
            assertFalse(Float.isInfinite(boost), "inf bost: $boost, byte=$i")
            assertFalse( Float.isNaN(boost), "nan boost for byte=$i")
            if (i > 0) {
                assertTrue(
                    "boost is not decreasing: $boost,byte=$i",
                    boost < stats.normTable[i - 1]
                )
            }
        }
    }

    @Test
    fun testSameNormsAsBM25() {
        val sim1 = ClassicSimilarity()
        val sim2 = BM25Similarity()
        for (iter in 0..99) {
            val length: Int = TestUtil.nextInt(random(), 1, 1000)
            val position: Int = random().nextInt(length)
            val numOverlaps: Int = random().nextInt(length)
            val maxTermFrequency = 1
            val uniqueTermCount = 1
            val state = FieldInvertState(Version.LATEST.major, "foo", IndexOptions.DOCS_AND_FREQS, position, length, numOverlaps, 100, maxTermFrequency, uniqueTermCount)
            assertEquals(sim2.computeNorm(state), sim1.computeNorm(state))
        }
    }

    override fun getSimilarity(random: Random): Similarity {
        return ClassicSimilarity()
    }
}
