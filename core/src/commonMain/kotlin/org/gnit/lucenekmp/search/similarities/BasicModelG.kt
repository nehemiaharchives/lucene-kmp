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
 * Geometric as limiting form of the Bose-Einstein model. The formula used in Lucene differs
 * slightly from the one in the original paper: `F` is increased by `1` and `N` is
 * increased by `F`.
 *
 * @lucene.experimental
 */
class BasicModelG : BasicModel {

    /** Sole constructor: parameter-free */
    constructor()

    override fun score(stats: BasicStats, tfn: Double, aeTimes1pTfn: Double): Double {
        // just like in BE, approximation only holds true when F << N, so we use lambda = F / (N + F)
        val F = (stats.totalTermFreq + 1).toDouble()
        val N = stats.numberOfDocuments.toDouble()
        val lambda = F / (N + F)
        // -log(1 / (lambda + 1)) -> log(lambda + 1)
        val A = SimilarityBase.log2(lambda + 1)
        val B = SimilarityBase.log2((1 + lambda) / lambda)

        // basic model G should return (A + B * tfn)
        // which we rewrite to B * (1 + tfn) - (B - A)
        // so that it can be combined with the after effect while still guaranteeing
        // that the result is non-decreasing with tfn since B >= A

        return (B - (B - A) / (1 + tfn)) * aeTimes1pTfn
    }

    override fun explain(stats: BasicStats, tfn: Double, aeTimes1pTfn: Double): Explanation {
        val F = (stats.totalTermFreq + 1).toDouble()
        val N = stats.numberOfDocuments.toDouble()
        val lambda = F / (N + F)
        val explLambda =
            Explanation.match(
                lambda.toFloat(),
                "lambda, computed as F / (N + F) from:",
                Explanation.match(
                    F.toFloat(), "F, total number of occurrences of term across all docs + 1"
                ),
                Explanation.match(N.toFloat(), "N, total number of documents with field")
            )

        return Explanation.match(
            (score(stats, tfn, aeTimes1pTfn) * (1 + tfn) / aeTimes1pTfn).toFloat(),
            this::class.simpleName
                    + ", computed as "
                    + "log2(lambda + 1) + tfn * log2((1 + lambda) / lambda) from:",
            Explanation.match(tfn.toFloat(), "tfn, normalized term frequency"),
            explLambda
        )
    }

    override fun toString(): String {
        return "G"
    }
}
