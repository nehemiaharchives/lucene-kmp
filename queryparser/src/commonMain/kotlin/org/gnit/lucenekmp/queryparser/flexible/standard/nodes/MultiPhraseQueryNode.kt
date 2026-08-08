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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.search.MultiPhraseQuery
import org.gnit.lucenekmp.search.PhraseQuery

/**
 * A [MultiPhraseQueryNode] indicates that its children should be used to build a [MultiPhraseQuery]
 * instead of [PhraseQuery].
 */
class MultiPhraseQueryNode : QueryNodeImpl(), FieldableNode {
    init {
        setLeaf(false)
        allocate()
    }

    override fun toString(): String {
        if (children == null || children!!.size == 0) return "<multiPhrase/>"
        val sb = StringBuilder()
        sb.append("<multiPhrase>")
        for (child in children!!) {
            sb.append("\n")
            sb.append(child.toString())
        }
        sb.append("\n</multiPhrase>")
        return sb.toString()
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        if (children == null || children!!.size == 0) return ""

        val sb = StringBuilder()
        var filler = ""
        for (child in children!!) {
            sb.append(filler).append(child.toQueryString(escapeSyntaxParser))
            filler = ","
        }

        return "[MTP[$sb]]"
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as MultiPhraseQueryNode

        // nothing to do

        return clone
    }

    override var field: CharSequence?
        get() {
            val children = children

            return if (children == null || children.size == 0) {
                null
            } else {
                (children[0] as FieldableNode).field
            }
        }
        set(fieldName) {
            val children = children

            if (children != null) {
                for (child in children) {
                    if (child is FieldableNode) {
                        child.field = fieldName
                    }
                }
            }
        }

    override fun cloneNode(): QueryNodeImpl {
        return MultiPhraseQueryNode()
    }
} // end class MultitermQueryNode
