package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.search.AbstractKnnCollector
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TaskExecutor
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TopKnnCollector
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.NamedThreadFactory
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.hnsw.HnswGraph.NodesIterator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.Executor
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tests HNSW KNN graphs */
abstract class HnswGraphTestCase<T> : LuceneTestCase() {

    protected lateinit var similarityFunction: VectorSimilarityFunction
    protected val flatVectorScorer = DefaultFlatVectorScorer()

    abstract fun getVectorEncoding(): VectorEncoding
    abstract fun knnQuery(field: String, vector: T, k: Int): Query
    abstract fun randomVector(dim: Int): T
    abstract fun vectorValues(size: Int, dimension: Int): KnnVectorValues
    abstract fun vectorValues(values: Array<FloatArray>): KnnVectorValues
    abstract fun vectorValues(reader: LeafReader, fieldName: String): KnnVectorValues
    abstract fun vectorValues(
        size: Int,
        dimension: Int,
        pregeneratedVectorValues: KnnVectorValues,
        pregeneratedOffset: Int
    ): KnnVectorValues
    abstract fun knnVectorField(name: String, vector: T, similarityFunction: VectorSimilarityFunction): Field
    abstract fun circularVectorValues(nDoc: Int): KnnVectorValues
    abstract fun getTargetVector(): T

    protected fun buildScorerSupplier(vectors: KnnVectorValues): RandomVectorScorerSupplier {
        return flatVectorScorer.getRandomVectorScorerSupplier(similarityFunction, vectors)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun buildScorer(vectors: KnnVectorValues, query: T): RandomVectorScorer {
        val vectorsCopy = vectors.copy()
        return when (getVectorEncoding()) {
            VectorEncoding.BYTE -> flatVectorScorer.getRandomVectorScorer(similarityFunction, vectorsCopy, query as ByteArray)
            VectorEncoding.FLOAT32 -> flatVectorScorer.getRandomVectorScorer(similarityFunction, vectorsCopy, query as FloatArray)
        }
    }

        open fun testRandomReadWriteAndMerge() {
        val dim = random().nextInt(10) + 1 // TODO reduced from 100 to 10 for dev speed
        val segmentSizes = intArrayOf(random().nextInt(20) + 1, random().nextInt(10) + 30, random().nextInt(10) + 20)
        val addDeletes = booleanArrayOf(random().nextBoolean(), random().nextBoolean(), random().nextBoolean())
        val isSparse = booleanArrayOf(random().nextBoolean(), random().nextBoolean(), random().nextBoolean())
        val numVectors = segmentSizes[0] + segmentSizes[1] + segmentSizes[2]
        val m = random().nextInt(4) + 2
        val beamWidth = random().nextInt(10) + 5
        val seed = random().nextLong()
        val vectors = vectorValues(numVectors, dim)
        HnswGraphBuilder.randSeed = seed

        newDirectory().use { dir ->
            val iwc =
                IndexWriterConfig()
                    .setCodec(TestUtil.alwaysKnnVectorsFormat(Lucene99HnswVectorsFormat(m, beamWidth)))
                    .setMergePolicy(newMergePolicy(random()))

            IndexWriter(dir, iwc).use { iw ->
                for (i in segmentSizes.indices) {
                    val size = segmentSizes[i]
                    for (ord in 0 until size) {
                        if (isSparse[i] && random().nextBoolean()) {
                            val d = random().nextInt(10) + 1
                            for (j in 0 until d) {
                                iw.addDocument(Document())
                            }
                        }
                        val doc = Document()
                        when (vectors.encoding) {
                            VectorEncoding.BYTE -> doc.add(knnVectorField("field", (vectors as ByteVectorValues).vectorValue(ord) as T, similarityFunction))
                            VectorEncoding.FLOAT32 -> doc.add(knnVectorField("field", (vectors as FloatVectorValues).vectorValue(ord) as T, similarityFunction))
                        }
                        doc.add(StringField("id", vectors.ordToDoc(ord).toString(), Field.Store.NO))
                        iw.addDocument(doc)
                    }
                    iw.commit()
                    if (addDeletes[i] && size > 1) {
                        var d = 0
                        while (d < size) {
                            iw.deleteDocuments(Term("id", d.toString()))
                            d += random().nextInt(5) + 1
                        }
                        iw.commit()
                    }
                }
                iw.commit()
                iw.forceMerge(1)
            }

            DirectoryReader.open(dir).use { reader ->
                for (ctx in reader.leaves()) {
                    val values = vectorValues(ctx.reader(), "field")
                    assertEquals(dim, values.dimension())
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun vectorValue(vectors: KnnVectorValues, ord: Int): T {
        return when (vectors.encoding) {
            VectorEncoding.BYTE -> (vectors as ByteVectorValues).vectorValue(ord) as T
            VectorEncoding.FLOAT32 -> (vectors as FloatVectorValues).vectorValue(ord) as T
        }
    }

        open fun testReadWrite() {
        val dim = random().nextInt(100) + 1
        val nDoc = random().nextInt(100) + 1
        val m = random().nextInt(4) + 2
        val beamWidth = random().nextInt(10) + 5
        val seed = random().nextLong()
        val vectors = vectorValues(nDoc, dim)
        val v2 = vectors.copy()
        val v3 = vectors.copy()
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, m, beamWidth, seed)
        val hnsw: OnHeapHnswGraph = builder.build(vectors.size())
        expectThrows(IllegalStateException::class) { builder.addGraphNode(0) }

        HnswGraphBuilder.randSeed = seed
        newDirectory().use { dir ->
            var nVec = 0
            var indexedDoc = 0
            val iwc = IndexWriterConfig().setCodec(TestUtil.alwaysKnnVectorsFormat(Lucene99HnswVectorsFormat(m, beamWidth)))
            IndexWriter(dir, iwc).use { iw ->
                val it2 = v2.iterator()
                while (it2.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    while (indexedDoc < it2.docID()) {
                        iw.addDocument(Document())
                        indexedDoc++
                    }
                    val doc = Document()
                    doc.add(knnVectorField("field", vectorValue(v2, it2.index()), similarityFunction))
                    doc.add(StoredField("id", it2.docID()))
                    iw.addDocument(doc)
                    nVec++
                    indexedDoc++
                }
            }

            DirectoryReader.open(dir).use { reader ->
                for (ctx in reader.leaves()) {
                    val values = vectorValues(ctx.reader(), "field")
                    assertEquals(dim, values.dimension())
                    assertEquals(nVec, values.size())
                    assertEquals(indexedDoc, ctx.reader().maxDoc())
                    assertEquals(indexedDoc, ctx.reader().numDocs())
                    assertVectorsEqual(v3, values)
                    val graphValues =
                        ((
                            ((ctx.reader() as CodecReader).vectorReader as PerFieldKnnVectorsFormat.FieldsReader)
                                .getFieldReader("field")
                                as Lucene99HnswVectorsReader
                            ).getGraph("field"))
                    assertGraphEqual(hnsw, graphValues)
                }
            }
        }
    }

        open fun testRandom() {
        val size = atLeast(100)
        val dim = atLeast(10)
        val vectors = vectorValues(size, dim)
        val topK = 5
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 10, 30, random().nextLong())
        val hnsw: OnHeapHnswGraph = builder.build(vectors.size())
        val acceptOrds = if (random().nextBoolean()) null else createRandomAcceptOrds(0, size)

        var totalMatches = 0
        for (i in 0 until 100) {
            val query = randomVector(dim)
            val actual = HnswGraphSearcher.search(buildScorer(vectors, query), 100, hnsw, acceptOrds ?: Bits.MatchAllBits(size), Int.MAX_VALUE)
            val topDocs = actual.topDocs()
            val expected = NeighborQueue(topK, false)
            for (j in 0 until size) {
                if (acceptOrds == null || acceptOrds.get(j)) {
                    if (getVectorEncoding() == VectorEncoding.BYTE) {
                        expected.add(j, similarityFunction.compare(query as ByteArray, vectorValue(vectors, j) as ByteArray))
                    } else {
                        expected.add(j, similarityFunction.compare(query as FloatArray, vectorValue(vectors, j) as FloatArray))
                    }
                    if (expected.size() > topK) {
                        expected.pop()
                    }
                }
            }
            val actualTopKDocs = IntArray(topK)
            for (j in 0 until topK) {
                actualTopKDocs[j] = topDocs.scoreDocs[j].doc
            }
            totalMatches += computeOverlap(actualTopKDocs, expected.nodes())
        }
        val overlap = totalMatches.toDouble() / (100 * topK).toDouble()
        assertTrue(overlap > 0.9, "overlap=$overlap")
    }

        open fun testAknnDiverse() {
        val nDoc = 100
        similarityFunction = VectorSimilarityFunction.DOT_PRODUCT
        val vectors = circularVectorValues(nDoc)
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 10, 100, random().nextLong())
        val hnsw: OnHeapHnswGraph = builder.build(vectors.size())

        val nn = HnswGraphSearcher.search(buildScorer(vectors, getTargetVector()), 10, hnsw, Bits.MatchAllBits(nDoc), Int.MAX_VALUE)
        val topDocs = nn.topDocs()
        assertTrue(topDocs.scoreDocs.size == 10, "Number of found results is not equal to [10].")
        var sum = 0
        for (node in topDocs.scoreDocs) {
            sum += node.doc
        }
        assertTrue(sum < 75, "sum(result docs)=$sum")

        for (i in 0 until nDoc) {
            val neighbors = hnsw.getNeighbors(0, i)
            val nnodes = neighbors.nodes()
            for (j in 0 until neighbors.size()) {
                assertTrue(nnodes[j] < nDoc)
            }
        }
    }

        open fun testSearchWithAcceptOrds() {
        val nDoc = 100
        val vectors = circularVectorValues(nDoc)
        similarityFunction = VectorSimilarityFunction.DOT_PRODUCT
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 16, 100, random().nextLong())
        val hnsw: OnHeapHnswGraph = builder.build(vectors.size())
        val acceptOrds = createRandomAcceptOrds(10, nDoc)
        val nn = HnswGraphSearcher.search(buildScorer(vectors, getTargetVector()), 10, hnsw, acceptOrds, Int.MAX_VALUE)
        val nodes = nn.topDocs()
        assertTrue(nodes.scoreDocs.size == 10, "Number of found results is not equal to [10].")
        var sum = 0
        for (node in nodes.scoreDocs!!) {
            assertTrue(acceptOrds.get(node.doc), "the results include a deleted document: $node")
            sum += node.doc
        }
        assertTrue(sum < 75, "sum(result docs)=$sum")
    }

        open fun testSearchWithSelectiveAcceptOrds() {
        val nDoc = 100
        val vectors = circularVectorValues(nDoc)
        similarityFunction = VectorSimilarityFunction.DOT_PRODUCT
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 16, 100, random().nextLong())
        val hnsw: OnHeapHnswGraph = builder.build(vectors.size())
        val acceptOrds = FixedBitSet(nDoc)
        var i = 0
        while (i < nDoc) {
            acceptOrds.set(i)
            i += random().nextInt(15, 20)
        }
        val numAccepted = acceptOrds.cardinality()
        val nn = HnswGraphSearcher.search(buildScorer(vectors, getTargetVector()), numAccepted, hnsw, acceptOrds, Int.MAX_VALUE)
        val nodes = nn.topDocs()
        assertEquals(numAccepted, nodes.scoreDocs!!.size)
        for (node in nodes.scoreDocs!!) {
            assertTrue(acceptOrds.get(node.doc), "the results include a deleted document: $node")
        }
    }

        open fun testHnswGraphBuilderInvalid() {
        val scorerSupplier = buildScorerSupplier(vectorValues(1, 1))
        expectThrows(IllegalArgumentException::class) { HnswGraphBuilder.create(scorerSupplier, 0, 10, 0L) }
        expectThrows(IllegalArgumentException::class) { HnswGraphBuilder.create(scorerSupplier, 10, 0, 0L) }
    }

        open fun testRamUsageEstimate() {
        val size = atLeast(20) // TODO reduced from 2000 to 20 for dev speed
        val dim = random().nextInt(100, 1024 + 1)
        val m = random().nextInt(4, 96 + 1)

        similarityFunction = VectorSimilarityFunction.entries[random().nextInt(VectorSimilarityFunction.entries.size)]
        val vectors = vectorValues(size, dim)
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, m, m * 2, random().nextLong())
        val hnsw: OnHeapHnswGraph = builder.build(vectors.size())
        val estimated = RamUsageEstimator.sizeOfObject(hnsw)
        val actual = estimated // TODO use ramUsed equivalent when available
        assertEquals(actual.toDouble(), estimated.toDouble(), actual * 0.3)
    }

        open fun testSortedAndUnsortedIndicesReturnSameResults() {
        val dim = random().nextInt(10) + 3
        val nDoc = random().nextInt(200) + 100
        val vectors = vectorValues(nDoc, dim)

        val m = random().nextInt(10) + 5
        val beamWidth = random().nextInt(10) + 10
        val localSimilarityFunction = VectorSimilarityFunction.entries[random().nextInt(VectorSimilarityFunction.entries.size)]
        val seed = random().nextLong()
        HnswGraphBuilder.randSeed = seed
        val iwc = IndexWriterConfig().setCodec(TestUtil.alwaysKnnVectorsFormat(Lucene99HnswVectorsFormat(m, beamWidth)))
        val iwc2 = IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(Lucene99HnswVectorsFormat(m, beamWidth)))
            .setIndexSort(Sort(SortField("sortkey", SortField.Type.LONG)))

        newDirectory().use { dir ->
            newDirectory().use { dir2 ->
                var indexedDoc = 0
                IndexWriter(dir, iwc).use { iw ->
                    IndexWriter(dir2, iwc2).use { iw2 ->
                        for (ord in 0 until vectors.size()) {
                            while (indexedDoc < vectors.ordToDoc(ord)) {
                                iw.addDocument(Document())
                                indexedDoc++
                            }
                            val doc = Document()
                            doc.add(knnVectorField("vector", vectorValue(vectors, ord), localSimilarityFunction))
                            doc.add(StoredField("id", vectors.ordToDoc(ord)))
                            doc.add(NumericDocValuesField("sortkey", random().nextLong()))
                            iw.addDocument(doc)
                            iw2.addDocument(doc)
                            indexedDoc++
                        }
                    }
                }

                DirectoryReader.open(dir).use { reader ->
                    DirectoryReader.open(dir2).use { reader2 ->
                        val searcher = IndexSearcher(reader)
                        val searcher2 = IndexSearcher(reader2)
                        outer@ for (i in 0 until 10) {
                            val query = knnQuery("vector", randomVector(dim), 60)
                            val searchSize = 5
                            val ids1 = ArrayList<String>(searchSize)
                            val docs1 = ArrayList<Int>(searchSize)
                            val ids2 = ArrayList<String>(searchSize)
                            val docs2 = ArrayList<Int>(searchSize)

                            val topDocs = searcher.search(query, searchSize + 1)
                            var lastScore = -1f
                            val storedFields = reader.storedFields()
                            for (j in 0 until searchSize + 1) {
                                val scoreDoc = topDocs.scoreDocs[j]
                                if (scoreDoc.score == lastScore) {
                                    continue@outer
                                } else {
                                    lastScore = scoreDoc.score
                                }
                                if (j < searchSize) {
                                    val doc = storedFields.document(scoreDoc.doc, mutableSetOf("id"))
                                    ids1.add(doc.get("id")!!)
                                    docs1.add(scoreDoc.doc)
                                }
                            }

                            val topDocs2 = searcher2.search(query, searchSize)
                            val storedFields2 = reader2.storedFields()
                            for (scoreDoc in topDocs2.scoreDocs) {
                                val doc = storedFields2.document(scoreDoc.doc, mutableSetOf("id"))
                                ids2.add(doc.get("id")!!)
                                docs2.add(scoreDoc.doc)
                            }
                            assertEquals(ids1, ids2)
                            assertNotEquals(docs1, docs2)
                        }
                    }
                }
            }
        }
    }

        open fun testHnswGraphBuilderInitializationFromGraph_withOffsetZero() {
        val totalSize = atLeast(100)
        val initializerSize = random().nextInt(5, totalSize)
        val docIdOffset = 0
        val dim = atLeast(10)
        val seed = random().nextLong()

        val initializerVectors = vectorValues(initializerSize, dim)
        val initialScorerSupplier = buildScorerSupplier(initializerVectors)
        val initializerBuilder = HnswGraphBuilder.create(initialScorerSupplier, 10, 30, seed)

        val initializerGraph: OnHeapHnswGraph = initializerBuilder.build(initializerVectors.size())
        val finalVectorValues = vectorValues(totalSize, dim, initializerVectors, docIdOffset)
        val initializerOrdMap = createOffsetOrdinalMap(initializerSize, finalVectorValues, docIdOffset)

        val finalScorerSupplier = buildScorerSupplier(finalVectorValues)

        val graphAfterInit = InitializedHnswGraphBuilder.initGraph(initializerGraph, initializerOrdMap, initializerGraph.size())

        val finalBuilder = InitializedHnswGraphBuilder.fromGraph(
            finalScorerSupplier,
            30,
            seed,
            initializerGraph,
            initializerOrdMap,
            org.gnit.lucenekmp.util.BitSet.of(DocIdSetIterator.range(docIdOffset, initializerSize + docIdOffset), totalSize + 1),
            totalSize
        )

        assertGraphEqual(initializerGraph, graphAfterInit)

        val finalGraph = finalBuilder.build(finalVectorValues.size())
        assertGraphContainsGraph(finalGraph, initializerGraph, initializerOrdMap)
    }

        open fun testHnswGraphBuilderInitializationFromGraph_withNonZeroOffset() {
        val totalSize = atLeast(100)
        val initializerSize = random().nextInt(5, totalSize)
        val docIdOffset = random().nextInt(1, totalSize - initializerSize + 1)
        val dim = atLeast(10)
        val seed = random().nextLong()

        val initializerVectors = vectorValues(initializerSize, dim)
        val initialScorerSupplier = buildScorerSupplier(initializerVectors)
        val initializerBuilder = HnswGraphBuilder.create(initialScorerSupplier, 10, 30, seed)

        val initializerGraph: OnHeapHnswGraph = initializerBuilder.build(initializerVectors.size())
        val finalVectorValues = vectorValues(totalSize, dim, initializerVectors.copy(), docIdOffset)
        val initializerOrdMap = createOffsetOrdinalMap(initializerSize, finalVectorValues, docIdOffset)

        val finalScorerSupplier = buildScorerSupplier(finalVectorValues)
        val finalBuilder = InitializedHnswGraphBuilder.fromGraph(
            finalScorerSupplier,
            30,
            seed,
            initializerGraph,
            initializerOrdMap,
            org.gnit.lucenekmp.util.BitSet.of(DocIdSetIterator.range(docIdOffset, initializerSize + docIdOffset), totalSize + 1),
            totalSize
        )

        assertGraphInitializedFromGraph(finalBuilder.graph, initializerGraph, initializerOrdMap)

        val finalGraph = finalBuilder.build(finalVectorValues.size())
        assertGraphContainsGraph(finalGraph, initializerGraph, initializerOrdMap)
    }

        open fun testVisitedLimit() {
        val nDoc = 500
        similarityFunction = VectorSimilarityFunction.DOT_PRODUCT
        val vectors = circularVectorValues(nDoc)
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 16, 100, random().nextLong())
        val hnsw: OnHeapHnswGraph = builder.build(vectors.size())

        val topK = 50
        val visitedLimit = topK + random().nextInt(5)
        val nn = HnswGraphSearcher.search(
            buildScorer(vectors, getTargetVector()),
            topK,
            hnsw,
            createRandomAcceptOrds(0, nDoc),
            visitedLimit
        )
        assertTrue(nn.earlyTerminated())
        assertTrue(nn.visitedCount() <= visitedLimit)
    }

        open fun testFindAll() {
        val numVectors = 10
        val vectorValues = circularVectorValues(numVectors)
        val target = getTargetVector()
        var minScore = Float.POSITIVE_INFINITY
        for (i in 0 until numVectors) {
            val score = when (getVectorEncoding()) {
                VectorEncoding.BYTE -> similarityFunction.compare((vectorValues as ByteVectorValues).vectorValue(i), target as ByteArray)
                VectorEncoding.FLOAT32 -> similarityFunction.compare((vectorValues as FloatVectorValues).vectorValue(i), target as FloatArray)
            }
            minScore = min(minScore, score)
        }

        val scorerSupplier = buildScorerSupplier(vectorValues)
        val builder = HnswGraphBuilder.create(scorerSupplier, 16, 100, random().nextLong())
        val hnsw: OnHeapHnswGraph = builder.build(numVectors)
        val finalMinScore = Math.nextDown(minScore)

        val collector = object : AbstractKnnCollector(numVectors, Long.MAX_VALUE, KnnSearchStrategy.Hnsw.DEFAULT) {
            var collected = 0

            override fun collect(docId: Int, similarity: Float): Boolean {
                collected++
                return true
            }

            override fun numCollected(): Int = collected

            override fun minCompetitiveSimilarity(): Float = finalMinScore

            override fun topDocs(): TopDocs {
                throw UnsupportedOperationException()
            }
        }

        HnswGraphSearcher.search(
            buildScorer(vectorValues, target),
            collector,
            hnsw,
            Bits.MatchAllBits(numVectors),
            numVectors
        )
        assertEquals(numVectors, collector.numCollected())
    }

        open fun testDiversity() {
        similarityFunction = VectorSimilarityFunction.DOT_PRODUCT
        val values = arrayOf(
            unitVector2d(0.5),
            unitVector2d(0.75),
            unitVector2d(0.2),
            unitVector2d(0.9),
            unitVector2d(0.8),
            unitVector2d(0.77),
            unitVector2d(0.6)
        )
        val vectors = vectorValues(values)
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 2, 10, random().nextLong())
        builder.addGraphNode(0)
        builder.addGraphNode(1)
        builder.addGraphNode(2)
        assertLevel0Neighbors(builder.graph, 0, intArrayOf(1, 2))
        assertLevel0Neighbors(builder.graph, 1, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 2, intArrayOf(0))

        builder.addGraphNode(3)
        assertLevel0Neighbors(builder.graph, 0, intArrayOf(1, 2))
        assertLevel0Neighbors(builder.graph, 1, intArrayOf(0, 3))
        assertLevel0Neighbors(builder.graph, 2, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 3, intArrayOf(1))

        builder.addGraphNode(4)
        assertLevel0Neighbors(builder.graph, 0, intArrayOf(1, 2))
        assertLevel0Neighbors(builder.graph, 1, intArrayOf(0, 3, 4))
        assertLevel0Neighbors(builder.graph, 2, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 3, intArrayOf(1, 4))
        assertLevel0Neighbors(builder.graph, 4, intArrayOf(1, 3))

        builder.addGraphNode(5)
        assertLevel0Neighbors(builder.graph, 0, intArrayOf(1, 2))
        assertLevel0Neighbors(builder.graph, 1, intArrayOf(0, 3, 4, 5))
        assertLevel0Neighbors(builder.graph, 2, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 3, intArrayOf(1, 4))
        assertLevel0Neighbors(builder.graph, 4, intArrayOf(1, 3, 5))
        assertLevel0Neighbors(builder.graph, 5, intArrayOf(1, 4))
    }

        open fun testDiversityFallback() {
        similarityFunction = VectorSimilarityFunction.EUCLIDEAN
        val values = arrayOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(0f, 10f, 0f),
            floatArrayOf(0f, 0f, 20f),
            floatArrayOf(10f, 0f, 0f),
            floatArrayOf(0f, 4f, 0f)
        )
        val vectors = vectorValues(values)
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 1, 10, random().nextLong())
        builder.addGraphNode(0)
        builder.addGraphNode(1)
        builder.addGraphNode(2)
        assertLevel0Neighbors(builder.graph, 0, intArrayOf(1, 2))
        assertLevel0Neighbors(builder.graph, 1, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 2, intArrayOf(0))

        builder.addGraphNode(3)
        assertLevel0Neighbors(builder.graph, 0, intArrayOf(1, 3))
        assertLevel0Neighbors(builder.graph, 1, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 2, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 3, intArrayOf(0))
    }

        open fun testDiversity3d() {
        similarityFunction = VectorSimilarityFunction.EUCLIDEAN
        val values = arrayOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(0f, 10f, 0f),
            floatArrayOf(0f, 0f, 20f),
            floatArrayOf(0f, 9f, 0f)
        )
        val vectors = vectorValues(values)
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 1, 10, random().nextLong())
        builder.addGraphNode(0)
        builder.addGraphNode(1)
        builder.addGraphNode(2)
        assertLevel0Neighbors(builder.graph, 0, intArrayOf(1, 2))
        assertLevel0Neighbors(builder.graph, 1, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 2, intArrayOf(0))

        builder.addGraphNode(3)
        assertLevel0Neighbors(builder.graph, 0, intArrayOf(2, 3))
        assertLevel0Neighbors(builder.graph, 1, intArrayOf(0, 3))
        assertLevel0Neighbors(builder.graph, 2, intArrayOf(0))
        assertLevel0Neighbors(builder.graph, 3, intArrayOf(0, 1))
    }

        open fun testOnHeapHnswGraphSearch() {
        val size = atLeast(100)
        val dim = atLeast(10)
        val vectors = vectorValues(size, dim)
        val scorerSupplier = buildScorerSupplier(vectors)
        val builder = HnswGraphBuilder.create(scorerSupplier, 10, 30, random().nextLong())
        val hnsw: OnHeapHnswGraph = builder.build(vectors.size())
        val acceptOrds = if (random().nextBoolean()) null else createRandomAcceptOrds(0, size)

        val queries = mutableListOf<T>()
        val expects = mutableListOf<KnnCollector>()
        for (i in 0 until 100) {
            val query = randomVector(dim)
            queries.add(query)
            val expect = HnswGraphSearcher.search(buildScorer(vectors, query), 100, hnsw, acceptOrds ?: Bits.MatchAllBits(size), Int.MAX_VALUE)
            expects.add(expect)
        }

        val threadFactory = NamedThreadFactory("onHeapHnswSearch")
        val taskExecutor = TaskExecutor(Executor { runnable -> threadFactory.newThread(runnable) })
        val futures = mutableListOf<Callable<KnnCollector>>()
        for (query in queries) {
            futures.add(Callable {
                HnswGraphSearcher.search(buildScorer(vectors, query), 100, hnsw, acceptOrds ?: Bits.MatchAllBits(size), Int.MAX_VALUE)
            })
        }
        val actuals: MutableList<KnnCollector> = runBlocking { taskExecutor.invokeAll(futures) }

        for (i in expects.indices) {
            val expect = expects[i].topDocs()
            val actual = actuals[i].topDocs()
            val expectedDocs = IntArray(expect.scoreDocs.size)
            for (j in expect.scoreDocs.indices) {
                expectedDocs[j] = expect.scoreDocs[j].doc
            }
            val actualDocs = IntArray(actual.scoreDocs.size)
            for (j in actual.scoreDocs.indices) {
                actualDocs[j] = actual.scoreDocs[j].doc
            }
            assertContentEquals(expectedDocs, actualDocs)
        }
    }

        open fun testConcurrentMergeBuilder() {
        val size = atLeast(1000)
        val dim = atLeast(10)
        val vectors = vectorValues(size, dim)
        val scorerSupplier = buildScorerSupplier(vectors)
        val threadFactory = NamedThreadFactory("hnswMerge")
        val taskExecutor = TaskExecutor(Executor { runnable -> threadFactory.newThread(runnable) })
        HnswGraphBuilder.randSeed = random().nextLong()
        val builder = HnswConcurrentMergeBuilder(taskExecutor, 4, scorerSupplier, 30, OnHeapHnswGraph(10, size), null)
        builder.setBatchSize(100)
        builder.build(size)
        val graph = builder.completedGraph
        assertTrue(graph.entryNode() != -1)
        assertEquals(size, graph.size())
        assertEquals(size - 1, graph.maxNodeId())
        for (l in 0 until graph.numLevels()) {
            assertNotNull(graph.getNodesOnLevel(l))
        }
        expectThrows(IllegalStateException::class) { builder.build(size) }
    }

        open fun testAllNodesVisitedInSingleLevel() {
        val size = atLeast(100)
        val dim = atLeast(50)
        val topK = size - 1

        val docVectors = vectorValues(size, dim)
        val graph = HnswGraphBuilder.create(buildScorerSupplier(docVectors), 10, 30, random().nextLong()).build(size)

        val singleLevelGraph = object : DelegateHnswGraph(graph) {
            override fun numLevels(): Int {
                return 1
            }
        }

        val queryVectors = vectorValues(1, dim)
        val queryScorer = buildScorer(docVectors, vectorValue(queryVectors, 0))

        val collector: KnnCollector = TopKnnCollector(topK, Int.MAX_VALUE)
        HnswGraphSearcher.search(queryScorer, collector, singleLevelGraph, Bits.MatchAllBits(size), size)

        assertEquals(graph.size().toLong(), collector.visitedCount())
    }

    private fun assertLevel0Neighbors(graph: OnHeapHnswGraph, node: Int, expectedInput: IntArray) {
        val expected = expectedInput.copyOf().apply { sort() }
        val nn = graph.getNeighbors(0, node)
        val actual = ArrayUtil.copyOfSubArray(nn.nodes(), 0, nn.size())
        actual.sort()
        assertContentEquals(expected, actual)
    }

    private fun assertGraphContainsGraph(g: HnswGraph, initializer: HnswGraph, newOrdinals: IntArray) {
        for (i in 0 until initializer.numLevels()) {
            val finalGraphNodesOnLevel = nodesIteratorToArray(g.getNodesOnLevel(i))
            val initializerGraphNodesOnLevel = mapArrayAndSort(nodesIteratorToArray(initializer.getNodesOnLevel(i)), newOrdinals)
            val overlap = computeOverlap(finalGraphNodesOnLevel, initializerGraphNodesOnLevel)
            assertEquals(initializerGraphNodesOnLevel.size, overlap)
        }
    }

    private fun assertGraphInitializedFromGraph(g: HnswGraph, initializer: HnswGraph, newOrdinals: IntArray) {
        assertEquals(initializer.numLevels(), g.numLevels(), "the number of levels in the graphs are different!")
        assertEquals(initializer.size(), g.size(), "the number of nodes in the graphs are different!")
        for (level in 0 until g.numLevels()) {
            val nodesOnLevel = initializer.getNodesOnLevel(level)
            while (nodesOnLevel.hasNext()) {
                val node = nodesOnLevel.nextInt()
                g.seek(level, newOrdinals[node])
                initializer.seek(level, node)
                val expectedNeighbors = getNeighborNodes(initializer).map { n -> newOrdinals[n] }.toSet()
                assertEquals(expectedNeighbors, getNeighborNodes(g), "arcs differ for node $node")
            }
        }
    }

    private fun nodesIteratorToArray(nodesIterator: NodesIterator): IntArray {
        val arr = IntArray(nodesIterator.size())
        var i = 0
        while (nodesIterator.hasNext()) {
            arr[i++] = nodesIterator.nextInt()
        }
        return arr
    }

    private fun mapArrayAndSort(arr: IntArray, offset: IntArray): IntArray {
        val mapped = IntArray(arr.size)
        for (i in arr.indices) {
            mapped[i] = offset[arr[i]]
        }
        mapped.sort()
        return mapped
    }

    private fun createOffsetOrdinalMap(docIdSize: Int, totalVectorValues: KnnVectorValues, docIdOffset: Int): IntArray {
        var ordinalOffset = 0
        val it = totalVectorValues.iterator()
        while (it.nextDoc() < docIdOffset) {
            ordinalOffset++
        }
        val offsetOrdinalMap = IntArray(docIdSize)
        var curr = 0
        while (it.docID() < docIdOffset + docIdSize) {
            offsetOrdinalMap[curr] = ordinalOffset + curr
            curr++
            it.nextDoc()
        }
        return offsetOrdinalMap
    }

    protected inner class CircularFloatVectorValues(private val size: Int) : FloatVectorValues() {
        private val value = FloatArray(2)
        private var doc = -1

        override fun copy() = CircularFloatVectorValues(size)
        override fun dimension() = 2
        override fun size() = size
        fun vectorValue() = vectorValue(doc)
        fun docID() = doc
        fun nextDoc() = advance(doc + 1)
        fun advance(target: Int): Int {
            doc = if (target in 0 until size) target else DocIdSetIterator.NO_MORE_DOCS
            return doc
        }

        override fun vectorValue(ord: Int): FloatArray {
            return unitVector2d(ord.toDouble() / size, value)
        }

        override fun scorer(target: FloatArray): VectorScorer {
            throw UnsupportedOperationException()
        }
    }

    protected inner class CircularByteVectorValues(private val size: Int) : ByteVectorValues() {
        private val value = FloatArray(2)
        private val bValue = ByteArray(2)
        private var doc = -1

        override fun copy() = CircularByteVectorValues(size)
        override fun dimension() = 2
        override fun size() = size
        fun vectorValue() = vectorValue(doc)
        fun docID() = doc
        fun nextDoc() = advance(doc + 1)
        fun advance(target: Int): Int {
            doc = if (target in 0 until size) target else DocIdSetIterator.NO_MORE_DOCS
            return doc
        }

        override fun vectorValue(ord: Int): ByteArray {
            unitVector2d(ord.toDouble() / size, value)
            for (i in value.indices) {
                bValue[i] = (value[i] * 127).toInt().toByte()
            }
            return bValue
        }

        override fun scorer(target: ByteArray): VectorScorer {
            throw UnsupportedOperationException()
        }
    }

    protected fun assertVectorsEqual(u: KnnVectorValues, v: KnnVectorValues) {
        assertEquals(u.size(), v.size())
        for (ord in 0 until u.size()) {
            val uDoc = u.ordToDoc(ord)
            val vDoc = v.ordToDoc(ord)
            assertEquals(uDoc, vDoc)
            assertNotEquals(DocIdSetIterator.NO_MORE_DOCS, uDoc)
            when (getVectorEncoding()) {
                VectorEncoding.BYTE -> assertContentEquals(vectorValue(u, ord) as ByteArray, vectorValue(v, ord) as ByteArray)
                VectorEncoding.FLOAT32 -> {
                    val a = vectorValue(u, ord) as FloatArray
                    val b = vectorValue(v, ord) as FloatArray
                    assertEquals(a.size, b.size)
                    for (i in a.indices) {
                        assertEquals(a[i], b[i], 1e-4f)
                    }
                }
            }
        }
    }

    protected fun sortedNodesOnLevel(h: HnswGraph, level: Int): MutableList<Int> {
        val nodesOnLevel = h.getNodesOnLevel(level)
        val nodes = mutableListOf<Int>()
        while (nodesOnLevel.hasNext()) {
            nodes.add(nodesOnLevel.next())
        }
        nodes.sort()
        return nodes
    }

    protected fun assertGraphEqual(g: HnswGraph, h: HnswGraph) {
        assertEquals(g.numLevels(), h.numLevels())
        assertEquals(g.size(), h.size())
        for (level in 0 until g.numLevels()) {
            assertEquals(sortedNodesOnLevel(g, level), sortedNodesOnLevel(h, level))
        }
        for (level in 0 until g.numLevels()) {
            val nodesOnLevel = g.getNodesOnLevel(level)
            while (nodesOnLevel.hasNext()) {
                val node = nodesOnLevel.nextInt()
                g.seek(level, node)
                h.seek(level, node)
                assertEquals(getNeighborNodes(g), getNeighborNodes(h))
            }
        }
    }

    protected fun getNeighborNodes(g: HnswGraph): Set<Int> {
        val neighbors = mutableSetOf<Int>()
        var n = g.nextNeighbor()
        while (n != DocIdSetIterator.NO_MORE_DOCS) {
            neighbors.add(n)
            n = g.nextNeighbor()
        }
        return neighbors
    }

    protected fun createRandomAcceptOrds(startIndex: Int, length: Int): Bits {
        val bits = FixedBitSet(length)
        for (i in 0 until startIndex) bits.set(i)
        for (i in startIndex until bits.length()) {
            if (random().nextFloat() < 0.667f) {
                bits.set(i)
            }
        }
        return bits
    }

    protected fun computeOverlap(a: IntArray, b: IntArray): Int {
        a.sort()
        b.sort()
        var overlap = 0
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            if (a[i] == b[j]) {
                overlap++
                i++
                j++
            } else if (a[i] > b[j]) {
                j++
            } else {
                i++
            }
        }
        return overlap
    }

    protected fun unitVector2d(piRadians: Double, value: FloatArray = FloatArray(2)): FloatArray {
        value[0] = cos(kotlin.math.PI * piRadians).toFloat()
        value[1] = sin(kotlin.math.PI * piRadians).toFloat()
        return value
    }

    protected fun randomVector(random: Random, dim: Int): FloatArray {
        val vec = FloatArray(dim)
        for (i in 0 until dim) {
            vec[i] = random.nextFloat()
            if (random.nextBoolean()) vec[i] = -vec[i]
        }
        VectorUtil.l2normalize(vec)
        return vec
    }

    protected fun randomVector8(random: Random, dim: Int): ByteArray {
        val fvec = randomVector(random, dim)
        val bvec = ByteArray(dim)
        for (i in 0 until dim) {
            bvec[i] = (fvec[i] * 127).toInt().toByte()
        }
        return bvec
    }

    private open inner class DelegateHnswGraph(private val delegate: HnswGraph) : HnswGraph() {
        override fun seek(level: Int, target: Int) {
            delegate.seek(level, target)
        }

        override fun size(): Int {
            return delegate.size()
        }

        override fun nextNeighbor(): Int {
            return delegate.nextNeighbor()
        }

        override fun numLevels(): Int {
            return delegate.numLevels()
        }

        override fun entryNode(): Int {
            return delegate.entryNode()
        }

        override fun neighborCount(): Int {
            return delegate.neighborCount()
        }

        override fun maxConn(): Int {
            return delegate.maxConn()
        }

        override fun getNodesOnLevel(level: Int): NodesIterator {
            return delegate.getNodesOnLevel(level)
        }
    }

    companion object {
        fun createRandomFloatVectors(size: Int, dimension: Int, random: Random): Array<FloatArray> {
            val vectors = Array(size) { FloatArray(dimension) }
            for (offset in 0 until size) {
                vectors[offset] = randomVector(random, dimension)
            }
            return vectors
        }

        fun createRandomByteVectors(size: Int, dimension: Int, random: Random): Array<ByteArray> {
            val vectors = Array(size) { ByteArray(dimension) }
            for (offset in 0 until size) {
                vectors[offset] = randomVector8(random, dimension)
            }
            return vectors
        }

        fun randomVector(random: Random, dim: Int): FloatArray {
            val vec = FloatArray(dim)
            for (i in 0 until dim) {
                vec[i] = random.nextFloat()
                if (random.nextBoolean()) {
                    vec[i] = -vec[i]
                }
            }
            VectorUtil.l2normalize(vec)
            return vec
        }

        fun randomVector8(random: Random, dim: Int): ByteArray {
            val fvec = randomVector(random, dim)
            val bvec = ByteArray(dim)
            for (i in 0 until dim) {
                bvec[i] = (fvec[i] * 127).toInt().toByte()
            }
            return bvec
        }
    }
}

