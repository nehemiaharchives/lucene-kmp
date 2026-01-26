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
 * Normalization model that assumes a uniform distribution of the term frequency.
 *
 * <p>While this model is parameterless in the <a
 * href="http://citeseer.ist.psu.edu/viewdoc/summary?doi=10.1.1.101.742">original article</a>, <a
 * href="http://dl.acm.org/citation.cfm?id=1835490">information-based models</a> (see [
 * IBSimilarity]) introduced a multiplying factor. The default value for the `c` parameter is
 * `1`.
 *
 * @lucene.experimental
 */
class NormalizationH1 : Normalization {
    val c: Float

    /**
     * Creates NormalizationH1 with the supplied parameter `c`.
     *
     * @param c hyper-parameter that controls the term frequency normalization with respect to the
     *     document length.
     */
    constructor(c: Float) {
        // unbounded but typical range 0..10 or so
        require(!(Float.isFinite(c) == false || c < 0)) {
            "illegal c value: $c, must be a non-negative finite value"
        }
        this.c = c
    }

    /** Calls [NormalizationH1] */
    constructor() : this(1f)

    override fun tfn(stats: BasicStats, tf: Double, len: Double): Double {
        return tf * c * (stats.avgFieldLength / len)
    }

    override fun explain(stats: BasicStats, tf: Double, len: Double): Explanation {
        return Explanation.match(
            tfn(stats, tf, len).toFloat(),
            this::class.simpleName + ", computed as tf * c * (avgfl / fl) from:",
            Explanation.match(tf.toFloat(), "tf, number of occurrences of term in the document"),
            Explanation.match(c, "c, hyper-parameter"),
            Explanation.match(
                stats.avgFieldLength.toFloat(),
                "avgfl, average length of field across all documents"
            ),
            Explanation.match(len.toFloat(), "fl, field length of the document")
        )
    }

    override fun toString(): String {
        return "1"
    }
}
