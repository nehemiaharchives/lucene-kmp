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
package org.gnit.lucenekmp.queryparser.flexible.spans

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.queries.spans.SpanTermQuery
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.builders.StandardQueryBuilder

/** This builder creates [SpanTermQuery]s from a [FieldQueryNode] object. */
class SpanTermQueryNodeBuilder : StandardQueryBuilder {

    override fun build(node: QueryNode): SpanTermQuery {
        val fieldQueryNode = node as FieldQueryNode

        return SpanTermQuery(
            Term(requireNotNull(fieldQueryNode.getFieldAsString()), requireNotNull(fieldQueryNode.getTextAsString()))
        )
    }
}
