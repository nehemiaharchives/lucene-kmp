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

import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer

/** Similarity that returns the raw TF as score. */
class RawTFSimilarity @JvmOverloads constructor(discountOverlaps: Boolean = true) :
    Similarity(discountOverlaps) {
    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        return object : SimScorer() {
            override fun score(freq: Float, norm: Long): Float {
                return boost * freq
            }
        }
    }
}
