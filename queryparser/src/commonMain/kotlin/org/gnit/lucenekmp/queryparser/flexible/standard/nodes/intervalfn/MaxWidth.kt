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

/** Node that represents [Intervals.maxwidth]. */
class MaxWidth(
    private val width: Int,
    private val source: IntervalFunction
) : IntervalFunction() {

    override fun toIntervalSource(field: String, analyzer: Analyzer): IntervalsSource {
        return Intervals.maxwidth(width, source.toIntervalSource(field, analyzer))
    }

    override fun toString(): String {
        return "fn:maxwidth(" + width + " " + source + ")"
    }
}

