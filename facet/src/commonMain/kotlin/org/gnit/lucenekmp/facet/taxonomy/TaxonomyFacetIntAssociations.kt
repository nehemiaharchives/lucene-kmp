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

import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollector.MatchingDocs
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.BitUtil

/**
 * Aggregates int values previously indexed with [IntAssociationFacetField], assuming the
 * default encoding. The aggregation function is defined by a provided [AssociationAggregationFunction].
 *
 * @lucene.experimental
 */
class TaxonomyFacetIntAssociations : IntTaxonomyFacets {
    /** Create `TaxonomyFacetIntAssociations` against the default index field. */
    constructor(
        taxoReader: TaxonomyReader,
        config: FacetsConfig,
        fc: FacetsCollector,
        aggregationFunction: AssociationAggregationFunction
    ) : this(FacetsConfig.DEFAULT_INDEX_FIELD_NAME, taxoReader, config, fc, aggregationFunction)

    /** Create `TaxonomyFacetIntAssociations` against the specified index field. */
    constructor(
        indexFieldName: String,
        taxoReader: TaxonomyReader,
        config: FacetsConfig,
        fc: FacetsCollector,
        aggregationFunction: AssociationAggregationFunction
    ) : super(indexFieldName, taxoReader, config, aggregationFunction, fc) {
        aggregateValues(aggregationFunction, fc.getMatchingDocs())
    }

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
                    val value = BitUtil.VH_BE_INT.get(bytes, offset)
                    offset += 4
                    // TODO: Can we optimize the null check in setValue? See LUCENE-10373.
                    val currentValue = getValue(ord)
                    val newValue = aggregationFunction.aggregate(currentValue, value)
                    setValue(ord, newValue)
                    setCount(ord, getCount(ord) + 1)
                }
                doc = it.nextDoc()
            }
        }
    }
}
