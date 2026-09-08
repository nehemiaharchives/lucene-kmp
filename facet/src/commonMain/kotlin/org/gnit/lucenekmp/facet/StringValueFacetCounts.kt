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
package org.gnit.lucenekmp.facet

import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.jdkport.assert


// TODO: Add a concurrent version much like ConcurrentSortedSetDocValuesFacetCounts?
/**
 * Compute facet counts from a previously indexed [SortedSetDocValues] or [org.gnit.lucenekmp.index.SortedDocValues] field. This approach will execute facet counting against
 * the string values found in the specified field, with no assumptions on their format. Unlike
 * [org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetCounts], no assumption is made
 * about a "dimension" path component being indexed. Because of this, the field itself is
 * effectively treated as the "dimension", and counts for all unique string values are produced.
 * This approach is meant to complement [LongValueFacetCounts] in that they both provide facet
 * counting on a doc value field with no assumptions of content.
 *
 * This implementation is useful if you want to dynamically count against any string doc value
 * field without relying on [FacetField] and [FacetsConfig]. The disadvantage is that a
 * separate field is required for each "dimension". If you want to pack multiple dimensions into the
 * same doc values field, you probably want one of [org.gnit.lucenekmp.facet.taxonomy.FastTaxonomyFacetCounts] or [org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetCounts].
 *
 * Note that there is an added cost on every [IndexReader] open to create a new [StringDocValuesReaderState]. Also note that this class should be instantiated and used from a
 * single thread, because it holds a thread-private instance of [SortedSetDocValues].
 *
 * @lucene.experimental
 */
class StringValueFacetCounts : Facets {

  private val reader: IndexReader
  private val field: String
  private val ordinalMap: OrdinalMap?
  private val docValues: SortedSetDocValues

  private var denseCounts: IntArray? = null
  private val sparseCounts: HashMap<Int, Int>?
  private var initialized: Boolean

  private val cardinality: Int
  private var totalDocCount = 0

  /**
   * Returns all facet counts for the field, same result as searching on [MatchAllDocsQuery]
   * but faster.
   */
  constructor(state: StringDocValuesReaderState) : this(state, null)

  /** Counts facets across the provided hits. */
  constructor(state: StringDocValuesReaderState, facetsCollector: FacetsCollector?) : super() {
    reader = state.reader
    field = state.field
    ordinalMap = state.ordinalMap
    docValues = getDocValues()

    val valueCount = docValues.valueCount
    if (valueCount > Int.MAX_VALUE) {
      throw IllegalArgumentException(
          "can only handle valueCount < Int.MAX_VALUE; got " + valueCount)
    }
    cardinality = valueCount.toInt()

    if (facetsCollector != null) {
      if (cardinality < 1024) { // count densely for low cardinality
        sparseCounts = null
        denseCounts = null
        initialized = false
        count(facetsCollector)
      } else {
        var totalHits = 0
        var totalDocs = 0
        for (matchingDocs in facetsCollector.getMatchingDocs()) {
          totalHits += matchingDocs.totalHits
          totalDocs += matchingDocs.context.reader().maxDoc()
        }

        // No counting needed if there are no hits:
        if (totalHits == 0) {
          sparseCounts = null
          denseCounts = null
          initialized = true
        } else {
          // If our result set is < 10% of the index, we collect sparsely (use hash map). This
          // heuristic is borrowed from IntTaxonomyFacetCounts:
          if (totalHits < totalDocs / 10) {
            sparseCounts = HashMap()
            denseCounts = null
            initialized = true
          } else {
            sparseCounts = null
            denseCounts = IntArray(cardinality)
            initialized = true
          }
          count(facetsCollector)
        }
      }
    } else {
      // Since we're counting all ordinals, count densely:
      sparseCounts = null
      denseCounts = IntArray(cardinality)
      initialized = true

      countAll()
    }
  }

  override fun getAllChildren(dim: String, vararg path: String): FacetResult {
    validateDimAndPathForGetChildren(dim, *path)

    val labelValues = ArrayList<LabelAndValue>()

    if (sparseCounts != null) {
      for (sparseCount in sparseCounts) {
        var count = sparseCount.value
        val term = docValues.lookupOrd((sparseCount.key).toLong())!!
        labelValues.add(LabelAndValue(term.utf8ToString(), count))
      }
    } else if (denseCounts != null) {
      for (i in 0 until denseCounts!!.size) {
        var count = denseCounts!![i]
        if (count != 0) {
          val term = docValues.lookupOrd((i).toLong())!!
          labelValues.add(LabelAndValue(term.utf8ToString(), count))
        }
      }
    }

    return FacetResult(
        field,
        emptyArray(),
        totalDocCount,
        labelValues.toTypedArray(),
        labelValues.size)
  }

  override fun getTopChildren(topN: Int, dim: String, vararg path: String): FacetResult {
    validateTopN(topN)
    validateDimAndPathForGetChildren(dim, *path)

    val topN = minOf(topN, cardinality)
    var q: TopOrdAndIntQueue? = null
    var reuse: TopOrdAndIntQueue.OrdAndInt? = null
    var childCount = 0 // total number of labels with non-zero count

    if (sparseCounts != null) {
      for (sparseCount in sparseCounts) {
        childCount++ // every count in sparseValues should be non-zero
        var ord = sparseCount.key
        var count = sparseCount.value
        if (q == null) {
          // Lazy init for sparse case:
          q = TopOrdAndIntQueue(topN)
        }
        if (reuse == null) {
          reuse = q.newOrdAndValue() as TopOrdAndIntQueue.OrdAndInt
        }
        reuse.ord = ord
        reuse.value = count
        reuse = q.insertWithOverflow(reuse) as TopOrdAndIntQueue.OrdAndInt?
      }
    } else if (denseCounts != null) {
      for (i in 0 until denseCounts!!.size) {
        var count = denseCounts!![i]
        if (count != 0) {
          childCount++
          if (q == null) {
            // Lazy init for sparse case:
            q = TopOrdAndIntQueue(topN)
          }
          if (reuse == null) {
            reuse = q.newOrdAndValue() as TopOrdAndIntQueue.OrdAndInt
          }
          reuse.ord = i
          reuse.value = count
          reuse = q.insertWithOverflow(reuse) as TopOrdAndIntQueue.OrdAndInt?
        }
      }
    }

    val resultCount = if (q == null) 0 else q.size()
    val labelValues = arrayOfNulls<LabelAndValue>(resultCount)
    for (i in labelValues.size - 1 downTo 0) {
      val ordAndValue = q!!.pop() as TopOrdAndIntQueue.OrdAndInt
      val term = docValues.lookupOrd((ordAndValue.ord).toLong())!!
      labelValues[i] = LabelAndValue(term.utf8ToString(), ordAndValue.value)
    }

    return FacetResult(field, emptyArray(), totalDocCount, labelValues.requireNoNulls(), childCount)
  }

  override fun getSpecificValue(dim: String, vararg path: String): Number {
    if (dim.equals(field) == false) {
      throw IllegalArgumentException(
          "invalid dim \"" + dim + "\"; should be \"" + field + "\"")
    }
    if (path.size != 1) {
      throw IllegalArgumentException("path must be length=1")
    }
    var ord = docValues.lookupTerm(BytesRef(path[0])).toInt()
    if (ord < 0) {
      return -1
    }

    if (sparseCounts != null) {
      return sparseCounts[ord] ?: 0
    }
    if (denseCounts != null) {
      return denseCounts!![ord]
    }
    return 0
  }

  override fun getAllDims(topN: Int): List<FacetResult> {
    validateTopN(topN)
    return listOf(getTopChildren(topN, field))
  }

  private fun getDocValues(): SortedSetDocValues {

    val leaves = reader.leaves()
    var leafCount = leaves.size

    if (leafCount == 0) {
      return DocValues.emptySortedSet()
    }

    if (leafCount == 1) {
      return DocValues.getSortedSet(leaves.get(0).reader(), field)
    }

    // A good bit of this logic is forked from MultiDocValues so we can re-use an ordinal map
    val docValues = arrayOfNulls<SortedSetDocValues>(leafCount)
    val starts = IntArray(leafCount + 1)
    var cost = 0L
    for (i in 0 until leafCount) {
      val context = leaves.get(i)
      val dv = DocValues.getSortedSet(context.reader(), field)
      docValues[i] = dv
      starts[i] = context.docBase
      cost += dv.cost()
    }
    starts[leafCount] = reader.maxDoc()

    return MultiDocValues.MultiSortedSetDocValues(docValues.requireNoNulls(), starts, ordinalMap!!, cost)
  }

  private fun count(facetsCollector: FacetsCollector): Unit {

    val matchingDocs = facetsCollector.getMatchingDocs()

    if (matchingDocs.isEmpty()) {
      return
    }

    if (matchingDocs.size == 1) {

      val hits = matchingDocs.get(0)
      if (hits.totalHits == 0) {
        return
      }

      // Validate state before doing anything else:
      validateState(hits.context)

      // Assuming the state is valid, ordinalMap should be null since we have one segment:
      assert(ordinalMap == null)

      countOneSegment(docValues, hits.context.ord, hits, null)
    } else {

      // Validate state before doing anything else. We only check the first segment since they
      // should all ladder up to the same top-level reader:
      validateState(matchingDocs[0].context)

      for (hits in matchingDocs) {
        // Assuming the state is valid, ordinalMap should be non-null and docValues should be
        // a MultiSortedSetDocValues since we have more than one segment:
        assert(ordinalMap != null)
        assert(docValues is MultiDocValues.MultiSortedSetDocValues)

        if (hits.totalHits == 0) {
          continue
        }

        val multiValues = docValues as MultiDocValues.MultiSortedSetDocValues

        countOneSegment(multiValues.values[hits.context.ord], hits.context.ord, hits, null)
      }
    }
  }

  private fun countAll(): Unit {

    val leaves = reader.leaves()
    var numLeaves = leaves.size
    if (numLeaves == 0) {
      return
    }

    if (numLeaves == 1) {
      // Since we have a single segment, ordinalMap should be null:
      assert(ordinalMap == null)

      val context = leaves.get(0)
      val liveDocs = context.reader().liveDocs
      if (liveDocs == null) {
        countOneSegmentNHLD(docValues, context.ord)
      } else {
        countOneSegment(docValues, context.ord, null, liveDocs)
      }
    } else {
      // Since we have more than one segment, we should have a non-null ordinalMap and docValues
      // should be a MultiSortedSetDocValues instance:
      assert(ordinalMap != null)
      assert(docValues is MultiDocValues.MultiSortedSetDocValues)

      val multiValues = docValues as MultiDocValues.MultiSortedSetDocValues

      for (i in 0 until numLeaves) {
        val context = leaves.get(i)
        val liveDocs = context.reader().liveDocs
        if (liveDocs == null) {
          countOneSegmentNHLD(multiValues.values[i], context.ord)
        } else {
          countOneSegment(multiValues.values[i], context.ord, null, liveDocs)
        }
      }
    }
  }

  private fun countOneSegment(multiValues: SortedSetDocValues, segmentOrd: Int, hits: FacetsCollector.MatchingDocs?, liveDocs: Bits?): Unit {
    if (initialized == false) {
      assert(denseCounts == null && sparseCounts == null)
      // If the counters weren't initialized, we can assume the cardinality is low enough that
      // dense counting will be preferrable:
      denseCounts = IntArray(cardinality)
      initialized = true
    }

    // It's slightly more efficient to work against SortedDocValues if the field is actually
    // single-valued (see: LUCENE-5309)
    val singleValues = DocValues.unwrapSingleton(multiValues)
    val valuesIt: DocIdSetIterator = singleValues ?: multiValues

    // Intersect hits with doc values unless we're "counting all," in which case we'll iterate
    // all doc values for this segment:
    val it: DocIdSetIterator
    if (hits == null) {
      assert(liveDocs != null)
      it = FacetUtils.liveDocsDISI(valuesIt, liveDocs!!)
    } else {
      it = ConjunctionUtils.intersectIterators(mutableListOf(hits.bits.iterator()!!, valuesIt))
    }

    // TODO: yet another option is to count all segs
    // first, only in seg-ord space, and then do a
    // merge-sort-PQ in the end to only "resolve to
    // global" those seg ords that can compete, if we know
    // we just want top K?  ie, this is the same algo
    // that'd be used for merging facets across shards
    // (distributed faceting).  but this has much higher
    // temp ram req'ts (sum of number of ords across all
    // segs)
    if (ordinalMap == null) {
      // If there's no ordinal map we don't need to map segment ordinals to globals, so counting
      // is very straight-forward:
      if (singleValues != null) {
        while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          increment(singleValues.ordValue())
          totalDocCount++
        }
      } else {
        while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          var countedDocInTotal = false
          for (i in 0 until multiValues.docValueCount()) {
            var term = multiValues.nextOrd().toInt()
            increment(term)
            if (countedDocInTotal == false) {
              totalDocCount++
              countedDocInTotal = true
            }
          }
        }
      }
    } else {
      // We need to map segment ordinals to globals. We have two different approaches to this
      // depending on how many hits we have to count relative to how many unique doc val ordinals
      // there are in this segment:
      val ordMap = ordinalMap.getGlobalOrds(segmentOrd)
      var segmentCardinality = multiValues.valueCount.toInt()

      if (hits != null && hits.totalHits < segmentCardinality / 10) {
        // Remap every ord to global ord as we iterate:
        if (singleValues != null) {
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            increment(ordMap.get(singleValues.ordValue().toLong()).toInt())
            totalDocCount++
          }
        } else {
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            var countedDocInTotal = false
            for (i in 0 until multiValues.docValueCount()) {
              var term = multiValues.nextOrd().toInt()
              increment(ordMap.get(term.toLong()).toInt())
              if (countedDocInTotal == false) {
                totalDocCount++
                countedDocInTotal = true
              }
            }
          }
        }
      } else {
        // First count in seg-ord space.
        // At this point, we're either counting all ordinals or our heuristic suggests that
        // we expect to visit a large percentage of the unique ordinals (lots of hits relative
        // to the segment cardinality), so we count the segment densely:
        val segCounts = IntArray(segmentCardinality)
        if (singleValues != null) {
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            segCounts[singleValues.ordValue()]++
            totalDocCount++
          }
        } else {
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            var countedDocInTotal = false
            for (i in 0 until multiValues.docValueCount()) {
              var term = multiValues.nextOrd().toInt()
              segCounts[term]++
              if (countedDocInTotal == false) {
                totalDocCount++
                countedDocInTotal = true
              }
            }
          }
        }

        // Then, migrate to global ords:
        for (ord in 0 until segmentCardinality) {
          var count = segCounts[ord]
          if (count != 0) {
            increment(ordMap.get(ord.toLong()).toInt(), count)
          }
        }
      }
    }
  }

  // Variant of countOneSegment, that has No Hits or Live Docs
  private fun countOneSegmentNHLD(multiValues: SortedSetDocValues, segmentOrd: Int): Unit {

    // It's slightly more efficient to work against SortedDocValues if the field is actually
    // single-valued (see: LUCENE-5309)
    val singleValues = DocValues.unwrapSingleton(multiValues)

    if (ordinalMap == null) {
      // If there's no ordinal map we don't need to map segment ordinals to globals, so counting
      // is very straight-forward:
      if (singleValues != null) {
        while (singleValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          increment(singleValues.ordValue())
          totalDocCount++
        }
      } else {
        while (multiValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          var countedDocInTotal = false
          for (i in 0 until multiValues.docValueCount()) {
            var term = multiValues.nextOrd().toInt()
            increment(term)
            if (countedDocInTotal == false) {
              totalDocCount++
              countedDocInTotal = true
            }
          }
        }
      }
    } else {
      // We need to map segment ordinals to globals. We have two different approaches to this
      // depending on how many hits we have to count relative to how many unique doc val ordinals
      // there are in this segment:
      val ordMap = ordinalMap.getGlobalOrds(segmentOrd)
      var segmentCardinality = multiValues.valueCount.toInt()

      // First count in seg-ord space.
      // At this point, we're either counting all ordinals or our heuristic suggests that
      // we expect to visit a large percentage of the unique ordinals (lots of hits relative
      // to the segment cardinality), so we count the segment densely:
      val segCounts = IntArray(segmentCardinality)
      if (singleValues != null) {
        while (singleValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          segCounts[singleValues.ordValue()]++
          totalDocCount++
        }
      } else {
        while (multiValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          var countedDocInTotal = false
          for (i in 0 until multiValues.docValueCount()) {
            var term = multiValues.nextOrd().toInt()
            segCounts[term]++
            if (countedDocInTotal == false) {
              totalDocCount++
              countedDocInTotal = true
            }
          }
        }
      }

      // Then, migrate to global ords:
      for (ord in 0 until segmentCardinality) {
        var count = segCounts[ord]
        if (count != 0) {
          increment(ordMap.get(ord.toLong()).toInt(), count)
        }
      }
    }
  }

  private fun increment(ordinal: Int): Unit {
    increment(ordinal, 1)
  }

  private fun increment(ordinal: Int, amount: Int): Unit {
    if (sparseCounts != null) {
      sparseCounts[ordinal] = (sparseCounts[ordinal] ?: 0) + amount
    } else {
      denseCounts!![ordinal] += amount
    }
  }

  private fun validateState(context: LeafReaderContext): Unit {
    // LUCENE-5090: make sure the provided reader context "matches"
    // the top-level reader passed to the
    // SortedSetDocValuesReaderState, else cryptic
    // AIOOBE can happen:
    if (ReaderUtil.getTopLevelContext(context).reader() != reader) {
      throw IllegalStateException(
          "the SortedSetDocValuesReaderState provided to this class does not match the reader being searched; you must create a new SortedSetDocValuesReaderState every time you open a new IndexReader")
    }
  }

  private fun validateDimAndPathForGetChildren(dim: String, vararg path: String): Unit {
    if (dim.equals(field) == false) {
      throw IllegalArgumentException(
          "invalid dim \"" + dim + "\"; should be \"" + field + "\"")
    }
    if (path.size != 0) {
      throw IllegalArgumentException("path.size should be 0")
    }
  }
}
