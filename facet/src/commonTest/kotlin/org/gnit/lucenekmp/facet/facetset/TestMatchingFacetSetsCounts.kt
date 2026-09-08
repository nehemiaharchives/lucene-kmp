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
import org.gnit.lucenekmp.facet.*
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.InPlaceMergeSorter
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TestMatchingFacetSetsCounts : FacetTestCase() {
    companion object {
        private const val FORD_ORD = 100
        private const val TOYOTA_ORD = 101
        private const val CHEVY_ORD = 102
        private const val NISSAN_ORD = 103
        private val MANUFACTURER_ORDS = intArrayOf(FORD_ORD, TOYOTA_ORD, CHEVY_ORD, NISSAN_ORD)
    }

    @Test
    fun testInvalidTopN() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val doc = Document()
        doc.add(FacetSetsField.create("field", LongFacetSet(123, 456)))
        w.addDocument(doc)
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        val facets: Facets = MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeLongs),
            ExactFacetSetMatcher("Test", LongFacetSet(123, 456)))
        assertFailsWith<IllegalArgumentException> { facets.getTopChildren(0, "field") }
        r.close()
        d.close()
    }

    @Test
    fun testInconsistentNumOfIndexedDimensions() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        var doc = Document()
        doc.add(FacetSetsField.create("field", LongFacetSet(123, 456)))
        w.addDocument(doc)
        doc = Document()
        doc.add(FacetSetsField.create("field", LongFacetSet(123)))
        w.addDocument(doc)
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        assertFailsWith<AssertionError> {
            MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeLongs),
                ExactFacetSetMatcher("Test", LongFacetSet(1)))
        }
        r.close()
        d.close()
    }

    @Test
    fun testTopChildren() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        // As a test scenario, we're faceting on the number of vehicles produced per day/make
        // combination over the past 30 days:
        val numBins = 30
        val expectedCounts = IntArray(numBins * MANUFACTURER_ORDS.size)
        val facetSetMatchers = arrayOfNulls<FacetSetMatcher>(numBins * MANUFACTURER_ORDS.size)
        var totalDocs = 0
        var totalNonZeroBins = 0
        var index = 0
        for (i in 0 until numBins) {
            for (ord in MANUFACTURER_ORDS) {
                facetSetMatchers[index] = ExactFacetSetMatcher("$i:$ord", LongFacetSet(i.toLong(), ord.toLong()))
                val carsManufactured = TestUtil.nextInt(random(), 0, 100)
                for (k in 0 until carsManufactured) {
                    // Create a document for every vehicle produced:
                    val doc = Document()
                    doc.add(FacetSetsField.create("field", LongFacetSet(i.toLong(), ord.toLong())))
                    w.addDocument(doc)
                }
                if (carsManufactured > 0) {
                    totalNonZeroBins++
                }
                totalDocs += carsManufactured
                expectedCounts[index] = carsManufactured
                index++
            }
        }
        val r = w.reader
        w.close()
        val s = newSearcher(r)
        val fc = s.search(MatchAllDocsQuery(), FacetsCollectorManager())!!
        @Suppress("UNCHECKED_CAST")
        val facets: Facets = MatchingFacetSetsCounts("field", fc, FacetSetDecoder(FacetSetDecoder::decodeLongs), *(facetSetMatchers as Array<FacetSetMatcher>))
        // Sort by count (high-to-low) and tie-break on label, same as in
        // MatchingFacetCounts#getTopChildren:
        val originalIndexes = IntArray(expectedCounts.size)
        for (i in originalIndexes.indices) {
            originalIndexes[i] = i
        }
        object : InPlaceMergeSorter() {
            override fun compare(i: Int, j: Int): Int {
                var cmp = expectedCounts[j].compareTo(expectedCounts[i])
                if (cmp == 0) {
                    val dayBinI = originalIndexes[i] / MANUFACTURER_ORDS.size
                    val dayBinJ = originalIndexes[j] / MANUFACTURER_ORDS.size
                    val ordIndexI = originalIndexes[i] % MANUFACTURER_ORDS.size
                    val ordIndexJ = originalIndexes[j] % MANUFACTURER_ORDS.size
                    val labelI = "$dayBinI:${MANUFACTURER_ORDS[ordIndexI]}"
                    val labelJ = "$dayBinJ:${MANUFACTURER_ORDS[ordIndexJ]}"
                    cmp = BytesRef(labelI).compareTo(BytesRef(labelJ))
                }
                return cmp
            }

            override fun swap(i: Int, j: Int) {
                var tmp = expectedCounts[i]
                expectedCounts[i] = expectedCounts[j]
                expectedCounts[j] = tmp
                tmp = originalIndexes[i]
                originalIndexes[i] = originalIndexes[j]
                originalIndexes[j] = tmp
            }
        }.sort(0, expectedCounts.size)
        val topN = 10
        val expected = Array(topN) { i ->
            val count = expectedCounts[i]
            val dayBin = originalIndexes[i] / MANUFACTURER_ORDS.size
            val ordIndex = originalIndexes[i] % MANUFACTURER_ORDS.size
            LabelAndValue("$dayBin:${MANUFACTURER_ORDS[ordIndex]}", count)
        }
        val result = facets.getTopChildren(topN, "field")!!
        assertFacetResult(result, "field", emptyArray(), totalNonZeroBins, totalDocs, *expected)
        r.close()
        d.close()
    }
}
