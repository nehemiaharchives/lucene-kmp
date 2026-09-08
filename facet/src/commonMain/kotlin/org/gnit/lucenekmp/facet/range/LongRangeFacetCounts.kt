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

import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollector.MatchingDocs
import org.gnit.lucenekmp.facet.MultiLongValuesSource
import org.gnit.lucenekmp.search.LongValuesSource
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.NumericUtils

/**
 * [Facets] implementation that computes counts for dynamic long ranges. Use this for
 * dimensions that change in real-time (e.g. a relative time based dimension like "Past day", "Past
 * 2 days", etc.) or that change for each request (e.g. distance from the user's location, "< 1
 * km", "< 2 km", etc.).
 *
 * @lucene.experimental
 */
class LongRangeFacetCounts : RangeFacetCounts {
    /**
   * Create `LongRangeFacetCounts` using long values from the specified field. The field may
   * be single-valued ([NumericDocValues]) or multi-valued ([SortedNumericDocValues]),
   * and will be interpreted as containing long values.
   */
    constructor(field: String, hits: FacetsCollector, vararg ranges: LongRange) : this(field, null as LongValuesSource?, hits, *ranges)
    /**
   * Create `LongRangeFacetCounts`, using the provided [LongValuesSource] if non-null.
   * If `valueSource` is null, doc values from the provided `field` will be used.
   */
    constructor(field: String, valueSource: LongValuesSource?, hits: FacetsCollector, vararg ranges: LongRange) : this(field, valueSource, hits, null, *ranges)
    /**
   * Create `LongRangeFacetCounts`, using the provided [MultiLongValuesSource] if
   * non-null. If `valuesSource` is null, doc values from the provided `field` will be
   * used.
   */
    constructor(field: String, valuesSource: MultiLongValuesSource?, hits: FacetsCollector, vararg ranges: LongRange) : this(field, valuesSource, hits, null, *ranges)
    /**
   * Create `LongRangeFacetCounts`, using the provided [LongValuesSource] if non-null.
   * If `valueSource` is null, doc values from the provided `field` will be used. Use
   * the provided `Query` as a fastmatch: only documents passing the filter are checked for
   * the matching ranges, which is helpful when the provided [LongValuesSource] is costly
   * per-document, such as a geo distance.
   */
    constructor(field: String, valueSource: LongValuesSource?, hits: FacetsCollector, fastMatchQuery: Query?, vararg ranges: LongRange) : super(field, ranges, fastMatchQuery) {
        // use the provided valueSource if non-null, otherwise use the doc values associated with the
        // field
        if (valueSource != null) count(valueSource, hits.getMatchingDocs()) else count(field, hits.getMatchingDocs())
    }
    /**
   * Create `LongRangeFacetCounts`, using the provided [MultiLongValuesSource] if
   * non-null. If `valuesSource` is null, doc values from the provided `field` will be
   * used. Use the provided `Query` as a fastmatch: only documents passing the filter are
   * checked for the matching ranges, which is helpful when the provided [LongValuesSource] is
   * costly per-document, such as a geo distance.
   */
    constructor(field: String, valuesSource: MultiLongValuesSource?, hits: FacetsCollector, fastMatchQuery: Query?, vararg ranges: LongRange) : super(field, ranges, fastMatchQuery) {
        // use the provided valueSource if non-null, otherwise use the doc values associated with the
        // field
        if (valuesSource != null) {
            val singleValues = MultiLongValuesSource.unwrapSingleton(valuesSource)
            if (singleValues != null) count(singleValues, hits.getMatchingDocs()) else count(valuesSource, hits.getMatchingDocs())
        } else count(field, hits.getMatchingDocs())
    }

    /** Counts from the provided valueSource. */
    private fun count(valueSource: LongValuesSource, matchingDocs: List<MatchingDocs>) {
        var counter: LongRangeCounter? = null
        var missingCount = 0
        for (hits in matchingDocs) {
            if (hits.totalHits == 0) continue
            val it = createIterator(hits)
            if (it == null) continue
            if (counter == null) counter = setupCounter()
            val fv = valueSource.getValues(hits.context, null)
            totCount += hits.totalHits
            var doc = it.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                // Skip missing docs:
                if (fv.advanceExact(doc)) counter.addSingleValued(fv.longValue())
                else missingCount++
                doc = it.nextDoc()
            }
        }
        if (counter != null) {
            missingCount += counter.finish()
            totCount -= missingCount
        }
    }

    /** Counts from the provided valueSource. */
    private fun count(valueSource: MultiLongValuesSource, matchingDocs: List<MatchingDocs>) {
        var counter: LongRangeCounter? = null
        
        for (hits in matchingDocs) {
            if (hits.totalHits == 0) continue
            val it = createIterator(hits)
            if (it == null) continue
            if (counter == null) counter = setupCounter()
            val multiValues = valueSource.getValues(hits.context)
            var doc = it.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                // Skip missing docs:
                if (multiValues.advanceExact(doc)) {
                    val limit = multiValues.getValueCount()
                    // optimize single-valued case:
                    if (limit == 1L) {
                        counter.addSingleValued(multiValues.nextValue())
                        totCount++
                    } else {
                        counter.startMultiValuedDoc()
                        var previous = 0L
                        var i = 0
                        while (i < limit) {
                            val `val` = multiValues.nextValue()
                            if (i == 0 || `val` != previous) {
                                counter.addMultiValued(`val`)
                                previous = `val`
                            }
                            i++
                        }
                        if (counter.endMultiValuedDoc()) totCount++
                    }
                }
                doc = it.nextDoc()
            }
        }
        if (counter != null) {
            val missingCount = counter.finish()
            totCount -= missingCount
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getLongRanges(): Array<out LongRange> = ranges as Array<out LongRange>
}
