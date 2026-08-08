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

import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl
import org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax

/**
 * A [WildcardQueryNode] represents wildcard query This does not apply to phrases. Examples:
 * `a*b*c Fl?w? m?ke*g`.
 */
open class WildcardQueryNode(
    /**
     * @param field - field name
     * @param text - value that contains one or more wild card characters (? or *)
     * @param begin - position in the query string
     * @param end - position in the query string
     */
    field: CharSequence?,
    text: CharSequence?,
    begin: Int,
    end: Int,
) : FieldQueryNode(field, text, begin, end) {
    constructor(fqn: FieldQueryNode) : this(fqn.field, fqn.text, fqn.begin, fqn.end)

    override fun toQueryString(escaper: EscapeQuerySyntax): CharSequence {
        if (isDefaultField(field)) {
            return requireNotNull(text)
        } else {
            return "$field:$text"
        }
    }

    override fun toString(): String {
        return "<wildcard field='$field' term='$text'/>"
    }

    override fun cloneTree(): WildcardQueryNode {
        val clone = super.cloneTree() as WildcardQueryNode

        // nothing to do here

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return WildcardQueryNode(field, text, begin, end)
    }
}
