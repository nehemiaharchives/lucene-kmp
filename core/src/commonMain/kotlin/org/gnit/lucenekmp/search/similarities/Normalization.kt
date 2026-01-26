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
 * This class acts as the base class for the implementations of the term frequency normalization
 * methods in the DFR framework.
 *
 * @see DFRSimilarity
 * @lucene.experimental
 */
abstract class Normalization {

    /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
    constructor()

    /**
     * Returns the normalized term frequency.
     *
     * @param len the field length.
     */
    abstract fun tfn(stats: BasicStats, tf: Double, len: Double): Double

    /**
     * Returns an explanation for the normalized term frequency.
     *
     * <p>The default normalization methods use the field length of the document and the average field
     * length to compute the normalized term frequency. This method provides a generic explanation for
     * such methods. Subclasses that use other statistics must override this method.
     */
    open fun explain(stats: BasicStats, tf: Double, len: Double): Explanation {
        return Explanation.match(
            tfn(stats, tf, len).toFloat(),
            this::class.simpleName + ", computed from:",
            Explanation.match(tf.toFloat(), "tf, number of occurrences of term in the document"),
            Explanation.match(
                stats.avgFieldLength.toFloat(),
                "avgfl, average length of field across all documents"
            ),
            Explanation.match(len.toFloat(), "fl, field length of the document")
        )
    }

    /** Implementation used when there is no normalization. */
    class NoNormalization : Normalization {

        /** Sole constructor: parameter-free */
        constructor()

        override fun tfn(stats: BasicStats, tf: Double, len: Double): Double {
            return tf
        }

        override fun explain(stats: BasicStats, tf: Double, len: Double): Explanation {
            return Explanation.match(1, "no normalization")
        }

        override fun toString(): String {
            return ""
        }
    }

    /**
     * Subclasses must override this method to return the code of the normalization formula. Refer to
     * the original paper for the list.
     */
    abstract override fun toString(): String
}
