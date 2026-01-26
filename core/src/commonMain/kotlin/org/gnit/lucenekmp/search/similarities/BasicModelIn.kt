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

import org.gnit.lucenekmp.search.Explanation

/**
 * The basic tf-idf model of randomness.
 *
 * @lucene.experimental
 */
class BasicModelIn : BasicModel {

    /** Sole constructor: parameter-free */
    constructor()

    override fun score(stats: BasicStats, tfn: Double, aeTimes1pTfn: Double): Double {
        val N = stats.numberOfDocuments
        val n = stats.docFreq
        val A = SimilarityBase.log2((N + 1).toDouble() / (n + 0.5))

        // basic model I(n) should return A * tfn
        // which we rewrite to A * (1 + tfn) - A
        // so that it can be combined with the after effect while still guaranteeing
        // that the result is non-decreasing with tfn

        return A * aeTimes1pTfn * (1 - 1 / (1 + tfn))
    }

    override fun explain(stats: BasicStats, tfn: Double, aeTimes1pTfn: Double): Explanation {
        return Explanation.match(
            (score(stats, tfn, aeTimes1pTfn) * (1 + tfn) / aeTimes1pTfn).toFloat(),
            this::class.simpleName + ", computed as tfn * log2((N + 1) / (n + 0.5)) from:",
            Explanation.match(tfn.toFloat(), "tfn, normalized term frequency"),
            Explanation.match(stats.numberOfDocuments, "N, total number of documents with field"),
            Explanation.match(stats.docFreq, "n, number of documents containing term")
        )
    }

    override fun toString(): String {
        return "I(n)"
    }
}
