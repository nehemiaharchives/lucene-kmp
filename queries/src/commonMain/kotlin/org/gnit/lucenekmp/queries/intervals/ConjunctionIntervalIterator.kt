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
import org.gnit.lucenekmp.search.ConjunctionUtils
import org.gnit.lucenekmp.search.DocIdSetIterator

internal abstract class ConjunctionIntervalIterator(
    val subIterators: List<IntervalIterator>
) : IntervalIterator() {

    val approximation: DocIdSetIterator =
        ConjunctionUtils.intersectIterators(subIterators.toMutableList())
    val cost: Float

    init {
        var costsum = 0f
        for (it in subIterators) {
            costsum += it.matchCost()
        }
        this.cost = costsum
    }

    override fun docID(): Int {
        return approximation.docID()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        val doc = approximation.nextDoc()
        if (doc != NO_MORE_DOCS) {
            reset()
        }
        return doc
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        val doc = approximation.advance(target)
        if (doc != NO_MORE_DOCS) {
            reset()
        }
        return doc
    }

    @Throws(IOException::class)
    protected abstract fun reset()

    override fun cost(): Long {
        return approximation.cost()
    }

    final override fun matchCost(): Float {
        return cost
    }
}
