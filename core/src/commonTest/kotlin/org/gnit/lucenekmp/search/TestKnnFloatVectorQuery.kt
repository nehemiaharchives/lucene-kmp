package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Ported from lucene/core/src/test/org/apache/lucene/search/TestKnnFloatVectorQuery.java
 * Skeleton with TODOs for future implementation.
 */
class TestKnnFloatVectorQuery : BaseKnnVectorQueryTestCase() {
    override fun getKnnVectorQuery(field: String, query: FloatArray, k: Int, queryFilter: Query?): KnnFloatVectorQuery {
        return KnnFloatVectorQuery(field, query, k, queryFilter)
    }

    override fun getThrowingKnnVectorQuery(field: String, query: FloatArray, k: Int, queryFilter: Query?): AbstractKnnVectorQuery {
        // TODO: provide throwing query when needed
        return KnnFloatVectorQuery(field, query, k, queryFilter)
    }

    override fun randomVector(dim: Int): FloatArray {
        // TODO: implement with random values
        return FloatArray(dim)
    }

    override fun getKnnVectorField(name: String, vector: FloatArray, similarityFunction: VectorSimilarityFunction): Field {
        return KnnFloatVectorField(name, vector, similarityFunction)
    }

    override fun getKnnVectorField(name: String, vector: FloatArray): Field {
        return KnnFloatVectorField(name, vector)
    }

    @Ignore
    @Test
    fun testToString() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testVectorEncodingMismatch() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testGetTarget() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testScoreNegativeDotProduct() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testScoreDotProduct() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreQueryBasics() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreQueryBasicsExact() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreQueryThrowsForUnsupported() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreQueryNextDoc() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreQuerySortsByField() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreQuerySortsByScore() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreCollectorManager() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreCollectorManagerThrowsForUnsupported() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreCollectorManagerSortsByField() {
        // TODO: implement
    }

    @Ignore
    @Test
    fun testDocAndScoreCollectorManagerSortsByScore() {
        // TODO: implement
    }
}

