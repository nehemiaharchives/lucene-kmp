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

import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.facet.FacetCountsWithFilterQuery
import org.gnit.lucenekmp.facet.FacetResult
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.LabelAndValue
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.jdkport.assert

/**
 * Returns the counts for each given [FacetSet]
 *
 * @lucene.experimental
 */
open class MatchingFacetSetsCounts : FacetCountsWithFilterQuery {
    private val facetSetMatchers: Array<out FacetSetMatcher>
    private val counts: IntArray
    private val field: String
    private val facetSetDecoder: FacetSetDecoder
    private val totCount: Int

    /**
   * Constructs a new instance of matching facet set counts which calculates the counts for each
   * given facet set matcher.
   */
    constructor(field: String, hits: FacetsCollector, facetSetDecoder: FacetSetDecoder, vararg facetSetMatchers: FacetSetMatcher) :
        this(field, hits, facetSetDecoder, null, *facetSetMatchers)

    /**
   * Constructs a new instance of matching facet set counts which calculates the counts for each
   * given facet set matcher. If `fastMatchQuery` is not `null`, then only documents
   * which are matched by it will be counted.
   */
    constructor(field: String, hits: FacetsCollector, facetSetDecoder: FacetSetDecoder, fastMatchQuery: Query?, vararg facetSetMatchers: FacetSetMatcher) : super(fastMatchQuery) {
        if (facetSetMatchers.isEmpty()) {
            throw IllegalArgumentException("facetSetMatchers cannot be null or empty")
        }
        if (areFacetSetMatcherDimensionsInconsistent(facetSetMatchers)) {
            throw IllegalArgumentException("All facet set matchers must be the same dimensionality")
        }
        this.field = field
        this.facetSetDecoder = facetSetDecoder
        this.facetSetMatchers = facetSetMatchers
        this.counts = IntArray(facetSetMatchers.size)
        this.totCount = count(field, hits.getMatchingDocs())
    }

    /** Counts from the provided field. */
    private fun count(field: String, matchingDocs: List<FacetsCollector.MatchingDocs>): Int {
        var totCount = 0
        for (hits in matchingDocs) {
            val binaryDocValues = DocValues.getBinary(hits.context.reader(), field)
            val it = createIterator(hits, binaryDocValues)
            if (it == null) {
                continue
            }
            var dimValues: LongArray? = null // dimension values buffer
            var expectedNumDims = -1
            var doc = it.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                var shouldCountDoc = false
                val bytesRef = binaryDocValues.binaryValue()!!
                val packedValue = bytesRef.bytes
                val numDims = IntPoint.decodeDimension(packedValue, 0)
                if (expectedNumDims == -1) {
                    expectedNumDims = numDims
                    dimValues = LongArray(numDims)
                } else {
                    // Verify that the number of indexed dimensions for all matching documents is the same
                    // (since we cannot verify that at indexing time).
                    assert(numDims == expectedNumDims) {
                        "Expected ($expectedNumDims) dimensions, found ($numDims) for doc ($doc)"
                    }
                }
                var start = Int.SIZE_BYTES
                while (start < bytesRef.length) {
                    start += facetSetDecoder.decode(bytesRef, start, dimValues!!)
                    for (j in facetSetMatchers.indices) { // for each facet set matcher
                        if (facetSetMatchers[j].matches(dimValues)) {
                            counts[j]++
                            shouldCountDoc = true
                        }
                    }
                }
                if (shouldCountDoc) {
                    totCount++
                }
                doc = it.nextDoc()
            }
        }
        return totCount
    }

    override fun getAllChildren(dim: String, vararg path: String): FacetResult {
        if (field != dim) {
            throw IllegalArgumentException("invalid dim \"$dim\"; should be \"$field\"")
        }
        if (path.isNotEmpty()) {
            throw IllegalArgumentException("path.length should be 0")
        }
        val labelValues = Array(counts.size) { i -> LabelAndValue(facetSetMatchers[i].label, counts[i]) }
        return FacetResult(dim, arrayOf(*path), totCount, labelValues, labelValues.size)
    }

    override fun getTopChildren(topN: Int, dim: String, vararg path: String): FacetResult {
        validateTopN(topN)
        val topN = minOf(topN, counts.size)
        val pq = object : PriorityQueue<Entry>(topN, { Entry("", 0) }) {
            override fun lessThan(a: Entry, b: Entry): Boolean = compare(a.count, b.count, a.label, b.label) < 0
        }
        var childCount = 0
        var reuse = pq.top()
        for (i in counts.indices) {
            val count = counts[i]
            if (count > 0) {
                childCount++
                val label = facetSetMatchers[i].label
                if (compare(reuse.count, count, reuse.label, label) < 0) {
                    reuse.label = label
                    reuse.count = count
                    reuse = pq.updateTop()
                }
            }
        }
        // Pop off any sentinel values in the case that we had fewer child labels with non-zero
        // counts than the requested top-n:
        while (childCount < pq.size()) {
            pq.pop()
        }
        val labelValues = arrayOfNulls<LabelAndValue>(minOf(topN, childCount))
        for (i in pq.size() - 1 downTo 0) {
            val e = pq.pop()!!
            labelValues[i] = LabelAndValue(e.label, e.count)
        }
        @Suppress("UNCHECKED_CAST")
        return FacetResult(dim, arrayOf(*path), totCount, labelValues as Array<LabelAndValue>, childCount)
    }

    override fun getSpecificValue(dim: String, vararg path: String): Number {
        throw UnsupportedOperationException()
    }

    override fun getAllDims(topN: Int): List<FacetResult> {
        validateTopN(topN)
        return listOf(getTopChildren(topN, field))
    }

    companion object {
        private fun areFacetSetMatcherDimensionsInconsistent(facetSetMatchers: Array<out FacetSetMatcher>): Boolean {
            val dims = facetSetMatchers[0].dims
            return facetSetMatchers.any { facetSetMatcher -> facetSetMatcher.dims != dims }
        }

        private fun compare(count1: Int, count2: Int, label1: String, label2: String): Int {
            var cmp = count1.compareTo(count2)
            if (cmp == 0) {
                cmp = label2.compareTo(label1)
            }
            return cmp
        }
    }

    private class Entry(var label: String, var count: Int)
}
