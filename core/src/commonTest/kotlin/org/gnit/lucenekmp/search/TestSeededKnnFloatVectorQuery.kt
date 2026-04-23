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

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.TestVectorUtil

@OptIn(ExperimentalAtomicApi::class)
open class TestSeededKnnFloatVectorQuery : BaseKnnVectorQueryTestCase() {
    override fun getKnnVectorQuery(
        field: String,
        query: FloatArray,
        k: Int,
        queryFilter: Query?
    ): AbstractKnnVectorQuery {
        val knnQuery = KnnFloatVectorQuery(field, query, k, queryFilter)
        return SeededKnnVectorQuery.fromFloatQuery(knnQuery, MATCH_NONE)
    }

    override fun getThrowingKnnVectorQuery(
        field: String,
        vec: FloatArray,
        k: Int,
        query: Query?
    ): AbstractKnnVectorQuery {
        val knnQuery = TestKnnFloatVectorQuery.ThrowingKnnVectorQuery(field, vec, k, query)
        return SeededKnnVectorQuery.fromFloatQuery(knnQuery, MATCH_NONE)
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
    open fun testSeedWithTimeout() {
        val numDocs = atLeast(50)
        val dimension = atLeast(5)
        val numIters = atLeast(5)
        newDirectoryForTest().use { d ->
            val iwc = IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())
            val w = RandomIndexWriter(random(), d, iwc)
            for (i in 0..<numDocs) {
                val doc = Document()
                doc.add(getKnnVectorField("field", randomVector(dimension)))
                doc.add(NumericDocValuesField("tag", i.toLong()))
                doc.add(IntPoint("tag", i))
                w.addDocument(doc)
            }
            w.close()

            DirectoryReader.open(d).use { reader ->
                val searcher = newSearcher(reader)
                searcher.timeout = { true }
                val k = random().nextInt(80) + 1
                for (i in 0..<numIters) {
                    // All documents as seeds
                    var seed: Query =
                        if (random().nextBoolean()) {
                            IntPoint.newRangeQuery("tag", 1, 6)
                        } else {
                            MatchAllDocsQuery()
                        }
                    val filter: Query? =
                        if (random().nextBoolean()) {
                            null
                        } else {
                            MatchAllDocsQuery()
                        }
                    val knnFloatVectorQuery =
                        KnnFloatVectorQuery("field", randomVector(dimension), k, filter)
                    var knnQuery: Query =
                        SeededKnnVectorQuery.fromFloatQuery(knnFloatVectorQuery, seed)
                    assertEquals(0, searcher.count(knnQuery))
                    // No seed documents -- falls back on full approx search
                    seed = MatchNoDocsQuery()
                    knnQuery = SeededKnnVectorQuery.fromFloatQuery(knnFloatVectorQuery, seed)
                    assertEquals(0, searcher.count(knnQuery))
                }
            }
        }
    }

    /** Tests with random vectors and a random seed. Uses RandomIndexWriter. */
    @Throws(IOException::class)
    @Test
    open fun testRandomWithSeed() {
        val numDocs = 1000
        val dimension = atLeast(5)
        val numIters = atLeast(10)
        var numDocsWithVector = 0
        newDirectoryForTest().use { d ->
            // Always use the default kNN format to have predictable behavior around when it hits
            // visitedLimit. This is fine since the test targets AbstractKnnVectorQuery logic, not the kNN
            // format
            // implementation.
            val iwc = IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())
            val w = RandomIndexWriter(random(), d, iwc)
            for (i in 0..<numDocs) {
                val doc = Document()
                if (random().nextBoolean()) {
                    // Randomly skip some vectors to test the mapping from docid to ordinals
                    doc.add(getKnnVectorField("field", randomVector(dimension)))
                    numDocsWithVector += 1
                }
                doc.add(NumericDocValuesField("tag", i.toLong()))
                doc.add(IntPoint("tag", i))
                w.addDocument(doc)
            }
            w.forceMerge(1)
            w.close()

            DirectoryReader.open(d).use { reader ->
                val searcher = newSearcher(reader)
                val helper = TestSeededKnnByteVectorQuery()
                for (i in 0..<numIters) {
                    // verify timeout collector wrapping is used
                    if (random().nextBoolean()) {
                        searcher.timeout = { false }
                    } else {
                        searcher.timeout = null
                    }
                    val k = random().nextInt(10) + 1
                    val n = random().nextInt(100) + 1
                    // we may get fewer results than requested if there are deletions, but this test doesn't
                    // check that
                    assertTrue(reader.hasDeletions() == false)

                    // All documents as seeds
                    val seedCalls = AtomicInteger(0)
                    val seed1: Query = MatchAllDocsQuery()
                    val filter: Query? =
                        if (random().nextBoolean()) {
                            null
                        } else {
                            MatchAllDocsQuery()
                        }
                    val knnFloatVectorQuery =
                        KnnFloatVectorQuery("field", randomVector(dimension), k, filter)
                    var query =
                        helper.AssertingSeededKnnVectorQuery(
                            knnFloatVectorQuery,
                            seed1,
                            null,
                            seedCalls
                        )
                    var results = searcher.search(query, n)
                    assertEquals(1, seedCalls.get())
                    var expected = minOf(minOf(n, k), numDocsWithVector)

                    assertEquals(expected, results.scoreDocs.size)
                    assertTrue(results.totalHits.value >= results.scoreDocs.size.toLong())
                    // verify the results are in descending score order
                    var last = Float.MAX_VALUE
                    for (scoreDoc in results.scoreDocs) {
                        assertTrue(scoreDoc.score <= last)
                        last = scoreDoc.score
                    }

                    // Restrictive seed query -- 6 documents
                    val seed2 = IntPoint.newRangeQuery("tag", 1, 6)
                    val seedCount =
                        searcher.count(
                            BooleanQuery.Builder()
                                .add(seed2, BooleanClause.Occur.MUST)
                                .add(FieldExistsQuery("field"), BooleanClause.Occur.MUST)
                                .build()
                        )
                    query =
                        helper.AssertingSeededKnnVectorQuery(
                            knnFloatVectorQuery,
                            seed2,
                            null,
                            if (seedCount > 0) seedCalls else null
                        )
                    results = searcher.search(query, n)
                    assertEquals(if (seedCount > 0) 2 else 1, seedCalls.get())
                    expected = minOf(minOf(n, k), reader.numDocs())
                    assertEquals(expected, results.scoreDocs.size)
                    assertTrue(results.totalHits.value >= results.scoreDocs.size.toLong())
                    // verify the results are in descending score order
                    last = Float.MAX_VALUE
                    for (scoreDoc in results.scoreDocs) {
                        assertTrue(scoreDoc.score <= last)
                        last = scoreDoc.score
                    }

                    // No seed documents -- falls back on full approx search
                    val seed3: Query = MatchNoDocsQuery()
                    query = helper.AssertingSeededKnnVectorQuery(knnFloatVectorQuery, seed3, null, null)
                    results = searcher.search(query, n)
                    expected = minOf(minOf(n, k), reader.numDocs())
                    assertEquals(expected, results.scoreDocs.size)
                    assertTrue(results.totalHits.value >= results.scoreDocs.size.toLong())
                    // verify the results are in descending score order
                    last = Float.MAX_VALUE
                    for (scoreDoc in results.scoreDocs) {
                        assertTrue(scoreDoc.score <= last)
                        last = scoreDoc.score
                    }
                }
            }
        }
    }

    companion object {
        private val MATCH_NONE: Query = MatchNoDocsQuery()
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
