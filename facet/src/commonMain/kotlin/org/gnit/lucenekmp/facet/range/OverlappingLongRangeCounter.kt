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

import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.internal.hppc.IntCursor
import org.gnit.lucenekmp.internal.hppc.LongArrayList
import org.gnit.lucenekmp.internal.hppc.LongIntHashMap
import org.gnit.lucenekmp.util.FixedBitSet

/**
 * This implementation supports requested ranges that overlap. Because of this, we use a
 * segment-tree to more efficiently aggregate counts into ranges at the end of processing. We also
 * need to worry about double-counting issues since it's possible that multiple elementary
 * intervals, although mutually-exclusive, can roll-up to the same requested range. This creates
 * some complexity with how we need to handle multi-valued documents.
 */
internal class OverlappingLongRangeCounter(ranges: Array<out LongRange>, countBuffer: IntArray) : LongRangeCounter(countBuffer) {

  /** segment tree root node */
  private val root: LongRangeNode

  /** elementary interval boundaries used for efficient counting (bsearch to find interval) */
  private val boundaries: LongArray

  /**
   * whether-or-not there are elementary interval counts that still need to be rolled up at the end
   */
  private var hasUnflushedCounts: Boolean = false

  // Needed only for counting single-valued docs:
  /** counts seen in each elementary interval */
  private var singleValuedElementaryIntervalCounts: IntArray? = null

  // Needed only for counting multi-valued docs:
  /** whether-or-not an elementary interval has seen at least one match for a single doc */
  private var multiValuedDocElementaryIntervalHits: FixedBitSet? = null

  /** whether-or-not a requested range has seen at least one match for a single doc */
  private var multiValuedDocRangeHits: FixedBitSet? = null

  // Used during rollup
  private var elementaryIntervalUpto: Int = 0

  /** number of counted documents that haven't matched any requested ranges */
  private var missingCount: Int = 0

  init {

    // Build elementary intervals:
    val elementaryIntervals = buildElementaryIntervals(ranges)

    // Build binary tree on top of intervals:
    root = split(0, elementaryIntervals.size, elementaryIntervals)

    // Set outputs, so we know which range to output for each node in the tree:
    for (i in 0 until ranges.size) {
      root.addOutputs(i, ranges[i])
    }

    // Keep track of elementary interval max boundaries for bsearch:
    boundaries = LongArray(elementaryIntervals.size)
    for (i in 0 until boundaries.size) {
      boundaries[i] = elementaryIntervals.get(i).end
    }
  }

  override   fun startMultiValuedDoc() {
    super.startMultiValuedDoc()
    // Lazy init a bitset to track the elementary intervals we see of a multi-valued doc:
    if (multiValuedDocElementaryIntervalHits == null) {
      multiValuedDocElementaryIntervalHits = FixedBitSet(boundaries.size)
    } else {
      multiValuedDocElementaryIntervalHits!!.clear()
    }
  }

  override   fun endMultiValuedDoc(): Boolean {
    assert(multiValuedDocElementaryIntervalHits != null) { "must call startDoc() first" }

    // Short-circuit if the caller didn't specify any ranges to count:
    if (rangeCount() == 0) {
      return false
    }

    // Do the rollup for this doc:

    // Lazy init a bitset to track the requested ranges seen for this multi-valued doc:
    if (multiValuedDocRangeHits == null) {
      multiValuedDocRangeHits = FixedBitSet(rangeCount())
    } else {
      multiValuedDocRangeHits!!.clear()
    }
    elementaryIntervalUpto = 0
    rollupMultiValued(root)

    // Actually increment the count for each matching range, and see if the doc contributed to
    // at least one:
    var docContributedToAtLeastOneRange = false
    var i = multiValuedDocRangeHits!!.nextSetBit(0)
    while (i < multiValuedDocRangeHits!!.length()) {
      increment(i)
      docContributedToAtLeastOneRange = true
      if (++i < multiValuedDocRangeHits!!.length()) {
        i = multiValuedDocRangeHits!!.nextSetBit(i)
      }
    }

    return docContributedToAtLeastOneRange
  }

  override   fun finish(): Int {
    if (hasUnflushedCounts) {
      // Rollup any outstanding counts from single-valued cases:
      missingCount = 0
      elementaryIntervalUpto = 0
      rollupSingleValued(root, false)

      return missingCount
    } else {
      return 0
    }
  }

  override   protected fun boundaries(): LongArray {
    return boundaries
  }

  override   protected fun processSingleValuedHit(elementaryIntervalNum: Int) {
    // Lazy init:
    if (singleValuedElementaryIntervalCounts == null) {
      singleValuedElementaryIntervalCounts = IntArray(boundaries.size)
    }

    singleValuedElementaryIntervalCounts!![elementaryIntervalNum]++
    hasUnflushedCounts = true
  }

  override   protected fun processMultiValuedHit(elementaryIntervalNum: Int) {
    assert(multiValuedDocElementaryIntervalHits != null) { "must call startDoc() first" }
    multiValuedDocElementaryIntervalHits!!.set(elementaryIntervalNum)
  }



  /**
   * Rolls up all the single-valued doc counts. Note that this is done once at the end of processing
   * all documents (as part of [finish]. This is done in bulk at the end for efficiency
   * purposes (vs. after ever document). This works only for cases where documents have a
   * single-value. Multi-valued docs need to get rolled up after each document to ensure there's no
   * double-counting (see [rollupMultiValued])
   */
  private fun rollupSingleValued(node: LongRangeNode, sawOutputs: Boolean): Int {
    var sawOutputs = sawOutputs
    var count: Int = 0
    sawOutputs = sawOutputs or (node.outputs != null)
    if (node.left != null) {
      count = rollupSingleValued(node.left, sawOutputs)
      count += rollupSingleValued(node.right!!, sawOutputs)
    } else {
      // Leaf:
      count = singleValuedElementaryIntervalCounts!![elementaryIntervalUpto]
      elementaryIntervalUpto++
      if (sawOutputs == false) {
        // This is a missing count (no output ranges were seen "above" us):
        missingCount += count
      }
    }
    if (node.outputs != null) {
      for (rangeIndex in node.outputs!!) {
        increment(rangeIndex!!.value, count)
      }
    }

    return count
  }

  /**
   * Rolls up all the multi-valued doc counts. Note that this is done at the end of each document
   * (as part of [endMultiValuedDoc]). All of the counts contributed by a single document
   * get rolled up into the appropriate ranges in this step. It must be done after each document so
   * that counts don't get double-counted, and so we know whether-or-not an individual doc actually
   * contributed to any of the user-requested ranges.
   */
  private fun rollupMultiValued(node: LongRangeNode): Boolean {
    var containedHit: Boolean = false
    if (node.left != null) {
      containedHit = rollupMultiValued(node.left)
      containedHit = containedHit or rollupMultiValued(node.right!!)
    } else {
      // Leaf:
      containedHit = multiValuedDocElementaryIntervalHits!!.get(elementaryIntervalUpto)
      elementaryIntervalUpto++
    }
    if (containedHit && node.outputs != null) {
      for (rangeIndex in node.outputs!!) {
        multiValuedDocRangeHits!!.set(rangeIndex!!.value)
      }
    }

    return containedHit
  }



  /** Holds one node of the segment tree. */
  class LongRangeNode(
// Our range, inclusive:
    val start: Long,
    val end: Long,
    val left: LongRangeNode?,
    val right: LongRangeNode?,
    val elementaryIntervalIndex: Int
  ) {
    // Our range, inclusive: start and end.
    // If we are a leaf, the index into elementary ranges that we point to:
    // Which range indices to output when a query goes
    // through this node:
    var outputs: IntArrayList? = null

    override     public fun toString(): String {
      val sb = StringBuilder()
      toString(sb, 0)
      return sb.toString()
    }

    fun indent(sb: StringBuilder, depth: Int) {
      for (i in 0 until depth) {
        sb.append("  ")
      }
    }

    /** Recursively assigns range outputs to each node. */
    fun addOutputs(index: Int, range: LongRange) {
      if (start >= range.min && end <= range.max) {
        // Our range is fully included in the incoming
        // range; add to our output list:
        if (outputs == null) {
          outputs = IntArrayList()
        }
        outputs!!.add(index)
      } else if (left != null) {
        assert(right != null)
        // Recurse:
        left.addOutputs(index, range)
        right!!.addOutputs(index, range)
      }
    }

    fun toString(sb: StringBuilder, depth: Int) {
      indent(sb, depth)
      if (left == null) {
        assert(right == null)
        sb.append("leaf: ").append(start).append(" to ").append(end)
      } else {
        sb.append("node: ").append(start).append(" to ").append(end)
      }
      if (outputs != null) {
        sb.append(" outputs=")
        sb.append(outputs)
      }
      sb.append('\n')

      if (left != null) {
        assert(right != null)
        left.toString(sb, depth + 1)
        right!!.toString(sb, depth + 1)
      }
    }
  }

  companion object {
  private fun split(start: Int, end: Int, elementaryIntervals: List<InclusiveRange>): LongRangeNode {
    if (start == end - 1) {
      // leaf
      var range = elementaryIntervals.get(start)
      return LongRangeNode(range.start, range.end, null, null, start)
    } else {
      var mid = (start + end) ushr 1
      var left = split(start, mid, elementaryIntervals)
      var right = split(mid, end, elementaryIntervals)
      return LongRangeNode(left.start, right.end, left, right, -1)
    }
  }
  private fun buildElementaryIntervals(ranges: Array<out LongRange>): List<InclusiveRange> {
    // Maps all range inclusive endpoints to int flags; 1
    // = start of interval, 2 = end of interval.  We need to
    // track the start vs end case separately because if a
    // given point is both, then it must be its own
    // elementary interval:
    var endsMap = LongIntHashMap()

    endsMap.put(Long.MIN_VALUE, 1)
    endsMap.put(Long.MAX_VALUE, 2)

    for (range in ranges) {
      var index = endsMap.indexOf(range.min)
      if (index < 0) {
        endsMap.indexInsert(index, range.min, 1)
      } else {
        endsMap.indexReplace(index, endsMap.indexGet(index) or 1)
      }
      index = endsMap.indexOf(range.max)
      if (index < 0) {
        endsMap.indexInsert(index, range.max, 2)
      } else {
        endsMap.indexReplace(index, endsMap.indexGet(index) or 2)
      }
    }

    var endsList = LongArrayList(endsMap.size())
    endsList.addAll(endsMap.keys())
    endsList.buffer.sort(0, endsList.size())

    // Build elementaryIntervals (a 1D Venn diagram):
    var elementaryIntervals : MutableList<InclusiveRange> = ArrayList()
    var upto = 1
    var v = endsList.get(0)
    var prev: Long = 0
    if (endsMap.get(v) == 3) {
      elementaryIntervals.add(InclusiveRange(v, v))
      prev = v + 1
    } else {
      prev = v
    }

    while (upto < endsList.size()) {
      v = endsList.get(upto)
      var flags = endsMap.get(v)
      if (flags == 3) {
        // This point is both an end and a start; we need to
        // separate it:
        if (v > prev) {
          elementaryIntervals.add(InclusiveRange(prev, v - 1))
        }
        elementaryIntervals.add(InclusiveRange(v, v))
        prev = v + 1
      } else if (flags == 1) {
        // This point is only the start of an interval;
        // attach it to next interval:
        if (v > prev) {
          elementaryIntervals.add(InclusiveRange(prev, v - 1))
        }
        prev = v
      } else {
        assert(flags == 2)
        // This point is only the end of an interval; attach
        // it to last interval:
        elementaryIntervals.add(InclusiveRange(prev, v))
        prev = v + 1
      }
      upto++
    }

    return elementaryIntervals
  }
  }
}
