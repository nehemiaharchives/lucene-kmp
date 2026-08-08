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

/**
 * A [DocIdSetIterator] which is a disjunction of the approximations of the provided
 * iterators.
 *
 * @lucene.internal
 */
internal class DisjunctionDISIApproximation(
    val subIterators: DisiPriorityQueue
) : DocIdSetIterator() {

    val cost: Long

    init {
        var cost = 0L
        for (w in subIterators) {
            cost += w.cost
        }
        this.cost = cost
    }

    override fun cost(): Long {
        return cost
    }

    override fun docID(): Int {
        return subIterators.top().doc
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        var top = subIterators.top()
        val doc = top.doc
        do {
            top.doc = top.approximation.nextDoc()
            top = subIterators.updateTop()
        } while (top.doc == doc)

        return top.doc
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        var top = subIterators.top()
        do {
            top.doc = top.approximation.advance(target)
            top = subIterators.updateTop()
        } while (top.doc < target)

        return top.doc
    }
}
