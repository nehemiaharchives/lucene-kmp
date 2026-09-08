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

import org.gnit.lucenekmp.facet.*
import org.gnit.lucenekmp.facet.FacetsConfig.DimConfig
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.internal.hppc.IntIntHashMap
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.jdkport.assert

/** Base class for all taxonomy-based facets impls. */
abstract class TaxonomyFacets /** Sole constructor. */ internal constructor(
    /** Index field name provided to the constructor. */
    internal val indexFieldName: String,
    /** `TaxonomyReader` provided to the constructor. */
    internal val taxoReader: TaxonomyReader,
    /** `FacetsConfig` provided to the constructor. */
    internal val config: FacetsConfig,
    /** `FacetsCollector` provided to the constructor. */
    internal val fc: FacetsCollector?
) : Facets() {
    /** Intermediate result to store top children for a given path before resolving labels, etc. */
    data class TopChildrenForPath(val pathValue: Number, val childCount: Int, val childQueue: TopOrdAndNumberQueue)

    private class DimValue(var dim: String, var dimOrd: Int, var value: Number)

    /** Maps parent ordinal to its child, or -1 if the parent is childless. */
    private var children: ParallelTaxonomyArrays.IntArray? = null
    /** Maps an ordinal to its sibling, or -1 if there is no sibling. */
    private var siblings: ParallelTaxonomyArrays.IntArray? = null
    /** Maps an ordinal to its parent, or -1 if there is no parent (root node). */
    internal val parents = taxoReader.getParallelTaxonomyArrays().parents()
    /** Dense ordinal counts. */
    internal var counts: IntArray? = null
    /** Sparse ordinal counts. */
    internal var sparseCounts: IntIntHashMap? = null
    /** Have value counters been initialized. */
    internal var initialized = false
    protected var valueComparator = Comparator<Number> { x, y -> x.toInt().compareTo(y.toInt()) }

/** Return true if a sparse hash table should be used for counting, instead of a dense int[]. */
    private fun useHashTable(fc: FacetsCollector?, taxoReader: TaxonomyReader): Boolean {
        if (taxoReader.getSize() < 1024) {
            // small number of unique values: use an array
            return false
        }
        if (fc == null) {
            // counting all docs: use an array
            return false
        }
        var maxDoc = 0
        var sumTotalHits = 0
        for (docs in fc.getMatchingDocs()) {
            sumTotalHits += docs.totalHits
            maxDoc += docs.context.reader().maxDoc()
        }
        // if our result set is < 10% of the index, we collect sparsely (use hash map):
        return sumTotalHits < maxDoc / 10
    }

    protected open fun initializeValueCounters() {
        if (initialized) {
            return
        }
        initialized = true
        assert(sparseCounts == null && counts == null)
        if (useHashTable(fc, taxoReader)) {
            sparseCounts = IntIntHashMap()
        } else {
            counts = IntArray(taxoReader.getSize())
        }
    }

/** Set the count for this ordinal to `newValue`. */
    protected fun setCount(ordinal: Int, newValue: Int) {
        if (sparseCounts != null) {
            sparseCounts!!.put(ordinal, newValue)
        } else {
            counts!![ordinal] = newValue
        }
    }

/** Get the count for this ordinal. */
    protected fun getCount(ordinal: Int): Int {
        if (sparseCounts != null) {
            return sparseCounts!!.get(ordinal)
        } else {
            return counts!![ordinal]
        }
    }

/** Get the aggregation value for this ordinal. */
    protected open fun getAggregationValue(ordinal: Int): Number {
        // By default, this is just the count.
        return getCount(ordinal)
    }

/** Apply an aggregation to the two values and return the result. */
    protected open fun aggregate(existingVal: Number, newVal: Number): Number {
        // By default, we are computing counts, so the values are interpreted as integers and summed.
        return existingVal.toInt() + newVal.toInt()
    }

/** Were any values actually aggregated during counting? */
    internal fun hasValues(): Boolean = initialized

/**
   * Returns int[] mapping each ordinal to its first child; this is a large array and is computed
   * (and then saved) the first time this method is invoked.
   */
    internal fun getChildren(): ParallelTaxonomyArrays.IntArray {
        if (children == null) {
            children = taxoReader.getParallelTaxonomyArrays().children()
        }
        return children!!
    }

/**
   * Returns int[] mapping each ordinal to its next sibling; this is a large array and is computed
   * (and then saved) the first time this method is invoked.
   */
    internal fun getSiblings(): ParallelTaxonomyArrays.IntArray {
        if (siblings == null) {
            siblings = taxoReader.getParallelTaxonomyArrays().siblings()
        }
        return siblings!!
    }

/**
   * Returns true if the (costly, and lazily initialized) children int[] was initialized.
   *
   * @lucene.experimental
   */
    fun childrenLoaded(): Boolean = children != null
/**
   * Returns true if the (costly, and lazily initialized) sibling int[] was initialized.
   *
   * @lucene.experimental
   */
    fun siblingsLoaded(): Boolean = siblings != null

/**
   * Verifies and returns [DimConfig] for the given dimension name.
   *
   * @return [DimConfig] for the given dim, or [FacetsConfig.DEFAULT_DIM_CONFIG] if it
   *     was never manually configured.
   * @throws IllegalArgumentException if the provided dimension was manually configured, but its
   *     [DimConfig.indexFieldName] does not match [indexFieldName].
   */
    internal fun verifyDim(dim: String): DimConfig {
        val dimConfig = config.getDimConfig(dim)
        if (config.isDimConfigured(dim) && dimConfig.indexFieldName != indexFieldName) {
            throw IllegalArgumentException("dimension \"$dim\" cannot be found in field \"$indexFieldName\", since it was configured to be indexed into field \"${dimConfig.indexFieldName}\"")
        }
        return dimConfig
    }

/**
   * Roll-up the aggregation values from `childOrdinal` to `ordinal`. Overrides should
   * probably call this to update the counts. Overriding allows us to work with primitive types for
   * the aggregation values, keeping aggregation efficient.
   */
    protected open fun updateValueFromRollup(ordinal: Int, childOrdinal: Int) {
        setCount(ordinal, getCount(ordinal) + rollup(childOrdinal))
    }

/**
   * Return a [TopOrdAndNumberQueue] of the appropriate type, i.e. a [TopOrdAndIntQueue]
   * or a [org.gnit.lucenekmp.facet.TopOrdAndFloatQueue].
   */
    protected open fun makeTopOrdAndNumberQueue(topN: Int): TopOrdAndNumberQueue = TopOrdAndIntQueue(minOf(taxoReader.getSize(), topN))

    // TODO: We don't need this if we're okay with having an integer -1 in the results even for float
    // aggregations.
/** Return the value for a missing aggregation, i.e. `-1` or `-1f`. */
    protected open fun missingAggregationValue(): Number = -1

/** Rolls up any single-valued hierarchical dimensions. */
    internal fun rollup() {
        if (!initialized) {
            return
        }
        // Rollup any necessary dims:
        var children: ParallelTaxonomyArrays.IntArray? = null
        for (ent in config.getDimConfigs().entries) {
            val dim = ent.key
            val ft = ent.value
            if (ft.hierarchical && !ft.multiValued) {
                val dimRootOrd = taxoReader.getOrdinal(FacetLabel(dim))
                // It can be -1 if this field was declared in the
                // config but never indexed:
                if (dimRootOrd > 0) {
                    if (children == null) {
                        // lazy init
                        children = getChildren()
                    }
                    updateValueFromRollup(dimRootOrd, children.get(dimRootOrd))
                }
            }
        }
    }

    private fun rollup(ord: Int): Int {
        var ord = ord
        val children = getChildren()
        val siblings = getSiblings()
        var aggregatedValue = 0
        while (ord != TaxonomyReader.INVALID_ORDINAL) {
            val currentValue = getCount(ord)
            val newValue = currentValue + rollup(children.get(ord))
            setCount(ord, newValue)
            aggregatedValue += getCount(ord)
            ord = siblings.get(ord)
        }
        return aggregatedValue
    }

/**
   * Create a FacetResult for the provided dim + path and intermediate results. Does the extra work
   * of resolving ordinals -> labels, etc. Will return null if there are no children.
   */
    private fun createFacetResult(topChildrenForPath: TopChildrenForPath?, dim: String, vararg path: String): FacetResult? {
        // If the intermediate result is null or there are no children, we return null:
        if (topChildrenForPath == null || topChildrenForPath.childCount == 0) {
            return null
        }
        val q = topChildrenForPath.childQueue
        val labelValues = arrayOfNulls<LabelAndValue>(q.size())
        val ordinals = IntArray(labelValues.size)
        val counts = IntArray(labelValues.size)
        val values = arrayOfNulls<Number>(labelValues.size)
        for (i in labelValues.size - 1 downTo 0) {
            val ordAndValue = q.pop()!!
            ordinals[i] = ordAndValue.ord
            values[i] = ordAndValue.getValue()
            counts[i] = getCount(ordinals[i])
        }
        val bulkPath = taxoReader.getBulkPath(*ordinals)
        // The path component we're interested in is the one immediately after the provided path. We
        // add 1 here to also account for the dim:
        val childComponentIdx = path.size + 1
        for (i in labelValues.indices) {
            labelValues[i] = LabelAndValue(bulkPath[i]!!.components[childComponentIdx], values[i]!!, counts[i])
        }
        @Suppress("UNCHECKED_CAST")
        return FacetResult(dim, arrayOf(*path), topChildrenForPath.pathValue, labelValues as Array<LabelAndValue>, topChildrenForPath.childCount)
    }

    override fun getAllChildren(dim: String, vararg path: String): FacetResult? {
        val dimConfig = verifyDim(dim)
        val cp = FacetLabel(dim, path)
        val dimOrd = taxoReader.getOrdinal(cp)
        if (dimOrd == -1) {
            return null
        }
        if (!initialized) {
            return null
        }
        var aggregatedValue: Number = 0
        var aggregatedCount = 0
        val ordinals = IntArrayList()
        val ordValues = mutableListOf<Number>()
        if (sparseCounts != null) {
            for (ordAndCount in sparseCounts!!) {
                val ord = ordAndCount!!.key
                val count = ordAndCount.value
                val value = getAggregationValue(ord)
                if (parents.get(ord) == dimOrd && count > 0) {
                    aggregatedCount += count
                    aggregatedValue = aggregate(aggregatedValue, value)
                    ordinals.add(ord)
                    ordValues.add(value)
                }
            }
        } else {
            val children = getChildren()
            val siblings = getSiblings()
            var ord = children.get(dimOrd)
            while (ord != TaxonomyReader.INVALID_ORDINAL) {
                val count = counts!![ord]
                val value = getAggregationValue(ord)
                if (count > 0) {
                    aggregatedCount += count
                    aggregatedValue = aggregate(aggregatedValue, value)
                    ordinals.add(ord)
                    ordValues.add(value)
                }
                ord = siblings.get(ord)
            }
        }
        if (aggregatedCount == 0) {
            return null
        }
        if (dimConfig.multiValued) {
            if (dimConfig.requireDimCount) {
                aggregatedValue = getAggregationValue(dimOrd)
            } else {
                // Our aggregated value is not correct, in general:
                aggregatedValue = missingAggregationValue()
            }
        } else {
            // Our aggregateddim value is accurate, so we keep it
        }
        // TODO: It would be nice if TaxonomyReader let us pass in a buffer + size so we didn't have to
        // do an array copy here:
        val bulkPath = taxoReader.getBulkPath(*ordinals.toArray())
        val labelValues = Array(ordValues.size) { i -> LabelAndValue(bulkPath[i]!!.components[cp.length], ordValues[i], getCount(ordinals.get(i))) }
        return FacetResult(dim, arrayOf(*path), aggregatedValue, labelValues, ordinals.size())
    }

    protected open fun setIncomingValue(incomingOrdAndValue: TopOrdAndNumberQueue.OrdAndValue, ord: Int) {
        (incomingOrdAndValue as TopOrdAndIntQueue.OrdAndInt).value = getCount(ord)
    }

    protected fun insertIntoQueue(q: TopOrdAndNumberQueue, incomingOrdAndValue: TopOrdAndNumberQueue.OrdAndValue?, ord: Int): TopOrdAndNumberQueue.OrdAndValue? {
        var incomingOrdAndValue = incomingOrdAndValue
        if (incomingOrdAndValue == null) {
            incomingOrdAndValue = q.newOrdAndValue()
        }
        incomingOrdAndValue.ord = ord
        setIncomingValue(incomingOrdAndValue, ord)
        incomingOrdAndValue = q.insertWithOverflow(incomingOrdAndValue)
        return incomingOrdAndValue
    }

    protected abstract class AggregatedValue {
        /** Aggregate the value corresponding to the given ordinal into this value. */
        abstract fun aggregate(ord: Int)
        /** Retrieve the encapsulated value. */
        abstract fun get(): Number
    }

    private inner class AggregatedCount(private var count: Int) : AggregatedValue() {
        override fun aggregate(ord: Int) { count += getCount(ord) }
        override fun get(): Number = count
    }

    protected open fun newAggregatedValue(): AggregatedValue = AggregatedCount(0)

/**
   * Determine the top-n children for a specified dimension + path. Results are in an intermediate
   * form.
   */
    protected fun getTopChildrenForPath(dimConfig: DimConfig, pathOrd: Int, topN: Int): TopChildrenForPath {
        val q = makeTopOrdAndNumberQueue(topN)
        val aggregatedValue = newAggregatedValue()
        var childCount = 0
        var incomingOrdAndValue: TopOrdAndNumberQueue.OrdAndValue? = null
        // TODO: would be faster if we had a "get the following children" API?  then we
        // can make a single pass over the hashmap
        if (sparseCounts != null) {
            for (c in sparseCounts!!) {
                val ord = c!!.key
                val count = c.value
                if (parents.get(ord) == pathOrd && count > 0) {
                    aggregatedValue.aggregate(ord)
                    childCount++
                    incomingOrdAndValue = insertIntoQueue(q, incomingOrdAndValue, ord)
                }
            }
        } else {
            val children = getChildren()
            val siblings = getSiblings()
            var ord = children.get(pathOrd)
            while (ord != TaxonomyReader.INVALID_ORDINAL) {
                val count = counts!![ord]
                if (count > 0) {
                    aggregatedValue.aggregate(ord)
                    childCount++
                    incomingOrdAndValue = insertIntoQueue(q, incomingOrdAndValue, ord)
                }
                ord = siblings.get(ord)
            }
        }
        var aggregatedValueNumber = aggregatedValue.get()
        if (dimConfig.multiValued) {
            if (dimConfig.requireDimCount) {
                aggregatedValueNumber = getAggregationValue(pathOrd)
            } else {
                // Our aggregated value is not correct, in general:
                aggregatedValueNumber = missingAggregationValue()
            }
        }
        return TopChildrenForPath(aggregatedValueNumber, childCount, q)
    }

    override fun getTopChildren(topN: Int, dim: String, vararg path: String): FacetResult? {
        validateTopN(topN)
        val dimConfig = verifyDim(dim)
        val cp = FacetLabel(dim, path)
        val dimOrd = taxoReader.getOrdinal(cp)
        if (dimOrd == -1) {
            return null
        }
        if (!initialized) {
            return null
        }
        val topChildrenForPath = getTopChildrenForPath(dimConfig, dimOrd, topN)
        return createFacetResult(topChildrenForPath, dim, *path)
    }

    override fun getSpecificValue(dim: String, vararg path: String): Number {
        val dimConfig = verifyDim(dim)
        if (path.isEmpty()) {
            if (dimConfig.hierarchical && !dimConfig.multiValued) {
                // ok: rolled up at search time
            } else if (dimConfig.requireDimCount && dimConfig.multiValued) {
                // ok: we indexed all ords at index time
            } else {
                throw IllegalArgumentException("cannot return dimension-level value alone; use getTopChildren instead")
            }
        }
        val ord = taxoReader.getOrdinal(FacetLabel(dim, path))
        if (ord < 0) {
            return -1
        }
        return if (initialized) getAggregationValue(ord) else 0
    }

    override fun getAllDims(topN: Int): List<FacetResult> {
        validateTopN(topN)
        if (!hasValues()) {
            return emptyList()
        }
        val children = getChildren()
        val siblings = getSiblings()
        var ord = children.get(TaxonomyReader.ROOT_ORDINAL)
        val results = mutableListOf<FacetResult>()
        while (ord != TaxonomyReader.INVALID_ORDINAL) {
            val dim = taxoReader.getPath(ord)!!.components[0]
            val dimConfig = config.getDimConfig(dim)
            if (dimConfig.indexFieldName == indexFieldName) {
                val result = getTopChildren(topN, dim)
                if (result != null) {
                    results.add(result)
                }
            }
            ord = siblings.get(ord)
        }
        // Sort by highest value, tie break by dim:
        results.sortWith(Comparator { a, b ->
            if (a.value.toDouble() > b.value.toDouble()) -1
            else if (b.value.toDouble() > a.value.toDouble()) 1
            else a.dim.compareTo(b.dim)
        })
        return results
    }

    override fun getTopDims(topNDims: Int, topNChildren: Int): List<FacetResult> {
        if (topNDims <= 0 || topNChildren <= 0) {
            throw IllegalArgumentException("topN must be > 0")
        }
        if (!initialized) {
            return emptyList()
        }
        // get children and siblings ordinal array from TaxonomyFacets
        val children = getChildren()
        val siblings = getSiblings()
        // Create priority queue to store top dimensions and sort by their aggregated values/hits and
        // string values.
        val pq = object : PriorityQueue<DimValue>(topNDims) {
            override fun lessThan(a: DimValue, b: DimValue): Boolean {
                val comparison = valueComparator.compare(a.value, b.value)
                if (comparison < 0) return true
                if (comparison > 0) return false
                return a.dim.compareTo(b.dim) > 0
            }
        }
        // Keep track of intermediate results, if we compute them, so we can reuse them later:
        var intermediateResults: MutableMap<String, TopChildrenForPath>? = null
        // iterate over children and siblings ordinals for all dims
        var ord = children.get(TaxonomyReader.ROOT_ORDINAL)
        while (ord != TaxonomyReader.INVALID_ORDINAL) {
            val dim = taxoReader.getPath(ord)!!.components[0]
            val dimConfig = config.getDimConfig(dim)
            if (dimConfig.indexFieldName == indexFieldName) {
                val cp = FacetLabel(dim)
                val dimOrd = taxoReader.getOrdinal(cp)
                if (dimOrd != -1) {
                    val dimValue: Number
                    if (dimConfig.multiValued) {
                        if (dimConfig.requireDimCount) {
                            // If the dim is configured as multi-valued and requires dim counts, we can access
                            // an accurate count for the dim computed at indexing time:
                            dimValue = getAggregationValue(dimOrd)
                        } else {
                            // If the dim is configured as multi-valued but not requiring dim counts, we cannot
                            // compute an accurate dim count, and use -1 as a place-holder:
                            dimValue = -1
                        }
                    } else {
                        // Single-valued dims require aggregating descendant paths to get accurate dim counts
                        // since we don't directly access ancestry paths:
                        // TODO: We could consider indexing dim counts directly if getTopDims is a common
                        // use-case.
                        val topChildrenForPath = getTopChildrenForPath(dimConfig, dimOrd, topNChildren)
                        if (intermediateResults == null) intermediateResults = HashMap()
                        intermediateResults[dim] = topChildrenForPath
                        dimValue = topChildrenForPath.pathValue
                    }
                    if (valueComparator.compare(dimValue, 0) != 0) {
                        if (pq.size() < topNDims) {
                            pq.add(DimValue(dim, dimOrd, dimValue))
                        } else {
                            if (valueComparator.compare(dimValue, pq.top().value) > 0 ||
                                (valueComparator.compare(dimValue, pq.top().value) == 0 && dim.compareTo(pq.top().dim) < 0)) {
                                val bottomDim = pq.top()
                                bottomDim.dim = dim
                                bottomDim.value = dimValue
                                pq.updateTop()
                            }
                        }
                    }
                }
            }
            ord = siblings.get(ord)
        }
        val results = arrayOfNulls<FacetResult>(pq.size())
        while (pq.size() > 0) {
            val dimValue = pq.pop()!!
            val dim = dimValue.dim
            var topChildrenForPath: TopChildrenForPath? = null
            if (intermediateResults != null) topChildrenForPath = intermediateResults[dim]
            if (topChildrenForPath == null) {
                val dimConfig = config.getDimConfig(dim)
                topChildrenForPath = getTopChildrenForPath(dimConfig, dimValue.dimOrd, topNChildren)
            }
            val facetResult = createFacetResult(topChildrenForPath, dim)
            assert(facetResult != null)
            results[pq.size()] = facetResult
        }
        @Suppress("UNCHECKED_CAST")
        return (results as Array<FacetResult>).asList()
    }
}
