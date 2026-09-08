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
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.facet.*
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermInSetQuery
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

class TestExactFacetSetMatcher : FacetTestCase() {
    companion object {
        private const val FORD_ORD = 100
        private const val TOYOTA_ORD = 101
        private const val CHEVY_ORD = 102
        private const val NISSAN_ORD = 103
        private val MANUFACTURER_ORDS = intArrayOf(FORD_ORD, TOYOTA_ORD, CHEVY_ORD, NISSAN_ORD)
        private val YEARS = intArrayOf(2010, 2011, 2012)
    }

    @Test
    fun testSimpleFacetSetMatching() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)

        // Ford-2010, Chevy-2011
        var doc = Document()
        doc.add(FacetSetsField.create("field", LongFacetSet(FORD_ORD.toLong(), 2010), LongFacetSet(CHEVY_ORD.toLong(), 2011)))
        w.addDocument(doc)

        // Ford-2011, Chevy-2010
        doc = Document()
        doc.add(FacetSetsField.create("field", LongFacetSet(FORD_ORD.toLong(), 2011), LongFacetSet(CHEVY_ORD.toLong(), 2010)))
        w.addDocument(doc)

        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        val facets: Facets = MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeLongs),
            ExactFacetSetMatcher("Ford 2010", LongFacetSet(FORD_ORD.toLong(), 2010)),
            ExactFacetSetMatcher("Chevy 2011", LongFacetSet(CHEVY_ORD.toLong(), 2011)))
        val result = facets.getAllChildren("field")!!
        assertEquals("field", result.dim)
        assertEquals(0, result.path.size)
        assertEquals(1, result.value)
        assertEquals(2, result.childCount)
        assertEquals(LabelAndValue("Ford 2010", 1), result.labelValues[0])
        assertEquals(LabelAndValue("Chevy 2011", 1), result.labelValues[1])
        r.close()
        d.close()
    }

    @Test
    fun testLongFacetSetMatching() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val allSets = mutableListOf<LongFacetSet>()
        for (manufacturerOrd in MANUFACTURER_ORDS) {
            for (year in YEARS) {
                allSets.add(LongFacetSet(manufacturerOrd.toLong(), year.toLong()))
            }
        }
        var numFord2010 = 0
        var numChevy2011 = 0
        var numMatchingDocs = 0
        for (i in 0 until 100) {
            val doc = Document()
            val numSets = TestUtil.nextInt(random(), 1, 4)
            allSets.shuffle(random())
            val facetSets = allSets.subList(0, numSets).toTypedArray()
            var matchingDoc = false
            for (facetSet in facetSets) {
                if (FORD_ORD.toLong() == facetSet.values[0] && facetSet.values[1] == 2010L) {
                    ++numFord2010
                    matchingDoc = true
                } else if (CHEVY_ORD.toLong() == facetSet.values[0] && facetSet.values[1] == 2011L) {
                    ++numChevy2011
                    matchingDoc = true
                }
            }
            numMatchingDocs += if (matchingDoc) 1 else 0
            doc.add(FacetSetsField.create("field", *facetSets))
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        val facets: Facets = MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeLongs),
            ExactFacetSetMatcher("Ford 2010", LongFacetSet(FORD_ORD.toLong(), 2010L)),
            ExactFacetSetMatcher("Chevy 2011", LongFacetSet(CHEVY_ORD.toLong(), 2011L)))
        val result = facets.getAllChildren("field")!!
        assertEquals("field", result.dim)
        assertEquals(0, result.path.size)
        assertEquals(numMatchingDocs, result.value)
        assertEquals(2, result.childCount)
        assertEquals(LabelAndValue("Ford 2010", numFord2010), result.labelValues[0])
        assertEquals(LabelAndValue("Chevy 2011", numChevy2011), result.labelValues[1])
        r.close()
        d.close()
    }

    @Test
    fun testIntFacetSetMatching() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val allSets = mutableListOf<IntFacetSet>()
        for (manufacturerOrd in MANUFACTURER_ORDS) {
            for (year in YEARS) {
                allSets.add(IntFacetSet(manufacturerOrd, year))
            }
        }
        var numFord2010 = 0
        var numChevy2011 = 0
        var numMatchingDocs = 0
        for (i in 0 until 100) {
            val doc = Document()
            val numSets = TestUtil.nextInt(random(), 1, 4)
            allSets.shuffle(random())
            val facetSets = allSets.subList(0, numSets).toTypedArray()
            var matchingDoc = false
            for (facetSet in facetSets) {
                if (FORD_ORD == facetSet.values[0] && facetSet.values[1] == 2010) {
                    ++numFord2010
                    matchingDoc = true
                } else if (CHEVY_ORD == facetSet.values[0] && facetSet.values[1] == 2011) {
                    ++numChevy2011
                    matchingDoc = true
                }
            }
            numMatchingDocs += if (matchingDoc) 1 else 0
            doc.add(FacetSetsField.create("field", *facetSets))
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        val facets: Facets = MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeInts),
            ExactFacetSetMatcher("Ford 2010", IntFacetSet(FORD_ORD, 2010)),
            ExactFacetSetMatcher("Chevy 2011", IntFacetSet(CHEVY_ORD, 2011)))
        val result = facets.getAllChildren("field")!!
        assertEquals("field", result.dim)
        assertEquals(0, result.path.size)
        assertEquals(numMatchingDocs, result.value)
        assertEquals(2, result.childCount)
        assertEquals(LabelAndValue("Ford 2010", numFord2010), result.labelValues[0])
        assertEquals(LabelAndValue("Chevy 2011", numChevy2011), result.labelValues[1])
        r.close()
        d.close()
    }

    @Test
    fun testDoubleFacetSetMatching() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val allSets = mutableListOf<DoubleFacetSet>()
        for (manufacturerOrd in MANUFACTURER_ORDS) {
            for (year in YEARS) {
                allSets.add(DoubleFacetSet(manufacturerOrd.toDouble(), year + 0.5))
            }
        }
        var numFord2010 = 0
        var numChevy2011 = 0
        var numMatchingDocs = 0
        for (i in 0 until 100) {
            val doc = Document()
            val numSets = TestUtil.nextInt(random(), 1, 4)
            allSets.shuffle(random())
            val facetSets = allSets.subList(0, numSets).toTypedArray()
            var matchingDoc = false
            for (facetSet in facetSets) {
                if (FORD_ORD.toDouble() == facetSet.values[0] && facetSet.values[1] == 2010.5) {
                    ++numFord2010
                    matchingDoc = true
                } else if (CHEVY_ORD.toDouble() == facetSet.values[0] && facetSet.values[1] == 2011.5) {
                    ++numChevy2011
                    matchingDoc = true
                }
            }
            numMatchingDocs += if (matchingDoc) 1 else 0
            doc.add(FacetSetsField.create("field", *facetSets))
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        val facets: Facets = MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeLongs),
            ExactFacetSetMatcher("Ford 2010", DoubleFacetSet(FORD_ORD.toDouble(), 2010.5)),
            ExactFacetSetMatcher("Chevy 2011", DoubleFacetSet(CHEVY_ORD.toDouble(), 2011.5)))
        val result = facets.getAllChildren("field")!!
        assertEquals("field", result.dim)
        assertEquals(0, result.path.size)
        assertEquals(numMatchingDocs, result.value)
        assertEquals(2, result.childCount)
        assertEquals(LabelAndValue("Ford 2010", numFord2010), result.labelValues[0])
        assertEquals(LabelAndValue("Chevy 2011", numChevy2011), result.labelValues[1])
        r.close()
        d.close()
    }

    @Test
    fun testFloatFacetSetMatching() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val allSets = mutableListOf<FloatFacetSet>()
        for (manufacturerOrd in MANUFACTURER_ORDS) {
            for (year in YEARS) {
                allSets.add(FloatFacetSet(manufacturerOrd.toFloat(), year + 0.5f))
            }
        }
        var numFord2010 = 0
        var numChevy2011 = 0
        var numMatchingDocs = 0
        for (i in 0 until 100) {
            val doc = Document()
            val numSets = TestUtil.nextInt(random(), 1, 4)
            allSets.shuffle(random())
            val facetSets = allSets.subList(0, numSets).toTypedArray()
            var matchingDoc = false
            for (facetSet in facetSets) {
                if (FORD_ORD.toFloat() == facetSet.values[0] && facetSet.values[1] == 2010.5f) {
                    ++numFord2010
                    matchingDoc = true
                } else if (CHEVY_ORD.toFloat() == facetSet.values[0] && facetSet.values[1] == 2011.5f) {
                    ++numChevy2011
                    matchingDoc = true
                }
            }
            numMatchingDocs += if (matchingDoc) 1 else 0
            doc.add(FacetSetsField.create("field", *facetSets))
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        val facets: Facets = MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeInts),
            ExactFacetSetMatcher("Ford 2010", FloatFacetSet(FORD_ORD.toFloat(), 2010.5f)),
            ExactFacetSetMatcher("Chevy 2011", FloatFacetSet(CHEVY_ORD.toFloat(), 2011.5f)))
        val result = facets.getAllChildren("field")!!
        assertEquals("field", result.dim)
        assertEquals(0, result.path.size)
        assertEquals(numMatchingDocs, result.value)
        assertEquals(2, result.childCount)
        assertEquals(LabelAndValue("Ford 2010", numFord2010), result.labelValues[0])
        assertEquals(LabelAndValue("Chevy 2011", numChevy2011), result.labelValues[1])
        r.close()
        d.close()
    }

    @Test
    fun testLongFacetSetMatchingWithFastMatchQuery() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val allSets = mutableListOf<LongFacetSet>()
        for (manufacturerOrd in MANUFACTURER_ORDS) {
            for (year in YEARS) {
                allSets.add(LongFacetSet(manufacturerOrd.toLong(), year.toLong()))
            }
        }
        var numFord2010 = 0
        var numChevy2011 = 0
        var numMatchingDocs = 0
        for (i in 0 until 100) {
            val doc = Document()
            val numSets = TestUtil.nextInt(random(), 1, 4)
            allSets.shuffle(random())
            val facetSets = allSets.subList(0, numSets).toTypedArray()
            var matchingDoc = false
            for (facetSet in facetSets) {
                if (FORD_ORD.toLong() == facetSet.values[0] && facetSet.values[1] == 2010L) {
                    ++numFord2010
                    matchingDoc = true
                } else if (CHEVY_ORD.toLong() == facetSet.values[0] && facetSet.values[1] == 2011L) {
                    ++numChevy2011
                    matchingDoc = true
                }
            }
            numMatchingDocs += if (matchingDoc) 1 else 0
            doc.add(FacetSetsField.create("field", *facetSets)) // field for aggregation
            // add fields for drill-down + fast matching
            addFastMatchField("manufacturer", doc, facetSets, 0)
            addFastMatchField("year", doc, facetSets, 1)
            w.addDocument(doc)
        }
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        val fastMatchQuery: Query = BooleanQuery.Builder()
            .add(createFastMatchQuery("manufacturer", FORD_ORD, CHEVY_ORD), BooleanClause.Occur.MUST)
            .add(createFastMatchQuery("year", 2010, 2011), BooleanClause.Occur.MUST)
            .build()
        val facets: Facets = MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeLongs),
            fastMatchQuery, ExactFacetSetMatcher("Ford 2010", LongFacetSet(FORD_ORD.toLong(), 2010L)),
            ExactFacetSetMatcher("Chevy 2011", LongFacetSet(CHEVY_ORD.toLong(), 2011L)))
        val result = facets.getAllChildren("field")!!
        assertEquals("field", result.dim)
        assertEquals(0, result.path.size)
        assertEquals(numMatchingDocs, result.value)
        assertEquals(2, result.childCount)
        assertEquals(LabelAndValue("Ford 2010", numFord2010), result.labelValues[0])
        assertEquals(LabelAndValue("Chevy 2011", numChevy2011), result.labelValues[1])
        r.close()
        d.close()
    }

    private fun createFastMatchQuery(field: String, vararg values: Int): Query =
        TermInSetQuery(field, values.map { BytesRef(it.toString()) }.toMutableList())

    private fun addFastMatchField(field: String, doc: Document, facetSets: Array<LongFacetSet>, index: Int) {
        facetSets.map { facetSet -> facetSet.values[index] }.distinct()
            .forEach { value -> doc.add(StringField(field, value.toString(), Field.Store.NO)) }
    }
}
