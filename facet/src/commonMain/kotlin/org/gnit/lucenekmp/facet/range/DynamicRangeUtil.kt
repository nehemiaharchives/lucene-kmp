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

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.LongValuesSource
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InPlaceMergeSorter
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.Future
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert

/**
 * Methods to create dynamic ranges for numeric fields.
 *
 * @lucene.experimental
 */
class DynamicRangeUtil private constructor() {
    companion object {
        /**
   * Construct dynamic ranges using the specified weight field to generate equi-weight range for the
   * specified numeric bin field
   *
   * @param weightFieldName Name of the specified weight field
   * @param weightValueSource Value source of the weight field
   * @param fieldValueSource Value source of the value field
   * @param facetsCollector FacetsCollector
   * @param topN Number of requested ranges
   * @param exec An executor service that is used to do the computation
   * @return A list of DynamicRangeInfo that contains count, relevance, min, max, and centroid for
   *     each range
   */
        fun computeDynamicRanges(
            weightFieldName: String,
            weightValueSource: LongValuesSource,
            fieldValueSource: LongValuesSource,
            facetsCollector: FacetsCollector,
            topN: Int,
            exec: ExecutorService
        ): List<DynamicRangeInfo>? {
            val matchingDocsList = facetsCollector.getMatchingDocs()
            val totalDoc = matchingDocsList.sumOf { it.totalHits }
            val values = LongArray(totalDoc)
            val weights = LongArray(totalDoc)
            var totalWeight = 0L
            var overallLength = 0

            val futures = ArrayList<Future<*>>()
            val tasks = ArrayList<SegmentTask>()
            for (matchingDocs in matchingDocsList) {
                if (matchingDocs.totalHits > 0) {
                    val segmentOutput = SegmentOutput(matchingDocs.totalHits)

                    // [1] retrieve values and associated weights concurrently
                    val task = SegmentTask(matchingDocs, fieldValueSource, weightValueSource, segmentOutput)
                    tasks.add(task)
                    futures.add(exec.submit(task))
                }
            }

            // [2] wait for all segment runs to finish
            for (future in futures) {
                try {
                    runBlocking { future.get() }
                } catch (ie: InterruptedException) {
                    throw RuntimeException(ie)
                } catch (ee: ExecutionException) {
                    IOUtils.rethrowAlways(ee.cause!!)
                }
            }

            // [3] merge the segment value and weight arrays into one array respectively and update the
            // total weights
            // and valid value length
            for (task in tasks) {
                val curSegmentOutput = task.segmentOutput
                // if segment total weight overflows, return null
                if (curSegmentOutput == null) {
                    return null
                }

                assert(curSegmentOutput.values.size == curSegmentOutput.weights.size)

                try {
                    totalWeight = Math.addExact(curSegmentOutput.segmentTotalWeight, totalWeight)
                } catch (ae: ArithmeticException) {
                    throw IllegalArgumentException(
                        "weight field \"$weightFieldName\": long totalWeight value out of bounds", ae)
                }

                val currSegmentLen = curSegmentOutput.segmentIdx
                curSegmentOutput.values.copyInto(values, overallLength, 0, currSegmentLen)
                curSegmentOutput.weights.copyInto(weights, overallLength, 0, currSegmentLen)
                overallLength += currSegmentLen
            }
            return computeDynamicNumericRanges(values, weights, overallLength, totalWeight, topN)
        }

        /**
   * Compute dynamic numeric ranges using weights.
   *
   * @param values an array that contains the values of matching documents
   * @param weights an array that contains the weights of matching documents
   * @param len actual length of values and weights
   * @param totalWeight the sum of weight values
   * @param topN the requested top-n parameter
   * @return A list of DynamicRangeInfo that contains count, relevance, min, max, and centroid
   *     values for each range. The size of dynamic ranges may not be exactly equal to top-N. top-N
   *     is used to compute the equi-weight per bin.
   */
        fun computeDynamicNumericRanges(
            values: LongArray, weights: LongArray, len: Int, totalWeight: Long, topN: Int
        ): List<DynamicRangeInfo> {
            assert(values.size == weights.size && len <= values.size && len >= 0)
            assert(topN >= 0)
            val dynamicRangeResult = ArrayList<DynamicRangeInfo>()
            if (len == 0 || topN == 0) {
                return dynamicRangeResult
            }

            object : InPlaceMergeSorter() {
                override fun compare(index1: Int, index2: Int): Int {
                    val cmp = values[index1].compareTo(values[index2])
                    if (cmp == 0) {
                        // If the values are equal, sort based on the weights.
                        // Any weight order is correct as long as it's deterministic.
                        return weights[index1].compareTo(weights[index2])
                    }
                    return cmp
                }

                override fun swap(index1: Int, index2: Int) {
                    var tmp = values[index1]
                    values[index1] = values[index2]
                    values[index2] = tmp
                    tmp = weights[index1]
                    weights[index1] = weights[index2]
                    weights[index2] = tmp
                }
            }.sort(0, len)

            var accuWeight = 0L
            var valueSum = 0L
            var count = 0
            var minIdx = 0

            val rangeWeightTarget = totalWeight.toDouble() / minOf(topN, len)

            for (i in 0 until len) {
                accuWeight += weights[i]
                valueSum += values[i]
                count++

                if (accuWeight >= rangeWeightTarget) {
                    dynamicRangeResult.add(
                        DynamicRangeInfo(count, accuWeight, values[minIdx], values[i], valueSum.toDouble() / count))
                    count = 0
                    accuWeight = 0
                    valueSum = 0
                    minIdx = i + 1
                }
            }

            // capture the remaining values to create the last range
            if (minIdx < len) {
                dynamicRangeResult.add(
                    DynamicRangeInfo(count, accuWeight, values[minIdx], values[len - 1], valueSum.toDouble() / count))
            }
            return dynamicRangeResult
        }
    }

    private class SegmentTask(
        private val matchingDocs: FacetsCollector.MatchingDocs,
        fieldValueSource: LongValuesSource,
        weightValueSource: LongValuesSource,
        segmentOutput: SegmentOutput
    ) : Callable<Unit?> {
        private val matchingParentDocsItr = matchingDocs.bits.iterator()!!
        private val fieldValueSource = fieldValueSource
        private val weightValueSource = weightValueSource
        var segmentOutput: SegmentOutput? = segmentOutput

        override fun call(): Unit? {
            val fieldValue = fieldValueSource.getValues(matchingDocs.context, null)
            val weightValue = weightValueSource.getValues(matchingDocs.context, null)
            var doc = matchingParentDocsItr.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                // If this doc doesn't have a weight, we skip it.
                if (fieldValue.advanceExact(doc) == false || weightValue.advanceExact(doc) == false) {
                    doc = matchingParentDocsItr.nextDoc()
                    continue
                }

                val curValue = fieldValue.longValue()

                val curWeight = weightValue.longValue()
                // We skip weights equal to zero, otherwise they can skew the ranges.
                // Imagine all the weights were zero - any ranges would be valid.
                if (curWeight == 0L) {
                    doc = matchingParentDocsItr.nextDoc()
                    continue
                }

                segmentOutput!!.values[segmentOutput!!.segmentIdx] = curValue
                segmentOutput!!.weights[segmentOutput!!.segmentIdx] = curWeight
                try {
                    segmentOutput!!.segmentTotalWeight =
                        Math.addExact(segmentOutput!!.segmentTotalWeight, curWeight)
                } catch (ae: ArithmeticException) {
                    throw IllegalArgumentException("segment long totalWeight value out of bounds", ae)
                }
                segmentOutput!!.segmentIdx++
                doc = matchingParentDocsItr.nextDoc()
            }
            return null
        }
    }

    /** Holds field value array, weight array, totalWeight, valid value index for each segment */
    private class SegmentOutput(hitsLength: Int) {
        val values = LongArray(hitsLength)
        val weights = LongArray(hitsLength)
        var segmentTotalWeight = 0L
        var segmentIdx = 0
    }

    /**
   * Holds parameters of a dynamic numeric range.
   *
   * @param count the number of items in the range
   * @param weight the summed weight of the items in the range
   * @param min the lower bound of the range (inclusive)
   * @param max the upper bound of the range (inclusive)
   * @param centroid the average value in the range
   */
    data class DynamicRangeInfo(val count: Int, val weight: Long, val min: Long, val max: Long, val centroid: Double)
}
