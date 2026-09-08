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

import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.index.MultiDocValues.MultiSortedSetDocValues
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.Accountables
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.jdkport.assert

/**
 * Default implementation of [SortedSetDocValuesFacetCounts]. You must ensure the original
 * [IndexReader] passed to the constructor is not closed whenever you use this class!
 */
open class DefaultSortedSetDocValuesReaderState : SortedSetDocValuesReaderState {

/** Indexed field we are reading. */
  final override val field: String
  private val valueCount: Int

  /** [IndexReader] passed to the constructor. */
  final override val reader: IndexReader

  /**
   * There is only ever one cached ordinal map, but storing it in a hash map together with the field
   * name is convenient. It enables us to have code blocks synchronized on the ordinal map even when
   * the ordinal map is null and it makes it easy to call to [Accountables.namedAccountables] in [getChildResources].
   */
  private val cachedOrdMap = HashMap<String, OrdinalMap>()
  // Explicit lock for the upstream cachedOrdMap monitor.
  private val cachedOrdMapLock = ReentrantLock()

  private val config: FacetsConfig

  /** Used for hierarchical dims. */
  private val prefixToDimTree = HashMap<String, DimTree>()

  /** Used for flat dims. */
  private val prefixToOrdRange = HashMap<String, OrdRange>()

  /**
   * Creates this with a config, pulling doc values from the default [FacetsConfig.DEFAULT_INDEX_FIELD_NAME].
   */
  constructor(reader: IndexReader, config: FacetsConfig) : this(reader, FacetsConfig.DEFAULT_INDEX_FIELD_NAME, config)

  /** Creates this, pulling doc values from the specified field. */
  constructor(reader: IndexReader, field: String, config: FacetsConfig) {
    this.field = field
    this.reader = reader
    this.config = config

    // We need this to create thread-safe MultiSortedSetDV
    // per collector:
    var dv = getDocValues()
    if (dv == null) {
      throw IllegalArgumentException(
          "field \"" + field + "\" was not indexed with SortedSetDocValues")
    }
    if (dv.valueCount > Int.MAX_VALUE) {
      throw IllegalArgumentException(
          "can only handle valueCount < Int.MAX_VALUE; got " + dv.valueCount)
    }
    valueCount = dv.valueCount.toInt()

    var ord = 0
    while (ord != valueCount) {
      var term = dv.lookupOrd((ord).toLong())!!
      var components = FacetsConfig.stringToPath(term.utf8ToString())
      var dim = components[0]
      if (config.getDimConfig(dim).hierarchical) {
        ord = createOneHierarchicalFacetDimState(dv, ord) + 1
      } else {
        ord = createOneFlatFacetDimState(dv, ord) + 1
      }
    }
  }

  // returns last ord of dimension
  private fun createOneHierarchicalFacetDimState(dv: SortedSetDocValues, dimStartOrd: Int): Int {
    val hasChildren = ArrayList<Boolean>()
    val siblings = ArrayList<Int>()

    // stack of paths with unfulfilled siblings
    val siblingStack = ArrayDeque<OrdAndComponent>()

    var dimEndOrd = dimStartOrd

    var nextTerm = dv.lookupOrd((dimEndOrd).toLong())!!
    var nextComponents = FacetsConfig.stringToPath(nextTerm.utf8ToString())
    var dim = nextComponents[0]

    while (true) {
      var components = nextComponents

      var ord = dimEndOrd - dimStartOrd

      while (siblingStack.isEmpty() == false
          && siblingStack.first().component.size >= components.size) {
        var possibleSibling = siblingStack.removeFirst()
        if (possibleSibling.component.size == components.size) {
          // lengths are equal, all non-siblings of equal length will have already been popped off
          // so this must be sibling
          siblings.set(possibleSibling.ord, ord)
        }
      }

      if (dimEndOrd + 1 == valueCount) {
        // current ord needs to be added, can't have children or siblings
        siblings.add(-1)
        hasChildren.add(false)
        break
      }

      nextTerm = dv.lookupOrd((dimEndOrd + 1).toLong())!!
      nextComponents = FacetsConfig.stringToPath(nextTerm.utf8ToString())

      if (nextComponents[0].equals(components[0]) == false) {
        // current ord needs to be added, can't have children or siblings
        siblings.add(-1)
        hasChildren.add(false)
        break
      }

      if (components.size < nextComponents.size) {
        // next ord must be a direct child of current ord, this is because we are indexing all
        // ancestral paths
        hasChildren.add(ord, true)
        // we don't know if this ord has a sibling or where it's sibling could be yet
        siblingStack.addFirst(OrdAndComponent(ord, components))
        // we still add INVALID_ORDINAL, which will be replaced if a valid sibling is found
        siblings.add(ord, INVALID_ORDINAL)
      } else if (components.size == nextComponents.size) {
        // next ord must be a sibling of current and there are no direct children of current, this
        // is because we
        // are indexing all ancestral paths
        siblings.add(ord, ord + 1)
        hasChildren.add(ord, false)
      } else {
        // components.length > nextComponents.length
        // next ord is neither sibling nor child
        siblings.add(ord, INVALID_ORDINAL)
        hasChildren.add(ord, false)
      }

      dimEndOrd++
    }

    prefixToDimTree.put(dim, DimTree(dimStartOrd, siblings, hasChildren))

    return dimEndOrd
  }

  // returns last ord of dimension
  private fun createOneFlatFacetDimState(dv: SortedSetDocValues, dimStartOrd: Int): Int {

    var dimEndOrd = dimStartOrd

    var nextTerm = dv.lookupOrd((dimEndOrd).toLong())!!
    var nextComponents = FacetsConfig.stringToPath(nextTerm.utf8ToString())
    // The first entry should always be length 1 or 2 (either just the dim itself if we explicitly
    // indexed it, or the first child):
    if (nextComponents.size > 2) {
      throw IllegalArgumentException(
          "dimension not configured to handle hierarchical field; got: "
              + nextComponents.contentToString()
              + " "
              + nextTerm.utf8ToString())
    }
    var dim = nextComponents[0]

    while (true) {
      var components = nextComponents

      if (dimEndOrd + 1 == valueCount) {
        break
      }

      nextTerm = dv.lookupOrd((dimEndOrd + 1).toLong())!!
      nextComponents = FacetsConfig.stringToPath(nextTerm.utf8ToString())

      if (nextComponents[0].equals(components[0]) == false) {
        break
      }

      // Each entry should have a length of exactly 2 since the dim is non-hierarchical:
      if (nextComponents.size != 2) {
        throw IllegalArgumentException(
            "dimension not configured to handle hierarchical field; got: "
                + nextComponents.contentToString()
                + " "
                + nextTerm.utf8ToString())
      }

      dimEndOrd++
    }
    prefixToOrdRange.put(dim, OrdRange(dimStartOrd, dimEndOrd))

    return dimEndOrd
  }

  /** Return the memory usage of this object in bytes. Negative values are illegal. */
  override fun ramBytesUsed(): Long {
    cachedOrdMapLock.withLock {
      val map = cachedOrdMap[field]
      if (map == null) {
        return 0
      } else {
        return map.ramBytesUsed()
      }
    }
  }

  /**
   * Returns nested resources of this class. The result should be a point-in-time snapshot (to avoid
   * race conditions).
   *
   * @see Accountables
   */
  override val childResources: MutableCollection<Accountable> get() {
    cachedOrdMapLock.withLock {
      return Accountables.namedAccountables("DefaultSortedSetDocValuesReaderState", cachedOrdMap)
    }
  }

  override fun toString(): String {
    return "DefaultSortedSetDocValuesReaderState(field=" + field + " reader=" + reader + ")"
  }

  /** Return top-level doc values. */
  override fun getDocValues(): SortedSetDocValues? {
    var map: OrdinalMap? = null
    cachedOrdMapLock.withLock {
      map = cachedOrdMap.get(field)
      if (map == null) {
        // uncached, or not a multi dv
        var dv = MultiDocValues.getSortedSetValues(reader, field)
        if (dv is MultiSortedSetDocValues) {
          map = dv.mapping
          var cacheHelper = reader.readerCacheHelper
          if (cacheHelper != null && map!!.owner == cacheHelper.key) {
            cachedOrdMap.put(field, map!!)
          }
        }
        return dv
      }
    }

    assert(map != null)
    var size = reader.leaves().size
    val values = arrayOfNulls<SortedSetDocValues>(size)
    val starts = IntArray(size + 1)
    var cost = 0L
    for (i in 0 until size) {
      var context = reader.leaves().get(i)
      var reader = context.reader()
      var fieldInfo = reader.fieldInfos.fieldInfo(field)
      if (fieldInfo != null && fieldInfo.docValuesType != DocValuesType.SORTED_SET) {
        return null
      }
      var v = reader.getSortedSetDocValues(field)
      if (v == null) {
        v = DocValues.emptySortedSet()
      }
      values[i] = v
      starts[i] = context.docBase
      cost += v.cost()
    }
    starts[size] = reader.maxDoc()
    return MultiSortedSetDocValues(values.requireNoNulls(), starts, map!!, cost)
  }



  /** Number of unique labels. */
  override val size: Int get() = valueCount

  override val facetsConfig: FacetsConfig get() = config

  override fun getDims(): Iterable<String> = Iterable {
    object : Iterator<String> {
      val dimTreeIterator = prefixToDimTree.keys.iterator()
      val ordRangeIterator = prefixToOrdRange.keys.iterator()

      override fun hasNext(): Boolean = ordRangeIterator.hasNext() || dimTreeIterator.hasNext()

      override fun next(): String {
        if (dimTreeIterator.hasNext()) {
          return dimTreeIterator.next()
        } else if (ordRangeIterator.hasNext()) {
          return ordRangeIterator.next()
        } else {
          // Kotlin's non-null Iterator contract rejects access after exhaustion.
          throw NoSuchElementException()
        }
      }
    }
  }

  /* Flat facet operations */

  override fun getPrefixToOrdRange(): Map<String, OrdRange> {
    return prefixToOrdRange
  }

  override fun getOrdRange(dim: String): OrdRange? {
    if (config.getDimConfig(dim).hierarchical) {
      throw UnsupportedOperationException(
          "This operation is only supported for flat dimensions")
    }
    return prefixToOrdRange.get(dim)
  }

  /* Hierarchical facet operations */

  override fun getDimTree(dim: String): DimTree? {
    if (config.getDimConfig(dim).hierarchical == false) {
      throw UnsupportedOperationException(
          "This operation is only supported for hierarchical facets")
    }
    return prefixToDimTree.get(dim)
  }

  private class OrdAndComponent(var ord: Int, var component: Array<String>)
}
