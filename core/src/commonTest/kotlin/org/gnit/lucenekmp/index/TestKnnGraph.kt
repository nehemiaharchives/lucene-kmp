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
package org.gnit.lucenekmp.index

import kotlinx.coroutines.Runnable
import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswScalarQuantizedVectorsFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.KnnFloatVectorQuery
import org.gnit.lucenekmp.search.SearcherFactory
import org.gnit.lucenekmp.search.SearcherManager
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomizedTest
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.hnsw.HnswGraph
import org.gnit.lucenekmp.util.hnsw.HnswGraphBuilder
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests indexing of a knn-graph
 */
class TestKnnGraph : LuceneTestCase() {

    companion object {
        private const val KNN_GRAPH_FIELD: String = "vector"

        @Suppress("NonFinalStaticField")
        var M: Int = HnswGraphBuilder.DEFAULT_MAX_CONN
    }

    private lateinit var codec: Codec
    private lateinit var float32Codec: Codec
    private lateinit var vectorEncoding: VectorEncoding
    private lateinit var similarityFunction: VectorSimilarityFunction

    @BeforeTest
    fun setup() {
        HnswGraphBuilder.randSeed = random().nextLong()
        if (random().nextBoolean()) {
            M = random().nextInt(256) + 3
        }

        val similarity = random().nextInt(VectorSimilarityFunction.entries.size - 1) + 1
        similarityFunction = VectorSimilarityFunction.entries.toTypedArray()[similarity]
        vectorEncoding = randomVectorEncoding()
        val quantized = RandomizedTest.randomBoolean()
        codec =
            TestUtil.alwaysKnnVectorsFormat(
                if (quantized) {
                    Lucene99HnswScalarQuantizedVectorsFormat(
                        M, HnswGraphBuilder.DEFAULT_BEAM_WIDTH)
                } else {
                    Lucene99HnswVectorsFormat(M, HnswGraphBuilder.DEFAULT_BEAM_WIDTH)
                })

        float32Codec =
            TestUtil.alwaysKnnVectorsFormat(
                Lucene99HnswVectorsFormat(M, HnswGraphBuilder.DEFAULT_BEAM_WIDTH))
    }

    private fun randomVectorEncoding(): VectorEncoding {
        return VectorEncoding.entries[random().nextInt(VectorEncoding.entries.size)]
    }

    @AfterTest
    fun cleanup() {
        M = HnswGraphBuilder.DEFAULT_MAX_CONN
    }

    @Test
    fun testBasic() {
        val dir = newDirectory()
        val config = IndexWriterConfig(MockAnalyzer(Random.Default, MockTokenizer.WHITESPACE, true))
        config.setCodec(codec)
        val iw = IndexWriter(dir, config)
        try {
            val numDoc = atLeast(10)
            val dimension = atLeast(3)
            val values = Array<FloatArray?>(numDoc) { null } as Array<FloatArray>
            for (i in 0 until numDoc) {
                if (random().nextBoolean()) {
                    values[i] = randomVector(dimension)
                }
                add(iw, i, values[i])
            }
            assertConsistentGraph(iw, values)
        } finally {
            iw.close()
            dir.close()
        }
    }

    @Test
    fun testSingleDocument() {
        val dir = newDirectory()
        val config = IndexWriterConfig(MockAnalyzer(Random.Default, MockTokenizer.WHITESPACE, true))
        config.setCodec(codec)
        val iw = IndexWriter(dir, config)
        try {
            val values = floatArrayOf(0f, 1f, 2f)
            if (similarityFunction == VectorSimilarityFunction.DOT_PRODUCT) {
                VectorUtil.l2normalize(values)
            }
            if (vectorEncoding == VectorEncoding.BYTE) {
                for (i in values.indices) {
                    values[i] = kotlin.math.floor(values[i] * 127)
                }
            }
            add(iw, 0, values)
            assertConsistentGraph(iw, arrayOf(values))
            iw.commit()
            assertConsistentGraph(iw, arrayOf(values))
        } finally {
            iw.close()
            dir.close()
        }
    }


    /** Verify that the graph properties are preserved when merging  */
    @Test
    @Throws(Exception::class)
    fun testMerge() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig().setCodec(codec)).use { iw ->
                val numDoc: Int = atLeast(100)
                val dimension: Int = atLeast(10)
                val values = randomVectors(numDoc, dimension)
                for (i in 0..<numDoc) {
                    if (random().nextBoolean()) {
                        values[i] = randomVector(dimension)
                    }
                    add(iw, i, values[i])
                    if (random().nextInt(10) == 3) {
                        iw.commit()
                    }
                }
                if (random().nextBoolean()) {
                    iw.forceMerge(1)
                }
                assertConsistentGraph(iw, values)
            }
        }
    }

    /** Test writing and reading of multiple vector fields *  */
    @Test
    @Throws(Exception::class)
    fun testMultipleVectorFields() {
        val numVectorFields: Int = RandomizedTest.randomIntBetween(2, 5)
        val numDoc: Int = atLeast(100)
        val dims = IntArray(numVectorFields)
        val values = arrayOfNulls<Array<FloatArray>>(numVectorFields)
        val fieldTypes: Array<FieldType> = kotlin.arrayOfNulls<FieldType>(numVectorFields) as Array<FieldType>
        for (field in 0..<numVectorFields) {
            dims[field] = atLeast(3)
            values[field] = randomVectors(numDoc, dims[field])
            fieldTypes[field] = KnnFloatVectorField.createFieldType(dims[field], similarityFunction)
        }

        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig().setCodec(codec)).use { iw ->
                for (docID in 0..<numDoc) {
                    val doc = Document()
                    for (field in 0..<numVectorFields) {
                        val vector = values[field]!![docID]
                        if (vector != null) {
                            doc.add(KnnFloatVectorField(KNN_GRAPH_FIELD + field, vector, fieldTypes[field]))
                        }
                    }
                    val idString = docID.toString()
                    doc.add(StringField("id", idString, Field.Store.YES))
                    iw.addDocument(doc)
                }
                for (field in 0..<numVectorFields) {
                    assertConsistentGraph(iw, values[field]!!, KNN_GRAPH_FIELD + field)
                }
            }
        }
    }

    private fun randomVectors(numDoc: Int, dimension: Int): Array<FloatArray> {
        val values = arrayOfNulls<FloatArray?>(numDoc) as Array<FloatArray>
        for (i in 0..<numDoc) {
            if (random().nextBoolean()) {
                values[i] = randomVector(dimension)
            }
        }
        return values
    }

    private fun randomVector(dimension: Int): FloatArray {
        val value = FloatArray(dimension)
        for (j in 0..<dimension) {
            value[j] = random().nextFloat()
        }
        VectorUtil.l2normalize(value)
        if (vectorEncoding == VectorEncoding.BYTE) {
            for (j in 0..<dimension) {
                value[j] = (value[j] * 127).toInt().toByte().toFloat()
            }
        }
        return value
    }

    /** Verify that searching does something reasonable  */
    @Test
    @Throws(Exception::class)
    fun testSearch() {
        // We can't use dot product here since the vectors are laid out on a grid, not a sphere.
        similarityFunction = VectorSimilarityFunction.EUCLIDEAN
        val config: IndexWriterConfig = newIndexWriterConfig()
        config.setCodec(float32Codec)
        newDirectory().use { dir ->
            IndexWriter(dir, config).use { iw ->
                indexData(iw)
                DirectoryReader.open(iw).use { dr ->
                    // results are ordered by score (descending) and docid (ascending);
                    // This is the insertion order:
                    // column major, origin at upper left
                    //  0 15  5 20 10
                    //  3 18  8 23 13
                    //  6 21 11  1 16
                    //  9 24 14  4 19
                    // 12  2 17  7 22

                    /* For this small graph the "search" is exhaustive, so this mostly tests the APIs, the
         * orientation of the various priority queues, the scoring function, but not so much the
         * approximate KNN search algorithm
         */
                    assertGraphSearch(intArrayOf(0, 15, 3, 18, 5), floatArrayOf(0f, 0.1f), dr)
                    // Tiebreaking by docid must be done after search.
                    // assertGraphSearch(new int[]{11, 1, 8, 14, 21}, new float[]{2, 2}, dr);
                    assertGraphSearch(intArrayOf(15, 18, 0, 3, 5), floatArrayOf(0.3f, 0.8f), dr)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun indexData(iw: IndexWriter) {
        // Add a document for every cartesian point in an NxN square so we can
        // easily know which are the nearest neighbors to every point. Insert by iterating
        // using a prime number that is not a divisor of N*N so that we will hit each point once,
        // and chosen so that points will be inserted in a deterministic
        // but somewhat distributed pattern
        val n = 5
        val stepSize = 17
        val values = arrayOfNulls<FloatArray?>(n * n) as Array<FloatArray>
        var index = 0
        for (i in values.indices) {
            // System.out.printf("%d: (%d, %d)\n", i, index % n, index / n);
            val x = index % n
            val y = index / n
            values[i] = floatArrayOf(x.toFloat(), y.toFloat())
            index = (index + stepSize) % (n * n)
            add(iw, i, values[i])
            if (i == 13) {
                // create 2 segments
                iw.commit()
            }
        }
        val forceMerge: Boolean = random().nextBoolean()
        if (forceMerge) {
            iw.forceMerge(1)
        }
        assertConsistentGraph(iw, values)
    }

    @Throws(Exception::class)
    @Test
    fun testMultiThreadedSearch() {
        similarityFunction = VectorSimilarityFunction.EUCLIDEAN
        val config: IndexWriterConfig = newIndexWriterConfig()
        config.setCodec(float32Codec)
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, config)
        indexData(iw)

        val manager = SearcherManager(iw, SearcherFactory())
        val latch = CountDownLatch(1)
        val threads: Array<Thread> = Array(RandomizedTest.randomIntBetween(2, 5))
        /*for (i in threads.indices)*/ {
            val thread =
                Thread(
                    Runnable {
                        try {
                            latch.await()
                            val searcher: IndexSearcher = manager.acquire()
                            try {
                                val query =
                                    KnnFloatVectorQuery("vector", floatArrayOf(0f, 0.1f), 5)
                                val results: TopDocs = searcher.search(query, 5)
                                val storedFields: StoredFields = searcher.storedFields()
                                for (doc in results.scoreDocs) {
                                    // map docId to insertion id
                                    doc.doc = storedFields.document(doc.doc).get("id")!!.toInt()
                                }
                                assertResults(intArrayOf(0, 15, 3, 18, 5), results)
                            } finally {
                                manager.release(searcher)
                            }
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    })
            thread.start()
            thread
        }

        latch.countDown()
        for (t in threads) {
            t.join()
        }
        IOUtils.close(manager, iw, dir)
    }

    @Throws(IOException::class)
    private fun assertGraphSearch(expected: IntArray, vector: FloatArray, reader: IndexReader) {
        val results: TopDocs = doKnnSearch(reader, vector, 5)
        val storedFields: StoredFields = reader.storedFields()
        for (doc in results.scoreDocs) {
            // map docId to insertion id
            doc.doc = storedFields.document(doc.doc).get("id")!!.toInt()
        }
        assertResults(expected, results)
    }

    @Throws(IOException::class)
    private fun doKnnSearch(reader: IndexReader, vector: FloatArray, k: Int): TopDocs {
        val results: Array<TopDocs> = arrayOfNulls<TopDocs>(reader.leaves().size) as Array<TopDocs>
        for (ctx in reader.leaves()) {
            val liveDocs: Bits? = ctx.reader().liveDocs
            results[ctx.ord] =
                ctx.reader()
                    .searchNearestVectors(KNN_GRAPH_FIELD, vector, k, liveDocs, Int.MAX_VALUE)
            if (ctx.docBase > 0) {
                for (doc in results[ctx.ord].scoreDocs) {
                    doc.doc += ctx.docBase
                }
            }
        }
        return TopDocs.merge(k, results)
    }

    private fun assertResults(expected: IntArray, results: TopDocs) {
        assertEquals(expected.size.toLong(), results.scoreDocs.size.toLong(), results.toString())
        for (i in expected.indices.reversed()) {
            assertEquals(expected[i].toLong(), results.scoreDocs[i].doc.toLong(), results.scoreDocs.contentToString())
        }
    }

    @Throws(IOException::class)
    private fun assertConsistentGraph(iw: IndexWriter, values: Array<FloatArray>) {
        assertConsistentGraph(iw, values, KNN_GRAPH_FIELD)
    }

    // For each leaf, verify that its graph nodes are 1-1 with vectors, that the vectors are the
    // expected values, and that the graph is fully connected and symmetric.
    // NOTE: when we impose max-fanout on the graph it wil no longer be symmetric, but should still
    // be fully connected. Is there any other invariant we can test Well, we can check that max
    // fanout is respected. We can test *desirable* properties of the graph like small-world
    // (the graph diameter should be tightly bounded).
    @Throws(IOException::class)
    private fun assertConsistentGraph(iw: IndexWriter, values: Array<FloatArray>, vectorField: String) {
        var numDocsWithVectors = 0
        DirectoryReader.open(iw).use { dr ->
            for (ctx in dr.leaves()) {
                val reader: LeafReader = ctx.reader()
                val perFieldReader: PerFieldKnnVectorsFormat.FieldsReader? =
                    (reader as CodecReader).vectorReader as? PerFieldKnnVectorsFormat.FieldsReader
                if (perFieldReader == null) {
                    continue
                }
                val vectorReader: Lucene99HnswVectorsReader? =
                    perFieldReader.getFieldReader(vectorField) as? Lucene99HnswVectorsReader
                if (vectorReader == null) {
                    continue
                }
                val graphValues: HnswGraph? = vectorReader.getGraph(vectorField)
                val vectorValues: FloatVectorValues? = reader.getFloatVectorValues(vectorField)
                if (vectorValues == null) {
                    assert(graphValues == null)
                    continue
                }

                // assert vector values:
                // stored vector values are the same as original
                var nextDocWithVectors = 0
                val storedFields: StoredFields = reader.storedFields()
                val iterator: KnnVectorValues.DocIndexIterator = vectorValues.iterator()
                var i = 0
                while (i < reader.maxDoc()) {
                    nextDocWithVectors = iterator.advance(i)
                    while (i < nextDocWithVectors && i < reader.maxDoc()) {
                        val id: Int = storedFields.document(i).get("id")!!.toInt()
                        assertNull(
                            values[id], "document $id, expected to have no vector, does have one"
                        )
                        ++i
                    }
                    if (nextDocWithVectors == NO_MORE_DOCS) {
                        break
                    }
                    val id: Int = storedFields.document(i).get("id")!!.toInt()
                    // documents with KnnGraphValues have the expected vectors
                    val scratch: FloatArray = vectorValues.vectorValue(iterator.index())
                    assertArrayEquals(
                        values[id],
                        scratch,
                        0f,
                        "vector did not match for doc " + i + ", id=" + id + ": " + scratch.contentToString()
                    )
                    numDocsWithVectors++
                    i++
                }
                // if IndexDisi.doc == NO_MORE_DOCS, we should not call IndexDisi.nextDoc()
                if (nextDocWithVectors != NO_MORE_DOCS) {
                    assertEquals(NO_MORE_DOCS.toLong(), iterator.nextDoc().toLong())
                } else {
                    assertEquals(NO_MORE_DOCS.toLong(), iterator.docID().toLong())
                }

                // assert graph values:
                // For each level of the graph assert that:
                // 1. There are no orphan nodes without any friends
                // 2. If orphans are found, than the level must contain only 0 or a single node
                // 3. If the number of nodes on the level doesn't exceed maxConnOnLevel, assert that the
                // graph is
                //   fully connected, i.e. any node is reachable from any other node.
                // 4. If the number of nodes on the level exceeds maxConnOnLevel, assert that maxConnOnLevel
                // is respected.
                for (level in 0..<graphValues!!.numLevels()) {
                    val maxConnOnLevel: Int = if (level == 0) M * 2 else M
                    val graphOnLevel = arrayOfNulls<IntArray>(graphValues.size()) as Array<IntArray>
                    var countOnLevel = 0
                    var foundOrphan = false
                    val nodesItr: HnswGraph.NodesIterator = graphValues.getNodesOnLevel(level)
                    while (nodesItr.hasNext()) {
                        val node: Int = nodesItr.nextInt()
                        graphValues.seek(level, node)
                        var arc: Int
                        val friends: MutableList<Int> = mutableListOf()
                        while ((graphValues.nextNeighbor().also { arc = it }) != NO_MORE_DOCS) {
                            friends.add(arc)
                        }
                        if (friends.isEmpty()) {
                            foundOrphan = true
                        } else {
                            graphOnLevel[node] = friends.toIntArray()
                        }
                        countOnLevel++
                    }
                    assertEquals(nodesItr.size().toLong(), countOnLevel.toLong())
                    assertNotEquals(countOnLevel, 0, "No nodes on level [$level]")
                    if (countOnLevel == 1) {
                        assertTrue(
                             foundOrphan, "Graph with 1 node has unexpected neighbors on level [$level]"
                        )
                    } else {
                        assertFalse(
                            foundOrphan, "Graph has orphan nodes with no friends on level [$level]"
                        )
                        if (maxConnOnLevel > countOnLevel) {
                            // assert that the graph is fully connected,
                            // i.e. any node can be reached from any other node
                            assertConnected(graphOnLevel)
                        } else {
                            // assert that max-connections was respected
                            assertMaxConn(graphOnLevel, maxConnOnLevel)
                        }
                    }
                }
            }
        }
        var expectedNumDocsWithVectors = 0
        for (value in values) {
            if (value != null) {
                ++expectedNumDocsWithVectors
            }
        }
        assertEquals(expectedNumDocsWithVectors.toLong(), numDocsWithVectors.toLong())
    }

    fun assertMaxConn(graph: Array<IntArray>, maxConn: Int) {
        for (ints in graph) {
            if (ints != null) {
                assert(ints.size <= maxConn)
                for (k in ints) {
                    assertNotNull(graph[k])
                }
            }
        }
    }

    /** Assert that every node is reachable from some other node  */
    private fun assertConnected(graph: Array<IntArray>) {
        val nodes: MutableList<Int> = mutableListOf()
        val visited: MutableSet<Int> = mutableSetOf()
        val queue: MutableList<Int> = mutableListOf()
        for (i in graph.indices) {
            if (graph[i] != null) {
                nodes.add(i)
            }
        }

        // start from any node
        val startIdx: Int = random().nextInt(nodes.size)
        queue.add(nodes[startIdx])
        while (queue.isEmpty() == false) {
            val i: Int = queue.removeAt(0)
            assertNotNull( graph[i], "expected neighbors of $i")
            visited.add(i)
            for (j in graph[i]) {
                if (visited.contains(j) == false) {
                    queue.add(j)
                }
            }
        }
        // assert that every node is reachable from some other node as it was visited
        for (node in nodes) {
            assertTrue(
                "Attempted to walk entire graph but never visited node [$node]",
                visited.contains(node)
            )
        }
    }

    @Throws(IOException::class)
    private fun add(iw: IndexWriter, id: Int, vector: FloatArray) {
        add(iw, id, vector, similarityFunction)
    }

    @Throws(IOException::class)
    private fun add(
        iw: IndexWriter, id: Int, vector: FloatArray, similarityFunction: VectorSimilarityFunction
    ) {
        val doc = Document()
        if (vector != null) {
            val fieldType: FieldType = KnnFloatVectorField.createFieldType(vector.size, similarityFunction)
            doc.add(KnnFloatVectorField(KNN_GRAPH_FIELD, vector, fieldType))
        }
        val idString = id.toString()
        doc.add(StringField("id", idString, Field.Store.YES))
        doc.add(SortedDocValuesField("id", BytesRef(idString)))
        iw.updateDocument(Term("id", idString), doc)
    }
}
