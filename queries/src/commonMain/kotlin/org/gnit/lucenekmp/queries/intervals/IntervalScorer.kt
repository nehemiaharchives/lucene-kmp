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

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.search.similarities.Similarity
import kotlin.math.max

internal class IntervalScorer(
    private val intervals: IntervalIterator,
    private val minExtent: Int,
    private val boost: Float,
    scoreFunction: IntervalScoreFunction
) : Scorer() {

    private val simScorer: Similarity.SimScorer = scoreFunction.scorer(boost)

    private var freq: Float = 0f
    private var lastScoredDoc = -1

    override fun docID(): Int {
        return intervals.docID()
    }

    @Throws(IOException::class)
    override fun score(): Float {
        ensureFreq()
        return simScorer.score(freq, 1)
    }

    @Throws(IOException::class)
    fun freq(): Float {
        ensureFreq()
        return freq
    }

    @Throws(IOException::class)
    private fun ensureFreq() {
        if (lastScoredDoc != docID()) {
            lastScoredDoc = docID()
            freq = 0f
            do {
                val length = intervals.end() - intervals.start() + 1
                freq += 1.0f / max(length - minExtent + 1, 1)
            } while (intervals.nextInterval() != IntervalIterator.NO_MORE_INTERVALS)
        }
    }

    override fun iterator(): DocIdSetIterator {
        return TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator())
    }

    override fun twoPhaseIterator(): TwoPhaseIterator {
        return object : TwoPhaseIterator(intervals) {
            @Throws(IOException::class)
            override fun matches(): Boolean {
                return intervals.nextInterval() != IntervalIterator.NO_MORE_INTERVALS
            }

            override fun matchCost(): Float {
                return intervals.matchCost()
            }
        }
    }

    override fun getMaxScore(upTo: Int): Float {
        return boost
    }
}
