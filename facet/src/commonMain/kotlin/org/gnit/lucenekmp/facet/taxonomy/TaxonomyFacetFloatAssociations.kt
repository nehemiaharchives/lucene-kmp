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
package org.gnit.lucenekmp.facet.taxonomy

import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollector.MatchingDocs
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DoubleValues
import org.gnit.lucenekmp.search.DoubleValuesSource
import org.gnit.lucenekmp.util.BitUtil

/**
 * Aggregates float values associated with facet fields. Supports two different approaches:
 *
 * <ol>
 *   <li>Fields can be indexed with [FloatAssociationFacetField], associating weights with
 *       facet values at indexing time.
 *   <li>Fields can be indexed with [FacetField] and a [DoubleValuesSource] can
 *       dynamically supply a weight from each doc. With this approach, the document's weight gets
 *       contributed to each facet value associated with the doc.
 * </ol>
 *
 * Aggregation logic is supplied by the provided [FloatAssociationFacetField].
 *
 * @lucene.experimental
 */
class TaxonomyFacetFloatAssociations : FloatTaxonomyFacets {
    /** Create `TaxonomyFacetFloatAssociations` against the default index field. */
    constructor(
        taxoReader: TaxonomyReader,
        config: FacetsConfig,
        fc: FacetsCollector,
        aggregationFunction: AssociationAggregationFunction
    ) : this(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, taxoReader, config, fc, aggregationFunction)

    /**
   * Create `TaxonomyFacetFloatAssociations` against the default index field. Sources values
   * from the provided `valuesSource`.
   */
    constructor(
        taxoReader: TaxonomyReader,
        config: FacetsConfig,
        fc: FacetsCollector,
        aggregationFunction: AssociationAggregationFunction,
        valuesSource: DoubleValuesSource
    ) : this(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, taxoReader, config, fc, aggregationFunction, valuesSource)

    /** Create `TaxonomyFacetFloatAssociations` against the specified index field. */
    constructor(
        indexFieldName: String,
        taxoReader: TaxonomyReader,
        config: FacetsConfig,
        fc: FacetsCollector,
        aggregationFunction: AssociationAggregationFunction
    ) : super(indexFieldName, taxoReader, aggregationFunction, config, fc) {
        aggregateValues(aggregationFunction, fc.getMatchingDocs())
    }

    /**
   * Create `TaxonomyFacetFloatAssociations` against the specified index field. Sources values
   * from the provided `valuesSource`.
   */
    constructor(
        indexFieldName: String,
        taxoReader: TaxonomyReader,
        config: FacetsConfig,
        fc: FacetsCollector,
        aggregationFunction: AssociationAggregationFunction,
        valuesSource: DoubleValuesSource
    ) : super(indexFieldName, taxoReader, aggregationFunction, config, fc) {
        aggregateValues(aggregationFunction, fc.getMatchingDocs(), fc.getKeepScores(), valuesSource)
    }

    /** Aggregate using the provided `DoubleValuesSource`. */
    private fun aggregateValues(
        aggregationFunction: AssociationAggregationFunction,
        matchingDocs: List<MatchingDocs>,
        keepScores: Boolean,
        valueSource: DoubleValuesSource
    ) {
        for (hits in matchingDocs) {
            if (hits.totalHits == 0) {
                continue
            }
            initializeValueCounters()

            val ordinalValues = DocValues.getSortedNumeric(hits.context.reader(), indexFieldName)
            val scores = if (keepScores) scores(hits) else null
            val functionValues = valueSource.getValues(hits.context, scores)
            val it = ConjunctionUtils.intersectIterators(mutableListOf(hits.bits.iterator()!!, ordinalValues))

            var doc = it.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                if (functionValues.advanceExact(doc)) {
                    val value = functionValues.doubleValue().toFloat()
                    val ordinalCount = ordinalValues.docValueCount()
                    for (i in 0 until ordinalCount) {
                        val ord = ordinalValues.nextValue().toInt()
                        val currentValue = getValue(ord)
                        val newValue = aggregationFunction.aggregate(currentValue, value)
                        setValue(ord, newValue)
                        setCount(ord, getCount(ord) + 1)
                    }
                }
                doc = it.nextDoc()
            }
        }

        // Hierarchical dimensions are supported when using a value source, so we need to rollup:
        rollup()
    }

    /** Aggregate from indexed association values. */
    private fun aggregateValues(
        aggregationFunction: AssociationAggregationFunction,
        matchingDocs: List<MatchingDocs>
    ) {
        for (hits in matchingDocs) {
            if (hits.totalHits == 0) {
                continue
            }
            initializeValueCounters()

            val dv = DocValues.getBinary(hits.context.reader(), indexFieldName)
            val it = ConjunctionUtils.intersectIterators(mutableListOf(hits.bits.iterator()!!, dv))

            var doc = it.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                val bytesRef = dv.binaryValue()!!
                val bytes = bytesRef.bytes
                val end = bytesRef.offset + bytesRef.length
                var offset = bytesRef.offset
                while (offset < end) {
                    val ord = BitUtil.VH_BE_INT.get(bytes, offset)
                    offset += 4
                    val value = Float.fromBits(BitUtil.VH_BE_INT.get(bytes, offset))
                    offset += 4
                    val currentValue = getValue(ord)
                    val newValue = aggregationFunction.aggregate(currentValue, value)
                    setValue(ord, newValue)
                    setCount(ord, getCount(ord) + 1)
                }
                doc = it.nextDoc()
            }
        }
    }

    companion object {
        private fun scores(hits: MatchingDocs): DoubleValues {
            return object : DoubleValues() {
                var index = -1

                override fun doubleValue(): Double {
                    return hits.scores!![index].toDouble()
                }

                override fun advanceExact(doc: Int): Boolean {
                    index = doc
                    return true
                }
            }
        }
    }
}
