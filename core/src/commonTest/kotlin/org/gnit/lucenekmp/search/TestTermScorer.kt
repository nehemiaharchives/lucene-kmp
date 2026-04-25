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
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class TestTermScorer : LuceneTestCase() {
    protected lateinit var directory: Directory

    companion object {
        private const val FIELD: String = "field"
    }

    protected var values = arrayOf("all", "dogs dogs", "like", "playing", "fetch", "all")
    protected lateinit var indexSearcher: IndexSearcher
    protected lateinit var indexReader: LeafReader

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        directory = newDirectory()

        val writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(newLogMergePolicy())
                    .setSimilarity(ClassicSimilarity())
            )
        for (i in values.indices) {
            val doc = Document()
            doc.add(newTextField(FIELD, values[i], Field.Store.YES))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        indexReader = getOnlyLeafReader(writer.reader)
        writer.close()
        indexSearcher = newSearcher(indexReader, false)
        indexSearcher.similarity = ClassicSimilarity()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        indexReader.close()
        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun test() {

        val allTerm = Term(FIELD, "all")
        val termQuery = TermQuery(allTerm)

        val weight = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE, 1f)
        assertTrue(indexSearcher.topReaderContext is LeafReaderContext)
        val context = indexSearcher.topReaderContext as LeafReaderContext
        val ts = weight.bulkScorer(context)
        // we have 2 documents with the term all in them, one document for all the
        // other values
        val docs = ArrayList<TestHit>()
        // must call next first

        ts!!.score(
            object : SimpleCollector() {
                private var base = 0
                private var scorerValue: Scorable? = null
                override var weight: Weight? = null

                override var scorer: Scorable?
                    get() = scorerValue
                    set(value) {
                        scorerValue = value
                    }

                @Throws(IOException::class)
                override fun collect(doc: Int) {
                    val score = scorerValue!!.score()
                    val actualDoc = doc + base
                    docs.add(TestHit(actualDoc, score))
                    assertTrue(score > 0, "score $score is not greater than 0")
                    assertTrue(actualDoc == 0 || actualDoc == 5, "Doc: $actualDoc does not equal 0 or doc does not equal 5")
                }

                @Throws(IOException::class)
                override fun doSetNextReader(context: LeafReaderContext) {
                    base = context.docBase
                }

                override fun scoreMode(): ScoreMode {
                    return ScoreMode.COMPLETE
                }
            },
            null,
            0,
            DocIdSetIterator.NO_MORE_DOCS
        )
        assertTrue(docs.size == 2, "docs Size: ${docs.size} is not: 2")
        val doc0 = docs[0]
        val doc5 = docs[1]
        // The scores should be the same
        assertTrue(doc0.score == doc5.score, "${doc0.score} does not equal: ${doc5.score}")
    }

    @Test
    @Throws(Exception::class)
    fun testNext() {

        val allTerm = Term(FIELD, "all")
        val termQuery = TermQuery(allTerm)

        val weight = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE, 1f)
        assertTrue(indexSearcher.topReaderContext is LeafReaderContext)
        val context = indexSearcher.topReaderContext as LeafReaderContext
        val ts = weight.scorer(context)
        assertTrue(ts!!.iterator().nextDoc() != DocIdSetIterator.NO_MORE_DOCS, "next did not return a doc")
        assertTrue(ts.iterator().nextDoc() != DocIdSetIterator.NO_MORE_DOCS, "next did not return a doc")
        assertTrue(ts.iterator().nextDoc() == DocIdSetIterator.NO_MORE_DOCS, "next returned a doc and it should not have")
    }

    @Test
    @Throws(Exception::class)
    fun testAdvance() {

        val allTerm = Term(FIELD, "all")
        val termQuery = TermQuery(allTerm)

        val weight = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE, 1f)
        assertTrue(indexSearcher.topReaderContext is LeafReaderContext)
        val context = indexSearcher.topReaderContext as LeafReaderContext
        val ts = weight.scorer(context)
        assertTrue(ts!!.iterator().advance(3) != DocIdSetIterator.NO_MORE_DOCS, "Didn't skip")
        // The next doc should be doc 5
        assertTrue(ts.docID() == 5, "doc should be number 5")
    }

    private class TestHit(var doc: Int, var score: Float) {
        override fun toString(): String {
            return "TestHit{doc=$doc, score=$score}"
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDoesNotLoadNorms() {
        val allTerm = Term(FIELD, "all")
        val termQuery = TermQuery(allTerm)

        val forbiddenNorms =
            object : FilterLeafReader(indexReader) {
                @Throws(IOException::class)
                override fun getNormValues(field: String): NumericDocValues? {
                    fail("Norms should not be loaded")
                }

                override val coreCacheHelper: CacheHelper?
                    get() = `in`.coreCacheHelper

                override val readerCacheHelper: CacheHelper?
                    get() = `in`.readerCacheHelper
            }
        // We don't use newSearcher because it sometimes runs checkIndex which loads norms
        val indexSearcher = IndexSearcher(forbiddenNorms)

        val weight = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE, 1f)
        expectThrows(AssertionError::class) {
            weight.scorer(forbiddenNorms.context)!!.iterator().nextDoc()
        }

        val weight2 = indexSearcher.createWeight(termQuery, ScoreMode.COMPLETE_NO_SCORES, 1f)
        // should not fail this time since norms are not necessary
        weight2.scorer(forbiddenNorms.context)!!.iterator().nextDoc()
    }

    @Test
    @Throws(IOException::class)
    fun testRandomTopDocs() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDocs =
            if (TEST_NIGHTLY)
                atLeast(128 * 8 * 8 * 3)
            else
                atLeast(500) // at night, make sure some terms have skip data
        for (i in 0..<numDocs) {
            val doc = Document()
            val numValues = random().nextInt(1 shl random().nextInt(5))
            val start = random().nextInt(10)
            for (j in 0..<numValues) {
                val freq = TestUtil.nextInt(random(), 1, 1 shl random().nextInt(3))
                for (k in 0..<freq) {
                    doc.add(TextField("foo", (start + j).toString(), Store.NO))
                }
            }
            w.addDocument(doc)
        }
        val reader = DirectoryReader.open(w)
        w.close()
        val searcher = newSearcher(reader)

        for (iter in 0..<15) {
            val query: Query = TermQuery(Term("foo", iter.toString()))

            var completeManager =
                TopScoreDocCollectorManager(10, Int.MAX_VALUE) // COMPLETE
            var topScoresManager =
                TopScoreDocCollectorManager(10, 1) // TOP_SCORES

            var complete = searcher.search(query, completeManager)
            var topScores = searcher.search(query, topScoresManager)
            CheckHits.checkEqual(query, complete.scoreDocs, topScores.scoreDocs)

            val filterTerm = random().nextInt(15)
            val filteredQuery =
                BooleanQuery.Builder()
                    .add(query, Occur.MUST)
                    .add(TermQuery(Term("foo", filterTerm.toString())), Occur.FILTER)
                    .build()

            completeManager = TopScoreDocCollectorManager(10, Int.MAX_VALUE) // COMPLETE
            topScoresManager = TopScoreDocCollectorManager(10, 1) // TOP_SCORES
            complete = searcher.search(filteredQuery, completeManager)
            topScores = searcher.search(filteredQuery, topScoresManager)
            CheckHits.checkEqual(query, complete.scoreDocs, topScores.scoreDocs)
        }
        reader.close()
        dir.close()
    }
}
