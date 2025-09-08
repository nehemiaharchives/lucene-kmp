package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.AbstractKnnVectorQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Ported from lucene/core/src/test/org/apache/lucene/search/BaseKnnVectorQueryTestCase.java
 * Skeleton with TODOs for future implementation.
 */
abstract class BaseKnnVectorQueryTestCase : LuceneTestCase() {
    companion object {
        // handle quantization noise
        const val EPSILON: Float = 0.001f
    }

    abstract fun getKnnVectorQuery(field: String, query: FloatArray, k: Int, queryFilter: Query?): AbstractKnnVectorQuery

    abstract fun getThrowingKnnVectorQuery(field: String, query: FloatArray, k: Int, queryFilter: Query?): AbstractKnnVectorQuery

    fun getKnnVectorQuery(field: String, query: FloatArray, k: Int): AbstractKnnVectorQuery =
        getKnnVectorQuery(field, query, k, null)

    abstract fun randomVector(dim: Int): FloatArray

    abstract fun getKnnVectorField(name: String, vector: FloatArray, similarityFunction: VectorSimilarityFunction): Field

    abstract fun getKnnVectorField(name: String, vector: FloatArray): Field

    /**
     * Creates a new directory. Subclasses can override to test different directory implementations.
     */
    protected open fun newDirectoryForTest(): BaseDirectoryWrapper = BaseDirectoryWrapper(ByteBuffersDirectory())

    protected open fun configStandardCodec(): IndexWriterConfig =
        IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())

    @Ignore
    @Test
    fun testEquals() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testGetField() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testGetK() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testGetFilter() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testEmptyIndex() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testFindAll() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testFindFewer() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testSearchBoost() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testSimpleFilter() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testFilterWithNoVectorMatches() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDimensionMismatch() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testNonVectorField() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testIllegalArguments() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDifferentReader() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testScoreEuclidean() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testScoreCosine() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testScoreMIP() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testExplain() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testExplainMultipleSegments() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testSkewedIndex() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testRandomConsistencySingleThreaded() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testRandomConsistencyMultiThreaded() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDeletes() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testAllDeletes() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testMergeAwayAllValues() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testBitSetQuery() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testTimeLimitingKnnCollectorManager() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testTimeout() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testSameFieldDifferentFormats() {
        // TODO: implement
    }
}

