package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.*

class TestTopDocsCollector : LuceneTestCase() {

    private class MyTopDocsCollectorMananger(private val numHits: Int) :
        CollectorManager<MyTopDocsCollector, MyTopDocsCollector> {

        override fun newCollector(): MyTopDocsCollector {
            return MyTopDocsCollector(numHits)
        }

        override fun reduce(collectors: MutableCollection<MyTopDocsCollector>): MyTopDocsCollector {
            var totalHits = 0
            val myTopDocsCollector = MyTopDocsCollector(numHits)
            for (collector in collectors) {
                totalHits += collector.totalHits
                for (scoreDoc in collector.topDocs().scoreDocs) {
                    myTopDocsCollector.add(scoreDoc)
                }
            }
            myTopDocsCollector.setTotalHits(totalHits)
            return myTopDocsCollector
        }
    }

    private class MyTopDocsCollector(size: Int) : TopDocsCollector<ScoreDoc>(HitQueue(size, false)) {

        fun add(scoreDoc: ScoreDoc) {
            (pq as HitQueue<ScoreDoc>).insertWithOverflow(scoreDoc)
        }

        fun setTotalHits(value: Int) {
            totalHits = value
        }

        override fun newTopDocs(results: Array<ScoreDoc>?, start: Int): TopDocs {
            if (results == null) {
                return EMPTY_TOPDOCS
            }
            return TopDocs(TotalHits(totalHits.toLong(), totalHitsRelation), results)
        }

        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            val base = context.docBase
            return object : LeafCollector {
                override fun collect(doc: Int) {
                    ++totalHits
                    add(ScoreDoc(doc + base, scores[context.docBase + doc]))
                }

                override fun setScorer(scorer: Scorable) {
                    // Don't do anything. Assign scores in random
                }
            }
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE_NO_SCORES
        }
    }

    companion object {
        // Scores array to be used by MyTopDocsCollector. If it is changed, MAX_SCORE must also change.
        val scores = floatArrayOf(
            0.7767749f,
            1.7839992f,
            8.9925785f,
            7.9608946f,
            0.07948637f,
            2.6356435f,
            7.4950366f,
            7.1490803f,
            8.108544f,
            4.961808f,
            2.2423935f,
            7.285586f,
            4.6699767f,
            2.9655676f,
            6.953706f,
            5.383931f,
            6.9916306f,
            8.365894f,
            7.888485f,
            8.723962f,
            3.1796896f,
            0.39971232f,
            1.3077754f,
            6.8489285f,
            9.17561f,
            5.060466f,
            7.9793315f,
            8.601509f,
            4.1858315f,
            0.28146625f
        )

        private const val MAX_SCORE = 9.17561f

        private fun doConcurrentSearchWithThreshold(
            numResults: Int,
            threshold: Int,
            q: Query,
            indexReader: IndexReader
        ): TopDocs {
            val searcher = newSearcher(indexReader, true, true, true)
            val collectorManager = TopScoreDocCollectorManager(numResults, null, threshold)
            return searcher.search(q, collectorManager)
        }
    }

    private lateinit var dir: Directory
    private lateinit var reader: IndexReader

    private fun doSearch(numResults: Int): TopDocs {
        val q: Query = MatchAllDocsQuery()
        val searcher = newSearcher(reader)
        return searcher.search(q, MyTopDocsCollectorMananger(numResults))
    }

    private fun doSearchWithThreshold(
        numResults: Int,
        thresHold: Int,
        q: Query,
        indexReader: IndexReader
    ): TopDocs {
        val searcher = newSearcher(indexReader, true, true, false)
        val collectorManager = TopScoreDocCollectorManager(numResults, null, thresHold)
        return searcher.search(q, collectorManager)
    }

    @BeforeTest
    override fun setUp() {
        super.setUp()
        dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig())
        for (i in 0 until 30) {
            writer.addDocument(Document())
        }
        reader = DirectoryReader.open(writer)
        writer.close()
    }

    @AfterTest
    override fun tearDown() {
        reader.close()
        dir.close()
        super.tearDown()
    }

    @Test
    fun testInvalidArguments() {
        val numResults = 5
        val tdc = doSearch(numResults)

        // start < 0
        var exception = assertFailsWith<IllegalArgumentException> {
            tdc.topDocs(-1)
        }
        assertEquals("Expected value of starting position is between 0 and 5, got -1", exception.message)

        // start == pq.size()
        assertEquals(0, tdc.topDocs(numResults).scoreDocs.size)

        // howMany < 0
        exception = assertFailsWith {
            tdc.topDocs(0, -1)
        }
        assertEquals("Number of hits requested must be greater than 0 but value was -1", exception.message)
    }

    @Test
    fun testZeroResults() {
        val tdc = MyTopDocsCollector(5)
        assertEquals(0, tdc.topDocs(0, 1).scoreDocs.size)
    }

    @Test
    fun testFirstResultsPage() {
        val tdc = doSearch(15)
        assertEquals(10, tdc.topDocs(0, 10).scoreDocs.size)
    }

    @Test
    fun testSecondResultsPages() {
        var tdc = doSearch(15)
        // ask for more results than are available
        assertEquals(5, tdc.topDocs(10, 10).scoreDocs.size)

        // ask for 5 results (exactly what there should be
        tdc = doSearch(15)
        assertEquals(5, tdc.topDocs(10, 5).scoreDocs.size)

        // ask for less results than there are
        tdc = doSearch(15)
        assertEquals(4, tdc.topDocs(10, 4).scoreDocs.size)
    }

    @Test
    fun testGetAllResults() {
        val tdc = doSearch(15)
        assertEquals(15, tdc.topDocs().scoreDocs.size)
    }

    @Test
    fun testGetResultsFromStart() {
        var tdc = doSearch(15)
        // should bring all results
        assertEquals(15, tdc.topDocs(0).scoreDocs.size)

        tdc = doSearch(15)
        // get the last 5 only.
        assertEquals(5, tdc.topDocs(10).scoreDocs.size)
    }

    @Test
    fun testIllegalArguments() {
        val tdc = doSearch(15)

        var expected = assertFailsWith<IllegalArgumentException> {
            tdc.topDocs(-1)
        }
        assertEquals("Expected value of starting position is between 0 and 15, got -1", expected.message)

        expected = assertFailsWith {
            tdc.topDocs(9, -1)
        }
        assertEquals("Number of hits requested must be greater than 0 but value was -1", expected.message)
    }

    @Test
    fun testResultsOrder() {
        val tdc = doSearch(15)
        val sd = tdc.topDocs().scoreDocs

        assertEquals(MAX_SCORE, sd[0].score)
        for (i in 1 until sd.size) {
            assertTrue(sd[i - 1].score >= sd[i].score)
        }
    }

    private class Score : Scorable() {
        var score: Float = 0f
        var minCompetitiveScore: Float? = null

        override fun setMinCompetitiveScore(score: Float) {
            check(minCompetitiveScore == null || score >= minCompetitiveScore!!)
            minCompetitiveScore = score
        }

        override fun score(): Float {
            return score
        }
    }

    @Test
    fun testSetMinCompetitiveScore() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        w.addDocuments(listOf(doc, doc, doc, doc))
        w.flush()
        w.addDocuments(listOf(doc, doc))
        w.flush()
        val reader = DirectoryReader.open(w)
        assertEquals(2, reader.leaves().size)
        w.close()

        val collectorManager = TopScoreDocCollectorManager(2, 2)
        val collector = collectorManager.newCollector()
        var scorer = Score()

        var leafCollector = collector.getLeafCollector(reader.leaves()[0])
        leafCollector.setScorer(scorer)
        assertNull(scorer.minCompetitiveScore)

        scorer.score = 1f
        leafCollector.collect(0)
        assertNull(scorer.minCompetitiveScore)

        scorer.score = 2f
        leafCollector.collect(1)
        assertNull(scorer.minCompetitiveScore)

        scorer.score = 3f
        leafCollector.collect(2)
        assertEquals(Math.nextUp(2f), scorer.minCompetitiveScore)

        scorer.score = 0.5f
        scorer.minCompetitiveScore = null
        leafCollector.collect(3)
        assertNull(scorer.minCompetitiveScore)

        scorer.score = 4f
        leafCollector.collect(4)
        assertEquals(Math.nextUp(3f), scorer.minCompetitiveScore)

        scorer = Score()
        leafCollector = collector.getLeafCollector(reader.leaves()[1])
        leafCollector.setScorer(scorer)
        assertEquals(Math.nextUp(3f), scorer.minCompetitiveScore)

        scorer.score = 1f
        leafCollector.collect(0)
        assertEquals(Math.nextUp(3f), scorer.minCompetitiveScore)

        scorer.score = 4f
        leafCollector.collect(1)
        assertEquals(Math.nextUp(4f), scorer.minCompetitiveScore)

        reader.close()
        dir.close()
    }

    @Test
    fun testSharedCountCollectorManager() {
        val q: Query = MatchAllDocsQuery()
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        w.addDocuments(listOf(doc, doc, doc, doc))
        w.flush()
        w.addDocuments(listOf(doc, doc))
        w.flush()
        val reader = DirectoryReader.open(w)
        assertEquals(2, reader.leaves().size)
        w.close()

        val tdc = doConcurrentSearchWithThreshold(5, 10, q, reader)
        val tdc2 = doSearchWithThreshold(5, 10, q, reader)

        assertEquals(tdc.scoreDocs.map { it.doc }, tdc2.scoreDocs.map { it.doc })

        reader.close()
        dir.close()
    }

    @Test
    fun testTotalHits() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        w.addDocuments(listOf(doc, doc, doc, doc))
        w.flush()
        w.addDocuments(listOf(doc, doc, doc, doc, doc, doc))
        w.flush()
        val reader = DirectoryReader.open(w)
        assertEquals(2, reader.leaves().size)
        w.close()

        for (totalHitsThreshold in 0 until 20) {
            val collectorManager = TopScoreDocCollectorManager(2, totalHitsThreshold)
            val collector = collectorManager.newCollector()
            val scorer = Score()

            var leafCollector = collector.getLeafCollector(reader.leaves()[0])
            leafCollector.setScorer(scorer)

            scorer.score = 3f
            leafCollector.collect(0)

            scorer.score = 3f
            leafCollector.collect(1)

            leafCollector = collector.getLeafCollector(reader.leaves()[1])
            leafCollector.setScorer(scorer)

            scorer.score = 3f
            leafCollector.collect(1)

            scorer.score = 4f
            leafCollector.collect(1)

            val topDocs = collector.topDocs()
            assertEquals(4, topDocs.totalHits.value().toInt())
            assertEquals(totalHitsThreshold < 4, scorer.minCompetitiveScore != null)
            assertEquals(
                TotalHits(
                    4,
                    if (totalHitsThreshold < 4) TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO else TotalHits.Relation.EQUAL_TO
                ),
                topDocs.totalHits
            )
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testRelationVsTopDocsCount() {
        newDirectory().use { dir ->
            val w = IndexWriter(dir, newIndexWriterConfig())
            val doc = Document()
            doc.add(TextField("f", "foo bar", Field.Store.NO))
            w.addDocuments(listOf(doc, doc, doc, doc, doc))
            w.flush()
            w.addDocuments(listOf(doc, doc, doc, doc, doc))
            w.flush()

            DirectoryReader.open(w).use { reader ->
                val searcher = IndexSearcher(reader)
                var collectorManager = TopScoreDocCollectorManager(2, 10)
                var topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
                assertEquals(10, topDocs.totalHits.value().toInt())
                assertEquals(TotalHits.Relation.EQUAL_TO, topDocs.totalHits.relation())

                collectorManager = TopScoreDocCollectorManager(2, 2)
                topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
                assertTrue(10 >= topDocs.totalHits.value().toInt())
                assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation())

                collectorManager = TopScoreDocCollectorManager(10, 2)
                topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
                assertEquals(10, topDocs.totalHits.value().toInt())
                assertEquals(TotalHits.Relation.EQUAL_TO, topDocs.totalHits.relation())
            }
        }
    }

    @Test
    fun testConcurrentMinScore() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        w.addDocuments(listOf(doc, doc, doc, doc, doc))
        w.flush()
        w.addDocuments(listOf(doc, doc, doc, doc, doc, doc))
        w.flush()
        w.addDocuments(listOf(doc, doc))
        w.flush()
        val reader = DirectoryReader.open(w)
        assertEquals(3, reader.leaves().size)
        w.close()

        val manager: CollectorManager<TopScoreDocCollector, TopDocs> = TopScoreDocCollectorManager(2, 0)
        val collector = manager.newCollector()
        val collector2 = manager.newCollector()
        assertTrue(collector.minScoreAcc === collector2.minScoreAcc)
        val minValueChecker = collector.minScoreAcc!!
        minValueChecker.modInterval = 0

        val scorer = Score()
        val scorer2 = Score()

        val leafCollector = collector.getLeafCollector(reader.leaves()[0])
        leafCollector.setScorer(scorer)
        val leafCollector2 = collector2.getLeafCollector(reader.leaves()[1])
        leafCollector2.setScorer(scorer2)

        scorer.score = 3f
        leafCollector.collect(0)
        assertEquals(Long.MIN_VALUE, minValueChecker.raw)
        assertNull(scorer.minCompetitiveScore)

        scorer.score = 7f
        leafCollector.collect(1)
        assertEquals(7f, MaxScoreAccumulator.toScore(minValueChecker.raw), 0f)
        assertEquals(Math.nextUp(7f), scorer.minCompetitiveScore)
        assertNull(scorer2.minCompetitiveScore)

        scorer2.score = 6f
        leafCollector2.collect(0)
        assertEquals(6f, MaxScoreAccumulator.toScore(minValueChecker.raw), 0f)
        assertEquals(Math.nextUp(7f), scorer.minCompetitiveScore)
        assertEquals(Math.nextUp(6f), scorer2.minCompetitiveScore)

        scorer2.score = 10f
        leafCollector2.collect(1)
        assertEquals(10f, MaxScoreAccumulator.toScore(minValueChecker.raw), 0f)
        assertEquals(Math.nextUp(7f), scorer.minCompetitiveScore)
        assertEquals(Math.nextUp(10f), scorer2.minCompetitiveScore)

        val collector3 = manager.newCollector()
        val leafCollector3 = collector3.getLeafCollector(reader.leaves()[2])
        val scorer3 = Score()
        leafCollector3.setScorer(scorer3)
        assertEquals(Math.nextUp(10f), scorer3.minCompetitiveScore)

        scorer3.score = 1f
        leafCollector3.collect(0)
        assertEquals(10f, MaxScoreAccumulator.toScore(minValueChecker.raw), 0f)
        assertEquals(Math.nextUp(10f), scorer3.minCompetitiveScore)

        scorer.score = 11f
        leafCollector.collect(4)
        assertEquals(11f, MaxScoreAccumulator.toScore(minValueChecker.raw), 0f)
        assertEquals(Math.nextUp(11f), scorer.minCompetitiveScore)
        assertEquals(Math.nextUp(7f), scorer2.minCompetitiveScore)
        assertEquals(Math.nextUp(10f), scorer3.minCompetitiveScore)

        scorer3.score = 2f
        leafCollector3.collect(1)
        assertEquals(11f, MaxScoreAccumulator.toScore(minValueChecker.raw), 0f)
        assertEquals(Math.nextUp(11f), scorer.minCompetitiveScore)
        assertEquals(Math.nextUp(7f), scorer2.minCompetitiveScore)
        assertEquals(Math.nextUp(11f), scorer3.minCompetitiveScore)

        val topDocs = manager.reduce(mutableListOf(collector, collector2, collector3))
        assertEquals(11, topDocs.totalHits.value().toInt())
        assertEquals(TotalHits(11, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), topDocs.totalHits)

        leafCollector.setScorer(scorer)
        leafCollector2.setScorer(scorer2)
        leafCollector3.setScorer(scorer3)

        reader.close()
        dir.close()
    }

    @Test
    fun testRandomMinCompetitiveScore() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDocs = atLeast(1000)
        for (i in 0 until numDocs) {
            val numAs = 1 + random().nextInt(5)
            val numBs = if (random().nextFloat() < 0.5f) 0 else 1 + random().nextInt(5)
            val numCs = if (random().nextFloat() < 0.1f) 0 else 1 + random().nextInt(5)
            val doc = Document()
            for (j in 0 until numAs) {
                doc.add(StringField("f", "A", Field.Store.NO))
            }
            for (j in 0 until numBs) {
                doc.add(StringField("f", "B", Field.Store.NO))
            }
            for (j in 0 until numCs) {
                doc.add(StringField("f", "C", Field.Store.NO))
            }
            w.addDocument(doc)
        }
        val indexReader = DirectoryReader.open(w)
        w.close()
        val queries = arrayOf<Query>(
            TermQuery(Term("f", "A")),
            TermQuery(Term("f", "B")),
            TermQuery(Term("f", "C")),
            BooleanQuery.Builder()
                .add(TermQuery(Term("f", "A")), BooleanClause.Occur.MUST)
                .add(TermQuery(Term("f", "B")), BooleanClause.Occur.SHOULD)
                .build()
        )
        for (query in queries) {
            val tdc = doConcurrentSearchWithThreshold(5, 0, query, indexReader)
            val tdc2 = doSearchWithThreshold(5, 0, query, indexReader)

            assertTrue(tdc.totalHits.value() > 0)
            assertTrue(tdc2.totalHits.value() > 0)
            assertEquals(tdc.scoreDocs.map { it.doc }, tdc2.scoreDocs.map { it.doc })
        }

        indexReader.close()
        dir.close()
    }

    @Test
    fun testRealisticConcurrentMinimumScore() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig())
        val docs = mutableListOf<Document>()
        val numDocs = atLeast(100)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(TextField("body", "random content $i", Field.Store.NO))
            docs.add(doc)
            writer.addDocument(doc)
        }

        val reader = DirectoryReader.open(writer)
        writer.close()

        val terms: Terms = MultiTerms.getTerms(reader, "body")
        var termCount = 0
        var termsEnum: TermsEnum = terms.iterator()
        while (termsEnum.next() != null) {
            termCount++
        }
        assertTrue(termCount > 0)

        val chance = 10.0 / termCount
        termsEnum = terms.iterator()
        while (termsEnum.next() != null) {
            if (random().nextDouble() <= chance) {
                val term = BytesRef.deepCopyOf(termsEnum.term())
                val query: Query = TermQuery(Term("body", term))

                val tdc = doConcurrentSearchWithThreshold(5, 0, query, reader)
                val tdc2 = doSearchWithThreshold(5, 0, query, reader)

                assertEquals(tdc.scoreDocs.map { it.doc }, tdc2.scoreDocs.map { it.doc })
            }
        }

        reader.close()
        dir.close()
    }
}
