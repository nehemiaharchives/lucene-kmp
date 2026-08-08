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
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.search.FuzzyQuery
import org.gnit.lucenekmp.util.codePointCount

/** Builds a [FuzzyQuery] object from a [FuzzyQueryNode] object. */
class FuzzyQueryNodeBuilder : StandardQueryBuilder {
    init {
        // empty constructor
    }

    override fun build(queryNode: QueryNode): FuzzyQuery {
        val fuzzyNode = queryNode as FuzzyQueryNode
        val text = requireNotNull(fuzzyNode.getTextAsString())

        val numEdits =
            FuzzyQuery.floatToEdits(fuzzyNode.similarity, text.codePointCount(0, text.length))

        return FuzzyQuery(
            Term(requireNotNull(fuzzyNode.getFieldAsString()), requireNotNull(fuzzyNode.getTextAsString())),
            numEdits,
            fuzzyNode.prefixLength,
        )
    }
}
