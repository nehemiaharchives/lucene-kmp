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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.GroupQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax

/** Node that represents a minimum-should-match restriction on a [GroupQueryNode]. */
class MinShouldMatchNode(
    val minShouldMatch: Int,
    val groupQueryNode: GroupQueryNode,
) : QueryNodeImpl() {
    init {
        setLeaf(false)
        allocate()
        add(groupQueryNode)
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        return groupQueryNode.toQueryString(escapeSyntaxParser).toString() + "@" + minShouldMatch
    }

    override fun cloneNode(): QueryNodeImpl {
        return MinShouldMatchNode(minShouldMatch, groupQueryNode)
    }
}
