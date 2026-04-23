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
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.KnnByteVectorField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.TestVectorUtil

@OptIn(ExperimentalAtomicApi::class)
open class TestSeededKnnByteVectorQuery : BaseKnnVectorQueryTestCase() {
    override fun getKnnVectorQuery(
        field: String,
        query: FloatArray,
        k: Int,
        queryFilter: Query?
    ): AbstractKnnVectorQuery {
        val knnByteVectorQuery =
            KnnByteVectorQuery(field, TestKnnByteVectorQuery.floatToBytes(query), k, queryFilter)
        return SeededKnnVectorQuery.fromByteQuery(knnByteVectorQuery, MATCH_NONE)
    }

    override fun getThrowingKnnVectorQuery(
        field: String,
        vec: FloatArray,
        k: Int,
        query: Query?
    ): AbstractKnnVectorQuery {
        val knnByteVectorQuery =
            TestKnnByteVectorQuery.ThrowingKnnVectorQuery(
                field,
                TestKnnByteVectorQuery.floatToBytes(vec),
                k,
                query
            )
        return SeededKnnVectorQuery.fromByteQuery(knnByteVectorQuery, MATCH_NONE)
    }

    override fun randomVector(dim: Int): FloatArray {
        val b = TestVectorUtil.randomVectorBytes(dim)
        val v = FloatArray(b.size)
        var vi = 0
        for (i in v.indices) {
            v[vi++] = b[i].toFloat()
        }
        return v
    }

    override fun getKnnVectorField(
        name: String,
        vector: FloatArray,
        similarityFunction: VectorSimilarityFunction
    ): Field {
        return KnnByteVectorField(
            name,
            TestKnnByteVectorQuery.floatToBytes(vector),
            similarityFunction
        )
    }

    override fun getKnnVectorField(name: String, vector: FloatArray): Field {
        return KnnByteVectorField(
            name,
            TestKnnByteVectorQuery.floatToBytes(vector),
            VectorSimilarityFunction.EUCLIDEAN
        )
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
                    val byteVectorQuery =
                        KnnByteVectorQuery(
                            "field",
                            TestKnnByteVectorQuery.floatToBytes(randomVector(dimension)),
                            k,
                            filter
                        )
                    var knnQuery: Query = SeededKnnVectorQuery.fromByteQuery(byteVectorQuery, seed)
                    assertEquals(0, searcher.count(knnQuery))
                    // No seed documents -- falls back on full approx search
                    seed = MatchNoDocsQuery()
                    knnQuery = SeededKnnVectorQuery.fromByteQuery(byteVectorQuery, seed)
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
                    var byteVectorQuery =
                        KnnByteVectorQuery(
                            "field",
                            TestKnnByteVectorQuery.floatToBytes(randomVector(dimension)),
                            k,
                            filter
                        )
                    var query =
                        AssertingSeededKnnVectorQuery(byteVectorQuery, seed1, null, seedCalls)
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
                    byteVectorQuery =
                        KnnByteVectorQuery(
                            "field",
                            TestKnnByteVectorQuery.floatToBytes(randomVector(dimension)),
                            k,
                            null
                        )
                    query =
                        AssertingSeededKnnVectorQuery(
                            byteVectorQuery,
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
                    query = AssertingSeededKnnVectorQuery(byteVectorQuery, seed3, null, null)
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

    open inner class AssertingSeededKnnVectorQuery(
        query: AbstractKnnVectorQuery,
        seed: Query,
        seedWeight: Weight?,
        private val seedCalls: AtomicInteger?
    ) : SeededKnnVectorQuery(query, seed, seedWeight) {
        override fun rewrite(indexSearcher: IndexSearcher): Query {
            if (seedWeight != null) {
                return super.rewrite(indexSearcher)
            }
            val rewritten =
                AssertingSeededKnnVectorQuery(delegate, seed, createSeedWeight(indexSearcher), seedCalls)
            return rewritten.rewrite(indexSearcher)
        }

        @Throws(IOException::class)
        override fun approximateSearch(
            context: LeafReaderContext,
            acceptDocs: Bits?,
            visitedLimit: Int,
            knnCollectorManager: KnnCollectorManager
        ): TopDocs {
            return delegate.approximateSearch(
                context,
                acceptDocs,
                visitedLimit,
                AssertingSeededCollectorManager(SeededCollectorManager(knnCollectorManager))
            )
        }

        inner class AssertingSeededCollectorManager(delegate: SeededCollectorManager) :
            SeededCollectorManager(delegate) {
            @Throws(IOException::class)
            override fun newCollector(
                visitedLimit: Int,
                searchStrategy: KnnSearchStrategy,
                context: LeafReaderContext
            ): KnnCollector {
                val knnCollector =
                    knnCollectorManager.newCollector(visitedLimit, searchStrategy, context)
                if (knnCollector.searchStrategy is KnnSearchStrategy.Seeded) {
                    val seeded = knnCollector.searchStrategy as KnnSearchStrategy.Seeded
                    if (seedCalls == null && seeded.numberOfEntryPoints() > 0) {
                        fail("Expected non-seeded collector but received: $knnCollector")
                    }
                    return AssertingKnnCollector(knnCollector)
                }
                if (seedCalls != null) {
                    fail("Expected seeded collector but received: $knnCollector")
                }
                return knnCollector
            }
        }

        inner class AssertingKnnCollector(collector: KnnCollector) : KnnCollector.Decorator(collector) {
            override val searchStrategy: KnnSearchStrategy?
                get() {
                    val searchStrategy = collector.searchStrategy
                    if (searchStrategy is KnnSearchStrategy.Seeded) {
                        return AssertingSeededStrategy(searchStrategy)
                    }
                    return searchStrategy
                }

            inner class AssertingSeededStrategy(private val seeded: KnnSearchStrategy.Seeded) :
                KnnSearchStrategy.Seeded(
                    seeded.entryPoints(),
                    seeded.numberOfEntryPoints(),
                    seeded.originalStrategy()
                ) {
                override fun numberOfEntryPoints(): Int {
                    return seeded.numberOfEntryPoints()
                }

                override fun entryPoints(): DocIdSetIterator {
                    val iterator = seeded.entryPoints()
                    assertTrue(iterator.cost() > 0)
                    seedCalls!!.incrementAndFetch()
                    return iterator
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
