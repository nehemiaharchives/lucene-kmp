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
package org.gnit.lucenekmp.facet.facetset

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.LongRange
import org.gnit.lucenekmp.facet.*
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRangeFacetSetMatcher : FacetTestCase() {
    companion object {
        private const val FORD_ORD = 100
        private const val TOYOTA_ORD = 101
        private const val CHEVY_ORD = 102
        private const val NISSAN_ORD = 103
        private val MANUFACTURER_ORDS = intArrayOf(FORD_ORD, TOYOTA_ORD, CHEVY_ORD, NISSAN_ORD)
        private val YEARS = intArrayOf(2010, 2011, 2012, 2013, 2014)
    }
  @Test
  fun testLongRangeFacetSetMatching() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)

    val allSets = mutableListOf<LongFacetSet>()
    for (manufacturerOrd in MANUFACTURER_ORDS) {
      for (year in YEARS) {
        allSets.add(LongFacetSet(manufacturerOrd.toLong(), year.toLong()))
      }
    }

    var numFord2011_2013 = 0
    var numFord2010_2014 = 0
    var numFord2011_2014 = 0
    var numFord2010_2013 = 0
    var numMatchingDocs = 0
    for (i in 0 until 100) {
      var doc = Document()
      var numSets = TestUtil.nextInt(random(), 1, 4)
      allSets.shuffle(random())
      val facetSets = allSets.subList(0, numSets).toTypedArray()
      var matchingDoc = false
      for (facetSet in facetSets) {
        if (FORD_ORD.toLong() != facetSet.values[0]) {
          continue
        }
        var year = facetSet.values[1]
        if (year > 2010 && year < 2014) {
          ++numFord2010_2013
          ++numFord2010_2014
          ++numFord2011_2013
          ++numFord2011_2014
          matchingDoc = true
        } else if (year == 2014L) {
          ++numFord2010_2014
          ++numFord2011_2014
          matchingDoc = true
        } else if (year == 2010L) {
          ++numFord2010_2014
          ++numFord2010_2013
          matchingDoc = true
        }
      }
      numMatchingDocs += if (matchingDoc) 1 else 0
      doc.add(FacetSetsField.create("field", *facetSets))
      w.addDocument(doc)
    }

    var r = w.reader
    w.close()

    var s = newSearcher(r)
    var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets =
        MatchingFacetSetsCounts(
            "field",
            fc,
            FacetSetDecoder(FacetSetDecoder::decodeLongs),
            RangeFacetSetMatcher(
                "Ford [2010-2014]", singleLong(FORD_ORD.toLong()), longRange(2010, true, 2014, true)),
            RangeFacetSetMatcher(
                "Ford (2010-2014]", singleLong(FORD_ORD.toLong()), longRange(2010, false, 2014, true)),
            RangeFacetSetMatcher(
                "Ford [2010-2014)", singleLong(FORD_ORD.toLong()), longRange(2010, true, 2014, false)),
            RangeFacetSetMatcher(
                "Ford (2010-2014)", singleLong(FORD_ORD.toLong()), longRange(2010, false, 2014, false)))

    var result = facets.getAllChildren("field")!!

    assertEquals("field", result.dim)
    assertEquals(0, result.path.size)
    assertEquals(numMatchingDocs, result.value)
    assertEquals(4, result.childCount)

    assertEquals(LabelAndValue("Ford [2010-2014]", numFord2010_2014), result.labelValues[0])
    assertEquals(LabelAndValue("Ford (2010-2014]", numFord2011_2014), result.labelValues[1])
    assertEquals(LabelAndValue("Ford [2010-2014)", numFord2010_2013), result.labelValues[2])
    assertEquals(LabelAndValue("Ford (2010-2014)", numFord2011_2013), result.labelValues[3])

    r.close()
    d.close()
  }

  @Test
  fun testIntRangeFacetSetMatching() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)

    val allSets = mutableListOf<IntFacetSet>()
    for (manufacturerOrd in MANUFACTURER_ORDS) {
      for (year in YEARS) {
        allSets.add(IntFacetSet(manufacturerOrd, year))
      }
    }

    var numFord2011_2013 = 0
    var numFord2010_2014 = 0
    var numFord2011_2014 = 0
    var numFord2010_2013 = 0
    var numMatchingDocs = 0
    for (i in 0 until 100) {
      var doc = Document()
      var numSets = TestUtil.nextInt(random(), 1, 4)
      allSets.shuffle(random())
      val facetSets = allSets.subList(0, numSets).toTypedArray()
      var matchingDoc = false
      for (facetSet in facetSets) {
        if (FORD_ORD != facetSet.values[0]) {
          continue
        }
        var year = facetSet.values[1]
        if (year > 2010 && year < 2014) {
          ++numFord2010_2013
          ++numFord2010_2014
          ++numFord2011_2013
          ++numFord2011_2014
          matchingDoc = true
        } else if (year == 2014) {
          ++numFord2010_2014
          ++numFord2011_2014
          matchingDoc = true
        } else if (year == 2010) {
          ++numFord2010_2014
          ++numFord2010_2013
          matchingDoc = true
        }
      }
      numMatchingDocs += if (matchingDoc) 1 else 0
      doc.add(FacetSetsField.create("field", *facetSets))
      w.addDocument(doc)
    }

    var r = w.reader
    w.close()

    var s = newSearcher(r)
    var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets =
        MatchingFacetSetsCounts(
            "field",
            fc,
            FacetSetDecoder(FacetSetDecoder::decodeInts),
            RangeFacetSetMatcher(
                "Ford [2010-2014]", singleLong(FORD_ORD.toLong()), longRange(2010, true, 2014, true)),
            RangeFacetSetMatcher(
                "Ford (2010-2014]", singleLong(FORD_ORD.toLong()), longRange(2010, false, 2014, true)),
            RangeFacetSetMatcher(
                "Ford [2010-2014)", singleLong(FORD_ORD.toLong()), longRange(2010, true, 2014, false)),
            RangeFacetSetMatcher(
                "Ford (2010-2014)", singleLong(FORD_ORD.toLong()), longRange(2010, false, 2014, false)))

    var result = facets.getAllChildren("field")!!

    assertEquals("field", result.dim)
    assertEquals(0, result.path.size)
    assertEquals(numMatchingDocs, result.value)
    assertEquals(4, result.childCount)

    assertEquals(LabelAndValue("Ford [2010-2014]", numFord2010_2014), result.labelValues[0])
    assertEquals(LabelAndValue("Ford (2010-2014]", numFord2011_2014), result.labelValues[1])
    assertEquals(LabelAndValue("Ford [2010-2014)", numFord2010_2013), result.labelValues[2])
    assertEquals(LabelAndValue("Ford (2010-2014)", numFord2011_2013), result.labelValues[3])

    r.close()
    d.close()
  }

  @Test
  fun testDoubleRangeFacetSetMatching() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)

    val allSets = mutableListOf<DoubleFacetSet>()
    for (manufacturerOrd in MANUFACTURER_ORDS) {
      for (year in YEARS) {
        allSets.add(DoubleFacetSet(manufacturerOrd.toDouble(), year + 0.5))
      }
    }

    var numFord2011_2014 = 0
    var numFord2010_2015 = 0
    var numFord2011_2015 = 0
    var numFord2010_2014 = 0
    var numMatchingDocs = 0
    for (i in 0 until 100) {
      var doc = Document()
      var numSets = TestUtil.nextInt(random(), 1, 4)
      allSets.shuffle(random())
      val facetSets = allSets.subList(0, numSets).toTypedArray()
      var matchingDoc = false
      for (facetSet in facetSets) {
        if (FORD_ORD.toDouble().compareTo(facetSet.values[0].toDouble()) != 0) {
          continue
        }
        var year = facetSet.values[1]
        if (year > 2010.5 && year < 2014.5) {
          ++numFord2010_2014
          ++numFord2010_2015
          ++numFord2011_2014
          ++numFord2011_2015
          matchingDoc = true
        } else if (year == 2014.5) {
          ++numFord2010_2015
          ++numFord2011_2015
          matchingDoc = true
        } else if (year == 2010.5) {
          ++numFord2010_2015
          ++numFord2010_2014
          matchingDoc = true
        }
      }
      numMatchingDocs += if (matchingDoc) 1 else 0
      doc.add(FacetSetsField.create("field", *facetSets))
      w.addDocument(doc)
    }

    var r = w.reader
    w.close()

    var s = newSearcher(r)
    var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets =
        MatchingFacetSetsCounts(
            "field",
            fc,
            FacetSetDecoder(FacetSetDecoder::decodeLongs),
            RangeFacetSetMatcher(
                "Ford [2010-2015]",
                singleDouble(FORD_ORD.toDouble()),
                doubleRange(2010.0, true, 2015.0, true)),
            RangeFacetSetMatcher(
                "Ford (2011-2015]",
                singleDouble(FORD_ORD.toDouble()),
                doubleRange(2011.0, false, 2015.0, true)),
            RangeFacetSetMatcher(
                "Ford [2010-2014)",
                singleDouble(FORD_ORD.toDouble()),
                doubleRange(2010.0, true, 2014.0, false)),
            RangeFacetSetMatcher(
                "Ford (2011-2014)",
                singleDouble(FORD_ORD.toDouble()),
                doubleRange(2011.0, false, 2014.0, false)))

    var result = facets.getAllChildren("field")!!

    assertEquals("field", result.dim)
    assertEquals(0, result.path.size)
    assertEquals(numMatchingDocs, result.value)
    assertEquals(4, result.childCount)

    assertEquals(LabelAndValue("Ford [2010-2015]", numFord2010_2015), result.labelValues[0])
    assertEquals(LabelAndValue("Ford (2011-2015]", numFord2011_2015), result.labelValues[1])
    assertEquals(LabelAndValue("Ford [2010-2014)", numFord2010_2014), result.labelValues[2])
    assertEquals(LabelAndValue("Ford (2011-2014)", numFord2011_2014), result.labelValues[3])

    r.close()
    d.close()
  }

  @Test
  fun testFloatRangeFacetSetMatching() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)

    val allSets = mutableListOf<FloatFacetSet>()
    for (manufacturerOrd in MANUFACTURER_ORDS) {
      for (year in YEARS) {
        allSets.add(FloatFacetSet(manufacturerOrd.toFloat(), year + 0.5f))
      }
    }

    var numFord2011_2014 = 0
    var numFord2010_2015 = 0
    var numFord2011_2015 = 0
    var numFord2010_2014 = 0
    var numMatchingDocs = 0
    for (i in 0 until 100) {
      var doc = Document()
      var numSets = TestUtil.nextInt(random(), 1, 4)
      allSets.shuffle(random())
      val facetSets = allSets.subList(0, numSets).toTypedArray()
      var matchingDoc = false
      for (facetSet in facetSets) {
        if (FORD_ORD.toDouble().compareTo(facetSet.values[0].toDouble()) != 0) {
          continue
        }
        var year = facetSet.values[1]
        if (year > 2010.5f && year < 2014.5f) {
          ++numFord2010_2014
          ++numFord2010_2015
          ++numFord2011_2014
          ++numFord2011_2015
          matchingDoc = true
        } else if (year == 2014.5f) {
          ++numFord2010_2015
          ++numFord2011_2015
          matchingDoc = true
        } else if (year == 2010.5f) {
          ++numFord2010_2015
          ++numFord2010_2014
          matchingDoc = true
        }
      }
      numMatchingDocs += if (matchingDoc) 1 else 0
      doc.add(FacetSetsField.create("field", *facetSets))
      w.addDocument(doc)
    }

    var r = w.reader
    w.close()

    var s = newSearcher(r)
    var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var facets =
        MatchingFacetSetsCounts(
            "field",
            fc,
            FacetSetDecoder(FacetSetDecoder::decodeInts),
            RangeFacetSetMatcher(
                "Ford [2010-2015]",
                singleFloat(FORD_ORD.toFloat()),
                floatRange(2010.0f, true, 2015.0f, true)),
            RangeFacetSetMatcher(
                "Ford (2010-2015]",
                singleFloat(FORD_ORD.toFloat()),
                floatRange(2010.5f, false, 2015.0f, true)),
            RangeFacetSetMatcher(
                "Ford [2010-2014)",
                singleFloat(FORD_ORD.toFloat()),
                floatRange(2010.0f, true, 2014.0f, false)),
            RangeFacetSetMatcher(
                "Ford (2011-2014)",
                singleFloat(FORD_ORD.toFloat()),
                floatRange(2011.0f, false, 2014.0f, false)))

    var result = facets.getAllChildren("field")!!

    assertEquals("field", result.dim)
    assertEquals(0, result.path.size)
    assertEquals(numMatchingDocs, result.value)
    assertEquals(4, result.childCount)

    assertEquals(LabelAndValue("Ford [2010-2015]", numFord2010_2015), result.labelValues[0])
    assertEquals(LabelAndValue("Ford (2010-2015]", numFord2011_2015), result.labelValues[1])
    assertEquals(LabelAndValue("Ford [2010-2014)", numFord2010_2014), result.labelValues[2])
    assertEquals(LabelAndValue("Ford (2011-2014)", numFord2011_2014), result.labelValues[3])

    r.close()
    d.close()
  }

  @Test
  fun testLongRangeFacetSetMatchingWithFastMatchQuery() {
    var d = newDirectory()
    var w = RandomIndexWriter(random(), d)

    val allSets = mutableListOf<LongFacetSet>()
    for (manufacturerOrd in MANUFACTURER_ORDS) {
      for (year in YEARS) {
        allSets.add(LongFacetSet(manufacturerOrd.toLong(), year.toLong()))
      }
    }

    var numFord2011_2013 = 0
    var numFord2010_2014 = 0
    var numFord2011_2014 = 0
    var numFord2010_2013 = 0
    var numMatchingDocs = 0
    for (i in 0 until 100) {
      var doc = Document()
      var numSets = TestUtil.nextInt(random(), 1, 4)
      allSets.shuffle(random())
      val facetSets = allSets.subList(0, numSets).toTypedArray()
      var matchingDoc = false
      for (facetSet in facetSets) {
        if (FORD_ORD.toLong() != facetSet.values[0]) {
          continue
        }
        var year = facetSet.values[1]
        if (year > 2010 && year < 2014) {
          ++numFord2010_2013
          ++numFord2010_2014
          ++numFord2011_2013
          ++numFord2011_2014
          matchingDoc = true
        } else if (year == 2014L) {
          ++numFord2010_2014
          ++numFord2011_2014
          matchingDoc = true
        } else if (year == 2010L) {
          ++numFord2010_2014
          ++numFord2010_2013
          matchingDoc = true
        }
      }
      numMatchingDocs += if (matchingDoc) 1 else 0
      doc.add(FacetSetsField.create("field", *facetSets))
      // add fields for drill-down + fast matching
      addFastMatchField("manufacturer", doc, facetSets, 0)
      addFastMatchField("year", doc, facetSets, 1)
      w.addDocument(doc)
    }

    var r = w.reader
    w.close()

    var s = newSearcher(r)
    var fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!

    var fastMatchQuery =
        BooleanQuery.Builder()
            .add(createFastMatchQuery("manufacturer", FORD_ORD.toLong(), FORD_ORD.toLong()), BooleanClause.Occur.MUST)
            .add(createFastMatchQuery("year", 2010, 2014), BooleanClause.Occur.MUST)
            .build()
    var facets =
        MatchingFacetSetsCounts(
            "field",
            fc,
            FacetSetDecoder(FacetSetDecoder::decodeLongs),
            fastMatchQuery,
            RangeFacetSetMatcher(
                "Ford [2010-2014]", singleLong(FORD_ORD.toLong()), longRange(2010, true, 2014, true)),
            RangeFacetSetMatcher(
                "Ford (2010-2014]", singleLong(FORD_ORD.toLong()), longRange(2010, false, 2014, true)),
            RangeFacetSetMatcher(
                "Ford [2010-2014)", singleLong(FORD_ORD.toLong()), longRange(2010, true, 2014, false)),
            RangeFacetSetMatcher(
                "Ford (2010-2014)", singleLong(FORD_ORD.toLong()), longRange(2010, false, 2014, false)))

    var result = facets.getAllChildren("field")!!

    assertEquals("field", result.dim)
    assertEquals(0, result.path.size)
    assertEquals(numMatchingDocs, result.value)
    assertEquals(4, result.childCount)

    assertEquals(LabelAndValue("Ford [2010-2014]", numFord2010_2014), result.labelValues[0])
    assertEquals(LabelAndValue("Ford (2010-2014]", numFord2011_2014), result.labelValues[1])
    assertEquals(LabelAndValue("Ford [2010-2014)", numFord2010_2013), result.labelValues[2])
    assertEquals(LabelAndValue("Ford (2010-2014)", numFord2011_2013), result.labelValues[3])

    r.close()
    d.close()
  }


    private fun singleLong(value: Long): DimRange = DimRange.fromLongs(value, true, value, true)

    private fun longRange(min: Long, minExclusive: Boolean, max: Long, maxExclusive: Boolean): DimRange =
        DimRange.fromLongs(min, minExclusive, max, maxExclusive)

    private fun singleDouble(value: Double): DimRange = DimRange.fromDoubles(value, true, value, true)

    private fun doubleRange(min: Double, minExclusive: Boolean, max: Double, maxExclusive: Boolean): DimRange =
        DimRange.fromDoubles(min, minExclusive, max, maxExclusive)

    private fun singleFloat(value: Float): DimRange = DimRange.fromFloats(value, true, value, true)

    private fun floatRange(min: Float, minExclusive: Boolean, max: Float, maxExclusive: Boolean): DimRange =
        DimRange.fromFloats(min, minExclusive, max, maxExclusive)

    private fun createFastMatchQuery(field: String, min: Long, max: Long): Query =
        LongRange.newIntersectsQuery(field, longArrayOf(min), longArrayOf(max))

    private fun addFastMatchField(field: String, doc: Document, facetSets: Array<LongFacetSet>, index: Int) {
        val min = facetSets.minOf { facetSet -> facetSet.values[index] }
        val max = facetSets.maxOf { facetSet -> facetSet.values[index] }
        doc.add(LongPoint(field, min, max))
    }
}
