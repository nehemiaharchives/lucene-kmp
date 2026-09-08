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

import kotlin.test.*

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.LongValuesSource
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil

/** Tests long value facets. */
class TestLongValueFacetCounts : FacetTestCase() {

  @Test
  fun testBasic() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)
    for (l in 0L until 100L) {
      var doc = Document()
      doc.add(NumericDocValuesField("field", l % 5))
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    var doc = Document()
    doc.add(NumericDocValuesField("field", Long.MAX_VALUE))
    w.addDocument(doc)

    var r = w.reader
    w.close()

    var s = newSearcher(r)
    var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facets = LongValueFacetCounts("field", fc!!)

    var result = facets.getAllChildrenSortByValue()
    assertEquals(
        "dim=field path=[] value=101 childCount=6\n  0 (20)\n  1 (20)\n  2 (20)\n  3 (20)\n  "
            + "4 (20)\n  9223372036854775807 (1)\n",
        result.toString())

    var topChildrenResult = facets.getTopChildren(2, "field")
    assertEquals(
        "dim=field path=[] value=101 childCount=6\n  0 (20)\n  1 (20)\n",
        topChildrenResult.toString())

    assertFacetResult(
        facets.getAllChildren("field"),
        "field",
        emptyArray<String>(),
        6,
        101,
        LabelAndValue("0", 20),
        LabelAndValue("1", 20),
        LabelAndValue("2", 20),
        LabelAndValue("3", 20),
        LabelAndValue("4", 20),
        LabelAndValue("9223372036854775807", 1))

    r.close()
    d.close()
  }

  // See: LUCENE-10070
  @Test
  fun testCountAll() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)

    for (i in 0 until 10) {
      var doc = Document()
      doc.add(StringField("id", i.toString(), Field.Store.NO))
      doc.add(NumericDocValuesField("field", (i % 2).toLong()))
      w.addDocument(doc)
    }

    w.deleteDocuments(Term("id", "0"))

    var r = w.reader
    w.close()

    var facets = LongValueFacetCounts("field", r)

    var result = facets.getAllChildrenSortByValue()
    assertEquals("dim=field path=[] value=9 childCount=2\n  0 (4)\n  1 (5)\n", result.toString())
    result = facets.getTopChildren(10, "field")
    assertEquals("dim=field path=[] value=9 childCount=2\n  1 (5)\n  0 (4)\n", result.toString())

    assertFacetResult(
        facets.getAllChildren("field"),
        "field",
        emptyArray<String>(),
        2,
        9,
        LabelAndValue("0", 4),
        LabelAndValue("1", 5))

    r.close()
    d.close()
  }

  @Test
  fun testOnlyBigLongs() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)
    for (l in 0L until 3L) {
      var doc = Document()
      doc.add(NumericDocValuesField("field", Long.MAX_VALUE - l))
      w.addDocument(doc)
    }

    var r = w.reader
    w.close()

    var s = newSearcher(r)
    var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facets = LongValueFacetCounts("field", fc!!)

    var result = facets.getAllChildrenSortByValue()

    assertFacetResult(
        facets.getAllChildren("field"),
        "field",
        emptyArray<String>(),
        3,
        3,
        LabelAndValue("9223372036854775805", 1),
        LabelAndValue("9223372036854775806", 1),
        LabelAndValue("9223372036854775807", 1))

    // since we have no insight into the value order in the hashMap, we sort labels by value and
    // count in
    // ascending order in order to compare with expected results
    result.labelValues.sortWith(compareBy<LabelAndValue> { it.label }.thenBy { it.value.toLong() })

    assertEquals(
        "dim=field path=[] value=3 childCount=3\n  9223372036854775805 (1)\n  "
            + "9223372036854775806 (1)\n  9223372036854775807 (1)\n",
        result.toString())
    r.close()
    d.close()
  }

  @Test
  fun testGetAllDims() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)
    for (l in 0L until 100L) {
      var doc = Document()
      doc.add(NumericDocValuesField("field", l % 5))
      w.addDocument(doc)
    }

    // Also add Long.MAX_VALUE
    var doc = Document()
    doc.add(NumericDocValuesField("field", Long.MAX_VALUE))
    w.addDocument(doc)

    var r = w.reader
    w.close()

    var s = newSearcher(r)
    var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())

    var facets = LongValueFacetCounts("field", fc!!)

    var result = facets.getAllDims(10)
    assertEquals(1, result.size)
    assertEquals(
        "dim=field path=[] value=101 childCount=6\n  0 (20)\n  1 (20)\n  2 (20)\n  "
            + "3 (20)\n  4 (20)\n  9223372036854775807 (1)\n",
        result.get(0).toString())

    // test default implementation of getTopDims
    var getTopDimResult = facets.getTopDims(1, 1)
    assertEquals(1, getTopDimResult.size)
    assertEquals(
        "dim=field path=[] value=101 childCount=6\n  0 (20)\n", getTopDimResult.get(0).toString())

    // test getTopDims(10, 10) and expect same results from getAllDims(10)
    var allDimsResults = facets.getTopDims(10, 10)
    assertEquals(result, allDimsResults)

    // test getTopDims(0, 1)
    var topDimsResults2 = facets.getTopDims(0, 1)
    assertEquals(0, topDimsResults2.size)
    // test getAllDims(0)
    expectThrows(IllegalArgumentException::class) { facets.getAllDims(0) }

    r.close()
    d.close()
  }

  @Test
  fun testRandomSingleValued() {
    var dir = newDirectory()
    var w = RandomIndexWriter(random(), dir)

    var docCount = atLeast(1000)
    var missingChance = random().nextDouble()
    var maxValue: Long
    if (random().nextBoolean()) {
      maxValue = random().nextLong() and Long.MAX_VALUE
    } else {
      maxValue = random().nextInt(1000).toLong()
    }
    if (VERBOSE) {
      println(
          "TEST: valueCount="
              + docCount
              + " valueRange=-"
              + maxValue
              + "-"
              + maxValue
              + " missingChance="
              + missingChance)
    }
    val values = arrayOfNulls<Long>(docCount)
    var missingCount = 0
    for (i in 0 until docCount) {
      var doc = Document()
      doc.add(IntPoint("id", i))
      if (random().nextDouble() > missingChance) {
        var value = TestUtil.nextLong(random(), -maxValue, maxValue)
        doc.add(NumericDocValuesField("field", value))
        values[i] = value
      } else {
        missingCount++
      }
      w.addDocument(doc)
    }

    var r = w.reader
    w.close()

    var s = newSearcher(r)

    var iters = atLeast(100)
    for (iter in 0 until iters) {
      if (VERBOSE) {
        println("\nTEST: iter=" + iter)
        println("  test all docs")
      }

      // all docs
      var expected = HashMap<Long, Int>()
      var expectedChildCount = 0
      for (i in 0 until docCount) {
        if (values[i] != null) {
          var curCount = expected.get(values[i])
          if (curCount == null) {
            curCount = 0
            expectedChildCount++
          }
          expected.put(values[i]!!, curCount + 1)
        }
      }

      var expectedCounts = ArrayList(expected.entries)

      // sort by value
      expectedCounts.sortBy { it.key }

      var facetCounts: LongValueFacetCounts
      if (random().nextBoolean()) {
        var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())
        if (random().nextBoolean()) {
          if (VERBOSE) {
            println("  use value source")
          }
          if (random().nextBoolean()) {
            facetCounts =
                LongValueFacetCounts("field", LongValuesSource.fromLongField("field"), fc!!)
          } else if (random().nextBoolean()) {
            facetCounts =
                LongValueFacetCounts("field", MultiLongValuesSource.fromLongField("field"), fc!!)
          } else {
            facetCounts =
                LongValueFacetCounts(
                    "field",
                    MultiLongValuesSource.fromSingleValued(LongValuesSource.fromLongField("field")),
                    fc!!)
          }
        } else {
          if (VERBOSE) {
            println("  use doc values")
          }
          facetCounts = LongValueFacetCounts("field", fc!!)
        }
      } else {
        // optimized count all:
        if (random().nextBoolean()) {
          if (VERBOSE) {
            println("  count all value source")
          }
          if (random().nextBoolean()) {
            facetCounts =
                LongValueFacetCounts("field", LongValuesSource.fromLongField("field"), r)
          } else if (random().nextBoolean()) {
            facetCounts =
                LongValueFacetCounts("field", MultiLongValuesSource.fromLongField("field"), r)
          } else {
            facetCounts =
                LongValueFacetCounts(
                    "field",
                    MultiLongValuesSource.fromSingleValued(LongValuesSource.fromLongField("field")),
                    r)
          }
        } else {
          if (VERBOSE) {
            println("  count all doc values")
          }
          facetCounts = LongValueFacetCounts("field", r)
        }
      }

      var actual = facetCounts.getAllChildrenSortByValue()
      assertSame(
          "all docs, sort facets by value",
          expectedCounts,
          expectedChildCount,
          docCount - missingCount,
          actual,
          Int.MAX_VALUE)

      // test getAllChildren
      expectedCounts.sortWith(compareBy<Map.Entry<Long, Int>> { it.key }.thenBy { it.value })
      var allChildren = facetCounts.getAllChildren("field")
      // sort labels by value, count in ascending order
      allChildren.labelValues.sortWith(compareBy<LabelAndValue> { it.label }.thenBy { it.value.toLong() })
      assertSame(
          "test getAllChildren",
          expectedCounts,
          expectedChildCount,
          docCount - missingCount,
          actual,
          Int.MAX_VALUE)

      // sort by count
      expectedCounts.sortWith(Comparator { a, b ->
            var cmp = -a.value.compareTo(b.value)
            if (cmp == 0) {
              // tie break by value
              cmp = a.key.compareTo(b.key)
            }
            cmp
          })
      var topN: Int
      if (random().nextBoolean()) {
        topN = docCount
      } else {
        topN = random().nextInt(1, docCount)
      }
      if (VERBOSE) {
        println("  topN=" + topN)
      }
      actual = facetCounts.getTopChildren(topN, "field")
      assertSame(
          "all docs, sort facets by count",
          expectedCounts,
          expectedChildCount,
          docCount - missingCount,
          actual,
          topN)

      // subset of docs
      var minId = random().nextInt(docCount)
      var maxId = random().nextInt(docCount)
      if (minId > maxId) {
        var tmp = minId
        minId = maxId
        maxId = tmp
      }
      if (VERBOSE) {
        println("  test id range " + minId + "-" + maxId)
      }

      var fc =
          s.search(IntPoint.newRangeQuery("id", minId, maxId), FacetsCollectorManager())
      if (random().nextBoolean()) {
        if (VERBOSE) {
          println("  use doc values")
        }
        facetCounts = LongValueFacetCounts("field", fc!!)
      } else {
        if (VERBOSE) {
          println("  use value source")
        }
        if (random().nextBoolean()) {
          facetCounts =
              LongValueFacetCounts("field", LongValuesSource.fromLongField("field"), fc!!)
        } else if (random().nextBoolean()) {
          facetCounts =
              LongValueFacetCounts("field", MultiLongValuesSource.fromLongField("field"), fc!!)
        } else {
          facetCounts =
              LongValueFacetCounts(
                  "field",
                  MultiLongValuesSource.fromSingleValued(LongValuesSource.fromLongField("field")),
                  fc)
        }
      }

      expected = HashMap<Long, Int>()
      expectedChildCount = 0
      var totCount = 0
      for (i in minId..maxId) {
        if (values[i] != null) {
          totCount++
          var curCount = expected.get(values[i])
          if (curCount == null) {
            expectedChildCount++
            curCount = 0
          }
          expected.put(values[i]!!, curCount + 1)
        }
      }
      expectedCounts = ArrayList(expected.entries)

      // sort by value
      expectedCounts.sortBy { it.key }
      actual = facetCounts.getAllChildrenSortByValue()
      assertSame(
          "id " + minId + "-" + maxId + ", sort facets by value",
          expectedCounts,
          expectedChildCount,
          totCount,
          actual,
          Int.MAX_VALUE)

      // sort by count
      expectedCounts.sortWith(Comparator { a, b ->
            var cmp = -a.value.compareTo(b.value)
            if (cmp == 0) {
              // tie break by value
              cmp = a.key.compareTo(b.key)
            }
            cmp
          })
      if (random().nextBoolean()) {
        topN = docCount
      } else {
        topN = random().nextInt(1, docCount)
      }
      actual = facetCounts.getTopChildren(topN, "field")
      assertSame(
          "id " + minId + "-" + maxId + ", sort facets by count",
          expectedCounts,
          expectedChildCount,
          totCount,
          actual,
          topN)
    }
    r.close()
    dir.close()
  }

  @Test
  fun testRandomMultiValued() {
    var dir = newDirectory()
    var w = RandomIndexWriter(random(), dir)

    var docCount = atLeast(1000)
    var missingChance = random().nextDouble()

    // sometimes exercise codec optimizations when a claimed multi valued field is in fact single
    // valued:
    var allSingleValued = rarely()
    var maxValue: Long

    if (random().nextBoolean()) {
      maxValue = random().nextLong() and Long.MAX_VALUE
    } else {
      maxValue = random().nextInt(1000).toLong()
    }
    if (VERBOSE) {
      println(
          "TEST: valueCount="
              + docCount
              + " valueRange=-"
              + maxValue
              + "-"
              + maxValue
              + " missingChance="
              + missingChance
              + " allSingleValued="
              + allSingleValued)
    }

    val values = arrayOfNulls<LongArray>(docCount)
    for (i in 0 until docCount) {
      var doc = Document()
      doc.add(IntPoint("id", i))
      if (random().nextDouble() > missingChance) {
        if (allSingleValued) {
          values[i] = LongArray(1)
        } else {
          values[i] = LongArray(TestUtil.nextInt(random(), 1, 5))
        }

        for (j in 0 until values[i]!!.size) {
          var value = TestUtil.nextLong(random(), -maxValue, maxValue)
          values[i]!![j] = value
          doc.add(SortedNumericDocValuesField("field", value))
        }

        if (VERBOSE) {
          println("  doc=" + i + " values=" + values[i].contentToString())
        }

        // sort values to enable duplicate detection by comparing with the previous value
        values[i]!!.sort()
      } else {
        if (VERBOSE) {
          println("  doc=" + i + " missing values")
        }
      }
      w.addDocument(doc)
    }

    var r = w.reader
    w.close()

    var s = newSearcher(r)

    var iters = atLeast(100)
    for (iter in 0 until iters) {
      if (VERBOSE) {
        println("\nTEST: iter=" + iter)
        println("  test all docs")
      }

      // all docs
      var expected = HashMap<Long, Int>()
      var expectedTotalCount = 0
      for (i in 0 until docCount) {
        if (values[i] != null && values[i]!!.size > 0) {
          expectedTotalCount++
          setExpectedFrequencies(values[i]!!, expected)
        }
      }

      var expectedCounts = ArrayList(expected.entries)
      var expectedChildCount = expected.size

      // sort by value
      expectedCounts.sortBy { it.key }

      var facetCounts: LongValueFacetCounts
      if (random().nextBoolean()) {
        var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())
        if (VERBOSE) {
          println("  use doc values")
        }
        if (random().nextBoolean()) {
          facetCounts = LongValueFacetCounts("field", fc!!)
        } else {
          facetCounts =
              LongValueFacetCounts("field", MultiLongValuesSource.fromLongField("field"), fc!!)
        }
      } else {
        // optimized count all:
        if (VERBOSE) {
          println("  count all doc values")
        }
        if (random().nextBoolean()) {
          facetCounts = LongValueFacetCounts("field", r)
        } else {
          facetCounts =
              LongValueFacetCounts("field", MultiLongValuesSource.fromLongField("field"), r)
        }
      }

      var actual = facetCounts.getAllChildrenSortByValue()
      assertSame(
          "all docs, sort facets by value",
          expectedCounts,
          expectedChildCount,
          expectedTotalCount,
          actual,
          Int.MAX_VALUE)

      // test getAllChildren
      expectedCounts.sortWith(compareBy<Map.Entry<Long, Int>> { it.key }.thenBy { it.value })
      var allChildren = facetCounts.getAllChildren("field")
      // sort labels by value, count in ascending order
      allChildren.labelValues.sortWith(compareBy<LabelAndValue> { it.label }.thenBy { it.value.toLong() })
      assertSame(
          "test getAllChildren",
          expectedCounts,
          expectedChildCount,
          expectedTotalCount,
          actual,
          Int.MAX_VALUE)

      // sort by count
      expectedCounts.sortWith(Comparator { a, b ->
            var cmp = -a.value.compareTo(b.value)
            if (cmp == 0) {
              // tie break by value
              cmp = a.key.compareTo(b.key)
            }
            cmp
          })
      var topN: Int
      if (random().nextBoolean()) {
        topN = docCount
      } else {
        topN = random().nextInt(1, docCount)
      }
      if (VERBOSE) {
        println("  topN=" + topN)
      }
      actual = facetCounts.getTopChildren(topN, "field")
      assertSame(
          "all docs, sort facets by count",
          expectedCounts,
          expectedChildCount,
          expectedTotalCount,
          actual,
          topN)

      // subset of docs
      var minId = random().nextInt(docCount)
      var maxId = random().nextInt(docCount)
      if (minId > maxId) {
        var tmp = minId
        minId = maxId
        maxId = tmp
      }
      if (VERBOSE) {
        println("  test id range " + minId + "-" + maxId)
      }

      var fc =
          s.search(IntPoint.newRangeQuery("id", minId, maxId), FacetsCollectorManager())
      if (random().nextBoolean()) {
        facetCounts = LongValueFacetCounts("field", fc!!)
      } else {
        facetCounts =
            LongValueFacetCounts("field", MultiLongValuesSource.fromLongField("field"), fc!!)
      }

      expected = HashMap<Long, Int>()
      expectedTotalCount = 0
      for (i in minId..maxId) {
        if (values[i] != null && values[i]!!.size > 0) {
          expectedTotalCount++
          setExpectedFrequencies(values[i]!!, expected)
        }
      }
      expectedCounts = ArrayList(expected.entries)
      expectedChildCount = expected.size

      // sort by value
      expectedCounts.sortBy { it.key }
      actual = facetCounts.getAllChildrenSortByValue()
      assertSame(
          "id " + minId + "-" + maxId + ", sort facets by value",
          expectedCounts,
          expectedChildCount,
          expectedTotalCount,
          actual,
          Int.MAX_VALUE)

      // sort by count
      expectedCounts.sortWith(Comparator { a, b ->
            var cmp = -a.value.compareTo(b.value)
            if (cmp == 0) {
              // tie break by value
              cmp = a.key.compareTo(b.key)
            }
            cmp
          })
      if (random().nextBoolean()) {
        topN = docCount
      } else {
        topN = random().nextInt(1, docCount)
      }
      actual = facetCounts.getTopChildren(topN, "field")
      assertSame(
          "id " + minId + "-" + maxId + ", sort facets by count",
          expectedCounts,
          expectedChildCount,
          expectedTotalCount,
          actual,
          topN)
    }
    r.close()
    dir.close()
  }

  private fun setExpectedFrequencies(values: LongArray, expected: MutableMap<Long, Int>) {
    var previousValue = 0L
    for (j in 0 until values.size) {
      if (j == 0 || previousValue != values[j]) {
        var curCount = (expected[values[j]] ?: 0)
        expected.put(values[j], curCount + 1)
      }
      previousValue = values[j]
    }
  }

  private fun assertSame(
      desc: String,
      expectedCounts: List<Map.Entry<Long, Int>>,
      expectedChildCount: Int,
      expectedTotalCount: Int,
      actual: FacetResult,
      topN: Int) {
    var expectedTopN = minOf(topN, expectedCounts.size)
    if (VERBOSE) {
      println("  expected topN=" + expectedTopN)
      for (i in 0 until expectedTopN) {
        println(
            "    "
                + i
                + ": value="
                + expectedCounts.get(i).key
                + " count="
                + expectedCounts.get(i).value)
      }
      println("  actual topN=" + actual.labelValues.size)
      for (i in 0 until actual.labelValues.size) {
        println(
            "    "
                + i
                + ": value="
                + actual.labelValues[i].label
                + " count="
                + actual.labelValues[i].value)
      }
    }
    assertEquals(expectedTopN, actual.labelValues.size, desc + ": topN")
    assertEquals(expectedChildCount, actual.childCount, desc + ": childCount")
    assertEquals(expectedTotalCount, actual.value.toInt(), desc + ": totCount")
    assertTrue(actual.labelValues.size <= topN)

    for (i in 0 until expectedTopN) {
      assertEquals(expectedCounts.get(i).key.toString(), actual.labelValues[i].label, desc + ": label[" + i + "]")
      assertEquals(expectedCounts.get(i).value.toInt(), actual.labelValues[i].value.toInt(), desc + ": counts[" + i + "]")
    }
  }

  /**
   * LUCENE-9964: Duplicate long values in a document field should only be counted once when using
   * SortedNumericDocValuesFields
   */
  @Test
  fun testDuplicateLongValues() {
    var dir = newDirectory()
    var w = RandomIndexWriter(random(), dir)

    var doc = Document()
    // these two values are not unique in a document
    doc.add(SortedNumericDocValuesField("field", 42))
    doc.add(SortedNumericDocValuesField("field", 42))
    w.addDocument(doc)
    doc = Document()
    doc.add(SortedNumericDocValuesField("field", 43))
    doc.add(SortedNumericDocValuesField("field", 43))
    w.addDocument(doc)

    var r = w.reader
    w.close()
    var facetCounts = LongValueFacetCounts("field", r)

    var fr = facetCounts.getAllChildrenSortByValue()
    for (labelAndValue in fr.labelValues) {
      assertTrue(labelAndValue.value.equals(1))
    }

    assertFacetResult(
        facetCounts.getAllChildren("field"),
        "field",
        emptyArray<String>(),
        2,
        2,
        LabelAndValue("42", 1),
        LabelAndValue("43", 1))

    r.close()
    dir.close()
  }
}
