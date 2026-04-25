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
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.search.SortField.Companion.FIELD_SCORE
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestTopFieldCollector : LuceneTestCase() {
  private lateinit var is_: IndexSearcher
  private lateinit var ir: IndexReader
  private lateinit var dir: Directory

  @BeforeTest
  fun setUp() {
    dir = newDirectory()
    val iw = RandomIndexWriter(random(), dir)
    val numDocs = atLeast(100)
    repeat(numDocs) {
      val doc = Document()
      iw.addDocument(doc)
    }
    ir = iw.reader
    iw.close()
    // cannot use threads with this IndexSearcher since some tests rely on no threads
    is_ = newSearcher(ir, true, true, false)
  }

  @AfterTest
  fun tearDown() {
    ir.close()
    dir.close()
  }

  companion object {
    private fun doSearchWithThreshold(
        numResults: Int, thresHold: Int, q: Query, sort: Sort, indexReader: IndexReader
    ): TopDocs {
      val searcher = newSearcher(indexReader)
      val manager = TopFieldCollectorManager(sort, numResults, null, thresHold)
      return searcher.search(q, manager)
    }

    private fun doConcurrentSearchWithThreshold(
        numResults: Int, threshold: Int, q: Query, sort: Sort, indexReader: IndexReader
    ): TopDocs {
      val searcher = newSearcher(indexReader, true, true, true)
      val collectorManager = TopFieldCollectorManager(sort, numResults, null, threshold)
      return searcher.search(q, collectorManager)
    }
  }

  @Test
  fun testSortWithoutFillFields() {
    // There was previously a bug in TopFieldCollector when fillFields was set
    // to false - the same doc and score was set in ScoreDoc[] array. This test
    // asserts that if fillFields is false, the documents are set properly. It
    // does not use Searcher's default search methods (with Sort) since all set
    // fillFields to true.
    val sorts = arrayOf(Sort(SortField.FIELD_DOC), Sort())
    for (sort in sorts) {
      val q: Query = MatchAllDocsQuery()
      val collectorManager = TopFieldCollectorManager(sort, 10, Int.MAX_VALUE)

      val sd = is_.search(q, collectorManager).scoreDocs
      for (j in 1 until sd.size) {
        assertTrue(sd[j].doc != sd[j - 1].doc)
      }
    }
  }

  @Test
  fun testSort() {
    // Two Sort criteria to instantiate the multi/single comparators.
    val sorts = arrayOf(Sort(SortField.FIELD_DOC), Sort())
    for (sort in sorts) {
      val q: Query = MatchAllDocsQuery()
      val tdc = TopFieldCollectorManager(sort, 10, null, Int.MAX_VALUE)
      val td = is_.search(q, tdc)
      val sd = td.scoreDocs
      for (j in sd.indices) {
        assertTrue(sd[j].score.isNaN())
      }
    }
  }

  @Test
  fun testSharedHitcountCollector() {
    val concurrentSearcher = newSearcher(ir, true, true, true)
    val singleThreadedSearcher = newSearcher(ir, true, true, false)

    // Two Sort criteria to instantiate the multi/single comparators.
    val sorts = arrayOf(Sort(SortField.FIELD_DOC), Sort())
    for (sort in sorts) {
      val q: Query = MatchAllDocsQuery()
      val tdc = TopFieldCollectorManager(sort, 10, Int.MAX_VALUE)
      val td = singleThreadedSearcher.search(q, tdc)

      val tsdc = TopFieldCollectorManager(sort, 10, Int.MAX_VALUE)
      val td2 = concurrentSearcher.search(q, tsdc)

      val sd = td.scoreDocs
      for (j in sd.indices) {
        assertTrue(sd[j].score.isNaN())
      }

      CheckHits.checkEqual(q, td.scoreDocs, td2.scoreDocs)
    }
  }

  @Test
  fun testSortWithoutTotalHitTracking() {
    val sort = Sort(SortField.FIELD_DOC)
    for (i in 0 until 2) {
      val q: Query = MatchAllDocsQuery()
      // check that setting trackTotalHits to false does not throw an NPE because
      // the index is not sorted
      val manager: TopFieldCollectorManager
      if (i % 2 == 0) {
        manager = TopFieldCollectorManager(sort, 10, 1)
      } else {
        val fieldDoc = FieldDoc(1, Float.NaN, arrayOf(1))
        manager = TopFieldCollectorManager(sort, 10, fieldDoc, 1)
      }

      val td = is_.search(q, manager)
      val sd = td.scoreDocs
      for (j in sd.indices) {
        assertTrue(sd[j].score.isNaN())
      }
    }
  }

  @Test
  fun testTotalHits() {
    val dir = newDirectory()
    val sort = Sort(SortField("foo", SortField.Type.LONG))
    val w =
        IndexWriter(
            dir,
            newIndexWriterConfig()
                .setMergePolicy(NoMergePolicy.INSTANCE)
                .setIndexSort(sort)
                .setMaxBufferedDocs(7)
                .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble()))
    val doc = Document()
    doc.add(NumericDocValuesField("foo", 3))
    listOf(doc, doc, doc, doc).forEach { w.addDocument(it) }
    w.flush()
    listOf(doc, doc, doc, doc, doc, doc).forEach { w.addDocument(it) }
    w.flush()
    val reader = DirectoryReader.open(w)
    assertEquals(2, reader.leaves().size)
    w.close()

    for (totalHitsThreshold in 0 until 20) {
      for (after in arrayOf(null, FieldDoc(4, Float.NaN, arrayOf(2L)))) {
        val collector =
            TopFieldCollectorManager(sort, 2, after, totalHitsThreshold).newCollector()
        val scorer = Score()

        var leafCollector = collector.getLeafCollector(reader.leaves()[0])
        leafCollector.scorer = scorer

        scorer.score = 3f
        leafCollector.collect(0)

        scorer.score = 3f
        leafCollector.collect(1)

        leafCollector = collector.getLeafCollector(reader.leaves()[1])
        leafCollector.scorer = scorer

        scorer.score = 3f
        if (totalHitsThreshold < 3) {
          assertFailsWith<CollectionTerminatedException> { leafCollector.collect(1) }
          val topDocs = collector.topDocs()
          assertEquals(
              TotalHits(3, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), topDocs.totalHits)
          continue
        } else {
          leafCollector.collect(1)
        }

        scorer.score = 4f
        if (totalHitsThreshold == 3) {
          assertFailsWith<CollectionTerminatedException> { leafCollector.collect(1) }
          val topDocs = collector.topDocs()
          assertEquals(
              TotalHits(4, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), topDocs.totalHits)
          continue
        } else {
          leafCollector.collect(1)
        }

        val topDocs = collector.topDocs()
        assertEquals(TotalHits(4, TotalHits.Relation.EQUAL_TO), topDocs.totalHits)
      }
    }

    reader.close()
    dir.close()
  }

  private class Score : Scorable() {
    var score: Float = 0f
    private var _minCompetitiveScore: Float? = null
    
    override var minCompetitiveScore: Float
      get() = _minCompetitiveScore ?: 0f
      set(value) {
        _minCompetitiveScore = value
      }

    // Allow test to check if value was set (null means not set)
    val minCompetitiveScoreOrNull: Float?
      get() = _minCompetitiveScore

    override fun score(): Float {
      return score
    }
  }

  @Test
  fun testSetMinCompetitiveScore() {
    val dir = newDirectory()
    val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
    val doc = Document()
    w.addDocuments(listOf(doc, doc, doc, doc))
    w.flush()
    w.addDocuments(listOf(doc, doc))
    w.flush()
    val reader = DirectoryReader.open(w)
    assertEquals(2, reader.leaves().size)
    w.close()

    val sort = Sort(FIELD_SCORE, SortField("foo", SortField.Type.LONG))
    val collector = TopFieldCollectorManager(sort, 2, 2).newCollector()
    val scorer = Score()

    var leafCollector = collector.getLeafCollector(reader.leaves()[0])
    leafCollector.scorer = scorer
    assertNull(scorer.minCompetitiveScoreOrNull)

    scorer.score = 1f
    leafCollector.collect(0)
    assertNull(scorer.minCompetitiveScoreOrNull)

    scorer.score = 2f
    leafCollector.collect(1)
    assertNull(scorer.minCompetitiveScoreOrNull)

    scorer.score = 3f
    leafCollector.collect(2)
    assertEquals(2f, scorer.minCompetitiveScore, 0f)

    scorer.score = 0.5f
    // Make sure we do not call setMinCompetitiveScore for non-competitive hits
    scorer.minCompetitiveScore = Float.NaN
    leafCollector.collect(3)
    assertTrue(scorer.minCompetitiveScore.isNaN())

    scorer.score = 4f
    leafCollector.collect(4)
    assertEquals(3f, scorer.minCompetitiveScore, 0f)

    // Make sure the min score is set on scorers on new segments
    var scorer2 = Score()
    leafCollector = collector.getLeafCollector(reader.leaves()[1])
    leafCollector.scorer = scorer2
    assertEquals(3f, scorer2.minCompetitiveScore, 0f)

    scorer2.score = 1f
    leafCollector.collect(0)
    assertEquals(3f, scorer2.minCompetitiveScore, 0f)

    scorer2.score = 4f
    leafCollector.collect(1)
    assertEquals(4f, scorer2.minCompetitiveScore, 0f)

    reader.close()
    dir.close()
  }

  @Test
  fun testTotalHitsWithScore() {
    val dir = newDirectory()
    val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
    val doc = Document()
    w.addDocuments(listOf(doc, doc, doc, doc))
    w.flush()
    w.addDocuments(listOf(doc, doc, doc, doc, doc, doc))
    w.flush()
    val reader = DirectoryReader.open(w)
    assertEquals(2, reader.leaves().size)
    w.close()

    for (totalHitsThreshold in 0 until 20) {
      val sort = Sort(FIELD_SCORE, SortField("foo", SortField.Type.LONG))
      val collector = TopFieldCollectorManager(sort, 2, totalHitsThreshold).newCollector()
      val scorer = Score()

      var leafCollector = collector.getLeafCollector(reader.leaves()[0])
      leafCollector.scorer = scorer

      scorer.score = 3f
      leafCollector.collect(0)

      scorer.score = 3f
      leafCollector.collect(1)

      leafCollector = collector.getLeafCollector(reader.leaves()[1])
      leafCollector.scorer = scorer

      scorer.score = 3f
      leafCollector.collect(1)

      scorer.score = 4f
      leafCollector.collect(1)

      val topDocs = collector.topDocs()
      assertEquals(totalHitsThreshold < 4, scorer.minCompetitiveScoreOrNull != null)
      assertEquals(
          TotalHits(
              4,
              if (totalHitsThreshold < 4)
                  TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
              else
                  TotalHits.Relation.EQUAL_TO),
          topDocs.totalHits)
    }

    reader.close()
    dir.close()
  }

  @Test
  fun testSortNoResults() {
    // Two Sort criteria to instantiate the multi/single comparators.
    val sorts = arrayOf(Sort(SortField.FIELD_DOC), Sort())
    for (sort in sorts) {
      val tdc = TopFieldCollectorManager(sort, 10, null, Int.MAX_VALUE).newCollector()
      val td = tdc.topDocs()
      val count = td.totalHits.value
      assertTrue(count == 0L)
    }
  }

  @Test
  fun testComputeScoresOnlyOnce() {
    val dir = newDirectory()
    val w = RandomIndexWriter(random(), dir)
    val doc = Document()
    val text = StringField("text", "foo", Field.Store.NO)
    doc.add(text)
    val relevance = NumericDocValuesField("relevance", 1)
    doc.add(relevance)
    w.addDocument(doc)
    text.setStringValue("bar")
    w.addDocument(doc)
    text.setStringValue("baz")
    w.addDocument(doc)
    val reader = w.reader
    val foo = TermQuery(Term("text", "foo"))
    val bar = TermQuery(Term("text", "bar"))
    val fooQuery = BoostQuery(foo, 2f)
    val baz = TermQuery(Term("text", "baz"))
    val bazQuery = BoostQuery(baz, 3f)
    val query =
        BooleanQuery.Builder()
            .add(fooQuery, BooleanClause.Occur.SHOULD)
            .add(bar, BooleanClause.Occur.SHOULD)
            .add(bazQuery, BooleanClause.Occur.SHOULD)
            .build()
    val searcher = newSearcher(reader)
    for (sort in arrayOf(Sort(FIELD_SCORE), Sort(SortField("f", SortField.Type.SCORE)))) {
      searcher.search(
          query,
          object : CollectorManager<Collector, Unit?> {
            val topFieldCollectorManager =
                TopFieldCollectorManager(sort, TestUtil.nextInt(random(), 1, 2), Int.MAX_VALUE)

            override fun newCollector(): Collector {
              val topCollector = topFieldCollectorManager.newCollector()
              return object : Collector {
                override var weight: Weight? = null

                override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
                  val leafCollectorIn = topCollector.getLeafCollector(context)
                  return object : FilterLeafCollector(leafCollectorIn) {
                    var currentDoc = -1

                    override fun collect(doc: Int) {
                      currentDoc = doc
                      super.collect(doc)
                    }

                    override var scorer: Scorable?
                      get() = super.scorer
                      set(scorer) {
                        var s: Scorable? = if (scorer != null) {
                          object : FilterScorable(scorer) {

                            private var lastComputedDoc = -1

                            override fun score(): Float {
                              if (lastComputedDoc == currentDoc) {
                                throw AssertionError("Score computed twice on $currentDoc")
                              }
                              lastComputedDoc = currentDoc
                              return scorer!!.score()
                            }
                          }
                        } else null
                        super.scorer = s
                      }
                  }
                }

                override fun scoreMode(): ScoreMode {
                  return ScoreMode.COMPLETE
                }
              }
            }

            override fun reduce(collectors: MutableCollection<Collector>): Unit? {
              return null
            }
          })
    }
    reader.close()
    w.close()
    dir.close()
  }

  @Test
  fun testPopulateScores() {
    val dir = newDirectory()
    val w = RandomIndexWriter(random(), dir)
    val doc = Document()
    val field = TextField("f", "foo bar", Field.Store.NO)
    doc.add(field)
    val sortField = NumericDocValuesField("sort", 0)
    doc.add(sortField)
    w.addDocument(doc)

    field.setStringValue("")
    sortField.setLongValue(3)
    w.addDocument(doc)

    field.setStringValue("foo foo bar")
    sortField.setLongValue(2)
    w.addDocument(doc)

    w.flush()

    field.setStringValue("foo")
    sortField.setLongValue(2)
    w.addDocument(doc)

    field.setStringValue("bar bar bar")
    sortField.setLongValue(0)
    w.addDocument(doc)

    val reader = w.reader
    w.close()
    val searcher = newSearcher(reader)

    for (queryText in arrayOf("foo", "bar")) {
      val query: Query = TermQuery(Term("f", queryText))
      for (reverse in arrayOf(false, true)) {
        val sortedByDoc = searcher.search(query, 10).scoreDocs
        sortedByDoc.sortWith(compareBy { it.doc })

        val sort = Sort(SortField("sort", SortField.Type.LONG, reverse))
        val sortedByField = searcher.search(query, 10, sort).scoreDocs
        val sortedByFieldClone = sortedByField.copyOf()
        TopFieldCollector.populateScores(sortedByFieldClone, searcher, query)
        for (i in sortedByFieldClone.indices) {
          assertEquals(sortedByFieldClone[i].doc, sortedByField[i].doc)
          assertSame(
              (sortedByFieldClone[i] as FieldDoc).fields, (sortedByField[i] as FieldDoc).fields)
          assertEquals(
              sortedByFieldClone[i].score,
              sortedByDoc[sortedByDoc.indexOfFirst { it.doc == sortedByFieldClone[i].doc }].score,
              0f)
        }
      }
    }

    reader.close()
    dir.close()
  }

  @Test
  fun testConcurrentMinScore() {
    val dir = newDirectory()
    val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
    val doc = Document()
    w.addDocuments(listOf(doc, doc, doc, doc, doc))
    w.flush()
    w.addDocuments(listOf(doc, doc, doc, doc, doc, doc))
    w.flush()
    w.addDocuments(listOf(doc, doc))
    w.flush()
    val reader = DirectoryReader.open(w)
    assertEquals(3, reader.leaves().size)
    w.close()

    val sort = Sort(SortField.FIELD_SCORE, SortField.FIELD_DOC)
    val manager: CollectorManager<TopFieldCollector, TopFieldDocs> =
        TopFieldCollectorManager(sort, 2, 0)
    val collector = manager.newCollector()
    val collector2 = manager.newCollector()
    assertTrue(collector.minScoreAcc === collector2.minScoreAcc)
    val minValueChecker = collector.minScoreAcc
    // force the check of the global minimum score on every round
    minValueChecker!!.modInterval = 0

    val scorer = Score()
    val scorer2 = Score()

    var leafCollector = collector.getLeafCollector(reader.leaves()[0])
    leafCollector.scorer = scorer
    var leafCollector2 = collector2.getLeafCollector(reader.leaves()[1])
    leafCollector2.scorer = scorer2

    scorer.score = 3f
    leafCollector.collect(0)
    assertEquals<Long>(Long.MIN_VALUE, minValueChecker.raw!!)
    assertNull(scorer.minCompetitiveScoreOrNull)

    scorer2.score = 6f
    leafCollector2.collect(0)
    assertEquals<Long>(Long.MIN_VALUE, minValueChecker.raw!!)
    assertNull(scorer2.minCompetitiveScoreOrNull)

    scorer.score = 2f
    leafCollector.collect(1)
    assertEquals<Long>(Long.MIN_VALUE, minValueChecker.raw!!)
    assertNull(scorer.minCompetitiveScoreOrNull)

    scorer2.score = 9f
    leafCollector2.collect(1)
    assertEquals<Long>(Long.MIN_VALUE, minValueChecker.raw!!)
    assertNull(scorer2.minCompetitiveScoreOrNull)

    scorer2.score = 7f
    leafCollector2.collect(2)
    assertEquals(7f, MaxScoreAccumulator.toScore(minValueChecker.raw!!), 0f)
    assertNull(scorer.minCompetitiveScoreOrNull)
    assertEquals(7f, scorer2.minCompetitiveScore, 0f)

    scorer2.score = 1f
    leafCollector2.collect(3)
    assertEquals(7f, MaxScoreAccumulator.toScore(minValueChecker.raw!!), 0f)
    assertNull(scorer.minCompetitiveScoreOrNull)
    assertEquals(7f, scorer2.minCompetitiveScore, 0f)

    scorer.score = 10f
    leafCollector.collect(2)
    assertEquals(7f, MaxScoreAccumulator.toScore(minValueChecker.raw!!), 0f)
    assertEquals(7f, scorer.minCompetitiveScore, 0f)
    assertEquals(7f, scorer2.minCompetitiveScore, 0f)

    scorer.score = 11f
    leafCollector.collect(3)
    assertEquals(10f, MaxScoreAccumulator.toScore(minValueChecker.raw!!), 0f)
    assertEquals(10f, scorer.minCompetitiveScore, 0f)
    assertEquals(7f, scorer2.minCompetitiveScore, 0f)

    val collector3 = manager.newCollector()
    var leafCollector3 = collector3.getLeafCollector(reader.leaves()[2])
    val scorer3 = Score()
    leafCollector3.scorer = scorer3
    assertEquals(10f, scorer3.minCompetitiveScore, 0f)

    scorer3.score = 1f
    leafCollector3.collect(0)
    assertEquals(10f, MaxScoreAccumulator.toScore(minValueChecker.raw!!), 0f)
    assertEquals(10f, scorer3.minCompetitiveScore, 0f)

    scorer.score = 11f
    leafCollector.collect(4)
    assertEquals(11f, MaxScoreAccumulator.toScore(minValueChecker.raw!!), 0f)
    assertEquals(11f, scorer.minCompetitiveScore, 0f)
    assertEquals(7f, scorer2.minCompetitiveScore, 0f)
    assertEquals(10f, scorer3.minCompetitiveScore, 0f)

    scorer3.score = 2f
    leafCollector3.collect(1)
    assertEquals(11f, MaxScoreAccumulator.toScore(minValueChecker.raw!!), 0f)
    assertEquals(11f, scorer.minCompetitiveScore, 0f)
    assertEquals(7f, scorer2.minCompetitiveScore, 0f)
    assertEquals(11f, scorer3.minCompetitiveScore, 0f)

    val topDocs = manager.reduce(mutableListOf(collector, collector2, collector3))
    val count1 = topDocs!!.totalHits.value
    assertTrue(count1 == 11L)
    assertEquals(TotalHits(11, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), topDocs.totalHits)
    assertEquals(TotalHits(11, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), topDocs.totalHits)

    leafCollector.scorer = scorer
    leafCollector2.scorer = scorer2
    leafCollector3.scorer = scorer3

    reader.close()
    dir.close()
  }

  @Test
  fun testRandomMinCompetitiveScore() {
    val dir = newDirectory()
    val w = RandomIndexWriter(random(), dir, newIndexWriterConfig())
    val numDocs = atLeast(1000)
    repeat(numDocs) {
      val numAs = 1 + random().nextInt(5)
      val numBs = if (random().nextFloat() < 0.5f) 0 else 1 + random().nextInt(5)
      val numCs = if (random().nextFloat() < 0.1f) 0 else 1 + random().nextInt(5)
      val doc = Document()
      repeat(numAs) {
        doc.add(StringField("f", "A", Field.Store.NO))
      }
      repeat(numBs) {
        doc.add(StringField("f", "B", Field.Store.NO))
      }
      repeat(numCs) {
        doc.add(StringField("f", "C", Field.Store.NO))
      }
      w.addDocument(doc)
    }
    val indexReader = w.reader
    w.close()
    val queries: Array<Query> =
        arrayOf(
            TermQuery(Term("f", "A")),
            TermQuery(Term("f", "B")),
            TermQuery(Term("f", "C")),
            BooleanQuery.Builder()
                .add(TermQuery(Term("f", "A")), BooleanClause.Occur.MUST)
                .add(TermQuery(Term("f", "B")), BooleanClause.Occur.SHOULD)
                .build())
    for (query in queries) {
      val sort = Sort(SortField.FIELD_SCORE, SortField.FIELD_DOC)
      val tdc = doConcurrentSearchWithThreshold(5, 0, query, sort, indexReader)
      val tdc2 = doSearchWithThreshold(5, 0, query, sort, indexReader)

      val countTdc = tdc.totalHits.value
      val countTdc2 = tdc2.totalHits.value
      assertTrue(countTdc > 0)
      assertTrue(countTdc2 > 0)
      CheckHits.checkEqual(query, tdc.scoreDocs, tdc2.scoreDocs)
    }

    indexReader.close()
    dir.close()
  }

  @Test
  fun testRelationVsTopDocsCount() {
    val sort = Sort(SortField.FIELD_SCORE, SortField.FIELD_DOC)
    val dir = newDirectory()
    val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))
    try {
      val doc = Document()
      doc.add(TextField("f", "foo bar", Field.Store.NO))
      w.addDocuments(listOf(doc, doc, doc, doc, doc))
      w.flush()
      w.addDocuments(listOf(doc, doc, doc, doc, doc))
      w.flush()

      val reader = DirectoryReader.open(w)
      try {
        val searcher = IndexSearcher(reader)
        var collectorManager = TopFieldCollectorManager(sort, 2, null, 10)
        var topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
        var count = topDocs.totalHits.value
        assertTrue(count == 10L)
        assertTrue(topDocs.totalHits.relation == TotalHits.Relation.EQUAL_TO)

        collectorManager = TopFieldCollectorManager(sort, 2, null, 2)
        topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
        count = topDocs.totalHits.value
        assertTrue(10L >= count)
        assertTrue(topDocs.totalHits.relation == TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO)

        collectorManager = TopFieldCollectorManager(sort, 10, null, 2)
        topDocs = searcher.search(TermQuery(Term("f", "foo")), collectorManager)
        count = topDocs.totalHits.value
        assertTrue(count == 10L)
        assertTrue(topDocs.totalHits.relation == TotalHits.Relation.EQUAL_TO)
      } finally {
        reader.close()
      }
    } finally {
      w.close()
      dir.close()
    }
  }
}
