package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.*

class TestTermScorer : LuceneTestCase() {
    protected lateinit var directory: Directory
    private val FIELD = "field"

    protected var values = arrayOf("all", "dogs dogs", "like", "playing", "fetch", "all")
    protected lateinit var indexSearcher: IndexSearcher
    protected lateinit var indexReader: LeafReader

    @BeforeTest
    fun setUp() {
        directory = ByteBuffersDirectory()
        val writer = IndexWriter(directory, IndexWriterConfig(MockAnalyzer(random())).setSimilarity(ClassicSimilarity()))
        for (v in values) {
            val doc = Document()
            doc.add(TextField(FIELD, v, Field.Store.YES))
            writer.addDocument(doc)
        }
        writer.close()
        val reader = DirectoryReader.open(directory)
        indexReader = getOnlyLeafReader(reader)
        indexSearcher = IndexSearcher(indexReader)
    }

    private fun getOnlyLeafReader(reader: IndexReader): LeafReader {
        val leaves = reader.leaves()
        assertEquals(1, leaves.size)
        return leaves[0].reader()
    }

    @AfterTest
    fun tearDown() {
        indexReader.close()
        directory.close()
    }

    @Test
    fun test() {
        val allTerm = Term(FIELD, "all")
        val termQuery = TermQuery(allTerm)
        val weight = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE, 1f)
        assertTrue(indexSearcher.topReaderContext is LeafReaderContext)
        val context = indexSearcher.topReaderContext as LeafReaderContext
        val ts = weight.bulkScorer(context)!!
        val docs = mutableListOf<TestHit>()
        val next = ts.score(object : SimpleCollector() {
            private var base = 0
            override var weight: Weight? = null
            override fun collect(doc: Int) {
                val score = scorer!!.score()
                val docId = doc + base
                docs.add(TestHit(docId, score))
                assertTrue("score $score is not greater than 0") { score > 0 }
                assertTrue("Doc: $docId does not equal 0 or doc does not equal 5") { docId == 0 || docId == 5 }
            }
            override fun doSetNextReader(context: LeafReaderContext) {
                base = context.docBase
            }
            override fun scoreMode(): ScoreMode {
                return ScoreMode.COMPLETE
            }
        }, null, 0, DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(2, docs.size)
        assertTrue("next returned a doc and it should not have") {
            next == DocIdSetIterator.NO_MORE_DOCS
        }
    }

    @Test
    fun testAdvance() {
        val allTerm = Term(FIELD, "all")
        val termQuery = TermQuery(allTerm)
        val weight = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE, 1f)
        assertTrue(indexSearcher.topReaderContext is LeafReaderContext)
        val context = indexSearcher.topReaderContext as LeafReaderContext
        val ts = weight.scorer(context)!!
        assertTrue("Didn't skip") { ts.iterator().advance(3) != DocIdSetIterator.NO_MORE_DOCS }
        assertTrue("doc should be number 5") { ts.docID() == 5 }
    }

    data class TestHit(val doc: Int, val score: Float)

    @Test
    fun testDoesNotLoadNorms() {
        val allTerm = Term(FIELD, "all")
        val termQuery = TermQuery(allTerm)
        val forbiddenNorms = object : FilterLeafReader(indexReader) {
            override fun getNormValues(field: String): NumericDocValues {
                fail("Norms should not be loaded")
            }
            override val coreCacheHelper: IndexReader.CacheHelper
                get() = delegate.coreCacheHelper
            override val readerCacheHelper: IndexReader.CacheHelper?
                get() = delegate.readerCacheHelper
        }
        val indexSearcher = IndexSearcher(forbiddenNorms)
        val weight = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE, 1f)
        LuceneTestCase.expectThrows(AssertionError::class) {
            weight.scorer(forbiddenNorms.context)!!.iterator().nextDoc()
        }
        val weight2 = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE_NO_SCORES, 1f)
        weight2.scorer(forbiddenNorms.context)!!.iterator().nextDoc()
    }

    @Test
    fun testRandomTopDocs() {
        val dir = ByteBuffersDirectory()
        val w = IndexWriter(dir, IndexWriterConfig())
        val numDocs = if (TEST_NIGHTLY) atLeast(random(), 128 * 8 * 8 * 3) else atLeast(random(), 500)
        for (i in 0 until numDocs) {
            val doc = Document()
            val numValues = random().nextInt(1 shl random().nextInt(5))
            val start = random().nextInt(10)
            for (j in 0 until numValues) {
                val freq = TestUtil.nextInt(random(), 1, 1 shl random().nextInt(3))
                repeat(freq) {
                    doc.add(TextField("foo", (start + j).toString(), Field.Store.NO))
                }
            }
            w.addDocument(doc)
        }
        val reader = DirectoryReader.open(w)
        w.close()
        val searcher = IndexSearcher(reader)
        for (iter in 0 until 15) {
            val query = TermQuery(Term("foo", iter.toString()))
            var completeManager = TopScoreDocCollectorManager(10, Int.MAX_VALUE)
            var topScoresManager = TopScoreDocCollectorManager(10, 1)
            var complete = searcher.search(query, completeManager)
            var topScores = searcher.search(query, topScoresManager)
            CheckHits.checkEqual(query, complete.scoreDocs, topScores.scoreDocs)
            val filterTerm = random().nextInt(15)
            val filteredQuery = BooleanQuery.Builder()
                .add(query, BooleanClause.Occur.MUST)
                .add(TermQuery(Term("foo", filterTerm.toString())), BooleanClause.Occur.FILTER)
                .build()
            completeManager = TopScoreDocCollectorManager(10, Int.MAX_VALUE)
            topScoresManager = TopScoreDocCollectorManager(10, 1)
            complete = searcher.search(filteredQuery, completeManager)
            topScores = searcher.search(filteredQuery, topScoresManager)
            CheckHits.checkEqual(query, complete.scoreDocs, topScores.scoreDocs)
        }
        reader.close()
        dir.close()
    }
}

