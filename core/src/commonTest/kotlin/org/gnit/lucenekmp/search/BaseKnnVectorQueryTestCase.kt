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
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.knn.TopKnnCollectorManager
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.frequently
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomBoolean
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.randomIntBetween
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test cases for AbstractKnnVectorQuery objects. */
abstract class BaseKnnVectorQueryTestCase : LuceneTestCase() {
    companion object {
        // handle quantization noise
        const val EPSILON = 0.001f
    }

    abstract fun getKnnVectorQuery(
        field: String,
        query: FloatArray,
        k: Int,
        queryFilter: Query?
    ): AbstractKnnVectorQuery

    abstract fun getThrowingKnnVectorQuery(
        field: String,
        query: FloatArray,
        k: Int,
        queryFilter: Query?
    ): AbstractKnnVectorQuery

    open fun getKnnVectorQuery(field: String, query: FloatArray, k: Int): AbstractKnnVectorQuery {
        return getKnnVectorQuery(field, query, k, null)
    }

    abstract fun randomVector(dim: Int): FloatArray

    abstract fun getKnnVectorField(
        name: String,
        vector: FloatArray,
        similarityFunction: VectorSimilarityFunction
    ): Field

    abstract fun getKnnVectorField(name: String, vector: FloatArray): Field

    /**
     * Creates a new directory. Subclasses can override to test different directory implementations.
     */
    protected open fun newDirectoryForTest(): BaseDirectoryWrapper {
        return LuceneTestCase.newDirectory(random())
    }

    @Throws(IOException::class)
    protected open fun configStandardCodec(): IndexWriterConfig {
        return IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())
    }

    open fun testEquals() {
        val q1 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10)
        val filter1 = TermQuery(Term("id", "id1"))
        val q2 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10, filter1)

        assertNotEquals(q2, q1)
        assertNotEquals(q1, q2)
        assertEquals(q2, getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10, filter1))

        val filter2 = TermQuery(Term("id", "id2"))
        assertNotEquals(q2, getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10, filter2))

        assertEquals(q1, getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10))

        assertFalse(q1.equals(null))

        assertFalse(q1 == TermQuery(Term("f1", "x")))

        assertNotEquals(q1, getKnnVectorQuery("f2", floatArrayOf(0f, 1f), 10))
        assertNotEquals(q1, getKnnVectorQuery("f1", floatArrayOf(1f, 1f), 10))
        assertNotEquals(q1, getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 2))
        assertNotEquals(q1, getKnnVectorQuery("f1", floatArrayOf(0f), 10))
    }

    open fun testGetField() {
        val q1 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10)
        val filter1 = TermQuery(Term("id", "id1"))
        val q2 = getKnnVectorQuery("f2", floatArrayOf(0f, 1f), 10, filter1)

        assertEquals("f1", q1.field)
        assertEquals("f2", q2.field)
    }

    open fun testGetK() {
        val q1 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 6)
        val filter1 = TermQuery(Term("id", "id1"))
        val q2 = getKnnVectorQuery("f2", floatArrayOf(0f, 1f), 7, filter1)

        assertEquals(6, q1.k)
        assertEquals(7, q2.k)
    }

    open fun testGetFilter() {
        val q1 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 6)
        val filter1 = TermQuery(Term("id", "id1"))
        val q2 = getKnnVectorQuery("f2", floatArrayOf(0f, 1f), 7, filter1)

        assertNull(q1.filter)
        assertEquals(filter1, q2.filter)
    }

    /**
     * Tests if a AbstractKnnVectorQuery is rewritten to a MatchNoDocsQuery when there are no
     * documents to match.
     */
    @Throws(IOException::class)
    open fun testEmptyIndex() {
        getIndexStore("field").use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val kvq = getKnnVectorQuery("field", floatArrayOf(1f, 2f), 10)
                assertMatches(searcher, kvq, 0)
                val q = searcher.rewrite(kvq)
                assertTrue(q is MatchNoDocsQuery)
            }
        }
    }

    /**
     * Tests that a AbstractKnnVectorQuery whose topK >= numDocs returns all the documents in score
     * order
     */
    @Throws(IOException::class)
    open fun testFindAll() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val kvq = getKnnVectorQuery("field", floatArrayOf(0f, 0f), 10)
                assertMatches(searcher, kvq, 3)
                val scoreDocs = searcher.search(kvq, 3).scoreDocs
                assertIdMatches(reader, "id2", scoreDocs[0])
                assertIdMatches(reader, "id0", scoreDocs[1])
                assertIdMatches(reader, "id1", scoreDocs[2])
            }
        }
    }

    @Throws(IOException::class)
    open fun testFindFewer() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val kvq = getKnnVectorQuery("field", floatArrayOf(0f, 0f), 2)
                assertMatches(searcher, kvq, 2)
                val scoreDocs = searcher.search(kvq, 3).scoreDocs
                assertEquals(scoreDocs.size, 2)
                assertTopIdsMatches(reader, setOf("id2", "id0"), scoreDocs)
            }
        }
    }

    @Throws(IOException::class)
    open fun testSearchBoost() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)

                val vectorQuery = getKnnVectorQuery("field", floatArrayOf(0f, 0f), 10)
                val scoreDocs = searcher.search(vectorQuery, 3).scoreDocs

                val boostQuery = BoostQuery(vectorQuery, 3.0f)
                val boostScoreDocs = searcher.search(boostQuery, 3).scoreDocs
                assertEquals(scoreDocs.size, boostScoreDocs.size)

                for (i in scoreDocs.indices) {
                    val scoreDoc = scoreDocs[i]
                    val boostScoreDoc = boostScoreDocs[i]

                    assertEquals(scoreDoc.doc, boostScoreDoc.doc)
                    assertEquals(scoreDoc.score * 3.0f, boostScoreDoc.score, 0.001f)
                }
            }
        }
    }

    /** Tests that a AbstractKnnVectorQuery applies the filter query */
    @Throws(IOException::class)
    open fun testSimpleFilter() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val filter = TermQuery(Term("id", "id2"))
                val kvq = getKnnVectorQuery("field", floatArrayOf(0f, 0f), 10, filter)
                val topDocs = searcher.search(kvq, 3)
                assertEquals(1, topDocs.totalHits.value)
                assertIdMatches(reader, "id2", topDocs.scoreDocs[0])
            }
        }
    }

    @Throws(IOException::class)
    open fun testFilterWithNoVectorMatches() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)

                val filter = TermQuery(Term("other", "value"))
                val kvq = getKnnVectorQuery("field", floatArrayOf(0f, 0f), 10, filter)
                val topDocs = searcher.search(kvq, 3)
                assertEquals(0, topDocs.totalHits.value)
            }
        }
    }

    /** testDimensionMismatch */
    @Throws(IOException::class)
    open fun testDimensionMismatch() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val kvq = getKnnVectorQuery("field", floatArrayOf(0f), 1)
                val e = expectThrows(IllegalArgumentException::class) {
                    searcher.search(kvq, 10)
                }
                assertEquals("vector query dimension: 1 differs from field dimension: 2", e.message)
            }
        }
    }

    /** testNonVectorField */
    @Throws(IOException::class)
    open fun testNonVectorField() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                assertMatches(searcher, getKnnVectorQuery("xyzzy", floatArrayOf(0f), 10), 0)
                assertMatches(searcher, getKnnVectorQuery("id", floatArrayOf(0f), 10), 0)
            }
        }
    }

    /** Test bad parameters */
    @Throws(IOException::class)
    open fun testIllegalArguments() {
        expectThrows(IllegalArgumentException::class) {
            getKnnVectorQuery("xx", floatArrayOf(1f), 0)
        }
    }

    @Throws(IOException::class)
    open fun testDifferentReader() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val query = getKnnVectorQuery("field", floatArrayOf(2f, 3f), 3)
                val dasq = query.rewrite(newSearcher(reader))
                val leafSearcher = newSearcher(reader.leaves()[0].reader())
                expectThrows(IllegalStateException::class) {
                    dasq.createWeight(leafSearcher, ScoreMode.COMPLETE, 1f)
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testScoreEuclidean() {
        val vectors = Array(5) { j -> floatArrayOf(j.toFloat(), j.toFloat()) }
        getStableIndexStore("field", *vectors).use { d ->
            DirectoryReader.open(d).use { reader ->
                val searcher = IndexSearcher(reader)
                val query = getKnnVectorQuery("field", floatArrayOf(2f, 3f), 3)
                val rewritten = query.rewrite(searcher)
                val weight = searcher.createWeight(rewritten, ScoreMode.COMPLETE, 1f)
                val scorer = weight.scorer(reader.leaves()[0])!!

                // prior to advancing, score is 0
                assertEquals(-1, scorer.docID())
                expectThrows(IndexOutOfBoundsException::class) {
                    scorer.score()
                    Unit
                }

                // This is 1 / ((l2distance((2,3), (2, 2)) = 1) + 1) = 0.5
                assertEquals(1 / 2f, scorer.getMaxScore(2), 0f)
                assertEquals(1 / 2f, scorer.getMaxScore(Int.MAX_VALUE), 0f)

                val it = scorer.iterator()
                assertEquals(3, it.cost())
                val firstDoc = it.nextDoc()
                if (firstDoc == 1) {
                    assertEquals(1 / 6f, scorer.score(), 0f)
                    assertEquals(3, it.advance(3))
                    assertEquals(1 / 2f, scorer.score(), 0f)
                    assertEquals(NO_MORE_DOCS, it.advance(4))
                } else {
                    assertEquals(2, firstDoc)
                    assertEquals(1 / 2f, scorer.score(), 0f)
                    assertEquals(4, it.advance(4))
                    assertEquals(1 / 6f, scorer.score(), 0f)
                    assertEquals(NO_MORE_DOCS, it.advance(5))
                }
                expectThrows(IndexOutOfBoundsException::class) {
                    scorer.score()
                    Unit
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testScoreCosine() {
        val vectors = Array(5) { j ->
            val jVal = (j + 1).toFloat()
            floatArrayOf(jVal, jVal * jVal)
        }
        getStableIndexStore("field", VectorSimilarityFunction.COSINE, *vectors).use { d ->
            DirectoryReader.open(d).use { reader ->
                assertEquals(1, reader.leaves().size)
                val searcher = IndexSearcher(reader)
                val query = getKnnVectorQuery("field", floatArrayOf(2f, 3f), 3)
                val rewritten = query.rewrite(searcher)
                val weight = searcher.createWeight(rewritten, ScoreMode.COMPLETE, 1f)
                val scorer = weight.scorer(reader.leaves()[0])!!

                // prior to advancing, score is undefined
                assertEquals(-1, scorer.docID())
                expectThrows(IndexOutOfBoundsException::class) {
                    scorer.score()
                    Unit
                }

                /* score0 = ((2,3) * (1, 1) = 5) / (||2, 3|| * ||1, 1|| = sqrt(26)), then
                 * normalized by (1 + x) /2.
                 */
                val score0 = ((1 + (2 * 1 + 3 * 1) / kotlin.math.sqrt((2 * 2 + 3 * 3) * (1 * 1 + 1 * 1).toDouble())) / 2).toFloat()

                /* score1 = ((2,3) * (2, 4) = 16) / (||2, 3|| * ||2, 4|| = sqrt(260)), then
                 * normalized by (1 + x) /2
                 */
                val score1 = ((1 + (2 * 2 + 3 * 4) / kotlin.math.sqrt((2 * 2 + 3 * 3) * (2 * 2 + 4 * 4).toDouble())) / 2).toFloat()

                // doc 1 happens to have the maximum score
                assertEquals(score1, scorer.getMaxScore(2), 0.0001f)
                assertEquals(score1, scorer.getMaxScore(Int.MAX_VALUE), 0.0001f)

                val it = scorer.iterator()
                assertEquals(3, it.cost())
                assertEquals(0, it.nextDoc())
                // doc 0 has (1, 1)
                assertEquals(score0, scorer.score(), 0.0001f)
                assertEquals(1, it.advance(1))
                assertEquals(score1, scorer.score(), 0.0001f)

                // since topK was 3
                assertEquals(NO_MORE_DOCS, it.advance(4))
                expectThrows(IndexOutOfBoundsException::class) {
                    scorer.score()
                    Unit
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testScoreMIP() {
        val vectors = arrayOf(
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        )
        getStableIndexStore("field", VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT, *vectors).use { d ->
            DirectoryReader.open(d).use { reader ->
                val searcher = newSearcher(reader)
                val kvq = getKnnVectorQuery("field", floatArrayOf(0f, -1f), 10)
                assertMatches(searcher, kvq, 3)
                val scoreDocs = searcher.search(kvq, 3).scoreDocs
                assertIdMatches(reader, "id2", scoreDocs[0])
                assertIdMatches(reader, "id0", scoreDocs[1])
                assertIdMatches(reader, "id1", scoreDocs[2])

                assertEquals(1.0, scoreDocs[0].score.toDouble(), 1e-7)
                assertEquals(1 / 2f, scoreDocs[1].score, 1e-7f)
                assertEquals(1 / 3f, scoreDocs[2].score, 1e-7f)
            }
        }
    }

    @Throws(IOException::class)
    open fun testExplain() {
        newDirectoryForTest().use { d ->
            IndexWriter(d, IndexWriterConfig()).use { w ->
                for (j in 0 until 5) {
                    val doc = Document()
                    doc.add(getKnnVectorField("field", floatArrayOf(j.toFloat(), j.toFloat())))
                    w.addDocument(doc)
                }
            }
            DirectoryReader.open(d).use { reader ->
                val searcher = IndexSearcher(reader)
                val query = getKnnVectorQuery("field", floatArrayOf(2f, 3f), 3)
                val matched = searcher.explain(query, 2)
                assertTrue(matched.isMatch)
                // scores vary widely due to quantization
                assertEquals(1 / 2.0, matched.value.toDouble(), 0.5)
                assertEquals(0, matched.getDetails().size)
                assertEquals("within top 3 docs", matched.description)

                val nomatch = searcher.explain(query, 5)
                assertFalse(nomatch.isMatch)
                assertEquals(0f, nomatch.value)
                assertEquals(0, matched.getDetails().size)
                assertEquals("not in top 3 docs", nomatch.description)
            }
        }
    }

    @Throws(IOException::class)
    open fun testExplainMultipleSegments() {
        newDirectoryForTest().use { d ->
            IndexWriter(d, IndexWriterConfig()).use { w ->
                for (j in 0 until 5) {
                    val doc = Document()
                    doc.add(getKnnVectorField("field", floatArrayOf(j.toFloat(), j.toFloat())))
                    w.addDocument(doc)
                    w.commit()
                }
            }
            DirectoryReader.open(d).use { reader ->
                val searcher = IndexSearcher(reader)
                val query = getKnnVectorQuery("field", floatArrayOf(2f, 3f), 3)
                val matched = searcher.explain(query, 2) // (2, 2)
                assertTrue(matched.isMatch)
                // scores vary widely due to quantization
                assertEquals(1 / 2.0, matched.value.toDouble(), 0.5)
                assertEquals(0, matched.getDetails().size)
                assertEquals("within top 3 docs", matched.description)

                val nomatch = searcher.explain(query, 4)
                assertFalse(nomatch.isMatch)
                assertEquals(0f, nomatch.value)
                assertEquals(0, matched.getDetails().size)
                assertEquals("not in top 3 docs", nomatch.description)
            }
        }
    }

    /** Test that when vectors are abnormally distributed among segments, we still find the top K */
    @Throws(IOException::class)
    open fun testSkewedIndex() {
        /* We have to choose the numbers carefully here so that some segment has more than the expected
         * number of top K documents, but no more than K documents in total (otherwise we might occasionally
         * randomly fail to find one).
         */
        newDirectoryForTest().use { d ->
            IndexWriter(d, configStandardCodec()).use { w ->
                var r = 0
                for (i in 0 until 5) {
                    for (j in 0 until 5) {
                        val doc = Document()
                        doc.add(getKnnVectorField("field", floatArrayOf(r.toFloat(), r.toFloat())))
                        doc.add(StringField("id", "id$r", Field.Store.YES))
                        w.addDocument(doc)
                        ++r
                    }
                    w.flush()
                }
            }
            DirectoryReader.open(d).use { reader ->
                val searcher = newSearcher(reader)
                var results = searcher.search(getKnnVectorQuery("field", floatArrayOf(0f, 0f), 8), 10)
                assertEquals(8, results.scoreDocs.size)
                assertIdMatches(reader, "id0", results.scoreDocs[0])
                assertIdMatches(reader, "id7", results.scoreDocs[7])

                // test some results in the middle of the sequence - also tests docid tiebreaking
                results = searcher.search(getKnnVectorQuery("field", floatArrayOf(10f, 10f), 8), 10)
                assertEquals(8, results.scoreDocs.size)
                assertIdMatches(reader, "id10", results.scoreDocs[0])
                assertIdMatches(reader, "id6", results.scoreDocs[7])
            }
        }
    }

    /** Tests with random vectors, number of documents, etc. Uses RandomIndexWriter. */
    @Throws(IOException::class)
    open fun testRandomConsistencySingleThreaded() {
        assertRandomConsistency(false)
    }

    // @AwaitsFix(bugUrl = "https://github.com/apache/lucene/issues/14180")
    @Throws(IOException::class)
    open fun testRandomConsistencyMultiThreaded() {
        assertRandomConsistency(true)
    }

    @Throws(IOException::class)
    private fun assertRandomConsistency(multiThreaded: Boolean) {
        val numDocs = 100
        val dimension = 4
        val numIters = 10
        val everyDocHasAVector = random().nextBoolean()
        val r = random()
        newDirectoryForTest().use { d ->
            // To ensure consistency between seeded runs, remove some randomness
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            iwc.setMergeScheduler(SerialMergeScheduler())
            iwc.setMergePolicy(NoMergePolicy.INSTANCE)
            iwc.setMaxBufferedDocs(numDocs)
            iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            IndexWriter(d, iwc).use { w ->
                for (i in 0 until numDocs) {
                    val doc = Document()
                    if (everyDocHasAVector || random().nextInt(10) != 2) {
                        doc.add(getKnnVectorField("field", randomVector(dimension)))
                    }
                    w.addDocument(doc)
                    if (r.nextBoolean() && i % 50 == 0) {
                        w.flush()
                    }
                }
            }
            DirectoryReader.open(d).use { reader ->
                val searcher = newSearcher(reader, true, true, multiThreaded)
                // first get the initial set of docs, and we expect all future queries to be exactly the
                // same
                val k = random().nextInt(80) + 1
                val query = getKnnVectorQuery("field", randomVector(dimension), k)
                val n = random().nextInt(100) + 1
                val expectedResults = searcher.search(query, n)
                for (i in 0 until numIters) {
                    val results = searcher.search(query, n)
                    assertEquals(expectedResults.totalHits.value, results.totalHits.value)
                    assertEquals(expectedResults.scoreDocs.size, results.scoreDocs.size)
                    for (j in results.scoreDocs.indices) {
                        assertEquals(expectedResults.scoreDocs[j].doc, results.scoreDocs[j].doc)
                        assertEquals(expectedResults.scoreDocs[j].score, results.scoreDocs[j].score, EPSILON)
                    }
                }
            }
        }
    }

    /** Tests with random vectors, number of documents, etc. Uses RandomIndexWriter. */
    @Throws(IOException::class)
    open fun testRandom() {
        val numDocs = atLeast(100)
        val dimension = atLeast(5)
        val numIters = atLeast(10)
        val everyDocHasAVector = random().nextBoolean()
        newDirectoryForTest().use { d ->
            val w = RandomIndexWriter(random(), d)
            for (i in 0 until numDocs) {
                val doc = Document()
                if (everyDocHasAVector || random().nextInt(10) != 2) {
                    doc.add(getKnnVectorField("field", randomVector(dimension)))
                }
                w.addDocument(doc)
            }
            w.close()
            DirectoryReader.open(d).use { reader ->
                val searcher = newSearcher(reader)
                for (i in 0 until numIters) {
                    val k = random().nextInt(80) + 1
                    val query = getKnnVectorQuery("field", randomVector(dimension), k)
                    val n = random().nextInt(100) + 1
                    val results = searcher.search(query, n)
                    val expected = minOf(minOf(n, k), reader.numDocs())
                    // we may get fewer results than requested if there are deletions, but this test doesn't
                    // test that
                    check(!reader.hasDeletions())
                    assertEquals(expected, results.scoreDocs.size)
                    assertTrue(results.totalHits.value >= results.scoreDocs.size.toLong())
                    // verify the results are in descending score order
                    var last = Float.MAX_VALUE
                    for (scoreDoc in results.scoreDocs) {
                        assertTrue(scoreDoc.score <= last)
                        last = scoreDoc.score
                    }
                }
            }
        }
    }

    /** Tests with random vectors and a random filter. Uses RandomIndexWriter. */
    @Throws(IOException::class)
    open fun testRandomWithFilter() {
        val numDocs = 1000
        val dimension = atLeast(5)
        val numIters = atLeast(10)
        newDirectoryForTest().use { d ->
            // Always use the default kNN format to have predictable behavior around when it hits
            // visitedLimit. This is fine since the test targets AbstractKnnVectorQuery logic, not the kNN
            // format
            // implementation.
            val iwc = configStandardCodec()
            val w = RandomIndexWriter(random(), d, iwc)
            for (i in 0 until numDocs) {
                val doc = Document()
                doc.add(getKnnVectorField("field", randomVector(dimension)))
                doc.add(NumericDocValuesField("tag", i.toLong()))
                doc.add(IntPoint("tag", i))
                w.addDocument(doc)
            }
            w.forceMerge(1)
            w.close()

            DirectoryReader.open(d).use { reader ->
                val searcher = newSearcher(reader)
                for (i in 0 until numIters) {
                    val lower = random().nextInt(500)

                    // Test a filter with cost less than k and check we use exact search
                    val filter1 = IntPoint.newRangeQuery("tag", lower, lower + 8)
                    var results = searcher.search(
                        getKnnVectorQuery("field", randomVector(dimension), 10, filter1),
                        numDocs
                    )
                    assertEquals(9, results.totalHits.value)
                    assertEquals(results.totalHits.value, results.scoreDocs.size.toLong())
                    expectThrows(UnsupportedOperationException::class) {
                        searcher.search(
                            getThrowingKnnVectorQuery("field", randomVector(dimension), 10, filter1),
                            numDocs
                        )
                    }

                    // Test an unrestrictive filter and check we use approximate search
                    val filter3 = IntPoint.newRangeQuery("tag", lower, numDocs)
                    results = searcher.search(
                        getThrowingKnnVectorQuery("field", randomVector(dimension), 5, filter3),
                        numDocs,
                        Sort(SortField("tag", SortField.Type.INT))
                    )
                    assertEquals(5, results.totalHits.value)
                    assertEquals(results.totalHits.value, results.scoreDocs.size.toLong())

                    for (scoreDoc in results.scoreDocs) {
                        val fieldDoc = scoreDoc as FieldDoc
                        assertEquals(1, fieldDoc.fields!!.size)

                        val tag = fieldDoc.fields!![0] as Int
                        assertTrue(lower <= tag && tag <= numDocs)
                    }
                }
                // Test a filter that exhausts visitedLimit in upper levels, and switches to exact search
                // due to extreme edge cases, removing the randomness
                val vector = FloatArray(dimension) { i ->
                    if (i % 2 == 0) 42f else 7f
                }
                val filter4 = IntPoint.newRangeQuery("tag", 250, 256)
                expectThrows(UnsupportedOperationException::class) {
                    searcher.search(getThrowingKnnVectorQuery("field", vector, 1, filter4), numDocs)
                }
            }
        }
    }

    /** Tests filtering when all vectors have the same score. */
    @Throws(IOException::class)
    open fun testFilterWithSameScore() {
        val numDocs = 100
        val dimension = atLeast(5)
        newDirectoryForTest().use { d ->
            // Always use the default kNN format to have predictable behavior around when it hits
            // visitedLimit. This is fine since the test targets AbstractKnnVectorQuery logic, not the kNN
            // format
            // implementation.
            val iwc = configStandardCodec()
            val w = IndexWriter(d, iwc)
            val vector = randomVector(dimension)
            for (i in 0 until numDocs) {
                val doc = Document()
                doc.add(getKnnVectorField("field", vector))
                doc.add(IntPoint("tag", i))
                w.addDocument(doc)
            }
            w.forceMerge(1)
            w.close()

            DirectoryReader.open(d).use { reader ->
                val searcher = newSearcher(reader)
                val lower = random().nextInt(50)
                val size = 5

                // Test a restrictive filter, which usually performs exact search
                val filter1 = IntPoint.newRangeQuery("tag", lower, lower + 6)
                var results = searcher.search(
                    getKnnVectorQuery("field", randomVector(dimension), size, filter1),
                    size
                )
                assertEquals(size, results.scoreDocs.size)

                // Test an unrestrictive filter, which usually performs approximate search
                val filter2 = IntPoint.newRangeQuery("tag", lower, numDocs)
                results = searcher.search(
                    getKnnVectorQuery("field", randomVector(dimension), size, filter2),
                    size
                )
                assertEquals(size, results.scoreDocs.size)
            }
        }
    }

    @Throws(IOException::class)
    open fun testDeletes() {
        newDirectoryForTest().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                val numDocs = atLeast(100)
                val dim = 30
                for (i in 0 until numDocs) {
                    val d = Document()
                    d.add(StringField("index", i.toString(), Field.Store.YES))
                    if (frequently()) {
                        d.add(getKnnVectorField("vector", randomVector(dim)))
                    }
                    w.addDocument(d)
                }
                w.commit()

                // Delete some documents at random, both those with and without vectors
                val toDelete = mutableSetOf<Term>()
                for (i in 0 until 25) {
                    val index = random().nextInt(numDocs)
                    toDelete.add(Term("index", index.toString()))
                }
                w.deleteDocuments(*toDelete.toTypedArray())
                w.commit()

                val hits = 50
                DirectoryReader.open(dir).use { reader ->
                    val allIds = mutableSetOf<String>()
                    val searcher = IndexSearcher(reader)
                    val query = getKnnVectorQuery("vector", randomVector(dim), hits)
                    val topDocs = searcher.search(query, numDocs)
                    val storedFields = reader.storedFields()
                    for (scoreDoc in topDocs.scoreDocs) {
                        val doc = storedFields.document(scoreDoc.doc, mutableSetOf("index"))
                        val index = requireNotNull(doc.get("index"))!!
                        assertFalse(
                            toDelete.contains(Term("index", index)),
                            "search returned a deleted document: $index"
                        )
                        allIds.add(index)
                    }
                    assertEquals(hits, allIds.size, "search missed some documents")
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testAllDeletes() {
        newDirectoryForTest().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                val numDocs = atLeast(100)
                val dim = 30
                for (i in 0 until numDocs) {
                    val d = Document()
                    d.add(getKnnVectorField("vector", randomVector(dim)))
                    w.addDocument(d)
                }
                w.commit()

                w.deleteDocuments(MatchAllDocsQuery())
                w.commit()

                DirectoryReader.open(dir).use { reader ->
                    val searcher = IndexSearcher(reader)
                    val query = getKnnVectorQuery("vector", randomVector(dim), numDocs)
                    val topDocs = searcher.search(query, numDocs)
                    assertEquals(0, topDocs.scoreDocs.size)
                }
            }
        }
    }

    // Test ghost fields, that have a field info but no values
    @Throws(IOException::class)
    open fun testMergeAwayAllValues() {
        val dim = 30
        newDirectoryForTest().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                var doc = Document()
                doc.add(StringField("id", "0", Field.Store.NO))
                w.addDocument(doc)
                doc = Document()
                doc.add(StringField("id", "1", Field.Store.NO))
                doc.add(getKnnVectorField("field", randomVector(dim)))
                w.addDocument(doc)
                w.commit()
                w.deleteDocuments(Term("id", "1"))
                w.forceMerge(1)

                DirectoryReader.open(w).use { reader ->
                    val leafReader = getOnlyLeafReader(reader)
                    val fi = leafReader.fieldInfos.fieldInfo("field")
                    assertNotNull(fi)
                    val vectorValues: KnnVectorValues = when (fi!!.vectorEncoding) {
                        VectorEncoding.BYTE -> leafReader.getByteVectorValues("field")!!
                        VectorEncoding.FLOAT32 -> leafReader.getFloatVectorValues("field")!!
                        else -> throw AssertionError()
                    }
                    assertNotNull(vectorValues)
                    assertEquals(NO_MORE_DOCS, vectorValues.iterator().nextDoc())
                }
            }
        }
    }

    /**
     * Check that the query behaves reasonably when using a custom filter reader where there are no
     * live docs.
     */
    @Throws(IOException::class)
    open fun testNoLiveDocsReader() {
        val iwc = newIndexWriterConfig()
        newDirectoryForTest().use { dir ->
            IndexWriter(dir, iwc).use { w ->
                val numDocs = 10
                val dim = 30
                for (i in 0 until numDocs) {
                    val d = Document()
                    d.add(StringField("index", i.toString(), Field.Store.NO))
                    d.add(getKnnVectorField("vector", randomVector(dim)))
                    w.addDocument(d)
                }
                w.commit()

                DirectoryReader.open(dir).use { reader ->
                    val wrappedReader = NoLiveDocsDirectoryReader(reader)
                    val searcher = IndexSearcher(wrappedReader)
                    val query = getKnnVectorQuery("vector", randomVector(dim), numDocs)
                    val topDocs = searcher.search(query, numDocs)
                    assertEquals(0, topDocs.scoreDocs.size)
                }
            }
        }
    }

    /**
     * Test that AbstractKnnVectorQuery optimizes the case where the filter query is backed by [BitSetIterator].
     */
    @Throws(IOException::class)
    open fun testBitSetQuery() {
        val iwc = newIndexWriterConfig()
        newDirectoryForTest().use { dir ->
            IndexWriter(dir, iwc).use { w ->
                val numDocs = 100
                val dim = 30
                for (i in 0 until numDocs) {
                    val d = Document()
                    d.add(getKnnVectorField("vector", randomVector(dim)))
                    w.addDocument(d)
                }
                w.commit()

                DirectoryReader.open(dir).use { reader ->
                    val searcher = IndexSearcher(reader)

                    val filter = ThrowingBitSetQuery(FixedBitSet(numDocs))
                    expectThrows(UnsupportedOperationException::class) {
                        searcher.search(
                            getKnnVectorQuery("vector", randomVector(dim), 10, filter),
                            numDocs
                        )
                    }
                }
            }
        }
    }

    /** Test functionality of [TimeLimitingKnnCollectorManager]. */
    @Throws(IOException::class)
    open fun testTimeLimitingKnnCollectorManager() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)

                val delegate = TopKnnCollectorManager(3, searcher)

                // A collector manager with no timeout
                val noTimeoutManager = TimeLimitingKnnCollectorManager(delegate, null)
                val noTimeoutCollector =
                    noTimeoutManager.newCollector(
                        Int.MAX_VALUE,
                        org.gnit.lucenekmp.search.knn.KnnSearchStrategy.Hnsw.DEFAULT,
                        searcher.leafContexts[0]
                    )

                // Check that a normal collector is created without timeout
                assertFalse(noTimeoutCollector is TimeLimitingKnnCollectorManager.TimeLimitingKnnCollector)
                noTimeoutCollector.collect(0, 0f)
                assertFalse(noTimeoutCollector.earlyTerminated())

                // Check that results are complete
                val noTimeoutTopDocs = noTimeoutCollector.topDocs()
                assertEquals(TotalHits.Relation.EQUAL_TO, noTimeoutTopDocs.totalHits.relation)
                assertEquals(1, noTimeoutTopDocs.scoreDocs.size)

                // A collector manager that immediately times out
                val timeoutManager = TimeLimitingKnnCollectorManager(delegate) { true }
                val timeoutCollector =
                    timeoutManager.newCollector(
                        Int.MAX_VALUE,
                        org.gnit.lucenekmp.search.knn.KnnSearchStrategy.Hnsw.DEFAULT,
                        searcher.leafContexts[0]
                    )

                // Check that a time limiting collector is created, which returns partial results
                assertFalse(timeoutCollector is TopKnnCollector)
                timeoutCollector.collect(0, 0f)
                assertTrue(timeoutCollector.earlyTerminated())

                // Check that partial results are returned
                val timeoutTopDocs = timeoutCollector.topDocs()
                assertEquals(TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO, timeoutTopDocs.totalHits.relation)
                assertEquals(1, timeoutTopDocs.scoreDocs.size)
            }
        }
    }

    /** Test that the query times out correctly. */
    @Throws(IOException::class)
    open fun testTimeout() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)

                val query = getKnnVectorQuery("field", floatArrayOf(0.0f, 1.0f), 2)
                val exactQuery = getKnnVectorQuery("field", floatArrayOf(0.0f, 1.0f), 10, MatchAllDocsQuery())

                assertEquals(2, searcher.count(query)) // Expect some results without timeout
                assertEquals(3, searcher.count(exactQuery)) // Same for exact search

                searcher.timeout = QueryTimeout { true } // Immediately timeout
                assertEquals(0, searcher.count(query)) // Expect no results with the timeout
                assertEquals(0, searcher.count(exactQuery)) // Same for exact search

                searcher.timeout = CountingQueryTimeout(1) // Only score 1 doc
                // Note: We get partial results when the HNSW graph has 1 layer, but no results for > 1 layer
                // because the timeout is exhausted while finding the best entry node for the last level
                assertTrue(searcher.count(query) <= 1) // Expect at most 1 result

                searcher.timeout = CountingQueryTimeout(1) // Only score 1 doc
                assertEquals(1, searcher.count(exactQuery)) // Expect only 1 result
            }
        }
    }

    /** Creates a new directory and adds documents with the given vectors as kNN vector fields */
    @Throws(IOException::class)
    fun getIndexStore(field: String, vararg contents: FloatArray): Directory {
        return getIndexStore(field, VectorSimilarityFunction.EUCLIDEAN, *contents)
    }

    /**
     * Creates a new directory and adds documents with the given vectors with similarity as kNN vector
     * fields
     */
    @Throws(IOException::class)
    fun getIndexStore(
        field: String,
        vectorSimilarityFunction: VectorSimilarityFunction,
        vararg contents: FloatArray
    ): Directory {
        val indexStore = newDirectoryForTest()
        val writer = RandomIndexWriter(random(), indexStore)
        for (i in contents.indices) {
            var doc = Document()
            doc.add(getKnnVectorField(field, contents[i], vectorSimilarityFunction))
            doc.add(StringField("id", "id$i", Field.Store.YES))
            writer.addDocument(doc)
            if (randomBoolean()) {
                // Add some documents without a vector
                for (j in 0 until randomIntBetween(1, 5)) {
                    doc = Document()
                    doc.add(StringField("other", "value", Field.Store.NO))
                    // Add fields that will be matched by our test filters but won't have vectors
                    doc.add(StringField("id", "id$j", Field.Store.YES))
                    writer.addDocument(doc)
                }
            }
        }
        // Add some documents without a vector
        for (i in 0 until 5) {
            val doc = Document()
            doc.add(StringField("other", "value", Field.Store.NO))
            writer.addDocument(doc)
        }
        writer.close()
        return indexStore
    }

    /**
     * Creates a new directory and adds documents with the given vectors as kNN vector fields,
     * preserving the order of the added documents.
     */
    @Throws(IOException::class)
    private fun getStableIndexStore(field: String, vararg contents: FloatArray): Directory {
        return getStableIndexStore(field, VectorSimilarityFunction.EUCLIDEAN, *contents)
    }

    @Throws(IOException::class)
    private fun getStableIndexStore(
        field: String,
        similarityFunction: VectorSimilarityFunction,
        vararg contents: FloatArray
    ): Directory {
        val indexStore = newDirectoryForTest()
        IndexWriter(indexStore, configStandardCodec()).use { writer ->
            for (i in contents.indices) {
                val doc = Document()
                doc.add(getKnnVectorField(field, contents[i], similarityFunction))
                doc.add(StringField("id", "id$i", Field.Store.YES))
                writer.addDocument(doc)
            }
            // Add some documents without a vector
            for (i in 0 until 5) {
                val doc = Document()
                doc.add(StringField("other", "value", Field.Store.NO))
                writer.addDocument(doc)
            }
        }
        return indexStore
    }

    @Throws(IOException::class)
    private fun assertMatches(searcher: IndexSearcher, q: Query, expectedMatches: Int) {
        val result = searcher.search(q, 1000).scoreDocs
        assertEquals(expectedMatches, result.size)
    }

    @Throws(IOException::class)
    fun assertIdMatches(reader: IndexReader, expectedId: String, scoreDoc: ScoreDoc) {
        val actualId = reader.storedFields().document(scoreDoc.doc).get("id")
        assertEquals(expectedId, actualId)
    }

    @Throws(IOException::class)
    fun assertTopIdsMatches(reader: IndexReader, expectedIds: Set<String>, scoreDocs: Array<ScoreDoc>) {
        val actualIds = mutableSetOf<String>()
        for (scoreDoc in scoreDocs) {
            actualIds.add(reader.storedFields().document(scoreDoc.doc).get("id")!!)
        }
        assertEquals(expectedIds.size, actualIds.size)
        assertEquals(expectedIds, actualIds)
    }

    fun assertDocScoreQueryToString(query: Query) {
        val queryString = query.toString("ignored")
        // The string should contain matching docIds and their score.
        // Since a forceMerge could occur in this test, we must not assert that a specific doc_id is
        // matched
        // But that instead the string format is expected and that the max score is 1.0
        assertTrue(queryString.matches(Regex("""DocAndScoreQuery\[\d+,...]\[\d+\.\d+,...],1\.0""")))
    }

    /**
     * A version of [AbstractKnnVectorQuery] that throws an error when an exact search is run.
     * This allows us to check what search strategy is being used.
     */
    private class NoLiveDocsDirectoryReader(inReader: DirectoryReader) : FilterDirectoryReader(
        inReader,
        object : SubReaderWrapper() {
            override fun wrap(reader: LeafReader): LeafReader {
                return NoLiveDocsLeafReader(reader)
            }
        }
    ) {
        @Throws(IOException::class)
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return NoLiveDocsDirectoryReader(`in`)
        }

        override val readerCacheHelper: CacheHelper?
            get() = `in`.readerCacheHelper
    }

    private class NoLiveDocsLeafReader(inReader: LeafReader) : FilterLeafReader(inReader) {
        override fun numDocs(): Int {
            return 0
        }

        override val liveDocs: Bits
            get() = Bits.MatchNoBits(`in`.maxDoc())

        override val readerCacheHelper: CacheHelper?
            get() = `in`.readerCacheHelper

        override val coreCacheHelper: CacheHelper?
            get() = `in`.coreCacheHelper
    }

    class ThrowingBitSetQuery(private val docs: FixedBitSet) : Query() {
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : ConstantScoreWeight(this, boost) {
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                    val bitSetIterator = ThrowingBitSetIterator(docs, docs.approximateCardinality().toLong())
                    val scorer = ConstantScoreScorer(score(), scoreMode, bitSetIterator)
                    return DefaultScorerSupplier(scorer)
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                }
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun toString(field: String?): String {
            return "throwingBitSetQuery"
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && docs == (other as ThrowingBitSetQuery).docs
        }

        override fun hashCode(): Int {
            return 31 * classHash() + docs.hashCode()
        }
    }

    private class ThrowingBitSetIterator(docs: FixedBitSet, cost: Long) : BitSetIterator(docs, cost) {
        override val bitSet: BitSet
            get() = throw UnsupportedOperationException("reusing BitSet is not supported")
    }

    @Throws(IOException::class)
    open fun testSameFieldDifferentFormats() {
        newDirectoryForTest().use { directory ->
            val mockAnalyzer = MockAnalyzer(random())
            var iwc = newIndexWriterConfig(mockAnalyzer)
            val format1 = randomVectorFormat(VectorEncoding.FLOAT32)
            val format2 = randomVectorFormat(VectorEncoding.FLOAT32)
            iwc.setCodec(TestUtil.alwaysKnnVectorsFormat(format1))

            IndexWriter(directory, iwc).use { iwriter ->
                var doc = Document()
                doc.add(getKnnVectorField("field1", floatArrayOf(1f, 1f, 1f)))
                iwriter.addDocument(doc)

                doc.clear()
                doc.add(getKnnVectorField("field1", floatArrayOf(1f, 2f, 3f)))
                iwriter.addDocument(doc)
                iwriter.commit()
            }

            iwc = newIndexWriterConfig(mockAnalyzer)
            iwc.setCodec(TestUtil.alwaysKnnVectorsFormat(format2))

            IndexWriter(directory, iwc).use { iwriter ->
                var doc = Document()
                doc.clear()
                doc.add(getKnnVectorField("field1", floatArrayOf(1f, 1f, 2f)))
                iwriter.addDocument(doc)

                doc.clear()
                doc.add(getKnnVectorField("field1", floatArrayOf(4f, 5f, 6f)))
                iwriter.addDocument(doc)
                iwriter.commit()
            }

            DirectoryReader.open(directory).use { ireader ->
                val vectorQuery = getKnnVectorQuery("field1", floatArrayOf(1f, 2f, 3f), 10)
                val hits1 = IndexSearcher(ireader).search(vectorQuery, 4)
                assertEquals(4, hits1.scoreDocs.size)
            }
        }
    }

    private fun randomVectorFormat(encoding: VectorEncoding): KnnVectorsFormat {
        return TestUtil.getDefaultKnnVectorsFormat()
    }

    private class CountingQueryTimeout(private var remaining: Int) : QueryTimeout {
        override fun shouldExit(): Boolean {
            if (remaining > 0) {
                remaining--
                return false
            }
            return true
        }
    }
}
