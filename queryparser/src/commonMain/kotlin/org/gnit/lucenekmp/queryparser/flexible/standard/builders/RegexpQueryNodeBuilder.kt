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
package org.gnit.lucenekmp.queryparser.flexible.standard.builders

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.RegexpQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.processors.MultiTermRewriteMethodProcessor
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.RegexpQuery
import org.gnit.lucenekmp.util.automaton.AutomatonProvider
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp

/** Builds a [RegexpQuery] object from a [RegexpQueryNode] object. */
class RegexpQueryNodeBuilder : StandardQueryBuilder {
    init {
        // empty constructor
    }

    override fun build(queryNode: QueryNode): RegexpQuery {
        val regexpNode = queryNode as RegexpQueryNode

        var method =
            queryNode.getTag(MultiTermRewriteMethodProcessor.TAG_ID) as MultiTermQuery.RewriteMethod?
        if (method == null) {
            method = MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE
        }

        // TODO: make the maxStates configurable w/ a reasonable default (QueryParserBase uses 10000)
        return RegexpQuery(
            Term(regexpNode.getFieldAsString(), regexpNode.textToBytesRef()),
            RegExp.ALL,
            0,
            object : AutomatonProvider {
                override fun getAutomaton(name: String) = null
            },
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
            method,
        )
    }
}
