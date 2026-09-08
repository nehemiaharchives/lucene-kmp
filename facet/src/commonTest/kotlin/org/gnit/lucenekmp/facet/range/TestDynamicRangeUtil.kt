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

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestDynamicRangeUtil : LuceneTestCase() {
  @Test
  fun testComputeDynamicNumericRangesBasic() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = LongArray(1000)
    val weights = LongArray(1000)

    var totalWeight = 0L
    for (i in 0 until 1000) {
      values[i] = (i + 1).toLong()
      weights[i] = i.toLong()
      totalWeight += i
    }

    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(501, 125250L, 1L, 501L, 251.0))
    expectedRangeInfoList.add(
        DynamicRangeUtil.DynamicRangeInfo(207, 125028L, 502L, 708L, 605.0))
    expectedRangeInfoList.add(
        DynamicRangeUtil.DynamicRangeInfo(159, 125133L, 709L, 867L, 788.0))
    expectedRangeInfoList.add(
        DynamicRangeUtil.DynamicRangeInfo(133, 124089L, 868L, 1000L, 934.0))
    assertDynamicNumericRangeResults(values, weights, 4, totalWeight, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithSameValues() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    var totalWeight = 0L
    val values = LongArray(100)
    val weights = LongArray(100)
    for (i in 0 until 100) {
      values[i] = 50
      weights[i] = i.toLong()
      totalWeight += i
    }

    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(51, 1275L, 50L, 50L, 50.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(21, 1281L, 50L, 50L, 50.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(16, 1272L, 50L, 50L, 50.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(12, 1122L, 50L, 50L, 50.0))

    assertDynamicNumericRangeResults(values, weights, 4, totalWeight, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithSameWeights() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = LongArray(100)
    val weights = LongArray(100)
    for (i in 0 until 100) {
      values[i] = i.toLong()
      weights[i] = 50
    }

    // Supplying only equal weights should return ranges with equal counts - excluding the last
    // range
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 0L, 24L, 12.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 25L, 49L, 37.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 50L, 74L, 62.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 75L, 99L, 87.0))
    assertDynamicNumericRangeResults(values, weights, 4, 5000L, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithOneValue() {
    val values = longArrayOf(50)
    val weights = longArrayOf(1)
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()

    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(1, 1L, 50L, 50L, 50.0))
    assertDynamicNumericRangeResults(values, weights, 4, 1, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithOneLargeWeight() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = longArrayOf(45, 32, 52, 14, 455, 342, 53)
    val weights = longArrayOf(143, 23, 1, 52343, 53, 12, 2534)

    // value 14 has its own bin since the weight is large, and the rest of the values fall in the
    // other bin
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(1, 52343L, 14L, 14L, 14.0))
    expectedRangeInfoList.add(
        DynamicRangeUtil.DynamicRangeInfo(6, 2766L, 32L, 455L, 163.16666666666666))
    assertDynamicNumericRangeResults(values, weights, 4, 55109, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithLargeTopN() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = longArrayOf(487, 439, 794, 277)
    val weights = longArrayOf(59, 508, 736, 560)

    // More requested ranges (TopN) than values should return ranges with weights larger than the
    // average weight - excluding the last range
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(1, 560L, 277L, 277L, 277.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(1, 508L, 439L, 439L, 439.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(2, 795L, 487L, 794L, 640.5))
    assertDynamicNumericRangeResults(values, weights, 42, 1863L, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithZeroTopN() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = longArrayOf(487, 439, 794, 277)
    val weights = longArrayOf(59, 508, 736, 560)

    // Zero requested ranges (TopN) should return a empty list of ranges regardless of inputs
    assertDynamicNumericRangeResults(values, weights, 0, 1863L, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithSingleTopN() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = longArrayOf(487, 439, 794, 277)
    val weights = longArrayOf(59, 508, 736, 560)

    // A single requested range (TopN) should return a single range regardless of the weights
    // provided
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(4, 1863L, 277L, 794L, 499.25))
    assertDynamicNumericRangeResults(values, weights, 1, 1863L, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithTwoTopN() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = longArrayOf(487, 439, 794, 277)
    val weights = longArrayOf(59, 508, 736, 560)

    // Two requested ranges (TopN) should return two ranges where the first range's weight is equal
    // or larger than half of the total weight
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(2, 1068L, 277L, 439L, 358.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(2, 795L, 487L, 794L, 640.5))
    assertDynamicNumericRangeResults(values, weights, 2, 1863L, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithSameWeightsShuffled() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = LongArray(100)
    val weights = LongArray(100)
    for (i in 0 until 100) {
      values[i] = i.toLong()
      weights[i] = 50
    }

    // Shuffling the values and weights should not change the answer between runs
    // We expect that returned ranges should come in a strict, deterministic order
    // with the same values and weights
    shuffleValuesWeights(values, weights)
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 0L, 24L, 12.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 25L, 49L, 37.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 50L, 74L, 62.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 75L, 99L, 87.0))
    assertDynamicNumericRangeResults(values, weights, 4, 5000L, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithSameValuesShuffled() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    var totalWeight = 0L
    val values = LongArray(100)
    val weights = LongArray(100)
    for (i in 0 until 100) {
      values[i] = 50
      weights[i] = i.toLong()
      totalWeight += i
    }

    // Shuffling the values and weights should not change the answer between runs
    // We expect that returned ranges should come in a strict, deterministic order
    // with the same values and weights
    shuffleValuesWeights(values, weights)
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(51, 1275L, 50L, 50L, 50.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(21, 1281L, 50L, 50L, 50.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(16, 1272L, 50L, 50L, 50.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(12, 1122L, 50L, 50L, 50.0))

    assertDynamicNumericRangeResults(values, weights, 4, totalWeight, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithMisplacedValue() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = longArrayOf(
          1, 2, 11, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 12, 111, 112, 113, 114, 115
        )
    val weights = longArrayOf(
          2, 3, 12, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 13, 112, 113, 114, 115, 116
        )

    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(8, 444L, 1L, 104L, 54.5))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(4, 430L, 105L, 108L, 106.5))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(4, 446L, 109L, 112L, 110.5))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(3, 345L, 113L, 115L, 114.0))
    assertDynamicNumericRangeResults(values, weights, 4, 1665, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithSameWeightsOutOfOrder() {
    val expectedRangeInfoList = ArrayList<DynamicRangeUtil.DynamicRangeInfo>()
    val values = longArrayOf(
          20, 15, 59, 49, 13, 93, 72, 21, 36, 81, 57, 1, 90, 79, 16, 51, 7, 17, 25, 63, 12, 5, 83,
          66, 48, 43, 55, 78, 64, 77, 65, 73, 80, 37, 54, 50, 95, 31, 97, 3, 82, 29, 70, 26, 4, 46,
          34, 67, 87, 0, 30, 19, 41, 85, 84, 89, 8, 10, 22, 28, 6, 23, 88, 40, 33, 44, 18, 27, 69,
          38, 91, 98, 62, 14, 35, 2, 92, 47, 94, 75, 32, 99, 86, 71, 74, 24, 52, 96, 9, 58, 39, 76,
          56, 11, 53, 61, 42, 68, 60, 45
        )
    val weights = longArrayOf(
          50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
          50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
          50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
          50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
          50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50
        )

    // This is testComputeDynamicNumericRangesWithSameWeightsShuffled with seed
    // 9AE79D72C8DD56D8 that failed a previous test
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 0L, 24L, 12.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 25L, 49L, 37.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 50L, 74L, 62.0))
    expectedRangeInfoList.add(DynamicRangeUtil.DynamicRangeInfo(25, 1250L, 75L, 99L, 87.0))
    assertDynamicNumericRangeResults(values, weights, 4, 5000L, expectedRangeInfoList)
  }

  @Test
  fun testComputeDynamicNumericRangesWithRandomValues() {
    var arraySize = random().nextInt(100)
    val values = LongArray(arraySize)
    val weights = LongArray(arraySize)

    for (i in 0 until arraySize) {
      values[i] = random().nextLong(1000)
      weights[i] = random().nextLong(1000)
    }

    var topN = random().nextInt(100)

    var totalWeight = 0L
    for (weight in weights) {
      totalWeight += weight
    }

    assertDynamicNumericRangeValidProperties(values, weights, topN, totalWeight)
  }

  /** Implementation of Durstenfeld's algorithm for shuffling values and weights */
  private fun shuffleValuesWeights(values: LongArray, weights: LongArray) {
    for (i in values.size - 1 downTo 1) {
      var rdmIdx = random().nextInt(i + 1)
      val tmpValue = values[i]
      val tmpWeight = weights[i]
      values[i] = values[rdmIdx]
      weights[i] = weights[rdmIdx]
      values[rdmIdx] = tmpValue
      weights[rdmIdx] = tmpWeight
    }
  }

  private fun assertDynamicNumericRangeValidProperties(
      values: LongArray, weights: LongArray, topN: Int, totalWeight: Long) {

    val sortedPairs = ArrayList<WeightedPair>()
    for (i in 0 until values.size) {
      val value = values[i]
      val weight = weights[i]
      val pair = WeightedPair(value, weight)
      sortedPairs.add(pair)
    }

    sortedPairs.sortWith(compareBy<WeightedPair> { it.value }.thenBy { it.weight })

    var len = values.size

    val rangeWeightTarget = totalWeight.toDouble() / minOf(topN, len)

    val mockDynamicRangeResult =
        DynamicRangeUtil.computeDynamicNumericRanges(
            values, weights, values.size, totalWeight, topN)

    // Zero requested ranges (TopN) should return a empty list of ranges regardless of inputs
    if (topN == 0) {
      assertTrue(mockDynamicRangeResult.size == 0)
      return // Early return; do not check anything else
    }

    // Adjacent ranges do not overlap - only adjacent max-min can overlap
    for (i in 0 until mockDynamicRangeResult.size - 1) {
      val rangeInfo = mockDynamicRangeResult.get(i)
      val nextRangeInfo = mockDynamicRangeResult.get(i + 1)
      assertTrue(rangeInfo.max <= nextRangeInfo.min)
    }

    // The count of every range sums to the number of values
    var accuCount = 0
    for (i in 0 until mockDynamicRangeResult.size) {
      val rangeInfo = mockDynamicRangeResult.get(i)
      var count = rangeInfo.count
      accuCount += count
    }
    assertTrue(accuCount == len)

    // The sum of every range weight equals the total weight
    var accuWeight = 0L
    for (i in 0 until mockDynamicRangeResult.size) {
      val rangeInfo = mockDynamicRangeResult.get(i)
      val weight = rangeInfo.weight
      accuWeight += weight
    }
    assertTrue(accuWeight == totalWeight)

    // All values appear in atleast one range
    run {
    var pairOffset = 0
    for (rangeIdx in 0 until mockDynamicRangeResult.size) {
      val rangeInfo = mockDynamicRangeResult.get(rangeIdx)
      var count = rangeInfo.count
      for (i in pairOffset until pairOffset + count) {
        val pair = sortedPairs.get(i)
        val value = pair.value
        assertTrue(rangeInfo.min <= value && value <= rangeInfo.max)
      }
      pairOffset += count
    }
    }

    // The minimum/maximum of each range is actually the smallest/largest value
    run {
    var pairOffset = 0
    for (rangeIdx in 0 until mockDynamicRangeResult.size) {
      val rangeInfo = mockDynamicRangeResult.get(rangeIdx)
      var count = rangeInfo.count
      val minPair = sortedPairs.get(pairOffset)
      val maxPair = sortedPairs.get(pairOffset + count - 1)
      val min = minPair.value
      val max = maxPair.value
      assertTrue(rangeInfo.min == min)
      assertTrue(rangeInfo.max == max)
      pairOffset += count
    }
    }

    // Weights of each range is over the rangeWeightTarget - exclude last range
    for (i in 0 until mockDynamicRangeResult.size - 1) {
      val rangeInfo = mockDynamicRangeResult.get(i)
      assertTrue(rangeInfo.weight >= rangeWeightTarget)
    }

    // Removing the last weight from a range brings it under the rangeWeightTarget - exclude last
    // range
    run {
    var pairOffset = 0
    for (rangeIdx in 0 until mockDynamicRangeResult.size - 1) {
      val rangeInfo = mockDynamicRangeResult.get(rangeIdx)
      var count = rangeInfo.count
      val lastPair = sortedPairs.get(pairOffset + count - 1)
      val lastWeight = lastPair.weight
      pairOffset += count
      assertTrue(rangeInfo.weight - lastWeight < rangeWeightTarget)
    }
    }

    // Centroids for each range are correct
    run {
    var pairOffset = 0
    for (rangeIdx in 0 until mockDynamicRangeResult.size) {
      val rangeInfo = mockDynamicRangeResult.get(rangeIdx)
      var count = rangeInfo.count
      var accuValue = 0L
      for (i in pairOffset until pairOffset + count) {
        val pair = sortedPairs.get(i)
        val value = pair.value
        accuValue += value
      }
      pairOffset += count
      assertTrue(rangeInfo.centroid == (accuValue.toDouble() / count))
    }
    }
  }

  private fun assertDynamicNumericRangeResults(
      values: LongArray,
      weights: LongArray,
      topN: Int,
      totalWeight: Long,
      expectedDynamicRangeResult: List<DynamicRangeUtil.DynamicRangeInfo>) {
    val mockDynamicRangeResult =
        DynamicRangeUtil.computeDynamicNumericRanges(
            values, weights, values.size, totalWeight, topN)
    assertEquals(expectedDynamicRangeResult, mockDynamicRangeResult)
  }

  /**
   * Holds parameters of a weighted pair.
   *
   * @param value the value of the pair
   * @param weight the weight of the pair
   */
  private data class WeightedPair(val value: Long, val weight: Long)
}
