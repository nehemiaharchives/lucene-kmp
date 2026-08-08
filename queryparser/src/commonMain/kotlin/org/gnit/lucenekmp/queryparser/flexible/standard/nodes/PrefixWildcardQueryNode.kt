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

/**
 * A [PrefixWildcardQueryNode] represents wildcardquery that matches abc* or *. This does not apply
 * to phrases, this is a special case on the original lucene parser. TODO: refactor the code to
 * remove this special case from the parser. and probably do it on a Processor
 */
class PrefixWildcardQueryNode(
    /**
     * @param field - field name
     * @param text - value including the wildcard
     * @param begin - position in the query string
     * @param end - position in the query string
     */
    field: CharSequence?,
    text: CharSequence?,
    begin: Int,
    end: Int,
) : WildcardQueryNode(field, text, begin, end) {
    constructor(fqn: FieldQueryNode) : this(fqn.field, fqn.text, fqn.begin, fqn.end)

    override fun toString(): String {
        return "<prefixWildcard field='$field' term='$text'/>"
    }

    override fun cloneTree(): PrefixWildcardQueryNode {
        val clone = super.cloneTree() as PrefixWildcardQueryNode

        // nothing to do here

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return PrefixWildcardQueryNode(field, text, begin, end)
    }
}
