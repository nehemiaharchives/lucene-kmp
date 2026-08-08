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

/** A [QuotedFieldQueryNode] represents phrase query. Example: "life is great" */
/**
 * @param field - field name
 * @param text - value
 * @param begin - position in the query string
 * @param end - position in the query string
 */
class QuotedFieldQueryNode(
    field: CharSequence?,
    text: CharSequence?,
    begin: Int,
    end: Int,
) : FieldQueryNode(field, text, begin, end) {
    override fun toQueryString(escaper: EscapeQuerySyntax): CharSequence {
        if (isDefaultField(field)) {
            return "\"${getTermEscapeQuoted(escaper)}\""
        } else {
            return "$field:\"${getTermEscapeQuoted(escaper)}\""
        }
    }

    override fun toString(): String {
        return "<quotedfield start='$begin' end='$end' field='$field' term='$text'/>"
    }

    override fun cloneTree(): QuotedFieldQueryNode {
        val clone = super.cloneTree() as QuotedFieldQueryNode
        // nothing to do here
        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return QuotedFieldQueryNode(field, text, begin, end)
    }
}
