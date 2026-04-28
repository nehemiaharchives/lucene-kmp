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

import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

class TestVectorSimilarityCollector : LuceneTestCase() {
  @Test
  fun testResultCollection() {
    val traversalSimilarity = 0.3f
    val resultSimilarity = 0.5f

    val collector =
      VectorSimilarityCollector(traversalSimilarity, resultSimilarity, Int.MAX_VALUE.toLong())
    val nodes = intArrayOf(1, 5, 10, 4, 8, 3, 2, 6, 7, 9)
    val scores = floatArrayOf(0.1f, 0.2f, 0.3f, 0.5f, 0.2f, 0.6f, 0.9f, 0.3f, 0.7f, 0.8f)

    val minCompetitiveSimilarities = FloatArray(nodes.size)
    for (i in nodes.indices) {
      collector.collect(nodes[i], scores[i])
      minCompetitiveSimilarities[i] = collector.minCompetitiveSimilarity()
    }

    val scoreDocs = collector.topDocs().scoreDocs
    val resultNodes = IntArray(scoreDocs.size)
    val resultScores = FloatArray(scoreDocs.size)
    for (i in scoreDocs.indices) {
      resultNodes[i] = scoreDocs[i].doc
      resultScores[i] = scoreDocs[i].score
    }

    // All nodes above resultSimilarity appear in order of collection
    assertArrayEquals(intArrayOf(4, 3, 2, 7, 9), resultNodes)
    assertArrayEquals(floatArrayOf(0.5f, 0.6f, 0.9f, 0.7f, 0.8f), resultScores, 1e-3f)

    // Min competitive similarity is minimum of traversalSimilarity or best result encountered
    assertArrayEquals(
      floatArrayOf(0.1f, 0.2f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f),
      minCompetitiveSimilarities,
      1e-3f
    )
  }
}
