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
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.TopOrdAndFloatQueue
import org.gnit.lucenekmp.facet.TopOrdAndNumberQueue
import org.gnit.lucenekmp.internal.hppc.IntFloatHashMap
import org.gnit.lucenekmp.jdkport.assert

/** Base class for all taxonomy-based facets that aggregate to float. */
abstract class FloatTaxonomyFacets : TaxonomyFacets {
    /** Aggregation function used for combining values. */
    protected val aggregationFunction: AssociationAggregationFunction
    /** Dense ordinal values. */
    internal var values: FloatArray? = null
    /** Sparse ordinal values. */
    internal var sparseValues: IntFloatHashMap? = null

    /** Sole constructor. */
    internal constructor(indexFieldName: String, taxoReader: TaxonomyReader, aggregationFunction: AssociationAggregationFunction, config: FacetsConfig, fc: FacetsCollector?) :
        super(indexFieldName, taxoReader, config, fc) {
        this.aggregationFunction = aggregationFunction
        valueComparator = Comparator { o1, o2 -> o1.toFloat().compareTo(o2.toFloat()) }
    }

    override fun initializeValueCounters() {
        if (initialized) return
        super.initializeValueCounters()
        assert(sparseValues == null && values == null)
        if (sparseCounts != null) sparseValues = IntFloatHashMap()
        else values = FloatArray(taxoReader.getSize())
    }

    /** Set the value associated with this ordinal to `newValue`. */
    internal fun setValue(ordinal: Int, newValue: Float) {
        if (sparseValues != null) sparseValues!!.put(ordinal, newValue)
        else values!![ordinal] = newValue
    }

    /** Get the value associated with this ordinal. */
    internal fun getValue(ordinal: Int): Float {
        if (sparseValues != null) return sparseValues!!.get(ordinal)
        else return values!![ordinal]
    }

    override fun getAggregationValue(ordinal: Int): Number = getValue(ordinal)
    override fun aggregate(existingVal: Number, newVal: Number): Number =
        aggregationFunction.aggregate(existingVal.toFloat(), newVal.toFloat())

    override fun updateValueFromRollup(ordinal: Int, childOrdinal: Int) {
        super.updateValueFromRollup(ordinal, childOrdinal)
        val currentValue = getValue(ordinal)
        val newValue = aggregationFunction.aggregate(currentValue, rollup(childOrdinal))
        setValue(ordinal, newValue)
    }

    override fun makeTopOrdAndNumberQueue(topN: Int): TopOrdAndNumberQueue = TopOrdAndFloatQueue(minOf(taxoReader.getSize(), topN))
    override fun missingAggregationValue(): Number = -1f

    private fun rollup(ord: Int): Float {
        var ord = ord
        val children = getChildren()
        val siblings = getSiblings()
        var aggregatedValue = 0f
        while (ord != TaxonomyReader.INVALID_ORDINAL) {
            updateValueFromRollup(ord, children.get(ord))
            aggregatedValue = aggregationFunction.aggregate(aggregatedValue, getValue(ord))
            ord = siblings.get(ord)
        }
        return aggregatedValue
    }

    override fun setIncomingValue(incomingOrdAndValue: TopOrdAndNumberQueue.OrdAndValue, ord: Int) {
        (incomingOrdAndValue as TopOrdAndFloatQueue.OrdAndFloat).value = getValue(ord)
    }

    protected inner class FloatAggregatedValue(private var value: Float) : AggregatedValue() {
        override fun aggregate(ord: Int) {
            value = aggregationFunction.aggregate(value, getValue(ord))
        }
        override fun get(): Number = value
    }

    override fun newAggregatedValue(): AggregatedValue = FloatAggregatedValue(0f)
}
