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
package org.gnit.lucenekmp.facet.sortedset

import kotlinx.coroutines.runBlocking

import kotlin.test.*

import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.LinkedBlockingQueue
import org.gnit.lucenekmp.jdkport.ThreadPoolExecutor
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.facet.DrillDownQuery
import org.gnit.lucenekmp.facet.FacetResult
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.Facets
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollectorManager
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.LabelAndValue
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NamedThreadFactory

class TestSortedSetDocValuesFacets : FacetTestCase() {

  // NOTE: TestDrillSideways.testRandom also sometimes
  // randomly uses SortedSetDV

  @Test
  fun testBasic() {
    var config = FacetsConfig()
    config.setMultiValued("a", true)
    config.setMultiValued("b", true)
    config.setRequireDimCount("b", true)
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      doc.add(SortedSetDocValuesFacetField("a", "bar"))
      doc.add(SortedSetDocValuesFacetField("a", "zoo"))
      doc.add(SortedSetDocValuesFacetField("b", "baz"))
      doc.add(SortedSetDocValuesFacetField("b", "buzz"))
      writer.addDocument(config.build(doc))
      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      doc.add(SortedSetDocValuesFacetField("b", "buzz"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)

          // value for dim a should be -1 since it's multivalued but doesn't require dim counts:
          assertEquals(
              "dim=a path=[] value=-1 childCount=3\n  foo (2)\n  bar (1)\n  zoo (1)\n",
              facets.getTopChildren(10, "a").toString())
          // value for dim b should be 2 since it's multivalued but _does_ require dim counts:
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  buzz (2)\n  baz (1)\n",
              facets.getTopChildren(10, "b").toString())

          // test getAllChildren
          assertFacetResult(
              facets.getAllChildren("a")!!,
              "a",
              emptyArray<String>(),
              3,
              -1,
              LabelAndValue("bar", 1),
              LabelAndValue("foo", 2),
              LabelAndValue("zoo", 1))

          // test getAllDims
          var results = facets.getAllDims(10)
          assertEquals(2, results.size)
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  buzz (2)\n  baz (1)\n",
              results.get(0).toString())
          assertEquals(
              "dim=a path=[] value=-1 childCount=3\n  foo (2)\n  bar (1)\n  zoo (1)\n",
              results.get(1).toString())

          // test getTopDims(10, 10) and expect same results from getAllDims(10)
          var allDimsResults = facets.getTopDims(10, 10)
          assertEquals(results, allDimsResults)

          // test getTopDims(2, 1)
          var topDimsResults = facets.getTopDims(2, 1)
          assertEquals(2, topDimsResults.size)
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  buzz (2)\n", topDimsResults.get(0).toString())
          assertEquals(
              "dim=a path=[] value=-1 childCount=3\n  foo (2)\n", topDimsResults.get(1).toString())

          // test getAllDims
          var results2 = facets.getAllDims(1)
          assertEquals(2, results2.size)
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  buzz (2)\n", results2.get(0).toString())

          // test getTopDims(1, 1)
          var topDimsResults1 = facets.getTopDims(1, 1)
          assertEquals(1, topDimsResults1.size)
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  buzz (2)\n",
              topDimsResults1.get(0).toString())

          // test getTopDims(0)
          expectThrows(IllegalArgumentException::class) { facets.getAllDims(0) }

          // test getSpecificValue
          assertEquals<Any?>(2, facets.getSpecificValue("a", "foo"))
          expectThrows(IllegalArgumentException::class) { facets.getSpecificValue("a", "foo", "bar") }

          // DrillDown:
          var q = DrillDownQuery(config)
          q.add("a", "foo")
          q.add("b", "baz")
          var hits = searcher.search(q, 1)
          assertEquals(1L, hits.totalHits.value)
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  // test tricky combinations of the three config: MultiValued, Hierarchical, and RequireDimCount of
  // a dim
  @Test
  fun testCombinationsOfConfig() {
    var config = FacetsConfig()

    // case 1: dimension "a" is hierarchical and non-multiValued
    // expect returns counts[pathOrd]
    config.setMultiValued("a", false)
    config.setHierarchical("a", true)

    // case 2:  dimension "b" is hierarchical and multiValued and setRequireDimCount = true
    // expect returns counts[pathOrd]
    config.setMultiValued("b", true)
    config.setHierarchical("b", true)
    config.setRequireDimCount("b", true)

    // case 3: dimension "c" is hierarchical and multiValued and setRequireDimCount != true
    // expect always returns counts[pathOrd] for Hierarchical = true
    config.setMultiValued("c", true)
    config.setHierarchical("c", true)

    // case 4: dimension "d" is non-hierarchical but multiValued and setRequireDimCount = true
    // expect returns counts[pathOrd]
    config.setMultiValued("d", true)
    config.setHierarchical("d", false)
    config.setRequireDimCount("d", true)

    // case 4: dimension "e" that is non-hierarchical and multiValued and setRequireDimCount = false
    // expect returns -1, this is the only case that we reset dimCount to -1
    config.setMultiValued("e", true)
    config.setHierarchical("e", false)
    config.setRequireDimCount("e", false)

    // case 5: dimension "f" that it is non-hierarchical and non-multiValued and expect returns
    // counts[pathOrd]
    config.setMultiValued("f", false)
    config.setHierarchical("f", false)

    // case 6: expect returns counts[pathOrd] for dims with setHierarchical = true
    config.setHierarchical("g", true)

    // case 7: expect returns counts[pathOrd] for dims with setHierarchical = true
    config.setHierarchical("g-2", false)

    // case 8: expect returns counts[pathOrd] for dims with setHierarchical = true
    config.setRequireDimCount("h", true)
    config.setMultiValued("h", true)

    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      doc.add(SortedSetDocValuesFacetField("b", "bar"))
      doc.add(SortedSetDocValuesFacetField("c", "zoo"))
      doc.add(SortedSetDocValuesFacetField("d", "baz"))
      doc.add(SortedSetDocValuesFacetField("e", "buzz"))
      doc.add(SortedSetDocValuesFacetField("f", "buzze"))
      doc.add(SortedSetDocValuesFacetField("g", "buzzel"))
      doc.add(SortedSetDocValuesFacetField("g-2", "buzzell"))
      doc.add(SortedSetDocValuesFacetField("h", "buzzele"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)
          assertEquals(
              "dim=a path=[] value=1 childCount=1\n  foo (1)\n",
              facets.getTopChildren(10, "a").toString())
          // value for dim b should be 1 since it's multivalued but _does_ require dim counts:
          assertEquals(
              "dim=b path=[] value=1 childCount=1\n  bar (1)\n",
              facets.getTopChildren(10, "b").toString())
          assertEquals(
              "dim=c path=[] value=1 childCount=1\n  zoo (1)\n",
              facets.getTopChildren(10, "c").toString())
          assertEquals(
              "dim=d path=[] value=1 childCount=1\n  baz (1)\n",
              facets.getTopChildren(10, "d").toString())
          // value for dim e should be -1 since it's multivalued but doesn't require dim counts:
          assertEquals(
              "dim=e path=[] value=-1 childCount=1\n  buzz (1)\n",
              facets.getTopChildren(10, "e").toString())
          assertEquals(
              "dim=f path=[] value=1 childCount=1\n  buzze (1)\n",
              facets.getTopChildren(10, "f").toString())
          assertEquals(
              "dim=g path=[] value=1 childCount=1\n  buzzel (1)\n",
              facets.getTopChildren(10, "g").toString())
          assertEquals(
              "dim=g-2 path=[] value=1 childCount=1\n  buzzell (1)\n",
              facets.getTopChildren(10, "g-2").toString())
          assertEquals(
              "dim=h path=[] value=1 childCount=1\n  buzzele (1)\n",
              facets.getTopChildren(10, "h").toString())

          // test getAllDims
          var results = facets.getAllDims(10)
          assertEquals(9, results.size)
          assertEquals(
              "dim=a path=[] value=1 childCount=1\n  foo (1)\n", results.get(0).toString())
          assertEquals(
              "dim=b path=[] value=1 childCount=1\n  bar (1)\n", results.get(1).toString())
          assertEquals(
              "dim=c path=[] value=1 childCount=1\n  zoo (1)\n", results.get(2).toString())
          assertEquals(
              "dim=d path=[] value=1 childCount=1\n  baz (1)\n", results.get(3).toString())
          assertEquals(
              "dim=f path=[] value=1 childCount=1\n  buzze (1)\n", results.get(4).toString())
          assertEquals(
              "dim=g path=[] value=1 childCount=1\n  buzzel (1)\n", results.get(5).toString())
          assertEquals(
              "dim=g-2 path=[] value=1 childCount=1\n  buzzell (1)\n", results.get(6).toString())
          assertEquals(
              "dim=h path=[] value=1 childCount=1\n  buzzele (1)\n", results.get(7).toString())
          assertEquals(
              "dim=e path=[] value=-1 childCount=1\n  buzz (1)\n", results.get(8).toString())

          // test getTopDims(10, 10) and expect same results from getAllDims(10)
          var allTopDimsResults = facets.getTopDims(10, 10)
          assertEquals(results, allTopDimsResults)

          // test getTopDims(n, 10)
          if (allTopDimsResults.size > 0) {
            for (i in 1 until results.size) {
              assertEquals(results.subList(0, i), facets.getTopDims(i, 10))
            }
          }

        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testBasicHierarchical() {
    var config = FacetsConfig()
    config.setMultiValued("a", true)
    config.setRequireDimCount("a", true)
    config.setMultiValued("c", true)
    config.setHierarchical("c", true)
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      doc.add(SortedSetDocValuesFacetField("a", "bar"))
      doc.add(SortedSetDocValuesFacetField("a", "zoo"))
      doc.add(SortedSetDocValuesFacetField("b", "baz"))
      doc.add(SortedSetDocValuesFacetField("c", "buzz"))
      doc.add(SortedSetDocValuesFacetField("c", "buzz", "bee"))
      doc.add(SortedSetDocValuesFacetField("c", "buzz", "bif"))
      doc.add(SortedSetDocValuesFacetField("c", "buzz", "bif", "baf"))
      doc.add(SortedSetDocValuesFacetField("c", "buzz", "biz"))
      doc.add(SortedSetDocValuesFacetField("c", "buzz", "biz", "bar"))
      writer.addDocument(config.build(doc))
      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      doc.add(SortedSetDocValuesFacetField("c", "buzz", "bif", "baf"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)

          // since a is not set to be hierarchical but _is_ multivalued, we expect a value of 2
          // (since two unique docs contain at least one value for this dim):
          assertEquals(
              "dim=a path=[] value=2 childCount=3\n  foo (2)\n  bar (1)\n  zoo (1)\n",
              facets.getTopChildren(10, "a").toString())
          assertEquals(
              "dim=b path=[] value=1 childCount=1\n  baz (1)\n",
              facets.getTopChildren(10, "b").toString())
          assertEquals(
              "dim=c path=[buzz] value=2 childCount=3\n  bif (2)\n  bee (1)\n  biz (1)\n",
              facets.getTopChildren(10, "c", "buzz").toString())
          assertEquals(
              "dim=c path=[buzz, bif] value=2 childCount=1\n  baf (2)\n",
              facets.getTopChildren(10, "c", "buzz", "bif").toString())

          assertFacetResult(
              facets.getAllChildren("a")!!,
              "a",
              emptyArray<String>(),
              3,
              2,
              LabelAndValue("bar", 1),
              LabelAndValue("foo", 2),
              LabelAndValue("zoo", 1))

          assertFacetResult(
              facets.getAllChildren("c", "buzz")!!,
              "c",
              arrayOf("buzz"),
              3,
              2,
              LabelAndValue("bee", 1),
              LabelAndValue("bif", 2),
              LabelAndValue("biz", 1))

          assertFacetResult(
              facets.getAllChildren("c", "buzz", "bif")!!,
              "c",
              arrayOf("buzz", "bif"),
              1,
              2,
              LabelAndValue("baf", 2))

          // test getSpecificValue (and make sure hierarchical dims are supported: LUCENE-10584):
          assertEquals<Any?>(2, facets.getSpecificValue("c", "buzz"))
          // should be able to request deeper paths on hierarchical dims:
          assertEquals<Any?>(1, facets.getSpecificValue("c", "buzz", "bee"))
          // ... but not on non-hierarchical dims:
          expectThrows(IllegalArgumentException::class) { facets.getSpecificValue("a", "foo", "bar)") }
          // DrillDown:
          var q = DrillDownQuery(config)
          q.add("a", "foo")
          q.add("b", "baz")
          var hits = searcher.search(q, 1)
          assertEquals(1L, hits.totalHits.value)

          q = DrillDownQuery(config)
          q.add("c", "buzz", "bif")
          hits = searcher.search(q, 2)
          assertEquals(2L, hits.totalHits.value)

          q = DrillDownQuery(config)
          q.add("c", "buzz", "biz", "bar")
          hits = searcher.search(q, 2)
          assertEquals(1L, hits.totalHits.value)
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  // See: LUCENE-10070
  @Test
  fun testCountAll() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var config = FacetsConfig()

      var doc = Document()
      doc.add(StringField("id", "0", Field.Store.NO))
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      writer.addDocument(config.build(doc))

      doc = Document()
      doc.add(StringField("id", "1", Field.Store.NO))
      doc.add(SortedSetDocValuesFacetField("a", "bar"))
      writer.addDocument(config.build(doc))

      writer.deleteDocuments(Term("id", "0"))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var facets: Facets = SortedSetDocValuesFacetCounts(state)

        assertEquals(
            "dim=a path=[] value=1 childCount=1\n  bar (1)\n",
            facets.getTopChildren(10, "a").toString())

        // test getAllChildren
        assertFacetResult(
            facets.getAllChildren("a")!!, "a", emptyArray<String>(), 1, 1, LabelAndValue("bar", 1))

        // test topNChildren = 0
        val finalFacets: Facets = facets
        expectThrows(IllegalArgumentException::class) { finalFacets.getTopChildren(0, "a") }

        var exec =
            ThreadPoolExecutor(
                1,
                TestUtil.nextInt(random(), 2, 6),
                Long.MAX_VALUE,
                TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(),
                NamedThreadFactory("TestIndexSearcher"))
        try {
          facets = ConcurrentSortedSetDocValuesFacetCounts(state, exec)

          assertEquals(
              "dim=a path=[] value=1 childCount=1\n  bar (1)\n",
              facets.getTopChildren(10, "a").toString())

          // test getTopDims in ConcurrentSortedSetDocValuesFacetCounts
          var results = facets.getAllDims(10)
          // test getTopDims(10, 10) and expect same results from getAllDims(10)
          var allTopDimsResults = facets.getTopDims(10, 10)

          // test getTopDims(n, 10)
          if (allTopDimsResults.size > 0) {
            for (i in 1 until results.size) {
              assertEquals(results.subList(0, i), facets.getTopDims(i, 10))
            }
          }

        } finally {
          runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testHierarchicalCountAll() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var config = FacetsConfig()
      config.setHierarchical("b", true)

      var doc = Document()
      doc.add(StringField("id", "0", Field.Store.NO))
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      doc.add(SortedSetDocValuesFacetField("b", "buzz", "bee"))
      writer.addDocument(config.build(doc))

      doc = Document()
      doc.add(StringField("id", "1", Field.Store.NO))
      doc.add(SortedSetDocValuesFacetField("a", "bar"))
      doc.add(SortedSetDocValuesFacetField("b", "buzz", "baz"))
      writer.addDocument(config.build(doc))

      doc = Document()
      doc.add(StringField("id", "2", Field.Store.NO))
      doc.add(SortedSetDocValuesFacetField("a", "buz"))
      doc.add(SortedSetDocValuesFacetField("b", "bar", "foo"))
      writer.addDocument(config.build(doc))

      doc = Document()
      doc.add(StringField("id", "2", Field.Store.NO))
      doc.add(SortedSetDocValuesFacetField("a", "baz"))
      doc.add(SortedSetDocValuesFacetField("b", "bar"))
      writer.addDocument(config.build(doc))

      writer.deleteDocuments(Term("id", "0"))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var facets: Facets = SortedSetDocValuesFacetCounts(state)

        assertEquals(
            "dim=a path=[] value=3 childCount=3\n  bar (1)\n  baz (1)\n  buz (1)\n",
            facets.getTopChildren(10, "a").toString())
        assertEquals(
            "dim=b path=[buzz] value=1 childCount=1\n  baz (1)\n",
            facets.getTopChildren(10, "b", "buzz").toString())

        assertFacetResult(
            facets.getAllChildren("a")!!,
            "a",
            emptyArray<String>(),
            3,
            3,
            LabelAndValue("bar", 1),
            LabelAndValue("baz", 1),
            LabelAndValue("buz", 1))

        assertFacetResult(
            facets.getAllChildren("b")!!,
            "b",
            emptyArray<String>(),
            2,
            3,
            LabelAndValue("bar", 2),
            LabelAndValue("buzz", 1))

        var exec =
            ThreadPoolExecutor(
                1,
                TestUtil.nextInt(random(), 2, 6),
                Long.MAX_VALUE,
                TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(),
                NamedThreadFactory("TestIndexSearcher"))
        try {
          facets = ConcurrentSortedSetDocValuesFacetCounts(state, exec)

          assertEquals(
              "dim=a path=[] value=3 childCount=3\n  bar (1)\n  baz (1)\n  buz (1)\n",
              facets.getTopChildren(10, "a").toString())
          assertEquals(
              "dim=b path=[buzz] value=1 childCount=1\n  baz (1)\n",
              facets.getTopChildren(10, "b", "buzz").toString())

          // test getTopDims in ConcurrentSortedSetDocValuesFacetCounts
          var results = facets.getAllDims(10)
          // test getTopDims(10, 10) and expect same results from getAllDims(10)
          var allTopDimsResults = facets.getTopDims(10, 10)

          // test getTopDims(n, 10)
          if (allTopDimsResults.size > 0) {
            for (i in 1 until results.size) {
              assertEquals(results.subList(0, i), facets.getTopDims(i, 10))
            }
          }
        } finally {
          runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testBasicSingleValued() {
    var config = FacetsConfig()
    config.setMultiValued("a", false)
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      doc.add(SortedSetDocValuesFacetField("b", "bar"))
      writer.addDocument(config.build(doc))
      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      writer.addDocument(config.build(doc))
      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "baz"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)

          assertEquals(
              "dim=a path=[] value=3 childCount=2\n  foo (2)\n  baz (1)\n",
              facets.getTopChildren(10, "a").toString())
          assertEquals(
              "dim=b path=[] value=1 childCount=1\n  bar (1)\n",
              facets.getTopChildren(10, "b").toString())
          assertFacetResult(
              facets.getAllChildren("a")!!,
              "a",
              emptyArray<String>(),
              2,
              3,
              LabelAndValue("baz", 1),
              LabelAndValue("foo", 2))

          // DrillDown:
          var q = DrillDownQuery(config)
          q.add("a", "foo")
          q.add("b", "bar")
          var hits = searcher.search(q, 1)
          assertEquals(1L, hits.totalHits.value)
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testHierarchicalBasicSingleValues() {
    var config = FacetsConfig()
    config.setHierarchical("c", true)
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("c", "buzz", "bar"))
      writer.addDocument(config.build(doc))
      doc = Document()
      doc.add(SortedSetDocValuesFacetField("c", "buzz", "buz"))
      writer.addDocument(config.build(doc))
      doc = Document()
      doc.add(SortedSetDocValuesFacetField("c", "buz", "baz"))
      writer.addDocument(config.build(doc))
      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("c", "baz"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)

          assertEquals(
              "dim=c path=[buzz] value=2 childCount=2\n  bar (1)\n  buz (1)\n",
              facets.getTopChildren(10, "c", "buzz").toString())
          assertEquals(
              "dim=c path=[buzz] value=2 childCount=2\n  bar (1)\n  buz (1)\n",
              facets.getTopChildren(10, "c", "buzz").toString())

          var q = DrillDownQuery(config)
          q.add("c", "buzz")
          var hits = searcher.search(q, 1)
          assertEquals(2L, hits.totalHits.value)

          q = DrillDownQuery(config)
          q.add("c", "buzz", "bar")
          hits = searcher.search(q, 1)
          assertEquals(1L, hits.totalHits.value)
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testDrillDownOptions() {
    var config = FacetsConfig()
    config.setDrillDownTermsIndexing("c", FacetsConfig.DrillDownTermsIndexing.NONE)
    config.setDrillDownTermsIndexing(
        "d", FacetsConfig.DrillDownTermsIndexing.DIMENSION_AND_FULL_PATH)
    config.setDrillDownTermsIndexing("e", FacetsConfig.DrillDownTermsIndexing.ALL_PATHS_NO_DIM)
    config.setDrillDownTermsIndexing("f", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY)
    config.setDrillDownTermsIndexing("g", FacetsConfig.DrillDownTermsIndexing.ALL)
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("c", "foo"))
      doc.add(SortedSetDocValuesFacetField("d", "foo"))
      doc.add(SortedSetDocValuesFacetField("e", "foo"))
      doc.add(SortedSetDocValuesFacetField("f", "foo"))
      doc.add(SortedSetDocValuesFacetField("g", "foo"))
      writer.addDocument(config.build(doc))
      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)
        // Drill down with different indexing configuration options
        var q = DrillDownQuery(config)
        q.add("c")
        var hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("c", "foo")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("d")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("d", "foo")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("e")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("e", "foo")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("f")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("f", "foo")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("g")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("g", "foo")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)
      }
    }}
  }

  @Test
  fun testHierarchicalDrillDownOptions() {
    var config = FacetsConfig()
    config.setDrillDownTermsIndexing("c", FacetsConfig.DrillDownTermsIndexing.NONE)
    config.setDrillDownTermsIndexing(
        "d", FacetsConfig.DrillDownTermsIndexing.DIMENSION_AND_FULL_PATH)
    config.setDrillDownTermsIndexing("e", FacetsConfig.DrillDownTermsIndexing.ALL_PATHS_NO_DIM)
    config.setDrillDownTermsIndexing("f", FacetsConfig.DrillDownTermsIndexing.FULL_PATH_ONLY)
    config.setDrillDownTermsIndexing("g", FacetsConfig.DrillDownTermsIndexing.ALL)
    config.setHierarchical("c", true)
    config.setHierarchical("d", true)
    config.setHierarchical("e", true)
    config.setHierarchical("f", true)
    config.setHierarchical("g", true)
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("c", "biz", "baz"))
      doc.add(SortedSetDocValuesFacetField("d", "biz", "baz"))
      doc.add(SortedSetDocValuesFacetField("e", "biz", "baz"))
      doc.add(SortedSetDocValuesFacetField("f", "biz", "baz"))
      doc.add(SortedSetDocValuesFacetField("g", "biz", "baz"))
      writer.addDocument(config.build(doc))
      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)
        // Drill down with different indexing configuration options
        var q = DrillDownQuery(config)
        q.add("c")
        var hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("c", "biz")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("c", "biz", "baz")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("c", "foo")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("d")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("d", "foo")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("d", "biz")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("d", "biz", "baz")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("e")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("e", "foo")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("e", "biz")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("e", "biz", "baz")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("f")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("f", "foo")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("f", "biz")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("f", "biz", "baz")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("g")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("g", "foo")
        hits = searcher.search(q, 1)
        assertEquals(0L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("g", "biz")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)

        q = DrillDownQuery(config)
        q.add("g", "biz", "baz")
        hits = searcher.search(q, 1)
        assertEquals(1L, hits.totalHits.value)
      }
    }}
  }

  // LUCENE-5090
  
  @Test
  fun testStaleState() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->

      var config = FacetsConfig()

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo"))
      writer.addDocument(config.build(doc))

      writer.reader.use { r ->
        var state = DefaultSortedSetDocValuesReaderState(r, config)

        doc = Document()
        doc.add(SortedSetDocValuesFacetField("a", "bar"))
        writer.addDocument(config.build(doc))

        doc = Document()
        doc.add(SortedSetDocValuesFacetField("a", "baz"))
        writer.addDocument(config.build(doc))

        writer.reader.use { r2 ->
          var searcher = newSearcher(r2)

          var c =
              searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())

          expectThrows(IllegalStateException::class) { SortedSetDocValuesFacetCounts(state, c!!) }
        }
      }
    }}
  }

  // LUCENE-5333
  @Test
  fun testSparseFacets() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->

      var config = FacetsConfig()

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo1"))
      writer.addDocument(config.build(doc))

      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo2"))
      doc.add(SortedSetDocValuesFacetField("b", "bar1"))
      writer.addDocument(config.build(doc))

      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo3"))
      doc.add(SortedSetDocValuesFacetField("b", "bar2"))
      doc.add(SortedSetDocValuesFacetField("c", "baz1"))
      doc.add(SortedSetDocValuesFacetField("d", "biz1"))
      writer.addDocument(config.build(doc))

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("d", "biz2"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)

          // Ask for top 10 labels for any dims that have counts:
          var results = facets.getAllDims(10)

          assertEquals(4, results.size)
          assertEquals(
              "dim=a path=[] value=3 childCount=3\n  foo1 (1)\n  foo2 (1)\n  foo3 (1)\n",
              results.get(0).toString())
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  bar1 (1)\n  bar2 (1)\n",
              results.get(1).toString())
          assertEquals(
              "dim=d path=[] value=2 childCount=2\n  biz1 (1)\n  biz2 (1)\n",
              results.get(2).toString())
          assertEquals(
              "dim=c path=[] value=1 childCount=1\n  baz1 (1)\n", results.get(3).toString())

          // test getAllDims with topN = 1, sort by dim names when values are equal
          var top1results = facets.getAllDims(1)
          assertEquals(4, results.size)
          assertEquals(
              "dim=a path=[] value=3 childCount=3\n  foo1 (1)\n", top1results.get(0).toString())
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  bar1 (1)\n", top1results.get(1).toString())
          assertEquals(
              "dim=d path=[] value=2 childCount=2\n  biz1 (1)\n", top1results.get(2).toString())
          assertEquals(
              "dim=c path=[] value=1 childCount=1\n  baz1 (1)\n", top1results.get(3).toString())

          // test getTopDims(1, 1)
          var topDimsResults1 = facets.getTopDims(1, 1)
          assertEquals(1, topDimsResults1.size)
          assertEquals(
              "dim=a path=[] value=3 childCount=3\n  foo1 (1)\n",
              topDimsResults1.get(0).toString())

          // test top 2 dims that have the same counts, expect to sort by dim names
          var topDimsResults2 = facets.getTopDims(3, 2)
          assertEquals(3, topDimsResults2.size)
          assertEquals(
              "dim=a path=[] value=3 childCount=3\n  foo1 (1)\n  foo2 (1)\n",
              topDimsResults2.get(0).toString())
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  bar1 (1)\n  bar2 (1)\n",
              topDimsResults2.get(1).toString())
          assertEquals(
              "dim=d path=[] value=2 childCount=2\n  biz1 (1)\n  biz2 (1)\n",
              topDimsResults2.get(2).toString())

          assertFacetResult(
              facets.getAllChildren("a")!!,
              "a",
              emptyArray<String>(),
              3,
              3,
              LabelAndValue("foo1", 1),
              LabelAndValue("foo2", 1),
              LabelAndValue("foo3", 1))

          assertFacetResult(
              facets.getAllChildren("b")!!,
              "b",
              emptyArray<String>(),
              2,
              2,
              LabelAndValue("bar1", 1),
              LabelAndValue("bar2", 1))

          assertFacetResult(
              facets.getAllChildren("c")!!, "c", emptyArray<String>(), 1, 1, LabelAndValue("baz1", 1))

          assertFacetResult(
              facets.getAllChildren("d")!!,
              "d",
              emptyArray<String>(),
              2,
              2,
              LabelAndValue("biz1", 1),
              LabelAndValue("biz2", 1))

          var resources = state.childResources
          assertTrue(state.toString().contains(FacetsConfig.DEFAULT_INDEX_FIELD_NAME))
          if (searcher.indexReader.leaves().size > 1) {
            assertTrue(state.ramBytesUsed() > 0)
            assertFalse(resources.isEmpty())
            assertTrue(resources.toString().contains(FacetsConfig.DEFAULT_INDEX_FIELD_NAME))
          } else {
            assertEquals(0L, state.ramBytesUsed())
            assertTrue(resources.isEmpty())
          }
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testHierarchicalSparseFacets() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->

      var config = FacetsConfig()
      config.setHierarchical("d", true)
      config.setHierarchical("e", true)

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("d", "foo", "bar"))
      writer.addDocument(config.build(doc))

      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("d", "foo", "baz"))
      writer.addDocument(config.build(doc))

      if (random().nextBoolean()) {
        writer.commit()
      }

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("e", "biz", "baz"))
      writer.addDocument(config.build(doc))

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)

          // Ask for top 10 labels for any dims that have counts:
          var results = facets.getAllDims(10)

          assertEquals(2, results.size)
          assertEquals(
              "dim=d path=[] value=2 childCount=1\n  foo (2)\n", results.get(0).toString())
          assertEquals(
              "dim=e path=[] value=1 childCount=1\n  biz (1)\n", results.get(1).toString())

          // test getTopDims(1, 1)
          var topDimsResults1 = facets.getTopDims(1, 1)
          assertEquals(1, topDimsResults1.size)
          assertEquals(
              "dim=d path=[] value=2 childCount=1\n  foo (2)\n", results.get(0).toString())
          assertFacetResult(
              facets.getAllChildren("d", "foo")!!,
              "d",
              arrayOf("foo"),
              2,
              2,
              LabelAndValue("bar", 1),
              LabelAndValue("baz", 1))
          assertFacetResult(
              facets.getAllChildren("d")!!, "d", emptyArray<String>(), 1, 2, LabelAndValue("foo", 2))

          var resources = state.childResources
          assertTrue(state.toString().contains(FacetsConfig.DEFAULT_INDEX_FIELD_NAME))
          if (searcher.indexReader.leaves().size > 1) {
            assertTrue(state.ramBytesUsed() > 0)
            assertFalse(resources.isEmpty())
            assertTrue(resources.toString().contains(FacetsConfig.DEFAULT_INDEX_FIELD_NAME))
          } else {
            assertEquals(0L, state.ramBytesUsed())
            assertTrue(resources.isEmpty())
          }
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testSomeSegmentsMissing() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->

      var config = FacetsConfig()

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo1"))
      writer.addDocument(config.build(doc))
      writer.commit()

      doc = Document()
      writer.addDocument(config.build(doc))
      writer.commit()

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo2"))
      writer.addDocument(config.build(doc))
      writer.commit()

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)

          // Ask for top 10 labels for any dims that have counts:
          assertEquals(
              "dim=a path=[] value=2 childCount=2\n  foo1 (1)\n  foo2 (1)\n",
              facets.getTopChildren(10, "a").toString())
          assertFacetResult(
              facets.getAllChildren("a")!!,
              "a",
              emptyArray<String>(),
              2,
              2,
              LabelAndValue("foo1", 1),
              LabelAndValue("foo2", 1))
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testHierarchicalSomeSegmentsMissing() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->

      var config = FacetsConfig()
      config.setHierarchical("b", true)
      config.setMultiValued("b", true)

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo1"))
      doc.add(SortedSetDocValuesFacetField("b", "foo", "bar"))
      doc.add(SortedSetDocValuesFacetField("b", "boo", "buzzz"))
      writer.addDocument(config.build(doc))
      writer.commit()

      doc = Document()
      writer.addDocument(config.build(doc))
      writer.commit()

      doc = Document()
      doc.add(SortedSetDocValuesFacetField("a", "foo2"))
      doc.add(SortedSetDocValuesFacetField("b", "foo", "buzz"))
      writer.addDocument(config.build(doc))
      writer.commit()

      // NRT open
      writer.reader.use { r ->
        var searcher = newSearcher(r)

        // Per-top-reader state:
        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)

          // Ask for top 10 labels for any dims that have counts:
          assertEquals(
              "dim=a path=[] value=2 childCount=2\n  foo1 (1)\n  foo2 (1)\n",
              facets.getTopChildren(10, "a").toString())
          assertEquals(
              "dim=b path=[] value=2 childCount=2\n  foo (2)\n  boo (1)\n",
              facets.getTopChildren(10, "b").toString())
          assertEquals(
              "dim=b path=[foo] value=2 childCount=2\n  bar (1)\n  buzz (1)\n",
              facets.getTopChildren(10, "b", "foo").toString())
          assertFacetResult(
              facets.getAllChildren("b")!!,
              "b",
              emptyArray<String>(),
              2,
              2,
              LabelAndValue("boo", 1),
              LabelAndValue("foo", 2))
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testRandom() {
    var fullIterations = if (LuceneTestCase.TEST_NIGHTLY) 20 else 3
    for (fullIter in 0 until fullIterations) {
      var tokens = getRandomTokens(10)

      newDirectory().use { indexDir -> RandomIndexWriter(random(), indexDir).use { w ->
        var config = FacetsConfig()
        var numDocs = atLeast(1000)
        // Most of the time allow up to 7 dims per doc, but occasionally limit all docs to a single
        // dim:
        val numDims: Int
        if (random().nextInt(10) < 8) {
          numDims = TestUtil.nextInt(random(), 1, 7)
        } else {
          numDims = 1
        }
        var testDocs = getRandomDocs(tokens, numDocs, numDims)
        for (testDoc in testDocs) {
          var doc = Document()
          doc.add(newStringField("content", testDoc.content, Field.Store.NO))
          for (j in 0 until numDims) {
            if (testDoc.dims[j] != null) {
              doc.add(SortedSetDocValuesFacetField("dim" + j, testDoc.dims[j]!!))
            }
          }
          w.addDocument(config.build(doc))
        }

        // NRT open
        w.reader.use { r ->
          var searcher = newSearcher(r)

          // Per-top-reader state:
          var state =
              DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)
          var exec = randomExecutorServiceOrNull()
          try {
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
              var facets: Facets
              if (exec != null) {
                facets = ConcurrentSortedSetDocValuesFacetCounts(state, fc, exec)
              } else {
                facets = SortedSetDocValuesFacetCounts(state, fc)
              }

              // Slow, yet hopefully bug-free, faceting:
              val expectedCounts = Array(numDims) { HashMap<String, Int>() }

              for (doc in testDocs) {
                if (doc.content.equals(searchToken)) {
                  for (j in 0 until numDims) {
                    if (doc.dims[j] != null) {
                      expectedCounts[j][doc.dims[j]!!] = (expectedCounts[j][doc.dims[j]] ?: 0) + 1
                    }
                  }
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
              // sortTies(actual);

              assertEquals(expected, actual)

              // test getTopDims(1, 10)
              if (actual.size > 0) {
                var topDimsResults1 = facets.getTopDims(1, 10)
                assertEquals(actual.get(0), topDimsResults1.get(0))
              }
            }
          } finally {
            if (exec != null) runBlocking { exec.shutdownNow() }
          }
        }
      }}
    }
  }

  @Test
  fun testRandomHierarchicalFlatMix() {
    var fullIterations = if (LuceneTestCase.TEST_NIGHTLY) 20 else 3
    for (fullIter in 0 until fullIterations) {
      var tokens = getRandomTokens(10)

      newDirectory().use { indexDir -> RandomIndexWriter(random(), indexDir).use { w ->
        var config = FacetsConfig()
        var numDocs = atLeast(1000)
        // Most of the time allow up to 7 dims per doc, but occasionally limit all docs to a single
        // dim:
        val numDims: Int
        if (random().nextInt(10) < 8) {
          numDims = TestUtil.nextInt(random(), 1, 7)
        } else {
          numDims = 1
        }
        val hierarchicalDims = BooleanArray(numDims)
        for (i in 0 until numDims) {
          var isHierarchicalDim = random().nextBoolean()
          config.setHierarchical("dim" + i, isHierarchicalDim)
          hierarchicalDims[i] = isHierarchicalDim
        }
        var testDocs = getRandomDocs(tokens, numDocs, numDims)
        val testDocFacets = ArrayList<Set<SortedSetDocValuesFacetField>>()
        for (testDoc in testDocs) {
          var doc = Document()
          val docFacets = HashSet<SortedSetDocValuesFacetField>()
          doc.add(newStringField("content", testDoc.content, Field.Store.NO))
          for (i in 0 until numDims) {
            if (hierarchicalDims[i]) {
              val pathLength: Int
              if (numDims == 1) {
                pathLength = 1
              } else {
                pathLength = random().nextInt(numDims - 1) + 1
              }
              val path = ArrayList<String>()
              for (j in 0 until pathLength) {
                if (testDoc.dims[j] != null) {
                  path.add(testDoc.dims[j]!!)
                }
              }
              doc.add(SortedSetDocValuesFacetField("dim" + i, *path.toTypedArray()))
              for (j in 0 until path.size) {
                docFacets.add(
                    SortedSetDocValuesFacetField(
                        "dim" + i, *path.subList(0, j + 1).toTypedArray()))
              }
            } else if (testDoc.dims[i] != null) {
              doc.add(SortedSetDocValuesFacetField("dim" + i, testDoc.dims[i]!!))
              docFacets.add(SortedSetDocValuesFacetField("dim" + i, testDoc.dims[i]!!))
            }
          }
          testDocFacets.add(docFacets)
          w.addDocument(config.build(doc))
        }

        // NRT open
        w.reader.use { r ->
          var searcher = newSearcher(r)

          // Per-top-reader state:
          var state =
              DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)
          var exec = randomExecutorServiceOrNull()
          try {
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
              var facets: Facets
              if (exec != null) {
                facets = ConcurrentSortedSetDocValuesFacetCounts(state, fc, exec)
              } else {
                facets = SortedSetDocValuesFacetCounts(state, fc)
              }
              // Slow, yet hopefully bug-free, faceting:
              val expectedResults = HashMap<String, FacetResult>()

              for (i in 0 until testDocs.size) {
                var doc = testDocs.get(i)
                if (doc.content.equals(searchToken)) {
                  // goes through all facets paths in the doc
                  for (facetField in testDocFacets.get(i)) {
                    var path = facetField.path
                    val parentDimPathString: String
                    if (path.size == 1) {
                      parentDimPathString = facetField.dim
                    } else {
                      parentDimPathString =
                          (facetField.dim
                              + FacetsConfig.DELIM_CHAR
                              + FacetsConfig.pathToString(path, path.size - 1))
                    }
                    var result = expectedResults.get(parentDimPathString)
                    if (result == null) {
                      val resultPath = Array(path.size - 1) { "" }
                      path.copyInto(resultPath, 0, 0, resultPath.size)
                      result =
                          FacetResult(facetField.dim, resultPath, 0, emptyArray<LabelAndValue>(), 0)
                    }
                    var child = path[path.size - 1]
                    var labelAndValues = result.labelValues
                    var containsChild = false
                    for (k in 0 until labelAndValues.size) {
                      if (labelAndValues[k].label.equals(child)) {
                        containsChild = true
                        labelAndValues[k] =
                            LabelAndValue(
                                labelAndValues[k].label, labelAndValues[k].value.toInt() + 1)
                        break
                      }
                    }
                    val newLabelAndValues: Array<LabelAndValue>
                    var childCount = result.childCount
                    if (containsChild == false) {
                      newLabelAndValues = Array(labelAndValues.size + 1) { k -> if (k < labelAndValues.size) labelAndValues[k] else LabelAndValue(child, 1) }
                      newLabelAndValues[newLabelAndValues.size - 1] = LabelAndValue(child, 1)
                      childCount++
                    } else {
                      newLabelAndValues = labelAndValues
                    }
                    newLabelAndValues.sortWith(Comparator { o1, o2 ->
                          if (o1.value.equals(o2.value)) {
                            BytesRef(o1.label).compareTo(BytesRef(o2.label))
                          } else {
                            o2.value.toInt() - o1.value.toInt()
                          }
                        })
                    var newResult =
                        FacetResult(result.dim, result.path, 0, newLabelAndValues, childCount)
                    expectedResults.put(parentDimPathString, newResult)
                  }
                }
              }

              // second pass to update values
              for (i in 0 until testDocs.size) {
                var doc = testDocs.get(i)
                if (doc.content.equals(searchToken)) {
                  val dimsCounted = HashSet<String>()
                  for (facetField in testDocFacets.get(i)) {
                    var dimPathString =
                        FacetsConfig.pathToString(facetField.dim, *facetField.path)
                    var result = expectedResults.get(dimPathString)
                    var dimResult = expectedResults.get(facetField.dim)
                    if (result != null) {
                      expectedResults.put(
                          dimPathString,
                          FacetResult(
                              result.dim,
                              result.path,
                              result.value.toInt() + 1,
                              result.labelValues,
                              result.childCount))
                    }
                    if (dimResult != null && dimsCounted.add(facetField.dim)) {
                      expectedResults.put(
                          facetField.dim,
                          FacetResult(
                              dimResult.dim,
                              dimResult.path,
                              dimResult.value.toInt() + 1,
                              dimResult.labelValues,
                              dimResult.childCount))
                    }
                  }
                }
              }

              var expected = ArrayList(expectedResults.values)

              val expectedAllDims = ArrayList<FacetResult>()
              for (result in expected) {
                if (result.path.size == 0) {
                  expectedAllDims.add(result)
                  if (expectedAllDims.size >= 10) {
                    break
                  }
                }
              }
              sortFacetResults(expectedAllDims)

              var actualAllDims = facets.getAllDims(10)

              assertEquals(expectedAllDims, actualAllDims)

              // test getTopDims(n, 10)
              if (actualAllDims.size > 0) {
                for (i in 1 until actualAllDims.size) {
                  assertEquals(actualAllDims.subList(0, i), facets.getTopDims(i, 10))
                }
              }

              // Dfs through top children
              for (dimResult in actualAllDims) {
                if (config.getDimConfig(dimResult.dim).hierarchical) {
                  val stack = ArrayDeque<Array<String>>()
                  for (labelAndValue in dimResult.labelValues) {
                    val path = Array(1) { "" }
                    path[0] = labelAndValue.label
                    stack.add(path)
                  }
                  while (stack.isEmpty() == false) {
                    var currPath = stack.removeFirst()
                    var expectedResult =
                        getFacetResultForPath(expected, dimResult.dim, currPath)
                    var actualResult = facets.getTopChildren(10, dimResult.dim, *currPath)
                    try {
                      assertEquals(expectedResult, actualResult)
                      // test getAllChildren
                      var allChildrenResult =
                          facets.getAllChildren(dimResult.dim, *currPath)
                      if (expectedResult != null && allChildrenResult != null) {
                        // sort actual allChildrenResult labels by value, count (since
                        // we have no insight into the ordinals assigned to the values, we resort)
                        allChildrenResult.labelValues.sortWith(Comparator { a, b ->
                              var cmp = a.label.compareTo(b.label) // low-to-high
                              if (cmp == 0) {
                                cmp =
                                    b.value.toLong().compareTo(a.value.toLong()) // low-to-high
                              }
                              cmp
                            })
                        // also sort expected labels by value to match the sorting behavior of
                        // getAllChildren
                        expectedResult.labelValues.sortWith(Comparator { a, b ->
                              var cmp = a.label.compareTo(b.label) // low-to-high
                              if (cmp == 0) {
                                cmp =
                                    b.value.toLong().compareTo(a.value.toLong()) // low-to-high
                              }
                              cmp
                            })
                        assertEquals(expectedResult, allChildrenResult)
                      }
                    } catch (e: AssertionError) {
                      println(iter)
                      println(config.getDimConfig(dimResult.dim).hierarchical)
                      throw e
                    }
                    if (actualResult != null) {
                      for (labelAndValue in actualResult.labelValues) {
                        val path = Array(currPath.size + 1) { "" }
                        currPath.copyInto(path, 0, 0, currPath.size)
                        path[path.size - 1] = labelAndValue.label
                        stack.add(path)
                      }
                    }
                  }
                }
              }
            }
          } finally {
            if (exec != null) runBlocking { exec.shutdownNow() }
          }
        }
      }}
    }
  }

  private fun getFacetResultForPath(allPaths: List<FacetResult>, dim: String, path: Array<String>): FacetResult? {
    for (result in allPaths) {
      if (path.size == 0) {
        if (result.path.size == 0 && result.dim.equals(dim)) {
          return result
        }
      } else {
        var isEqualPath = true
        if (path.size != result.path.size) {
          isEqualPath = false
        } else {
          for (i in 0 until path.size) {
            if (path[i].equals(result.path[i]) == false) {
              isEqualPath = false
              break
            }
          }
        }
        if (isEqualPath && result.dim.equals(dim)) {
          return result
        }
      }
    }
    return null
  }

  @Test
  fun testNonExistentDimension() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var config = FacetsConfig()

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("foo", "bar"))
      writer.addDocument(config.build(doc))
      writer.commit()

      writer.reader.use { r ->
        var searcher = newSearcher(r)

        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)
          var result = facets.getTopChildren(5, "non-existent dimension")

          // make sure the result is null (and no exception was thrown)
          assertNull(result)
          result = facets.getAllChildren("non-existent dimension")
          assertNull(result)
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testHierarchicalNonExistentDimension() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var config = FacetsConfig()
      config.setHierarchical("fizz", true)

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("foo", "bar"))
      doc.add(SortedSetDocValuesFacetField("fizz", "buzz", "baz"))
      writer.addDocument(config.build(doc))
      writer.commit()

      writer.reader.use { r ->
        var searcher = newSearcher(r)

        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)
          var result = facets.getTopChildren(5, "non-existent dimension")

          // make sure the result is null (and no exception was thrown)
          assertNull(result)
          result = facets.getAllChildren("non-existent dimension")
          assertNull(result)

          expectThrows(IllegalArgumentException::class) { facets.getTopChildren(5, "non-existent dimension", "with a path") }
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  @Test
  fun testHierarchicalNonExistentPath() {
    newDirectory().use { dir -> RandomIndexWriter(random(), dir).use { writer ->
      var config = FacetsConfig()
      config.setHierarchical("fizz", true)

      var doc = Document()
      doc.add(SortedSetDocValuesFacetField("fizz", "buzz", "baz"))
      writer.addDocument(config.build(doc))
      writer.commit()

      writer.reader.use { r ->
        var searcher = newSearcher(r)

        var state =
            DefaultSortedSetDocValuesReaderState(searcher.indexReader, config)

        var exec = randomExecutorServiceOrNull()
        try {
          var facets: Facets = getAllFacets(searcher, state, exec)
          var result = facets.getTopChildren(5, "fizz", "fake", "path")

          // make sure the result is null (and no exception was thrown)
          assertNull(result)
          result = facets.getAllChildren("fizz", "fake", "path")
          assertNull(result)
        } finally {
          if (exec != null) runBlocking { exec.shutdownNow() }
        }
      }
    }}
  }

  private fun getAllFacets(searcher: IndexSearcher, state: SortedSetDocValuesReaderState, exec: ExecutorService?): Facets {
    if (random().nextBoolean()) {
      var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())
      if (exec != null) {
        return ConcurrentSortedSetDocValuesFacetCounts(state, c!!, exec)
      } else {
        return SortedSetDocValuesFacetCounts(state, c!!)
      }
    } else if (exec != null) {
      return ConcurrentSortedSetDocValuesFacetCounts(state, exec)
    } else {
      return SortedSetDocValuesFacetCounts(state)
    }
  }

  private fun randomExecutorServiceOrNull(): ExecutorService? {
    if (random().nextBoolean()) {
      return null
    } else {
      return ThreadPoolExecutor(
          1,
          TestUtil.nextInt(random(), 2, 6),
          Long.MAX_VALUE,
          TimeUnit.MILLISECONDS,
          LinkedBlockingQueue(),
          NamedThreadFactory("TestIndexSearcher"))
    }
  }
}
