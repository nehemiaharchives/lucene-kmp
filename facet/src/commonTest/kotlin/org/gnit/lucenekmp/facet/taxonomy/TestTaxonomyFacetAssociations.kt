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
import org.gnit.lucenekmp.document.TextField
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
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils

/** Test for associations */
class TestTaxonomyFacetAssociations : FacetTestCase() {

  private lateinit var dir: Directory
  private lateinit var reader: IndexReader
  private lateinit var taxoDir: Directory
  private lateinit var taxoReader: TaxonomyReader

  private lateinit var config: FacetsConfig

  private lateinit var randomIntValues: MutableMap<String, MutableList<Int>>
  private lateinit var randomFloatValues: MutableMap<String, MutableList<Float>>
  private lateinit var randomIntSingleValued: MutableMap<String, MutableList<Int>>
  private lateinit var randomFloatSingleValued: MutableMap<String, MutableList<Float>>

  @BeforeTest
  fun beforeClass() {
    dir = newDirectory()
    taxoDir = newDirectory()
    // preparations - index, taxonomy, content

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)

    // Cannot mix ints & floats in the same indexed field:
    config = FacetsConfig()
    config.setIndexFieldName("int", "\$facets.int")
    config.setMultiValued("int", true)
    config.setIndexFieldName("int_random", "\$facets.int")
    config.setMultiValued("int_random", true)
    config.setIndexFieldName("int_single_valued", "\$facets.int")
    config.setIndexFieldName("float", "\$facets.float")
    config.setMultiValued("float", true)
    config.setIndexFieldName("float_random", "\$facets.float")
    config.setMultiValued("float_random", true)
    config.setIndexFieldName("float_single_valued", "\$facets.float")

    var writer = RandomIndexWriter(random(), dir)

    // index documents, 50% have only 'b' and all have 'a'
    for (i in 0 until 110) {
      var doc = Document()
      // every 11th document is added empty, this used to cause the association
      // aggregators to go into an infinite loop
      if (i % 11 != 0) {
        doc.add(IntAssociationFacetField(2, "int", "a"))
        doc.add(FloatAssociationFacetField(0.5f, "float", "a"))
        if (i % 2 == 0) { // 50
          doc.add(IntAssociationFacetField(3, "int", "b"))
          doc.add(FloatAssociationFacetField(0.2f, "float", "b"))
        }
      }
      doc.add(TextField("match", "yes", Field.Store.NO))
      writer.addDocument(config.build(taxoWriter, doc))
    }

    // Also index random content for more random testing:
    val paths = arrayOf("a", "b", "c")
    var count = random().nextInt(1000)
    randomIntValues = HashMap()
    randomFloatValues = HashMap()
    randomIntSingleValued = HashMap()
    randomFloatSingleValued = HashMap()
    for (i in 0 until count) {
      var doc = Document()

      if (random().nextInt(10) >= 2) { // occasionally don't add any fields
        // Add up to five ordinals + values for each doc. Note that duplicates are totally fine:
        for (j in 0 until 5) {
          var path = paths[random().nextInt(3)]
          if (random().nextBoolean()) { // maybe index an int association with the dim
            var nextInt = atLeast(1)
            randomIntValues.getOrPut(path) { ArrayList() }.add(nextInt)
            doc.add(IntAssociationFacetField(nextInt, "int_random", path))
          }
          if (random().nextBoolean()) { // maybe index a float association with the dim
            var nextFloat = random().nextFloat() * 10000f
            doc.add(FloatAssociationFacetField(nextFloat, "float_random", path))
          }
        }

        // Also, (maybe) add to the single-valued association fields:
        var path = paths[random().nextInt(3)]
        if (random().nextBoolean()) {
          var nextInt = atLeast(1)
          randomIntSingleValued.getOrPut(path) { ArrayList() }.add(nextInt)
          doc.add(IntAssociationFacetField(nextInt, "int_single_valued", path))
        }
        if (random().nextBoolean()) {
          var nextFloat = random().nextFloat() * 10000f
          doc.add(FloatAssociationFacetField(nextFloat, "float_single_valued", path))
        }
      }

      doc.add(TextField("match", "yes", Field.Store.NO))
      writer.addDocument(config.build(taxoWriter, doc))
    }

    // Add more random labels and documents to randomly make the test run on sparse/dense
    // aggregation values.
    count = random().nextInt(10_000)
    for (i in 0 until count) {
      var doc = Document()
      doc.add(FacetField("random_dim_" + i, "path"))
      doc.add(TextField("match", "no", Field.Store.NO))
      writer.addDocument(config.build(taxoWriter, doc))
    }

    taxoWriter.close()
    reader = writer.reader
    writer.close()
    taxoReader = DirectoryTaxonomyReader(taxoDir)

    // To avoid floating point precision issues, it's useful to keep track of the values in the
    // exact same order they appear when iterating the doc values in the index. This ensures we
    // sum them in the same order when determining expected values for tests cases and when the
    // actual facets implementation sums them. See LUCENE-10530:
    for (ctx in reader.leaves()) {
      var dv = DocValues.getBinary(ctx.reader(), "\$facets.float")
      while (dv.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        var bytesRef = dv.binaryValue()!!
        var bytes = bytesRef.bytes
        var end = bytesRef.offset + bytesRef.length
        var offset = bytesRef.offset
        while (offset < end) {
          var ord = BitUtil.VH_BE_INT.get(bytes, offset)
          offset += 4
          var value = Float.fromBits(BitUtil.VH_BE_INT.get(bytes, offset))
          offset += 4
          var label = taxoReader.getPath(ord)!!
          var dim = label.components[0]
          var child = label.components[1]
          if ("float_random".equals(dim)) {
            randomFloatValues.getOrPut(child) { ArrayList() }.add(value)
          } else if ("float_single_valued".equals(dim)) {
            randomFloatSingleValued.getOrPut(child) { ArrayList() }.add(value)
          }
        }
      }
    }
  }

  @AfterTest
  fun afterClass() {
    reader.close()
    dir.close()
    taxoReader.close()
    taxoDir.close()
  }

  @Test
  fun testIntSumAssociation() {

    var searcher = newSearcher(reader)
    var fc =
        searcher.search(TermQuery(Term("match", "yes")), FacetsCollectorManager())

    var facets: Facets =
        TaxonomyFacetIntAssociations(
            "\$facets.int", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    assertEquals(
        "dim=int path=[] value=-1 childCount=2\n  a (200)\n  b (150)\n",
        facets.getTopChildren(10, "int").toString())
    assertFacetResult(
        facets.getAllChildren("int")!!,
        "int",
        emptyArray<String>(),
        2,
        -1,
        mapOf("a" to 100, "b" to 50),
        LabelAndValue("a", 200),
        LabelAndValue("b", 150))
    assertEquals(200, facets.getSpecificValue("int", "a").toInt(), "Wrong count for category 'a'!")
    assertEquals(150, facets.getSpecificValue("int", "b").toInt(), "Wrong count for category 'b'!")

    // test getAllDims and getTopDims
    var topDims = facets.getTopDims(10, 10)
    var allDims = facets.getAllDims(10)
    assertEquals(topDims, allDims)
  }

  @Test
  fun testIntAssociationRandom() {

    var searcher = newSearcher(reader)
    var fc =
        searcher.search(TermQuery(Term("match", "yes")), FacetsCollectorManager())

    var expected: MutableMap<String, Int>
    var facets: Facets

    // SUM:
    facets =
        TaxonomyFacetIntAssociations(
            "\$facets.int", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    expected = HashMap()
    for (e in randomIntValues.entries) {
      expected.put(e.key, e.value.sum())
    }
    validateInts("int_random", expected, AssociationAggregationFunction.SUM, true, facets)
    expected = HashMap()
    for (e in randomIntSingleValued.entries) {
      expected.put(e.key, e.value.sum())
    }
    validateInts("int_single_valued", expected, AssociationAggregationFunction.SUM, false, facets)

    // test getAllDims and getTopDims
    var allDims = facets.getAllDims(10)
    var topDims = facets.getTopDims(10, 10)
    assertEquals(topDims, allDims)

    // MAX:
    facets =
        TaxonomyFacetIntAssociations(
            "\$facets.int", taxoReader, config, fc!!, AssociationAggregationFunction.MAX)
    expected = HashMap()
    for (e in randomIntValues.entries) {
      expected.put(e.key, (e.value.maxOrNull() ?: 0))
    }
    validateInts("int_random", expected, AssociationAggregationFunction.MAX, true, facets)
    expected = HashMap()
    for (e in randomIntSingleValued.entries) {
      expected.put(e.key, (e.value.maxOrNull() ?: 0))
    }
    validateInts("int_single_valued", expected, AssociationAggregationFunction.MAX, false, facets)

    // test getAllDims and getTopDims
    topDims = facets.getTopDims(10, 10)
    allDims = facets.getAllDims(10)
    assertEquals(topDims, allDims)
  }

  @Test
  fun testFloatSumAssociation() {
    var searcher = newSearcher(reader)
    var fc =
        searcher.search(TermQuery(Term("match", "yes")), FacetsCollectorManager())

    var facets: Facets =
        TaxonomyFacetFloatAssociations(
            "\$facets.float", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    assertEquals(
        "dim=float path=[] value=-1.0 childCount=2\n  a (50.0)\n  b (9.999995)\n",
        facets.getTopChildren(10, "float").toString())

    assertFacetResult(
        facets.getAllChildren("float")!!,
        "float",
        emptyArray<String>(),
        2,
        -1f,
        mapOf("a" to 100, "b" to 50),
        LabelAndValue("a", 50.0f),
        LabelAndValue("b", 9.999995f))

    assertEquals(50f, facets.getSpecificValue("float", "a").toFloat(), 0.00001f, "Wrong count for category 'a'!")
    assertEquals(10f, facets.getSpecificValue("float", "b").toFloat(), 0.00001f, "Wrong count for category 'b'!")

    // test getAllDims and getTopDims
    var topDims = facets.getTopDims(10, 10)
    var allDims = facets.getAllDims(10)
    assertFloatFacetResultsEqual(topDims, allDims)
  }

  @Test
  fun testFloatAssociationRandom() {
    // disabling search concurrency because validateFloats relies on ordering which requires
    // sequential execution
    var searcher = newSearcher(reader, true, true, false)
    var fc =
        searcher.search(TermQuery(Term("match", "yes")), FacetsCollectorManager())

    var expected: MutableMap<String, Float>
    var facets: Facets

    // SUM:
    facets =
        TaxonomyFacetFloatAssociations(
            "\$facets.float", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    expected = HashMap()
    for (e in randomFloatValues.entries) {
      expected.put(e.key, (e.value.reduceOrNull { a, b -> a + b } ?: 0f))
    }
    validateFloats("float_random", expected, AssociationAggregationFunction.SUM, true, facets)
    expected = HashMap()
    for (e in randomFloatSingleValued.entries) {
      expected.put(e.key, (e.value.reduceOrNull { a, b -> a + b } ?: 0f))
    }
    validateFloats(
        "float_single_valued", expected, AssociationAggregationFunction.SUM, false, facets)

    // test getAllDims and getTopDims
    var topDims = facets.getTopDims(10, 10)
    var allDims = facets.getAllDims(10)
    assertFloatFacetResultsEqual(topDims, allDims)

    // MAX:
    facets =
        TaxonomyFacetFloatAssociations(
            "\$facets.float", taxoReader, config, fc!!, AssociationAggregationFunction.MAX)
    expected = HashMap()
    for (e in randomFloatValues.entries) {
      expected.put(e.key, (e.value.maxOrNull() ?: 0f))
    }
    validateFloats("float_random", expected, AssociationAggregationFunction.MAX, true, facets)
    expected = HashMap()
    for (e in randomFloatSingleValued.entries) {
      expected.put(e.key, (e.value.maxOrNull() ?: 0f))
    }
    validateFloats(
        "float_single_valued", expected, AssociationAggregationFunction.MAX, false, facets)

    // test getAllDims and getTopDims
    topDims = facets.getTopDims(10, 10)
    allDims = facets.getAllDims(10)
    assertFloatFacetResultsEqual(topDims, allDims)
  }

  /**
   * Make sure we can test both int and float assocs in one index, as long as we send each to a
   * different field.
   */
  @Test
  fun testIntAndFloatAssocation() {
    var searcher = newSearcher(reader)
    var fc =
        searcher.search(TermQuery(Term("match", "yes")), FacetsCollectorManager())

    var facets: Facets =
        TaxonomyFacetFloatAssociations(
            "\$facets.float", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    assertEquals(50f, facets.getSpecificValue("float", "a").toFloat(), 0.00001f, "Wrong count for category 'a'!")
    assertEquals(10f, facets.getSpecificValue("float", "b").toFloat(), 0.00001f, "Wrong count for category 'b'!")

    facets =
        TaxonomyFacetIntAssociations(
            "\$facets.int", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    assertEquals(200, facets.getSpecificValue("int", "a").toInt(), "Wrong count for category 'a'!")
    assertEquals(150, facets.getSpecificValue("int", "b").toInt(), "Wrong count for category 'b'!")
  }

  @Test
  fun testWrongIndexFieldName() {
    var searcher = newSearcher(reader)
    var fc =
        searcher.search(TermQuery(Term("match", "yes")), FacetsCollectorManager())
    var facets: Facets =
        TaxonomyFacetFloatAssociations(
            "wrong_field", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    expectThrows(IllegalArgumentException::class) { facets.getSpecificValue("float") }

    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(10, "float") }

    expectThrows(IllegalArgumentException::class) { facets.getAllChildren("float")!! }
  }

  @Test
  fun testMixedTypesInSameIndexField() {
    var dir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(IntAssociationFacetField(14, "a", "x"))
    doc.add(FloatAssociationFacetField(55.0f, "b", "y"))
    expectThrows(IllegalArgumentException::class) { writer.addDocument(config.build(taxoWriter, doc)) }
    writer.close()
    IOUtils.close(taxoWriter, dir, taxoDir)
  }

  @Test
  fun testNoHierarchy() {
    var dir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    config.setHierarchical("a", true)
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(IntAssociationFacetField(14, "a", "x"))
    expectThrows(IllegalArgumentException::class) { writer.addDocument(config.build(taxoWriter, doc)) }

    writer.close()
    IOUtils.close(taxoWriter, dir, taxoDir)
  }

  @Test
  fun testRequireDimCount() {
    var dir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    config.setRequireDimCount("a", true)
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(IntAssociationFacetField(14, "a", "x"))
    expectThrows(IllegalArgumentException::class) { writer.addDocument(config.build(taxoWriter, doc)) }

    writer.close()
    IOUtils.close(taxoWriter, dir, taxoDir)
  }

  @Test
  fun testIntSumAssociationDrillDown() {
    var searcher = newSearcher(reader)
    var q = DrillDownQuery(config)
    q.add("int", "b")
    var fc = searcher.search(q, FacetsCollectorManager())

    var facets: Facets =
        TaxonomyFacetIntAssociations(
            "\$facets.int", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    assertEquals(
        "dim=int path=[] value=-1 childCount=2\n  b (150)\n  a (100)\n",
        facets.getTopChildren(10, "int").toString())

    assertEquals(
        "dim=int path=[] value=-1 childCount=2\n  a (100)\n  b (150)\n",
        sortAllChildren(facets.getAllChildren("int")!!).toString())

    assertFacetResult(
        facets.getAllChildren("int")!!,
        "int",
        emptyArray<String>(),
        2,
        -1,
        LabelAndValue("a", 100),
        LabelAndValue("b", 150))

    assertEquals(100, facets.getSpecificValue("int", "a").toInt(), "Wrong count for category 'a'!")
    assertEquals(150, facets.getSpecificValue("int", "b").toInt(), "Wrong count for category 'b'!")
  }

  @Test
  fun testNonPositiveAggregations() {
    var dir = newDirectory()
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)

    var config = FacetsConfig()
    config.setIndexFieldName("a", "\$float_facets")
    config.setIndexFieldName("b", "\$int_facets")

    var writer = RandomIndexWriter(random(), dir)
    var d: Document

    d = Document()
    // Positive association
    d.add(FloatAssociationFacetField(1f, "a", "1"))
    d.add(IntAssociationFacetField(1, "b", "1"))
    writer.addDocument(config.build(taxoWriter, d))

    d = Document()
    // Zero association
    d.add(FloatAssociationFacetField(0f, "a", "2"))
    d.add(IntAssociationFacetField(0, "b", "2"))
    writer.addDocument(config.build(taxoWriter, d))

    d = Document()
    // Negative association
    d.add(FloatAssociationFacetField(-1f, "a", "3"))
    d.add(IntAssociationFacetField(-1, "b", "3"))
    writer.addDocument(config.build(taxoWriter, d))

    var reader = writer.reader
    IOUtils.close(taxoWriter, writer)

    var searcher = newSearcher(reader)
    var q = MatchAllDocsQuery()
    var fc = searcher.search(q, FacetsCollectorManager())

    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var floatFacets =
        TaxonomyFacetFloatAssociations(
            "\$float_facets", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)
    var intFacets =
        TaxonomyFacetIntAssociations(
            "\$int_facets", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)

    // "2" and "3" are included in the result despite having non-positive values associated to them.
    assertEquals(
        "dim=a path=[] value=0.0 childCount=3\n  1 (1.0)\n  2 (0.0)\n  3 (-1.0)\n",
        floatFacets.getTopChildren(10, "a").toString())
    assertEquals(
        "dim=b path=[] value=0 childCount=3\n  1 (1)\n  2 (0)\n  3 (-1)\n",
        intFacets.getTopChildren(10, "b").toString())

    IOUtils.close(taxoReader, reader, taxoDir, dir)
  }

  @Test
  fun testAggregationCounts() {
    var taxoDir = newDirectory()

    var taxoWriter = DirectoryTaxonomyWriter(taxoDir)

    var config = FacetsConfig()
    config.setIndexFieldName("a", "\$int_facets")

    var writer = RandomIndexWriter(random(), dir)
    var d: Document

    d = Document()
    d.add(IntAssociationFacetField(1, "a", "1"))
    writer.addDocument(config.build(taxoWriter, d))

    d = Document()
    d.add(IntAssociationFacetField(5, "a", "2"))
    writer.addDocument(config.build(taxoWriter, d))

    d = Document()
    d.add(IntAssociationFacetField(1, "a", "1"))
    writer.addDocument(config.build(taxoWriter, d))

    var reader = writer.reader
    IOUtils.close(taxoWriter, writer)

    var searcher = newSearcher(reader)
    var q = MatchAllDocsQuery()
    var fc = searcher.search(q, FacetsCollectorManager())

    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var intFacets =
        TaxonomyFacetIntAssociations(
            "\$int_facets", taxoReader, config, fc!!, AssociationAggregationFunction.SUM)

    var result = intFacets.getTopChildren(10, "a")!!
    assertEquals("dim=a path=[] value=7 childCount=2\n  2 (5)\n  1 (2)\n", result.toString())
    assertEquals(1, result.labelValues[0].count)
    assertEquals(2, result.labelValues[1].count)

    IOUtils.close(taxoReader, reader, taxoDir)
  }

  private fun validateInts(dim: String, expected: Map<String, Int>, aggregationFunction: AssociationAggregationFunction, isMultiValued: Boolean, facets: Facets) {
    var aggregatedValue = 0
    for (e in expected.entries) {
      var value = e.value
      assertEquals(value, facets.getSpecificValue(dim, e.key).toInt())
      aggregatedValue = aggregationFunction.aggregate(aggregatedValue, value)
    }

    if (isMultiValued) {
      aggregatedValue = -1
    }

    var facetResult = facets.getTopChildren(10, dim)
    var allChildrenResult = facets.getAllChildren(dim)

    if (expected.isEmpty()) {
      // If we hit the rare random case where nothing is indexed for the dim, we expect a null
      // facetResult (see: LUCENE-10529)
      assertNull(facetResult)
      assertNull(allChildrenResult)
    } else {
      assertEquals(dim, facetResult!!.dim)
      assertEquals(dim, allChildrenResult!!.dim)
      assertEquals(aggregatedValue, facetResult.value.toInt())
      assertEquals(aggregatedValue, allChildrenResult.value.toInt())
      assertEquals(expected.size, facetResult.childCount)
      assertEquals(expected.size, allChildrenResult.childCount)
    }
  }

  private fun validateFloats(dim: String, expected: Map<String, Float>, aggregationFunction: AssociationAggregationFunction, isMultiValued: Boolean, facets: Facets) {
    var aggregatedValue = 0f
    for (e in expected.entries) {
      var value = e.value
      // We can expect the floats to be exactly equal here since we're ensuring that we sum them
      // in the same order when determining expected values and when computing facets. See
      // LUCENE-10530. This though requires sequential execution.
      assertEquals(value, facets.getSpecificValue(dim, e.key).toFloat(), 0f)
      aggregatedValue = aggregationFunction.aggregate(aggregatedValue, value)
    }

    if (isMultiValued) {
      aggregatedValue = -1f
    }

    var facetResult = facets.getTopChildren(10, dim)
    var allChildrenResult = facets.getAllChildren(dim)

    if (expected.isEmpty()) {
      // If we hit the rare random case where nothing is indexed for the dim, we expect a null
      // facetResult (see: LUCENE-10529)
      assertNull(facetResult)
      assertNull(allChildrenResult)
    } else {
      assertEquals(dim, facetResult!!.dim)
      assertEquals(dim, allChildrenResult!!.dim)
      assertEquals(aggregatedValue, facetResult.value.toFloat(), 1f)
      assertEquals(aggregatedValue, allChildrenResult.value.toFloat(), 1f)
      assertEquals(expected.size, facetResult.childCount)
      assertEquals(expected.size, allChildrenResult.childCount)
    }
  }

  private fun assertFloatFacetResultsEqual(expected: List<FacetResult>, actual: List<FacetResult>) {
    assertEquals(expected.size, actual.size)
    for (i in 0 until expected.size) {
      var expectedResult = expected.get(i)
      var actualResult = actual.get(i)

      assertEquals(expectedResult.dim, actualResult.dim)
      assertContentEquals(expectedResult.path, actualResult.path)
      assertFloatUlpEquals(expectedResult.value.toFloat(), actualResult.value.toFloat(), 2.toShort())
      assertEquals(expectedResult.childCount, actualResult.childCount)
    }
  }

  // since we have no insight into the ordinals assigned to the values, we sort labels by value and
  // count in
  // ascending order in order to compare with expected results
  private fun sortAllChildren(allChildrenResult: FacetResult): FacetResult {
    allChildrenResult.labelValues.sortWith(compareBy<LabelAndValue> { it.label }.thenBy { it.value.toLong() })
    return allChildrenResult
  }
}
