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

import org.gnit.lucenekmp.search.IndexSearcher

internal object Disjunctions {

    // Given a list of sources that contain disjunctions, and a combiner function,
    // pulls the disjunctions to the top of the source tree

    // eg FUNC(a, b, OR(c, "d e")) => [FUNC(a, b, c), FUNC(a, b, "d e")]

    fun pullUp(
        sources: List<IntervalsSource>,
        function: (List<IntervalsSource>) -> IntervalsSource
    ): List<IntervalsSource> {
        var rewritten = mutableListOf<MutableList<IntervalsSource>>()
        rewritten.add(mutableListOf())
        for (source in sources) {
            val disjuncts = splitDisjunctions(source)
            if (disjuncts.size == 1) {
                rewritten.forEach { l -> l.add(disjuncts[0]) }
            } else {
                if (rewritten.size * disjuncts.size > IndexSearcher.maxClauseCount) {
                    throw IllegalArgumentException("Too many disjunctions to expand")
                }
                val toAdd = mutableListOf<MutableList<IntervalsSource>>()
                for (disj in disjuncts) {
                    // clone the rewritten list, then append the disjunct
                    for (subList in rewritten) {
                        val l = subList.toMutableList()
                        l.add(disj)
                        toAdd.add(l)
                    }
                }
                rewritten = toAdd
            }
        }
        if (rewritten.size == 1) {
            return listOf(function(rewritten[0]))
        }
        return rewritten.map(function)
    }

    // Given a source containing disjunctions, and a mapping function,
    // pulls the disjunctions to the top of the source tree
    fun pullUp(
        source: IntervalsSource,
        function: (IntervalsSource) -> IntervalsSource
    ): List<IntervalsSource> {
        val disjuncts = splitDisjunctions(source)
        if (disjuncts.size == 1) {
            return listOf(function(disjuncts[0]))
        }
        return disjuncts.map(function)
    }

    // Separate out disjunctions into individual sources
    // Clauses that have a minExtent of 1 are grouped together and treated as a single
    // source, as any overlapping intervals of length 1 can be treated as identical,
    // and we know that all combinatorial sources have a minExtent > 1
    private fun splitDisjunctions(source: IntervalsSource): List<IntervalsSource> {
        val singletons = mutableListOf<IntervalsSource>()
        val nonSingletons = mutableListOf<IntervalsSource>()
        for (disj in source.pullUpDisjunctions()) {
            if (disj.minExtent() == 1) {
                singletons.add(disj)
            } else {
                nonSingletons.add(disj)
            }
        }
        val split = mutableListOf<IntervalsSource>()
        if (singletons.size > 0) {
            split.add(Intervals.or(*singletons.toTypedArray()))
        }
        split.addAll(nonSingletons)
        return split
    }
}
