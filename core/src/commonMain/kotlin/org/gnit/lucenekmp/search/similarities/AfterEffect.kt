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
 * This class acts as the base class for the implementations of the <em>first normalization of the
 * informative content</em> in the DFR framework. This component is also called the <em>after
 * effect</em> and is defined by the formula <em>Inf<sub>2</sub> = 1 - Prob<sub>2</sub></em>, where
 * <em>Prob<sub>2</sub></em> measures the <em>information gain</em>.
 *
 * @see DFRSimilarity
 * @lucene.experimental
 */
abstract class AfterEffect {

    /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
    constructor()

    /**
     * Returns the product of the after effect with `1+tfn`. This may not depend on the value of
     * `tfn`.
     */
    abstract fun scoreTimes1pTfn(stats: BasicStats): Double

    /** Returns an explanation for the score. */
    abstract fun explain(stats: BasicStats, tfn: Double): Explanation

    /**
     * Subclasses must override this method to return the code of the after effect formula. Refer to
     * the original paper for the list.
     */
    abstract override fun toString(): String
}
