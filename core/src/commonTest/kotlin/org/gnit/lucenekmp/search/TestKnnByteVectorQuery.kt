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
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnByteVectorField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.util.TestVectorUtil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class TestKnnByteVectorQuery : BaseKnnVectorQueryTestCase() {
    override fun getKnnVectorQuery(
        field: String,
        query: FloatArray,
        k: Int,
        queryFilter: Query?
    ): AbstractKnnVectorQuery {
        return KnnByteVectorQuery(field, floatToBytes(query), k, queryFilter)
    }

    override fun getThrowingKnnVectorQuery(
        field: String,
        query: FloatArray,
        k: Int,
        queryFilter: Query?
    ): AbstractKnnVectorQuery {
        return ThrowingKnnVectorQuery(field, floatToBytes(query), k, queryFilter)
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
        return KnnByteVectorField(name, floatToBytes(vector), similarityFunction)
    }

    override fun getKnnVectorField(name: String, vector: FloatArray): Field {
        return KnnByteVectorField(name, floatToBytes(vector), VectorSimilarityFunction.EUCLIDEAN)
    }

    @Throws(IOException::class)
    @Test
    fun testToString() {
        getIndexStore(
            "field",
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 2f),
            floatArrayOf(0f, 0f)
        ).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                var query = getKnnVectorQuery("field", floatArrayOf(0f, 1f), 10)
                assertEquals("KnnByteVectorQuery:field[0,...][10]", query.toString("ignored"))

                assertDocScoreQueryToString(query.rewrite(newSearcher(reader)))

                // test with filter
                val filter = TermQuery(Term("id", "text"))
                query = getKnnVectorQuery("field", floatArrayOf(0f, 1f), 10, filter)
                assertEquals("KnnByteVectorQuery:field[0,...][10][id:text]", query.toString("ignored"))
            }
        }
    }

    @Test
    fun testGetTarget() {
        val queryVectorBytes = floatToBytes(floatArrayOf(0f, 1f))
        val q1 = KnnByteVectorQuery("f1", queryVectorBytes, 10)
        assertContentEquals(queryVectorBytes, q1.targetCopy)
        assertNotSame(queryVectorBytes, q1.targetCopy)
    }

    @Throws(IOException::class)
    @Test
    fun testVectorEncodingMismatch() {
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
                val query = KnnFloatVectorQuery("field", floatArrayOf(0f, 1f), 10, filter)
                val searcher = newSearcher(reader)
                expectThrows(IllegalStateException::class) {
                    searcher.search(query, 10)
                }
            }
        }
    }

    class ThrowingKnnVectorQuery(
        field: String,
        target: ByteArray,
        k: Int,
        filter: Query?
    ) : KnnByteVectorQuery(field, target, k, filter, KnnSearchStrategy.Hnsw(0)) {
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

    companion object {
        fun floatToBytes(query: FloatArray): ByteArray {
            val bytes = ByteArray(query.size)
            for (i in query.indices) {
                check(
                    query[i] <= Byte.MAX_VALUE && query[i] >= Byte.MIN_VALUE && (query[i] % 1) == 0f
                ) { "float value cannot be converted to byte; provided: ${query[i]}" }
                bytes[i] = query[i].toInt().toByte()
            }
            return bytes
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
