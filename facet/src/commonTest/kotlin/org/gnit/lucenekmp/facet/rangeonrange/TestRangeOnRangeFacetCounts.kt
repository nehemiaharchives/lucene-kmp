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
package org.gnit.lucenekmp.facet.rangeonrange

import org.gnit.lucenekmp.document.LongRange.Companion.verifyAndEncode
import org.gnit.lucenekmp.document.DoubleRange.Companion.verifyAndEncode
import kotlin.test.*
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Concurrency


import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleRangeDocValuesField
import org.gnit.lucenekmp.document.LongRangeDocValuesField
import org.gnit.lucenekmp.document.RangeFieldQuery
import org.gnit.lucenekmp.facet.DrillDownQuery
import org.gnit.lucenekmp.facet.DrillSideways
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetResult
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.Facets
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollectorManager
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.LabelAndValue
import org.gnit.lucenekmp.facet.MultiFacets
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils

class TestRangeOnRangeFacetCounts : FacetTestCase() {

  @Test

  fun testBasicSingleDimLong() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: LongRangeDocValuesField =
        LongRangeDocValuesField("field", longArray(0L), longArray(10L))
    doc.add(field)
    for (l in 0L until 100L) {
      setLongRangeValue(field, l, 10L + l)
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    setLongRangeValue(field, Long.MAX_VALUE - 10L, Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, true))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=32 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (19)\n  90 or above (20)\n  over 1000 (1)\n",
        result.toString())

    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=32 childCount=5\n  90 or above (20)\n  over 90 (19)\n  less than or equal to 10 (11)\n  less than 10 (10)\n",
        result.toString())

    // test getTopChildren(0, dim)
    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(0, "field") }

    r.close()
    d.close()
  }

  @Test

  fun testBasicMultiDimLong() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var min: LongArray = longArray(0L, 1L, 2L)
    var max: LongArray = longArray(10L, 11L, 12L)
    var field: LongRangeDocValuesField = LongRangeDocValuesField("field", min, max)
    doc.add(field)
    for (l in 0L until 100L) {
      setLongRangeValue(field, longArray(l, l + 1L, l + 2L), longArray(l + 10L, l + 11L, l + 12L))
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    var infMin: LongArray = longArray(Long.MAX_VALUE - 12L, Long.MAX_VALUE - 11L, Long.MAX_VALUE - 10L)
    var infMax: LongArray = longArray(Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE)
    setLongRangeValue(field, infMin, infMax)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            LongRange("less than 10", longArray(0L, 1L, 2L), longArray(8L, 9L, 10L)),
            LongRange("a bit over 10", longArray(0L, 1L, 2L), longArray(10L, 11L, 12L)),
            LongRange("a bit under 90", longArray(88L, 89L, 90L), longArray(100L, 101L, 102L)),
            LongRange("over 90", longArray(90L, 91L, 92L), longArray(100L, 101L, 102L)),
            LongRange(
                "over 1000",
                longArray(1000L, 1001L, 1002L),
                longArray(Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE)))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=34 childCount=5\n  less than 10 (9)\n  a bit over 10 (11)\n  a bit under 90 (22)\n  over 90 (20)\n  over 1000 (1)\n",
        result.toString())

    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=34 childCount=5\n  a bit under 90 (22)\n  over 90 (20)\n  a bit over 10 (11)\n  less than 10 (9)\n",
        result.toString())

    // test getTopChildren(0, dim)
    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(0, "field") }

    r.close()
    d.close()
  }

  @Test

  fun testLongGetAllDimsSingleDim() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: LongRangeDocValuesField =
        LongRangeDocValuesField("field", longArray(0L), longArray(10L))
    doc.add(field)
    for (l in 0L until 100L) {
      setLongRangeValue(field, l, 10L + l)
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    setLongRangeValue(field, Long.MAX_VALUE - 10L, Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, true))

    var result: List<FacetResult> = facets.getAllDims(10)
    assertEquals(1, result.size)
    assertEquals(
        "dim=field path=[] value=32 childCount=5\n  90 or above (20)\n  over 90 (19)\n  less than or equal to 10 (11)\n  less than 10 (10)\n  over 1000 (1)\n",
        result.get(0).toString())

    // test getAllDims(1)
    result = facets.getAllDims(1)
    assertEquals(1, result.size)
    assertEquals(
        "dim=field path=[] value=32 childCount=5\n  90 or above (20)\n", result.get(0).toString())

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

  fun testGetAllDimsMultiDim() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var min: LongArray = longArray(0L, 1L, 2L)
    var max: LongArray = longArray(10L, 11L, 12L)
    var field: LongRangeDocValuesField = LongRangeDocValuesField("field", min, max)
    doc.add(field)
    for (l in 0L until 100L) {
      var newMin: LongArray = longArray(l, l + 1L, l + 2L)
      var newMax: LongArray = longArray(l + 10L, l + 11L, l + 12L)
      setLongRangeValue(field, newMin, newMax)
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    var infMin: LongArray = longArray(Long.MAX_VALUE - 12L, Long.MAX_VALUE - 11L, Long.MAX_VALUE - 10L)
    var infMax: LongArray = longArray(Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE)
    setLongRangeValue(field, infMin, infMax)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            LongRange("less than 10", longArray(0L, 1L, 2L), longArray(8L, 9L, 10L)),
            LongRange("a bit over 10", longArray(0L, 1L, 2L), longArray(10L, 11L, 12L)),
            LongRange("a bit under 90", longArray(88L, 89L, 90L), longArray(100L, 101L, 102L)),
            LongRange("over 90", longArray(90L, 91L, 92L), longArray(100L, 101L, 102L)),
            LongRange(
                "over 1000",
                longArray(1000L, 1001L, 1002L),
                longArray(Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE)))

    var result: List<FacetResult> = facets.getAllDims(10)
    assertEquals(1, result.size)
    assertEquals(
        "dim=field path=[] value=34 childCount=5\n  a bit under 90 (22)\n  over 90 (20)\n  a bit over 10 (11)\n  less than 10 (9)\n  over 1000 (1)\n",
        result.get(0).toString())

    // test getAllDims(1)
    result = facets.getAllDims(1)
    assertEquals(1, result.size)
    assertEquals(
        "dim=field path=[] value=34 childCount=5\n  a bit under 90 (22)\n",
        result.get(0).toString())

    // test getTopChildren(0, dim)
    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(0, "field") }

    r.close()
    d.close()
  }

  @Test

  fun testUselessRangeSingleDim() {
    expectThrows(IllegalArgumentException::class) { LongRange("useless", 7, true, 6, true) }
    expectThrows(IllegalArgumentException::class) { LongRange("useless", 7, true, 7, false) }
    expectThrows(IllegalArgumentException::class) { DoubleRange("useless", 7.0, true, 6.0, true) }
    expectThrows(IllegalArgumentException::class) { DoubleRange("useless", 7.0, true, 7.0, false) }
  }

  @Test

  fun testUselessMultiDimRange() {
    expectThrows(IllegalArgumentException::class) { LongRange("useless", longArray(7L, 7L), longArray(6L, 6L)) }
    expectThrows(IllegalArgumentException::class) { LongRange("useless", longArray(7L, 7L), longArray(7L, 6L)) }
    expectThrows(IllegalArgumentException::class) { DoubleRange("useless", doubleArray(7.0, 7.0), doubleArray(6.0, 6.0)) }
    expectThrows(IllegalArgumentException::class) { DoubleRange("useless", doubleArray(7.0, 7.0), doubleArray(7.0, 6.0)) }
  }

  @Test

  fun testSingleDimLongMinMax() {

    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: LongRangeDocValuesField =
        LongRangeDocValuesField("field", longArray(0L), longArray(10L))
    doc.add(field)
    setLongRangeValue(field, Long.MIN_VALUE, Long.MIN_VALUE + 10L)
    w.addDocument(doc)
    setLongRangeValue(field, 0L, 10L)
    w.addDocument(doc)
    setLongRangeValue(field, Long.MAX_VALUE - 10L, Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            LongRange("min", Long.MIN_VALUE, true, Long.MIN_VALUE, true),
            LongRange("max", Long.MAX_VALUE, true, Long.MAX_VALUE, true),
            LongRange("all0", Long.MIN_VALUE, true, Long.MAX_VALUE, true),
            LongRange("all1", Long.MIN_VALUE, false, Long.MAX_VALUE, true),
            LongRange("all2", Long.MIN_VALUE, true, Long.MAX_VALUE, false),
            LongRange("all3", Long.MIN_VALUE, false, Long.MAX_VALUE, false))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=3 childCount=6\n  min (1)\n  max (1)\n  all0 (3)\n  all1 (3)\n  all2 (3)\n  all3 (3)\n",
        result.toString())

    result = facets.getTopChildren(5, "field")!!
    assertEquals(
        "dim=field path=[] value=3 childCount=6\n  all0 (3)\n  all1 (3)\n  all2 (3)\n  all3 (3)\n  max (1)\n",
        result.toString())

    r.close()
    d.close()
  }

  @Test

  fun testMultiDimLongMinMax() {

    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: LongRangeDocValuesField =
        LongRangeDocValuesField("field", longArray(0L, 1L), longArray(1L, 2L))
    doc.add(field)
    setLongRangeValue(
        field,
        longArray(Long.MIN_VALUE, Long.MIN_VALUE),
        longArray(Long.MIN_VALUE + 10L, Long.MIN_VALUE + 11L))
    w.addDocument(doc)
    setLongRangeValue(field, longArray(0L, 1L), longArray(10L, 11L))
    w.addDocument(doc)
    setLongRangeValue(
        field,
        longArray(Long.MAX_VALUE - 11L, Long.MAX_VALUE - 10L),
        longArray(Long.MAX_VALUE, Long.MAX_VALUE))
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            LongRange(
                "min",
                longArray(Long.MIN_VALUE, Long.MIN_VALUE),
                longArray(Long.MIN_VALUE, Long.MIN_VALUE)),
            LongRange(
                "max",
                longArray(Long.MAX_VALUE, Long.MAX_VALUE),
                longArray(Long.MAX_VALUE, Long.MAX_VALUE)),
            LongRange(
                "all0",
                longArray(Long.MIN_VALUE, Long.MIN_VALUE),
                longArray(Long.MIN_VALUE, Long.MAX_VALUE)),
            LongRange(
                "all1",
                longArray(Long.MIN_VALUE, Long.MIN_VALUE),
                longArray(Long.MAX_VALUE, Long.MIN_VALUE)),
            LongRange(
                "all2",
                longArray(Long.MIN_VALUE, Long.MIN_VALUE),
                longArray(Long.MAX_VALUE, Long.MAX_VALUE)))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=3 childCount=5\n  min (1)\n  max (1)\n  all0 (1)\n  all1 (1)\n  all2 (3)\n",
        result.toString())

    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=3 childCount=5\n  all2 (3)\n  all0 (1)\n  all1 (1)\n  max (1)\n",
        result.toString())

    r.close()
    d.close()
  }

  @Test

  fun testSingleDimOverlappedEndStart() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: LongRangeDocValuesField =
        LongRangeDocValuesField("field", longArray(0L), longArray(10L))
    doc.add(field)
    for (l in 0L until 100L) {
      setLongRangeValue(field, l, 10L + l)
      w.addDocument(doc)
    }

    setLongRangeValue(field, Long.MAX_VALUE - 10L, Long.MAX_VALUE)
    w.addDocument(doc)

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        LongRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            LongRange("0-10", 0L, true, 10L, true),
            LongRange("10-20", 10L, true, 20L, true),
            LongRange("20-30", 20L, true, 30L, true),
            LongRange("30-40", 30L, true, 40L, true))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=41 childCount=4\n  0-10 (11)\n  10-20 (21)\n  20-30 (21)\n  30-40 (21)\n",
        result.toString())

    result = facets.getTopChildren(3, "field")!!
    assertEquals(
        "dim=field path=[] value=41 childCount=4\n  10-20 (21)\n  20-30 (21)\n  30-40 (21)\n",
        result.toString())
    r.close()
    d.close()
  }

  @Test

  fun testSingleDimMixedRangeAndNonRangeTaxonomy() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var td: Directory = newDirectory()
    var tw: DirectoryTaxonomyWriter = DirectoryTaxonomyWriter(td, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()

    for (l in 0L until 100L) {
      var doc: Document = Document()
      // For computing range facet counts:
      doc.add(LongRangeDocValuesField("field", longArray(l), longArray(l + 10L)))
      // For drill down by numeric range:
      doc.add(org.gnit.lucenekmp.document.LongRange("field", longArray(l), longArray(l + 10L)))

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
                LongRangeOnRangeFacetCounts(
                    "field",
                    fieldFC,
                    RangeFieldQuery.QueryType.INTERSECTS,
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
    var dsr: DrillSideways.DrillSidewaysResult = ds.search(null, ddq, 10)

    assertEquals(100L, dsr.hits!!.totalHits.value)
    assertEquals(
        "dim=dim path=[] value=100 childCount=2\n  b (75)\n  a (25)\n",
        dsr.facets!!.getTopChildren(10, "dim").toString())
    assertEquals(
        "dim=field path=[] value=31 childCount=4\n  90 or above (20)\n  over 90 (19)\n  less than or equal to 10 (11)\n  less than 10 (10)\n",
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
        "dim=field path=[] value=23 childCount=4\n  90 or above (15)\n  over 90 (15)\n  less than or equal to 10 (8)\n  less than 10 (7)\n",
        dsr.facets!!.getTopChildren(10, "field").toString())

    w.close()
    IOUtils.close(tw, tr, td, r, d)
  }

  @Test

  fun testMultiDimMixedRangeAndNonRangeTaxonomy() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var td: Directory = newDirectory()
    var tw: DirectoryTaxonomyWriter = DirectoryTaxonomyWriter(td, IndexWriterConfig.OpenMode.CREATE)

    var config: FacetsConfig = FacetsConfig()

    for (l in 0L until 100L) {
      var doc: Document = Document()
      // For computing range facet counts:
      doc.add(
          LongRangeDocValuesField(
              "field", longArray(l, l + 1L, l + 2L), longArray(l + 10L, l + 11L, l + 12L)))
      // For drill down by numeric range:
      doc.add(
          org.gnit.lucenekmp.document.LongRange(
              "field", longArray(l, l + 1L, l + 2L), longArray(l + 10L, l + 11L, l + 12L)))

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
                LongRangeOnRangeFacetCounts(
                    "field",
                    fieldFC,
                    RangeFieldQuery.QueryType.INTERSECTS,
                    LongRange("less than 10", longArray(0L, 1L, 2L), longArray(8L, 9L, 10L)),
                    LongRange("a bit over 10", longArray(0L, 1L, 2L), longArray(10L, 11L, 12L)),
                    LongRange(
                        "a bit under 90", longArray(88L, 89L, 90L), longArray(100L, 101L, 102L)),
                    LongRange("over 90", longArray(90L, 91L, 92L), longArray(100L, 101L, 102L)),
                    LongRange(
                        "over 1000",
                        longArray(1000L, 1001L, 1002L),
                        longArray(Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE))))
            byDim.put("dim", getTaxonomyFacetCounts(taxoReader!!, config, dimFC))
            return MultiFacets(byDim, null)
          }

          override protected fun scoreSubDocsAtOnce(): Boolean {
            return random().nextBoolean()
          }
        }

    // First search, no drill downs:
    var ddq: DrillDownQuery = DrillDownQuery(config)
    var dsr: DrillSideways.DrillSidewaysResult = ds.search(null, ddq, 10)

    assertEquals(100L, dsr.hits!!.totalHits.value)
    assertEquals(
        "dim=dim path=[] value=100 childCount=2\n  b (75)\n  a (25)\n",
        dsr.facets!!.getTopChildren(10, "dim").toString())
    assertEquals(
        "dim=field path=[] value=33 childCount=4\n  a bit under 90 (22)\n  over 90 (20)\n  a bit over 10 (11)\n  less than 10 (9)\n",
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
        "dim=field path=[] value=25 childCount=4\n  a bit under 90 (17)\n  over 90 (15)\n  a bit over 10 (8)\n  less than 10 (6)\n",
        dsr.facets!!.getTopChildren(10, "field").toString())

    w.close()
    IOUtils.close(tw, tr, td, r, d)
  }

  @Test

  fun testBasicSingleDimDouble() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: DoubleRangeDocValuesField =
        DoubleRangeDocValuesField("field", doubleArray(0.0), doubleArray(10.0))
    doc.add(field)
    for (i in 0 until 100) {
      val l = i.toDouble()
      setDoubleRangeValue(field, l, 10.0 + l)
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        DoubleRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            DoubleRange("less than 10", 0.0, true, 10.0, false),
            DoubleRange("less than or equal to 10", 0.0, true, 10.0, true),
            DoubleRange("over 90", 90.0, false, 100.0, false),
            DoubleRange("90 or above", 90.0, true, 100.0, false),
            DoubleRange("over 1000", 1000.0, false, Double.POSITIVE_INFINITY, false))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=31 childCount=5\n  less than 10 (10)\n  less than or equal to 10 (11)\n  over 90 (19)\n  90 or above (20)\n  over 1000 (0)\n",
        result.toString())

    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=31 childCount=4\n  90 or above (20)\n  over 90 (19)\n  less than or equal to 10 (11)\n  less than 10 (10)\n",
        result.toString())

    // test getTopChildren(0, dim)
    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(0, "field") }

    IOUtils.close(r, d)
  }

  @Test

  fun testBasicMultiDimDouble() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var min: DoubleArray = doubleArray(0.0, 1.0, 2.0)
    var max: DoubleArray = doubleArray(10.0, 11.0, 12.0)
    var field: DoubleRangeDocValuesField = DoubleRangeDocValuesField("field", min, max)
    doc.add(field)
    for (i in 0 until 100) {
      val l = i.toDouble()
      setDoubleRangeValue(
          field, doubleArray(l, l + 1.0, l + 2.0), doubleArray(l + 10.0, l + 11.0, l + 12.0))
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader
    w.close()

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets: Facets =
        DoubleRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            DoubleRange(
                "less than 10", doubleArray(0.0, 1.0, 2.0), doubleArray(8.0, 9.0, 10.0)),
            DoubleRange(
                "a bit over 10", doubleArray(0.0, 1.0, 2.0), doubleArray(10.0, 11.0, 12.0)),
            DoubleRange(
                "a bit under 90", doubleArray(88.0, 89.0, 90.0), doubleArray(100.0, 101.0, 102.0)),
            DoubleRange(
                "over 90", doubleArray(90.0, 91.0, 92.0), doubleArray(100.0, 101.0, 102.0)),
            DoubleRange(
                "over 1000",
                doubleArray(1000.0, 1001.0, 1002.0),
                doubleArray(
                    Double.POSITIVE_INFINITY - 2.0,
                    Double.POSITIVE_INFINITY - 1.0,
                    Double.POSITIVE_INFINITY)))

    var result: FacetResult = facets.getAllChildren("field")!!
    assertEquals(
        "dim=field path=[] value=33 childCount=5\n  less than 10 (9)\n  a bit over 10 (11)\n  a bit under 90 (22)\n  over 90 (20)\n  over 1000 (0)\n",
        result.toString())

    result = facets.getTopChildren(4, "field")!!
    assertEquals(
        "dim=field path=[] value=33 childCount=4\n  a bit under 90 (22)\n  over 90 (20)\n  a bit over 10 (11)\n  less than 10 (9)\n",
        result.toString())

    // test getTopChildren(0, dim)
    expectThrows(IllegalArgumentException::class) { facets.getTopChildren(0, "field") }

    IOUtils.close(r, d)
  }

  @Test

  fun testRandomSingleDimLongs() {
    var dir: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var numDocs: Int = atLeast(1000)
    if (VERBOSE) {
      println("TEST: numDocs=" + numDocs)
    }
    var mins: LongArray = LongArray(numDocs)
    var maxes: LongArray = LongArray(numDocs)
    var minValue: Long = Long.MAX_VALUE
    var maxValue: Long = Long.MIN_VALUE
    for (i in 0 until numDocs) {
      var doc: Document = Document()
      var min: Long = random().nextLong()
      var max: Long = random().nextLong()
      if (min > max) {
        var tmp: Long = min
        min = max
        max = tmp
      }
      mins[i] = min
      maxes[i] = max
      doc.add(LongRangeDocValuesField("field", longArray(min), longArray(max)))
      doc.add(org.gnit.lucenekmp.document.LongRange("field", longArray(min), longArray(max)))
      w.addDocument(doc)
      minValue = minOf(minValue, min)
      maxValue = maxOf(maxValue, min)
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
            min = prevRange.min[0]
          } else {
            min = prevRange.max[0]
          }
        } else {
          min = random().nextLong()
        }
        var max: Long
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: LongRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            max = prevRange.min[0]
          } else {
            max = prevRange.max[0]
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

        var queryPackedValue: ByteArray = ByteArray(2 * Long.SIZE_BYTES)
        var comparator: ArrayUtil.Companion.ByteArrayComparator = ArrayUtil.getUnsignedComparator(Long.SIZE_BYTES)
        verifyAndEncode(longArrayOf(min), longArrayOf(max), queryPackedValue)
        for (i in 0 until numDocs) {
          var packedValue: ByteArray = ByteArray(2 * Long.SIZE_BYTES)
          verifyAndEncode(longArrayOf(mins[i]), longArrayOf(maxes[i]), packedValue)
          if (RangeFieldQuery.QueryType.INTERSECTS.matches(
              queryPackedValue, packedValue, 1, Long.SIZE_BYTES, comparator)) {
            expectedCounts[rangeID]++
            expectedTopNChildrenCounts[rangeID]++
            minAcceptedValue = minOf(minAcceptedValue, mins[i])
            maxAcceptedValue = maxOf(maxAcceptedValue, maxes[i])
          }
        }
      }

      var sfc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
      var fastMatchQuery: Query?
      if (random().nextBoolean()) {
        fastMatchQuery =
            org.gnit.lucenekmp.document.LongRange.newIntersectsQuery(
                "field", longArrayOf(minAcceptedValue), longArrayOf(maxAcceptedValue))
      } else {
        fastMatchQuery = null
      }

      var facets: Facets =
          LongRangeOnRangeFacetCounts(
              "field", sfc, RangeFieldQuery.QueryType.INTERSECTS, fastMatchQuery, *ranges.requireNoNulls())
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
        ddq.add(
            "field",
            org.gnit.lucenekmp.document.LongRange.newIntersectsQuery("field", range.min, range.max))
        assertEquals(expectedCounts[rangeID], s.count(ddq))
      }
    }

    w.close()
    IOUtils.close(r, dir)
  }

  @Test

  fun testRandomMultiDimLongs() {
    var dir: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var numDocs: Int = atLeast(1000)
    var dims: Int = random().nextInt(2, 5)
    if (VERBOSE) {
      println("TEST: numDocs=" + numDocs)
    }
    var mins: Array<LongArray> = Array(numDocs) { LongArray(dims) }
    var maxes: Array<LongArray> = Array(numDocs) { LongArray(dims) }
    for (i in 0 until numDocs) {
      var doc: Document = Document()
      for (dim in 0 until dims) {
        var min: Long = random().nextLong()
        var max: Long = random().nextLong()
        if (min > max) {
          var tmp: Long = min
          min = max
          max = tmp
        }
        mins[i][dim] = min
        maxes[i][dim] = max
      }
      doc.add(LongRangeDocValuesField("field", mins[i], maxes[i]))
      doc.add(org.gnit.lucenekmp.document.LongRange("field", mins[i], maxes[i]))
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
      var minAcceptedValue: LongArray = LongArray(dims)
      minAcceptedValue.fill(Long.MAX_VALUE)
      var maxAcceptedValue: LongArray = LongArray(dims)
      maxAcceptedValue.fill(Long.MIN_VALUE)
      for (rangeID in 0 until numRange) {
        var min: LongArray = LongArray(dims)
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: LongRange = ranges[random().nextInt(rangeID)]!!
          for (dim in 0 until dims) {
            if (random().nextBoolean()) {
              min[dim] = prevRange.min[dim]
            } else {
              min[dim] = prevRange.max[dim]
            }
          }
        } else {
          for (dim in 0 until dims) {
            min[dim] = random().nextLong()
          }
        }
        var max: LongArray = LongArray(dims)
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: LongRange = ranges[random().nextInt(rangeID)]!!
          for (dim in 0 until dims) {
            if (random().nextBoolean()) {
              max[dim] = prevRange.min[dim]
            } else {
              max[dim] = prevRange.max[dim]
            }
          }
        } else {
          for (dim in 0 until dims) {
            max[dim] = random().nextLong()
          }
        }

        for (dim in 0 until dims) {
          if (min[dim] > max[dim]) {
            var x: Long = min[dim]
            min[dim] = max[dim]
            max[dim] = x
          }
        }

        ranges[rangeID] = LongRange("r" + rangeID, min, max)
        if (VERBOSE) {
          println("  range " + rangeID + ": " + ranges[rangeID])
        }

        var queryPackedValue: ByteArray = ByteArray(2 * Long.SIZE_BYTES * dims)
        var comparator: ArrayUtil.Companion.ByteArrayComparator = ArrayUtil.getUnsignedComparator(Long.SIZE_BYTES)
        verifyAndEncode(min, max, queryPackedValue)
        for (i in 0 until numDocs) {
          var packedValue: ByteArray = ByteArray(2 * Long.SIZE_BYTES * dims)
          verifyAndEncode(mins[i], maxes[i], packedValue)
          if (RangeFieldQuery.QueryType.INTERSECTS.matches(
              queryPackedValue, packedValue, dims, Long.SIZE_BYTES, comparator)) {
            expectedCounts[rangeID]++
            expectedTopNChildrenCounts[rangeID]++
            for (dim in 0 until dims) {
              minAcceptedValue[dim] = minOf(minAcceptedValue[dim], mins[i][dim])
              maxAcceptedValue[dim] = maxOf(maxAcceptedValue[dim], maxes[i][dim])
            }
          }
        }
      }

      var sfc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
      var fastMatchQuery: Query?
      if (random().nextBoolean()) {
        fastMatchQuery =
            org.gnit.lucenekmp.document.LongRange.newIntersectsQuery(
                "field", minAcceptedValue, maxAcceptedValue)
      } else {
        fastMatchQuery = null
      }

      var facets: Facets =
          LongRangeOnRangeFacetCounts(
              "field", sfc, RangeFieldQuery.QueryType.INTERSECTS, fastMatchQuery, *ranges.requireNoNulls())
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
        ddq.add(
            "field",
            org.gnit.lucenekmp.document.LongRange.newIntersectsQuery("field", range.min, range.max))
        assertEquals(expectedCounts[rangeID], s.count(ddq))
      }
    }

    w.close()
    IOUtils.close(r, dir)
  }

  @Test

  fun testRandomSingleDimDoubles() {
    var dir: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var numDocs: Int = atLeast(1000)
    if (VERBOSE) {
      println("TEST: numDocs=" + numDocs)
    }
    var mins: DoubleArray = DoubleArray(numDocs)
    var maxes: DoubleArray = DoubleArray(numDocs)
    var minValue: Double = Double.POSITIVE_INFINITY
    var maxValue: Double = Double.NEGATIVE_INFINITY
    for (i in 0 until numDocs) {
      var doc: Document = Document()
      var min: Double = random().nextDouble()
      var max: Double = random().nextDouble()
      if (min > max) {
        var tmp: Double = min
        min = max
        max = tmp
      }
      mins[i] = min
      maxes[i] = max
      doc.add(DoubleRangeDocValuesField("field", doubleArray(min), doubleArray(max)))
      doc.add(
          org.gnit.lucenekmp.document.DoubleRange("field", doubleArray(min), doubleArray(max)))
      w.addDocument(doc)
      minValue = minOf(minValue, min)
      maxValue = maxOf(maxValue, min)
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
            min = prevRange.min[0]
          } else {
            min = prevRange.max[0]
          }
        } else {
          min = random().nextDouble()
        }
        var max: Double
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: DoubleRange = ranges[random().nextInt(rangeID)]!!
          if (random().nextBoolean()) {
            max = prevRange.min[0]
          } else {
            max = prevRange.max[0]
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
        ranges[rangeID] = DoubleRange("r" + rangeID, min, minIncl, max, maxIncl)
        if (VERBOSE) {
          println("  range " + rangeID + ": " + ranges[rangeID])
        }

        var queryPackedValue: ByteArray = ByteArray(2 * Double.SIZE_BYTES)
        verifyAndEncode(doubleArrayOf(min), doubleArrayOf(max), queryPackedValue)
        var comparator: ArrayUtil.Companion.ByteArrayComparator = ArrayUtil.getUnsignedComparator(Double.SIZE_BYTES)
        for (i in 0 until numDocs) {
          var packedValue: ByteArray = ByteArray(2 * Double.SIZE_BYTES)
          verifyAndEncode(doubleArrayOf(mins[i]), doubleArrayOf(maxes[i]), packedValue)
          if (RangeFieldQuery.QueryType.INTERSECTS.matches(
              queryPackedValue, packedValue, 1, Double.SIZE_BYTES, comparator)) {
            expectedCounts[rangeID]++
            expectedTopNChildrenCounts[rangeID]++
            minAcceptedValue = minOf(minAcceptedValue, mins[i])
            maxAcceptedValue = maxOf(maxAcceptedValue, maxes[i])
          }
        }
      }

      var sfc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
      var fastMatchQuery: Query?
      if (random().nextBoolean()) {
        fastMatchQuery =
            org.gnit.lucenekmp.document.DoubleRange.newIntersectsQuery(
                "field", doubleArrayOf(minAcceptedValue), doubleArrayOf(maxAcceptedValue))
      } else {
        fastMatchQuery = null
      }

      var facets: Facets =
          DoubleRangeOnRangeFacetCounts(
              "field", sfc, RangeFieldQuery.QueryType.INTERSECTS, fastMatchQuery, *ranges.requireNoNulls())
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
        ddq.add(
            "field",
            org.gnit.lucenekmp.document.DoubleRange.newIntersectsQuery(
                "field", range.min, range.max))
        assertEquals(expectedCounts[rangeID], s.count(ddq))
      }
    }

    w.close()
    IOUtils.close(r, dir)
  }

  @Test

  fun testRandomMultiDimDoubles() {
    var dir: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), dir)

    var numDocs: Int = atLeast(1000)
    var dims: Int = random().nextInt(2, 5)
    if (VERBOSE) {
      println("TEST: numDocs=" + numDocs)
    }
    var mins: Array<DoubleArray> = Array(numDocs) { DoubleArray(dims) }
    var maxes: Array<DoubleArray> = Array(numDocs) { DoubleArray(dims) }
    for (i in 0 until numDocs) {
      var doc: Document = Document()
      for (dim in 0 until dims) {
        var min: Double = random().nextDouble()
        var max: Double = random().nextDouble()
        if (min > max) {
          var tmp: Double = min
          min = max
          max = tmp
        }
        mins[i][dim] = min
        maxes[i][dim] = max
      }
      doc.add(DoubleRangeDocValuesField("field", mins[i], maxes[i]))
      doc.add(org.gnit.lucenekmp.document.DoubleRange("field", mins[i], maxes[i]))
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
      var ranges: Array<DoubleRange?> = arrayOfNulls<DoubleRange>(numRange)
      var expectedCounts: IntArray = IntArray(numRange)
      var expectedTopNChildrenCounts: IntArray = IntArray(numRange)
      var minAcceptedValue: DoubleArray = DoubleArray(dims)
      minAcceptedValue.fill(Double.POSITIVE_INFINITY)
      var maxAcceptedValue: DoubleArray = DoubleArray(dims)
      maxAcceptedValue.fill(Double.NEGATIVE_INFINITY)
      for (rangeID in 0 until numRange) {
        var min: DoubleArray = DoubleArray(dims)
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: DoubleRange = ranges[random().nextInt(rangeID)]!!
          for (dim in 0 until dims) {
            if (random().nextBoolean()) {
              min[dim] = prevRange.min[dim]
            } else {
              min[dim] = prevRange.max[dim]
            }
          }
        } else {
          for (dim in 0 until dims) {
            min[dim] = random().nextDouble()
          }
        }
        var max: DoubleArray = DoubleArray(dims)
        if (rangeID > 0 && random().nextInt(10) == 7) {
          // Use an existing boundary:
          var prevRange: DoubleRange = ranges[random().nextInt(rangeID)]!!
          for (dim in 0 until dims) {
            if (random().nextBoolean()) {
              max[dim] = prevRange.min[dim]
            } else {
              max[dim] = prevRange.max[dim]
            }
          }
        } else {
          for (dim in 0 until dims) {
            max[dim] = random().nextDouble()
          }
        }

        for (dim in 0 until dims) {
          if (min[dim] > max[dim]) {
            var x: Double = min[dim]
            min[dim] = max[dim]
            max[dim] = x
          }
        }

        ranges[rangeID] = DoubleRange("r" + rangeID, min, max)
        if (VERBOSE) {
          println("  range " + rangeID + ": " + ranges[rangeID])
        }

        var queryPackedValue: ByteArray = ByteArray(2 * Double.SIZE_BYTES * dims)
        var comparator: ArrayUtil.Companion.ByteArrayComparator = ArrayUtil.getUnsignedComparator(Double.SIZE_BYTES)
        verifyAndEncode(min, max, queryPackedValue)
        for (i in 0 until numDocs) {
          var packedValue: ByteArray = ByteArray(2 * Double.SIZE_BYTES * dims)
          verifyAndEncode(mins[i], maxes[i], packedValue)
          if (RangeFieldQuery.QueryType.INTERSECTS.matches(
              queryPackedValue, packedValue, dims, Double.SIZE_BYTES, comparator)) {
            expectedCounts[rangeID]++
            expectedTopNChildrenCounts[rangeID]++
            for (dim in 0 until dims) {
              minAcceptedValue[dim] = minOf(minAcceptedValue[dim], mins[i][dim])
              maxAcceptedValue[dim] = maxOf(maxAcceptedValue[dim], maxes[i][dim])
            }
          }
        }
      }

      var sfc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
      var fastMatchQuery: Query?
      if (random().nextBoolean()) {
        fastMatchQuery =
            org.gnit.lucenekmp.document.DoubleRange.newIntersectsQuery(
                "field", minAcceptedValue, maxAcceptedValue)
      } else {
        fastMatchQuery = null
      }

      var facets: Facets =
          DoubleRangeOnRangeFacetCounts(
              "field", sfc, RangeFieldQuery.QueryType.INTERSECTS, fastMatchQuery, *ranges.requireNoNulls())
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
        ddq.add(
            "field",
            org.gnit.lucenekmp.document.DoubleRange.newIntersectsQuery(
                "field", range.min, range.max))
        assertEquals(expectedCounts[rangeID], s.count(ddq))
      }
    }

    w.close()
    IOUtils.close(r, dir)
  }

  @Test

  fun testMissingValues() {
    var d: Directory = newDirectory()
    var w: RandomIndexWriter = RandomIndexWriter(random(), d)
    var doc: Document = Document()
    var field: LongRangeDocValuesField =
        LongRangeDocValuesField("field", longArray(0L), longArray(10L))
    doc.add(field)
    for (l in 0L until 100L) {
      if (l % 5L == 0L) {
        // Every 5th doc is missing the value:
        w.addDocument(Document())
        continue
      }
      setLongRangeValue(field, l, l + 10L)
      w.addDocument(doc)
    }

    var r: IndexReader = w.reader

    var s: IndexSearcher = newSearcher(r)
    var fc: FacetsCollector = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
    var facets: Facets =
        LongRangeOnRangeFacetCounts(
            "field",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            LongRange("less than 10", 0L, true, 10L, false),
            LongRange("less than or equal to 10", 0L, true, 10L, true),
            LongRange("over 90", 90L, false, 100L, false),
            LongRange("90 or above", 90L, true, 100L, false),
            LongRange("over 1000", 1000L, false, Long.MAX_VALUE, false))

    assertEquals(
        "dim=field path=[] value=24 childCount=5\n  less than 10 (8)\n  less than or equal to 10 (8)\n  over 90 (16)\n  90 or above (16)\n  over 1000 (0)\n",
        facets.getAllChildren("field").toString())!!
    assertEquals(
        "dim=field path=[] value=24 childCount=4\n  90 or above (16)\n  over 90 (16)\n  less than 10 (8)\n  less than or equal to 10 (8)\n",
        facets.getTopChildren(4, "field").toString())!!

    w.close()
    IOUtils.close(r, d)
  }

  @Test

  fun testLongRangeEquals() {
    assertEquals(
        LongRange("field", -7, true, 17, false), LongRange("field", -7, true, 17, false))
    assertEquals(
        LongRange("field", -7, true, 17, false).hashCode(),
        LongRange("field", -7, true, 17, false).hashCode())
    assertNotEquals(
        LongRange("field", -7, true, 17, false), LongRange("field", -7, true, 17, true))
    assertNotEquals(
        LongRange("field", -7, true, 17, false).hashCode(),
        LongRange("field", -7, true, 17, true).hashCode())
    assertNotEquals(
        LongRange("field", -7, true, 17, false), LongRange("field", -7, true, 18, false))
    assertNotEquals(
        LongRange("field", -7, true, 17, false).hashCode(),
        LongRange("field", -7, true, 18, false).hashCode())
    // mutlidim tests
    assertEquals(
        LongRange("field", longArray(-7, -8), longArray(17, 18)),
        LongRange("field", longArray(-7, -8), longArray(17, 18)))
    assertEquals(
        LongRange("field", longArray(-7, -8), longArray(17, 18)).hashCode(),
        LongRange("field", longArray(-7, -8), longArray(17, 18)).hashCode())
    assertNotEquals(
        LongRange("field", longArray(-7, -8, -9), longArray(17, 18, 19)).hashCode(),
        LongRange("field", longArray(-7, -8), longArray(17, 18)).hashCode())
    assertNotEquals(
        LongRange("field", longArray(-7, -9), longArray(17, 18)).hashCode(),
        LongRange("field", longArray(-7, -8), longArray(17, 18)).hashCode())
    assertNotEquals(
        LongRange("field", longArray(-7, -8), longArray(17, 19)).hashCode(),
        LongRange("field", longArray(-7, -8), longArray(17, 18)).hashCode())
  }

  @Test

  fun testDoubleRangeEquals() {
    assertEquals(
        DoubleRange("field", -7.0, true, 17.0, false),
        DoubleRange("field", -7.0, true, 17.0, false))
    assertEquals(
        DoubleRange("field", -7.0, true, 17.0, false).hashCode(),
        DoubleRange("field", -7.0, true, 17.0, false).hashCode())
    assertNotEquals(
        DoubleRange("field", -7.0, true, 17.0, false),
        DoubleRange("field", -7.0, true, 17.0, true))
    assertNotEquals(
        DoubleRange("field", -7.0, true, 17.0, false).hashCode(),
        DoubleRange("field", -7.0, true, 17.0, true).hashCode())
    assertNotEquals(
        DoubleRange("field", -7.0, true, 17.0, false),
        DoubleRange("field", -7.0, true, 18.0, false))
    assertNotEquals(
        DoubleRange("field", -7.0, true, 17.0, false).hashCode(),
        DoubleRange("field", -7.0, true, 18.0, false).hashCode())
    // multidim tests
    assertEquals(
        DoubleRange("field", doubleArray(-7.0, -8.0), doubleArray(17.0, 18.0)),
        DoubleRange("field", doubleArray(-7.0, -8.0), doubleArray(17.0, 18.0)))
    assertEquals(
        DoubleRange("field", doubleArray(-7.0, -8.0), doubleArray(17.0, 18.0)).hashCode(),
        DoubleRange("field", doubleArray(-7.0, -8.0), doubleArray(17.0, 18.0)).hashCode())
    assertNotEquals(
        DoubleRange("field", doubleArray(-7.0, -8.0, -9.0), doubleArray(17.0, 18.0, 19.0)),
        DoubleRange("field", doubleArray(-7.0, -8.0), doubleArray(17.0, 18.0)))
    assertNotEquals(
        DoubleRange("field", doubleArray(-7.0, -9.0), doubleArray(17.0, 18.0)),
        DoubleRange("field", doubleArray(-7.0, -8.0), doubleArray(17.0, 18.0)))
    assertNotEquals(
        DoubleRange("field", doubleArray(-7.0, -8.0), doubleArray(17.0, 19.0)),
        DoubleRange("field", doubleArray(-7.0, -8.0), doubleArray(17.0, 18.0)))
  }

  private fun setLongRangeValue(field: LongRangeDocValuesField, min: Long, max: Long) {
    setLongRangeValue(field, longArray(min), longArray(max))
  }

  private fun setLongRangeValue(field: LongRangeDocValuesField, min: LongArray, max: LongArray) {
    var encodedValue: ByteArray = ByteArray(2 * min.size * Long.SIZE_BYTES)
    org.gnit.lucenekmp.document.LongRange.verifyAndEncode(min, max, encodedValue)
    field.setBytesValue(BytesRef(encodedValue))
  }

  private fun setDoubleRangeValue(field: DoubleRangeDocValuesField, min: Double, max: Double) {
    setDoubleRangeValue(field, doubleArray(min), doubleArray(max))
  }

  private fun setDoubleRangeValue(field: DoubleRangeDocValuesField, min: DoubleArray, max: DoubleArray) {
    var encodedValue: ByteArray = ByteArray(2 * min.size * Double.SIZE_BYTES)
    org.gnit.lucenekmp.document.DoubleRange.verifyAndEncode(min, max, encodedValue)
    field.setBytesValue(BytesRef(encodedValue))
  }

  private fun longArray(vararg longs: Long): LongArray {
    var ret: LongArray = LongArray(longs.size)
    longs.copyInto(ret, 0, 0, longs.size)
    return ret
  }

  private fun doubleArray(vararg doubles: Double): DoubleArray {
    var ret: DoubleArray = DoubleArray(doubles.size)
    doubles.copyInto(ret, 0, 0, doubles.size)
    return ret
  }
}
