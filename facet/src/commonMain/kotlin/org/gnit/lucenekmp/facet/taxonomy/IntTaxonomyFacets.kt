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
import org.gnit.lucenekmp.facet.TopOrdAndIntQueue
import org.gnit.lucenekmp.facet.TopOrdAndNumberQueue
import org.gnit.lucenekmp.internal.hppc.IntIntHashMap
import org.gnit.lucenekmp.jdkport.assert

/** Base class for all taxonomy-based facets that aggregate to int. */
abstract class IntTaxonomyFacets : TaxonomyFacets {
    /** Aggregation function used for combining values. */
    protected val aggregationFunction: AssociationAggregationFunction
    /** Dense ordinal values. */
    internal var values: IntArray? = null
    /** Sparse ordinal values. */
    internal var sparseValues: IntIntHashMap? = null

    /** Sole constructor. */
    internal constructor(indexFieldName: String, taxoReader: TaxonomyReader, config: FacetsConfig, aggregationFunction: AssociationAggregationFunction, fc: FacetsCollector?) :
        super(indexFieldName, taxoReader, config, fc) {
        this.aggregationFunction = aggregationFunction
        valueComparator = Comparator { o1, o2 -> o1.toInt().compareTo(o2.toInt()) }
    }

    override fun initializeValueCounters() {
        if (initialized) return
        super.initializeValueCounters()
        assert(sparseValues == null && values == null)
        if (sparseCounts != null) sparseValues = IntIntHashMap()
        else values = IntArray(taxoReader.getSize())
    }

    /** Set the value associated with this ordinal to `newValue`. */
    internal fun setValue(ordinal: Int, newValue: Int) {
        if (sparseValues != null) sparseValues!!.put(ordinal, newValue)
        else values!![ordinal] = newValue
    }

    /** Get the value associated with this ordinal. */
    internal fun getValue(ordinal: Int): Int {
        if (sparseValues != null) return sparseValues!!.get(ordinal)
        else return values!![ordinal]
    }

    override fun getAggregationValue(ordinal: Int): Number = getValue(ordinal)
    override fun aggregate(existingVal: Number, newVal: Number): Number =
        aggregationFunction.aggregate(existingVal.toInt(), newVal.toInt())

    override fun updateValueFromRollup(ordinal: Int, childOrdinal: Int) {
        super.updateValueFromRollup(ordinal, childOrdinal)
        val currentValue = getValue(ordinal)
        val newValue = aggregationFunction.aggregate(currentValue, rollup(childOrdinal))
        setValue(ordinal, newValue)
    }

    private fun rollup(ord: Int): Int {
        var ord = ord
        val children = getChildren()
        val siblings = getSiblings()
        var aggregatedValue = 0
        while (ord != TaxonomyReader.INVALID_ORDINAL) {
            updateValueFromRollup(ord, children.get(ord))
            aggregatedValue = aggregationFunction.aggregate(aggregatedValue, getValue(ord))
            ord = siblings.get(ord)
        }
        return aggregatedValue
    }

    override fun setIncomingValue(incomingOrdAndValue: TopOrdAndNumberQueue.OrdAndValue, ord: Int) {
        (incomingOrdAndValue as TopOrdAndIntQueue.OrdAndInt).value = getValue(ord)
    }

    protected inner class IntAggregatedValue(private var value: Int) : AggregatedValue() {
        override fun aggregate(ord: Int) {
            value = aggregationFunction.aggregate(value, getValue(ord))
        }
        override fun get(): Number = value
    }

    override fun newAggregatedValue(): AggregatedValue = IntAggregatedValue(0)
}
