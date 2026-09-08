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
package org.gnit.lucenekmp.facet.taxonomy

import kotlin.test.*

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetResult
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.Facets
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollectorManager
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.LabelAndValue
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queries.function.FunctionQuery
import org.gnit.lucenekmp.queries.function.valuesource.LongFieldSource
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.ConstantScoreQuery
import org.gnit.lucenekmp.search.DoubleValuesSource
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils

class TestTaxonomyFacetValueSource : FacetTestCase() {

  @Test
  fun testBasic() {

    var dir = newDirectory()
    var taxoDir = newDirectory()

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var writer = RandomIndexWriter(random(), dir)
    var config = FacetsConfig()

    // Reused across documents, to add the necessary facet
    // fields:
    var doc = Document()
    doc.add(NumericDocValuesField("num", 10))
    doc.add(FacetField("Author", "Bob"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(NumericDocValuesField("num", 20))
    doc.add(FacetField("Author", "Lisa"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(NumericDocValuesField("num", 30))
    doc.add(FacetField("Author", "Lisa"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(NumericDocValuesField("num", 40))
    doc.add(FacetField("Author", "Susan"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(NumericDocValuesField("num", 45))
    doc.add(FacetField("Author", "Frank"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)
    writer.close()

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)
    taxoWriter.close()

    // MatchAllDocsQuery is for "browsing" (counts facets
    // for all non-deleted docs in the index); normally
    // you'd use a "normal" query and one of the
    // Facets.search utility methods:
    var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facetsConfig = FacetsConfig()
    var valuesSource = DoubleValuesSource.fromIntField("num")

    // Test SUM:
    var facets =
        TaxonomyFacetFloatAssociations(
            taxoReader, facetsConfig, c!!, AssociationAggregationFunction.SUM, valuesSource)
    // Retrieve & verify results:
    assertEquals(
        "dim=Author path=[] value=145.0 childCount=4\n  Lisa (50.0)\n  Frank (45.0)\n  Susan (40.0)\n  Bob (10.0)\n",
        facets.getTopChildren(10, "Author").toString())

    // Test MAX:
    facets =
        TaxonomyFacetFloatAssociations(
            taxoReader, facetsConfig, c!!, AssociationAggregationFunction.MAX, valuesSource)
    assertEquals(
        "dim=Author path=[] value=45.0 childCount=4\n  Frank (45.0)\n  Susan (40.0)\n  Lisa (30.0)\n  Bob (10.0)\n",
        facets.getTopChildren(10, "Author").toString())

    // test getTopChildren(0, dim)
    var f = facets
    expectThrows(IllegalArgumentException::class) { f.getTopChildren(0, "Author") }

    taxoReader.close()
    searcher.indexReader.close()
    dir.close()
    taxoDir.close()
  }

  // LUCENE-5333
  @Test
  fun testSparseFacets() {
    var dir = newDirectory()
    var taxoDir = newDirectory()

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var writer = RandomIndexWriter(random(), dir)
    var config = FacetsConfig()

    var doc = Document()
    doc.add(NumericDocValuesField("num", 10))
    doc.add(FacetField("a", "foo1"))
    writer.addDocument(config.build(taxoWriter, doc))

    if (random().nextBoolean()) {
      writer.commit()
    }

    doc = Document()
    doc.add(NumericDocValuesField("num", 20))
    doc.add(FacetField("a", "foo2"))
    doc.add(FacetField("b", "bar1"))
    writer.addDocument(config.build(taxoWriter, doc))

    if (random().nextBoolean()) {
      writer.commit()
    }

    doc = Document()
    doc.add(NumericDocValuesField("num", 30))
    doc.add(FacetField("a", "foo3"))
    doc.add(FacetField("b", "bar2"))
    doc.add(FacetField("c", "baz1"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)
    writer.close()

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)
    taxoWriter.close()

    var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facets =
        TaxonomyFacetFloatAssociations(
            taxoReader,
            FacetsConfig(),
            c!!,
            AssociationAggregationFunction.SUM,
            DoubleValuesSource.fromIntField("num"))

    // Ask for top 10 labels for any dims that have counts:
    var results = facets.getAllDims(10)

    // test getAllDims(0)
    expectThrows(IllegalArgumentException::class) { facets.getAllDims(0) }

    assertEquals(3, results.size)
    assertEquals(
        "dim=a path=[] value=60.0 childCount=3\n  foo3 (30.0)\n  foo2 (20.0)\n  foo1 (10.0)\n",
        results.get(0).toString())
    assertEquals(
        "dim=b path=[] value=50.0 childCount=2\n  bar2 (30.0)\n  bar1 (20.0)\n",
        results.get(1).toString())
    assertEquals(
        "dim=c path=[] value=30.0 childCount=1\n  baz1 (30.0)\n", results.get(2).toString())

    // test default implementation of getTopDims
    var topNDimsResult = facets.getTopDims(2, 1)
    assertEquals(2, topNDimsResult.size)
    assertEquals(
        "dim=a path=[] value=60.0 childCount=3\n  foo3 (30.0)\n", topNDimsResult.get(0).toString())
    assertEquals(
        "dim=b path=[] value=50.0 childCount=2\n  bar2 (30.0)\n", topNDimsResult.get(1).toString())

    // test getTopDims(10, 10) and expect same results from getAllDims(10)
    var allDimsResults = facets.getTopDims(10, 10)
    assertEquals(results, allDimsResults)

    // test getTopDims(0, 1)
    expectThrows(IllegalArgumentException::class) { facets.getTopDims(0, 1) }

    // test getTopDims(1, 0) with topNChildren = 0
    expectThrows(IllegalArgumentException::class) { facets.getTopDims(1, 0) }

    IOUtils.close(searcher.indexReader, taxoReader, dir, taxoDir)
  }

  @Test
  fun testWrongIndexFieldName() {

    var dir = newDirectory()
    var taxoDir = newDirectory()

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config = FacetsConfig()
    config.setIndexFieldName("a", "\$facets2")

    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(NumericDocValuesField("num", 10))
    doc.add(FacetField("a", "foo1"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)
    writer.close()

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)
    taxoWriter.close()

    var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facets =
        TaxonomyFacetFloatAssociations(
            taxoReader,
            config,
            c!!,
            AssociationAggregationFunction.SUM,
            DoubleValuesSource.fromIntField("num"))

    // Ask for top 10 labels for any dims that have counts:
    var results = facets.getAllDims(10)
    assertTrue(results.isEmpty())

    // test default implementation of getTopDims
    var topDimsResults = facets.getTopDims(10, 10)
    assertTrue(topDimsResults.isEmpty())
    expectThrows(IllegalArgumentException::class) { facets.getSpecificValue("a") }

    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(10, "a") }

    IOUtils.close(searcher.indexReader, taxoReader, dir, taxoDir)
  }

  @Test
  fun testScoreAggregator() {
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))

    var config = FacetsConfig()

    for (i in atLeast(30) downTo 1) {
      var doc = Document()
      if (random().nextBoolean()) { // don't match all documents
        doc.add(StringField("f", "v", Field.Store.NO))
      }
      doc.add(FacetField("dim", "a"))
      iw.addDocument(config.build(taxoWriter, doc))
    }

    var r = DirectoryReader.open(iw)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var csq = BoostQuery(ConstantScoreQuery(MatchAllDocsQuery()), 2f)

    var facetsResult =
        FacetsCollectorManager.search(newSearcher(r), csq, 10, FacetsCollectorManager(true))
    var td = facetsResult.topDocs
    var fc = facetsResult.facetsCollector
    assertTrue(fc.getKeepScores())

    // Test SUM:
    var facets =
        TaxonomyFacetFloatAssociations(
            taxoReader, config, fc, AssociationAggregationFunction.SUM, DoubleValuesSource.SCORES)
    var expected = (csq.boost * td.totalHits.value).toInt()
    assertEquals(expected, facets.getSpecificValue("dim", "a").toInt())

    // Test MAX:
    facets =
        TaxonomyFacetFloatAssociations(
            taxoReader, config, fc, AssociationAggregationFunction.MAX, DoubleValuesSource.SCORES)
    expected = csq.boost.toInt()
    assertEquals(expected, facets.getSpecificValue("dim", "a").toInt())

    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  @Test
  fun testNoScore() {
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var config = FacetsConfig()
    for (i in 0 until 4) {
      var doc = Document()
      doc.add(NumericDocValuesField("price", (i + 1).toLong()))
      doc.add(FacetField("a", (i % 2).toString()))
      iw.addDocument(config.build(taxoWriter, doc))
    }

    var r = DirectoryReader.open(iw)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var sfc =
        newSearcher(r).search(MatchAllDocsQuery(), FacetsCollectorManager())

    // Test SUM:
    var facets =
        TaxonomyFacetFloatAssociations(
            taxoReader,
            config,
            sfc!!,
            AssociationAggregationFunction.SUM,
            DoubleValuesSource.fromLongField("price"))
    assertEquals(
        "dim=a path=[] value=10.0 childCount=2\n  1 (6.0)\n  0 (4.0)\n",
        facets.getTopChildren(10, "a").toString())

    // Test MAX:
    facets =
        TaxonomyFacetFloatAssociations(
            taxoReader,
            config,
            sfc!!,
            AssociationAggregationFunction.MAX,
            DoubleValuesSource.fromLongField("price"))
    assertEquals(
        "dim=a path=[] value=4.0 childCount=2\n  1 (4.0)\n  0 (3.0)\n",
        facets.getTopChildren(10, "a").toString())

    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  @Test
  fun testWithScore() {
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))

    var config = FacetsConfig()

    // Add a doc without the price field to exercise the bug found in LUCENE-10491:
    var doc = Document()
    iw.addDocument(config.build(taxoWriter, doc))

    for (i in 0 until 4) {
      doc = Document()
      doc.add(NumericDocValuesField("price", (i + 1).toLong()))
      doc.add(FacetField("a", (i % 2).toString()))
      iw.addDocument(config.build(taxoWriter, doc))
    }

    var r = DirectoryReader.open(iw)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    // score documents by their 'price' field - makes asserting the correct counts for the
    // categories easier
    var q = FunctionQuery(LongFieldSource("price"))
    var fc =
        FacetsCollectorManager.search(newSearcher(r), q, 10, FacetsCollectorManager(true))
            .facetsCollector
    assertTrue(fc.getKeepScores())

    // Test SUM:
    var facets =
        TaxonomyFacetFloatAssociations(
            taxoReader, config, fc, AssociationAggregationFunction.SUM, DoubleValuesSource.SCORES)
    assertEquals(
        "dim=a path=[] value=10.0 childCount=2\n  1 (6.0)\n  0 (4.0)\n",
        facets.getTopChildren(10, "a").toString())

    // Test MAX:
    facets =
        TaxonomyFacetFloatAssociations(
            taxoReader, config, fc, AssociationAggregationFunction.MAX, DoubleValuesSource.SCORES)
    assertEquals(
        "dim=a path=[] value=4.0 childCount=2\n  1 (4.0)\n  0 (3.0)\n",
        facets.getTopChildren(10, "a").toString())

    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  @Test
  fun testRollupValues() {
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var config = FacetsConfig()
    config.setHierarchical("a", true)
    // config.setRequireDimCount("a", true);

    for (i in 0 until 4) {
      var doc = Document()
      doc.add(NumericDocValuesField("price", (i + 1).toLong()))
      doc.add(FacetField("a", (i % 2).toString(), "1"))
      iw.addDocument(config.build(taxoWriter, doc))
    }

    var r = DirectoryReader.open(iw)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var sfc =
        newSearcher(r).search(MatchAllDocsQuery(), FacetsCollectorManager())
    // Test SUM:
    var facets =
        TaxonomyFacetFloatAssociations(
            taxoReader,
            config,
            sfc!!,
            AssociationAggregationFunction.SUM,
            DoubleValuesSource.fromLongField("price"))
    assertEquals(
        "dim=a path=[] value=10.0 childCount=2\n  1 (6.0)\n  0 (4.0)\n",
        facets.getTopChildren(10, "a").toString())

    // Test MAX:
    facets =
        TaxonomyFacetFloatAssociations(
            taxoReader,
            config,
            sfc!!,
            AssociationAggregationFunction.MAX,
            DoubleValuesSource.fromLongField("price"))
    assertEquals(
        "dim=a path=[] value=4.0 childCount=2\n  1 (4.0)\n  0 (3.0)\n",
        facets.getTopChildren(10, "a").toString())

    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  // LUCENE-10495
  @Test
  fun testChildrenAndSiblingsLoaded() {
    val shouldLoad = booleanArrayOf(false, true)
    for (load in shouldLoad) {
      var indexDir = newDirectory()
      var taxoDir = newDirectory()

      var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
      var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
      var config = FacetsConfig()

      config.setHierarchical("a", true)
      config.setMultiValued("a", load == false)
      config.setRequireDimCount("a", true)

      var doc = Document()
      doc.add(FacetField("a", "1", "2"))
      iw.addDocument(config.build(taxoWriter, doc))

      var r = DirectoryReader.open(iw)
      var taxoReader = DirectoryTaxonomyReader(taxoWriter)

      var sfc =
          newSearcher(r).search(MatchAllDocsQuery(), FacetsCollectorManager())

      var facets =
          TaxonomyFacetFloatAssociations(
              taxoReader,
              config,
              sfc!!,
              AssociationAggregationFunction.MAX,
              DoubleValuesSource.fromLongField("price"))

      assertEquals(load, facets.childrenLoaded())
      assertEquals(load, facets.siblingsLoaded())

      iw.close()
      IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
    }
  }

  @Test
  fun testCountAndSumScore() {
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var config = FacetsConfig()
    config.setIndexFieldName("b", "\$b")

    for (i in atLeast(30) downTo 1) {
      var doc = Document()
      doc.add(StringField("f", "v", Field.Store.NO))
      doc.add(FacetField("a", "1"))
      doc.add(FacetField("b", "1"))
      iw.addDocument(config.build(taxoWriter, doc))
    }

    var r = DirectoryReader.open(iw)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var fc =
        FacetsCollectorManager.search(
                newSearcher(r), MatchAllDocsQuery(), 10, FacetsCollectorManager(true))
            .facetsCollector
    assertTrue(fc.getKeepScores())

    var facets1 = getTaxonomyFacetCounts(taxoReader, config, fc)
    var facets2 =
        TaxonomyFacetFloatAssociations(
            "\$b",
            taxoReader,
            config,
            fc,
            AssociationAggregationFunction.SUM,
            DoubleValuesSource.SCORES)

    assertEquals(r.maxDoc(), facets1.getTopChildren(10, "a")!!.value.toInt())
    assertEquals(r.maxDoc().toDouble(), facets2.getTopChildren(10, "b")!!.value.toDouble(), 1E-10)
    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  @Test
  fun testRandom() {
    var tokens = getRandomTokens(10)
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var w = RandomIndexWriter(random(), indexDir)
    var tw = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    var numDocs = atLeast(1000)
    var numDims = TestUtil.nextInt(random(), 1, 7)
    var testDocs = getRandomDocs(tokens, numDocs, numDims)
    for (testDoc in testDocs) {
      var doc = Document()
      doc.add(newStringField("content", testDoc.content, Field.Store.NO))
      testDoc.value = random().nextFloat()
      doc.add(FloatDocValuesField("value", testDoc.value))
      for (j in 0 until numDims) {
        if (testDoc.dims[j] != null) {
          doc.add(FacetField("dim" + j, testDoc.dims[j]!!))
        }
      }
      w.addDocument(config.build(tw, doc))
    }

    // NRT open
    var searcher = newSearcher(w.reader)

    // NRT open
    var tr = DirectoryTaxonomyReader(tw)

    var iters = atLeast(100)
    for (iter in 0 until iters) {
      var searchToken = tokens[random().nextInt(tokens.size)]
      if (VERBOSE) {
        println("\nTEST: iter content=" + searchToken)
      }
      var fc =
          FacetsCollectorManager.search(
                  searcher,
                  TermQuery(Term("content", searchToken)),
                  10,
                  FacetsCollectorManager())
              .facetsCollector

      checkResults(
          numDims,
          testDocs,
          searchToken,
          tr,
          config,
          fc,
          DoubleValuesSource.fromFloatField("value"),
          AssociationAggregationFunction.SUM)
      checkResults(
          numDims,
          testDocs,
          searchToken,
          tr,
          config,
          fc,
          DoubleValuesSource.fromFloatField("value"),
          AssociationAggregationFunction.MAX)
    }

    w.close()
    IOUtils.close(tw, searcher.indexReader, tr, indexDir, taxoDir)
  }

  private fun checkResults(
      numDims: Int,
      testDocs: List<TestDoc>,
      searchToken: String,
      taxoReader: TaxonomyReader,
      facetsConfig: FacetsConfig,
      facetsCollector: FacetsCollector,
      valuesSource: DoubleValuesSource,
      aggregationFunction: AssociationAggregationFunction) {
    // Slow, yet hopefully bug-free, faceting:
    val expectedValues = Array(numDims) { HashMap<String, Float>() }
    for (i in 0 until numDims) {
      expectedValues[i] = HashMap()
    }

    for (doc in testDocs) {
      if (doc.content.equals(searchToken)) {
        for (j in 0 until numDims) {
          if (doc.dims[j] != null) {
            var v = expectedValues[j].get(doc.dims[j])
            if (v == null) {
              expectedValues[j].put(doc.dims[j]!!, doc.value)
            } else {
              var newValue = aggregationFunction.aggregate(v, doc.value)
              expectedValues[j].put(doc.dims[j]!!, newValue)
            }
          }
        }
      }
    }

    val expected = ArrayList<FacetResult>()
    for (i in 0 until numDims) {
      val labelValues = ArrayList<LabelAndValue>()
      var aggregatedValue = 0f
      for (ent in expectedValues[i].entries) {
        labelValues.add(LabelAndValue(ent.key, ent.value))
        aggregatedValue = aggregationFunction.aggregate(aggregatedValue, ent.value)
      }
      sortLabelValues(labelValues)
      if (aggregatedValue > 0) {
        expected.add(
            FacetResult(
                "dim" + i,
                emptyArray<String>(),
                aggregatedValue,
                labelValues.toTypedArray(),
                labelValues.size))
      }
    }

    // Sort by highest value, tie break by value:
    sortFacetResults(expected)

    var facets =
        TaxonomyFacetFloatAssociations(
            taxoReader, facetsConfig, facetsCollector, aggregationFunction, valuesSource)

    var actual = facets.getAllDims(10)

    // test default implementation of getTopDims
    if (actual.size > 0) {
      var topDimsResults1 = facets.getTopDims(1, 10)
      assertEquals(actual.get(0), topDimsResults1.get(0))
    }

    // Messy: fixup ties
    sortTies(actual)

    if (VERBOSE) {
      println("expected=\n" + expected)
      println("actual=\n" + actual)
    }

    assertFloatValuesEquals(expected, actual)
  }
}
