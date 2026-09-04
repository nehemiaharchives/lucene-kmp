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
package org.gnit.lucenekmp.queryparser.flexible.core.nodes

import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax

/**
 * A [DeletedQueryNode] represents a node that was deleted from the query node tree. It can be
 * removed from the tree using the RemoveDeletedQueryNodesProcessor processor.
 */
open class DeletedQueryNode : QueryNodeImpl() {
    init {
        // empty constructor
    }

    override fun toQueryString(escaper: EscapeQuerySyntax): CharSequence {
        return "[DELETEDCHILD]"
    }

    override fun toString(): String {
        return "<deleted/>"
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as DeletedQueryNode

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return DeletedQueryNode()
    }
}
