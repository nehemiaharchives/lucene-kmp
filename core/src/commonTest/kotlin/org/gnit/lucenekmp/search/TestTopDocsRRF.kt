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

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TestTopDocsRRF : LuceneTestCase() {

  @Test
  fun testBasics() {
    val td1 = TopDocs(
      TotalHits(100L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO),
      arrayOf(ScoreDoc(42, 10f), ScoreDoc(10, 5f), ScoreDoc(20, 3f))
    )
    val td2 = TopDocs(
      TotalHits(80L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO),
      arrayOf(ScoreDoc(10, 10f), ScoreDoc(20, 5f))
    )

    val rrfTd = TopDocs.rrf(3, 20, arrayOf(td1, td2))
    assertEquals(TotalHits(100L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), rrfTd.totalHits)

    val rrfScoreDocs = rrfTd.scoreDocs
    assertEquals(3, rrfScoreDocs.size)

    assertEquals(10, rrfScoreDocs[0].doc)
    assertEquals(-1, rrfScoreDocs[0].shardIndex)
    assertEquals((1.0 / (20 + 2) + 1.0 / (20 + 1)).toFloat(), rrfScoreDocs[0].score, 0f)

    assertEquals(20, rrfScoreDocs[1].doc)
    assertEquals(-1, rrfScoreDocs[1].shardIndex)
    assertEquals((1.0 / (20 + 3) + 1.0 / (20 + 2)).toFloat(), rrfScoreDocs[1].score, 0f)

    assertEquals(42, rrfScoreDocs[2].doc)
    assertEquals(-1, rrfScoreDocs[2].shardIndex)
    assertEquals((1.0 / (20 + 1)).toFloat(), rrfScoreDocs[2].score, 0f)
  }

  @Test
  fun testShardIndex() {
    val td1 = TopDocs(
      TotalHits(100L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO),
      arrayOf(
        ScoreDoc(42, 10f, 0), ScoreDoc(10, 5f, 1), ScoreDoc(20, 3f, 0)
      )
    )
    val td2 = TopDocs(
      TotalHits(80L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO),
      arrayOf(ScoreDoc(10, 10f, 1), ScoreDoc(20, 5f, 1))
    )

    val rrfTd = TopDocs.rrf(3, 20, arrayOf(td1, td2))
    assertEquals(TotalHits(100L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), rrfTd.totalHits)

    val rrfScoreDocs = rrfTd.scoreDocs
    assertEquals(3, rrfScoreDocs.size)

    assertEquals(10, rrfScoreDocs[0].doc)
    assertEquals(1, rrfScoreDocs[0].shardIndex)
    assertEquals((1.0 / (20 + 2) + 1.0 / (20 + 1)).toFloat(), rrfScoreDocs[0].score, 0f)

    assertEquals(42, rrfScoreDocs[1].doc)
    assertEquals(0, rrfScoreDocs[1].shardIndex)
    assertEquals((1.0 / (20 + 1)).toFloat(), rrfScoreDocs[1].score, 0f)

    assertEquals(20, rrfScoreDocs[2].doc)
    assertEquals(1, rrfScoreDocs[2].shardIndex)
    assertEquals((1.0 / (20 + 2)).toFloat(), rrfScoreDocs[2].score, 0f)
  }

  @Test
  fun testInconsistentShardIndex() {
    val td1 = TopDocs(
      TotalHits(100L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO),
      arrayOf(
        ScoreDoc(42, 10f, 0), ScoreDoc(10, 5f, 1), ScoreDoc(20, 3f, 0)
      )
    )
    val td2 = TopDocs(
      TotalHits(80L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO),
      arrayOf(ScoreDoc(10, 10f, -1), ScoreDoc(20, 5f, -1))
    )

    val e = assertFailsWith<IllegalArgumentException> {
      TopDocs.rrf(3, 20, arrayOf(td1, td2))
    }
    assertTrue(e.message!!.contains("shardIndex"))
  }

  @Test
  fun testInvalidTopN() {
    val td1 = TopDocs(
      TotalHits(100L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), arrayOf()
    )
    val td2 = TopDocs(
      TotalHits(80L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), arrayOf()
    )

    val e = assertFailsWith<IllegalArgumentException> {
      TopDocs.rrf(0, 20, arrayOf(td1, td2))
    }
    assertTrue(e.message!!.contains("topN"))
  }

  @Test
  fun testInvalidK() {
    val td1 = TopDocs(
      TotalHits(100L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), arrayOf()
    )
    val td2 = TopDocs(
      TotalHits(80L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), arrayOf()
    )

    val e = assertFailsWith<IllegalArgumentException> {
      TopDocs.rrf(3, 0, arrayOf(td1, td2))
    }
    assertTrue(e.message!!.contains("k"))
  }
}
