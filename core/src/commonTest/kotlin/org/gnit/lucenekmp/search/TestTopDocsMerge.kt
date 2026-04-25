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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.CompositeReaderContext
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexReaderContext
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TestTopDocsMerge : LuceneTestCase() {

  private class ShardSearcher(
    private val ctx: LeafReaderContext,
    parent: IndexReaderContext
  ) : IndexSearcher(parent) {

    fun search(weight: Weight, collector: Collector) {
      searchLeaf(ctx, 0, DocIdSetIterator.NO_MORE_DOCS, weight, collector)
    }

    fun search(weight: Weight, topN: Int): TopDocs {
      val collector = TopScoreDocCollectorManager(topN, null, Int.MAX_VALUE).newCollector()
      searchLeaf(ctx, 0, DocIdSetIterator.NO_MORE_DOCS, weight, collector)
      return collector.topDocs()
    }

    override fun toString(): String {
      return "ShardSearcher($ctx)"
    }
  }

  @Test
  fun testSort_1() {
    testSort(false)
  }

  @Test
  fun testSort_2() {
    testSort(true)
  }

  @Test
  fun testInconsistentTopDocsFail() {
    val topDocs = arrayOf(
      TopDocs(
        TotalHits(1, TotalHits.Relation.EQUAL_TO),
        arrayOf(ScoreDoc(1, 1.0f, 5))
      ),
      TopDocs(
        TotalHits(1, TotalHits.Relation.EQUAL_TO),
        arrayOf(ScoreDoc(1, 1.0f, -1))
      )
    )
    if (random().nextBoolean()) {
      ArrayUtil.swap(topDocs, 0, 1)
    }
    assertFailsWith<IllegalArgumentException> {
      TopDocs.merge(0, 2, topDocs)
    }
  }

  @Test
  fun testPreAssignedShardIndex() {
    val useConstantScore = random().nextBoolean()
    val numTopDocs = 2 + random().nextInt(10)
    val topDocs = ArrayList<TopDocs>(numTopDocs)
    val shardResultMapping = HashMap<Int, TopDocs>()
    var numHitsTotal = 0
    for (i in 0 until numTopDocs) {
      val numHits = 1 + random().nextInt(10)
      numHitsTotal += numHits
      val scoreDocs = Array(numHits) { j ->
        val score = if (useConstantScore) 1.0f else random().nextFloat()
        // we set the shard index to index in the list here but shuffle the entire list below
        ScoreDoc((100 * i) + j, score, i)
      }
      topDocs.add(TopDocs(TotalHits(numHits.toLong(), TotalHits.Relation.EQUAL_TO), scoreDocs))
      shardResultMapping[i] = topDocs[i]
    }
    // shuffle the entire thing such that we don't get 1 to 1 mapping of shard index to index in the
    // array
    // -- well likely ;)
    topDocs.shuffle(random())
    val from = random().nextInt(numHitsTotal - 1)
    val size = 1 + random().nextInt(numHitsTotal - from)

    // passing false here means TopDocs.merge uses the incoming ScoreDoc.shardIndex
    // that we already set, instead of the position of that TopDocs in the array:
    val merge = TopDocs.merge(from, size, topDocs.toTypedArray())

    assertTrue(merge.scoreDocs.isNotEmpty())
    for (scoreDoc in merge.scoreDocs) {
      assertTrue(scoreDoc.shardIndex != -1)
      val shardTopDocs = shardResultMapping[scoreDoc.shardIndex]
      assertNotNull(shardTopDocs)
      var found = false
      for (shardScoreDoc in shardTopDocs.scoreDocs) {
        if (shardScoreDoc === scoreDoc) {
          found = true
          break
        }
      }
      assertTrue(found)
    }

    // now ensure merge is stable even if we use our own shard IDs
    topDocs.shuffle(random())
    val merge2 = TopDocs.merge(from, size, topDocs.toTypedArray())
    assertTrue(merge.scoreDocs.contentEquals(merge2.scoreDocs))
  }

  private fun testSort(useFrom: Boolean) {

    var reader: IndexReader? = null
    var dir: Directory? = null

    val numDocs = if (TEST_NIGHTLY) atLeast(1000) else atLeast(100)

    val tokens = arrayOf("a", "b", "c", "d", "e")

    if (VERBOSE) {
      println("TEST: make index")
    }

    run {
      dir = newDirectory()
      val w = RandomIndexWriter(random(), dir!!)
      // w.setDoRandomForceMerge(false);

      // w.w.getConfig().setMaxBufferedDocs(atLeast(100));

      val content = Array(atLeast(20)) { "" }

      for (contentIDX in content.indices) {
        val sb = StringBuilder()
        val numTokens = TestUtil.nextInt(random(), 1, 10)
        for (tokenIDX in 0 until numTokens) {
          sb.append(tokens[random().nextInt(tokens.size)]).append(' ')
        }
        content[contentIDX] = sb.toString()
      }

      for (docIDX in 0 until numDocs) {
        val doc = Document()
        doc.add(
          SortedDocValuesField(
            "string", BytesRef(TestUtil.randomRealisticUnicodeString(random()))
          )
        )
        doc.add(newTextField("text", content[random().nextInt(content.size)], Field.Store.NO))
        doc.add(FloatDocValuesField("float", random().nextFloat()))
        val intValue = when {
          random().nextInt(100) == 17 -> Int.MIN_VALUE
          random().nextInt(100) == 17 -> Int.MAX_VALUE
          else -> random().nextInt()
        }
        doc.add(NumericDocValuesField("int", intValue.toLong()))
        if (VERBOSE) {
          println("  doc=$doc")
        }
        w.addDocument(doc)
      }

      reader = w.reader
      w.close()
    }

    // NOTE: sometimes reader has just one segment, which is
    // important to test
    val searcher = newSearcher(reader!!)
    val ctx = searcher.topReaderContext

    val subSearchers: Array<ShardSearcher>
    val docStarts: IntArray

    if (ctx is LeafReaderContext) {
      subSearchers = Array(1) { ShardSearcher(ctx, ctx) }
      docStarts = IntArray(1)
      docStarts[0] = 0
    } else {
      val compCTX = ctx as CompositeReaderContext
      val size = compCTX.leaves().size
      subSearchers = Array(size) { idx ->
        val leave = compCTX.leaves()[idx]
        ShardSearcher(leave, compCTX)
      }
      docStarts = IntArray(size) { idx ->
        var docBase = 0
        for (i in 0 until idx) {
          docBase += compCTX.leaves()[i].reader().maxDoc()
        }
        docBase
      }
    }

    val sortFields = ArrayList<SortField>()
    sortFields.add(SortField("string", SortField.Type.STRING, true))
    sortFields.add(SortField("string", SortField.Type.STRING, false))
    sortFields.add(SortField("int", SortField.Type.INT, true))
    sortFields.add(SortField("int", SortField.Type.INT, false))
    sortFields.add(SortField("float", SortField.Type.FLOAT, true))
    sortFields.add(SortField("float", SortField.Type.FLOAT, false))
    sortFields.add(SortField(null, SortField.Type.SCORE, true))
    sortFields.add(SortField(null, SortField.Type.SCORE, false))
    sortFields.add(SortField(null, SortField.Type.DOC, true))
    sortFields.add(SortField(null, SortField.Type.DOC, false))

    val numIters = atLeast(300)
    for (iter in 0 until numIters) {

      // TODO: custom FieldComp...
      val query = TermQuery(Term("text", tokens[random().nextInt(tokens.size)]))

      val sort = if (random().nextInt(10) == 4) {
        // Sort by score
        null
      } else {
        val randomSortFields = Array(TestUtil.nextInt(random(), 1, 3)) { sortIDX ->
          sortFields[random().nextInt(sortFields.size)]
        }
        Sort(*randomSortFields)
      }

      val numHits = TestUtil.nextInt(random(), 1, numDocs + 5)
      // final int numHits = 5;

      if (VERBOSE) {
        println("TEST: search query=$query sort=$sort numHits=$numHits")
      }

      var from = -1
      var size = -1
      // First search on whole index:
      val topHits: TopDocs
      if (sort == null) {
        if (useFrom) {

          from = TestUtil.nextInt(random(), 0, numHits - 1)
          size = numHits - from
          val tempTopHits =
            searcher.search(query, TopScoreDocCollectorManager(numHits, Int.MAX_VALUE))
          if (from < tempTopHits.scoreDocs.size) {
            // Can't use TopDocs#topDocs(start, howMany), since it has different behaviour when
            // start >= hitCount
            // than TopDocs#merge currently has
            val newScoreDocs = Array(kotlin.math.min(size, tempTopHits.scoreDocs.size - from)) { j ->
              tempTopHits.scoreDocs[from + j]
            }
            tempTopHits.scoreDocs = newScoreDocs
            topHits = tempTopHits
          } else {
            topHits = TopDocs(tempTopHits.totalHits, emptyArray())
          }
        } else {
          topHits = searcher.search(query, numHits)
        }
      } else {
        val topFieldDocs =
          searcher.search(query, TopFieldCollectorManager(sort, numHits, Int.MAX_VALUE))
        if (useFrom) {
          from = TestUtil.nextInt(random(), 0, numHits - 1)
          size = numHits - from
          if (from < topFieldDocs.scoreDocs.size) {
            // Can't use TopDocs#topDocs(start, howMany), since it has different behaviour when
            // start >= hitCount
            // than TopDocs#merge currently has
            val newScoreDocs = Array(kotlin.math.min(size, topFieldDocs.scoreDocs.size - from)) { j ->
              topFieldDocs.scoreDocs[from + j]
            }
            topFieldDocs.scoreDocs = newScoreDocs
            topHits = topFieldDocs
          } else {
            topHits = TopDocs(topFieldDocs.totalHits, emptyArray())
          }
        } else {
          topHits = topFieldDocs
        }
      }

      if (VERBOSE) {
        if (useFrom) {
          println("from=$from size=$size")
        }
        val hitsSize = if (topHits.scoreDocs == null) "null" else topHits.scoreDocs.size
        println("  top search: ${topHits.totalHits.value} totalHits; hits=$hitsSize")
        if (topHits.scoreDocs != null) {
          for (hitIDX in topHits.scoreDocs.indices) {
            val sd = topHits.scoreDocs[hitIDX]
            println("    doc=${sd.doc} score=${sd.score}")
          }
        }
      }

      // ... then all shards:
      val w = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)

      val shardHits: Array<TopDocs>
      if (sort == null) {
        shardHits = Array(subSearchers.size) { TopDocs(TotalHits(0, TotalHits.Relation.EQUAL_TO), arrayOf()) }
      } else {
        shardHits = Array(subSearchers.size) { TopFieldDocs(TotalHits(0, TotalHits.Relation.EQUAL_TO), arrayOf(), arrayOf()) } as Array<TopDocs>
      }
      for (shardIDX in subSearchers.indices) {
        val subHits: TopDocs
        val subSearcher = subSearchers[shardIDX]
        if (sort == null) {
          subHits = subSearcher.search(w, numHits)
        } else {
          val c = TopFieldCollectorManager(sort, numHits, null, Int.MAX_VALUE).newCollector()
          subSearcher.search(w, c)
          subHits = c.topDocs(0, numHits)
        }

        for (i in subHits.scoreDocs.indices) {
          subHits.scoreDocs[i].shardIndex = shardIDX
        }

        shardHits[shardIDX] = subHits
        if (VERBOSE) {
          val hitsSize = if (subHits.scoreDocs == null) "null" else subHits.scoreDocs.size
          println("  shard=$shardIDX ${subHits.totalHits.value} totalHits hits=$hitsSize")
          if (subHits.scoreDocs != null) {
            for (sd in subHits.scoreDocs) {
              println("    doc=${sd.doc} score=${sd.score}")
            }
          }
        }
      }

      // Merge:
      val mergedHits: TopDocs
      if (useFrom) {
        mergedHits = if (sort == null) {
          TopDocs.merge(from, size, shardHits)
        } else {
          TopDocs.merge(sort, from, size, shardHits as Array<TopFieldDocs>)
        }
      } else {
        mergedHits = if (sort == null) {
          TopDocs.merge(numHits, shardHits)
        } else {
          TopDocs.merge(sort, numHits, shardHits as Array<TopFieldDocs>)
        }
      }

      if (mergedHits.scoreDocs != null) {
        // Make sure the returned shards are correct:
        for (hitIDX in mergedHits.scoreDocs.indices) {
          val sd = mergedHits.scoreDocs[hitIDX]
          val expected = ReaderUtil.subIndex(sd.doc, docStarts)
          val actual = sd.shardIndex
          assertEquals(
            expected,
            actual,
            "doc=${sd.doc} wrong shard"
          )
        }
      }

      TestUtil.assertConsistent(topHits, mergedHits)
    }
    reader!!.close()
    dir!!.close()
  }

  @Test
  fun testMergeTotalHitsRelation() {
    val topDocs1 =
      TopDocs(
        TotalHits(2L, TotalHits.Relation.EQUAL_TO),
        arrayOf(ScoreDoc(42, 2f, 0))
      )
    val topDocs2 =
      TopDocs(
        TotalHits(1L, TotalHits.Relation.EQUAL_TO),
        arrayOf(ScoreDoc(42, 2f, 1))
      )
    val topDocs3 =
      TopDocs(
        TotalHits(1L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO),
        arrayOf(ScoreDoc(42, 2f, 2))
      )
    val topDocs4 =
      TopDocs(
        TotalHits(3L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO),
        arrayOf(ScoreDoc(42, 2f, 3))
      )

    val merged1 = TopDocs.merge(1, arrayOf(topDocs1, topDocs2))
    assertEquals(TotalHits(3L, TotalHits.Relation.EQUAL_TO), merged1.totalHits)

    val merged2 = TopDocs.merge(1, arrayOf(topDocs1, topDocs3))
    assertEquals(TotalHits(3L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), merged2.totalHits)

    val merged3 = TopDocs.merge(1, arrayOf(topDocs3, topDocs4))
    assertEquals(TotalHits(4L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), merged3.totalHits)

    val merged4 = TopDocs.merge(1, arrayOf(topDocs4, topDocs2))
    assertEquals(TotalHits(4L, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), merged4.totalHits)
  }
}
