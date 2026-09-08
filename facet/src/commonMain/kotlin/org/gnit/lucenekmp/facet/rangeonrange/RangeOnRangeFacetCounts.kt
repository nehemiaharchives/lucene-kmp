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

import org.gnit.lucenekmp.document.BinaryRangeDocValues
import org.gnit.lucenekmp.document.RangeFieldQuery
import org.gnit.lucenekmp.facet.FacetCountsWithFilterQuery
import org.gnit.lucenekmp.facet.FacetResult
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.LabelAndValue
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.jdkport.assert

abstract class RangeOnRangeFacetCounts protected constructor(
/** Our field name. */
    private val field: String,
    hits: FacetsCollector,
    queryType: RangeFieldQuery.QueryType,
    fastMatchQuery: Query?,
    numEncodedValueBytes: Int,
    encodedRanges: Array<ByteArray>,
    private val labels: Array<String>
) : FacetCountsWithFilterQuery(fastMatchQuery) {
    /** Counts, initialized in by subclass. */
    private val counts = IntArray(encodedRanges.size)
    /** Total number of hits. */
    private var totCount = 0

    init {
        assert(encodedRanges.size == labels.size)
        assert(encodedRanges[0].size % (2 * numEncodedValueBytes) == 0)
        count(field, hits.getMatchingDocs(), encodedRanges, numEncodedValueBytes, queryType)
    }

    /** Counts from the provided field. */
    protected fun count(field: String, matchingDocs: List<FacetsCollector.MatchingDocs>, encodedRanges: Array<ByteArray>, numEncodedValueBytes: Int, queryType: RangeFieldQuery.QueryType) {
        // TODO: We currently just exhaustively check the ranges in each document with every range in
        // the ranges array.
        // We might be able to do something more efficient here by grouping the ranges array into a
        // space partitioning
        // data structure of some sort.
        val dims = encodedRanges[0].size / (2 * numEncodedValueBytes)
        val comparator = ArrayUtil.getUnsignedComparator(numEncodedValueBytes)
        var missingCount = 0
        for (hits in matchingDocs) {
            val binaryRangeDocValues = BinaryRangeDocValues(DocValues.getBinary(hits.context.reader(), field), dims, numEncodedValueBytes)
            val it = createIterator(hits)
            if (it == null) continue
            totCount += hits.totalHits
            var doc = it.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                if (binaryRangeDocValues.advanceExact(doc)) {
                    var hasValidRange = false
                    for (range in encodedRanges.indices) {
                        val encodedRange = encodedRanges[range]
                        val packedRange = binaryRangeDocValues.getPackedValue()
                        assert(encodedRange.size == packedRange.size)
                        if (queryType.matches(encodedRange, packedRange, dims, numEncodedValueBytes, comparator)) {
                            counts[range]++
                            hasValidRange = true
                        }
                    }
                    if (hasValidRange == false) missingCount++
                } else missingCount++
                doc = it.nextDoc()
            }
        }
        totCount -= missingCount
    }

    /**
   * {@inheritDoc}
   *
   * NOTE: This implementation guarantees that ranges will be returned in the order specified by
   * the user when calling the constructor.
   */
    override fun getAllChildren(dim: String, vararg path: String): FacetResult {
        validateDimAndPathForGetChildren(dim, *path)
        val labelValues = Array(counts.size) { i -> LabelAndValue(labels[i], counts[i]) }
        return FacetResult(dim, arrayOf(*path), totCount, labelValues, labelValues.size)
    }

    override fun getTopChildren(topN: Int, dim: String, vararg path: String): FacetResult {
        validateTopN(topN)
        validateDimAndPathForGetChildren(dim, *path)
        val pq = object : PriorityQueue<Entry>(minOf(topN, counts.size)) {
            override fun lessThan(a: Entry, b: Entry): Boolean {
                var cmp = a.count.compareTo(b.count)
                if (cmp == 0) cmp = b.label.compareTo(a.label)
                return cmp < 0
            }
        }
        var childCount = 0
        var e: Entry? = null
        for (i in counts.indices) {
            if (counts[i] != 0) {
                childCount++
                if (e == null) e = Entry()
                e.label = labels[i]
                e.count = counts[i]
                e = pq.insertWithOverflow(e)
            }
        }
        val results = arrayOfNulls<LabelAndValue>(pq.size())
        while (pq.size() != 0) {
            val entry = pq.pop()!!
            results[pq.size()] = LabelAndValue(entry.label, entry.count)
        }
        @Suppress("UNCHECKED_CAST")
        return FacetResult(dim, arrayOf(*path), totCount, results as Array<LabelAndValue>, childCount)
    }

    override fun getSpecificValue(dim: String, vararg path: String): Number = throw UnsupportedOperationException()
    override fun getAllDims(topN: Int): List<FacetResult> {
        validateTopN(topN)
        return listOf(getTopChildren(topN, field))
    }
    private fun validateDimAndPathForGetChildren(dim: String, vararg path: String) {
        if (dim != field) throw IllegalArgumentException("invalid dim \"$dim\"; should be \"$field\"")
        if (path.isNotEmpty()) throw IllegalArgumentException("path.length should be 0")
    }
    private class Entry {
        var count = 0
        lateinit var label: String
    }
}
