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
package org.gnit.lucenekmp.facet.range

import kotlin.test.*
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Concurrency

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleDocValuesField
import org.gnit.lucenekmp.document.DoublePoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.facet.DrillDownQuery
import org.gnit.lucenekmp.facet.DrillSideways
import org.gnit.lucenekmp.facet.DrillSideways.DrillSidewaysResult
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetResult
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.Facets
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollectorManager
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.LabelAndValue
import org.gnit.lucenekmp.facet.MultiDoubleValuesSource
import org.gnit.lucenekmp.facet.MultiFacets
import org.gnit.lucenekmp.facet.MultiLongValuesSource
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.DoubleValues
import org.gnit.lucenekmp.search.DoubleValuesSource
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.FilterWeight
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.LongValuesSource
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.NumericUtils

@OptIn(ExperimentalAtomicApi::class)
class TestRangeFacetCounts : FacetTestCase() {

  @Test

  fun testBasicLong() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: NumericDocValuesField = NumericDocValuesField("field", 0L)
    doc.add(field)
    for (l in 0L until 100L) {
      field.setLongValue(l)
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    field.setLongValue(Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeFacetCounts(
            "field",
            fc,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, true))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=22 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (1)\n",
        result.toString())

    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=22 childCount=5\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n",
        result.toString())

    // test getTopChildren(0, dim)
    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(0, "field") }

    r.close()
    d.close()
  }

  @Test

  fun testBasicLongMultiValued() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    // just index the same value twice each time and make sure we don't double count
    var field1: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    var field2: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    doc.add(field1)
    doc.add(field2)
    for (l in 0L until 100L) {
      field1.setLongValue(l.toLong())
      field2.setLongValue(l.toLong())
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    field1.setLongValue(Long.MAX_VALUE)
    field2.setLongValue(Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeFacetCounts(
            "field",
            fc,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, true))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=22 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (1)\n",
        result.toString())

    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=22 childCount=5\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n",
        result.toString())

    // test getTopChildren(0, dim)
    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(0, "field") }

    r.close()
    d.close()
  }

  @Test

  fun testBasicLongMultiValuedMixedSegmentTypes() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var field1: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    var field2: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    // write docs as two segments (50 in each). the first segment will contain a mix of single- and
    // multi-value cases, while the second segment will be all single values.
    for (l in 0 until 100) {
      field1.setLongValue(l.toLong())
      field2.setLongValue(l.toLong())
      var doc: Document = Document()
      doc.add(field1)
      if (l == 0) {
        doc.add(field2)
      } else if (l < 50) {
        if (random().nextBoolean()) {
          doc.add(field2)
        }
      }
      w.addDocument(doc)
      if (l == 50) {
        w.commit()
      }
    }

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeFacetCounts(
            "field",
            fc,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, true))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=21 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n",
        result.toString())

    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=21 childCount=4\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n",
        result.toString())

    r.close()
    d.close()
  }

  @Test

  fun testLongGetAllDims() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: NumericDocValuesField = NumericDocValuesField("field", 0L)
    doc.add(field)
    for (l in 0L until 100L) {
      field.setLongValue(l)
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    field.setLongValue(Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeFacetCounts(
            "field",
            fc,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, true))

    var result: List<FacetResult> = facets.getAllDims(10)
    assertEquals(1, result.size)
    assertEquals(
        "dim=field path=[] value=22 childCount=5\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n  over 1000 (1)\n",
        result.get(0).toString())

    // test getAllDims(1)
    result = facets.getAllDims(1)
    assertEquals(1, result.size)
    assertEquals(
        "dim=field path=[] value=22 childCount=5\n  less than or equal to 10 (11)\n",
        result.get(0).toString())

    // test default implementation of getTopDims
    var topNDimsResult: List<FacetResult> = facets.getTopDims(1, 1)
    assertEquals(result, topNDimsResult)

    // test getTopDims(0, 1)
    topNDimsResult = facets.getTopDims(0, 1)
    assertEquals(0, topNDimsResult.size)

    // test getAllDims(0)
    expectThrows(IllegalArgumentException::class) { facets.getAllDims(0) }

    r.close()
    d.close()
  }

  @Test

  fun testUselessRange() {
    expectThrows(IllegalArgumentException::class) { LongRange("useless", 7, true, 6, true) }
    expectThrows(IllegalArgumentException::class) { LongRange("useless", 7, true, 7, false) }
    expectThrows(IllegalArgumentException::class) { DoubleRange("useless", 7.0, true, 6.0, true) }
    expectThrows(IllegalArgumentException::class) { DoubleRange("useless", 7.0, true, 7.0, false) }
  }

  @Test

  fun testLongMinMax() {

    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: NumericDocValuesField = NumericDocValuesField("field", 0L)
    doc.add(field)
    field.setLongValue(Long.MIN_VALUE)
    w.addDocument(doc)
    field.setLongValue(0)
    w.addDocument(doc)
    field.setLongValue(Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeFacetCounts(
            "field",
            fc,
            LongRange("min", Long.MIN_VALUE, true, Long.MIN_VALUE, true),
            LongRange("max", Long.MAX_VALUE, true, Long.MAX_VALUE, true),
            LongRange("all0", Long.MIN_VALUE, true, Long.MAX_VALUE, true),
            LongRange("all1", Long.MIN_VALUE, false, Long.MAX_VALUE, true),
            LongRange("all2", Long.MIN_VALUE, true, Long.MAX_VALUE, false),
            LongRange("all3", Long.MIN_VALUE, false, Long.MAX_VALUE, false))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=3 childCount=6\n  min (1)\n  max (1)\n  all0 (3)\n  all1 (2)\n  all2 (2)\n  all3 (1)\n",
        result.toString())

    result = facets.getTopChildren(5, "field")!!
    assertEquals(
        "dim=field path=[] value=3 childCount=6\n  all0 (3)\n  all1 (2)\n  all2 (2)\n  all3 (1)\n  max (1)\n",
        result.toString())

    r.close()
    d.close()
  }

  @Test

  fun testOverlappedEndStart() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: NumericDocValuesField = NumericDocValuesField("field", 0L)
    doc.add(field)
    for (l in 0L until 100L) {
      field.setLongValue(l)
      w.addDocument(doc)
    }
    field.setLongValue(Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeFacetCounts(
            "field",
            fc,
            LongRange("0-10", 0L, true, 10L, true),
            LongRange("10-20", 10L, true, 20L, true),
            LongRange("20-30", 20L, true, 30L, true),
            LongRange("30-40", 30L, true, 40L, true))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=41 childCount=4\n  0-10 (11)\n  10-20 (11)\n  20-30 (11)\n  30-40 (11)\n",
        result.toString())

    result = facets.getTopChildren(3, "field")!!
    assertEquals(
        "dim=field path=[] value=41 childCount=4\n  0-10 (11)\n  10-20 (11)\n  20-30 (11)\n",
        result.toString())
    r.close()
    d.close()
  }

  @Test

  fun testEmptyRangesSingleValued() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: NumericDocValuesField = NumericDocValuesField("field", 0L)
    doc.add(field)
    for (l in 0L until 100L) {
      field.setLongValue(l)
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets = LongRangeFacetCounts("field", fc)

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals("dim=field path=[] value=0 childCount=0\n", result.toString())
    result = facets.getTopChildren(1, "field")!!
    assertEquals("dim=field path=[] value=0 childCount=0\n", result.toString())

    r.close()
    d.close()
  }

  @Test

  fun testEmptyRangesMultiValued() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field1: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    var field2: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    doc.add(field1)
    doc.add(field2)
    for (l in 0L until 100L) {
      field1.setLongValue(l.toLong())
      field2.setLongValue(l.toLong())
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets = LongRangeFacetCounts("field", fc)

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals("dim=field path=[] value=0 childCount=0\n", result.toString())
    result = facets.getTopChildren(1, "field")!!
    assertEquals("dim=field path=[] value=0 childCount=0\n", result.toString())

    r.close()
    d.close()
  }

  /**
   * Tests single request that mixes Range and non-Range faceting, with DrillSideways and taxonomy.
   */
  @Test
  fun testMixedRangeAndNonRangeTaxonomy() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var td: Directory = newDirectory()
    var tw: DirectoryTaxonomyWriter = DirectoryTaxonomyWriter(td, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()

    for (l in 0L until 100L) {
      var doc: Document = Document()
      // For computing range facet counts:
      doc.add(NumericDocValuesField("field", l))
      // For drill down by numeric range:
      doc.add(LongPoint("field", l))

      if ((l and 3L) == 0L) {
        doc.add(FacetField("dim", "a"))
      } else {
        doc.add(FacetField("dim", "b"))
      }
      w.addDocument(config.build(tw, doc))
    }

    val r: IndexReader = w.reader

    val tr: TaxonomyReader = DirectoryTaxonomyReader(tw)

    var s: IndexSearcher = newSearcher(r, false, false, Concurrency.INTER_SEGMENT)
    // DrillSideways requires the entire range of docs to be scored at once, so it doesn't support
    // timeouts whose implementation scores one window of doc IDs at a time.
    s.timeout = null

    if (VERBOSE) {
      println("TEST: searcher=" + s)
    }

    var ds: DrillSideways =
        object : DrillSideways(s, config, tr) {

          override protected fun buildFacetsResult(drillDowns: FacetsCollector?, drillSideways: Array<FacetsCollector>?, drillSidewaysDims: Array<String>?): Facets {
            var dimFC: FacetsCollector = drillDowns!!
            var fieldFC: FacetsCollector = drillDowns!!
            if (drillSideways != null) {
              for (i in 0 until drillSideways.size) {
                var dim: String = drillSidewaysDims!![i]
                if (dim.equals("field")) {
                  fieldFC = drillSideways[i]
                } else {
                  dimFC = drillSideways[i]
                }
              }
            }

            var byDim: MutableMap<String, Facets> = HashMap()
            byDim.put(
                "field",
                LongRangeFacetCounts(
                    "field",
                    fieldFC,
                    LongRange("less than 10", 0L, true, 10L, false),
                    LongRange("less than or equal to 10", 0L, true, 10L, true),
                    LongRange("over 90", 90L, false, 100L, false),
                    LongRange("90 or above", 90L, true, 100L, false),
                    LongRange("over 1000", 1000L, false, Long.MAX_VALUE, false)))
            byDim.put("dim", getTaxonomyFacetCounts(taxoReader!!, config, dimFC))
            return MultiFacets(byDim, null)
          }

          override protected fun scoreSubDocsAtOnce(): Boolean {
            return random().nextBoolean()
          }
        }

    // First search, no drill downs:
    var ddq: DrillDownQuery = DrillDownQuery(config)
    var dsr: DrillSidewaysResult = ds.search(null, ddq, 10)

    assertEquals(100L, dsr.hits!!.totalHits.value)
    assertEquals(
        "dim=dim path=[] value=100 childCount=2\n  b (75)\n  a (25)\n",
        dsr.facets!!.getTopChildren(10, "dim").toString())
    assertEquals(
        "dim=field path=[] value=21 childCount=4\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n",
        dsr.facets!!.getTopChildren(10, "field").toString())

    // Second search, drill down on dim=b:
    ddq = DrillDownQuery(config)
    ddq.add("dim", "b")
    dsr = ds.search(null, ddq, 10)

    assertEquals(75L, dsr.hits!!.totalHits.value)
    assertEquals(
        "dim=dim path=[] value=100 childCount=2\n  b (75)\n  a (25)\n",
        dsr.facets!!.getTopChildren(10, "dim").toString())
    assertEquals(
        "dim=field path=[] value=16 childCount=4\n  90 or above (8)\n  less than or equal to 10 (8)\n  less than 10 (7)\n  over 90 (7)\n",
        dsr.facets!!.getTopChildren(10, "field").toString())

    // Third search, drill down on "less than or equal to 10":
    ddq = DrillDownQuery(config)
    ddq.add("field", LongPoint.newRangeQuery("field", 0L, 10L))
    dsr = ds.search(null, ddq, 10)

    assertEquals(11L, dsr.hits!!.totalHits.value)
    assertEquals(
        "dim=dim path=[] value=11 childCount=2\n  b (8)\n  a (3)\n",
        dsr.facets!!.getTopChildren(10, "dim").toString())
    assertEquals(
        "dim=field path=[] value=21 childCount=4\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n",
        dsr.facets!!.getTopChildren(10, "field").toString())
    w.close()
    IOUtils.close(tw, tr, td, r, d)
  }

  @Test

  fun testBasicDouble() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: DoubleDocValuesField = DoubleDocValuesField("field", 0.0)
    doc.add(field)
    for (i in 0 until 100) {
      field.setDoubleValue(i.toDouble())
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
    var facets: Facets =
        DoubleRangeFacetCounts(
            "field",
            fc,
            DoubleRange("less than 10", 0.0, true, 10.0, false),
            DoubleRange("less than or equal to 10", 0.0, true, 10.0, true),
            DoubleRange("over 90", 90.0, false, 100.0, false),
            DoubleRange("90 or above", 90.0, true, 100.0, false),
            DoubleRange("over 1000", 1000.0, false, Double.POSITIVE_INFINITY, false))

    assertEquals(
        "dim=field path=[] value=21 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n",
        facets.getAllChildren("field").toString())!!
    assertEquals(
        "dim=field path=[] value=21 childCount=4\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n",
        facets.getTopChildren(4, "field").toString())!!
    w.close()
    IOUtils.close(r, d)
  }

  @Test

  fun testBasicDoubleMultiValued() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    // index the same value twice and make sure we don't double count
    var field1: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0)
    var field2: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0)
    doc.add(field1)
    doc.add(field2)
    for (i in 0 until 100) {
      field1.setLongValue(NumericUtils.doubleToSortableLong(i.toDouble()))
      field2.setLongValue(NumericUtils.doubleToSortableLong(i.toDouble()))
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
    var facets: Facets =
        DoubleRangeFacetCounts(
            "field",
            fc,
            DoubleRange("less than 10", 0.0, true, 10.0, false),
            DoubleRange("less than or equal to 10", 0.0, true, 10.0, true),
            DoubleRange("over 90", 90.0, false, 100.0, false),
            DoubleRange("90 or above", 90.0, true, 100.0, false),
            DoubleRange("over 1000", 1000.0, false, Double.POSITIVE_INFINITY, false))

    facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=21 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n",
        facets.getAllChildren("field").toString())!!
    assertEquals(
        "dim=field path=[] value=21 childCount=4\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n",
        facets.getTopChildren(4, "field").toString())!!
    facets.getTopChildren(4, "field")!!
    w.close()
    IOUtils.close(r, d)
  }

  @Test

  fun testBasicDoubleMultiValuedMixedSegmentTypes() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var field1: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    var field2: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    // write docs as two segments (50 in each). the first segment will contain a mix of single- and
    // multi-value cases, while the second segment will be all single values.
    for (l in 0 until 100) {
      field1.setLongValue(NumericUtils.doubleToSortableLong(l.toDouble()))
      field2.setLongValue(NumericUtils.doubleToSortableLong(l.toDouble()))
      var doc: Document = Document()
      doc.add(field1)
      if (l == 0) {
        doc.add(field2)
      } else if (l < 50) {
        if (random().nextBoolean()) {
          doc.add(field2)
        }
      }
      w.addDocument(doc)
      if (l == 50) {
        w.commit()
      }
    }

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        DoubleRangeFacetCounts(
            "field",
            fc,
            DoubleRange("less than 10", 0.0, true, 10.0, false),
            DoubleRange("less than or equal to 10", 0.0, true, 10.0, true),
            DoubleRange("over 90", 90.0, false, 100.0, false),
            DoubleRange("90 or above", 90.0, true, 100.0, false),
            DoubleRange("over 1000", 1000.0, false, Double.POSITIVE_INFINITY, false))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=21 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (9)\n  90 or above (10)\n  over 1000 (0)\n",
        result.toString())
    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=21 childCount=4\n  less than or equal to 10 (11)\n  90 or above (10)\n  less than 10 (10)\n  over 90 (9)\n",
        result.toString())

    r.close()
    d.close()
  }

  @Test

  fun testRandomLongsSingleValued() {
    var dir: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var numDocs: Int = atLeast(1000)
    if (VERBOSE) {
      println("TEST: numDocs=" + numDocs)
    }
    var values: LongArray = LongArray(numDocs)
    var minValue: Long = Long.MAX_VALUE
    var maxValue: Long = Long.MIN_VALUE
    for (i in 0 until numDocs) {
      var doc: Document = Document()
      var v: Long = random().nextLong()
      values[i] = v
      doc.add(NumericDocValuesField("field", v))
      doc.add(LongPoint("field", v))
      w.addDocument(doc)
      minValue = minOf(minValue, v)
      maxValue = maxOf(maxValue, v)
    }
    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r, false)
    var config: FacetsConfig = FacetsConfig()

    var numIters: Int = atLeast(10)
    for (iter in 0 until numIters) {
      if (VERBOSE) {
        println("TEST: iter=" + iter)
      }
      var numRange: Int = TestUtil.nextInt(random(), 1, 100)
      var ranges: Array<LongRange?> = arrayOfNulls<LongRange>(numRange)
      var expectedCounts: IntArray = IntArray(numRange)
      var expectedTopNChildrenCounts: IntArray = IntArray(numRange)
      var minAcceptedValue: Long = Long.MAX_VALUE
      var maxAcceptedValue: Long = Long.MIN_VALUE
      for (rangeID in 0 until numRange) {
        var min: Long
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: LongRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            min = prevRange.min
          } else {
            min = prevRange.max
          }
        } else {
          min = random().nextLong()
        }
        var max: Long
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: LongRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            max = prevRange.min
          } else {
            max = prevRange.max
          }
        } else {
          max = random().nextLong()
        }

        if (min > max) {
          var x: Long = min
          min = max
          max = x
        }
        var minIncl: Boolean
        var maxIncl: Boolean

        // NOTE: max - min >= 0 is here to handle the common overflow case!
        if (max - min >= 0 && max - min < 2) {
          // If max == min or max == min+1, we always do inclusive, else we might pass an empty
          // range and hit exc from LongRange's ctor:
          minIncl = true
          maxIncl = true
        } else {
          minIncl = random().nextBoolean()
          maxIncl = random().nextBoolean()
        }
        ranges[rangeID] = LongRange("r" + rangeID, min, minIncl, max, maxIncl)
        if (VERBOSE) {
          println("  range " + rangeID + ": " + ranges[rangeID])
        }

        // Do "slow but hopefully correct" computation of
        // expected count:
        for (i in 0 until numDocs) {
          var accept: Boolean = true
          if (minIncl) {
            accept = accept and (values[i] >= min)
          } else {
            accept = accept and (values[i] > min)
          }
          if (maxIncl) {
            accept = accept and (values[i] <= max)
          } else {
            accept = accept and (values[i] < max)
          }
          if (accept) {
            expectedCounts[rangeID]++
            expectedTopNChildrenCounts[rangeID]++
            minAcceptedValue = minOf(minAcceptedValue, values[i])
            maxAcceptedValue = maxOf(maxAcceptedValue, values[i])
          }
        }
      }

      var sfc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
      var fastMatchQuery: Query?
      if (random().nextBoolean()) {
        if (random().nextBoolean()) {
          fastMatchQuery = LongPoint.newRangeQuery("field", minValue, maxValue)
        } else {
          fastMatchQuery = LongPoint.newRangeQuery("field", minAcceptedValue, maxAcceptedValue)
        }
      } else {
        fastMatchQuery = null
      }

      if (random().nextBoolean()) {
        var vs: LongValuesSource = LongValuesSource.fromLongField("field")
        var mvs: MultiLongValuesSource = MultiLongValuesSource.fromLongField("field")
        var facets: Facets
        if (random().nextBoolean()) {
          facets = LongRangeFacetCounts("field", vs, sfc, fastMatchQuery, *ranges.requireNoNulls())
        } else if (random().nextBoolean()) {
          facets = LongRangeFacetCounts("field", mvs, sfc, fastMatchQuery, *ranges.requireNoNulls())
        } else {
          facets =
              LongRangeFacetCounts(
                  "field", MultiLongValuesSource.fromSingleValued(vs), sfc, fastMatchQuery, *ranges.requireNoNulls())
        }
        var result: FacetResult = facets.getAllChildren("field")!!
        assertEquals(numRange, result.labelValues.size)
        var topNResult: FacetResult = facets.getTopChildren(numRange, "field")!!
        expectedTopNChildrenCounts.sort()

        for (rangeID in 0 until numRange) {
          if (VERBOSE) {
            println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID])
          }
          var subNode: LabelAndValue = result.labelValues[rangeID]
          assertEquals("r" + rangeID, subNode.label)
          assertEquals(expectedCounts[rangeID], subNode.value.toInt())
          // test topNChildren and assert topNResults are sorted by count
          if (rangeID < topNResult.labelValues.size) {
            var topNsubNode: LabelAndValue = topNResult.labelValues[rangeID]
            assertEquals(
                expectedTopNChildrenCounts[numRange - rangeID - 1], topNsubNode.value.toInt())
          }

          var range: LongRange = ranges[rangeID]!!

          // Test drill-down:
          var ddq: DrillDownQuery = DrillDownQuery(config)
          if (random().nextBoolean()) {
            ddq.add("field", LongPoint.newRangeQuery("field", range.min, range.max))
          } else if (random().nextBoolean()) {
            ddq.add("field", range.getQuery(fastMatchQuery, mvs))
          } else {
            ddq.add("field", range.getQuery(fastMatchQuery, vs))
          }
          assertEquals(expectedCounts[rangeID], s.count(ddq))
        }
      } else {
        var vs: MultiLongValuesSource = MultiLongValuesSource.fromLongField("field")
        var facets: Facets = LongRangeFacetCounts("field", vs, sfc, fastMatchQuery, *ranges.requireNoNulls())
        var result: FacetResult = facets.getAllChildren("field")!!
        assertEquals(numRange, result.labelValues.size)
        var topNResult: FacetResult = facets.getTopChildren(numRange, "field")!!
        expectedTopNChildrenCounts.sort()

        for (rangeID in 0 until numRange) {
          if (VERBOSE) {
            println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID])
          }
          var subNode: LabelAndValue = result.labelValues[rangeID]
          assertEquals("r" + rangeID, subNode.label)
          assertEquals(expectedCounts[rangeID], subNode.value.toInt())

          // test topNChildren and assert topNResults are sorted by count
          if (rangeID < topNResult.labelValues.size) {
            var topNsubNode: LabelAndValue = topNResult.labelValues[rangeID]
            assertEquals(
                expectedTopNChildrenCounts[numRange - rangeID - 1], topNsubNode.value.toInt())
          }

          var range: LongRange = ranges[rangeID]!!

          // Test drill-down:
          var ddq: DrillDownQuery = DrillDownQuery(config)
          if (random().nextBoolean()) {
            ddq.add("field", LongPoint.newRangeQuery("field", range.min, range.max))
          } else {
            ddq.add("field", range.getQuery(fastMatchQuery, vs))
          }
          assertEquals(expectedCounts[rangeID], s.count(ddq))
        }
      }
    }

    w.close()
    IOUtils.close(r, dir)
  }

  @Test

  fun testRandomLongsMultiValued() {
    var dir: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var numDocs: Int = atLeast(1000)
    if (VERBOSE) {
      println("TEST: numDocs=" + numDocs)
    }
    var values: Array<LongArray> = Array(numDocs) { LongArray(0) }
    var minValue: Long = Long.MAX_VALUE
    var maxValue: Long = Long.MIN_VALUE
    for (i in 0 until numDocs) {
      var doc: Document = Document()
      var numVals: Int = TestUtil.nextInt(random(), 1, 50)
      if (random().nextInt(10) == 0) {
        numVals = 1 // make sure we have ample testing of single-value cases
      }
      values[i] = LongArray(numVals)
      for (j in 0 until numVals) {
        var v: Long = random().nextLong()
        values[i][j] = v
        doc.add(SortedNumericDocValuesField("field", v))
        doc.add(LongPoint("field", v))
        minValue = minOf(minValue, v)
        maxValue = maxOf(maxValue, v)
      }
      w.addDocument(doc)
    }
    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r, false)
    var config: FacetsConfig = FacetsConfig()

    var numIters: Int = atLeast(10)
    for (iter in 0 until numIters) {
      if (VERBOSE) {
        println("TEST: iter=" + iter)
      }
      var numRange: Int = TestUtil.nextInt(random(), 1, 100)
      var ranges: Array<LongRange?> = arrayOfNulls<LongRange>(numRange)
      var expectedCounts: IntArray = IntArray(numRange)
      var expectedTopNChildrenCounts: IntArray = IntArray(numRange)
      var minAcceptedValue: Long = Long.MAX_VALUE
      var maxAcceptedValue: Long = Long.MIN_VALUE
      for (rangeID in 0 until numRange) {
        var min: Long
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: LongRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            min = prevRange.min
          } else {
            min = prevRange.max
          }
        } else {
          min = random().nextLong()
        }
        var max: Long
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: LongRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            max = prevRange.min
          } else {
            max = prevRange.max
          }
        } else {
          max = random().nextLong()
        }

        if (min > max) {
          var x: Long = min
          min = max
          max = x
        }
        var minIncl: Boolean
        var maxIncl: Boolean

        // NOTE: max - min >= 0 is here to handle the common overflow case!
        if (max - min >= 0 && max - min < 2) {
          // If max == min or max == min+1, we always do inclusive, else we might pass an empty
          // range and hit exc from LongRange's ctor:
          minIncl = true
          maxIncl = true
        } else {
          minIncl = random().nextBoolean()
          maxIncl = random().nextBoolean()
        }
        ranges[rangeID] = LongRange("r" + rangeID, min, minIncl, max, maxIncl)
        if (VERBOSE) {
          println("  range " + rangeID + ": " + ranges[rangeID])
        }

        // Do "slow but hopefully correct" computation of
        // expected count:
        for (i in 0 until numDocs) {
          for (j in 0 until values[i].size) {
            var accept: Boolean = true
            if (minIncl) {
              accept = accept and (values[i][j] >= min)
            } else {
              accept = accept and (values[i][j] > min)
            }
            if (maxIncl) {
              accept = accept and (values[i][j] <= max)
            } else {
              accept = accept and (values[i][j] < max)
            }
            if (accept) {
              expectedCounts[rangeID]++
              expectedTopNChildrenCounts[rangeID]++
              minAcceptedValue = minOf(minAcceptedValue, values[i][j])
              maxAcceptedValue = maxOf(maxAcceptedValue, values[i][j])
              break // ensure each doc can contribute at most 1 count to each range
            }
          }
        }
      }

      var sfc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
      var fastMatchQuery: Query?
      if (random().nextBoolean()) {
        if (random().nextBoolean()) {
          fastMatchQuery = LongPoint.newRangeQuery("field", minValue, maxValue)
        } else {
          fastMatchQuery = LongPoint.newRangeQuery("field", minAcceptedValue, maxAcceptedValue)
        }
      } else {
        fastMatchQuery = null
      }
      var facets: Facets
      if (random().nextBoolean()) {
        facets =
            LongRangeFacetCounts(
                "field", MultiLongValuesSource.fromLongField("field"), sfc, fastMatchQuery, *ranges.requireNoNulls())
      } else {
        facets =
            LongRangeFacetCounts(
                "field", null as MultiLongValuesSource?, sfc, fastMatchQuery, *ranges.requireNoNulls())
      }
      var result: FacetResult = facets.getAllChildren("field")!!
      assertEquals(numRange, result.labelValues.size)
      var topNResult: FacetResult = facets.getTopChildren(numRange, "field")!!
      expectedTopNChildrenCounts.sort()

      for (rangeID in 0 until numRange) {
        if (VERBOSE) {
          println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID])
        }
        var subNode: LabelAndValue = result.labelValues[rangeID]
        assertEquals("r" + rangeID, subNode.label)
        assertEquals(expectedCounts[rangeID], subNode.value.toInt())
        // test topNChildren and assert topNResults are sorted by count
        if (rangeID < topNResult.labelValues.size) {
          var topNsubNode: LabelAndValue = topNResult.labelValues[rangeID]
          assertEquals(
              expectedTopNChildrenCounts[numRange - rangeID - 1], topNsubNode.value.toInt())
        }

        var range: LongRange = ranges[rangeID]!!

        // Test drill-down:
        var ddq: DrillDownQuery = DrillDownQuery(config)
        if (random().nextBoolean()) {
          ddq.add("field", LongPoint.newRangeQuery("field", range.min, range.max))
        } else {
          ddq.add(
              "field",
              SortedNumericDocValuesField.newSlowRangeQuery("field", range.min, range.max))
        }
        assertEquals(expectedCounts[rangeID], s.count(ddq))
      }
    }

    w.close()
    IOUtils.close(r, dir)
  }

  @Test

  fun testRandomDoublesSingleValued() {
    var dir: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var numDocs: Int = atLeast(1000)
    var values: DoubleArray = DoubleArray(numDocs)
    var minValue: Double = Double.POSITIVE_INFINITY
    var maxValue: Double = Double.NEGATIVE_INFINITY
    for (i in 0 until numDocs) {
      var doc: Document = Document()
      var v: Double = random().nextDouble()
      values[i] = v
      doc.add(DoubleDocValuesField("field", v))
      doc.add(DoublePoint("field", v))
      w.addDocument(doc)
      minValue = minOf(minValue, v)
      maxValue = maxOf(maxValue, v)
    }
    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r, false)
    var config: FacetsConfig = FacetsConfig()

    var numIters: Int = atLeast(10)
    for (iter in 0 until numIters) {
      if (VERBOSE) {
        println("TEST: iter=" + iter)
      }
      var numRange: Int = TestUtil.nextInt(random(), 1, 5)
      var ranges: Array<DoubleRange?> = arrayOfNulls<DoubleRange>(numRange)
      var expectedCounts: IntArray = IntArray(numRange)
      var expectedTopNChildrenCounts: IntArray = IntArray(numRange)
      var minAcceptedValue: Double = Double.POSITIVE_INFINITY
      var maxAcceptedValue: Double = Double.NEGATIVE_INFINITY
      for (rangeID in 0 until numRange) {
        var min: Double
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: DoubleRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            min = prevRange.min
          } else {
            min = prevRange.max
          }
        } else {
          min = random().nextDouble()
        }
        var max: Double
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: DoubleRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            max = prevRange.min
          } else {
            max = prevRange.max
          }
        } else {
          max = random().nextDouble()
        }

        if (min > max) {
          var x: Double = min
          min = max
          max = x
        }

        var minIncl: Boolean
        var maxIncl: Boolean

        var minAsLong: Long = NumericUtils.doubleToSortableLong(min)
        var maxAsLong: Long = NumericUtils.doubleToSortableLong(max)
        // NOTE: maxAsLong - minAsLong >= 0 is here to handle the common overflow case!
        if (maxAsLong - minAsLong >= 0 && maxAsLong - minAsLong < 2) {
          minIncl = true
          maxIncl = true
        } else {
          minIncl = random().nextBoolean()
          maxIncl = random().nextBoolean()
        }
        ranges[rangeID] = DoubleRange("r" + rangeID, min, minIncl, max, maxIncl)

        // Do "slow but hopefully correct" computation of
        // expected count:
        for (i in 0 until numDocs) {
          var accept: Boolean = true
          if (minIncl) {
            accept = accept and (values[i] >= min)
          } else {
            accept = accept and (values[i] > min)
          }
          if (maxIncl) {
            accept = accept and (values[i] <= max)
          } else {
            accept = accept and (values[i] < max)
          }
          if (accept) {
            expectedCounts[rangeID]++
            expectedTopNChildrenCounts[rangeID]++
            minAcceptedValue = minOf(minAcceptedValue, values[i])
            maxAcceptedValue = maxOf(maxAcceptedValue, values[i])
          }
        }
      }

      var sfc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
      var fastMatchFilter: Query?
      if (random().nextBoolean()) {
        if (random().nextBoolean()) {
          fastMatchFilter = DoublePoint.newRangeQuery("field", minValue, maxValue)
        } else {
          fastMatchFilter = DoublePoint.newRangeQuery("field", minAcceptedValue, maxAcceptedValue)
        }
      } else {
        fastMatchFilter = null
      }
      var vs: DoubleValuesSource = DoubleValuesSource.fromDoubleField("field")
      var mvs: MultiDoubleValuesSource = MultiDoubleValuesSource.fromDoubleField("field")
      var facets: Facets
      if (random().nextBoolean()) {
        facets = DoubleRangeFacetCounts("field", vs, sfc, fastMatchFilter, *ranges.requireNoNulls())
      } else if (random().nextBoolean()) {
        facets =
            DoubleRangeFacetCounts(
                "field",
                MultiDoubleValuesSource.fromSingleValued(vs),
                sfc,
                fastMatchFilter,
                *ranges.requireNoNulls())
      } else {
        facets = DoubleRangeFacetCounts("field", mvs, sfc, fastMatchFilter, *ranges.requireNoNulls())
      }
      var result: FacetResult = facets.getAllChildren("field")!!
      assertEquals(numRange, result.labelValues.size)
      var topNResult: FacetResult = facets.getTopChildren(numRange, "field")!!
      expectedTopNChildrenCounts.sort()

      for (rangeID in 0 until numRange) {
        if (VERBOSE) {
          println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID])
        }
        var subNode: LabelAndValue = result.labelValues[rangeID]
        assertEquals("r" + rangeID, subNode.label)
        assertEquals(expectedCounts[rangeID], subNode.value.toInt())
        // test topNChildren and assert topNResults are sorted by count
        if (rangeID < topNResult.labelValues.size) {
          var topNsubNode: LabelAndValue = topNResult.labelValues[rangeID]
          assertEquals(
              expectedTopNChildrenCounts[numRange - rangeID - 1], topNsubNode.value.toInt())
        }

        var range: DoubleRange = ranges[rangeID]!!

        // Test drill-down:
        var ddq: DrillDownQuery = DrillDownQuery(config)
        if (random().nextBoolean()) {
          ddq.add("field", DoublePoint.newRangeQuery("field", range.min, range.max))
        } else if (random().nextBoolean()) {
          ddq.add("field", range.getQuery(fastMatchFilter, vs))
        } else {
          ddq.add("field", range.getQuery(fastMatchFilter, mvs))
        }

        assertEquals(expectedCounts[rangeID], s.count(ddq))
      }
    }

    w.close()
    IOUtils.close(r, dir)
  }

  @Test

  fun testRandomDoublesMultiValued() {
    var dir: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var numDocs: Int = atLeast(1000)
    var values: Array<DoubleArray> = Array(numDocs) { DoubleArray(0) }
    var minValue: Double = Double.POSITIVE_INFINITY
    var maxValue: Double = Double.NEGATIVE_INFINITY
    for (i in 0 until numDocs) {
      var doc: Document = Document()
      var numVals: Int = TestUtil.nextInt(random(), 1, 50)
      if (random().nextInt(10) == 0) {
        numVals = 1 // make sure we have ample testing of single-value cases
      }
      values[i] = DoubleArray(numVals)
      for (j in 0 until numVals) {
        var v: Double = random().nextDouble()
        values[i][j] = v
        doc.add(SortedNumericDocValuesField("field", v.toBits()))
        doc.add(DoublePoint("field", v))
        minValue = minOf(minValue, v)
        maxValue = maxOf(maxValue, v)
      }
      w.addDocument(doc)
    }
    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r, false)
    var config: FacetsConfig = FacetsConfig()

    var numIters: Int = atLeast(10)
    for (iter in 0 until numIters) {
      if (VERBOSE) {
        println("TEST: iter=" + iter)
      }
      var numRange: Int = TestUtil.nextInt(random(), 1, 5)
      var ranges: Array<DoubleRange?> = arrayOfNulls<DoubleRange>(numRange)
      var expectedCounts: IntArray = IntArray(numRange)
      var expectedTopNChildrenCounts: IntArray = IntArray(numRange)
      var minAcceptedValue: Double = Double.POSITIVE_INFINITY
      var maxAcceptedValue: Double = Double.NEGATIVE_INFINITY
      for (rangeID in 0 until numRange) {
        var min: Double
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: DoubleRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            min = prevRange.min
          } else {
            min = prevRange.max
          }
        } else {
          min = random().nextDouble()
        }
        var max: Double
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: DoubleRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            max = prevRange.min
          } else {
            max = prevRange.max
          }
        } else {
          max = random().nextDouble()
        }

        if (min > max) {
          var x: Double = min
          min = max
          max = x
        }

        var minIncl: Boolean
        var maxIncl: Boolean

        var minAsLong: Long = NumericUtils.doubleToSortableLong(min)
        var maxAsLong: Long = NumericUtils.doubleToSortableLong(max)
        // NOTE: maxAsLong - minAsLong >= 0 is here to handle the common overflow case!
        if (maxAsLong - minAsLong >= 0 && maxAsLong - minAsLong < 2) {
          minIncl = true
          maxIncl = true
        } else {
          minIncl = random().nextBoolean()
          maxIncl = random().nextBoolean()
        }
        ranges[rangeID] = DoubleRange("r" + rangeID, min, minIncl, max, maxIncl)

        // Do "slow but hopefully correct" computation of
        // expected count:
        for (i in 0 until numDocs) {
          for (j in 0 until values[i].size) {
            var accept: Boolean = true
            if (minIncl) {
              accept = accept and (values[i][j] >= min)
            } else {
              accept = accept and (values[i][j] > min)
            }
            if (maxIncl) {
              accept = accept and (values[i][j] <= max)
            } else {
              accept = accept and (values[i][j] < max)
            }
            if (accept) {
              expectedCounts[rangeID]++
              expectedTopNChildrenCounts[rangeID]++
              minAcceptedValue = minOf(minAcceptedValue, values[i][j])
              maxAcceptedValue = maxOf(maxAcceptedValue, values[i][j])
              break // ensure each doc can contribute at most 1 count to each range
            }
          }
        }
      }

      var sfc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
      var fastMatchFilter: Query?
      if (random().nextBoolean()) {
        if (random().nextBoolean()) {
          fastMatchFilter = DoublePoint.newRangeQuery("field", minValue, maxValue)
        } else {
          fastMatchFilter = DoublePoint.newRangeQuery("field", minAcceptedValue, maxAcceptedValue)
        }
      } else {
        fastMatchFilter = null
      }
      var facets: Facets
      if (random().nextBoolean()) {
        facets =
            DoubleRangeFacetCounts(
                "field",
                MultiDoubleValuesSource.fromDoubleField("field"),
                sfc,
                fastMatchFilter,
                *ranges.requireNoNulls())
      } else {
        facets =
            DoubleRangeFacetCounts(
                "field", null as MultiDoubleValuesSource?, sfc, fastMatchFilter, *ranges.requireNoNulls())
      }
      var result: FacetResult = facets.getAllChildren("field")!!
      assertEquals(numRange, result.labelValues.size)
      var topNResult: FacetResult = facets.getTopChildren(numRange, "field")!!
      expectedTopNChildrenCounts.sort()
      for (rangeID in 0 until numRange) {
        if (VERBOSE) {
          println("  range " + rangeID + " expectedCount=" + expectedCounts[rangeID])
        }
        var subNode: LabelAndValue = result.labelValues[rangeID]
        assertEquals("r" + rangeID, subNode.label)
        assertEquals(expectedCounts[rangeID], subNode.value.toInt())
        // test topNChildren and assert topNResults are sorted by count
        if (rangeID < topNResult.labelValues.size) {
          var topNsubNode: LabelAndValue = topNResult.labelValues[rangeID]
          assertEquals(
              expectedTopNChildrenCounts[numRange - rangeID - 1], topNsubNode.value.toInt())
        }

        var range: DoubleRange = ranges[rangeID]!!

        // Test drill-down:
        var ddq: DrillDownQuery = DrillDownQuery(config)
        if (random().nextBoolean()) {
          ddq.add("field", DoublePoint.newRangeQuery("field", range.min, range.max))
        } else {
          ddq.add(
              "field",
              SortedNumericDocValuesField.newSlowRangeQuery(
                  "field", range.min.toBits(), range.max.toBits()))
        }

        assertEquals(expectedCounts[rangeID], s.count(ddq))
      }
    }

    w.close()
    IOUtils.close(r, dir)
  }

  // LUCENE-5178
  @Test
  fun testMissingValues() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: NumericDocValuesField = NumericDocValuesField("field", 0L)
    doc.add(field)
    for (l in 0L until 100L) {
      if (l % 5L == 0L) {
        // Every 5th doc is missing the value:
        w.addDocument(Document())
        continue
      }
      field.setLongValue(l)
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
    var facets: Facets =
        LongRangeFacetCounts(
            "field",
            fc,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, false))

    assertEquals(
        "dim=field path=[] value=16 childCount=5\n  less than 10 (8)\n  less than or equal to 10 (8)\n  over 90 (8)\n  90 or above (8)\n  over 1000 (0)\n",
        facets.getAllChildren("field").toString())!!
    assertEquals(
        "dim=field path=[] value=16 childCount=4\n  90 or above (8)\n  less than 10 (8)\n  less than or equal to 10 (8)\n  over 90 (8)\n",
        facets.getTopChildren(4, "field").toString())!!

    w.close()
    IOUtils.close(r, d)
  }

  @Test

  fun testMissingValuesMultiValued() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    // index the same field twice to test multi-valued logic
    var field1: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    var field2: SortedNumericDocValuesField = SortedNumericDocValuesField("field", 0L)
    doc.add(field1)
    doc.add(field2)
    for (l in 0L until 100L) {
      if (l % 5L == 0L) {
        // Every 5th doc is missing the value:
        w.addDocument(Document())
        continue
      }
      field1.setLongValue(l.toLong())
      field2.setLongValue(l.toLong())
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
    var facets: Facets =
        LongRangeFacetCounts(
            "field",
            fc,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, false))

    assertEquals(
        "dim=field path=[] value=16 childCount=5\n  less than 10 (8)\n  less than or equal to 10 (8)\n  over 90 (8)\n  90 or above (8)\n  over 1000 (0)\n",
        facets.getAllChildren("field").toString())!!
    assertEquals(
        "dim=field path=[] value=16 childCount=4\n  90 or above (8)\n  less than 10 (8)\n  less than or equal to 10 (8)\n  over 90 (8)\n",
        facets.getTopChildren(4, "field").toString())!!

    w.close()
    IOUtils.close(r, d)
  }

  private class UsedQuery(private val queryIn: Query, private val used: AtomicBoolean) : Query() {

    override fun equals(other: Any?): Boolean {
      return sameClassAs(other) && queryIn.equals((other as UsedQuery).queryIn)
    }

    override fun hashCode(): Int {
      return classHash() + queryIn.hashCode()
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
      val inRewritten: Query = queryIn.rewrite(indexSearcher)
      if (queryIn !== inRewritten) {
        return UsedQuery(inRewritten, used)
      }
      return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
      queryIn.visit(visitor)
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
      val weightIn: Weight = queryIn.createWeight(searcher, scoreMode, boost)
      return object : FilterWeight(weightIn) {
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
          val scorer = weightIn.scorer(context)
          if (scorer == null) return null
          return object : ScorerSupplier() {
            override fun get(leadCost: Long): Scorer {
              used.store(true)
              return scorer
            }

            override fun cost(): Long {
              return scorer.iterator().cost()
            }
          }
        }
      }
    }

    override fun toString(field: String?): String {
      return "UsedQuery(" + queryIn + ")"
    }
  }

  private class PlusOneValuesSource : DoubleValuesSource() {

    override fun getValues(ctx: LeafReaderContext, scores: DoubleValues?): DoubleValues {
      return object : DoubleValues() {
        var doc: Int = -1

        override fun doubleValue(): Double {
          return (doc + 1).toDouble()
        }

        override fun advanceExact(doc: Int): Boolean {
          this.doc = doc
          return true
        }
      }
    }

    override fun needsScores(): Boolean {
      return false
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
      return false
    }

    override fun explain(ctx: LeafReaderContext, docId: Int, scoreExplanation: Explanation): Explanation {
      return Explanation.match(docId + 1, "")
    }

    override fun rewrite(searcher: IndexSearcher): DoubleValuesSource {
      return this
    }

    override fun hashCode(): Int {
      return 0
    }

    override fun equals(obj: Any?): Boolean {
      return obj is PlusOneValuesSource
    }

    override fun toString(): String {
      // Kotlin requires a non-null string; this test source has no display representation.
      return "null"
    }
  }

  @Test

  fun testCustomDoubleValuesSource() {
    var dir: Directory = newDirectory()
    var writer: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var doc: Document = Document()
    writer.addDocument(doc)
    writer.addDocument(doc)
    writer.addDocument(doc)

    // Test wants 3 docs in one segment:
    writer.forceMerge(1)

    val vs: DoubleValuesSource = PlusOneValuesSource()

    var config: FacetsConfig = FacetsConfig()

    var r: IndexReader = writer.reader

    var s: IndexSearcher = newSearcher(r, false, false, Concurrency.INTER_SEGMENT)
    // DrillSideways requires the entire range of docs to be scored at once, so it doesn't support
    // timeouts whose implementation scores one window of doc IDs at a time.
    s.timeout = null
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    val ranges: Array<DoubleRange> =
        arrayOf(
          DoubleRange("< 1", 0.0, true, 1.0, false),
          DoubleRange("< 2", 0.0, true, 2.0, false),
          DoubleRange("< 5", 0.0, true, 5.0, false),
          DoubleRange("< 10", 0.0, true, 10.0, false),
          DoubleRange("< 20", 0.0, true, 20.0, false),
          DoubleRange("< 50", 0.0, true, 50.0, false)
        )

    val fastMatchFilter: Query?
    val filterWasUsed: AtomicBoolean = AtomicBoolean(false)
    if (random().nextBoolean()) {
      // Sort of silly:
      val queryIn: Query = MatchAllDocsQuery()
      fastMatchFilter = UsedQuery(queryIn, filterWasUsed)
    } else {
      fastMatchFilter = null
    }

    if (VERBOSE) {
      println("TEST: fastMatchFilter=" + fastMatchFilter)
    }

    var facets: Facets
    if (random().nextBoolean()) {
      facets = DoubleRangeFacetCounts("field", vs, fc, fastMatchFilter, *ranges)
    } else {
      facets =
          DoubleRangeFacetCounts(
              "field", MultiDoubleValuesSource.fromSingleValued(vs), fc, fastMatchFilter, *ranges)
    }

    assertEquals(
        "dim=field path=[] value=3 childCount=6\n  < 1 (0)\n  < 2 (1)\n  < 5 (3)\n  < 10 (3)\n  < 20 (3)\n  < 50 (3)\n",
        facets.getAllChildren("field").toString())!!
    assertEquals(
        "dim=field path=[] value=3 childCount=5\n  < 10 (3)\n  < 20 (3)\n  < 5 (3)\n  < 50 (3)\n  < 2 (1)\n",
        facets.getTopChildren(5, "field").toString())!!
    assertTrue(fastMatchFilter == null || filterWasUsed.load())

    var ddq: DrillDownQuery = DrillDownQuery(config)
    if (random().nextBoolean()) {
      ddq.add("field", ranges[1].getQuery(fastMatchFilter, vs))
    } else {
      ddq.add(
          "field",
          ranges[1].getQuery(fastMatchFilter, MultiDoubleValuesSource.fromSingleValued(vs)))
    }

    // Test simple drill-down:
    assertEquals(1L, s.search(ddq, 10).totalHits.value)

    // Test drill-sideways after drill-down
    var ds: DrillSideways =
        object : DrillSideways(s, config, null as TaxonomyReader?) {

          override protected fun buildFacetsResult(drillDowns: FacetsCollector?, drillSideways: Array<FacetsCollector>?, drillSidewaysDims: Array<String>?): Facets {
            assertEquals(1, drillSideways!!.size)
            return DoubleRangeFacetCounts(
                "field",
                MultiDoubleValuesSource.fromSingleValued(vs),
                drillSideways!![0],
                fastMatchFilter,
                *ranges)
          }

          override protected fun scoreSubDocsAtOnce(): Boolean {
            return random().nextBoolean()
          }
        }

    var dsr: DrillSidewaysResult = ds.search(ddq, 10)
    assertEquals(1L, dsr.hits!!.totalHits.value)
    assertEquals(
        "dim=field path=[] value=3 childCount=6\n  < 1 (0)\n  < 2 (1)\n  < 5 (3)\n  < 10 (3)\n  < 20 (3)\n  < 50 (3)\n",
        dsr.facets!!.getAllChildren("field").toString())
    assertEquals(
        "dim=field path=[] value=3 childCount=5\n  < 10 (3)\n  < 20 (3)\n  < 5 (3)\n  < 50 (3)\n  < 2 (1)\n",
        dsr.facets!!.getTopChildren(5, "field").toString())

    writer.close()
    IOUtils.close(r, dir)
  }

  @Test

  fun testLongRangeEquals() {
    assertEquals(
        LongRange("field", -7, true, 17, false), LongRange("field", -7, true, 17, false))
    assertEquals(
        LongRange("field", -7, true, 17, false).hashCode(),
        LongRange("field", -7, true, 17, false).hashCode())
    assertFalse(
        LongRange("field", -7, true, 17, false)
            .equals(LongRange("field", -7, true, 17, true)))
    assertFalse(
        LongRange("field", -7, true, 17, false).hashCode()
            == LongRange("field", -7, true, 17, true).hashCode())
    assertFalse(
        LongRange("field", -7, true, 17, false)
            .equals(LongRange("field", -7, true, 18, false)))
    assertFalse(
        LongRange("field", -7, true, 17, false).hashCode()
            == LongRange("field", -7, true, 18, false).hashCode())
  }

  @Test

  fun testDoubleRangeEquals() {
    assertEquals(
        DoubleRange("field", -7.0, true, 17.0, false),
        DoubleRange("field", -7.0, true, 17.0, false))
    assertEquals(
        DoubleRange("field", -7.0, true, 17.0, false).hashCode(),
        DoubleRange("field", -7.0, true, 17.0, false).hashCode())
    assertFalse(
        DoubleRange("field", -7.0, true, 17.0, false)
            .equals(DoubleRange("field", -7.0, true, 17.0, true)))
    assertFalse(
        DoubleRange("field", -7.0, true, 17.0, false).hashCode()
            == DoubleRange("field", -7.0, true, 17.0, true).hashCode())
    assertFalse(
        DoubleRange("field", -7.0, true, 17.0, false)
            .equals(DoubleRange("field", -7.0, true, 18.0, false)))
    assertFalse(
        DoubleRange("field", -7.0, true, 17.0, false).hashCode()
            == DoubleRange("field", -7.0, true, 18.0, false).hashCode())
  }
}
