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
package org.gnit.lucenekmp.queryparser.flexible.standard.nodes

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.queries.intervals.IntervalQuery
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.IntervalFunction
import org.gnit.lucenekmp.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl
import org.gnit.lucenekmp.search.Query

/** Node that represents an interval function. */
class IntervalQueryNode(
    field: String?,
    private val source: IntervalFunction
) : QueryNodeImpl(), FieldableNode {
    private var fieldValue: String? = field
    private var analyzer: Analyzer? = null

    val query: Query
        get() {
            val field = requireNotNull(fieldValue) {
                "Field must not be null for interval queries."
            }
            val analyzer = requireNotNull(analyzer) {
                "Analyzer must not be null for interval queries."
            }
            return IntervalQuery(field, source.toIntervalSource(field, analyzer))
        }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): String {
        return "$fieldValue:$source"
    }

    override fun toString(): String {
        return toQueryString(EscapeQuerySyntaxImpl())
    }

    override var field: CharSequence?
        get() = fieldValue
        set(fieldName) {
            fieldValue = requireNotNull(fieldName).toString()
        }

    override fun cloneTree(): IntervalQueryNode {
        return IntervalQueryNode(fieldValue, source)
    }

    fun setAnalyzer(analyzer: Analyzer) {
        this.analyzer = requireNotNull(analyzer) {
            "Analyzer must not be null for interval queries."
        }
    }

    override fun cloneNode(): QueryNodeImpl {
        return IntervalQueryNode(fieldValue, source)
    }
}
