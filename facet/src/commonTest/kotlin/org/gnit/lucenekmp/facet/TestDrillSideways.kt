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
package org.gnit.lucenekmp.facet

import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.Executors
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.facet.DrillSideways.DrillSidewaysResult
import org.gnit.lucenekmp.facet.sortedset.DefaultSortedSetDocValuesReaderState
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetField
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesReaderState
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.CollectionTerminatedException
import org.gnit.lucenekmp.search.Collector
import org.gnit.lucenekmp.search.CollectorManager
import org.gnit.lucenekmp.search.ConstantScoreScorer
import org.gnit.lucenekmp.search.ConstantScoreWeight
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.LeafCollector
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryCachingPolicy
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TotalHits
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.AssertingCollector
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InPlaceMergeSorter
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.test.*
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.ThreadPoolExecutor
import org.gnit.lucenekmp.jdkport.LinkedBlockingQueue
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Concurrency

open class TestDrillSideways : FacetTestCase() {

  protected open fun getNewDrillSideways(searcher: IndexSearcher, config: FacetsConfig, state: SortedSetDocValuesReaderState): DrillSideways {
    return DrillSideways(searcher, config, state)
  }

  protected open fun getNewDrillSideways(searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader): DrillSideways {
    return DrillSideways(searcher, config, taxoReader)
  }

  protected open fun getNewDrillSidewaysScoreSubdocsAtOnce(searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader): DrillSideways {
    return object : DrillSideways(searcher, config, taxoReader) {
      override protected fun scoreSubDocsAtOnce(): Boolean {
        return true
      }
    }
  }

  protected open fun getNewDrillSidewaysBuildFacetsResult(searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader): DrillSideways {
    return object : DrillSideways(searcher, config, taxoReader) {
      override protected fun buildFacetsResult(drillDowns: FacetsCollector?, drillSideways: Array<FacetsCollector>?, drillSidewaysDims: Array<String>?): Facets {
        var drillSidewaysFacets: MutableMap<String, Facets> = HashMap()
        var drillDownFacets: Facets = getTaxonomyFacetCounts(taxoReader, config, drillDowns!!)
        if (drillSideways != null) {
          for (i in 0 until drillSideways.size) {
            drillSidewaysFacets.put(
                drillSidewaysDims!![i], getTaxonomyFacetCounts(taxoReader, config, drillSideways[i]))
          }
        }

        if (drillSidewaysFacets.isEmpty()) {
          return drillDownFacets
        } else {
          return MultiFacets(drillSidewaysFacets, drillDownFacets)
        }
      }
    }
  }

  private fun getNewSearcher(reader: IndexReader): IndexSearcher {
    // Do not wrap with an asserting searcher, since DrillSidewaysQuery doesn't
    // implement all the required components like Weight#scorer.
    var searcher: IndexSearcher = newSearcher(reader, true, false, Concurrency.INTER_SEGMENT)
    // DrillSideways requires the entire range of docs to be scored at once, so it doesn't support
    // timeouts whose implementation scores one window of doc IDs at a time.
    searcher.timeout = null
    return searcher
  }

  // See LUCENE-10060:
  @Test
  fun testNoCaching() {
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()

    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)
    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()

    // Setup some basic test documents:
    var doc: Document = Document()
    doc.add(StringField("id", "1", Field.Store.NO))
    doc.add(SortedDocValuesField("id", BytesRef("1")))
    doc.add(FacetField("Color", "Red"))
    doc.add(FacetField("Size", "Small"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(StringField("id", "2", Field.Store.NO))
    doc.add(SortedDocValuesField("id", BytesRef("2")))
    doc.add(FacetField("Color", "Green"))
    doc.add(FacetField("Size", "Small"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(StringField("id", "3", Field.Store.NO))
    doc.add(SortedDocValuesField("id", BytesRef("3")))
    doc.add(FacetField("Color", "Blue"))
    doc.add(FacetField("Size", "Small"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(StringField("id", "4", Field.Store.NO))
    doc.add(SortedDocValuesField("id", BytesRef("4")))
    doc.add(FacetField("Color", "Red"))
    doc.add(FacetField("Size", "Medium"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(StringField("id", "5", Field.Store.NO))
    doc.add(SortedDocValuesField("id", BytesRef("5")))
    doc.add(FacetField("Color", "Blue"))
    doc.add(FacetField("Size", "Medium"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(StringField("id", "6", Field.Store.NO))
    doc.add(SortedDocValuesField("id", BytesRef("6")))
    doc.add(FacetField("Color", "Blue"))
    doc.add(FacetField("Size", "Large"))
    writer.addDocument(config.build(taxoWriter, doc))

    // Delete a couple documents. We'll want to make sure they don't get counted in binning:
    writer.deleteDocuments(Term("id", "4"))
    writer.deleteDocuments(Term("id", "6"))

    // Simple DDQ that just filters all results by Color == Blue:
    var ddq: DrillDownQuery = DrillDownQuery(config)
    ddq.add("Color", "Blue")

    // Set up an IndexSearcher that will try to cache queries aggressively:
    var searcher: IndexSearcher = getNewSearcher(writer.reader)
    searcher.queryCachingPolicy =
        object : QueryCachingPolicy {
          override fun onUse(query: Query) {}

          override fun shouldCache(query: Query): Boolean {
            return true
          }
        }

    // Set up a DS instance for searching:
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)
    var ds: DrillSideways = getNewDrillSideways(searcher, config, taxoReader)

    // We'll use a CollectorManager to trigger the trickiest caching behavior:
    var collectorManager: SimpleCollectorManager =
        SimpleCollectorManager(
            10, compareBy<CollectedResult> { it.id }, ScoreMode.COMPLETE_NO_SCORES)
    // Make sure our CM produces Collectors that _do not_ need scores to ensure IndexSearcher tries
    // to cache:
    assertFalse(collectorManager.newCollector().scoreMode().needsScores())
    // If we incorrectly cache here, the "sideways" FacetsCollectors will get populated with counts
    // for the deleted
    // docs. Make sure they don't:
    var concurrentResult: DrillSideways.ConcurrentDrillSidewaysResult<List<DocAndScore>> =
        ds.search(ddq, collectorManager)
    assertEquals(2, concurrentResult.collectorResult.size)
    assertEquals(
        "dim=Color path=[] value=4 childCount=3\n  Blue (2)\n  Red (1)\n  Green (1)\n",
        concurrentResult.facets!!.getTopChildren(10, "Color").toString())
    assertEquals(
        "dim=Size path=[] value=2 childCount=2\n  Small (1)\n  Medium (1)\n",
        concurrentResult.facets!!.getTopChildren(10, "Size").toString())

    // test getTopChildren(0, dim)
    expectThrows(IllegalArgumentException::class) { concurrentResult.facets!!.getTopChildren(0, "Color") }

    writer.close()
    IOUtils.close(searcher.indexReader, taxoReader, taxoWriter, dir, taxoDir)
  }

  @Test

  fun testCollectionTerminated() {
    newDirectory().use { dir ->
RandomIndexWriter(random(), dir).use { w ->

      var d: Document = Document()
      d.add(TextField("foo", "bar", Field.Store.NO))
      w.addDocument(d)

      d = Document()
      d.add(TextField("foo", "bar", Field.Store.NO))
      w.addDocument(d)

      d = Document()
      d.add(TextField("foo", "baz", Field.Store.NO))
      w.addDocument(d)

      w.reader.use { r ->

        var searcher: IndexSearcher = IndexSearcher(r)
        var ctx: LeafReaderContext = r.leaves().get(0)

        var baseQuery: Query = MatchAllDocsQuery()
        var baseWeight: Weight = searcher.createWeight(baseQuery, ScoreMode.COMPLETE_NO_SCORES, 1f)
        var baseScorer: Scorer = baseWeight.scorer(ctx)!!

        var dimQ: Query = TermQuery(Term("foo", "bar"))
        dimQ = searcher.rewrite(dimQ)
        var dimWeight: Weight = searcher.createWeight(dimQ, ScoreMode.COMPLETE_NO_SCORES, 1f)
        var dimScorer: Scorer = dimWeight.scorer(ctx)!!

        var dimFC: FacetsCollector = FacetsCollector()
        var docsAndCost: DrillSidewaysScorer.DocsAndCost =
            DrillSidewaysScorer.DocsAndCost(dimScorer, dimFC.getLeafCollector(ctx))

        var baseCollector: LeafCollector =
            object : LeafCollector {
              var counter: Int = 0

              override var scorer: Scorable? = null // doesn't matter

              override fun collect(doc: Int) {
                if (++counter > 1) {
                  throw CollectionTerminatedException()
                }
              }
            }

        var scoreSubDocsAtOnce: Boolean = random().nextBoolean()
        var scorer: DrillSidewaysScorer =
            DrillSidewaysScorer(
                ctx,
                baseScorer,
                arrayOf(docsAndCost),
                scoreSubDocsAtOnce)
        expectThrows(CollectionTerminatedException::class) { scorer.score(baseCollector, null, 0, DocIdSetIterator.NO_MORE_DOCS) }

        // We've set things up so that our base collector with throw CollectionTerminatedException
        // after collecting the first doc. This means we'll only collect the first indexed doc for
        // both our base and sideways dim facets collectors. What we really want to test here is
        // that the matching docs are still correctly present and populated after an early
        // termination occurs (i.e., #finish is properly called in that scenario):
        assertEquals(1, dimFC.getMatchingDocs().size)
      }
    }}
  }

  @Test

  fun testBasic() {
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()
    config.setHierarchical("Publish Date", true)

    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var doc: Document = Document()
    doc.add(FacetField("Author", "Bob"))
    doc.add(FacetField("Publish Date", "2010", "10", "15"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2010", "10", "20"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2012", "1", "1"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Susan"))
    doc.add(FacetField("Publish Date", "2012", "1", "7"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Frank"))
    doc.add(FacetField("Publish Date", "1999", "5", "5"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher: IndexSearcher = getNewSearcher(writer.reader)

    // System.out.println("searcher=" + searcher);

    // NRT open
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)

    // Run all the basic test cases with a standard DrillSideways implementation:
    var ds: DrillSideways = getNewDrillSideways(searcher, config, taxoReader)
    runDrillSidewaysTestCases(config, ds)

    // Run all the basic test cases but make sure DS is set to score all sub-docs at once, so
    // we exercise the doc-at-a-time scoring methodology:
    ds = getNewDrillSidewaysScoreSubdocsAtOnce(searcher, config, taxoReader)
    runDrillSidewaysTestCases(config, ds)

    writer.close()
    IOUtils.close(searcher.indexReader, taxoReader, taxoWriter, dir, taxoDir)
  }

  @Test

  fun testLeafCollectorSingleFinishCall() {
    newDirectory().use { dir ->
newDirectory().use { taxoDir ->
RandomIndexWriter(random(), dir).use { w ->
DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE).use { taxoW ->

      var facetsConfig: FacetsConfig = FacetsConfig()

      var d: Document = Document()
      d.add(FacetField("foo", "bar"))
      w.addDocument(facetsConfig.build(taxoW, d))

      w.reader.use { r ->
DirectoryTaxonomyReader(taxoW).use { taxoR ->

        var searcher: IndexSearcher = DrillSidewaysAssertingIndexSearcher(r)

        var baseQuery: Query = MatchAllDocsQuery()
        var ddq: DrillDownQuery = DrillDownQuery(facetsConfig, baseQuery)
        ddq.add("foo", "bar")
        var drillSideways: DrillSideways = DrillSideways(searcher, facetsConfig, taxoR)

        // It doesn't really matter what we have our collector manager do here since all we care
        // about for this test are the assertions provided by the "asserting" collectors/leaf
        // collectors, etc. But we have this definition already for other tests and it's easy to
        // use, so we'll use it here and do a little extra checking:
        var cm: SimpleCollectorManager =
            SimpleCollectorManager(10, compareBy<CollectedResult> { it.docAndScore.doc })

        var result: DrillSideways.ConcurrentDrillSidewaysResult<List<DocAndScore>> =
            drillSideways.search(ddq, cm)

        assertEquals(1, result.collectorResult.size)
      }}
    }}}}
  }

  private fun runDrillSidewaysTestCases(config: FacetsConfig, ds: DrillSideways) {
    // case: drill-down on a single field; in this
    // case the drill-sideways + drill-down counts ==
    // drill-down of just the query:
    var ddq: DrillDownQuery = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    var r: DrillSidewaysResult = ds.search(null, ddq, 10)
    assertEquals(2L, r.hits!!.totalHits.value)
    // Publish Date is only drill-down, and Lisa published
    // one in 2012 and one in 2010:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())

    // Author is drill-sideways + drill-down: Lisa
    // (drill-down) published twice, and Frank/Susan/Bob
    // published once:
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // Another simple case: drill-down on single fields
    // but OR of two values
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Author", "Bob")
    r = ds.search(null, ddq, 10)
    assertEquals(3L, r.hits!!.totalHits.value)
    // Publish Date is only drill-down: Lisa and Bob
    // (drill-down) published twice in 2010 and once in 2012:
    assertEquals(
        "dim=Publish Date path=[] value=3 childCount=2\n  2010 (2)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down: Lisa
    // (drill-down) published twice, and Frank/Susan/Bob
    // published once:
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    assertTrue(r.facets is MultiFacets)
    var allResults: List<FacetResult> = r.facets!!.getAllDims(10)
    assertEquals(2, allResults.size)
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        allResults.get(0).toString())
    assertEquals(
        "dim=Publish Date path=[] value=3 childCount=2\n  2010 (2)\n  2012 (1)\n",
        allResults.get(1).toString())

    // test default implementation of getTopDims
    var topNDimsResult: List<FacetResult> = r.facets!!.getTopDims(2, 1)
    assertEquals(2, topNDimsResult.size)
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n", topNDimsResult.get(0).toString())
    assertEquals(
        "dim=Publish Date path=[] value=3 childCount=2\n  2010 (2)\n",
        topNDimsResult.get(1).toString())

    // test getTopDims(0, 1)
    var topDimsResults2: List<FacetResult> = r.facets!!.getTopDims(0, 1)
    assertEquals(0, topDimsResults2.size)

    // test getAllDims(0)
    var finalR1: DrillSidewaysResult = r
    expectThrows(IllegalArgumentException::class) { finalR1.facets!!.getAllDims(0) }

    // More interesting case: drill-down on two fields
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Publish Date", "2010")
    r = ds.search(null, ddq, 10)
    assertEquals(1L, r.hits!!.totalHits.value)
    // Publish Date is drill-sideways + drill-down: Lisa
    // (drill-down) published once in 2010 and once in 2012:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down:
    // only Lisa & Bob published (once each) in 2010:
    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // Even more interesting case: drill down on two fields,
    // but one of them is OR
    ddq = DrillDownQuery(config)

    // Drill down on Lisa or Bob:
    ddq.add("Author", "Lisa")
    ddq.add("Publish Date", "2010")
    ddq.add("Author", "Bob")
    r = ds.search(null, ddq, 10)
    assertEquals(2L, r.hits!!.totalHits.value)
    // Publish Date is both drill-sideways + drill-down:
    // Lisa or Bob published twice in 2010 and once in 2012:
    assertEquals(
        "dim=Publish Date path=[] value=3 childCount=2\n  2010 (2)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down:
    // only Lisa & Bob published (once each) in 2010:
    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // Test drilling down on invalid field:
    ddq = DrillDownQuery(config)
    ddq.add("Foobar", "Baz")
    r = ds.search(null, ddq, 10)
    assertEquals(0L, r.hits!!.totalHits.value)
    assertNull(r.facets!!.getTopChildren(10, "Publish Date"))
    assertNull(r.facets!!.getTopChildren(10, "Foobar"))

    // Test drilling down on valid term or'd with invalid term:
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Author", "Tom")
    r = ds.search(null, ddq, 10)
    assertEquals(2L, r.hits!!.totalHits.value)
    // Publish Date is only drill-down, and Lisa published
    // one in 2012 and one in 2010:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down: Lisa
    // (drill-down) published twice, and Frank/Susan/Bob
    // published once:
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // LUCENE-4915: test drilling down on a dimension but
    // NOT facet counting it:
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Author", "Tom")
    r = ds.search(null, ddq, 10)
    assertEquals(2L, r.hits!!.totalHits.value)
    // Publish Date is only drill-down, and Lisa published
    // one in 2012 and one in 2010:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())

    // Test main query gets null scorer:
    ddq = DrillDownQuery(config, TermQuery(Term("foobar", "baz")))
    ddq.add("Author", "Lisa")
    r = ds.search(null, ddq, 10)

    assertEquals(0L, r.hits!!.totalHits.value)
    assertNull(r.facets!!.getTopChildren(10, "Publish Date"))
    assertNull(r.facets!!.getTopChildren(10, "Author"))

    // test getTopChildren(0, dim)
    var finalR: DrillSidewaysResult = r
    expectThrows(IllegalArgumentException::class) { finalR.facets!!.getTopChildren(0, "Author") }
  }

  @Test

  fun testBasicWithCollectorManager() {
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()
    config.setHierarchical("Publish Date", true)

    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var doc: Document = Document()
    doc.add(newStringField("content", "foo", Field.Store.NO))
    doc.add(FacetField("Author", "Bob"))
    doc.add(FacetField("Publish Date", "2010", "10", "15"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(newStringField("content", "foo", Field.Store.NO))
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2010", "10", "20"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(newStringField("content", "foo", Field.Store.NO))
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2012", "1", "1"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(newStringField("content", "bar", Field.Store.NO))
    doc.add(FacetField("Author", "Susan"))
    doc.add(FacetField("Publish Date", "2012", "1", "7"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(newStringField("content", "bar", Field.Store.NO))
    doc.add(FacetField("Author", "Frank"))
    doc.add(FacetField("Publish Date", "1999", "5", "5"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher: IndexSearcher = getNewSearcher(writer.reader)

    // NRT open
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)

    var ds: DrillSideways = getNewDrillSideways(searcher, config, taxoReader)

    var comparator: Comparator<CollectedResult> =
        Comparator { a, b ->
          var cmp: Int = b.docAndScore.score.compareTo(a.docAndScore.score) // highest-to-lowest
          if (cmp == 0) {
            cmp = a.docAndScore.doc.compareTo(b.docAndScore.doc) // tie-break
          }
          cmp
        }

    var manager: SimpleCollectorManager = SimpleCollectorManager(10, comparator)

    //  case: drill-down on a single field; in this
    // case the drill-sideways + drill-down counts ==
    // drill-down of just the query:
    var ddq: DrillDownQuery = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    var r: DrillSideways.ConcurrentDrillSidewaysResult<List<DocAndScore>> = ds.search(ddq, manager)
    assertEquals(2, r.collectorResult.size)
    // Publish Date is only drill-down, and Lisa published
    // one in 2012 and one in 2010:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())

    // Author is drill-sideways + drill-down: Lisa
    // (drill-down) published twice, and Frank/Susan/Bob
    // published once:
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // Same simple case, but no baseQuery (pure browse):
    // drill-down on a single field; in this case the
    // drill-sideways + drill-down counts == drill-down of
    // just the query:
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    r = ds.search(ddq, manager)

    assertEquals(2, r.collectorResult.size)
    // Publish Date is only drill-down, and Lisa published
    // one in 2012 and one in 2010:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())

    // Author is drill-sideways + drill-down: Lisa
    // (drill-down) published twice, and Frank/Susan/Bob
    // published once:
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // Another simple case: drill-down on single fields
    // but OR of two values
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Author", "Bob")
    r = ds.search(ddq, manager)
    assertEquals(3, r.collectorResult.size)
    // Publish Date is only drill-down: Lisa and Bob
    // (drill-down) published twice in 2010 and once in 2012:
    assertEquals(
        "dim=Publish Date path=[] value=3 childCount=2\n  2010 (2)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down: Lisa
    // (drill-down) published twice, and Frank/Susan/Bob
    // published once:
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    assertTrue(r.facets is MultiFacets)
    var allResults: List<FacetResult> = r.facets!!.getAllDims(10)
    assertEquals(2, allResults.size)
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        allResults.get(0).toString())
    assertEquals(
        "dim=Publish Date path=[] value=3 childCount=2\n  2010 (2)\n  2012 (1)\n",
        allResults.get(1).toString())

    // test default implementation of getTopDims
    var topNDimsResult: List<FacetResult> = r.facets!!.getTopDims(1, 2)
    assertEquals(1, topNDimsResult.size)
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n",
        topNDimsResult.get(0).toString())

    // test getTopDims(10, 10) and expect same results from getAllDims(10)
    var allDimsResults: List<FacetResult> = r.facets!!.getTopDims(10, 10)
    assertEquals(allResults, allDimsResults)

    // More interesting case: drill-down on two fields
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Publish Date", "2010")
    r = ds.search(ddq, manager)
    assertEquals(1, r.collectorResult.size)
    // Publish Date is drill-sideways + drill-down: Lisa
    // (drill-down) published once in 2010 and once in 2012:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down:
    // only Lisa & Bob published (once each) in 2010:
    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // Even more interesting case: drill down on two fields,
    // but one of them is OR
    ddq = DrillDownQuery(config)

    // Drill down on Lisa or Bob:
    ddq.add("Author", "Lisa")
    ddq.add("Publish Date", "2010")
    ddq.add("Author", "Bob")
    r = ds.search(ddq, manager)
    assertEquals(2, r.collectorResult.size)
    // Publish Date is both drill-sideways + drill-down:
    // Lisa or Bob published twice in 2010 and once in 2012:
    assertEquals(
        "dim=Publish Date path=[] value=3 childCount=2\n  2010 (2)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down:
    // only Lisa & Bob published (once each) in 2010:
    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // Test drilling down on invalid field:
    ddq = DrillDownQuery(config)
    ddq.add("Foobar", "Baz")
    r = ds.search(ddq, manager)
    assertEquals(0, r.collectorResult.size)
    assertNull(r.facets!!.getTopChildren(10, "Publish Date"))
    assertNull(r.facets!!.getTopChildren(10, "Foobar"))

    // Test drilling down on valid term or'd with invalid term:
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Author", "Tom")
    r = ds.search(ddq, manager)
    assertEquals(2, r.collectorResult.size)
    // Publish Date is only drill-down, and Lisa published
    // one in 2012 and one in 2010:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down: Lisa
    // (drill-down) published twice, and Frank/Susan/Bob
    // published once:
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // LUCENE-4915: test drilling down on a dimension but
    // NOT facet counting it:
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Author", "Tom")
    r = ds.search(ddq, manager)
    assertEquals(2, r.collectorResult.size)
    // Publish Date is only drill-down, and Lisa published
    // one in 2012 and one in 2010:
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())

    // Test main query gets null scorer:
    ddq = DrillDownQuery(config, TermQuery(Term("foobar", "baz")))
    ddq.add("Author", "Lisa")
    r = ds.search(ddq, manager)
    assertEquals(0, r.collectorResult.size)
    assertNull(r.facets!!.getTopChildren(10, "Publish Date"))
    assertNull(r.facets!!.getTopChildren(10, "Author"))

    // Test no drill down dims:
    ddq = DrillDownQuery(config, MatchAllDocsQuery())
    r = ds.search(ddq, manager)
    assertEquals(5, r.collectorResult.size)
    assertEquals(
        "dim=Publish Date path=[] value=5 childCount=3\n  2010 (2)\n  2012 (2)\n  1999 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    // Test no drill down dims with null FacetsCollectorManager for the main query:
    ddq = DrillDownQuery(config, MatchAllDocsQuery())
    r =
        object : DrillSideways(searcher, config, taxoReader) {
          override protected fun createDrillDownFacetsCollectorManager(): FacetsCollectorManager? {
            return null
          }
        }.search(ddq, manager)
    assertEquals(5, r.collectorResult.size)
    // Expect null facets since we provided a null FacetsCollectorManager
    assertNull(r.facets)

    // Test the case where the base query rewrites itself. See LUCENE-9988:
    var baseQuery: Query =
        object : TermQuery(Term("content", "foo")) {
          override fun rewrite(indexSearcher: IndexSearcher): Query {
            // return a new instance, forcing the DrillDownQuery to also rewrite itself, exposing
            // the bug in LUCENE-9988:
            return TermQuery(getTerm())
          }
        }
    ddq = DrillDownQuery(config, baseQuery)
    ddq.add("Author", "Lisa")
    r = ds.search(ddq, manager)
    assertEquals(2, r.collectorResult.size)
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    assertEquals(
        "dim=Author path=[] value=3 childCount=2\n  Lisa (2)\n  Bob (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    writer.close()
    IOUtils.close(searcher.indexReader, taxoReader, taxoWriter, dir, taxoDir)
  }

  @Test

  fun testSometimesInvalidDrillDown() {
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()
    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()
    config.setHierarchical("Publish Date", true)

    var doc: Document = Document()
    doc.add(FacetField("Author", "Bob"))
    doc.add(FacetField("Publish Date", "2010", "10", "15"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2010", "10", "20"))
    writer.addDocument(config.build(taxoWriter, doc))

    writer.commit()

    // 2nd segment has no Author:
    doc = Document()
    doc.add(FacetField("Foobar", "Lisa"))
    doc.add(FacetField("Publish Date", "2012", "1", "1"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher: IndexSearcher = getNewSearcher(writer.reader)

    // System.out.println("searcher=" + searcher);

    // NRT open
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)

    var ddq: DrillDownQuery = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    var r: DrillSidewaysResult = getNewDrillSideways(searcher, config, taxoReader).search(null, ddq, 10)

    assertEquals(1L, r.hits!!.totalHits.value)
    // Publish Date is only drill-down, and Lisa published
    // one in 2012 and one in 2010:
    assertEquals(
        "dim=Publish Date path=[] value=1 childCount=1\n  2010 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    // Author is drill-sideways + drill-down: Lisa
    // (drill-down) published once, and Bob
    // published once:
    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    writer.close()
    IOUtils.close(searcher.indexReader, taxoReader, taxoWriter, dir, taxoDir)
  }

  @Test

  fun testMultipleRequestsPerDim() {
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()
    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()
    config.setHierarchical("dim", true)

    var doc: Document = Document()
    doc.add(FacetField("dim", "a", "x"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("dim", "a", "y"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("dim", "a", "z"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("dim", "b"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("dim", "c"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("dim", "d"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher: IndexSearcher = getNewSearcher(writer.reader)

    // System.out.println("searcher=" + searcher);

    // NRT open
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)

    var ddq: DrillDownQuery = DrillDownQuery(config)
    ddq.add("dim", "a")
    var r: DrillSidewaysResult = getNewDrillSideways(searcher, config, taxoReader).search(null, ddq, 10)

    assertEquals(3L, r.hits!!.totalHits.value)
    assertEquals(
        "dim=dim path=[] value=6 childCount=4\n  a (3)\n  b (1)\n  c (1)\n  d (1)\n",
        r.facets!!.getTopChildren(10, "dim").toString())
    assertEquals(
        "dim=dim path=[a] value=3 childCount=3\n  x (1)\n  y (1)\n  z (1)\n",
        r.facets!!.getTopChildren(10, "dim", "a").toString())

    writer.close()
    IOUtils.close(searcher.indexReader, taxoReader, taxoWriter, dir, taxoDir)
  }

  private class Doc : Comparable<Doc> {
    lateinit var id: String
    lateinit var contentToken: String



    // -1 if the doc is missing this dim, else the index
    // -into the values for this dim:
    lateinit var dims: IntArray

    // 2nd value per dim for the doc (so we test
    // multivalued fields):
    lateinit var dims2: IntArray
    var deleted = false

    override fun compareTo(other: Doc): Int {
      return id.compareTo(other.id)
    }
  }

  private var aChance = 0.0
  private var bChance = 0.0
  private var cChance = 0.0

  private fun randomContentToken(isQuery: Boolean): String {
    var d: Double = random().nextDouble()
    if (isQuery) {
      if (d < 0.33) {
        return "a"
      } else if (d < 0.66) {
        return "b"
      } else {
        return "c"
      }
    } else {
      if (d <= aChance) {
        return "a"
      } else if (d < aChance + bChance) {
        return "b"
      } else {
        return "c"
      }
    }
  }

  @Test

  fun testRandom() {

    while (aChance == 0.0) {
      aChance = random().nextDouble()
    }
    while (bChance == 0.0) {
      bChance = random().nextDouble()
    }
    while (cChance == 0.0) {
      cChance = random().nextDouble()
    }
    // aChance = .01;
    // bChance = 0.5;
    // cChance = 1.0;
    var sum: Double = aChance + bChance + cChance
    aChance /= sum
    bChance /= sum
    cChance /= sum

    var numDims: Int = TestUtil.nextInt(random(), 2, 5)
    // int numDims = 3;
    var numDocs: Int = atLeast(300)
    // int numDocs = 20;
    if (VERBOSE) {
      println(
          "numDims="
              + numDims
              + " numDocs="
              + numDocs
              + " aChance="
              + aChance
              + " bChance="
              + bChance
              + " cChance="
              + cChance)
    }
    var dimValues: Array<Array<String>> = Array(numDims) { emptyArray<String>() }
    var valueCount: Int = 2

    for (dim in 0 until numDims) {
      var values: MutableSet<String> = HashSet()
      while (values.size < valueCount) {
        var s: String = TestUtil.randomRealisticUnicodeString(random())
        // String s = _TestUtil.randomString(random());
        if (s.length > 0) {
          values.add(s)
        }
      }
      dimValues[dim] = values.toTypedArray()
      valueCount *= 2
    }

    var docs: MutableList<Doc> = ArrayList()
    for (i in 0 until numDocs) {
      var doc: Doc = Doc()
      doc.id = "" + i
      doc.contentToken = randomContentToken(false)
      doc.dims = IntArray(numDims)
      doc.dims2 = IntArray(numDims)
      for (dim in 0 until numDims) {
        if (random().nextInt(5) == 3) {
          // This doc is missing this dim:
          doc.dims[dim] = -1
        } else if (dimValues[dim].size <= 4) {
          var dimUpto: Int = 0
          doc.dims[dim] = dimValues[dim].size - 1
          while (dimUpto < dimValues[dim].size) {
            if (random().nextBoolean()) {
              doc.dims[dim] = dimUpto
              break
            }
            dimUpto++
          }
        } else {
          doc.dims[dim] = random().nextInt(dimValues[dim].size)
        }

        if (random().nextInt(5) == 3) {
          // 2nd value:
          doc.dims2[dim] = random().nextInt(dimValues[dim].size)
        } else {
          doc.dims2[dim] = -1
        }
      }
      docs.add(doc)
    }

    var d: Directory = newDirectory()
    var td: Directory = newDirectory()

    var iwc: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(random()))
    iwc.setInfoStream(InfoStream.NO_OUTPUT)
    var w: RandomIndexWriter = RandomIndexWriter(random(), d, iwc)
    var tw: DirectoryTaxonomyWriter = DirectoryTaxonomyWriter(td, IndexWriterConfig.OpenMode.CREATE)
    var config: FacetsConfig = FacetsConfig()
    for (i in 0 until numDims) {
      config.setMultiValued("dim" + i, true)
    }

    var doUseDV: Boolean = random().nextBoolean()

    for (rawDoc in docs) {
      var doc: Document = Document()
      doc.add(newStringField("id", rawDoc.id, Field.Store.YES))
      doc.add(SortedDocValuesField("id", BytesRef(rawDoc.id)))
      doc.add(newStringField("content", rawDoc.contentToken, Field.Store.NO))

      if (VERBOSE) {
        println("  doc id=" + rawDoc.id + " token=" + rawDoc.contentToken)
      }
      for (dim in 0 until numDims) {
        var dimValue: Int = rawDoc.dims[dim]
        if (dimValue != -1) {
          if (doUseDV) {
            doc.add(SortedSetDocValuesFacetField("dim" + dim, dimValues[dim][dimValue]))
          } else {
            doc.add(FacetField("dim" + dim, dimValues[dim][dimValue]))
          }
          doc.add(StringField("dim" + dim, dimValues[dim][dimValue], Field.Store.YES))
          doc.add(SortedSetDocValuesField("dim" + dim, BytesRef(dimValues[dim][dimValue])))
          if (VERBOSE) {
            println("    dim" + dim + "=" + BytesRef(dimValues[dim][dimValue]))
          }
        }
        var dimValue2: Int = rawDoc.dims2[dim]
        if (dimValue2 != -1) {
          if (doUseDV) {
            doc.add(SortedSetDocValuesFacetField("dim" + dim, dimValues[dim][dimValue2]))
          } else {
            doc.add(FacetField("dim" + dim, dimValues[dim][dimValue2]))
          }
          doc.add(StringField("dim" + dim, dimValues[dim][dimValue2], Field.Store.YES))
          doc.add(
              SortedSetDocValuesField("dim" + dim, BytesRef(dimValues[dim][dimValue2])))
          if (VERBOSE) {
            println("      dim" + dim + "=" + BytesRef(dimValues[dim][dimValue2]))
          }
        }
      }

      w.addDocument(config.build(tw, doc))
    }

    if (random().nextBoolean()) {
      // Randomly delete a few docs:
      var numDel: Int = TestUtil.nextInt(random(), 1, (numDocs * 0.05).toInt())
      if (VERBOSE) {
        println("delete " + numDel)
      }
      var delCount: Int = 0
      while (delCount < numDel) {
        var doc: Doc = docs.get(random().nextInt(docs.size))
        if (!doc.deleted) {
          if (VERBOSE) {
            println("  delete id=" + doc.id)
          }
          doc.deleted = true
          w.deleteDocuments(Term("id", doc.id))
          delCount++
        }
      }
    }

    if (random().nextBoolean()) {
      if (VERBOSE) {
        println("TEST: forceMerge(1)...")
      }
      w.forceMerge(1)
    }
    var r: IndexReader = w.reader

    val sortedSetDVState: SortedSetDocValuesReaderState?
    var s: IndexSearcher = getNewSearcher(r)

    if (doUseDV) {
      sortedSetDVState = DefaultSortedSetDocValuesReaderState(s.indexReader, config)
    } else {
      sortedSetDVState = null
    }

    if (VERBOSE) {
      println("r.numDocs() = " + r.numDocs())
    }

    // NRT open
    var tr: TaxonomyReader = DirectoryTaxonomyReader(tw)

    var numIters: Int = atLeast(10)

    for (iter in 0 until numIters) {

      var contentToken: String? = if (random().nextInt(30) == 17) null else randomContentToken(true)
      var numDrillDown: Int = TestUtil.nextInt(random(), 1, minOf(4, numDims))
      if (VERBOSE) {
        println(
            "\nTEST: iter="
                + iter
                + " baseQuery="
                + contentToken
                + " numDrillDown="
                + numDrillDown
                + " useSortedSetDV="
                + doUseDV)
      }

      var drillDowns: Array<Array<String>?> = arrayOfNulls(numDims)

      var count: Int = 0
      while (count < numDrillDown) {
        var dim: Int = random().nextInt(numDims)
        if (drillDowns[dim] == null) {
          if (random().nextBoolean()) {
            // Drill down on one value:
            drillDowns[dim] =
                arrayOf(dimValues[dim][random().nextInt(dimValues[dim].size)])
          } else {
            var orCount: Int = TestUtil.nextInt(random(), 1, minOf(5, dimValues[dim].size))
            drillDowns[dim] = Array(orCount) { "" }
            for (i in 0 until orCount) {
              while (true) {
                var value: String? = dimValues[dim][random().nextInt(dimValues[dim].size)]
                for (j in 0 until i) {
                  if (value.equals(drillDowns[dim]!![j])) {
                    value = null
                    break
                  }
                }
                if (value != null) {
                  drillDowns[dim]!![i] = value
                  break
                }
              }
            }
          }
          if (VERBOSE) {
            var values: Array<BytesRef> = Array(drillDowns[dim]!!.size) { BytesRef() }
            for (i in 0 until values.size) {
              values[i] = BytesRef(drillDowns[dim]!![i])
            }
            println("  dim" + dim + "=" + values.contentToString())
          }
          count++
        }
      }

      var baseQuery: Query
      if (contentToken == null) {
        baseQuery = MatchAllDocsQuery()
      } else {
        baseQuery = TermQuery(Term("content", contentToken))
      }

      var ddq: DrillDownQuery = DrillDownQuery(config, baseQuery)

      for (dim in 0 until numDims) {
        if (drillDowns[dim] != null) {
          for (value in drillDowns[dim]!!) {
            // Sometimes use a "traditional" term query and sometimes use a two-phase approach to
            // ensure code coverage:
            if (random().nextBoolean()) {
              ddq.add("dim" + dim, value)
            } else {
              ddq.add(
                  "dim" + dim,
                  SortedSetDocValuesField.newSlowExactQuery("dim" + dim, BytesRef(value)))
            }
          }
        }
      }

      var filter: Query?
      if (random().nextInt(7) == 6) {
        if (VERBOSE) {
          println("  only-even filter")
        }
        filter =
            object : Query() {
              private val identity = Any()

              override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
                return object : ConstantScoreWeight(this, boost) {
                  override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                    var approximation: DocIdSetIterator =
                        DocIdSetIterator.all(context.reader().maxDoc())
                    var scorer =
                        ConstantScoreScorer(
                            score(),
                            scoreMode,
                            object : TwoPhaseIterator(approximation) {

                              override fun matches(): Boolean {
                                var docID: Int = approximation.docID()
                                return (context.reader().storedFields().document(docID).get("id")!!.toInt() and 1) == 0
                              }

                              override fun matchCost(): Float {
                                return 1000f
                              }
                            })
                    return DefaultScorerSupplier(scorer)
                  }

                  override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                  }
                }
              }

              override fun visit(visitor: QueryVisitor) {}

              override fun toString(field: String?): String {
                return "drillSidewaysTestFilter"
              }

              override fun equals(o: Any?): Boolean {
                return o === this
              }

              override fun hashCode(): Int {
                return identity.hashCode()
              }
            }
      } else {
        filter = null
      }

      // Verify docs are always collected in order.  If we
      // had an AssertingScorer it could catch it when
      // Weight.scoresDocsOutOfOrder lies!:
      getNewDrillSideways(s, config, tr)
          .search(
              ddq,
              SimpleCollectorManager(
                  numDocs,
                  compareBy<CollectedResult> { it.docAndScore.doc },
                  ScoreMode.COMPLETE_NO_SCORES))

      var sort: Sort = Sort(SortField("id", SortField.Type.STRING))
      var ds: DrillSideways
      if (doUseDV) {
        ds = getNewDrillSideways(s, config, sortedSetDVState!!)
      } else {
        ds = getNewDrillSidewaysBuildFacetsResult(s, config, tr)
      }

      var actual: DSResults = DSResults()
      if (random().nextBoolean()) {
        var dsr: DrillSidewaysResult = ds.search(ddq, filter, null, numDocs, sort, true)
        actual.resultCount = dsr.hits!!.totalHits
        for (scoreDoc in dsr.hits!!.scoreDocs) {
          actual.results.add(DocAndScore(scoreDoc.doc, scoreDoc.score))
        }
        actual.facets = dsr.facets!!
      } else {

        // can't support filter queries with this impl
        filter = null

        var manager: CollectorManager<SimpleCollector, List<DocAndScore>> =
            SimpleCollectorManager(numDocs, compareBy<CollectedResult> { it.id })
        // Because we validate the scores computed through DrillSideways against those found through
        // a direct search
        // against IndexSearcher, make sure our Collectors announce themselves as requiring scores.
        // See conversation
        // in LUCENE-10060 where this bug was introduced and then discovered:
        assertTrue(manager.newCollector().scoreMode().needsScores())
        var cr: DrillSideways.ConcurrentDrillSidewaysResult<List<DocAndScore>> = ds.search(ddq, manager)
        actual.results = cr.collectorResult.toMutableList()
        actual.resultCount = TotalHits(actual.results.size.toLong(), TotalHits.Relation.EQUAL_TO)
        actual.facets = cr.facets!!
      }

      var expected: TestFacetResult =
          slowDrillSidewaysSearch(s, docs, contentToken, drillDowns, dimValues, filter)

      var hits: TopDocs = s.search(baseQuery, numDocs)
      var scores: MutableMap<String, Float> = HashMap()
      for (sd in hits.scoreDocs) {
        scores.put(s.storedFields().document(sd.doc).get("id")!!, sd.score)
      }
      if (VERBOSE) {
        println("  verify all facets")
      }
      verifyEquals(dimValues, s, expected, actual, scores, doUseDV)

      // Make sure drill down doesn't change score:
      var q: Query = ddq
      if (filter != null) {
        q = BooleanQuery.Builder().add(q, Occur.MUST).add(filter, Occur.FILTER).build()
      }
      var ddqHits: TopDocs = s.search(q, numDocs)
      assertEquals(expected.hits.size.toLong(), ddqHits.totalHits.value)
      for (i in 0 until expected.hits.size) {
        // Score should be IDENTICAL:
        assertEquals(scores.get(expected.hits.get(i).id)!!, ddqHits.scoreDocs[i].score, 0.0f)
      }
    }

    w.close()
    IOUtils.close(r, tr, tw, d, td)
  }

  @Test

  fun testFinishOnAllDimsNoHitsQuery() {
    newDirectory().use { dir ->
newDirectory().use { taxoDir ->
RandomIndexWriter(random(), dir).use { w ->
DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE).use { taxoW ->

      var facetsConfig: FacetsConfig = FacetsConfig()

      var d: Document = Document()
      d.add(FacetField("foo", "bar"))
      w.addDocument(facetsConfig.build(taxoW, d))

      w.reader.use { r ->
DirectoryTaxonomyReader(taxoW).use { taxoR ->

        var searcher: IndexSearcher = DrillSidewaysAssertingIndexSearcher(r)

        // Creating a query that matches nothing to make sure #finish still gets called on all
        // facet collectors:
        var baseQuery: Query = MatchNoDocsQuery()
        var ddq: DrillDownQuery = DrillDownQuery(facetsConfig, baseQuery)
        ddq.add("foo", "bar")
        var drillSideways: DrillSideways =
            object : DrillSideways(searcher, facetsConfig, taxoR) {
              override protected fun createDrillDownFacetsCollectorManager(): FacetsCollectorManager? {
                return AssertingFacetsCollectorManager()
              }

              override protected fun createDrillSidewaysFacetsCollectorManager(): FacetsCollectorManager {
                return AssertingFacetsCollectorManager()
              }
            }

        var cm: SimpleCollectorManager =
            SimpleCollectorManager(10, compareBy<CollectedResult> { it.docAndScore.doc })
        var result: DrillSideways.ConcurrentDrillSidewaysResult<List<DocAndScore>> =
            drillSideways.search(ddq, cm)
        assertEquals(0, result.collectorResult.size)

        // Make sure the "matching docs" are still populated with the appropriate leaf reader
        // context, which happens as part of #finish getting called:
        assertEquals(1, result.drillDownFacetsCollector!!.getMatchingDocs().size)
        assertEquals(
            1,
            result.drillDownFacetsCollector!!.getMatchingDocs().get(0).context.reader().maxDoc())
        assertEquals(1, result.drillSidewaysFacetsCollector!!.size)
        assertEquals(1, result.drillSidewaysFacetsCollector!![0].getMatchingDocs().size)
        assertEquals(
            1,
            result
                .drillSidewaysFacetsCollector!![0]
                .getMatchingDocs()
                .get(0)
                .context
                .reader()
                .maxDoc())
      }}
    }}}}
  }

  private class Counters {
    lateinit var counts: Array<IntArray>

    constructor(dimValues: Array<Array<String>>) {
      counts = Array(dimValues.size) { IntArray(0) }
      for (dim in 0 until dimValues.size) {
        counts[dim] = IntArray(dimValues[dim].size)
      }
    }

    fun inc(dims: IntArray, dims2: IntArray) {
      inc(dims, dims2, -1)
    }

    fun inc(dims: IntArray, dims2: IntArray, onlyDim: Int) {
      assertTrue(dims.size == counts.size)
      assertTrue(dims2.size == counts.size)
      for (dim in 0 until dims.size) {
        if (onlyDim == -1 || dim == onlyDim) {
          if (dims[dim] != -1) {
            counts[dim][dims[dim]]++
          }
          if (dims2[dim] != -1 && dims2[dim] != dims[dim]) {
            counts[dim][dims2[dim]]++
          }
        }
      }
    }
  }

  private class TestFacetResult {
    lateinit var hits: MutableList<Doc>
    lateinit var counts: Array<IntArray>
    lateinit var uniqueCounts: IntArray


  }

  private class DocAndScore(val doc: Int, val score: Float) {
    constructor(other: DocAndScore) : this(other.doc, other.score)
  }

  private class DSResults {
    lateinit var resultCount: TotalHits
    var results: MutableList<DocAndScore> = ArrayList()
    lateinit var facets: Facets
  }

  private data class CollectedResult(val docAndScore: DocAndScore, val id: String?)

  private abstract class SimpleLeafCollector : LeafCollector {
    override var scorer: Scorable? = null
  }

  private class SimpleCollector : Collector {
    override var weight: Weight? = null

    private val scoreMode: ScoreMode
    val hits: MutableList<CollectedResult> = ArrayList()

    constructor(scoreMode: ScoreMode) {
      this.scoreMode = scoreMode
    }

    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {

      var docBase: Int = context.docBase
      var idVals: SortedDocValues = DocValues.getSorted(context.reader(), "id")

      return object : SimpleLeafCollector() {

        override fun collect(doc: Int) {
          var score: Float = scorer!!.score()
          var id: String? = null
          if (idVals.advanceExact(doc)) {
            id = idVals.lookupOrd(idVals.ordValue())!!.utf8ToString()
          }
          hits.add(CollectedResult(DocAndScore(docBase + doc, score), id))
        }
      }
    }

    override fun scoreMode(): ScoreMode {
      return scoreMode
    }
  }

  private class SimpleCollectorManager : CollectorManager<SimpleCollector, List<DocAndScore>> {
    private val numDocs: Int
    private val comparator: Comparator<CollectedResult>
    private val scoreMode: ScoreMode

    constructor(numDocs: Int, comparator: Comparator<CollectedResult>) : this(numDocs, comparator, ScoreMode.COMPLETE) {
    }

    constructor(numDocs: Int, comparator: Comparator<CollectedResult>, scoreMode: ScoreMode) {
      this.numDocs = numDocs
      this.comparator = comparator
      this.scoreMode = scoreMode
    }

    override fun newCollector(): SimpleCollector {
      return SimpleCollector(scoreMode)
    }

    override fun reduce(collectors: MutableCollection<SimpleCollector>): List<DocAndScore> {
      var all: MutableList<CollectedResult> = ArrayList()
      for (c in collectors) {
        all.addAll(c.hits)
      }

      // Just sorts using the provided comparator returns up to 'numDocs'.
      // This is an inefficient way to go about doing this, but it's only for unit testing.
      return all.sortedWith(comparator).asSequence()
          .map { cr -> DocAndScore(cr.docAndScore) }
          .take(numDocs)
          .toList()
    }
  }

  // We can't use AssertingIndexSearcher unfortunately since it may randomly decide to bulk
  // score a sub-range of docs instead of all docs at once. This is incompatible will drill
  // sideways, so we have to do our own check here. This just makes sure we call #finish on
  // the last leaf. It's too bad we need to do this and maybe some day we can clean this up
  // by rethinking drill-sideways:
  private class DrillSidewaysAssertingIndexSearcher(r: IndexReader) : IndexSearcher(r) {



    override protected fun search(partitions: Array<LeafReaderContextPartition>, weight: Weight, collector: Collector) {
      var assertingCollector: AssertingCollector = AssertingCollector.wrap(collector)
      super.search(partitions, weight, assertingCollector)
      assertTrue(assertingCollector.hasFinishedCollectingPreviousLeaf)
    }
  }

  private class AssertingFacetsCollectorManager : FacetsCollectorManager() {
    override fun newCollector(): FacetsCollector {
      return AssertingFacetsCollector()
    }

    override fun reduce(collectors: MutableCollection<FacetsCollector>): FacetsCollector {
      for (fc in collectors) {
        assertTrue(fc is AssertingFacetsCollector)
        assertTrue((fc as AssertingFacetsCollector).hasFinishedLastLeaf == true)
      }
      return super.reduce(collectors)
    }
  }

  private class AssertingFacetsCollector : FacetsCollector() {
    var hasFinishedLastLeaf: Boolean = true

    override protected fun doSetNextReader(context: LeafReaderContext) {
      assertTrue(hasFinishedLastLeaf)
      hasFinishedLastLeaf = false
      super.doSetNextReader(context)
    }

    override fun finish() {
      hasFinishedLastLeaf = true
      super.finish()
    }
  }

  private fun getTopNOrds(counts: IntArray, values: Array<String>, topN: Int): IntArray {
    var topN = topN
    val ids: IntArray = IntArray(counts.size)
    for (i in 0 until ids.size) {
      ids[i] = i
    }

    // Naive (on purpose, to reduce bug in tester/gold):
    // sort all ids, then return top N slice:
    object : InPlaceMergeSorter() {

      override protected fun swap(i: Int, j: Int) {
        var id: Int = ids[i]
        ids[i] = ids[j]
        ids[j] = id
      }

      override protected fun compare(i: Int, j: Int): Int {
        var counti: Int = counts[ids[i]]
        var countj: Int = counts[ids[j]]
        // Sort by count descending...
        if (counti > countj) {
          return -1
        } else if (counti < countj) {
          return 1
        } else {
          // ... then by label ascending:
          return BytesRef(values[ids[i]]).compareTo(BytesRef(values[ids[j]]))
        }
      }
    }.sort(0, ids.size)

    if (topN > ids.size) {
      topN = ids.size
    }

    var numSet: Int = topN
    for (i in 0 until topN) {
      if (counts[ids[i]] == 0) {
        numSet = i
        break
      }
    }

    var topNIDs: IntArray = IntArray(numSet)
    ids.copyInto(topNIDs, 0, 0, topNIDs.size)
    return topNIDs
  }

  private fun slowDrillSidewaysSearch(s: IndexSearcher, docs: List<Doc>, contentToken: String?, drillDowns: Array<Array<String>?>, dimValues: Array<Array<String>>, onlyEven: Query?): TestFacetResult {
    var numDims: Int = dimValues.size

    var hits: MutableList<Doc> = ArrayList()
    var drillDownCounts: Counters = Counters(dimValues)
    var drillSidewaysCounts: Array<Counters> = Array(dimValues.size) { Counters(dimValues) }
    for (dim in 0 until numDims) {
      drillSidewaysCounts[dim] = Counters(dimValues)
    }

    if (VERBOSE) {
      println("  compute expected")
    }

    nextDoc@
    for (doc in docs) {
      if (doc.deleted) {
        continue
      }
      if ((onlyEven != null) and ((doc.id.toInt() and 1) != 0)) {
        continue
      }
      if (contentToken == null || doc.contentToken.equals(contentToken)) {
        var failDim: Int = -1
        for (dim in 0 until numDims) {
          if (drillDowns[dim] != null) {
            var docValue: String? = if (doc.dims[dim] == -1) null else dimValues[dim][doc.dims[dim]]
            var docValue2: String? = if (doc.dims2[dim] == -1) null else dimValues[dim][doc.dims2[dim]]
            var matches: Boolean = false
            for (value in drillDowns[dim]!!) {
              if (value.equals(docValue) || value.equals(docValue2)) {
                matches = true
                break
              }
            }
            if (!matches) {
              if (failDim == -1) {
                // Doc could be a near-miss, if no other dim fails
                failDim = dim
              } else {
                // Doc isn't a hit nor a near-miss
                continue@nextDoc
              }
            }
          }
        }

        if (failDim == -1) {
          if (VERBOSE) {
            println("    exp: id=" + doc.id + " is a hit")
          }
          // Hit:
          hits.add(doc)
          drillDownCounts.inc(doc.dims, doc.dims2)
          for (dim in 0 until dimValues.size) {
            drillSidewaysCounts[dim].inc(doc.dims, doc.dims2)
          }
        } else {
          if (VERBOSE) {
            println("    exp: id=" + doc.id + " is a near-miss on dim=" + failDim)
          }
          drillSidewaysCounts[failDim].inc(doc.dims, doc.dims2, failDim)
        }
      }
    }

    var idToDocID: MutableMap<String, Int> = HashMap()
    for (i in 0 until s.indexReader.maxDoc()) {
      idToDocID.put(s.storedFields().document(i).get("id")!!, i)
    }

    hits.sort()

    var res: TestFacetResult = TestFacetResult()
    res.hits = hits
    res.counts = Array(numDims) { IntArray(0) }
    res.uniqueCounts = IntArray(numDims)
    for (dim in 0 until numDims) {
      if (drillDowns[dim] != null) {
        res.counts[dim] = drillSidewaysCounts[dim].counts[dim]
      } else {
        res.counts[dim] = drillDownCounts.counts[dim]
      }
      var uniqueCount: Int = 0
      for (j in 0 until res.counts[dim].size) {
        if (res.counts[dim][j] != 0) {
          uniqueCount++
        }
      }
      res.uniqueCounts[dim] = uniqueCount
    }

    return res
  }

  private fun verifyEquals(dimValues: Array<Array<String>>, s: IndexSearcher, expected: TestFacetResult, actual: DSResults, scores: MutableMap<String, Float>, isSortedSetDV: Boolean) {
    if (VERBOSE) {
      println("  verify totHits=" + expected.hits.size)
    }
    assertEquals(expected.hits.size.toLong(), actual.resultCount.value)
    assertEquals(expected.hits.size, actual.results.size)
    for (i in 0 until expected.hits.size) {
      if (VERBOSE) {
        println("    hit " + i + " expected=" + expected.hits.get(i).id)
      }
      assertEquals(
          expected.hits.get(i).id, s.storedFields().document(actual.results.get(i).doc).get("id"))
      // Score should be IDENTICAL:
      assertEquals(scores.get(expected.hits.get(i).id)!!, actual.results.get(i).score, 0.0f)
    }

    for (dim in 0 until expected.counts.size) {
      var topN: Int =
          if (random().nextBoolean()) dimValues[dim].size
              else TestUtil.nextInt(random(), 1, dimValues[dim].size)
      var fr: FacetResult? = actual.facets!!.getTopChildren(topN, "dim" + dim)
      if (VERBOSE) {
        println(
            "    dim"
                + dim
                + " topN="
                + topN
                + " (vs "
                + dimValues[dim].size
                + " unique values)")
        println("      actual")
      }

      var idx: Int = 0
      var actualValues: MutableMap<String, Int> = HashMap()

      if (fr != null) {
        for (labelValue in fr.labelValues) {
          actualValues.put(labelValue.label, labelValue.value.toInt())
          if (VERBOSE) {
            println(
                "        " + idx + ": " + BytesRef(labelValue.label) + ": " + labelValue.value)
            idx++
          }
        }
        assertEquals(expected.uniqueCounts[dim], fr.childCount, "dim=" + dim)
      }

      if (topN < dimValues[dim].size) {
        var topNIDs: IntArray = getTopNOrds(expected.counts[dim], dimValues[dim], topN)
        if (VERBOSE) {
          idx = 0
          println("      expected (sorted)")
          for (expectedOrd in topNIDs) {
            var value: String = dimValues[dim][expectedOrd]
            println(
                "        "
                    + idx
                    + ": "
                    + BytesRef(value)
                    + ": "
                    + expected.counts[dim][expectedOrd])
            idx++
          }
        }
        if (VERBOSE) {
          println("      topN=" + topN + " expectedTopN=" + topNIDs.size)
        }

        if (fr != null) {
          assertEquals(topNIDs.size, fr.labelValues.size)
        } else {
          assertEquals(0, topNIDs.size)
        }
        for (i in 0 until topNIDs.size) {
          var expectedOrd: Int = topNIDs[i]
          assertEquals(expected.counts[dim][expectedOrd], fr!!.labelValues[i].value.toInt())
          if (isSortedSetDV) {
            // Tie-break facet labels are only in unicode
            // order with SortedSetDVFacets:
            assertEquals(dimValues[dim][expectedOrd], fr!!.labelValues[i].label, "value @ idx=" + i)
          }
        }
      } else {

        if (VERBOSE) {
          idx = 0
          println("      expected (unsorted)")
          for (i in 0 until dimValues[dim].size) {
            var value: String = dimValues[dim][i]
            if (expected.counts[dim][i] != 0) {
              println(
                  "        " + idx + ": " + BytesRef(value) + ": " + expected.counts[dim][i])
              idx++
            }
          }
        }

        var setCount: Int = 0
        for (i in 0 until dimValues[dim].size) {
          var value: String = dimValues[dim][i]
          if (expected.counts[dim][i] != 0) {
            assertTrue(actualValues.containsKey(value))
            assertEquals(expected.counts[dim][i], actualValues.get(value)!!.toInt())
            setCount++
          } else {
            assertFalse(actualValues.containsKey(value))
          }
        }
        assertEquals(setCount, actualValues.size)
      }
    }
  }

  @Test

  fun testEmptyIndex() {
    // LUCENE-5045: make sure DrillSideways works with an empty index
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()
    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)
    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)
    var searcher: IndexSearcher = getNewSearcher(writer.reader)
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)

    // Count "Author"
    var config: FacetsConfig = FacetsConfig()
    var ds: DrillSideways = getNewDrillSideways(searcher, config, taxoReader)
    var ddq: DrillDownQuery = DrillDownQuery(config)
    ddq.add("Author", "Lisa")

    var r: DrillSidewaysResult = ds.search(ddq, 10) // this used to fail on IllegalArgEx
    assertEquals(0L, r.hits!!.totalHits.value)

    r =
        ds.search(
            ddq,
            null,
            null,
            10,
            Sort(SortField("foo", SortField.Type.INT)),
            false) // this used to fail on IllegalArgEx
    assertEquals(0L, r.hits!!.totalHits.value)

    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, dir, taxoDir)
  }

  @Test

  fun testNoDrillDownFacetCollection() {
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()
    config.setHierarchical("Publish Date", true)

    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var doc: Document = Document()
    doc.add(FacetField("Author", "Bob"))
    doc.add(FacetField("Publish Date", "2010", "10", "15"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2010", "10", "20"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2012", "1", "1"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Susan"))
    doc.add(FacetField("Publish Date", "2012", "1", "7"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(FacetField("Author", "Frank"))
    doc.add(FacetField("Publish Date", "1999", "5", "5"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher: IndexSearcher = getNewSearcher(writer.reader)

    // NRT open
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)

    // Sometimes pass an ExecutorService to test both paths for providing null drill down collectors
    // The actual configuration of the ExecutorService doesn't matter at all:
    var executorService: ExecutorService? = null
    if (random().nextBoolean()) {
      executorService = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(), NamedThreadFactory("ds_test"))
    }

    // Don't collect for drill down faceting
    var ds: DrillSideways =
        object : DrillSideways(searcher, config, taxoReader, null, executorService) {
          override protected fun createDrillDownFacetsCollectorManager(): FacetsCollectorManager? {
            return null
          }
        }

    // Another simple case: drill-down on single fields
    // but OR of two values
    var ddq: DrillDownQuery = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Author", "Bob")
    var r: DrillSidewaysResult = ds.search(null, ddq, 10)
    var facets: Facets = r.facets!!
    assertEquals(3L, r.hits!!.totalHits.value)
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        facets.getTopChildren(10, "Author").toString())
    // Because we don't collect drill-downs, we shouldn't be able to get counts for Publish Date
    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(10, "Publish Date") }

    assertTrue(facets is MultiFacets)
    var allResults: List<FacetResult> = facets.getAllDims(10)
    // Should only have the one dimension because we didn't collect for drill down
    assertEquals(1, allResults.size)
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        allResults.get(0).toString())

    // test default implementation of getTopDims
    var topNDimsResult: List<FacetResult> = facets.getTopDims(1, 2)
    assertEquals(1, topNDimsResult.size)
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n",
        topNDimsResult.get(0).toString())

    // test getAllDims(0)
    expectThrows(IllegalArgumentException::class) { facets.getAllDims(0) }
    // More interesting case: drill-down on two fields
    ddq = DrillDownQuery(config)
    ddq.add("Author", "Lisa")
    ddq.add("Publish Date", "2010")
    r = ds.search(null, ddq, 10)
    assertEquals(1L, r.hits!!.totalHits.value)
    // Should be able to count on both fields since they're both drill sideways cases
    assertEquals(
        "dim=Publish Date path=[] value=2 childCount=2\n  2010 (1)\n  2012 (1)\n",
        r.facets!!.getTopChildren(10, "Publish Date").toString())
    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)\n",
        r.facets!!.getTopChildren(10, "Author").toString())

    if (executorService != null) {
      executorService.shutdown()
    }
    writer.close()
    IOUtils.close(searcher.indexReader, taxoReader, taxoWriter, dir, taxoDir)
  }

  @Test

  fun testScorer() {
    // LUCENE-6001 some scorers, e.g. ReqExlScorer, can hit NPE if cost is called after nextDoc
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()

    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var doc: Document = Document()
    doc.add(newTextField("field", "foo bar", Field.Store.NO))
    doc.add(FacetField("Author", "Bob"))
    doc.add(FacetField("dim", "a"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher: IndexSearcher = getNewSearcher(writer.reader)

    // NRT open
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)

    var ds: DrillSideways = getNewDrillSideways(searcher, config, taxoReader)

    var bq: BooleanQuery.Builder = BooleanQuery.Builder()
    bq.add(TermQuery(Term("field", "foo")), BooleanClause.Occur.MUST)
    bq.add(TermQuery(Term("field", "bar")), BooleanClause.Occur.MUST_NOT)
    var ddq: DrillDownQuery = DrillDownQuery(config, bq.build())
    ddq.add("field", "foo")
    ddq.add("author", bq.build())
    ddq.add("dim", bq.build())
    var r: DrillSidewaysResult = ds.search(null, ddq, 10)
    assertEquals(0L, r.hits!!.totalHits.value)

    writer.close()
    IOUtils.close(searcher.indexReader, taxoReader, taxoWriter, dir, taxoDir)
  }

  @Test

  fun testExtendedDrillSidewaysResult() {
    // LUCENE-9945: Extend DrillSideways to support exposing FacetCollectors directly
    var dir: Directory = newDirectory()
    var taxoDir: Directory = newDirectory()

    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var taxoWriter: DirectoryTaxonomyWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()
    config.setHierarchical("dim", true)

    var doc: Document = Document()
    doc.add(FacetField("dim", "a"))
    writer.addDocument(config.build(taxoWriter, doc))

    var doc2: Document = Document()
    doc.add(FacetField("dim", "x"))
    writer.addDocument(config.build(taxoWriter, doc2))

    // open NRT
    var searcher: IndexSearcher = getNewSearcher(writer.reader)
    var taxoReader: TaxonomyReader = DirectoryTaxonomyReader(taxoWriter)

    var ddq: DrillDownQuery = DrillDownQuery(config)
    ddq.add("dim", "x")

    var ds: DrillSideways = getNewDrillSidewaysBuildFacetsResult(searcher, config, taxoReader)

    var manager: SimpleCollectorManager =
        SimpleCollectorManager(
            10, Comparator { a, b -> b.docAndScore.score.compareTo(a.docAndScore.score) })

    var r: DrillSidewaysResult = ds.search(ddq, manager)

    // compute Facets using exposed FacetCollectors from DrillSidewaysResult
    var drillSidewaysFacets: MutableMap<String, Facets> = HashMap()
    var drillDownFacets: Facets = getTaxonomyFacetCounts(taxoReader, config, r.drillDownFacetsCollector!!)
    if (r.drillSidewaysFacetsCollector != null) {
      for (i in 0 until r.drillSidewaysFacetsCollector.size) {
        drillSidewaysFacets.put(
            r.drillSidewaysDims!![i],
            getTaxonomyFacetCounts(taxoReader, config, r.drillSidewaysFacetsCollector[i]))
      }
    }

    lateinit var facets: Facets
    if (drillSidewaysFacets.isEmpty()) {
      facets = drillDownFacets
    } else {
      facets = MultiFacets(drillSidewaysFacets, drillDownFacets)
    }

    // Facets computed using FacetsCollector exposed in DrillSidewaysResult
    // should match the Facets computed by {@link DrillSideways#buildFacetsResult}
    var facetResultActual: FacetResult = facets.getTopChildren(2, "dim")!!
    var facetResultExpected: FacetResult = r.facets!!.getTopChildren(2, "dim")!!

    assertEquals(facetResultExpected.dim, facetResultActual.dim)
    assertEquals(facetResultExpected.path.size, facetResultActual.path.size)
    assertEquals(facetResultExpected.value, facetResultActual.value)
    assertEquals(facetResultExpected.childCount, facetResultActual.childCount)

    writer.close()
    IOUtils.close(searcher.indexReader, taxoReader, taxoWriter, dir, taxoDir)
  }

  @Test
  fun testDrillSidewaysSearchUseCorrectIterator() {
    // This test reproduces an issue (see GitHub #12211) where DrillSidewaysScorer would ultimately
    // cause multiple consecutive calls to TwoPhaseIterator::matches, which results in a failed
    // assert in the PostingsReaderBase implementation (or a failing to match a document that should
    // have matched, if asserts are disabled).
    var dir: Directory = newDirectory()
    var iwc = IndexWriterConfig(StandardAnalyzer())
    var indexWriter = IndexWriter(dir, iwc)
    var taxoDir = newDirectory()
    var taxonomyWriter = DirectoryTaxonomyWriter(taxoDir)
    var facetsConfig = FacetsConfig()
    facetsConfig.setRequireDimCount("dim1", true)
    facetsConfig.setDrillDownTermsIndexing("dim1", FacetsConfig.DrillDownTermsIndexing.ALL)
    // Add a doc that we'll try to match
    var doc = Document()
    doc.add(TextField("content", "bt tv v1 1b b1 10 04 40 08 81 14 48", Field.Store.NO))
    doc.add(FacetField("dim1", "dim1"))
    indexWriter.addDocument(facetsConfig.build(taxonomyWriter, doc))
    // Add some more docs as filler in the index
    for (i in 0 until 25) {
      var fillerDoc = Document()
      fillerDoc.add(TextField("content", "content", Field.Store.NO))
      fillerDoc.add(FacetField("dim1", "dim1"))
      indexWriter.addDocument(facetsConfig.build(taxonomyWriter, fillerDoc))
    }
    taxonomyWriter.commit()
    indexWriter.commit()
    var taxonomyReader = DirectoryTaxonomyReader(taxoDir)
    var indexReader = DirectoryReader.open(indexWriter)
    var searcher = IndexSearcher(indexReader)
    var drill = DrillSideways(searcher, facetsConfig, taxonomyReader)
    var drillDownQuery =
        DrillDownQuery(
            facetsConfig,
            PhraseQuery(
                "content", "bt", "tv", "v1", "1b", "b1", "10", "04", "40", "08", "81", "14", "48"))
    drillDownQuery.add("dim1", "dim1")
    var result = drill.search(drillDownQuery, 99)
    // We expect to match exactly one document from the query above
    assertEquals(1L, result.hits!!.totalHits.value)

    indexReader.close()
    taxonomyReader.close()
    taxonomyWriter.close()
    indexWriter.close()
    dir.close()
    taxoDir.close()
  }
}
