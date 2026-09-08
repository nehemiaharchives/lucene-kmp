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

import org.gnit.lucenekmp.facet.FacetUtils
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollector.MatchingDocs
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.index.MultiDocValues.MultiSortedSetDocValues
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.jdkport.assert

/**
 * Compute facets counts from previously indexed [SortedSetDocValuesFacetField], without
 * require a separate taxonomy index. Faceting is a bit slower (~25%), and there is added cost on
 * every [IndexReader] open to create a new [SortedSetDocValuesReaderState].
 *
 * **NOTE**: this class should be instantiated and then used from a single thread, because it
 * holds a thread-private instance of [SortedSetDocValues].
 *
 * **NOTE**: tie-break is by unicode sort order
 *
 * **NOTE**: if you have multi-valued dims that require dim counts (see [FacetsConfig],
 * make sure to provide your `FacetsConfig` instance when instantiating [SortedSetDocValuesReaderState], or else dim counts can be inaccurate
 *
 * @lucene.experimental
 */
class SortedSetDocValuesFacetCounts : AbstractSortedSetDocValueFacetCounts {
  internal var counts: IntArray? = null

  /** Returns all facet counts, same result as searching on [MatchAllDocsQuery] but faster. */
  constructor(state: SortedSetDocValuesReaderState) : this(state, null)

  /** Counts all facet dimensions across the provided hits. */
  constructor(state: SortedSetDocValuesReaderState, hits: FacetsCollector?) : super(state) {
    if (hits == null) {
      // browse only
      countAll()
    } else {
      count(hits.getMatchingDocs())
    }
  }

  private fun initializeCounts() {
    if (counts == null) {
      counts = IntArray(state.size)
    }
  }

  override fun hasCounts(): Boolean {
    return counts != null
  }

  override fun getCount(ord: Int): Int {
    return counts!![ord]
  }

  // Variant of countOneSegment, that has No Hits or Live Docs
  private fun countOneSegmentNHLD(ordinalMap: OrdinalMap?, reader: LeafReader, segOrd: Int) {
    val multiValues = DocValues.getSortedSet(reader, field)
    if (multiValues == null) {
      // nothing to count
      return
    }

    // Initialize counts:
    initializeCounts()

    // It's slightly more efficient to work against SortedDocValues if the field is actually
    // single-valued (see: LUCENE-5309)
    val singleValues = DocValues.unwrapSingleton(multiValues)

    // TODO: yet another option is to count all segs
    // first, only in seg-ord space, and then do a
    // merge-sort-PQ in the end to only "resolve to
    // global" those seg ords that can compete, if we know
    // we just want top K?  ie, this is the same algo
    // that'd be used for merging facets across shards
    // (distributed faceting).  but this has much higher
    // temp ram req'ts (sum of number of ords across all
    // segs)
    if (ordinalMap != null) {
      val ordMap = ordinalMap.getGlobalOrds(segOrd)
      val numSegOrds = multiValues.valueCount.toInt()

      // First count in seg-ord space:
      val segCounts = IntArray(numSegOrds)
      if (singleValues != null) {
        while (singleValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          segCounts[singleValues.ordValue()]++
        }
      } else {
        while (multiValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          for (i in 0 until multiValues.docValueCount()) {
            val term = multiValues.nextOrd().toInt()
            segCounts[term]++
          }
        }
      }

      // Then, migrate to global ords:
      for (ord in 0 until numSegOrds) {
        val count = segCounts[ord]
        if (count != 0) {
          // ordinalMap.getGlobalOrd(segOrd, ord));
          counts!![ordMap.get(ord.toLong()).toInt()] += count
        }
      }
    } else {
      // No ord mapping (e.g., single segment index):
      // just aggregate directly into counts:
      if (singleValues != null) {
        while (singleValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          counts!![singleValues.ordValue()]++
        }
      } else {
        while (multiValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          for (i in 0 until multiValues.docValueCount()) {
            val term = multiValues.nextOrd().toInt()
            counts!![term]++
          }
        }
      }
    }
  }

  private fun countOneSegment(ordinalMap: OrdinalMap?, reader: LeafReader, segOrd: Int, hits: MatchingDocs?, liveDocs: Bits?) {
    if (hits != null && hits.totalHits == 0) {
      return
    }

    val multiValues = DocValues.getSortedSet(reader, field)
    if (multiValues == null) {
      // nothing to count
      return
    }

    // Initialize counts:
    initializeCounts()

    // It's slightly more efficient to work against SortedDocValues if the field is actually
    // single-valued (see: LUCENE-5309)
    val singleValues = DocValues.unwrapSingleton(multiValues)
    val valuesIt: DocIdSetIterator = singleValues ?: multiValues

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
    if (ordinalMap != null) {
      val ordMap = ordinalMap.getGlobalOrds(segOrd)

      val numSegOrds = multiValues.valueCount.toInt()

      if (hits != null && hits.totalHits < numSegOrds / 10) {
        // Remap every ord to global ord as we iterate:
        if (singleValues != null) {
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            counts!![ordMap.get(singleValues.ordValue().toLong()).toInt()]++
          }
        } else {
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            for (i in 0 until multiValues.docValueCount()) {
              val term = multiValues.nextOrd().toInt()
              counts!![ordMap.get(term.toLong()).toInt()]++
            }
          }
        }
      } else {
        // First count in seg-ord space:
        val segCounts = IntArray(numSegOrds)
        if (singleValues != null) {
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            segCounts[singleValues.ordValue()]++
          }
        } else {
          while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            for (i in 0 until multiValues.docValueCount()) {
              val term = multiValues.nextOrd().toInt()
              segCounts[term]++
            }
          }
        }

        // Then, migrate to global ords:
        for (ord in 0 until numSegOrds) {
          val count = segCounts[ord]
          if (count != 0) {
            // ordinalMap.getGlobalOrd(segOrd, ord));
            counts!![ordMap.get(ord.toLong()).toInt()] += count
          }
        }
      }
    } else {
      // No ord mapping (e.g., single segment index):
      // just aggregate directly into counts:
      if (singleValues != null) {
        while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          counts!![singleValues.ordValue()]++
        }
      } else {
        while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          for (i in 0 until multiValues.docValueCount()) {
            val term = multiValues.nextOrd().toInt()
            counts!![term]++
          }
        }
      }
    }
  }

  /** Does all the "real work" of tallying up the counts. */
  private fun count(matchingDocs: List<MatchingDocs>) {

    val ordinalMap: OrdinalMap?

    // TODO: is this right?  really, we need a way to
    // verify that this ordinalMap "matches" the leaves in
    // matchingDocs...
    if (dv is MultiSortedSetDocValues && matchingDocs.size > 1) {
      ordinalMap = dv.mapping
    } else {
      ordinalMap = null
    }

    val reader = state.reader

    for (hits in matchingDocs) {

      // LUCENE-5090: make sure the provided reader context "matches"
      // the top-level reader passed to the
      // SortedSetDocValuesReaderState, else cryptic
      // AIOOBE can happen:
      if (ReaderUtil.getTopLevelContext(hits.context).reader() != reader) {
        throw IllegalStateException(
            "the SortedSetDocValuesReaderState provided to this class does not match the reader being searched; you must create a new SortedSetDocValuesReaderState every time you open a new IndexReader")
      }

      countOneSegment(ordinalMap, hits.context.reader(), hits.context.ord, hits, null)
    }
  }

  /** Does all the "real work" of tallying up the counts. */
  private fun countAll() {

    val ordinalMap: OrdinalMap?

    // TODO: is this right?  really, we need a way to
    // verify that this ordinalMap "matches" the leaves in
    // matchingDocs...
    if (dv is MultiSortedSetDocValues) {
      ordinalMap = dv.mapping
    } else {
      ordinalMap = null
    }

    for (context in state.reader.leaves()) {

      val liveDocs = context.reader().liveDocs
      if (liveDocs == null) {
        countOneSegmentNHLD(ordinalMap, context.reader(), context.ord)
      } else {
        countOneSegment(ordinalMap, context.reader(), context.ord, null, liveDocs)
      }
    }
  }
}
