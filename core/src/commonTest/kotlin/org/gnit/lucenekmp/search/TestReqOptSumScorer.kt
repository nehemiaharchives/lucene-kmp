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
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.TFIDFSimilarity
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.RandomApproximationQuery
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReqOptSumScorer : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testBasicsMust() {
        doTestBasics(Occur.MUST)
    }

    @Test
    @Throws(IOException::class)
    fun testBasicsFilter() {
        doTestBasics(Occur.FILTER)
    }

    @Throws(IOException::class)
    private fun doTestBasics(reqOccur: Occur) {
        val dir = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig()
                    .setMergePolicy(
                        // retain doc id order
                        newLogMergePolicy(random().nextBoolean()),
                    ),
            )
        var doc = Document()
        doc.add(StringField("f", "foo", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(StringField("f", "foo", Field.Store.NO))
        doc.add(StringField("f", "bar", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(StringField("f", "foo", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(StringField("f", "bar", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(StringField("f", "foo", Field.Store.NO))
        doc.add(StringField("f", "bar", Field.Store.NO))
        w.addDocument(doc)
        w.forceMerge(1)

        val reader = w.reader
        w.close()
        val searcher = newSearcher(reader)
        val query: Query =
            BooleanQuery.Builder()
                .add(ConstantScoreQuery(TermQuery(Term("f", "foo"))), reqOccur)
                .add(ConstantScoreQuery(TermQuery(Term("f", "bar"))), Occur.SHOULD)
                .build()
        val weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
        val context = searcher.indexReader.leaves()[0]

        var scorer = weight.scorer(context)!!
        assertEquals(0, scorer.iterator().nextDoc())
        assertEquals(1, scorer.iterator().nextDoc())
        assertEquals(2, scorer.iterator().nextDoc())
        assertEquals(4, scorer.iterator().nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

        var ss = weight.scorerSupplier(context)!!
        ss.setTopLevelScoringClause()
        scorer = ss.get(Long.MAX_VALUE)!!
        scorer.minCompetitiveScore = Math.nextDown(1f)
        if (reqOccur == Occur.MUST) {
            assertEquals(0, scorer.iterator().nextDoc())
        }
        assertEquals(1, scorer.iterator().nextDoc())
        if (reqOccur == Occur.MUST) {
            assertEquals(2, scorer.iterator().nextDoc())
        }
        assertEquals(4, scorer.iterator().nextDoc())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

        ss = weight.scorerSupplier(context)!!
        ss.setTopLevelScoringClause()
        scorer = ss.get(Long.MAX_VALUE)!!
        scorer.minCompetitiveScore = Math.nextUp(1f)
        if (reqOccur == Occur.MUST) {
            assertEquals(1, scorer.iterator().nextDoc())
            assertEquals(4, scorer.iterator().nextDoc())
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

        ss = weight.scorerSupplier(context)!!
        ss.setTopLevelScoringClause()
        scorer = ss.get(Long.MAX_VALUE)!!
        assertEquals(0, scorer.iterator().nextDoc())
        scorer.minCompetitiveScore = Math.nextUp(1f)
        if (reqOccur == Occur.MUST) {
            assertEquals(1, scorer.iterator().nextDoc())
            assertEquals(4, scorer.iterator().nextDoc())
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMaxBlock() {
        val dir = newDirectory()
        val w =
            IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
        val ft = FieldType()
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        ft.setTokenized(true)
        ft.freeze()

        for (i in 0..<1024) {
            // create documents with an increasing number of As and one B
            val doc = Document()
            doc.add(Field("foo", TermFreqTokenStream("a", i + 1), ft))
            if (random().nextFloat() < 0.5f) {
                doc.add(Field("foo", TermFreqTokenStream("b", 1), ft))
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        w.close()
        val reader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)
        searcher.similarity = SimpleSimilarity()
        // freq == score
        // searcher.setSimilarity(new TestSimilarity.SimpleSimilarity());
        val reqQ: Query = TermQuery(Term("foo", "a"))
        val optQ: Query = TermQuery(Term("foo", "b"))
        val boolQ: Query =
            BooleanQuery.Builder().add(reqQ, Occur.MUST).add(optQ, Occur.SHOULD).build()
        val actual = reqOptScorer(searcher, reqQ, optQ, true)
        val expected =
            searcher
                .createWeight(boolQ, ScoreMode.COMPLETE, 1f)
                .scorer(searcher.indexReader.leaves()[0])!!
        actual.minCompetitiveScore = Math.nextUp(1f)
        // Checks that all blocks are fully visited
        for (i in 0..<1024) {
            assertEquals(i, actual.iterator().nextDoc())
            assertEquals(i, expected.iterator().nextDoc())
            assertEquals(actual.score(), expected.score(), 0f)
        }
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMaxScoreSegment() {
        val dir = newDirectory()
        val w =
            IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
        for (values in listOf(
            arrayOf("A"), // 0
            arrayOf("A"), // 1
            arrayOf(), // 2
            arrayOf("A", "B"), // 3
            arrayOf("A"), // 4
            arrayOf("B"), // 5
            arrayOf("A", "B"), // 6
            arrayOf("B"), // 7
        )) {
            val doc = Document()
            for (value in values) {
                doc.add(StringField("foo", value, Field.Store.NO))
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)
        w.close()

        val reader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)
        val reqQ: Query = ConstantScoreQuery(TermQuery(Term("foo", "A")))
        val optQ: Query = ConstantScoreQuery(TermQuery(Term("foo", "B")))
        var scorer = reqOptScorer(searcher, reqQ, optQ, false)
        assertEquals(0, scorer.iterator().nextDoc())
        assertEquals(1f, scorer.score(), 0f)
        assertEquals(1, scorer.iterator().nextDoc())
        assertEquals(1f, scorer.score(), 0f)
        assertEquals(3, scorer.iterator().nextDoc())
        assertEquals(2f, scorer.score(), 0f)
        assertEquals(4, scorer.iterator().nextDoc())
        assertEquals(1f, scorer.score(), 0f)
        assertEquals(6, scorer.iterator().nextDoc())
        assertEquals(2f, scorer.score(), 0f)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

        scorer = reqOptScorer(searcher, reqQ, optQ, false)
        scorer.minCompetitiveScore = Math.nextDown(1f)
        assertEquals(0, scorer.iterator().nextDoc())
        assertEquals(1f, scorer.score(), 0f)
        assertEquals(1, scorer.iterator().nextDoc())
        assertEquals(1f, scorer.score(), 0f)
        assertEquals(3, scorer.iterator().nextDoc())
        assertEquals(2f, scorer.score(), 0f)
        assertEquals(4, scorer.iterator().nextDoc())
        assertEquals(1f, scorer.score(), 0f)
        assertEquals(6, scorer.iterator().nextDoc())
        assertEquals(2f, scorer.score(), 0f)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

        scorer = reqOptScorer(searcher, reqQ, optQ, false)
        scorer.minCompetitiveScore = Math.nextUp(1f)
        assertEquals(3, scorer.iterator().nextDoc())
        assertEquals(2f, scorer.score(), 0f)
        assertEquals(6, scorer.iterator().nextDoc())
        assertEquals(2f, scorer.score(), 0f)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

        scorer = reqOptScorer(searcher, reqQ, optQ, true)
        scorer.minCompetitiveScore = Math.nextUp(2f)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMustRandomFrequentOpt() {
        doTestRandom(Occur.MUST, 0.5)
    }

    @Test
    @Throws(IOException::class)
    fun testMustRandomRareOpt() {
        doTestRandom(Occur.MUST, 0.05)
    }

    @Test
    @Throws(IOException::class)
    fun testFilterRandomFrequentOpt() {
        doTestRandom(Occur.FILTER, 0.5)
    }

    @Test
    @Throws(IOException::class)
    fun testFilterRandomRareOpt() {
        doTestRandom(Occur.FILTER, 0.05)
    }

    @Throws(IOException::class)
    private fun doTestRandom(reqOccur: Occur, optFreq: Double) {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())
        val numDocs = atLeast(1000)
        for (i in 0..<numDocs) {
            val numAs = if (random().nextBoolean()) 0 else 1 + random().nextInt(5)
            val numBs = if (random().nextDouble() < optFreq) 0 else 1 + random().nextInt(5)
            val doc = Document()
            for (j in 0..<numAs) {
                doc.add(StringField("f", "A", Field.Store.NO))
            }
            for (j in 0..<numBs) {
                doc.add(StringField("f", "B", Field.Store.NO))
            }
            if (random().nextBoolean()) {
                doc.add(StringField("f", "C", Field.Store.NO))
            }
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()
        val searcher = newSearcher(r)

        val mustTerm: Query = TermQuery(Term("f", "A"))
        val shouldTerm: Query = TermQuery(Term("f", "B"))
        var query: Query =
            BooleanQuery.Builder().add(mustTerm, reqOccur).add(shouldTerm, Occur.SHOULD).build()

        var collectorManager =
            TopScoreDocCollectorManager(10, Int.MAX_VALUE)
        var topDocs = searcher.search(query, collectorManager)
        val expected = topDocs.scoreDocs

        // Also test a filtered query, since it does not compute the score on all
        // matches.
        query =
            BooleanQuery.Builder()
                .add(query, Occur.MUST)
                .add(TermQuery(Term("f", "C")), Occur.FILTER)
                .build()

        collectorManager = TopScoreDocCollectorManager(10, Int.MAX_VALUE)
        topDocs = searcher.search(query, collectorManager)
        val expectedFiltered = topDocs.scoreDocs

        CheckHits.checkTopScores(random(), query, searcher)

        run {
            var q: Query =
                BooleanQuery.Builder()
                    .add(RandomApproximationQuery(mustTerm, random()), reqOccur)
                    .add(shouldTerm, Occur.SHOULD)
                    .build()

            collectorManager = TopScoreDocCollectorManager(10, 1)
            topDocs = searcher.search(q, collectorManager)
            var actual = topDocs.scoreDocs
            CheckHits.checkEqual(query, expected, actual)

            q =
                BooleanQuery.Builder()
                    .add(mustTerm, reqOccur)
                    .add(RandomApproximationQuery(shouldTerm, random()), Occur.SHOULD)
                    .build()
            collectorManager = TopScoreDocCollectorManager(10, 1)
            topDocs = searcher.search(q, collectorManager)
            actual = topDocs.scoreDocs
            CheckHits.checkEqual(q, expected, actual)

            q =
                BooleanQuery.Builder()
                    .add(RandomApproximationQuery(mustTerm, random()), reqOccur)
                    .add(RandomApproximationQuery(shouldTerm, random()), Occur.SHOULD)
                    .build()
            collectorManager = TopScoreDocCollectorManager(10, 1)
            topDocs = searcher.search(q, collectorManager)
            actual = topDocs.scoreDocs
            CheckHits.checkEqual(q, expected, actual)
        }

        run {
            val nestedQ: Query =
                BooleanQuery.Builder()
                    .add(query, Occur.MUST)
                    .add(TermQuery(Term("f", "C")), Occur.FILTER)
                    .build()
            CheckHits.checkTopScores(random(), nestedQ, searcher)

            query =
                BooleanQuery.Builder()
                    .add(query, Occur.MUST)
                    .add(
                        RandomApproximationQuery(TermQuery(Term("f", "C")), random()),
                        Occur.FILTER,
                    )
                    .build()

            collectorManager = TopScoreDocCollectorManager(10, 1)
            topDocs = searcher.search(nestedQ, collectorManager)
            val actualFiltered = topDocs.scoreDocs
            CheckHits.checkEqual(nestedQ, expectedFiltered, actualFiltered)
        }

        run {
            query =
                BooleanQuery.Builder()
                    .add(query, reqOccur)
                    .add(TermQuery(Term("f", "C")), Occur.SHOULD)
                    .build()

            CheckHits.checkTopScores(random(), query, searcher)

            query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term("f", "C")), reqOccur)
                    .add(query, Occur.SHOULD)
                    .build()

            CheckHits.checkTopScores(random(), query, searcher)
        }

        r.close()
        dir.close()
    }

    private class SimpleSimilarity : TFIDFSimilarity() {
        override fun lengthNorm(length: Int): Float {
            return 1f
        }

        override fun tf(freq: Float): Float {
            return freq
        }

        override fun idf(docFreq: Long, docCount: Long): Float {
            return 1.0f
        }

        override fun idfExplain(
            collectionStats: CollectionStatistics,
            termStats: TermStatistics
        ): Explanation {
            return Explanation.match(1.0f, "Inexplicable")
        }
    }

    private class TermFreqTokenStream(private val term: String, private val termFreq: Int) : TokenStream() {
        private val termAtt = addAttribute(CharTermAttribute::class)
        private val termFreqAtt = addAttribute(TermFrequencyAttribute::class)
        private var finish = false

        override fun incrementToken(): Boolean {
            if (finish) {
                return false
            }

            clearAttributes()

            termAtt.append(term)
            termFreqAtt.termFrequency = termFreq

            finish = true
            return true
        }

        override fun reset() {
            finish = false
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun reqOptScorer(
            searcher: IndexSearcher,
            reqQ: Query,
            optQ: Query,
            withBlockScore: Boolean
        ): Scorer {
            val reqScorer =
                searcher
                    .createWeight(reqQ, ScoreMode.TOP_SCORES, 1f)
                    .scorer(searcher.indexReader.leaves()[0])!!
            val optScorer =
                searcher
                    .createWeight(optQ, ScoreMode.TOP_SCORES, 1f)
                    .scorer(searcher.indexReader.leaves()[0])!!
            return if (withBlockScore) {
                ReqOptSumScorer(reqScorer, optScorer, ScoreMode.TOP_SCORES)
            } else {
                object : ReqOptSumScorer(reqScorer, optScorer, ScoreMode.TOP_SCORES) {
                    override fun getMaxScore(upTo: Int): Float {
                        return Float.POSITIVE_INFINITY
                    }
                }
            }
        }
    }
}
