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

/** Query node for PhraseQuery's slop factor. */
class PhraseSlopQueryNode(
    query: QueryNode,
    val value: Int,
) : QueryNodeImpl(), FieldableNode {
    /** @exception QueryNodeError throw in overridden method to disallow */
    init {
        setLeaf(false)
        allocate()
        add(query)
    }

    val child: QueryNode
        get() = requireNotNull(children)[0]

    private fun getValueString(): CharSequence {
        val f = value.toFloat()
        if (f == f.toLong().toFloat()) return "${f.toLong()}"
        else return "$f"
    }

    override fun toString(): String {
        return "<phraseslop value='${getValueString()}'>\n$child\n</phraseslop>"
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        return "${child.toQueryString(escapeSyntaxParser)}~${getValueString()}"
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as PhraseSlopQueryNode

        return clone
    }

    override var field: CharSequence?
        get() {
            val child = child

            if (child is FieldableNode) {
                return child.field
            }

            return null
        }
        set(fieldName) {
            val child = child

            if (child is FieldableNode) {
                child.field = fieldName
            }
        }

    override fun cloneNode(): QueryNodeImpl {
        return PhraseSlopQueryNode(child.cloneTree(), value)
    }
}
