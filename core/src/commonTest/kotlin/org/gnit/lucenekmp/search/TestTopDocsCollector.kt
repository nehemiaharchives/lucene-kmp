/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                collector.forEachScoreDoc { scoreDoc ->
                    myTopDocsCollector.insertWithOverflow(scoreDoc)
                }
            }
            myTopDocsCollector.resetTotalHits(totalHits)
            return myTopDocsCollector
        }
    }

    private class MyTopDocsCollector(size: Int) :
        TopDocsCollector<ScoreDoc>(HitQueue(size, false) as PriorityQueue<ScoreDoc>) {
        override var weight: Weight? = null

        override fun newTopDocs(results: Array<ScoreDoc>?, start: Int): TopDocs {
            if (results == null) {
                return EMPTY_TOPDOCS
            }

            return TopDocs(TotalHits(totalHits.toLong(), totalHitsRelation), results)
        }

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            val base = context.docBase
            return object : LeafCollector {
                override var scorer: Scorable? = null
                    set(value) {
                        // Don't do anything. Assign scores in random
                        field = value
                    }

                override fun collect(doc: Int) {
                    ++totalHits
                    pq.insertWithOverflow(ScoreDoc(doc + base, scores[context.docBase + doc]))
                }
            }
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE_NO_SCORES
        }

        fun forEachScoreDoc(consumer: (ScoreDoc) -> Unit) {
            pq.forEach(consumer)
        }

        fun insertWithOverflow(scoreDoc: ScoreDoc) {
            pq.insertWithOverflow(scoreDoc)
        }

        fun resetTotalHits(totalHits: Int) {
            this.totalHits = totalHits
        }
    }

    // Scores array to be used by MyTopDocsCollector. If it is changed, MAX_SCORE
    // must also change.
    companion object {
        private val scores =
            floatArrayOf(
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

        @Throws(IOException::class)
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

    @Throws(IOException::class)
    private fun doSearch(numResults: Int): TopDocsCollector<ScoreDoc> {
        val q: Query = MatchAllDocsQuery()
        val searcher = newSearcher(reader)
        return searcher.search(q, MyTopDocsCollectorMananger(numResults))
    }

    @Throws(IOException::class)
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
    @Throws(Exception::class)
    fun setUp() {
        // populate an index with 30 documents, this should be enough for the test.
        // The documents have no content - the test uses MatchAllDocsQuery().
        dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        for (i in 0..<30) {
            writer.addDocument(Document())
        }
        reader = writer.reader
        writer.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidArguments() {
        val numResults = 5
        val tdc = doSearch(numResults)

        // start < 0
        var exception =
            expectThrows(IllegalArgumentException::class) {
                tdc.topDocs(-1)
            }

        assertEquals(
            "Expected value of starting position is between 0 and 5, got -1",
            exception.message
        )

        // start == pq.size()
        assertEquals(0, tdc.topDocs(numResults).scoreDocs.size)

        // howMany < 0
        exception =
            expectThrows(IllegalArgumentException::class) {
                tdc.topDocs(0, -1)
            }

        assertEquals(
            "Number of hits requested must be greater than 0 but value was -1",
            exception.message
        )
    }

    @Test
    @Throws(Exception::class)
    fun testZeroResults() {
        val tdc: TopDocsCollector<ScoreDoc> = MyTopDocsCollector(5)
        assertEquals(0, tdc.topDocs(0, 1).scoreDocs.size)
    }

    @Test
    @Throws(Exception::class)
    fun testFirstResultsPage() {
        val tdc = doSearch(15)
        assertEquals(10, tdc.topDocs(0, 10).scoreDocs.size)
    }

    @Test
    @Throws(Exception::class)
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
    @Throws(Exception::class)
    fun testGetAllResults() {
        val tdc = doSearch(15)
        assertEquals(15, tdc.topDocs().scoreDocs.size)
    }

    @Test
    @Throws(Exception::class)
    fun testGetResultsFromStart() {
        var tdc = doSearch(15)
        // should bring all results
        assertEquals(15, tdc.topDocs(0).scoreDocs.size)

        tdc = doSearch(15)
        // get the last 5 only.
        assertEquals(5, tdc.topDocs(10).scoreDocs.size)
    }

    @Test
    @Throws(Exception::class)
    fun testIllegalArguments() {
        val tdc = doSearch(15)

        var expected =
            expectThrows(IllegalArgumentException::class) {
                tdc.topDocs(-1)
            }

        assertEquals(
            "Expected value of starting position is between 0 and 15, got -1",
            expected.message
        )

        expected =
            expectThrows(IllegalArgumentException::class) {
                tdc.topDocs(9, -1)
            }

        assertEquals(
            "Number of hits requested must be greater than 0 but value was -1",
            expected.message
        )
    }

    // This does not test the PQ's correctness, but whether topDocs()
    // implementations return the results in decreasing score order.
    @Test
    @Throws(Exception::class)
    fun testResultsOrder() {
        val tdc = doSearch(15)
        val sd = tdc.topDocs().scoreDocs

        assertEquals(MAX_SCORE, sd[0].score, 0f)
        for (i in 1..<sd.size) {
            assertTrue(sd[i - 1].score >= sd[i].score)
        }
    }

    private class Score : Scorable() {
        var score = 0f
        var minCompetitiveScoreValue: Float? = null

        override var minCompetitiveScore: Float
            get() = minCompetitiveScoreValue ?: 0f
            set(score) {
                assert(minCompetitiveScoreValue == null || score >= minCompetitiveScoreValue!!)
                minCompetitiveScoreValue = score
            }

        @Throws(IOException::class)
        override fun score(): Float {
            return score
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSetMinCompetitiveScore() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
        val doc = Document()
        w.addDocuments(mutableListOf(doc, doc, doc, doc))
        w.flush()
        w.addDocuments(mutableListOf(doc, doc))
        w.flush()
        val reader = DirectoryReader.open(w)
        assertEquals(2, reader.leaves().size)
        w.close()

        val collectorManager = TopScoreDocCollectorManager(2, 2)
        val collector = collectorManager.newCollector()
        var scorer = Score()

        var leafCollector = collector.getLeafCollector(reader.leaves()[0])
        leafCollector.scorer = scorer
        assertNull(scorer.minCompetitiveScoreValue)

        scorer.score = 1f
        leafCollector.collect(0)
        assertNull(scorer.minCompetitiveScoreValue)

        scorer.score = 2f
        leafCollector.collect(1)
        assertNull(scorer.minCompetitiveScoreValue)

        scorer.score = 3f
        leafCollector.collect(2)
        assertEquals(Math.nextUp(2f), requireNotNull(scorer.minCompetitiveScoreValue), 0f)

        scorer.score = 0.5f
        // Make sure we do not call setMinCompetitiveScore for non-competitive hits
        scorer.minCompetitiveScoreValue = null
        leafCollector.collect(3)
        assertNull(scorer.minCompetitiveScoreValue)

        scorer.score = 4f
        leafCollector.collect(4)
        assertEquals(Math.nextUp(3f), requireNotNull(scorer.minCompetitiveScoreValue), 0f)

        // Make sure the min score is set on scorers on new segments
        scorer = Score()
        leafCollector = collector.getLeafCollector(reader.leaves()[1])
        leafCollector.scorer = scorer
        assertEquals(Math.nextUp(3f), requireNotNull(scorer.minCompetitiveScoreValue), 0f)

        scorer.score = 1f
        leafCollector.collect(0)
        assertEquals(Math.nextUp(3f), requireNotNull(scorer.minCompetitiveScoreValue), 0f)

        scorer.score = 4f
        leafCollector.collect(1)
        assertEquals(Math.nextUp(4f), requireNotNull(scorer.minCompetitiveScoreValue), 0f)

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSharedCountCollectorManager() {
        val q: Query = MatchAllDocsQuery()
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
        val doc = Document()
        w.addDocuments(mutableListOf(doc, doc, doc, doc))
        w.flush()
        w.addDocuments(mutableListOf(doc, doc))
        w.flush()
        val reader = DirectoryReader.open(w)
        assertEquals(2, reader.leaves().size)
        w.close()

        val tdc = doConcurrentSearchWithThreshold(5, 10, q, reader)
        val tdc2 = doSearchWithThreshold(5, 10, q, reader)

        CheckHits.checkEqual(q, tdc.scoreDocs, tdc2.scoreDocs)

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTotalHits() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
        val doc = Document()
        w.addDocuments(mutableListOf(doc, doc, doc, doc))
        w.flush()
        w.addDocuments(mutableListOf(doc, doc, doc, doc, doc, doc))
        w.flush()
        val reader = DirectoryReader.open(w)
        assertEquals(2, reader.leaves().size)
        w.close()

        for (totalHitsThreshold in 0..<20) {
            val collectorManager = TopScoreDocCollectorManager(2, totalHitsThreshold)
            val collector = collectorManager.newCollector()
            val scorer = Score()

            var leafCollector = collector.getLeafCollector(reader.leaves()[0])
            leafCollector.scorer = scorer

            scorer.score = 3f
            leafCollector.collect(0)

            scorer.score = 3f
            leafCollector.collect(1)

            leafCollector = collector.getLeafCollector(reader.leaves()[1])
            leafCollector.scorer = scorer

            scorer.score = 3f
            leafCollector.collect(1)

            scorer.score = 4f
            leafCollector.collect(1)

            val topDocs = collector.topDocs()
            assertEquals(4, topDocs.totalHits.value)
            assertEquals(totalHitsThreshold < 4, scorer.minCompetitiveScoreValue != null)
            assertEquals(
                TotalHits(
                    4,
                    if (totalHitsThreshold < 4) {
                        TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
                    } else {
                        TotalHits.Relation.EQUAL_TO
                    }
                ),
                topDocs.totalHits
            )
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRelationVsTopDocsCount() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
        try {
            val doc = Document()
            doc.add(TextField("f", "foo bar", Store.NO))
            w.addDocuments(mutableListOf(doc, doc, doc, doc, doc))
            w.flush()
            w.addDocuments(mutableListOf(doc, doc, doc, doc, doc))
            w.flush()

            val reader = DirectoryReader.open(w)
            try {
                val searcher = IndexSearcher(reader)
                var collectorManager = TopScoreDocCollectorManager(2, 10)
                var topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
                assertEquals(10, topDocs.totalHits.value)
                assertEquals(TotalHits.Relation.EQUAL_TO, topDocs.totalHits.relation)

                collectorManager = TopScoreDocCollectorManager(2, 2)
                topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
                assertTrue(10 >= topDocs.totalHits.value)
                assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, topDocs.totalHits.relation)

                collectorManager = TopScoreDocCollectorManager(10, 2)
                topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
                assertEquals(10, topDocs.totalHits.value)
                assertEquals(TotalHits.Relation.EQUAL_TO, topDocs.totalHits.relation)
            } finally {
                reader.close()
            }
        } finally {
            w.close()
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testConcurrentMinScore() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
        val doc = Document()
        w.addDocuments(mutableListOf(doc, doc, doc, doc, doc))
        w.flush()
        w.addDocuments(mutableListOf(doc, doc, doc, doc, doc, doc))
        w.flush()
        w.addDocuments(mutableListOf(doc, doc))
        w.flush()
        val reader = DirectoryReader.open(w)
        assertEquals(3, reader.leaves().size)
        w.close()

        val manager: CollectorManager<TopScoreDocCollector, TopDocs> = TopScoreDocCollectorManager(2, 0)
        val collector = manager.newCollector()
        val collector2 = manager.newCollector()
        assertTrue(collector.minScoreAcc === collector2.minScoreAcc)
        val minValueChecker = collector.minScoreAcc!!
        // force the check of the global minimum score on every round
        minValueChecker.modInterval = 0

        val scorer = Score()
        val scorer2 = Score()

        val leafCollector = collector.getLeafCollector(reader.leaves()[0])
        leafCollector.scorer = scorer
        val leafCollector2 = collector2.getLeafCollector(reader.leaves()[1])
        leafCollector2.scorer = scorer2

        scorer.score = 3f
        leafCollector.collect(0)
        assertEquals(Long.MIN_VALUE, minValueChecker.raw)
        assertNull(scorer.minCompetitiveScoreValue)

        scorer2.score = 6f
        leafCollector2.collect(0)
        assertEquals(Long.MIN_VALUE, minValueChecker.raw)
        assertNull(scorer2.minCompetitiveScoreValue)

        scorer.score = 2f
        leafCollector.collect(1)
        assertEquals(Long.MIN_VALUE, minValueChecker.raw)
        assertNull(scorer.minCompetitiveScoreValue)

        scorer2.score = 9f
        leafCollector2.collect(1)
        assertEquals(Long.MIN_VALUE, minValueChecker.raw)
        assertNull(scorer2.minCompetitiveScoreValue)

        scorer2.score = 7f
        leafCollector2.collect(2)
        assertEquals(MaxScoreAccumulator.toScore(minValueChecker.raw), 7f, 0f)
        assertNull(scorer.minCompetitiveScoreValue)
        assertEquals(Math.nextUp(7f), requireNotNull(scorer2.minCompetitiveScoreValue), 0f)

        scorer2.score = 1f
        leafCollector2.collect(3)
        assertEquals(MaxScoreAccumulator.toScore(minValueChecker.raw), 7f, 0f)
        assertNull(scorer.minCompetitiveScoreValue)
        assertEquals(Math.nextUp(7f), requireNotNull(scorer2.minCompetitiveScoreValue), 0f)

        scorer.score = 10f
        leafCollector.collect(2)
        assertEquals(MaxScoreAccumulator.toScore(minValueChecker.raw), 7f, 0f)
        assertEquals(7f, requireNotNull(scorer.minCompetitiveScoreValue), 0f)
        assertEquals(Math.nextUp(7f), requireNotNull(scorer2.minCompetitiveScoreValue), 0f)

        scorer.score = 11f
        leafCollector.collect(3)
        assertEquals(MaxScoreAccumulator.toScore(minValueChecker.raw), 10f, 0f)
        assertEquals(Math.nextUp(10f), requireNotNull(scorer.minCompetitiveScoreValue), 0f)
        assertEquals(Math.nextUp(7f), requireNotNull(scorer2.minCompetitiveScoreValue), 0f)

        val collector3 = manager.newCollector()
        val leafCollector3 = collector3.getLeafCollector(reader.leaves()[2])
        val scorer3 = Score()
        leafCollector3.scorer = scorer3
        assertEquals(Math.nextUp(10f), requireNotNull(scorer3.minCompetitiveScoreValue), 0f)

        scorer3.score = 1f
        leafCollector3.collect(0)
        assertEquals(10f, MaxScoreAccumulator.toScore(minValueChecker.raw), 0f)
        assertEquals(Math.nextUp(10f), requireNotNull(scorer3.minCompetitiveScoreValue), 0f)

        scorer.score = 11f
        leafCollector.collect(4)
        assertEquals(11f, MaxScoreAccumulator.toScore(minValueChecker.raw), 0f)
        assertEquals(Math.nextUp(11f), requireNotNull(scorer.minCompetitiveScoreValue), 0f)
        assertEquals(Math.nextUp(7f), requireNotNull(scorer2.minCompetitiveScoreValue), 0f)
        assertEquals(Math.nextUp(10f), requireNotNull(scorer3.minCompetitiveScoreValue), 0f)

        scorer3.score = 2f
        leafCollector3.collect(1)
        assertEquals(MaxScoreAccumulator.toScore(minValueChecker.raw), 11f, 0f)
        assertEquals(Math.nextUp(11f), requireNotNull(scorer.minCompetitiveScoreValue), 0f)
        assertEquals(Math.nextUp(7f), requireNotNull(scorer2.minCompetitiveScoreValue), 0f)
        assertEquals(Math.nextUp(11f), requireNotNull(scorer3.minCompetitiveScoreValue), 0f)

        val topDocs = manager.reduce(mutableListOf(collector, collector2, collector3))!!
        assertEquals(11, topDocs.totalHits.value)
        assertEquals(TotalHits(11, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), topDocs.totalHits)

        leafCollector.scorer = scorer
        leafCollector2.scorer = scorer2
        leafCollector3.scorer = scorer3

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomMinCompetitiveScore() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())
        val numDocs = atLeast(1000)
        for (i in 0..<numDocs) {
            val numAs = 1 + random().nextInt(5)
            val numBs = if (random().nextFloat() < 0.5f) 0 else 1 + random().nextInt(5)
            val numCs = if (random().nextFloat() < 0.1f) 0 else 1 + random().nextInt(5)
            val doc = Document()
            for (j in 0..<numAs) {
                doc.add(StringField("f", "A", Field.Store.NO))
            }
            for (j in 0..<numBs) {
                doc.add(StringField("f", "B", Field.Store.NO))
            }
            for (j in 0..<numCs) {
                doc.add(StringField("f", "C", Field.Store.NO))
            }
            w.addDocument(doc)
        }
        val indexReader = w.reader
        w.close()
        val queries =
            arrayOf<Query>(
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

            assertTrue(tdc.totalHits.value > 0)
            assertTrue(tdc2.totalHits.value > 0)
            CheckHits.checkEqual(query, tdc.scoreDocs, tdc2.scoreDocs)
        }

        indexReader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRealisticConcurrentMinimumScore() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        LineFileDocs(random()).use { docs ->
            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                writer.addDocument(docs.nextDoc())
            }
        }

        val reader = writer.reader
        writer.close()

        val terms: Terms = MultiTerms.getTerms(reader, "body")!!
        var termCount = 0
        var termsEnum: TermsEnum = terms.iterator()
        while (termsEnum.next() != null) {
            termCount++
        }
        assertTrue(termCount > 0)

        // Target ~10 terms to search:
        val chance = 10.0 / termCount
        termsEnum = terms.iterator()
        while (termsEnum.next() != null) {
            if (random().nextDouble() <= chance) {
                val term = BytesRef.deepCopyOf(requireNotNull(termsEnum.term()))
                val query: Query = TermQuery(Term("body", term))

                val tdc = doConcurrentSearchWithThreshold(5, 0, query, reader)
                val tdc2 = doSearchWithThreshold(5, 0, query, reader)

                CheckHits.checkEqual(query, tdc.scoreDocs, tdc2.scoreDocs)
            }
        }

        reader.close()
        dir.close()
    }
}
