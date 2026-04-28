package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

class TestTopKnnResults : LuceneTestCase() {

    @Test
    fun testCollectAndProvideResults() {
        val results = TopKnnCollector(5, Int.Companion.MAX_VALUE)
        val nodes = intArrayOf(4, 1, 5, 7, 8, 10, 2)
        val scores = floatArrayOf(1f, 0.5f, 0.6f, 2f, 2f, 1.2f, 4f)
        for (i in nodes.indices) {
            results.collect(nodes[i], scores[i])
        }
        val topDocs: TopDocs = results.topDocs()
        val sortedNodes = IntArray(topDocs.scoreDocs.size)
        val sortedScores = FloatArray(topDocs.scoreDocs.size)
        for (i in topDocs.scoreDocs.indices) {
            sortedNodes[i] = topDocs.scoreDocs[i].doc
            sortedScores[i] = topDocs.scoreDocs[i].score
        }
        assertArrayEquals(intArrayOf(2, 7, 8, 10, 4), sortedNodes)
        assertArrayEquals(floatArrayOf(4f, 2f, 2f, 1.2f, 1f), sortedScores, 0f)
    }
}
