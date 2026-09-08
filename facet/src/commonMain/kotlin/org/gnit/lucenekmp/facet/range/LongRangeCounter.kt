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
package org.gnit.lucenekmp.facet.range

import org.gnit.lucenekmp.jdkport.assert


/**
 * Counter for numeric ranges. Works for both single- and multi-valued cases (assuming you use it
 * correctly).
 *
 * Usage notes: When counting a document field that only has a single value, callers should call
 * addSingleValued() with the value. Whenever a document field has multiple values, callers should
 * call startMultiValuedDoc() at the beginning of processing the document, followed by
 * addMultiValued() with each value before finally calling endMultiValuedDoc() at the end of
 * processing the document. The call to endMultiValuedDoc() will respond with a boolean indicating
 * whether-or-not the specific document matched against at least one of the ranges being counted.
 * Finally, after processing all documents, the caller should call finish(). This final call will
 * ensure the contents of the user-provided `countBuffer` contains accurate counts (each index
 * corresponding to the provided `LongRange` in `ranges`). The final call to finish()
 * will also report how many additional documents did not match against any ranges. The combination
 * of the endMultiValuedDoc() boolean responses and the number reported by finish() communicates the
 * total number of missing documents. Note that the call to finish() will not report any documents
 * already reported missing by endMultiValuedDoc().
 */
internal abstract class LongRangeCounter(countBuffer: IntArray) {

  /** accumulated counts for all of the ranges */
  private val countBuffer = countBuffer
  // We'll populate the user-provided count buffer with range counts:


  /**
   * for multi-value docs, we keep track of the last elementary interval we've counted so we can use
   * that as a lower-bound when counting subsequent values. this takes advantage of the fact that
   * values within a given doc are sorted.
   */
  protected var multiValuedDocLastSeenElementaryInterval: Int = 0




  /** Start processing a new doc. It's unnecessary to call this for single-value cases. */
  open fun startMultiValuedDoc() {
    multiValuedDocLastSeenElementaryInterval = -1
  }

  /**
   * Finish processing a new doc. Returns whether-or-not the document contributed a count to at
   * least one range. It's unnecessary to call this for single-value cases.
   */
  abstract fun endMultiValuedDoc(): Boolean

  /** Count a single valued doc */
  open fun addSingleValued(v: Long) {

    // NOTE: this works too, but it's ~6% slower on a simple
    // test with a high-freq TermQuery w/ range faceting on
    // wikimediumall:
    /*
    int index = Arrays.binarySearch(boundaries, v);
    if (index < 0) {
      index = -index-1;
    }
    leafCounts[index]++;
    */

    // Binary search to find matched elementary range; we
    // are guaranteed to find a match because the last
    // boundary is Long.MAX_VALUE:

    var boundaries = boundaries()

    var lo = 0
    var hi = boundaries.size - 1
    while (true) {
      var mid = (lo + hi) ushr 1
      if (v <= boundaries[mid]) {
        if (mid == 0) {
          processSingleValuedHit(mid)
          return
        } else {
          hi = mid - 1
        }
      } else if (v > boundaries[mid + 1]) {
        lo = mid + 1
      } else {
        processSingleValuedHit(mid + 1)
        return
      }
    }
  }

  /** Count a multi-valued doc value */
  fun addMultiValued(v: Long) {

    if (rangeCount() == 0) {
      return // don't bother if there aren't any requested ranges
    }

    var boundaries = boundaries()

    // First check if we've "advanced" beyond the last elementary interval we counted for this doc.
    // If we haven't, there's no sense doing anything else:
    if (multiValuedDocLastSeenElementaryInterval != -1
        && v <= boundaries[multiValuedDocLastSeenElementaryInterval]) {
      return
    }

    // Also check if we've already counted the last elementary interval. If so, there's nothing
    // else to count for this doc:
    val nextCandidateElementaryInterval = multiValuedDocLastSeenElementaryInterval + 1
    if (nextCandidateElementaryInterval == boundaries.size) {
      return
    }

    // Binary search in the range of the next candidate interval up to the last interval:
    var lo = nextCandidateElementaryInterval
    var hi = boundaries.size - 1
    while (true) {
      var mid = (lo + hi) ushr 1
      if (v <= boundaries[mid]) {
        if (mid == nextCandidateElementaryInterval) {
          processMultiValuedHit(mid)
          multiValuedDocLastSeenElementaryInterval = mid
          return
        } else {
          hi = mid - 1
        }
      } else if (v > boundaries[mid + 1]) {
        lo = mid + 1
      } else {
        var idx = mid + 1
        processMultiValuedHit(idx)
        multiValuedDocLastSeenElementaryInterval = idx
        return
      }
    }
  }

  /**
   * Finish processing all documents. This will return the number of docs that didn't contribute to
   * any ranges (that weren't already reported when calling endMultiValuedDoc()).
   */
  abstract fun finish(): Int

  /** Provide boundary information for elementary intervals (max inclusive value per interval) */
  protected abstract fun boundaries(): LongArray

  /** Process a single-value "hit" against an elementary interval. */
  protected abstract fun processSingleValuedHit(elementaryIntervalNum: Int)

  /** Process a multi-value "hit" against an elementary interval. */
  protected abstract fun processMultiValuedHit(elementaryIntervalNum: Int)

  /** Increment the specified range by one. */
  protected fun increment(rangeNum: Int) {
    countBuffer[rangeNum]++
  }

  /** Increment the specified range by the specified count. */
  protected fun increment(rangeNum: Int, count: Int) {
    countBuffer[rangeNum] += count
  }

  /** Number of ranges requested by the caller. */
  protected fun rangeCount(): Int {
    return countBuffer.size
  }

  /** Determine whether-or-not any requested ranges overlap */


  protected class InclusiveRange(val start: Long, val end: Long) {
    init { assert(end >= start) }
    override fun toString(): String = "$start to $end"
  }

  companion object {
  fun create(ranges: Array<out LongRange>, countBuffer: IntArray): LongRangeCounter {
    if (hasOverlappingRanges(ranges)) {
      return OverlappingLongRangeCounter(ranges, countBuffer)
    } else {
      return ExclusiveLongRangeCounter(ranges, countBuffer)
    }
  }
  private fun hasOverlappingRanges(ranges: Array<out LongRange>): Boolean {
    if (ranges.size == 0) {
      return false
    }

    // Copy before sorting so we don't mess with the caller's original ranges:
    val sortedRanges = ranges.copyOf()
    sortedRanges.sortBy { it.min }

    var previousMax = sortedRanges[0].max
    for (i in 1 until sortedRanges.size) {
      // Ranges overlap if the next min is <= the previous max (note that LongRange models
      // closed ranges, so equal limit points are considered overlapping):
      if (sortedRanges[i].min <= previousMax) {
        return true
      }
      previousMax = sortedRanges[i].max
    }

    return false
  }
  }
}
