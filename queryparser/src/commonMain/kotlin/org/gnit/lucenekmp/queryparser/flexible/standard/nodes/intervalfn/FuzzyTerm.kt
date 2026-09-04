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

package org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.queries.intervals.Intervals
import org.gnit.lucenekmp.queries.intervals.IntervalsSource
import org.gnit.lucenekmp.search.FuzzyQuery

/**
 * An interval function equivalent to [FuzzyQuery]. A fuzzy term expands to a disjunction of
 * intervals of terms that are within the specified `maxEdits` from the provided term. A limit of
 * `maxExpansions` prevents the internal implementation from blowing up on too many potential
 * candidate terms.
 */
class FuzzyTerm(
    private val term: String,
    maxEdits: Int?,
    maxExpansions: Int?
) : IntervalFunction() {
    private val maxEdits: Int = maxEdits ?: FuzzyQuery.defaultMaxEdits
    private val maxExpansions: Int =
        maxExpansions ?: Intervals.DEFAULT_MAX_EXPANSIONS

    override fun toIntervalSource(field: String, analyzer: Analyzer): IntervalsSource {
        return Intervals.fuzzyTerm(
            term,
            maxEdits,
            FuzzyQuery.defaultPrefixLength,
            FuzzyQuery.defaultTranspositions,
            maxExpansions
        )
    }

    override fun toString(): String {
        val displayTerm = if (AnalyzedText.requiresQuotes(term)) "\"$term\"" else term
        return "fn:fuzzyTerm($displayTerm $maxEdits$maxExpansions)"
    }
}
