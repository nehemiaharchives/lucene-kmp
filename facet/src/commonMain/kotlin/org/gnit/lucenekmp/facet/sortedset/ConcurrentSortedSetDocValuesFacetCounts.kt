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

import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicIntArray
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.facet.FacetUtils
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollector.MatchingDocs
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.index.MultiDocValues.MultiSortedSetDocValues
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.Future
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.ExecutorService

/**
 * Like [SortedSetDocValuesFacetCounts], but aggregates counts concurrently across segments.
 *
 * @lucene.experimental
 */
@OptIn(ExperimentalAtomicApi::class)
class ConcurrentSortedSetDocValuesFacetCounts : AbstractSortedSetDocValueFacetCounts {

  internal val exec: ExecutorService
  internal val counts: AtomicIntArray

  /** Returns all facet counts, same result as searching on [MatchAllDocsQuery] but faster. */
  constructor(state: SortedSetDocValuesReaderState, exec: ExecutorService) : this(state, null, exec)

  /** Counts all facet dimensions across the provided hits. */
  constructor(state: SortedSetDocValuesReaderState, hits: FacetsCollector?, exec: ExecutorService) : super(state) {
    this.exec = exec
    counts = AtomicIntArray(state.size)
    if (hits == null) {
      // browse only
      countAll()
    } else {
      count(hits.getMatchingDocs())
    }
  }

  override fun hasCounts(): Boolean {
    // TODO: safe to always assume there are counts, but maybe it would be more optimal to
    // actually track if we see a count?
    return true
  }

  override fun getCount(ord: Int): Int {
    return counts.loadAt(ord)
  }

  private inner class CountOneSegment(
    val leafReader: LeafReader,
    val hits: MatchingDocs?,
    val ordinalMap: OrdinalMap?,
    val segOrd: Int
  ) : Callable<Unit?> {
    override fun call(): Unit? {
      // If we're counting collected hits but there were none, short-circuit:
      if (hits != null && hits.totalHits == 0) {
        return null
      }

      val multiValues = DocValues.getSortedSet(leafReader, field)
      if (multiValues == null) {
        // nothing to count here
        return null
      }

      // It's slightly more efficient to work against SortedDocValues if the field is actually
      // single-valued (see: LUCENE-5309)
      val singleValues = DocValues.unwrapSingleton(multiValues)
      val valuesIt: DocIdSetIterator = singleValues ?: multiValues

      // TODO: yet another option is to count all segs
      // first, only in seg-ord space, and then do a
      // merge-sort-PQ in the end to only "resolve to
      // global" those seg ords that can compete, if we know
      // we just want top K?  ie, this is the same algo
      // that'd be used for merging facets across shards
      // (distributed faceting).  but this has much higher
      // temp ram req'ts (sum of number of ords across all
      // segs)
      val it: DocIdSetIterator
      if (hits == null) {
        // count all
        // Initializing liveDocs bits in the constructor leads to a situation where liveDocs bits
        // get initialized in the calling thread but get used in a different thread leading to an
        // AssertionError. See LUCENE-10134
        val liveDocs = leafReader.liveDocs
        it = if (liveDocs != null) FacetUtils.liveDocsDISI(valuesIt, liveDocs) else valuesIt
      } else {
        it = ConjunctionUtils.intersectIterators(mutableListOf(hits.bits.iterator()!!, valuesIt))
      }

      if (ordinalMap != null) {
        val ordMap = ordinalMap.getGlobalOrds(segOrd)

        val numSegOrds = multiValues.valueCount.toInt()

        if (hits != null && hits.totalHits < numSegOrds / 10) {
          // Remap every ord to global ord as we iterate:
          if (singleValues != null) {
            if (singleValues == it) {
              while (singleValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                counts.fetchAndAddAt(ordMap.get(singleValues.ordValue().toLong()).toInt(), 1)
              }
            } else {
              while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                counts.fetchAndAddAt(ordMap.get(singleValues.ordValue().toLong()).toInt(), 1)
              }
            }
          } else {
            if (multiValues == it) {
              while (multiValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                for (i in 0 until multiValues.docValueCount()) {
                  val term = multiValues.nextOrd().toInt()
                  counts.fetchAndAddAt(ordMap.get(term.toLong()).toInt(), 1)
                }
              }
            } else {
              while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                for (i in 0 until multiValues.docValueCount()) {
                  val term = multiValues.nextOrd().toInt()
                  counts.fetchAndAddAt(ordMap.get(term.toLong()).toInt(), 1)
                }
              }
            }
          }
        } else {

          // First count in seg-ord space:
          val segCounts = IntArray(numSegOrds)
          if (singleValues != null) {
            if (singleValues == it) {
              while (singleValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                segCounts[singleValues.ordValue()]++
              }
            } else {
              while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                segCounts[singleValues.ordValue()]++
              }
            }
          } else {
            if (multiValues == it) {
              while (multiValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                for (i in 0 until multiValues.docValueCount()) {
                  val term = multiValues.nextOrd().toInt()
                  segCounts[term]++
                }
              }
            } else {
              while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                for (i in 0 until multiValues.docValueCount()) {
                  val term = multiValues.nextOrd().toInt()
                  segCounts[term]++
                }
              }
            }
          }

          // Then, migrate to global ords:
          for (ord in 0 until numSegOrds) {
            val count = segCounts[ord]
            if (count != 0) {
              counts.fetchAndAddAt(ordMap.get(ord.toLong()).toInt(), count)
            }
          }
        }
      } else {
        // No ord mapping (e.g., single segment index):
        // just aggregate directly into counts:
        if (singleValues != null) {
          if (singleValues == it) {
            while (singleValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
              counts.fetchAndAddAt(singleValues.ordValue(), 1)
            }
          } else {
            while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
              counts.fetchAndAddAt(singleValues.ordValue(), 1)
            }
          }
        } else {
          if (multiValues == it) {
            while (multiValues.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
              for (i in 0 until multiValues.docValueCount()) {
                val term = multiValues.nextOrd().toInt()
                counts.fetchAndAddAt(term, 1)
              }
            }
          } else {
            while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
              for (i in 0 until multiValues.docValueCount()) {
                val term = multiValues.nextOrd().toInt()
                counts.fetchAndAddAt(term, 1)
              }
            }
          }
        }
      }

      return null
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
    val results = ArrayList<Future<Unit?>>()

    for (hits in matchingDocs) {
      // LUCENE-5090: make sure the provided reader context "matches"
      // the top-level reader passed to the
      // SortedSetDocValuesReaderState, else cryptic
      // AIOOBE can happen:
      if (ReaderUtil.getTopLevelContext(hits.context).reader() != reader) {
        throw IllegalStateException(
            "the SortedSetDocValuesReaderState provided to this class does not match the reader being searched; you must create a new SortedSetDocValuesReaderState every time you open a new IndexReader")
      }

      results.add(
          exec.submit(
              CountOneSegment(hits.context.reader(), hits, ordinalMap, hits.context.ord)))
    }

    for (result in results) {
      try {
        runBlocking { result.get() }
      } catch (ee: ExecutionException) {
        // Theoretically cause can be null; guard against that.
        val cause = ee.cause
        throw IOUtils.rethrowAlways(cause ?: ee)
      }
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

    val results = ArrayList<Future<Unit?>>()

    for (context in state.reader.leaves()) {
      results.add(
          exec.submit(CountOneSegment(context.reader(), null, ordinalMap, context.ord)))
    }

    for (result in results) {
      try {
        runBlocking { result.get() }
      } catch (ee: ExecutionException) {
        // Theoretically cause can be null; guard against that.
        val cause = ee.cause
        throw IOUtils.rethrowAlways(cause ?: ee)
      }
    }
  }
}
