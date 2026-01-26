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
 * Model of the information gain based on Laplace's law of succession.
 *
 * @lucene.experimental
 */
class AfterEffectL : AfterEffect {

    /** Sole constructor: parameter-free */
    constructor()

    final override fun scoreTimes1pTfn(stats: BasicStats): Double {
        return 1.0
    }

    final override fun explain(stats: BasicStats, tfn: Double): Explanation {
        return Explanation.match(
            (scoreTimes1pTfn(stats) / (1 + tfn)).toFloat(),
            this::class.simpleName + ", computed as 1 / (tfn + 1) from:",
            Explanation.match(tfn.toFloat(), "tfn, normalized term frequency")
        )
    }

    override fun toString(): String {
        return "L"
    }
}
