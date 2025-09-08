package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.AbstractKnnVectorQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun testEquals() {
        val q1 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10)
        val filter1 = TermQuery(Term("id", "id1"))
        val q2 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10, filter1)

        assertNotEquals(q2, q1)
        assertNotEquals(q1, q2)
        assertEquals(q2, getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10, filter1))

        val filter2 = TermQuery(Term("id", "id2"))
        assertNotEquals(q2, getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10, filter2))

        assertEquals(q1, getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10))

        assertNotNull(q1)
        assertTrue(q1 != TermQuery(Term("f1", "x")))
        assertNotEquals(q1, getKnnVectorQuery("f2", floatArrayOf(0f, 1f), 10))
        assertNotEquals(q1, getKnnVectorQuery("f1", floatArrayOf(1f, 1f), 10))
        assertNotEquals(q1, getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 2))
        assertNotEquals(q1, getKnnVectorQuery("f1", floatArrayOf(0f), 10))
    }

    @Test
    fun testGetField() {
        val q1 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 10)
        val filter1 = TermQuery(Term("id", "id1"))
        val q2 = getKnnVectorQuery("f2", floatArrayOf(0f, 1f), 10, filter1)

        assertEquals("f1", q1.field)
        assertEquals("f2", q2.field)
    }

    @Test
    fun testGetK() {
        val q1 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 6)
        val filter1 = TermQuery(Term("id", "id1"))
        val q2 = getKnnVectorQuery("f2", floatArrayOf(0f, 1f), 7, filter1)

        assertEquals(6, q1.k)
        assertEquals(7, q2.k)
    }

    @Test
    fun testGetFilter() {
        val q1 = getKnnVectorQuery("f1", floatArrayOf(0f, 1f), 6)
        val filter1 = TermQuery(Term("id", "id1"))
        val q2 = getKnnVectorQuery("f2", floatArrayOf(0f, 1f), 7, filter1)

        assertNull(q1.filter)
        assertEquals(filter1, q2.filter)
    }

    @Test
    fun testEmptyIndex() {
        val indexStore = getIndexStore("field")
        try {
            val reader = DirectoryReader.open(indexStore)
            try {
                val searcher = IndexSearcher(reader)
                val kvq = getKnnVectorQuery("field", floatArrayOf(1f, 2f), 10)
                assertMatches(searcher, kvq, 0)
                val q = searcher.rewrite(kvq)
                assertTrue(q is MatchNoDocsQuery)
            } finally {
                reader.close()
            }
        } finally {
            indexStore.close()
        }
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

    // Helper methods required by tests
    protected fun getIndexStore(field: String, vararg contents: FloatArray): BaseDirectoryWrapper {
        return getIndexStore(field, VectorSimilarityFunction.EUCLIDEAN, *contents)
    }

    protected fun getIndexStore(
        field: String,
        similarityFunction: VectorSimilarityFunction,
        vararg contents: FloatArray
    ): BaseDirectoryWrapper {
        val dir = newDirectoryForTest()
        val writer = IndexWriter(dir, configStandardCodec())
        try {
            for (i in contents.indices) {
                val doc = Document()
                doc.add(getKnnVectorField(field, contents[i], similarityFunction))
                doc.add(StringField("id", "id" + i, Field.Store.YES))
                writer.addDocument(doc)
            }
            if (contents.isNotEmpty()) {
                repeat(5) {
                    val doc = Document()
                    doc.add(StringField("other", "value", Field.Store.NO))
                    writer.addDocument(doc)
                }
            }
            writer.commit()
        } finally {
            writer.close()
        }
        return dir
    }

    private fun assertMatches(searcher: IndexSearcher, q: Query, expectedMatches: Int) {
        val result = searcher.search(q, 1000).scoreDocs
        assertEquals(expectedMatches, result.size)
    }
}

