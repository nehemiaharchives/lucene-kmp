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

package org.gnit.lucenekmp.queries.intervals

import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.similarities.Similarity
import kotlin.math.pow

internal abstract class IntervalScoreFunction {

    companion object {
        fun saturationFunction(pivot: Float): IntervalScoreFunction {
            if (pivot <= 0 || pivot.isFinite() == false) {
                throw IllegalArgumentException("pivot must be > 0, got: $pivot")
            }
            return SaturationFunction(pivot)
        }

        fun sigmoidFunction(pivot: Float, exp: Float): IntervalScoreFunction {
            if (pivot <= 0 || pivot.isFinite() == false) {
                throw IllegalArgumentException("pivot must be > 0, got: $pivot")
            }
            if (exp <= 0 || exp.isFinite() == false) {
                throw IllegalArgumentException("exp must be > 0, got: $exp")
            }
            return SigmoidFunction(pivot, exp)
        }
    }

    abstract fun scorer(weight: Float): Similarity.SimScorer

    abstract fun explain(interval: String, weight: Float, sloppyFreq: Float): Explanation

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    abstract override fun toString(): String

    private class SaturationFunction(
        val pivot: Float
    ) : IntervalScoreFunction() {

        override fun scorer(weight: Float): Similarity.SimScorer {
            return object : Similarity.SimScorer() {
                override fun score(freq: Float, norm: Long): Float {
                    // should be f / (f + k) but we rewrite it to
                    // 1 - k / (f + k) to make sure it doesn't decrease
                    // with f in spite of rounding
                    return weight * (1.0f - pivot / (pivot + freq))
                }
            }
        }

        override fun explain(interval: String, weight: Float, sloppyFreq: Float): Explanation {
            val score = scorer(weight).score(sloppyFreq, 1L)
            return Explanation.match(
                score,
                "Saturation function on interval frequency, computed as w * S / (S + k) from:",
                Explanation.match(weight, "w, weight of this function"),
                Explanation.match(
                    pivot,
                    "k, pivot feature value that would give a score contribution equal to w/2"
                ),
                Explanation.match(
                    sloppyFreq,
                    "S, the sloppy frequency of the interval query $interval"
                )
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SaturationFunction) return false
            return Float.compare(other.pivot, pivot) == 0
        }

        override fun hashCode(): Int {
            return 31 + pivot.hashCode()
        }

        override fun toString(): String {
            return "SaturationFunction(pivot=$pivot)"
        }
    }

    private class SigmoidFunction(
        private val pivot: Float,
        private val a: Float
    ) : IntervalScoreFunction() {

        private val pivotPa: Double = pivot.toDouble().pow(a.toDouble())

        override fun scorer(weight: Float): Similarity.SimScorer {
            return object : Similarity.SimScorer() {
                override fun score(freq: Float, norm: Long): Float {
                    // should be f^a / (f^a + k^a) but we rewrite it to
                    // 1 - k^a / (f + k^a) to make sure it doesn't decrease
                    // with f in spite of rounding
                    return (
                        weight * (
                            1.0f -
                                pivotPa / (freq.toDouble().pow(a.toDouble()) + pivotPa)
                            )
                        ).toFloat()
                }
            }
        }

        override fun explain(interval: String, weight: Float, sloppyFreq: Float): Explanation {
            val score = scorer(weight).score(sloppyFreq, 1L)
            return Explanation.match(
                score,
                "Sigmoid function on interval frequency, computed as w * S^a / (S^a + k^a) from:",
                Explanation.match(weight, "w, weight of this function"),
                Explanation.match(
                    pivot,
                    "k, pivot feature value that would give a score contribution equal to w/2"
                ),
                Explanation.match(
                    a,
                    "a, exponent, higher values make the function grow slower before k and faster after k"
                ),
                Explanation.match(
                    sloppyFreq,
                    "S, the sloppy frequency of the interval query $interval"
                )
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SigmoidFunction) return false
            return Float.compare(other.pivot, pivot) == 0 &&
                Float.compare(other.a, a) == 0
        }

        override fun hashCode(): Int {
            var result = 1
            result = 31 * result + pivot.hashCode()
            result = 31 * result + a.hashCode()
            return result
        }

        override fun toString(): String {
            return "SigmoidFunction(pivot=$pivot, a=$a)"
        }
    }

}
