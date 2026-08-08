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
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.TextableQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax
import org.gnit.lucenekmp.search.RegexpQuery
import org.gnit.lucenekmp.util.BytesRef

/** A [RegexpQueryNode] represents [RegexpQuery] query Examples: /[a-z]|[0-9]/ */
class RegexpQueryNode(
    /**
     * @param field - field name
     * @param text - value that contains a regular expression
     * @param begin - position in the query string
     * @param end - position in the query string
     */
    override var field: CharSequence?,
    text: CharSequence,
    begin: Int,
    end: Int,
) : QueryNodeImpl(), TextableQueryNode, FieldableNode {
    override var text: CharSequence? = text.subSequence(begin, end)

    /**
     * @param field - field name
     * @param text - value that contains a regular expression
     */
    constructor(field: CharSequence?, text: CharSequence) : this(field, text, 0, text.length)

    fun textToBytesRef(): BytesRef {
        return BytesRef(requireNotNull(text))
    }

    override fun toString(): String {
        return "<regexp field='$field' term='$text'/>"
    }

    override fun cloneTree(): RegexpQueryNode {
        val clone = super.cloneTree() as RegexpQueryNode
        clone.field = field
        clone.text = text
        return clone
    }

    fun getFieldAsString(): String {
        return field.toString()
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        return if (isDefaultField(field)) "/$text/" else "$field:/$text/"
    }

    override fun cloneNode(): QueryNodeImpl {
        return RegexpQueryNode(field, requireNotNull(text))
    }
}
