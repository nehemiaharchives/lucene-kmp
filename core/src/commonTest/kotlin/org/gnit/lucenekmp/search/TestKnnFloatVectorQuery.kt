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

import okio.ArrayIndexOutOfBoundsException as OkioArrayIndexOutOfBoundsException
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.index.VectorSimilarityFunction.DOT_PRODUCT
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomFloat
import org.gnit.lucenekmp.util.TestVectorUtil
import org.gnit.lucenekmp.util.VectorUtil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

open class TestKnnFloatVectorQuery : BaseKnnVectorQueryTestCase() {
    override fun getKnnVectorQuery(
        field: String,
        query: FloatArray,
        k: Int,
        queryFilter: Query?
    ): KnnFloatVectorQuery {
        return KnnFloatVectorQuery(field, query, k, queryFilter)
    }

    override fun getThrowingKnnVectorQuery(
        field: String,
        query: FloatArray,
        k: Int,
        queryFilter: Query?
    ): AbstractKnnVectorQuery {
        return ThrowingKnnVectorQuery(field, query, k, queryFilter)
    }

    override fun randomVector(dim: Int): FloatArray {
        return TestVectorUtil.randomVector(dim)
    }

    override fun getKnnVectorField(
        name: String,
        vector: FloatArray,
        similarityFunction: VectorSimilarityFunction
    ): Field {
        return KnnFloatVectorField(name, vector, similarityFunction)
    }

    override fun getKnnVectorField(name: String, vector: FloatArray): Field {
        return KnnFloatVectorField(name, vector)
    }

    @Throws(IOException::class)
    @Test
    open fun testToString() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                var query: AbstractKnnVectorQuery =
                    getKnnVectorQuery("field", floatArrayOf(0.0f, 1.0f), 10)
                assertEquals("KnnFloatVectorQuery:field[0.0,...][10]", query.toString("ignored"))

                assertDocScoreQueryToString(query.rewrite(newSearcher(reader)))

                // test with filter
                val filter = TermQuery(Term("id", "text"))
                query = getKnnVectorQuery("field", floatArrayOf(0.0f, 1.0f), 10, filter)
                assertEquals("KnnFloatVectorQuery:field[0.0,...][10][id:text]", query.toString("ignored"))
            }
        }
    }

    @Throws(IOException::class)
    @Test
    open fun testVectorEncodingMismatch() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                var filter: Query? = null
                if (random().nextBoolean()) {
                    filter = MatchAllDocsQuery()
                }
                val query: AbstractKnnVectorQuery = KnnByteVectorQuery("field", byteArrayOf(0, 1), 10, filter)
                val searcher = newSearcher(reader)
                expectThrows(IllegalStateException::class) {
                    searcher.search(query, 10)
                }
            }
        }
    }

    @Test
    open fun testGetTarget() {
        val queryVector = floatArrayOf(0f, 1f)
        val q1 = KnnFloatVectorQuery("f1", queryVector, 10)

        assertContentEquals(queryVector, q1.targetCopy)
        assertNotSame(queryVector, q1.targetCopy)
    }

    @Throws(IOException::class)
    @Test
    open fun testScoreNegativeDotProduct() {
        newDirectory().use { d ->
            IndexWriter(d, IndexWriterConfig()).use { w ->
                var doc = Document()
                doc.add(getKnnVectorField("field", floatArrayOf(-1f, 0f), DOT_PRODUCT))
                w.addDocument(doc)
                doc = Document()
                doc.add(getKnnVectorField("field", floatArrayOf(1f, 0f), DOT_PRODUCT))
                w.addDocument(doc)
            }
            DirectoryReader.open(d).use { reader ->
                assertEquals(1, reader.leaves().size)
                val searcher = IndexSearcher(reader)
                val query: AbstractKnnVectorQuery = getKnnVectorQuery("field", floatArrayOf(1f, 0f), 2)
                val rewritten = query.rewrite(searcher)
                val weight = searcher.createWeight(rewritten, ScoreMode.COMPLETE, 1f)
                val scorer = weight.scorer(reader.leaves()[0])!!

                // scores are normalized to lie in [0, 1]
                val it = scorer.iterator()
                assertEquals(2L, it.cost())
                assertEquals(0, it.nextDoc())
                assertTrue(0 <= scorer.score())
                assertEquals(1, it.advance(1))
                assertEquals(1f, scorer.score(), EPSILON)
            }
        }
    }

    @Throws(IOException::class)
    @Test
    open fun testScoreDotProduct() {
        newDirectory().use { d ->
            IndexWriter(d, configStandardCodec()).use { w ->
                for (j in 1..5) {
                    val doc = Document()
                    doc.add(
                        getKnnVectorField(
                            "field",
                            VectorUtil.l2normalize(floatArrayOf(j.toFloat(), (j * j).toFloat())),
                            DOT_PRODUCT
                        )
                    )
                    w.addDocument(doc)
                }
            }
            DirectoryReader.open(d).use { reader ->
                assertEquals(1, reader.leaves().size)
                val searcher = IndexSearcher(reader)
                val query: AbstractKnnVectorQuery =
                    getKnnVectorQuery("field", VectorUtil.l2normalize(floatArrayOf(2f, 3f)), 3)
                val rewritten = query.rewrite(searcher)
                val weight = searcher.createWeight(rewritten, ScoreMode.COMPLETE, 1f)
                val scorer = weight.scorer(reader.leaves()[0])!!

                // prior to advancing, score is undefined
                assertEquals(-1, scorer.docID())
                expectThrowsAnyOf(
                    mutableListOf(
                        OkioArrayIndexOutOfBoundsException::class,
                        IndexOutOfBoundsException::class
                    )
                ) {
                    scorer.score()
                }

                /* score0 = ((2,3) * (1, 1) = 5) / (||2, 3|| * ||1, 1|| = sqrt(26)), then
                 * normalized by (1 + x) /2.
                 */
                val score0 = ((1 + (2 * 1 + 3 * 1) / kotlin.math.sqrt(((2 * 2 + 3 * 3) * (1 * 1 + 1 * 1)).toDouble())) / 2).toFloat()

                /* score1 = ((2,3) * (2, 4) = 16) / (||2, 3|| * ||2, 4|| = sqrt(260)), then
                 * normalized by (1 + x) /2
                 */
                val score1 = ((1 + (2 * 2 + 3 * 4) / kotlin.math.sqrt(((2 * 2 + 3 * 3) * (2 * 2 + 4 * 4)).toDouble())) / 2).toFloat()

                // doc 1 happens to have the max score
                assertEquals(score1, scorer.getMaxScore(2), 0.001f)
                assertEquals(score1, scorer.getMaxScore(Int.MAX_VALUE), 0.0001f)

                val it = scorer.iterator()
                assertEquals(3L, it.cost())
                assertEquals(0, it.nextDoc())
                // doc 0 has (1, 1)
                assertEquals(score0, scorer.score(), 0.0001f)
                assertEquals(1, it.advance(1))
                assertEquals(score1, scorer.score(), 0.0001f)

                // since topK was 3
                assertEquals(NO_MORE_DOCS, it.advance(4))
                expectThrowsAnyOf(
                    mutableListOf(
                        OkioArrayIndexOutOfBoundsException::class,
                        IndexOutOfBoundsException::class
                    )
                ) {
                    scorer.score()
                }
            }
        }
    }

    @Throws(IOException::class)
    @Test
    open fun testDocAndScoreQueryBasics() {
        newDirectory().use { directory ->
            val reader: DirectoryReader
            RandomIndexWriter(random(), directory).use { iw ->
                for (i in 0 until 50) {
                    val doc = Document()
                    doc.add(StringField("field", "value$i", Field.Store.NO))
                    iw.addDocument(doc)
                    if (i % 10 == 0) {
                        iw.flush()
                    }
                }
                reader = iw.reader
            }
            reader.use {
                val searcher = LuceneTestCase.newSearcher(reader)
                val scoreDocsList = mutableListOf<ScoreDoc>()
                var doc = 0
                while (doc < 30) {
                    scoreDocsList.add(ScoreDoc(doc, randomFloat()))
                    doc += 1 + random().nextInt(5)
                }
                val scoreDocs = scoreDocsList.toTypedArray()
                val docs = IntArray(scoreDocs.size)
                val scores = FloatArray(scoreDocs.size)
                var maxScore = Float.MIN_VALUE
                for (i in scoreDocs.indices) {
                    docs[i] = scoreDocs[i].doc
                    scores[i] = scoreDocs[i].score
                    maxScore = kotlin.math.max(maxScore, scores[i])
                }
                val indexReader: IndexReader = searcher.indexReader
                val segments = AbstractKnnVectorQuery.findSegmentStarts(indexReader.leaves(), docs)

                val query = AbstractKnnVectorQuery.DocAndScoreQuery(
                    docs,
                    scores,
                    maxScore,
                    segments,
                    indexReader.context.id()
                )
                val w = query.createWeight(searcher, ScoreMode.TOP_SCORES, 1.0f)
                val topDocs = searcher.search(query, 100)
                assertEquals(scoreDocs.size.toLong(), topDocs.totalHits.value)
                assertEquals(TotalHits.Relation.EQUAL_TO, topDocs.totalHits.relation)
                topDocs.scoreDocs.sortBy { it.doc }
                assertEquals(scoreDocs.size, topDocs.scoreDocs.size)
                for (i in scoreDocs.indices) {
                    assertEquals(scoreDocs[i].doc, topDocs.scoreDocs[i].doc)
                    assertEquals(scoreDocs[i].score, topDocs.scoreDocs[i].score, 0.0001f)
                    assertTrue(searcher.explain(query, scoreDocs[i].doc).isMatch)
                }

                for (leafReaderContext in searcher.leafContexts) {
                    val scorer = w.scorer(leafReaderContext)
                    val count = w.count(leafReaderContext)
                    if (scorer == null) {
                        assertEquals(0, count)
                    } else {
                        assertTrue(scorer.getMaxScore(NO_MORE_DOCS) > 0.0f, leafReaderContext.toString())
                        assertTrue(count > 0)
                        var iteratorCount = 0
                        while (scorer.iterator().nextDoc() != NO_MORE_DOCS) {
                            iteratorCount++
                        }
                        assertEquals(iteratorCount, count)
                    }
                }
            }
        }
    }

    class ThrowingKnnVectorQuery(
        field: String,
        target: FloatArray,
        k: Int,
        filter: Query?
    ) : KnnFloatVectorQuery(field, target, k, filter, KnnSearchStrategy.Hnsw(0)) {
        @Throws(IOException::class)
        override fun exactSearch(
            context: LeafReaderContext,
            acceptIterator: DocIdSetIterator,
            queryTimeout: QueryTimeout?
        ): TopDocs {
            throw UnsupportedOperationException("exact search is not supported")
        }

        override fun toString(field: String?): String {
            return "ThrowingKnnVectorQuery"
        }
    }

    // tests inherited from BaseKnnVectorQueryTestCase
    @Test
    override fun testEquals() = super.testEquals()

    @Test
    override fun testGetField() = super.testGetField()

    @Test
    override fun testGetK() = super.testGetK()

    @Test
    override fun testGetFilter() = super.testGetFilter()

    @Test
    override fun testEmptyIndex() = super.testEmptyIndex()

    @Test
    override fun testFindAll() = super.testFindAll()

    @Test
    override fun testFindFewer() = super.testFindFewer()

    @Test
    override fun testSearchBoost() = super.testSearchBoost()

    @Test
    override fun testSimpleFilter() = super.testSimpleFilter()

    @Test
    override fun testFilterWithNoVectorMatches() = super.testFilterWithNoVectorMatches()

    @Test
    override fun testDimensionMismatch() = super.testDimensionMismatch()

    @Test
    override fun testNonVectorField() = super.testNonVectorField()

    @Test
    override fun testIllegalArguments() = super.testIllegalArguments()

    @Test
    override fun testDifferentReader() = super.testDifferentReader()

    @Test
    override fun testScoreEuclidean() = super.testScoreEuclidean()

    @Test
    override fun testScoreCosine() = super.testScoreCosine()

    @Test
    override fun testScoreMIP() = super.testScoreMIP()

    @Test
    override fun testExplain() = super.testExplain()

    @Test
    override fun testExplainMultipleSegments() = super.testExplainMultipleSegments()

    @Test
    override fun testSkewedIndex() = super.testSkewedIndex()

    @Test
    override fun testRandomConsistencySingleThreaded() = super.testRandomConsistencySingleThreaded()

    @Test
    override fun testRandomConsistencyMultiThreaded() = super.testRandomConsistencyMultiThreaded()

    @Test
    override fun testRandom() = super.testRandom()

    @Test
    override fun testRandomWithFilter() = super.testRandomWithFilter()

    @Test
    override fun testFilterWithSameScore() = super.testFilterWithSameScore()

    @Test
    override fun testDeletes() = super.testDeletes()

    @Test
    override fun testAllDeletes() = super.testAllDeletes()

    @Test
    override fun testMergeAwayAllValues() = super.testMergeAwayAllValues()

    @Test
    override fun testNoLiveDocsReader() = super.testNoLiveDocsReader()

    @Test
    override fun testBitSetQuery() = super.testBitSetQuery()

    @Test
    override fun testTimeLimitingKnnCollectorManager() = super.testTimeLimitingKnnCollectorManager()

    @Test
    override fun testTimeout() = super.testTimeout()

    @Test
    override fun testSameFieldDifferentFormats() = super.testSameFieldDifferentFormats()
}
