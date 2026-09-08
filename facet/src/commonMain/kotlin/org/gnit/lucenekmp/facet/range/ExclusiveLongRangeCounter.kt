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
 * This implementation assumes the requested ranges <i>do not</i> overlap. With this assumption,
 * we're able to take a simpler approach to accumulating range counts by just binary searching for
 * the appropriate range and counting directly as each value comes in.
 */
internal class ExclusiveLongRangeCounter(ranges: Array<out LongRange>, countBuffer: IntArray) : LongRangeCounter(countBuffer) {

  /** elementary interval boundaries used for efficient counting (bsearch to find interval) */
  private val boundaries: LongArray

  /** original range number each elementary interval corresponds to (index into countBuffer) */
  private val rangeNums: IntArray

  /** number of counted documents that haven't matched any requested ranges */
  private var missingCount: Int = 0

  /** whether-or-not the multi-valued doc currently being counted has matched any ranges */
  private var multiValuedDocMatchedRange: Boolean = false

  init {

    // Create a copy of the requested ranges, sorted by min, and keeping track of the original
    // position:
    val sortedRanges = Array(ranges.size) { i -> LongRangeAndPos(ranges[i], i) }
    sortedRanges.sortBy { it.range.min }

    // Create elementary intervals, which include requested ranges and "gaps" in-between:
    val elementaryIntervals = buildElementaryIntervals(sortedRanges)

    // Keep track of elementary interval boundary ends (for bsearching) along with the requested
    // range they map back to (and -1 when they map to a "gap" range):
    boundaries = LongArray(elementaryIntervals.size)
    rangeNums = IntArray(elementaryIntervals.size)
    rangeNums.fill(-1)
    var currRange = 0
    for (i in 0 until boundaries.size) {
      boundaries[i] = elementaryIntervals.get(i).end
      if (currRange < sortedRanges.size) {
        var curr = sortedRanges[currRange]
        if (boundaries[i] == curr.range.max) {
          rangeNums[i] = curr.pos
          currRange++
        }
      }
    }
  }

  override   fun startMultiValuedDoc() {
    super.startMultiValuedDoc()
    multiValuedDocMatchedRange = false
  }

  override   fun endMultiValuedDoc(): Boolean {
    return multiValuedDocMatchedRange
  }

  override   fun addSingleValued(v: Long) {
    if (rangeCount() == 0) {
      missingCount++
      return
    }

    super.addSingleValued(v)
  }

  override   fun finish(): Int {
    // Nothing much to do in this case since we're able to count directly into the requested
    // ranges as we go in this implementation. Just report any missing count:
    return missingCount
  }

  override   protected fun boundaries(): LongArray {
    return boundaries
  }

  override   protected fun processSingleValuedHit(elementaryIntervalNum: Int) {
    var rangeNum = rangeNums[elementaryIntervalNum]
    if (rangeNum != -1) {
      // The elementary interval we matched against corresponds to a requested
      // range, so increment it:
      increment(rangeNum)
    } else {
      // The matched elementary interval is a "gap" range, so the doc isn't
      // present in any requested ranges:
      missingCount++
    }
  }

  override   protected fun processMultiValuedHit(elementaryIntervalNum: Int) {
    var rangeNum = rangeNums[elementaryIntervalNum]
    if (rangeNum != -1) {
      // The elementary interval we matched against corresponds to a requested
      // range, so increment it. We can do this without fear of double-counting
      // since we know the requested ranges don't overlap:
      increment(rangeNum)
      multiValuedDocMatchedRange = true
    }
  }

  /**
   * Create elementary intervals, which include requested ranges and "gaps" in-between. This logic
   * assumes no requested ranges overlap, and that the incoming ranges have already been sorted.
   */


  /** Simple container for a requested range and its original position */
  private data class LongRangeAndPos(val range: LongRange, val pos: Int)

  companion object {
  private fun buildElementaryIntervals(sortedRanges: Array<LongRangeAndPos>): List<InclusiveRange> {
    var elementaryIntervals : MutableList<InclusiveRange> = ArrayList()
    var prev = Long.MIN_VALUE
    for (range in sortedRanges) {
      if (range.range.min > prev) {
        // add a "gap" range preceding requested range if necessary:
        elementaryIntervals.add(InclusiveRange(prev, range.range.min - 1))
      }
      // add the requested range:
      elementaryIntervals.add(InclusiveRange(range.range.min, range.range.max))
      prev = range.range.max + 1
    }
    if (elementaryIntervals.isEmpty() == false) {
      var lastEnd = elementaryIntervals.get(elementaryIntervals.size - 1).end
      if (lastEnd < Long.MAX_VALUE) {
        elementaryIntervals.add(InclusiveRange(lastEnd + 1, Long.MAX_VALUE))
      }
    } else {
      // If no ranges were requested, create a single entry from MIN_VALUE to MAX_VALUE:
      elementaryIntervals.add(InclusiveRange(Long.MIN_VALUE, Long.MAX_VALUE))
    }

    return elementaryIntervals
  }
  }
}
