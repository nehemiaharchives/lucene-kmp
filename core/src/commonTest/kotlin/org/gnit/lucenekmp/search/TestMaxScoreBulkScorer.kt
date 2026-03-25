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
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

// These basic tests are similar to some of the tests in TestWANDScorer, and may not need to be kept
class TestMaxScoreBulkScorer : LuceneTestCase() {
    @Throws(IOException::class)
    private fun writeDocuments(dir: Directory) {
        IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy())).use { w ->
            for (values in
                listOf(
                    arrayOf("A", "B"), // 0
                    arrayOf("A"), // 1
                    emptyArray(), // 2
                    arrayOf("A", "B", "C"), // 3
                    arrayOf("B"), // 4
                    arrayOf("B", "C"), // 5
                )) {
                val doc = Document()
                for (value in values) {
                    doc.add(StringField("foo", value, Field.Store.NO))
                }
                w.addDocument(doc)
                for (i in 1..<MaxScoreBulkScorer.INNER_WINDOW_SIZE) {
                    w.addDocument(Document())
                }
            }
            w.forceMerge(1)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithTwoDisjunctionClauses() {
        newDirectory().use { dir ->
            writeDocuments(dir)

            DirectoryReader.open(dir).use { reader ->
                val searcher = newSearcher(reader)

                val clause1 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f)
                val clause2 = ConstantScoreQuery(TermQuery(Term("foo", "B")))
                val context = searcher.indexReader.leaves()[0]
                val scorer1 = searcher.createWeight(searcher.rewrite(clause1), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val scorer2 = searcher.createWeight(searcher.rewrite(clause2), ScoreMode.TOP_SCORES, 1f).scorer(context)

                val bulkScorer = MaxScoreBulkScorer(
                    context.reader().maxDoc(),
                    mutableListOf(scorer1!!, scorer2!!),
                    null,
                )

                bulkScorer.score(
                    object : LeafCollector {
                        private var i = 0
                        override var scorer: Scorable? = null

                        override fun collect(doc: Int) {
                            when (i++) {
                                0 -> {
                                    assertEquals(0, doc)
                                    assertEquals(2 + 1f, scorer!!.score(), 0f)
                                }
                                1 -> {
                                    assertEquals(4096, doc)
                                    assertEquals(2f, scorer!!.score(), 0f)
                                }
                                2 -> {
                                    assertEquals(12288, doc)
                                    assertEquals(2 + 1f, scorer!!.score(), 0f)
                                }
                                3 -> {
                                    assertEquals(16384, doc)
                                    assertEquals(1f, scorer!!.score(), 0f)
                                }
                                4 -> {
                                    assertEquals(20480, doc)
                                    assertEquals(1f, scorer!!.score(), 0f)
                                }
                                else -> fail()
                            }
                        }
                    },
                    null,
                    0,
                    DocIdSetIterator.NO_MORE_DOCS,
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFilteredDisjunction() {
        newDirectory().use { dir ->
            writeDocuments(dir)

            DirectoryReader.open(dir).use { reader ->
                val searcher = newSearcher(reader)

                val clause1 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f)
                val clause2 = ConstantScoreQuery(TermQuery(Term("foo", "C")))
                val filter = TermQuery(Term("foo", "B"))
                val context = searcher.indexReader.leaves()[0]
                val scorer1 = searcher.createWeight(searcher.rewrite(clause1), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val scorer2 = searcher.createWeight(searcher.rewrite(clause2), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val filterScorer = searcher.createWeight(searcher.rewrite(filter), ScoreMode.TOP_SCORES, 1f).scorer(context)

                val bulkScorer = MaxScoreBulkScorer(
                    context.reader().maxDoc(),
                    mutableListOf(scorer1!!, scorer2!!),
                    filterScorer,
                )

                bulkScorer.score(
                    object : LeafCollector {
                        private var i = 0
                        override var scorer: Scorable? = null

                        override fun collect(doc: Int) {
                            when (i++) {
                                0 -> {
                                    assertEquals(0, doc)
                                    assertEquals(2f, scorer!!.score(), 0f)
                                }
                                1 -> {
                                    assertEquals(12288, doc)
                                    assertEquals(2 + 1f, scorer!!.score(), 0f)
                                }
                                2 -> {
                                    assertEquals(20480, doc)
                                    assertEquals(1f, scorer!!.score(), 0f)
                                }
                                else -> fail()
                            }
                        }
                    },
                    null,
                    0,
                    DocIdSetIterator.NO_MORE_DOCS,
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFilteredDisjunctionWithSkipping() {
        newDirectory().use { dir ->
            writeDocuments(dir)

            DirectoryReader.open(dir).use { reader ->
                val searcher = newSearcher(reader)

                val clause1 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f)
                val clause2 = ConstantScoreQuery(TermQuery(Term("foo", "C")))
                val filter = TermQuery(Term("foo", "B"))
                val context = searcher.indexReader.leaves()[0]
                val scorer1 = searcher.createWeight(searcher.rewrite(clause1), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val scorer2 = searcher.createWeight(searcher.rewrite(clause2), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val filterScorer = searcher.createWeight(searcher.rewrite(filter), ScoreMode.TOP_SCORES, 1f).scorer(context)

                val bulkScorer = MaxScoreBulkScorer(
                    context.reader().maxDoc(),
                    mutableListOf(scorer1!!, scorer2!!),
                    filterScorer,
                )

                bulkScorer.score(
                    object : LeafCollector {
                        private var i = 0
                        override var scorer: Scorable? = null

                        override fun collect(doc: Int) {
                            when (i++) {
                                0 -> {
                                    assertEquals(0, doc)
                                    assertEquals(2f, scorer!!.score(), 0f)
                                    scorer!!.minCompetitiveScore = Math.nextUp(2f)
                                }
                                1 -> {
                                    assertEquals(12288, doc)
                                    assertEquals(2 + 1f, scorer!!.score(), 0f)
                                    scorer!!.minCompetitiveScore = Math.nextUp(2 + 1f)
                                }
                                else -> fail()
                            }
                        }
                    },
                    null,
                    0,
                    DocIdSetIterator.NO_MORE_DOCS,
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithTwoDisjunctionClausesAndSkipping() {
        newDirectory().use { dir ->
            writeDocuments(dir)

            DirectoryReader.open(dir).use { reader ->
                val searcher = newSearcher(reader)

                val clause1 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f)
                val clause2 = ConstantScoreQuery(TermQuery(Term("foo", "B")))
                val context = searcher.indexReader.leaves()[0]
                val scorer1 = searcher.createWeight(searcher.rewrite(clause1), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val scorer2 = searcher.createWeight(searcher.rewrite(clause2), ScoreMode.TOP_SCORES, 1f).scorer(context)

                val bulkScorer = MaxScoreBulkScorer(
                    context.reader().maxDoc(),
                    mutableListOf(scorer1!!, scorer2!!),
                    null,
                )

                bulkScorer.score(
                    object : LeafCollector {
                        private var i = 0
                        override var scorer: Scorable? = null

                        override fun collect(doc: Int) {
                            when (i++) {
                                0 -> {
                                    assertEquals(0, doc)
                                    assertEquals(2 + 1f, scorer!!.score(), 0f)
                                }
                                1 -> {
                                    assertEquals(4096, doc)
                                    assertEquals(2f, scorer!!.score(), 0f)
                                    // simulate top-2 retrieval
                                    scorer!!.minCompetitiveScore = Math.nextUp(2f)
                                }
                                2 -> {
                                    assertEquals(12288, doc)
                                    assertEquals(2 + 1f, scorer!!.score(), 0f)
                                    scorer!!.minCompetitiveScore = Math.nextUp(2 + 1f)
                                }
                                else -> fail()
                            }
                        }
                    },
                    null,
                    0,
                    DocIdSetIterator.NO_MORE_DOCS,
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithThreeDisjunctionClauses() {
        newDirectory().use { dir ->
            writeDocuments(dir)

            DirectoryReader.open(dir).use { reader ->
                val searcher = newSearcher(reader)

                val clause1 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f)
                val clause2 = ConstantScoreQuery(TermQuery(Term("foo", "B")))
                val clause3 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "C"))), 3f)
                val context = searcher.indexReader.leaves()[0]
                val scorer1 = searcher.createWeight(searcher.rewrite(clause1), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val scorer2 = searcher.createWeight(searcher.rewrite(clause2), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val scorer3 = searcher.createWeight(searcher.rewrite(clause3), ScoreMode.TOP_SCORES, 1f).scorer(context)

                val bulkScorer = MaxScoreBulkScorer(
                    context.reader().maxDoc(),
                    mutableListOf(scorer1!!, scorer2!!, scorer3!!),
                    null,
                )

                bulkScorer.score(
                    object : LeafCollector {
                        private var i = 0
                        override var scorer: Scorable? = null

                        override fun collect(doc: Int) {
                            when (i++) {
                                0 -> {
                                    assertEquals(0, doc)
                                    assertEquals(2 + 1f, scorer!!.score(), 0f)
                                }
                                1 -> {
                                    assertEquals(4096, doc)
                                    assertEquals(2f, scorer!!.score(), 0f)
                                }
                                2 -> {
                                    assertEquals(12288, doc)
                                    assertEquals(2 + 1 + 3f, scorer!!.score(), 0f)
                                }
                                3 -> {
                                    assertEquals(16384, doc)
                                    assertEquals(1f, scorer!!.score(), 0f)
                                }
                                4 -> {
                                    assertEquals(20480, doc)
                                    assertEquals(1 + 3f, scorer!!.score(), 0f)
                                }
                                else -> fail()
                            }
                        }
                    },
                    null,
                    0,
                    DocIdSetIterator.NO_MORE_DOCS,
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithThreeDisjunctionClausesAndSkipping() {
        newDirectory().use { dir ->
            writeDocuments(dir)

            DirectoryReader.open(dir).use { reader ->
                val searcher = newSearcher(reader)

                val clause1 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f)
                val clause2 = ConstantScoreQuery(TermQuery(Term("foo", "B")))
                val clause3 = BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "C"))), 3f)
                val context = searcher.indexReader.leaves()[0]
                val scorer1 = searcher.createWeight(searcher.rewrite(clause1), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val scorer2 = searcher.createWeight(searcher.rewrite(clause2), ScoreMode.TOP_SCORES, 1f).scorer(context)
                val scorer3 = searcher.createWeight(searcher.rewrite(clause3), ScoreMode.TOP_SCORES, 1f).scorer(context)

                val bulkScorer = MaxScoreBulkScorer(
                    context.reader().maxDoc(),
                    mutableListOf(scorer1!!, scorer2!!, scorer3!!),
                    null,
                )

                bulkScorer.score(
                    object : LeafCollector {
                        private var i = 0
                        override var scorer: Scorable? = null

                        override fun collect(doc: Int) {
                            when (i++) {
                                0 -> {
                                    assertEquals(0, doc)
                                    assertEquals(2 + 1f, scorer!!.score(), 0f)
                                }
                                1 -> {
                                    assertEquals(4096, doc)
                                    assertEquals(2f, scorer!!.score(), 0f)
                                    // simulate top-2 retrieval
                                    scorer!!.minCompetitiveScore = Math.nextUp(2f)
                                }
                                2 -> {
                                    assertEquals(12288, doc)
                                    assertEquals(2 + 1 + 3f, scorer!!.score(), 0f)
                                    scorer!!.minCompetitiveScore = Math.nextUp(2 + 1f)
                                }
                                3 -> {
                                    assertEquals(20480, doc)
                                    assertEquals(1 + 3f, scorer!!.score(), 0f)
                                    scorer!!.minCompetitiveScore = Math.nextUp(1 + 3f)
                                }
                                else -> fail()
                            }
                        }
                    },
                    null,
                    0,
                    DocIdSetIterator.NO_MORE_DOCS,
                )
            }
        }
    }

    private class FakeScorer(val toString: String) : Scorer() {
        var docID = -1
        var maxScoreUpTo = DocIdSetIterator.NO_MORE_DOCS
        var maxScore = 1f
        var cost = 10

        override fun docID(): Int {
            return docID
        }

        override fun iterator(): DocIdSetIterator {
            return DocIdSetIterator.all(cost) // just so that it exposes the right cost
        }

        @Throws(IOException::class)
        override fun advanceShallow(target: Int): Int {
            return maxScoreUpTo
        }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            return maxScore
        }

        @Throws(IOException::class)
        override fun score(): Float {
            throw UnsupportedOperationException()
        }

        override fun toString(): String {
            return toString
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDeletes() {
        val dir = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy())
        val w = IndexWriter(dir, iwc)
        val doc1 = Document()
        doc1.add(StringField("field", "foo", Field.Store.NO))
        doc1.add(StringField("field", "bar", Field.Store.NO))
        doc1.add(StringField("field", "quux", Field.Store.NO))
        val doc2 = Document()
        val doc3 = Document()
        for (field: IndexableField in doc1) {
            doc2.add(field)
            doc3.add(field)
        }
        doc1.add(StringField("id", "1", Field.Store.NO))
        doc2.add(StringField("id", "2", Field.Store.NO))
        doc3.add(StringField("id", "3", Field.Store.NO))
        w.addDocument(doc1)
        w.addDocument(doc2)
        w.addDocument(doc3)

        w.forceMerge(1)

        val reader: IndexReader = DirectoryReader.open(w)
        w.close()

        val query = BooleanQuery.Builder()
            .add(BoostQuery(ConstantScoreQuery(TermQuery(Term("field", "foo"))), 1f), Occur.SHOULD)
            .add(BoostQuery(ConstantScoreQuery(TermQuery(Term("field", "bar"))), 1.5f), Occur.SHOULD)
            .add(BoostQuery(ConstantScoreQuery(TermQuery(Term("field", "quux"))), 0.1f), Occur.SHOULD)
            .build()

        val searcher = newSearcher(reader)
        val weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)

        val liveDocs = object : Bits {
            override fun get(index: Int): Boolean {
                return index == 1
            }

            override fun length(): Int {
                return 3
            }
        }

        // Test min competitive scores that exercise different execution modes
        for (minCompetitiveScore in floatArrayOf(0f, 1f, 1.2f, 2f)) {
            val bulkScorer = weight.bulkScorer(searcher.indexReader.leaves()[0])!!
            val collector = object : LeafCollector {
                var i = 0
                override var scorer: Scorable? = null
                    set(value) {
                        field = value
                        value!!.minCompetitiveScore = minCompetitiveScore
                    }

                override fun collect(doc: Int) {
                    assertEquals(1, doc)
                    assertEquals(0, i++)
                }

                override fun finish() {
                    assertEquals(1, i)
                }
            }
            bulkScorer.score(collector, liveDocs, 0, DocIdSetIterator.NO_MORE_DOCS)
            collector.finish()
        }

        reader.close()
        dir.close()
    }

    // This test simulates what happens over time for the query `the quick fox` as collection
    // progresses and the minimum competitive score increases.
    @Test
    @Throws(IOException::class)
    fun testPartition() {
        val the = FakeScorer("the")
        the.cost = 9_000
        the.maxScore = 0.1f
        val quick = FakeScorer("quick")
        quick.cost = 1_000
        quick.maxScore = 1f
        val fox = FakeScorer("fox")
        fox.cost = 900
        fox.maxScore = 1.1f

        val bulkScorer = MaxScoreBulkScorer(10_000, mutableListOf(the, quick, fox), null)
        the.docID = 4
        the.maxScoreUpTo = 130
        quick.docID = 4
        quick.maxScoreUpTo = 999
        fox.docID = 10
        fox.maxScoreUpTo = 1_200

        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(0, bulkScorer.firstEssentialScorer) // all clauses are essential
        assertEquals(3, bulkScorer.firstRequiredScorer) // no required clauses

        // less than the minimum score of every clause
        bulkScorer.minCompetitiveScore = 0.09f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(0, bulkScorer.firstEssentialScorer) // all clauses are still essential
        assertEquals(3, bulkScorer.firstRequiredScorer) // no required clauses

        // equal to the maximum score of `the`
        bulkScorer.minCompetitiveScore = 0.1f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(0, bulkScorer.firstEssentialScorer) // all clauses are still essential
        assertEquals(3, bulkScorer.firstRequiredScorer) // no required clauses

        // gt than the minimum score of `the`
        bulkScorer.minCompetitiveScore = 0.11f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(1, bulkScorer.firstEssentialScorer) // the is non essential
        assertEquals(3, bulkScorer.firstRequiredScorer) // no required clauses
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)

        // equal to the sum of the max scores of the and quick
        bulkScorer.minCompetitiveScore = 1.1f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(1, bulkScorer.firstEssentialScorer) // the is non essential
        assertEquals(3, bulkScorer.firstRequiredScorer) // no required clauses
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)

        // greater than the sum of the max scores of the and quick
        bulkScorer.minCompetitiveScore = 1.11f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(2, bulkScorer.firstEssentialScorer) // the and quick are non essential
        assertEquals(2, bulkScorer.firstRequiredScorer) // fox is required
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)
        assertSame(quick, bulkScorer.allScorers[1]!!.scorer)
        assertSame(fox, bulkScorer.allScorers[2]!!.scorer)

        // equal to the sum of the max scores of the and fox
        bulkScorer.minCompetitiveScore = 1.2f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(2, bulkScorer.firstEssentialScorer) // the and quick are non essential
        assertEquals(2, bulkScorer.firstRequiredScorer) // fox is required
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)
        assertSame(quick, bulkScorer.allScorers[1]!!.scorer)
        assertSame(fox, bulkScorer.allScorers[2]!!.scorer)

        // greater than the sum of the max scores of the and fox
        bulkScorer.minCompetitiveScore = 1.21f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(2, bulkScorer.firstEssentialScorer) // the and quick are non essential
        assertEquals(1, bulkScorer.firstRequiredScorer) // quick and fox are required
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)
        assertSame(quick, bulkScorer.allScorers[1]!!.scorer)
        assertSame(fox, bulkScorer.allScorers[2]!!.scorer)

        // equal to the sum of the max scores of quick and fox
        bulkScorer.minCompetitiveScore = 2.1f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(2, bulkScorer.firstEssentialScorer) // the and quick are non essential
        assertEquals(1, bulkScorer.firstRequiredScorer) // quick and fox are required
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)
        assertSame(quick, bulkScorer.allScorers[1]!!.scorer)
        assertSame(fox, bulkScorer.allScorers[2]!!.scorer)

        // greater than the sum of the max scores of quick and fox
        bulkScorer.minCompetitiveScore = 2.11f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(2, bulkScorer.firstEssentialScorer) // the and quick are non essential
        assertEquals(0, bulkScorer.firstRequiredScorer) // all terms are required
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)
        assertSame(quick, bulkScorer.allScorers[1]!!.scorer)
        assertSame(fox, bulkScorer.allScorers[2]!!.scorer)

        // greater than the sum of the max scores of quick and fox
        bulkScorer.minCompetitiveScore = 2.11f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(2, bulkScorer.firstEssentialScorer) // the and quick are non essential
        assertEquals(0, bulkScorer.firstRequiredScorer) // all terms are required
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)
        assertSame(quick, bulkScorer.allScorers[1]!!.scorer)
        assertSame(fox, bulkScorer.allScorers[2]!!.scorer)

        // equal to the sum of the max scores of all terms
        bulkScorer.minCompetitiveScore = 2.2f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertTrue(bulkScorer.partitionScorers())
        assertEquals(2, bulkScorer.firstEssentialScorer) // the and quick are non essential
        assertEquals(0, bulkScorer.firstRequiredScorer) // all terms are required
        assertSame(the, bulkScorer.allScorers[0]!!.scorer)
        assertSame(quick, bulkScorer.allScorers[1]!!.scorer)
        assertSame(fox, bulkScorer.allScorers[2]!!.scorer)

        // greater than the sum of the max scores of all terms
        bulkScorer.minCompetitiveScore = 2.21f
        bulkScorer.allScorers.shuffle(random())
        bulkScorer.updateMaxWindowScores(4, 100)
        assertFalse(bulkScorer.partitionScorers()) // no possible match in this window
    }
}
