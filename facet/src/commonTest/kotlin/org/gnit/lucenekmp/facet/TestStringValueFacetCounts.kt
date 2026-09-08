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

import org.gnit.lucenekmp.document.*
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.*

class TestStringValueFacetCounts : FacetTestCase() {

  @Test
  fun testBasicSingleValued() {

    var dir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("bar")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("bar")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("baz")))
    writer.addDocument(doc)

    val expectedCounts = mapOf("foo" to 2, "bar" to 2, "baz" to 1)
    var expectedTotalDocCount = 5

    var searcher = newSearcher(writer.reader)
    writer.close()

    var state =
        StringDocValuesReaderState(searcher.indexReader, "field")
    checkTopNFacetResult(expectedCounts, expectedTotalDocCount, searcher, state, 10, 2, 1, 0)

    IOUtils.close(searcher.indexReader, dir)
  }

  private fun assertEmptyFacetResult(result: FacetResult) {
    assertEquals(0, result.path.size)
    assertEquals(0, result.value)
    assertEquals(0, result.childCount)
    assertEquals(0, result.labelValues.size)
  }

  @Test
  fun testEmptyMatchset() {
    var dir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    writer.addDocument(doc)

    var searcher = newSearcher(writer.reader)
    writer.close()

    var facetsCollector =
        searcher.search(MatchNoDocsQuery(), FacetsCollectorManager())!!
    var state =
        StringDocValuesReaderState(searcher.indexReader, "field")

    var counts = StringValueFacetCounts(state, facetsCollector)

    var top = counts.getTopChildren(10, "field")
    assertEmptyFacetResult(top)

    var all = counts.getAllChildren("field")
    assertEmptyFacetResult(all)

    assertEquals(0, counts.getSpecificValue("field", "foo"))

    IOUtils.close(searcher.indexReader, dir)
  }

  // See: LUCENE-10070
  @Test
  fun testCountAll() {

    var dir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(StringField("id", "0", Field.Store.NO))
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(StringField("id", "1", Field.Store.NO))
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(StringField("id", "2", Field.Store.NO))
    doc.add(SortedSetDocValuesField("field", BytesRef("bar")))
    writer.addDocument(doc)

    writer.deleteDocuments(Term("id", "0"))

    var searcher = newSearcher(writer.reader)
    writer.close()

    var state =
        StringDocValuesReaderState(searcher.indexReader, "field")

    var facets = StringValueFacetCounts(state)
    assertEquals(
        "dim=field path=[] value=2 childCount=2\n  bar (1)",
        facets.getTopChildren(1, "field").toString().trim())

    assertEquals(
        "dim=field path=[] value=2 childCount=2\n  bar (1)\n  foo (1)",
        facets.getTopChildren(10, "field").toString().trim())

    assertFacetResult(
        facets.getAllChildren("field"),
        "field",
        emptyArray<String>(),
        2,
        2,
        *arrayOf(LabelAndValue("bar", 1), LabelAndValue("foo", 1)))

    IOUtils.close(searcher.indexReader, dir)
  }

  @Test
  fun testBasicSingleValuedUsingSortedDoc() {

    var dir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(SortedDocValuesField("field", BytesRef("foo")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedDocValuesField("field", BytesRef("foo")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedDocValuesField("field", BytesRef("bar")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedDocValuesField("field", BytesRef("bar")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedDocValuesField("field", BytesRef("baz")))
    writer.addDocument(doc)

    val expectedCounts = mapOf("foo" to 2, "bar" to 2, "baz" to 1)
    var expectedTotalDocCount = 5

    var searcher = newSearcher(writer.reader)
    writer.close()

    var state =
        StringDocValuesReaderState(searcher.indexReader, "field")

    checkTopNFacetResult(expectedCounts, expectedTotalDocCount, searcher, state, 10, 2, 1, 0)
    checkAllChildrenFacetResult(expectedCounts, expectedTotalDocCount, searcher, state)

    IOUtils.close(searcher.indexReader, dir)
  }

  @Test
  fun testBasicMultiValued() {

    var dir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    doc.add(SortedSetDocValuesField("field", BytesRef("bar")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    doc.add(SortedSetDocValuesField("field", BytesRef("baz")))
    writer.addDocument(doc)

    doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("baz")))
    writer.addDocument(doc)

    val expectedCounts = mapOf("foo" to 2, "bar" to 1, "baz" to 2)
    var expectedTotalDocCount = 3

    var searcher = newSearcher(writer.reader)
    writer.close()

    var state =
        StringDocValuesReaderState(searcher.indexReader, "field")

    checkTopNFacetResult(expectedCounts, expectedTotalDocCount, searcher, state, 10, 2, 1, 0)
    checkAllChildrenFacetResult(expectedCounts, expectedTotalDocCount, searcher, state)

    IOUtils.close(searcher.indexReader, dir)
  }

  @Test
  fun testSparseMultiSegmentCase() {
    var dir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)

    val expectedValueCounts = HashMap<String, Int>()

    // Create two segments, each with only one doc that has a large number of SSDV field values.
    // This ensures "sparse" counting will occur in StringValueFacetCounts (i.e., small number
    // of hits relative to the field cardinality):
    var doc = Document()
    for (i in 0 until 100) {
      doc.add(SortedSetDocValuesField("field", BytesRef("foo_" + i)))
      expectedValueCounts.put("foo_" + i, 1)
    }
    writer.addDocument(doc)
    writer.commit()

    doc = Document()
    for (i in 0 until 100) {
      doc.add(SortedSetDocValuesField("field", BytesRef("bar_" + i)))
      expectedValueCounts.put("bar_" + i, 1)
    }
    writer.addDocument(doc)

    var expectedTotalDocCount = 2

    var searcher = newSearcher(writer.reader)
    writer.close()

    var state =
        StringDocValuesReaderState(searcher.indexReader, "field")

    checkTopNFacetResult(expectedValueCounts, expectedTotalDocCount, searcher, state, 10, 2, 1, 0)
    checkAllChildrenFacetResult(expectedValueCounts, expectedTotalDocCount, searcher, state)

    IOUtils.close(searcher.indexReader, dir)
  }

  @Test
  fun testMissingSegment() {

    var dir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    doc.add(SortedSetDocValuesField("field", BytesRef("bar")))
    writer.addDocument(doc)
    writer.commit()

    // segment with no values
    doc = Document()
    writer.addDocument(doc)
    writer.commit()

    doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("baz")))
    writer.addDocument(doc)
    writer.commit()

    val expectedCounts = mapOf("foo" to 1, "bar" to 1, "baz" to 1)
    var expectedTotalDocCount = 2

    var searcher = newSearcher(writer.reader)
    writer.close()

    var state =
        StringDocValuesReaderState(searcher.indexReader, "field")

    checkTopNFacetResult(expectedCounts, expectedTotalDocCount, searcher, state, 10, 2, 1, 0)
    checkAllChildrenFacetResult(expectedCounts, expectedTotalDocCount, searcher, state)

    IOUtils.close(searcher.indexReader, dir)
  }

  @Test
  fun testStaleState() {

    var dir = newDirectory()
    var writer = RandomIndexWriter(random(), dir)

    var doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("foo")))
    writer.addDocument(doc)

    var reader = writer.reader
    var state = StringDocValuesReaderState(reader, "field")

    doc = Document()
    doc.add(SortedSetDocValuesField("field", BytesRef("bar")))
    writer.addDocument(doc)

    var searcher = newSearcher(writer.reader)
    writer.close()

    var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    // using a stale state
    expectThrows(IllegalStateException::class) { StringValueFacetCounts(state, c) }

    IOUtils.close(reader, searcher.indexReader, dir)
  }

  @Test
  fun testRandom() {

    val fullIterations = if (TEST_NIGHTLY) 20 else 3
    for (iter in 0 until fullIterations) {
      var dir = newDirectory()
      var writer = RandomIndexWriter(random(), dir)

      // Build up test data
      val tokens = getRandomTokens(50) // 50 random values to pick from
      var numDocs = atLeast(1000)
      var expectedTotalDocCount = 0
      val expected = HashMap<String, Int>()
      for (i in 0 until numDocs) {
        var doc = Document()
        // Sometimes we restrict docs to be single-valued, but most of the time they can have up to
        // 5:
        val maxValuesPerDoc: Int
        if (random().nextInt(10) < 8) {
          maxValuesPerDoc = 5
        } else {
          maxValuesPerDoc = 1
        }
        var valCount = random().nextInt(maxValuesPerDoc)
        val docVals = HashSet<String>()
        for (j in 0 until valCount) {
          var tokenIdx = random().nextInt(tokens.size)
          var `val` = tokens[tokenIdx]
          // values should only be counted once per document
          if (docVals.contains(`val`) == false) {
            expected.put(`val`, (expected[`val`] ?: 0) + 1)
          }
          docVals.add(`val`)
          doc.add(SortedSetDocValuesField("field", BytesRef(`val`)))
        }
        // only docs with at least one value in the field should be counted in the total
        if (docVals.isEmpty() == false) {
          expectedTotalDocCount++
        }
        writer.addDocument(doc)
        if (random().nextInt(10) == 0) {
          writer.commit() // sometimes commit
        }
      }

      var searcher = newSearcher(writer.reader)
      writer.close()

      // run iterations with random values of topN
      val iterations = if (TEST_NIGHTLY) 10_000 else 50
      val topNs = IntArray(iterations)
      for (i in 0 until iterations) {
        topNs[i] = atLeast(1)
      }

      var state =
          StringDocValuesReaderState(searcher.indexReader, "field")

      checkTopNFacetResult(expected, expectedTotalDocCount, searcher, state, *topNs)
      checkAllChildrenFacetResult(expected, expectedTotalDocCount, searcher, state)

      IOUtils.close(searcher.indexReader, dir)
    }
  }

  private fun checkTopNFacetResult(expectedCounts: Map<String, Int>, expectedTotalDocsWithValue: Int, searcher: IndexSearcher, state: StringDocValuesReaderState, vararg topNs: Int) {

    var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    for (topN in topNs) {

      val facets: StringValueFacetCounts
      // should get the same result whether-or-not we provide a FacetsCollector since it's doing
      // a MatchAllDocsQuery:
      if (random().nextBoolean()) {
        facets = StringValueFacetCounts(state, c)
      } else {
        facets = StringValueFacetCounts(state)
      }

      // sort expected counts by count, value
      val expectedCountsSorted = ArrayList(expectedCounts.entries)
      expectedCountsSorted.sortWith { a, b ->
            var cmp = b.value.compareTo(a.value) // high-to-low
            if (cmp == 0) {
              cmp = a.key.compareTo(b.key) // low-to-high
            }
            cmp
          }

      // number of labels we expect is the number with a non-zero count
      val expectedLabelCount = expectedCountsSorted.count { it.value > 0 }

      // topN == 0 is intentionally unsupported
      if (topN == 0) {
        expectThrows(IllegalArgumentException::class) { facets.getTopChildren(topN, "field") }
        return
      }

      var facetResult = facets.getTopChildren(topN, "field")

      assertEquals("field", facetResult.dim)
      assertEquals(0, facetResult.path.size)
      assertEquals(expectedTotalDocsWithValue, facetResult.value)
      assertEquals(expectedLabelCount, facetResult.childCount)

      // getAllDims should return a singleton list with the same results as getTopChildren
      var allDims = facets.getAllDims(topN)
      assertEquals(1, allDims.size)
      assertEquals(facetResult, allDims.get(0))

      // test default implementation of getTopDims
      var topNDimsResult = facets.getTopDims(2, topN)
      assertEquals(1, topNDimsResult.size)
      assertEquals(facetResult, topNDimsResult.get(0))

      // test getAllDims(0)
      expectThrows(IllegalArgumentException::class) { facets.getAllDims(0) }

      // This is a little strange, but we request all labels at this point so that when we
      // secondarily sort by label value in order to compare to the expected results, we have
      // all the values. See LUCENE-9991:
      var maxTopN = expectedCountsSorted.size
      facetResult = facets.getTopChildren(maxTopN, "field")

      // also sort expected labels by count, value (these will be sorted by count, ord -- but since
      // we have no insight into the ordinals assigned to the values, we resort)
      facetResult.labelValues.sortWith { a, b ->
            var cmp = b.value.toLong().compareTo(a.value.toLong()) // high-to-low
            if (cmp == 0) {
              cmp = a.label.compareTo(b.label) // low-to-high
            }
            cmp
          }

      for (i in 0 until minOf(topN, maxTopN)) {
        var expectedKey = expectedCountsSorted.get(i).key
        var expectedValue = expectedCountsSorted.get(i).value
        assertEquals(expectedKey, facetResult.labelValues[i].label)
        assertEquals(expectedValue, facetResult.labelValues[i].value)
        // make sure getSpecificValue reports the same count
        assertEquals(expectedValue, facets.getSpecificValue("field", expectedKey))
      }

      // execute a "drill down" query on one of the values at random and make sure the total hits
      // match the expected count provided by faceting
      if (expectedCountsSorted.isEmpty() == false) {
        var q = DrillDownQuery(FacetsConfig())
        var randomTestValIdx = random().nextInt(minOf(expectedCountsSorted.size, topN))
        q.add("field", expectedCountsSorted.get(randomTestValIdx).key)
        searcher.search(q, 1)
        assertEquals(
            expectedCountsSorted.get(randomTestValIdx).value,
            facetResult.labelValues[randomTestValIdx].value)
      }
    }
  }

  private fun checkAllChildrenFacetResult(expectedValueCounts: Map<String, Int>, expectedTotalDocsWithValue: Int, searcher: IndexSearcher, state: StringDocValuesReaderState) {

    var c = searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    val facets: StringValueFacetCounts
    // should get the same result whether-or-not we provide a FacetsCollector since it's doing
    // a MatchAllDocsQuery:
    if (random().nextBoolean()) {
      facets = StringValueFacetCounts(state, c)
    } else {
      facets = StringValueFacetCounts(state)
    }

    val expectedCountsSortedByValue = ArrayList(expectedValueCounts.entries)

    // sort expected counts by value, count
    expectedCountsSortedByValue.sortWith(compareBy<Map.Entry<String, Int>> { it.key }.thenBy { it.value })

    var facetResult = facets.getAllChildren("field")
    assertEquals(expectedTotalDocsWithValue, facetResult.value)

    // since we have no insight into the ordinals assigned to the values, we sort labels by value
    // and count in ascending order in order to compare with expected results
    facetResult.labelValues.sortWith(compareBy<LabelAndValue> { it.label }.thenBy { it.value.toLong() })

    for (i in 0 until expectedCountsSortedByValue.size) {
      var expectedKey = expectedCountsSortedByValue.get(i).key
      var expectedValue = expectedCountsSortedByValue.get(i).value
      assertEquals(expectedKey, facetResult.labelValues[i].label)
      assertEquals(expectedValue, facetResult.labelValues[i].value)
      // make sure getSpecificValue reports the same count
      assertEquals(expectedValue, facets.getSpecificValue("field", expectedKey))
    }
  }
}
