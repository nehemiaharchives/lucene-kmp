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
package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.search.Explanation
import kotlin.math.pow

/**
 * Pareto-Zipf Normalization
 *
 * @lucene.experimental
 */
class NormalizationZ : Normalization {
    val z: Float

    /** Calls [NormalizationZ] */
    constructor() : this(0.30f)

    /**
     * Creates NormalizationZ with the supplied parameter `z`.
     *
     * @param z represents `A/(A+1)` where `A` measures the specificity of the
     *     language. It ranges from (0 .. 0.5)
     */
    constructor(z: Float) {
        if (Float.isNaN(z) || z <= 0f || z >= 0.5f) {
            throw IllegalArgumentException(
                "illegal z value: $z, must be in the range (0 .. 0.5)"
            )
        }
        this.z = z
    }

    override fun tfn(stats: BasicStats, tf: Double, len: Double): Double {
        return tf * (stats.avgFieldLength / len).pow(z.toDouble())
    }

    override fun explain(stats: BasicStats, tf: Double, len: Double): Explanation {
        return Explanation.match(
            tfn(stats, tf, len).toFloat(),
            this::class.simpleName + ", computed as tf * Math.pow(avgfl / fl, z) from:",
            Explanation.match(tf.toFloat(), "tf, number of occurrences of term in the document"),
            Explanation.match(
                stats.avgFieldLength.toFloat(),
                "avgfl, average length of field across all documents"
            ),
            Explanation.match(len.toFloat(), "fl, field length of the document"),
            Explanation.match(z, "z, relates to specificity of the language")
        )
    }

    override fun toString(): String {
        return "Z($z)"
    }
}
