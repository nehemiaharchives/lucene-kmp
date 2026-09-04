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
 * A [OpaqueQueryNode] is used for specify values that are not supposed to be parsed by the parser.
 * For example: and XPATH query in the middle of a query string a
 * b @xpath:'/bookstore/book[1]/title' c d
 */
/**
 * @param schema - schema identifier
 * @param value - value that was not parsed
 */
class OpaqueQueryNode(
    schema: CharSequence,
    value: CharSequence,
) : QueryNodeImpl() {
    /**
     * @return the schema
     */
    var schema: CharSequence = schema
        private set

    /**
     * @return the value
     */
    var value: CharSequence = value
        private set

    init {
        setLeaf(true)
    }

    override fun toString(): String {
        return "<opaque schema='$schema' value='$value'/>"
    }

    override fun toQueryString(escapeSyntaxParser: EscapeQuerySyntax): CharSequence {
        return "@$schema:'$value'"
    }

    override fun cloneTree(): QueryNode {
        val clone = super.cloneTree() as OpaqueQueryNode

        clone.schema = schema
        clone.value = value

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return OpaqueQueryNode(schema, value)
    }

}
