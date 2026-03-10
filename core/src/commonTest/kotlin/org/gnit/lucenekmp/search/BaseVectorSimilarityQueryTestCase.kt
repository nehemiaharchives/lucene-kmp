package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.hnsw.HnswUtil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

abstract class BaseVectorSimilarityQueryTestCase<V, F : Field, Q : AbstractVectorSimilarityQuery> : LuceneTestCase() {
    protected lateinit var vectorField: String
    protected lateinit var idField: String
    protected lateinit var function: VectorSimilarityFunction
    protected var numDocs: Int = 0
    protected var dim: Int = 0

    abstract fun getRandomVector(dim: Int): V

    abstract fun compare(vector1: V, vector2: V): Float

    abstract fun checkEquals(vector1: V, vector2: V): Boolean

    abstract fun getVectorField(name: String, vector: V, function: VectorSimilarityFunction): F

    abstract fun getVectorQuery(
        field: String,
        vector: V,
        traversalSimilarity: Float,
        resultSimilarity: Float,
        filter: Query?
    ): Q

    abstract fun getThrowingVectorQuery(
        field: String,
        vector: V,
        traversalSimilarity: Float,
        resultSimilarity: Float,
        filter: Query?
    ): Q

    open fun testEquals() {
        val field1 = "f1"
        val field2 = "f2"

        val vector1 = getRandomVector(dim)
        var vector2: V
        do {
            vector2 = getRandomVector(dim)
        } while (checkEquals(vector1, vector2))

        val traversalSimilarity1 = 0.3f
        val traversalSimilarity2 = 0.4f
        val resultSimilarity1 = 0.4f
        val resultSimilarity2 = 0.5f

        val filter1: Query = TermQuery(Term("t1", "v1"))
        val filter2: Query = TermQuery(Term("t2", "v2"))

        val query = getVectorQuery(field1, vector1, traversalSimilarity1, resultSimilarity1, filter1)

        assertEquals(query, getVectorQuery(field1, vector1, traversalSimilarity1, resultSimilarity1, filter1))
        assertFalse(query.equals(null))
        assertNotEquals(query, getVectorQuery(field2, vector1, traversalSimilarity1, resultSimilarity1, filter1))
        assertNotEquals(query, getVectorQuery(field1, vector2, traversalSimilarity1, resultSimilarity1, filter1))
        assertNotEquals(query, getVectorQuery(field1, vector1, traversalSimilarity2, resultSimilarity1, filter1))
        assertNotEquals(query, getVectorQuery(field1, vector1, traversalSimilarity1, resultSimilarity2, filter1))
        assertNotEquals(query, getVectorQuery(field1, vector1, traversalSimilarity1, resultSimilarity1, filter2))
    }

    @Throws(IOException::class)
    open fun testEmptyIndex() {
        numDocs = 0

        getIndexStore(*getRandomVectors(numDocs, dim)).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val query = getVectorQuery(
                    vectorField,
                    getRandomVector(dim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    null
                )
                assertEquals(0, searcher.count(query))
            }
        }
    }

    @Throws(IOException::class)
    open fun testExtremes() {
        getIndexStore(*getRandomVectors(numDocs, dim)).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                assumeTrue("graph is disconnected", HnswUtil.graphIsRooted(reader, vectorField))

                val query1 = getVectorQuery(
                    vectorField,
                    getRandomVector(dim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    null
                )
                assertEquals(numDocs, searcher.count(query1))

                val query2 = getVectorQuery(
                    vectorField,
                    getRandomVector(dim),
                    Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY,
                    null
                )
                assertEquals(0, searcher.count(query2))
            }
        }
    }

    @Throws(IOException::class)
    open fun testRandomFilter() {
        val startIndex = random().nextInt(numDocs)
        val endIndex = random().nextInt(startIndex, numDocs)
        val filter = IntField.newRangeQuery(idField, startIndex, endIndex)

        getIndexStore(*getRandomVectors(numDocs, dim)).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                assumeTrue("graph is disconnected", HnswUtil.graphIsRooted(reader, vectorField))
                val searcher = newSearcher(reader)
                val query = getVectorQuery(
                    vectorField,
                    getRandomVector(dim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    filter
                )

                val scoreDocs = searcher.search(query, numDocs).scoreDocs
                for (scoreDoc in scoreDocs) {
                    val id = getId(searcher, scoreDoc.doc)
                    assertTrue(id >= startIndex && id <= endIndex)
                }
                assertEquals(endIndex - startIndex + 1, scoreDocs.size)
            }
        }
    }

    @Throws(IOException::class)
    open fun testFilterWithNoMatches() {
        getIndexStore(*getRandomVectors(numDocs, dim)).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)

                val filter1: Query = TermQuery(Term("random_field", "random_value"))
                val query1 = getVectorQuery(
                    vectorField,
                    getRandomVector(dim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    filter1
                )
                assertEquals(0, searcher.count(query1))

                val filter2 = IntField.newExactQuery(idField, -1)
                val query2 = getVectorQuery(
                    vectorField,
                    getRandomVector(dim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    filter2
                )
                assertEquals(0, searcher.count(query2))
            }
        }
    }

    @Throws(IOException::class)
    open fun testDimensionMismatch() {
        val newDim = atLeast(dim + 1)

        getIndexStore(*getRandomVectors(numDocs, dim)).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val query = getVectorQuery(
                    vectorField,
                    getRandomVector(newDim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    null
                )
                val e = expectThrows(IllegalArgumentException::class) { searcher.count(query) }
                assertEquals("vector query dimension: $newDim differs from field dimension: $dim", e.message)
            }
        }
    }

    @Throws(IOException::class)
    open fun testNonVectorsField() {
        getIndexStore(*getRandomVectors(numDocs, dim)).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)

                val query1 = getVectorQuery(
                    "random_field",
                    getRandomVector(dim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    null
                )
                assertEquals(0, searcher.count(query1))

                val query2 = getVectorQuery(
                    idField,
                    getRandomVector(dim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    null
                )
                assertEquals(0, searcher.count(query2))
            }
        }
    }

    @Throws(IOException::class)
    open fun testSomeDeletes() {
        val startIndex = random().nextInt(numDocs)
        val endIndex = random().nextInt(startIndex, numDocs)
        val delete = IntField.newRangeQuery(idField, startIndex, endIndex)

        getIndexStore(*getRandomVectors(numDocs, dim)).use { indexStore ->
            IndexWriter(indexStore, newIndexWriterConfig()).use { writer ->
                writer.deleteDocuments(delete)
                writer.commit()

                DirectoryReader.open(indexStore).use { reader ->
                    assumeTrue("graph is disconnected", HnswUtil.graphIsRooted(reader, vectorField))
                    val searcher = newSearcher(reader)
                    val query = getVectorQuery(
                        vectorField,
                        getRandomVector(dim),
                        Float.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        null
                    )

                    val scoreDocs = searcher.search(query, numDocs).scoreDocs
                    for (scoreDoc in scoreDocs) {
                        val id = getId(searcher, scoreDoc.doc)
                        assertFalse(id >= startIndex && id <= endIndex)
                    }
                    assertEquals(numDocs - endIndex + startIndex - 1, scoreDocs.size)
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testAllDeletes() {
        getIndexStore(*getRandomVectors(numDocs, dim)).use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { writer ->
                writer.deleteDocuments(MatchAllDocsQuery())
                writer.commit()

                DirectoryReader.open(dir).use { reader ->
                    val searcher = newSearcher(reader)
                    val query = getVectorQuery(
                        vectorField,
                        getRandomVector(dim),
                        Float.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        null
                    )
                    assertEquals(0, searcher.count(query))
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testBoostQuery() {
        val boost = 5f + random().nextFloat() * 5f
        val delta = 1e-3f

        getIndexStore(*getRandomVectors(numDocs, dim)).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)

                val query1 = getVectorQuery(
                    vectorField,
                    getRandomVector(dim),
                    Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    null
                )
                val scoreDocs1 = searcher.search(query1, numDocs).scoreDocs

                val query2 = BoostQuery(query1, boost)
                val scoreDocs2 = searcher.search(query2, numDocs).scoreDocs

                assertEquals(scoreDocs1.size, scoreDocs2.size)
                for (i in scoreDocs1.indices) {
                    val boostedDoc = scoreDocs2.firstOrNull { it.doc == scoreDocs1[i].doc }
                    assertTrue(boostedDoc != null)
                    assertEquals(boost * scoreDocs1[i].score, boostedDoc.score, delta)
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testVectorsAboveSimilarity() {
        val numAccepted = random().nextInt(numDocs / 3, numDocs / 2)
        val delta = 1e-3f

        val vectors = getRandomVectors(numDocs, dim)
        val queryVector = getRandomVector(dim)
        val resultSimilarity = getSimilarity(vectors, queryVector, numAccepted)

        val scores = HashMap<Int, Float>()
        for (i in vectors.indices) {
            val score = compare(queryVector, vectors[i])
            if (score >= resultSimilarity) {
                scores[i] = score
            }
        }

        getStableIndexStore(*vectors).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val query = getVectorQuery(vectorField, queryVector, Float.NEGATIVE_INFINITY, resultSimilarity, null)
                val scoreDocs = searcher.search(query, numDocs).scoreDocs
                for (scoreDoc in scoreDocs) {
                    val id = getId(searcher, scoreDoc.doc)
                    assertTrue(scores.containsKey(id))
                    assertEquals(scores[id]!!, scoreDoc.score, delta)
                }
                assertEquals(scores.size, scoreDocs.size)
            }
        }
    }

    @Throws(IOException::class)
    open fun testFallbackToExact() {
        val numFiltered = numDocs / 5
        val targetVisited = numDocs

        val vectors = getRandomVectors(numDocs, dim)
        val queryVector = getRandomVector(dim)
        val resultSimilarity = getSimilarity(vectors, queryVector, targetVisited)
        val filter = IntField.newSetQuery(idField, *getFiltered(numFiltered))

        getIndexStore(*vectors).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                val query = getThrowingVectorQuery(vectorField, queryVector, resultSimilarity, resultSimilarity, filter)
                expectThrows(UnsupportedOperationException::class) { searcher.count(query) }
            }
        }
    }

    @Throws(IOException::class)
    open fun testApproximate() {
        val numFiltered = numDocs - 1
        val targetVisited = random().nextInt(1, numFiltered / 10)

        val vectors = getRandomVectors(numDocs, dim)
        val queryVector = getRandomVector(dim)
        val resultSimilarity = getSimilarity(vectors, queryVector, targetVisited)
        val filter = IntField.newSetQuery(idField, *getFiltered(numFiltered))

        getIndexStore(*vectors).use { indexStore ->
            IndexWriter(indexStore, newIndexWriterConfig()).use { writer ->
                writer.forceMerge(1)
                writer.commit()

                DirectoryReader.open(indexStore).use { reader ->
                    val searcher = newSearcher(reader)
                    val query = getThrowingVectorQuery(vectorField, queryVector, resultSimilarity, resultSimilarity, filter)
                    assertTrue(searcher.count(query) <= numFiltered)
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testTimeout() {
        val vectors = getRandomVectors(numDocs, dim)
        val queryVector = getRandomVector(dim)

        getIndexStore(*vectors).use { indexStore ->
            DirectoryReader.open(indexStore).use { reader ->
                val searcher = newSearcher(reader)
                searcher.queryCache = null

                val query = CountingQuery(
                    getVectorQuery(
                        vectorField,
                        queryVector,
                        Float.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        null
                    )
                )

                assertEquals(numDocs, searcher.count(query))

                searcher.timeout = QueryTimeout { true }
                assertEquals(0, searcher.count(query))

                searcher.timeout = CountingQueryTimeout(numDocs - 1)
                val count = searcher.count(query)
                assertTrue(count > 0 && count < numDocs, "0 < count=$count < numDocs=$numDocs")

                val numFiltered = random().nextInt(numDocs / 2, numDocs)
                val filter = IntField.newSetQuery(idField, *getFiltered(numFiltered))
                val filteredQuery = CountingQuery(
                    getVectorQuery(
                        vectorField,
                        queryVector,
                        Float.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        filter
                    )
                )

                searcher.timeout = QueryTimeout { false }
                assertEquals(numFiltered, searcher.count(filteredQuery))

                searcher.timeout = CountingQueryTimeout(numFiltered - 1)
                val filteredCount = searcher.count(filteredQuery)
                assertTrue(
                    filteredCount > 0 && filteredCount < numFiltered,
                    "0 < filteredCount=$filteredCount < numFiltered=$numFiltered"
                )
            }
        }
    }

    protected fun getSimilarity(vectors: Array<V>, queryVector: V, targetVisited: Int): Float {
        assertTrue(targetVisited >= 0 && targetVisited <= numDocs)
        if (targetVisited == 0) {
            return Float.POSITIVE_INFINITY
        }

        val scores = FloatArray(numDocs)
        for (i in vectors.indices) {
            scores[i] = compare(queryVector, vectors[i])
        }
        scores.sort()
        return scores[numDocs - targetVisited]
    }

    protected fun getFiltered(numFiltered: Int): IntArray {
        val accepted = mutableSetOf<Int>()
        while (accepted.size < numFiltered) {
            accepted.add(random().nextInt(numDocs))
        }
        return accepted.toIntArray().sortedArray()
    }

    protected fun getId(searcher: IndexSearcher, doc: Int): Int {
        return searcher.storedFields().document(doc).getField(idField)!!.numericValue()!!.toInt()
    }

    @Suppress("UNCHECKED_CAST")
    protected fun getRandomVectors(numDocs: Int, dim: Int): Array<V> {
        return Array<Any?>(numDocs) { getRandomVector(dim) } as Array<V>
    }

    protected fun getIndexStore(vararg vectors: V): Directory {
        val dir = newDirectory()
        RandomIndexWriter(random(), dir).use { writer ->
            for (i in vectors.indices) {
                val doc = Document()
                doc.add(getVectorField(vectorField, vectors[i], function))
                doc.add(IntField(idField, i, Field.Store.YES))
                writer.addDocument(doc)
            }
        }
        return dir
    }

    protected fun getStableIndexStore(vararg vectors: V): Directory {
        val dir = newDirectory()
        val iwc = IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())
        IndexWriter(dir, iwc).use { writer ->
            for (i in vectors.indices) {
                val doc = Document()
                doc.add(getVectorField(vectorField, vectors[i], function))
                doc.add(IntField(idField, i, Field.Store.YES))
                writer.addDocument(doc)
            }
        }
        return dir
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

    private class CountingQuery(private val delegate: Query) : Query() {
        @Throws(Exception::class)
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : Weight(this) {
                private val delegateWeight = delegate.createWeight(searcher, scoreMode, boost)

                override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                    return delegateWeight.explain(context, doc)
                }

                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    return delegateWeight.scorerSupplier(context)
                }

                override fun count(context: LeafReaderContext): Int {
                    val scorer = scorer(context) ?: return 0
                    var count = 0
                    val iterator = scorer.iterator()
                    while (iterator.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                        count++
                    }
                    return count
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return delegateWeight.isCacheable(ctx)
                }
            }
        }

        override fun toString(field: String?): String {
            return "${this::class.simpleName}[${delegate.toString(field)}]"
        }

        override fun visit(visitor: QueryVisitor) {
            visitor.visitLeaf(this)
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && delegate == (other as CountingQuery).delegate
        }

        override fun hashCode(): Int {
            return delegate.hashCode()
        }
    }
}
