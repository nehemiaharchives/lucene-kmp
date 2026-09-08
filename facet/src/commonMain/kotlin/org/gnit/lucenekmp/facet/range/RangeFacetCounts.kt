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

import org.gnit.lucenekmp.facet.*
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.jdkport.assert

/**
 * Base class for range faceting.
 *
 * @lucene.experimental
 */
abstract class RangeFacetCounts /** Create `RangeFacetCounts` */ protected constructor(
    /** Our field name. */
    protected val field: String,
    /** Ranges passed to constructor. */
    protected val ranges: Array<out Range>,
    fastMatchQuery: Query?
) : FacetCountsWithFilterQuery(fastMatchQuery) {
    /** Counts. */
    protected var counts: IntArray? = null
    /** Total number of hits. */
    protected var totCount = 0

    protected abstract fun getLongRanges(): Array<out LongRange>
    protected open fun mapDocValue(l: Long): Long = l
    internal fun setupCounter(): LongRangeCounter {
        assert(counts == null)
        counts = IntArray(ranges.size)
        return LongRangeCounter.create(getLongRanges(), counts!!)
    }

    /** Counts from the provided field. */
    protected fun count(field: String, matchingDocs: List<FacetsCollector.MatchingDocs>) {
        // load doc values for all segments up front and keep track of whether-or-not we found any that
        // were actually multi-valued. this allows us to optimize the case where all segments contain
        // single-values.
        var multiValuedDocVals: Array<SortedNumericDocValues?>? = null
        var singleValuedDocVals: Array<NumericDocValues?>? = null
        var foundMultiValued = false
        for (i in matchingDocs.indices) {
            val hits = matchingDocs[i]
            if (hits.totalHits == 0) continue
            val multiValues = DocValues.getSortedNumeric(hits.context.reader(), field)
            if (multiValuedDocVals == null) multiValuedDocVals = arrayOfNulls(matchingDocs.size)
            multiValuedDocVals[i] = multiValues
            // only bother trying to unwrap a singleton if we haven't yet seen any true multi-valued cases
            if (foundMultiValued == false) {
                val singleValues = DocValues.unwrapSingleton(multiValues)
                if (singleValues != null) {
                    if (singleValuedDocVals == null) singleValuedDocVals = arrayOfNulls(matchingDocs.size)
                    singleValuedDocVals[i] = singleValues
                } else foundMultiValued = true
            }
        }
        if (multiValuedDocVals == null) {
            // no hits or no doc values in all segments. nothing to count:
            return
        }
        // we only need to keep around one or the other at this point
        if (foundMultiValued) singleValuedDocVals = null else multiValuedDocVals = null
        val counter = setupCounter()
        var missingCount = 0
        // if we didn't find any multi-valued cases, we can run a more optimal counting algorithm
        if (foundMultiValued == false) {
            for (i in matchingDocs.indices) {
                val hits = matchingDocs[i]
                val it = createIterator(hits)
                if (it == null) continue
                assert(singleValuedDocVals != null)
                val singleValues = singleValuedDocVals!![i]
                totCount += hits.totalHits
                var doc = it.nextDoc()
                while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                    if (singleValues!!.advanceExact(doc)) counter.addSingleValued(mapDocValue(singleValues.longValue()))
                    else missingCount++
                    doc = it.nextDoc()
                }
            }
        } else {
            for (i in matchingDocs.indices) {
                val it = createIterator(matchingDocs[i])
                if (it == null) continue
                val multiValues = multiValuedDocVals!![i]
                var doc = it.nextDoc()
                while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                    if (multiValues!!.advanceExact(doc)) {
                        val limit = multiValues.docValueCount()
                        // optimize single-value case
                        if (limit == 1) {
                            counter.addSingleValued(mapDocValue(multiValues.nextValue()))
                            totCount++
                        } else {
                            counter.startMultiValuedDoc()
                            var previous = 0L
                            for (j in 0 until limit) {
                                val `val` = mapDocValue(multiValues.nextValue())
                                if (j == 0 || `val` != previous) {
                                    counter.addMultiValued(`val`)
                                    previous = `val`
                                }
                            }
                            if (counter.endMultiValuedDoc()) totCount++
                        }
                    }
                    doc = it.nextDoc()
                }
            }
        }
        missingCount += counter.finish()
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
        val labelValues = Array(ranges.size) { i -> LabelAndValue(ranges[i].label, counts?.get(i) ?: 0) }
        return FacetResult(dim, arrayOf(*path), totCount, labelValues, labelValues.size)
    }

    override fun getTopChildren(topN: Int, dim: String, vararg path: String): FacetResult {
        validateTopN(topN)
        validateDimAndPathForGetChildren(dim, *path)
        if (counts == null) {
            assert(totCount == 0)
            return FacetResult(dim, arrayOf(*path), totCount, emptyArray(), 0)
        }
        val pq = object : PriorityQueue<Entry>(minOf(topN, counts!!.size)) {
            override fun lessThan(a: Entry, b: Entry): Boolean {
                var cmp = a.count.compareTo(b.count)
                if (cmp == 0) cmp = b.label.compareTo(a.label)
                return cmp < 0
            }
        }
        var childCount = 0
        var e: Entry? = null
        for (i in counts!!.indices) {
            if (counts!![i] != 0) {
                childCount++
                if (e == null) e = Entry()
                e.label = ranges[i].label
                e.count = counts!![i]
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

    override fun getSpecificValue(dim: String, vararg path: String): Number {
        // TODO: should we impl this?
        throw UnsupportedOperationException()
    }
    override fun getAllDims(topN: Int): List<FacetResult> {
        validateTopN(topN)
        return listOf(getTopChildren(topN, field))
    }
    override fun toString(): String {
        val b = StringBuilder()
        b.append("RangeFacetCounts totCount=")
        b.append(totCount)
        b.append(":\n")
        for (i in ranges.indices) {
            b.append("  ")
            b.append(ranges[i].label)
            b.append(" -> count=")
            b.append(counts?.get(i) ?: 0)
            b.append('\n')
        }
        return b.toString()
    }
    private fun validateDimAndPathForGetChildren(dim: String, vararg path: String) {
        if (dim != field) throw IllegalArgumentException("invalid dim \"$dim\"; should be \"$field\"")
        if (path.isNotEmpty()) throw IllegalArgumentException("path.length should be 0")
    }
    /** Reusable entry to hold range label and int count. */
    private class Entry {
        var count = 0
        lateinit var label: String
    }
}
