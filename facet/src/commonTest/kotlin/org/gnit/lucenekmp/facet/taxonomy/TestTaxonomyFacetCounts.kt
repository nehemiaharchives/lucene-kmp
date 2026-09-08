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
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.PrintStream


import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.facet.DrillDownQuery
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
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.search.similarities.PerFieldSimilarityWrapper
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils

class TestTaxonomyFacetCounts : FacetTestCase() {

  @Test
  fun testBasic() {
    var dir = newDirectory()
    var taxoDir = newDirectory()

    // Writes facet ords to a separate directory from the
    // main index:
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config = FacetsConfig()
    config.setHierarchical("Publish Date", true)

    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
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
    var searcher = newSearcher(writer.reader)

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher, taxoReader, config)

    // Publish Date is hierarchical, so we should have loaded all 3 int[]:
    assertTrue((facets as TaxonomyFacets).siblingsLoaded())
    assertTrue((facets as TaxonomyFacets).childrenLoaded())

    var finalFacets = facets
    expectThrows(IllegalArgumentException::class) { finalFacets.getTopChildren(0, "Author") }

    // Retrieve & verify results:
    assertEquals(
        "dim=Publish Date path=[] value=5 childCount=3\n  2010 (2)\n  2012 (2)\n  1999 (1)\n",
        facets.getTopChildren(10, "Publish Date").toString())
    assertEquals(
        "dim=Author path=[] value=5 childCount=4\n  Lisa (2)\n  Bob (1)\n  Susan (1)\n  Frank (1)\n",
        facets.getTopChildren(10, "Author").toString())

    assertFacetResult(
        facets.getAllChildren("Publish Date")!!,
        "Publish Date",
        emptyArray<String>(),
        3,
        5,
        LabelAndValue("1999", 1),
        LabelAndValue("2010", 2),
        LabelAndValue("2012", 2))

    assertFacetResult(
        facets.getAllChildren("Author")!!,
        "Author",
        emptyArray<String>(),
        4,
        5,
        LabelAndValue("Bob", 1),
        LabelAndValue("Frank", 1),
        LabelAndValue("Lisa", 2),
        LabelAndValue("Susan", 1))

    // test getAllDims
    var results = facets.getAllDims(10)
    // test getTopDims(10, 10) and expect same results from getAllDims(10)
    var allTopDimsResults = facets.getTopDims(10, 10)
    assertEquals(results, allTopDimsResults)

    // Now user drills down on Publish Date/2010:
    var q2 = DrillDownQuery(config)
    q2.add("Publish Date", "2010")
    var c = searcher.search(q2, FacetsCollectorManager())
    facets = FastTaxonomyFacetCounts(taxoReader, config, c!!)
    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)\n",
        facets.getTopChildren(10, "Author").toString())

    assertEquals<Any?>(1, facets.getSpecificValue("Author", "Lisa"))

    assertNull(facets.getTopChildren(10, "Non exitent dim"))

    // Smoke test PrintTaxonomyStats:
    var bos = ByteArrayOutputStream()
    PrintTaxonomyStats.printStats(taxoReader, PrintStream(out = bos), true)
    var result = bos.toByteArray().decodeToString()
    assertTrue(result.contains("/Author: 4 immediate children; 5 total categories"))
    assertTrue(result.contains("/Publish Date: 3 immediate children; 12 total categories"))
    // Make sure at least a few nodes of the tree came out:
    assertTrue(result.contains("  /1999"))
    assertTrue(result.contains("  /2012"))
    assertTrue(result.contains("      /20"))

    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, taxoDir, dir)
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
    doc.add(FacetField("a", "foo1"))
    doc.add(FacetField("b", "aar1"))
    writer.addDocument(config.build(taxoWriter, doc))

    if (random().nextBoolean()) {
      writer.commit()
    }

    doc = Document()
    doc.add(FacetField("a", "foo2"))
    doc.add(FacetField("b", "bar1"))
    writer.addDocument(config.build(taxoWriter, doc))

    if (random().nextBoolean()) {
      writer.commit()
    }

    doc = Document()
    doc.add(FacetField("a", "foo3"))
    doc.add(FacetField("b", "bar2"))
    doc.add(FacetField("c", "baz1"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher, taxoReader, config)

    // test getAllDims(0)
    expectThrows(IllegalArgumentException::class) { facets.getAllDims(0) }

    // Ask for top 10 labels for any dims that have counts:
    var results = facets.getAllDims(10)

    assertEquals(3, results.size)
    assertEquals(
        "dim=a path=[] value=3 childCount=3\n  foo1 (1)\n  foo2 (1)\n  foo3 (1)\n",
        results.get(0).toString())
    assertEquals(
        "dim=b path=[] value=3 childCount=3\n  aar1 (1)\n  bar1 (1)\n  bar2 (1)\n",
        results.get(1).toString())
    assertEquals("dim=c path=[] value=1 childCount=1\n  baz1 (1)\n", results.get(2).toString())

    // test getAllDims with topN = 1, sort by dim names when values are equal
    var top1results = facets.getAllDims(1)

    assertEquals(3, results.size)
    assertEquals("dim=a path=[] value=3 childCount=3\n  foo1 (1)\n", top1results.get(0).toString())
    assertEquals("dim=b path=[] value=3 childCount=3\n  aar1 (1)\n", top1results.get(1).toString())
    assertEquals("dim=c path=[] value=1 childCount=1\n  baz1 (1)\n", top1results.get(2).toString())

    // test default implementation of getTopDims
    var topNDimsResult = facets.getTopDims(2, 1)
    assertEquals(2, topNDimsResult.size)
    assertEquals(
        "dim=a path=[] value=3 childCount=3\n  foo1 (1)\n", topNDimsResult.get(0).toString())
    assertEquals(
        "dim=b path=[] value=3 childCount=3\n  aar1 (1)\n", topNDimsResult.get(1).toString())

    // test getTopDims(10, 10) and expect same results from getAllDims(10)
    var allDimsResults = facets.getTopDims(10, 10)
    assertEquals(results, allDimsResults)

    // test getTopDims(0, 1)
    expectThrows(IllegalArgumentException::class) { facets.getTopDims(0, 1) }

    // test getTopDims(1, 0) with topNChildren = 0
    expectThrows(IllegalArgumentException::class) { facets.getTopDims(1, 0) }

    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, taxoDir, dir)
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
    doc.add(FacetField("a", "foo1"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

    // Uses default $facets field:
    var facets = FastTaxonomyFacetCounts(taxoReader, config, c!!)

    // Ask for top 10 labels for any dims that have counts:
    var results = facets.getAllDims(10)
    assertTrue(results.isEmpty())

    // test getTopDims(10, 10) and expect same results from getAllDims(10)
    var allTopDimsResults = facets.getTopDims(10, 10)
    assertEquals(results, allTopDimsResults)

    expectThrows(IllegalArgumentException::class) { facets.getSpecificValue("a") }

    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(10, "a") }
    expectThrows(IllegalArgumentException::class) { facets.getAllChildren("a") }

    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, taxoDir, dir)
  }

  @Test
  fun testNonExistentDimension() {
    var dir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config = FacetsConfig()
    config.setIndexFieldName("foo", "\$custom")

    var doc = Document()
    doc.add(FacetField("foo", "bar"))

    var writer = RandomIndexWriter(random(), dir)
    writer.addDocument(config.build(taxoWriter, doc))

    var searcher = newSearcher(writer.reader)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets = getAllFacets("\$custom", searcher, taxoReader, config)

    // get facets for the dimension, which was never configured or indexed before
    var result = facets.getTopChildren(5, "non-existent dimension")

    // make sure the result is null (and no exception was thrown)
    assertNull(result)

    // get facets for the dimension, which was never configured or indexed before
    var allChildrenResult = facets.getAllChildren("non-existent dimension")

    // make sure the result is null (and no exception was thrown)
    assertNull(allChildrenResult)

    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, taxoDir, dir)
  }

  @Test
  fun testReallyNoNormsForDrillDown() {
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var iwc = newIndexWriterConfig(MockAnalyzer(random()))
    iwc.setSimilarity(
        object : PerFieldSimilarityWrapper() {
          var sim = ClassicSimilarity()

          override fun get(name: String): Similarity {
            assertEquals("field", name)
            return sim
          }
        })
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)
    var writer = RandomIndexWriter(random(), dir, iwc)
    var config = FacetsConfig()

    var doc = Document()
    doc.add(newTextField("field", "text", Field.Store.NO))
    doc.add(FacetField("a", "path"))
    writer.addDocument(config.build(taxoWriter, doc))
    writer.close()
    IOUtils.close(taxoWriter, dir, taxoDir)
  }

  @Test
  fun testMultiValuedHierarchy() {
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)
    var config = FacetsConfig()
    config.setHierarchical("a", true)
    config.setMultiValued("a", true)
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(newTextField("field", "text", Field.Store.NO))
    doc.add(FacetField("a", "path", "x"))
    doc.add(FacetField("a", "path", "y"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher, taxoReader, config)

    expectThrows(IllegalArgumentException::class) { facets.getSpecificValue("a") }

    var result = facets.getTopChildren(10, "a")!!
    assertEquals(1, result.labelValues.size)
    assertEquals(1, result.labelValues[0].value.toInt())

    var allChildren = facets.getAllChildren("a")!!
    assertEquals(1, allChildren.labelValues.size)
    assertEquals(1, allChildren.labelValues[0].value.toInt())

    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, dir, taxoDir)
  }

  @Test
  fun testLabelWithDelimiter() {
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config = FacetsConfig()
    config.setMultiValued("dim", true)

    var doc = Document()
    doc.add(newTextField("field", "text", Field.Store.NO))
    doc.add(FacetField("dim", "test\u001Fone"))
    doc.add(FacetField("dim", "test\u001Etwo"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher, taxoReader, config)

    assertEquals<Any?>(1, facets.getSpecificValue("dim", "test\u001Fone"))
    assertEquals<Any?>(1, facets.getSpecificValue("dim", "test\u001Etwo"))

    // no hierarchy
    assertFalse((facets as TaxonomyFacets).siblingsLoaded())
    assertFalse((facets as TaxonomyFacets).childrenLoaded())

    var result = facets.getTopChildren(10, "dim")!!
    assertEquals(
        "dim=dim path=[] value=-1 childCount=2\n  test\u001Fone (1)\n  test\u001Etwo (1)\n",
        result.toString())

    assertFacetResult(
        facets.getAllChildren("dim")!!,
        "dim",
        emptyArray<String>(),
        2,
        -1,
        LabelAndValue("test\u001Etwo", 1),
        LabelAndValue("test\u001Fone", 1))
    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, dir, taxoDir)
  }

  @Test
  fun testRequireDimCount() {
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config = FacetsConfig()
    config.setRequireDimCount("dim", true)

    config.setMultiValued("dim2", true)
    config.setRequireDimCount("dim2", true)

    config.setMultiValued("dim3", true)
    config.setHierarchical("dim3", true)
    config.setRequireDimCount("dim3", true)

    var doc = Document()
    doc.add(newTextField("field", "text", Field.Store.NO))
    doc.add(FacetField("dim", "a"))
    doc.add(FacetField("dim2", "a"))
    doc.add(FacetField("dim2", "b"))
    doc.add(FacetField("dim3", "a", "b"))
    doc.add(FacetField("dim3", "a", "c"))
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)
    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher, taxoReader, config)

    assertEquals<Any?>(1, facets.getTopChildren(10, "dim")!!.value)
    assertEquals<Any?>(1, facets.getAllChildren("dim")!!.value)
    assertEquals<Any?>(1, facets.getTopChildren(10, "dim2")!!.value)
    assertEquals<Any?>(1, facets.getAllChildren("dim2")!!.value)
    assertEquals<Any?>(1, facets.getTopChildren(10, "dim3")!!.value)
    assertEquals<Any?>(1, facets.getAllChildren("dim3")!!.value)
    expectThrows(IllegalArgumentException::class) { facets.getSpecificValue("dim") }

    assertEquals<Any?>(1, facets.getSpecificValue("dim2"))
    assertEquals<Any?>(1, facets.getSpecificValue("dim3"))
    writer.close()
    IOUtils.close(taxoWriter, searcher.indexReader, taxoReader, dir, taxoDir)
  }

  // LUCENE-4583: make sure if we require > 32 KB for one
  // document, we don't hit exc when using Facet42DocValuesFormat
  @Test
  fun testManyFacetsInOneDocument() {
    assumeTrue(
        "default Codec doesn't support huge BinaryDocValues",
        TestUtil.fieldSupportsHugeBinaryDocValues(FacetsConfig.DEFAULT_INDEX_FIELD_NAME))
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var iwc = newIndexWriterConfig(MockAnalyzer(random()))
    var writer = RandomIndexWriter(random(), dir, iwc)
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config = FacetsConfig()
    config.setMultiValued("dim", true)

    var numLabels =
        if (TEST_NIGHTLY) TestUtil.nextInt(random(), 40000, 100000) else TestUtil.nextInt(random(), 4000, 10000)

    var doc = Document()
    doc.add(newTextField("field", "text", Field.Store.NO))
    for (i in 0 until numLabels) {
      doc.add(FacetField("dim", "" + i))
    }
    writer.addDocument(config.build(taxoWriter, doc))

    // NRT open
    var searcher = newSearcher(writer.reader)

    // NRT open
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher, taxoReader, config)

    var result = facets.getTopChildren(Int.MAX_VALUE, "dim")!!
    assertEquals(numLabels, result.labelValues.size)
    var allLabels = HashSet<String>()
    for (labelValue in result.labelValues) {
      allLabels.add(labelValue.label)
      assertEquals(1, labelValue.value.toInt())
    }
    assertEquals(numLabels, allLabels.size)

    var allChildrenResult = facets.getAllChildren("dim")!!
    assertEquals(numLabels, result.labelValues.size)
    var allChildrenLabels = HashSet<String>()
    for (labelValue in allChildrenResult.labelValues) {
      allChildrenLabels.add(labelValue.label)
      assertEquals(1, labelValue.value.toInt())
    }
    assertEquals(numLabels, allChildrenLabels.size)

    writer.close()
    IOUtils.close(searcher.indexReader, taxoWriter, taxoReader, dir, taxoDir)
  }

  // Make sure we catch when app didn't declare field as
  // hierarchical but it was:
  @Test
  fun testDetectHierarchicalField() {
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)
    var writer = RandomIndexWriter(random(), dir)
    var config = FacetsConfig()

    var doc = Document()
    doc.add(newTextField("field", "text", Field.Store.NO))
    doc.add(FacetField("a", "path", "other"))
    expectThrows(IllegalArgumentException::class) { config.build(taxoWriter, doc) }

    writer.close()
    IOUtils.close(taxoWriter, dir, taxoDir)
  }

  // Make sure we catch when app didn't declare field as
  // multi-valued but it was:
  @Test
  fun testDetectMultiValuedField() {
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)
    var writer = RandomIndexWriter(random(), dir)
    var config = FacetsConfig()

    var doc = Document()
    doc.add(newTextField("field", "text", Field.Store.NO))
    doc.add(FacetField("a", "path"))
    doc.add(FacetField("a", "path2"))
    expectThrows(IllegalArgumentException::class) { config.build(taxoWriter, doc) }

    writer.close()
    IOUtils.close(taxoWriter, dir, taxoDir)
  }

  @Test
  fun testSeparateIndexedFields() {
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

    var sfc =
        newSearcher(r).search(MatchAllDocsQuery(), FacetsCollectorManager())
    var facets1 = getTaxonomyFacetCounts(taxoReader, config, sfc!!)
    var facets2 = getTaxonomyFacetCounts(taxoReader, config, sfc!!, "\$b")
    assertEquals(r.maxDoc(), facets1.getTopChildren(10, "a")!!.value.toInt())
    assertEquals(r.maxDoc(), facets1.getAllChildren("a")!!.value.toInt())
    assertEquals(r.maxDoc(), facets2.getTopChildren(10, "b")!!.value.toInt())
    assertEquals(r.maxDoc(), facets2.getAllChildren("b")!!.value.toInt())
    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  @Test
  fun testCountRoot() {
    // LUCENE-4882: FacetsAccumulator threw NPE if a FacetRequest was defined on CP.EMPTY
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var config = FacetsConfig()
    for (i in atLeast(30) downTo 1) {
      var doc = Document()
      doc.add(FacetField("a", "1"))
      doc.add(FacetField("b", "1"))
      iw.addDocument(config.build(taxoWriter, doc))
    }

    var r = DirectoryReader.open(iw)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, newSearcher(r), taxoReader, config)

    var allDimsResult = facets.getAllDims(10)
    for (result in allDimsResult) {
      assertEquals(r.numDocs(), result.value.toInt())
    }

    // test override implementation of getTopDims
    if (allDimsResult.size > 0) {
      var topNDimsResult = facets.getTopDims(1, 10)
      assertEquals(allDimsResult.get(0), topNDimsResult.get(0))
    }

    // test getTopDims(0, 1)
    expectThrows(IllegalArgumentException::class) { facets.getTopDims(0, 1) }

    // test getTopDims(1, 0) with topNChildren = 0
    expectThrows(IllegalArgumentException::class) { facets.getTopDims(1, 0) }

    // test getAllDims(0)
    expectThrows(IllegalArgumentException::class) { facets.getAllDims(0) }

    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  @Test
  fun testGetFacetResultsTwice() {
    // LUCENE-4893: counts were multiplied as many times as getFacetResults was called.
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var config = FacetsConfig()

    var doc = Document()
    doc.add(FacetField("a", "1"))
    doc.add(FacetField("b", "1"))
    iw.addDocument(config.build(taxoWriter, doc))

    var r = DirectoryReader.open(iw)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, newSearcher(r), taxoReader, config)

    var res1 = facets.getAllDims(10)
    var res2 = facets.getAllDims(10)
    assertEquals(
        res1, res2, "calling getFacetResults twice should return the .equals()=true result")

    // test getTopDims(n, 10)
    if (res1.size > 0) {
      for (i in 1 until res1.size) {
        assertEquals(res1.subList(0, i), facets.getTopDims(i, 10))
      }
    }

    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  @Test
  fun testChildCount() {
    // LUCENE-4885: FacetResult.numValidDescendants was not set properly by FacetsAccumulator
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var iw = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var config = FacetsConfig()
    for (i in 0 until 10) {
      var doc = Document()
      doc.add(FacetField("a", i.toString()))
      iw.addDocument(config.build(taxoWriter, doc))
    }

    var r = DirectoryReader.open(iw)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)

    var facets =
        getAllFacets(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, newSearcher(r), taxoReader, config)

    assertEquals(10, facets.getTopChildren(2, "a")!!.childCount)
    assertEquals(10, facets.getAllChildren("a")!!.childCount)

    iw.close()
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir)
  }

  @Test
  fun testMultiValuedFieldCountAll() {

    var dir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config = FacetsConfig()
    config.setHierarchical("Publish Date", true)

    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(StringField("id", "0", Field.Store.NO))
    doc.add(FacetField("Author", "Bob"))
    doc.add(FacetField("Publish Date", "2010", "10", "15"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(StringField("id", "1", Field.Store.NO))
    doc.add(FacetField("Author", "Lisa"))
    doc.add(FacetField("Publish Date", "2010", "10", "20"))
    writer.addDocument(config.build(taxoWriter, doc))

    var searcher1 = newSearcher(writer.reader)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)
    var facets =
        FastTaxonomyFacetCounts(
            FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher1.indexReader, taxoReader, config)

    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)",
        facets.getTopChildren(10, "Author").toString().trim())

    assertFacetResult(
        facets.getAllChildren("Author")!!,
        "Author",
        emptyArray<String>(),
        2,
        2,
        LabelAndValue("Bob", 1),
        LabelAndValue("Lisa", 1))

    // -- delete to trigger liveDocs != null
    writer.deleteDocuments(Term("id", "0"))
    var searcher2 = newSearcher(writer.reader)
    facets =
        FastTaxonomyFacetCounts(
            FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher2.indexReader, taxoReader, config)

    assertEquals(
        "dim=Author path=[] value=1 childCount=1\n  Lisa (1)",
        facets.getTopChildren(10, "Author").toString().trim())

    assertFacetResult(
        facets.getAllChildren("Author")!!,
        "Author",
        emptyArray<String>(),
        1,
        1,
        LabelAndValue("Lisa", 1))

    IOUtils.close(
        writer,
        taxoWriter,
        searcher1.indexReader,
        searcher2.indexReader,
        taxoReader,
        taxoDir,
        dir)
  }

  @Test
  fun testCountAllSingleValuedField() {

    var dir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter =
        DirectoryTaxonomyWriter(taxoDir, IndexWriterConfig.OpenMode.CREATE)

    var config = FacetsConfig()
    config.setHierarchical("Author", true)

    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(StringField("id", "0", Field.Store.NO))
    doc.add(FacetField("Author", "Bob"))
    writer.addDocument(config.build(taxoWriter, doc))

    doc = Document()
    doc.add(StringField("id", "1", Field.Store.NO))
    doc.add(FacetField("Author", "Lisa"))
    writer.addDocument(config.build(taxoWriter, doc))

    var searcher1 = newSearcher(writer.reader)
    var taxoReader = DirectoryTaxonomyReader(taxoWriter)
    var facets =
        FastTaxonomyFacetCounts(
            FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher1.indexReader, taxoReader, config)

    assertEquals(
        "dim=Author path=[] value=2 childCount=2\n  Bob (1)\n  Lisa (1)",
        facets.getTopChildren(10, "Author").toString().trim())

    assertFacetResult(
        facets.getAllChildren("Author")!!,
        "Author",
        emptyArray<String>(),
        2,
        2,
        LabelAndValue("Bob", 1),
        LabelAndValue("Lisa", 1))

    // -- delete to trigger liveDocs != null
    writer.deleteDocuments(Term("id", "0"))
    var searcher2 = newSearcher(writer.reader)
    facets =
        FastTaxonomyFacetCounts(
            FacetsConfig.DEFAULT_INDEX_FIELD_NAME, searcher2.indexReader, taxoReader, config)

    assertEquals(
        "dim=Author path=[] value=1 childCount=1\n  Lisa (1)",
        facets.getTopChildren(10, "Author").toString().trim())

    assertFacetResult(
        facets.getAllChildren("Author")!!,
        "Author",
        emptyArray<String>(),
        1,
        1,
        LabelAndValue("Lisa", 1))

    IOUtils.close(
        writer,
        taxoWriter,
        searcher1.indexReader,
        searcher2.indexReader,
        taxoReader,
        taxoDir,
        dir)
  }

  private fun indexTwoDocs(taxoWriter: TaxonomyWriter, indexWriter: IndexWriter, config: FacetsConfig?, withContent: Boolean) {
    for (i in 0 until 2) {
      var doc = Document()
      if (withContent) {
        doc.add(StringField("f", "a", Field.Store.NO))
      }
      if (config != null) {
        doc.add(FacetField("A", i.toString()))
        indexWriter.addDocument(config.build(taxoWriter, doc))
      } else {
        indexWriter.addDocument(doc)
      }
    }

    indexWriter.commit()
  }

  @Test
  fun testSegmentsWithoutCategoriesOrResults() {
    // tests the accumulator when there are segments with no results
    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var iwc = newIndexWriterConfig(MockAnalyzer(random()))
    iwc.setMergePolicy(NoMergePolicy.INSTANCE) // prevent merges
    var indexWriter = IndexWriter(indexDir, iwc)

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    indexTwoDocs(
        taxoWriter, indexWriter, config, false) // 1st segment, no content, with categories
    indexTwoDocs(taxoWriter, indexWriter, null, true) // 2nd segment, with content, no categories
    indexTwoDocs(taxoWriter, indexWriter, config, true) // 3rd segment ok
    indexTwoDocs(taxoWriter, indexWriter, null, false) // 4th segment, no content, or categories
    indexTwoDocs(taxoWriter, indexWriter, null, true) // 5th segment, with content, no categories
    indexTwoDocs(
        taxoWriter, indexWriter, config, true) // 6th segment, with content, with categories
    indexTwoDocs(taxoWriter, indexWriter, null, true) // 7th segment, with content, no categories
    indexWriter.close()
    IOUtils.close(taxoWriter)

    var indexReader = DirectoryReader.open(indexDir)
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var indexSearcher = newSearcher(indexReader)

    // search for "f:a", only segments 1 and 3 should match results
    var q = TermQuery(Term("f", "a"))
    var sfc = indexSearcher.search(q, FacetsCollectorManager())
    var facets = getTaxonomyFacetCounts(taxoReader, config, sfc!!)
    var result = facets.getTopChildren(10, "A")!!
    assertEquals(2, result!!.labelValues.size, "wrong number of children")
    for (labelValue in result.labelValues) {
      assertEquals(2, labelValue.value.toInt(), "wrong weight for child " + labelValue.label)
    }
    var allChildrenResult = facets.getAllChildren("A")!!
    assertEquals(2, allChildrenResult!!.labelValues.size, "wrong number of children")
    for (labelValue in allChildrenResult.labelValues) {
      assertEquals(2, labelValue.value.toInt(), "wrong weight for child " + labelValue.label)
    }

    IOUtils.close(indexReader, taxoReader, indexDir, taxoDir)
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
      var facets = getTaxonomyFacetCounts(tr, config, fc)

      // Slow, yet hopefully bug-free, faceting:
      val expectedCounts = Array(numDims) { HashMap<String, Int>() }
      val expectedLabels = ArrayList<MutableList<FacetLabel>>()

      for (i in 0 until numDims) {
        expectedCounts[i] = HashMap()
      }

      for (doc in testDocs) {
        if (doc.content.equals(searchToken)) {
          val facetLabels = ArrayList<FacetLabel>()
          for (j in 0 until numDims) {
            if (doc.dims[j] != null) {
              expectedCounts[j][doc.dims[j]!!] = (expectedCounts[j][doc.dims[j]] ?: 0) + 1
              // Add document facet labels
              facetLabels.add(FacetLabel("dim" + j, doc.dims[j]!!))
            }
          }
          expectedLabels.add(facetLabels)
        }
      }

      val expected = ArrayList<FacetResult>()
      for (i in 0 until numDims) {
        val labelValues = ArrayList<LabelAndValue>()
        var totCount = 0
        for (ent in expectedCounts[i].entries) {
          labelValues.add(LabelAndValue(ent.key, ent.value))
          totCount += ent.value
        }
        sortLabelValues(labelValues)
        if (totCount > 0) {
          expected.add(
              FacetResult(
                  "dim" + i,
                  emptyArray<String>(),
                  totCount,
                  labelValues.toTypedArray(),
                  labelValues.size))
        }
      }

      // Sort by highest value, tie-break by value:
      sortFacetResults(expected)

      var actual = facets.getAllDims(10)
      // Messy: fixup ties
      sortTies(actual)

      assertEquals(expected, actual)

      // test getTopDims
      if (actual.size > 0) {
        var topNDimsResult = facets.getTopDims(actual.size, 10)
        sortTies(topNDimsResult)
        assertEquals(actual, topNDimsResult)
      }

      // Test facet labels for each matching test doc
      var actualLabels = getAllTaxonomyFacetLabels(null, tr, fc)
      assertEquals(expectedLabels.size, actualLabels.size)
      assertEquals(sortedFacetLabels(expectedLabels), sortedFacetLabels(actualLabels.map { it.toMutableList() }.toMutableList()))

      // Test facet labels for each matching test doc, given a specific dimension chosen randomly
      var dimension = "dim" + random().nextInt(numDims)
      expectedLabels.forEach { list -> list.removeAll { f -> f.components[0].equals(dimension) == false } }

      actualLabels = getAllTaxonomyFacetLabels(dimension, tr, fc)
      assertEquals(sortedFacetLabels(expectedLabels), sortedFacetLabels(actualLabels.map { it.toMutableList() }.toMutableList()))
    }

    w.close()
    IOUtils.close(tw, searcher.indexReader, tr, indexDir, taxoDir)
  }

  private fun sortedFacetLabels(allFacetLabels: MutableList<MutableList<FacetLabel>>): List<List<FacetLabel>> {
    // Sort each inner list since there is no guaranteed order in which
    // FacetLabels are expected to be retrieved for each document.
    for (facetLabels in allFacetLabels) {
      facetLabels.sort()
    }

    allFacetLabels.sortWith(Comparator { o1, o2 ->
          var diff = o1.size - o2.size
          if (diff != 0) {
            return@Comparator diff
          }

          // the lists are equal in size and sorted
          for (i in 0 until o1.size) {
            var comp = o1.get(i).compareTo(o2.get(i))
            if (comp != 0) {
              return@Comparator comp
            }
          }
          // all elements are equal
          0
        })

    return allFacetLabels
  }

  private fun getAllFacets(indexFieldName: String, searcher: IndexSearcher, taxoReader: TaxonomyReader, config: FacetsConfig): Facets {
    if (random().nextBoolean()) {
      // MatchAllDocsQuery is for "browsing" (counts facets
      // for all non-deleted docs in the index); normally
      // you'd use a "normal" query, and use MultiCollector to
      // wrap collecting the "normal" hits and also facets:
      var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

      return FastTaxonomyFacetCounts(taxoReader, config, c!!)
    } else {
      return FastTaxonomyFacetCounts(
          indexFieldName, searcher.indexReader, taxoReader, config)
    }
  }
}
