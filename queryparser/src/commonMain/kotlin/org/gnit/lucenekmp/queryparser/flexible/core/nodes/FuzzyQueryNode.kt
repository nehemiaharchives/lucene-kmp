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

/** A [FuzzyQueryNode] represents a element that contains field/text/similarity tuple */
class FuzzyQueryNode(
    /** @param field Name of the field query will use. */
    field: CharSequence?,
    /** @param term Term token to use for building term for the query */
    term: CharSequence?,
    /** @param minSimilarity - similarity value */
    var similarity: Float,
    begin: Int,
    end: Int,
) : FieldQueryNode(field, term, begin, end) {
    var prefixLength: Int = 0

    init {
        setLeaf(true)
    }

    override fun toQueryString(escaper: EscapeQuerySyntax): CharSequence {
        if (isDefaultField(field)) {
            return "${getTermEscaped(escaper)}~$similarity"
        } else {
            return "$field:${getTermEscaped(escaper)}~$similarity"
        }
    }

    override fun toString(): String {
        return "<fuzzy field='$field' similarity='$similarity' term='$text'/>"
    }

    override fun cloneTree(): FuzzyQueryNode {
        val clone = super.cloneTree() as FuzzyQueryNode

        clone.similarity = similarity

        return clone
    }

    override fun cloneNode(): QueryNodeImpl {
        return FuzzyQueryNode(field, text, similarity, begin, end).also {
            it.prefixLength = prefixLength
        }
    }
}
