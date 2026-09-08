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
package org.gnit.lucenekmp.facet.sortedset

import org.gnit.lucenekmp.facet.*
import org.gnit.lucenekmp.facet.FacetsConfig.DimConfig
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.jdkport.PrimitiveIterator
import org.gnit.lucenekmp.jdkport.assert

/** Base class for SSDV faceting implementations. */
abstract class AbstractSortedSetDocValueFacetCounts(state: SortedSetDocValuesReaderState) : Facets() {

  protected val state = state
  protected val stateConfig: FacetsConfig
  protected val dv: SortedSetDocValues
  protected val field: String

  init {
    this.field = state.field
    this.stateConfig = state.facetsConfig
    this.dv = state.getDocValues()!!
  }

  override fun getTopChildren(topN: Int, dim: String, vararg path: String): FacetResult? {
    validateTopN(topN)
    if (hasCounts() == false) {
      return null
    }
    val topChildrenForPath = getTopChildrenForPath(topN, dim, *path)
    return createFacetResult(topChildrenForPath, dim, *path)
  }

  override fun getAllChildren(dim: String, vararg path: String): FacetResult? {
    val dimConfig = stateConfig.getDimConfig(dim)
    val iterationCursor = prepareChildIteration(dim, dimConfig, *path)
    if (iterationCursor == null) {
      return null
    }

    if (hasCounts() == false) {
      return null
    }

    // Compute the actual results:
    var pathCount = 0
    val labelValues = ArrayList<LabelAndValue>()
    while (iterationCursor.childIterator.hasNext()) {
      var ord = iterationCursor.childIterator.next()
      var count = getCount(ord)
      if (count > 0) {
        pathCount += count
        val term = dv.lookupOrd(ord.toLong())!!
        val parts = FacetsConfig.stringToPath(term.utf8ToString())
        labelValues.add(LabelAndValue(parts[parts.size - 1], count))
      }
    }

    pathCount = adjustPathCountIfNecessary(dimConfig, iterationCursor.pathOrd, pathCount)

    return FacetResult(
        dim, arrayOf(*path), pathCount, labelValues.toTypedArray(), labelValues.size)
  }

  override fun getSpecificValue(dim: String, vararg path: String): Number {
    if (stateConfig.getDimConfig(dim).hierarchical == false && path.size != 1) {
      throw IllegalArgumentException(
          dim + " is not configured as hierarchical, path must be length=1")
    }
    var ord = dv.lookupTerm(BytesRef(FacetsConfig.pathToString(dim, *path))).toInt()
    if (ord < 0) {
      return -1
    }

    return if (hasCounts() == false) 0 else getCount(ord)
  }

  override fun getAllDims(topN: Int): List<FacetResult> {
    validateTopN(topN)

    if (hasCounts() == false) {
      return emptyList()
    }

    val results = ArrayList<FacetResult>()
    for (dim in state.getDims()) {
      val topChildrenForPath = getTopChildrenForPath(topN, dim)
      val facetResult = createFacetResult(topChildrenForPath, dim)
      if (facetResult != null) {
        results.add(facetResult)
      }
    }

    // Sort by highest count:
    results.sortWith { a, b ->
      if (a.value.toInt() > b.value.toInt()) {
        -1
      } else if (b.value.toInt() > a.value.toInt()) {
        1
      } else {
        a.dim.compareTo(b.dim)
      }
    }
    return results
  }

  override fun getTopDims(topNDims: Int, topNChildren: Int): List<FacetResult> {
    validateTopN(topNDims)
    validateTopN(topNChildren)

    if (hasCounts() == false) {
      return emptyList()
    }

    // Creates priority queue to store top dimensions and sort by their aggregated values/hits and
    // string values.
    val pq = object : PriorityQueue<DimValue>(topNDims) {
      override fun lessThan(a: DimValue, b: DimValue): Boolean {
        if (a.value > b.value) {
          return false
        } else if (a.value < b.value) {
          return true
        } else {
          return a.dim.compareTo(b.dim) > 0
        }
      }
    }

    // Keep track of intermediate results, if we compute them, so we can reuse them later:
    var intermediateResults: MutableMap<String, TopChildrenForPath>? = null

    for (dim in state.getDims()) {
      val dimConfig = stateConfig.getDimConfig(dim)
      val dimCount: Int
      if (dimConfig.hierarchical) {
        // For hierarchical dims, we directly index each level of the ancestry path (i.e., we
        // "rollup" at indexing time), meaning we can directly access accurate dim counts without
        // needing to rollup the descendant paths:
        var dimOrd = state.getDimTree(dim)!!.dimStartOrd
        dimCount = getCount(dimOrd)
      } else {
        val ordRange = state.getOrdRange(dim)!!
        var dimOrd = ordRange.start
        if (dimConfig.multiValued) {
          if (dimConfig.requireDimCount) {
            // If a dim is configured as multi-valued and requires dim count, we index dim counts
            // directly, so we can access accurate counts  without needing to rollup children:
            dimCount = getCount(dimOrd)
          } else {
            // If a dim is configured as multi-valued but _not_ requiring dim count, we have no
            // way to get accurate counts. We use -1 to indicate this:
            dimCount = -1
          }
        } else {
          // If a dim is single-valued, we must aggregate child counts to get accurate dim counts.
          // We don't index the dim counts directly:
          // TODO: If getTopDims becomes a common use-case, we could consider always indexing dim
          // counts to optimize this path.
          val childIt = ordRange.iterator()
          val topChildrenForPath =
              computeTopChildren(childIt, topNChildren, dimConfig, dimOrd)
          if (intermediateResults == null) {
            intermediateResults = HashMap()
          }
          intermediateResults.put(dim, topChildrenForPath)
          dimCount = topChildrenForPath.pathCount
        }
      }

      if (dimCount != 0) {
        if (pq.size() < topNDims) {
          pq.add(DimValue(dim, dimCount))
        } else {
          if (dimCount > pq.top()!!.value
              || (dimCount == pq.top()!!.value && dim.compareTo(pq.top()!!.dim) < 0)) {
            val bottomDim = pq.top()!!
            bottomDim.dim = dim
            bottomDim.value = dimCount
            pq.updateTop()
          }
        }
      }
    }

    var resultSize = pq.size()
    val results = arrayOfNulls<FacetResult>(resultSize)

    while (pq.size() > 0) {
      val dimValue = pq.pop()!!
      assert(dimValue != null)
      var topChildrenForPath: TopChildrenForPath? = null
      if (intermediateResults != null) {
        topChildrenForPath = intermediateResults.get(dimValue.dim)
      }
      if (topChildrenForPath == null) {
        topChildrenForPath = getTopChildrenForPath(topNChildren, dimValue.dim)
      }
      val facetResult = createFacetResult(topChildrenForPath, dimValue.dim)
      // should not be null since only dims with non-zero values were considered earlier
      assert(facetResult != null)
      resultSize--
      results[resultSize] = facetResult
    }
    return results.requireNoNulls().asList()
  }

  /** Were any counts actually computed? (They may not be if there are no hits, etc.) */
  protected abstract fun hasCounts(): Boolean

  /** Retrieve the count for a specified ordinal. */
  protected abstract fun getCount(ord: Int): Int

  /**
   * Determine the path ord and resolve an iterator to its immediate children. The logic for this
   * depends on whether-or-not the dimension is configured as hierarchical.
   */
  private fun prepareChildIteration(dim: String, dimConfig: DimConfig, vararg path: String): ChildIterationCursor? {
    val pathOrd: Int
    val childIterator: PrimitiveIterator.OfInt
    if (dimConfig.hierarchical) {
      val dimTree = state.getDimTree(dim)!!
      if (path.size > 0) {
        pathOrd = dv.lookupTerm(BytesRef(FacetsConfig.pathToString(dim, *path))).toInt()
      } else {
        // If there's no path, this is a little more efficient to just look up the dim:
        pathOrd = dimTree.dimStartOrd
      }
      if (pathOrd < 0) {
        // path was never indexed
        return null
      }
      childIterator = dimTree.iterator(pathOrd)
    } else {
      if (path.size > 0) {
        throw IllegalArgumentException(
            "Field is not configured as hierarchical, path should be 0 length")
      }
      val ordRange = state.getOrdRange(dim)
      if (ordRange == null) {
        // means dimension was never indexed
        return null
      }
      pathOrd = ordRange.start
      childIterator = ordRange.iterator()
      if (dimConfig.multiValued && dimConfig.requireDimCount) {
        // If the dim is multi-valued and requires dim counts, we know we've explicitly indexed
        // the dimension and we need to skip past it so the iterator is positioned on the first
        // child:
        childIterator.next()
      }
    }

    return ChildIterationCursor(pathOrd, childIterator)
  }

  /**
   * Determine the top-n children for a specified dimension + path. Results are in an intermediate
   * form.
   */
  private fun getTopChildrenForPath(topN: Int, dim: String, vararg path: String): TopChildrenForPath? {
    val dimConfig = stateConfig.getDimConfig(dim)
    val iterationCursor = prepareChildIteration(dim, dimConfig, *path)
    if (iterationCursor == null) {
      return null
    }

    // Compute the actual results:
    return computeTopChildren(
        iterationCursor.childIterator, topN, dimConfig, iterationCursor.pathOrd)
  }

  /**
   * Compute the top-n children for the given path and iterator of all immediate children of the
   * path. This returns an intermediate result that does the minimal required work, avoiding the
   * cost of looking up string labels, etc.
   */
  private fun computeTopChildren(childOrds: PrimitiveIterator.OfInt, topN: Int, dimConfig: DimConfig, pathOrd: Int): TopChildrenForPath {
    var q: TopOrdAndIntQueue? = null
    var pathCount = 0
    var childCount = 0

    var reuse: TopOrdAndIntQueue.OrdAndInt? = null
    while (childOrds.hasNext()) {
      var ord = childOrds.next()
      var count = getCount(ord)
      if (count > 0) {
        pathCount += count
        childCount++
        if (q == null) {
          // Lazy init, so we don't create this for the
          // sparse case unnecessarily
          q = TopOrdAndIntQueue(topN)
        }
        if (reuse == null) {
          reuse = q.newOrdAndValue() as TopOrdAndIntQueue.OrdAndInt
        }
        reuse.ord = ord
        reuse.value = count
        reuse = q.insertWithOverflow(reuse) as TopOrdAndIntQueue.OrdAndInt?
      }
    }

    pathCount = adjustPathCountIfNecessary(dimConfig, pathOrd, pathCount)

    return TopChildrenForPath(pathCount, childCount, q)
  }

  private fun adjustPathCountIfNecessary(dimConfig: DimConfig, pathOrd: Int, computedCount: Int): Int {
    if (dimConfig.hierarchical) {
      // hierarchical dims index all path counts directly:
      return getCount(pathOrd)
    } else {
      if (dimConfig.multiValued) {
        if (dimConfig.requireDimCount) {
          // multi-value dims configured to "require dim counts" also index path counts directly:
          return getCount(pathOrd)
        } else {
          // we're unable to produce accurate counts for multi-value dims that are _not_ configured
          // to "require dim counts":
          return -1
        }
      } else {
        // aggregated counts are correct for single-valued, non-hierarchical dims:
        return computedCount
      }
    }
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

    val q = topChildrenForPath.q!!
    assert(q != null)

    val labelValues = arrayOfNulls<LabelAndValue>(q.size())
    for (i in labelValues.size - 1 downTo 0) {
      val ordAndValue = q.pop() as TopOrdAndIntQueue.OrdAndInt
      assert(ordAndValue != null)
      val term = dv.lookupOrd(ordAndValue.ord.toLong())!!
      val parts = FacetsConfig.stringToPath(term.utf8ToString())
      labelValues[i] = LabelAndValue(parts[parts.size - 1], ordAndValue.value)
    }

    return FacetResult(
        dim, arrayOf(*path), topChildrenForPath.pathCount, labelValues.requireNoNulls(), topChildrenForPath.childCount)
  }

  /** Intermediate result to store top children for a given path before resolving labels, etc. */
  data class TopChildrenForPath(val pathCount: Int, val childCount: Int, val q: TopOrdAndIntQueue?)

  private class DimValue(var dim: String, var value: Int)

  private data class ChildIterationCursor(val pathOrd: Int, val childIterator: PrimitiveIterator.OfInt)
}
