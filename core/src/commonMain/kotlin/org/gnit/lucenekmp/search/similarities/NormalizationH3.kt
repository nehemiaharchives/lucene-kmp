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

import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.search.Explanation

/**
 * Dirichlet Priors normalization
 *
 * @lucene.experimental
 */
class NormalizationH3 : Normalization {
    val mu: Float

    /** Calls [NormalizationH3] */
    constructor() : this(800f)

    /**
     * Creates NormalizationH3 with the supplied parameter `mu`.
     *
     * @param mu smoothing parameter `mu`
     */
    constructor(mu: Float) {
        require(!(Float.isFinite(mu) == false || mu < 0)) {
            "illegal mu value: $mu, must be a non-negative finite value"
        }
        this.mu = mu
    }

    override fun tfn(stats: BasicStats, tf: Double, len: Double): Double {
        return (tf + mu * ((stats.totalTermFreq + 1f) / (stats.numberOfFieldTokens + 1f))) /
                (len + mu) * mu
    }

    override fun explain(stats: BasicStats, tf: Double, len: Double): Explanation {
        return Explanation.match(
            tfn(stats, tf, len).toFloat(),
            this::class.simpleName
                    + ", computed as (tf + mu * ((F+1) / (T+1))) / (fl + mu) * mu from:",
            Explanation.match(tf.toFloat(), "tf, number of occurrences of term in the document"),
            Explanation.match(mu, "mu, smoothing parameter"),
            Explanation.match(
                stats.totalTermFreq.toFloat(),
                "F,  total number of occurrences of term across all documents"
            ),
            Explanation.match(
                stats.numberOfFieldTokens.toFloat(),
                "T, total number of tokens of the field across all documents"
            ),
            Explanation.match(len.toFloat(), "fl, field length of the document")
        )
    }

    override fun toString(): String {
        return "3($mu)"
    }
}
