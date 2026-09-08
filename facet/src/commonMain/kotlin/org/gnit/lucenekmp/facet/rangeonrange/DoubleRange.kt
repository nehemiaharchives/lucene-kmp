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
package org.gnit.lucenekmp.facet.rangeonrange

import kotlin.math.nextUp
import kotlin.math.nextDown

/** Represents a double range for RangeOnRange faceting */
class DoubleRange : Range {
    /** Minimum (inclusive). */
    val min: DoubleArray
    /** Maximum (inclusive). */
    val max: DoubleArray

    /**
   * Represents a double range for RangeOnRange faceting
   *
   * @param label the name of the range
   * @param minIn the minimum
   * @param minInclusive if the minimum is inclusive
   * @param maxIn the maximum
   * @param maxInclusive if the maximum is inclusive
   */
    constructor(label: String, minIn: Double, minInclusive: Boolean, maxIn: Double, maxInclusive: Boolean) : super(label, 1) {
        var minIn = minIn
        var maxIn = maxIn
        if (minIn.isNaN()) throw IllegalArgumentException("min cannot be NaN")
        if (minInclusive == false) minIn = minIn.nextUp()
        if (maxIn.isNaN()) throw IllegalArgumentException("max cannot be NaN")
        if (maxInclusive == false) {
            // Why no Math.nextDown?
            maxIn = maxIn.nextDown()
        }
        if (minIn > maxIn) failNoMatch()
        min = doubleArrayOf(minIn)
        max = doubleArrayOf(maxIn)
    }

    /**
   * Represents a double range for RangeOnRange faceting
   *
   * @param label the name of the range
   * @param min the minimum, inclusive
   * @param max the maximum, inclusive
   */
    constructor(label: String, min: DoubleArray, max: DoubleArray) : super(label, min.size) {
        checkArgs(min, max)
        this.min = min
        this.max = max
    }

    override fun toString(): String = "DoubleRange(label: $label, min: ${min.contentToString()}, max: ${max.contentToString()})"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DoubleRange) return false
        return min.contentEquals(other.min) && max.contentEquals(other.max) && label == other.label && dims == other.dims
    }
    override fun hashCode(): Int = 31 * (31 * (31 * (31 + label.hashCode()) + min.contentHashCode()) + max.contentHashCode()) + dims

    /** validate the arguments */
    private fun checkArgs(min: DoubleArray, max: DoubleArray) {
        if (min.isEmpty() || max.isEmpty()) failNoMatch()
        if (min.size != max.size) failNoMatch()
        for (i in min.indices) {
            if (min[i] > max[i]) failNoMatch()
        }
    }
}
