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
package org.gnit.lucenekmp.queryparser.flexible.standard.processors

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QuotedFieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.core.util.UnescapedCharSequence
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PrefixWildcardQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.TermRangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode
import org.gnit.lucenekmp.search.PrefixQuery

/**
 * The StandardSyntaxParser creates [PrefixWildcardQueryNode] nodes which have values containing the
 * prefixed wildcard. However, Lucene [PrefixQuery] cannot contain the prefixed wildcard. So, this
 * processor basically removed the prefixed wildcard from the [PrefixWildcardQueryNode] value.
 *
 * @see PrefixQuery
 * @see PrefixWildcardQueryNode
 */
class WildcardQueryNodeProcessor : QueryNodeProcessorImpl() {
    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        // the old Lucene Parser ignores FuzzyQueryNode that are also PrefixWildcardQueryNode or
        // WildcardQueryNode
        // we do the same here, also ignore empty terms
        if (node is FieldQueryNode || node is FuzzyQueryNode) {
            val fqn = node as FieldQueryNode
            var text = requireNotNull(fqn.text)

            // do not process wildcards for TermRangeQueryNode children and
            // QuotedFieldQueryNode to reproduce the old parser behavior
            if (fqn.parent is TermRangeQueryNode ||
                fqn is QuotedFieldQueryNode ||
                text.length <= 0
            ) {
                // Ignore empty terms
                return node
            }

            // Code below simulates the old lucene parser behavior for wildcards

            if (isWildcard(text)) {
                val analyzer = requireNotNull(queryConfigHandler).get(ConfigurationKeys.ANALYZER)
                if (analyzer != null) {
                    text = analyzeWildcard(analyzer, fqn.getFieldAsString() ?: "", text.toString())
                }
                if (isPrefixWildcard(text)) {
                    return PrefixWildcardQueryNode(fqn.field, text, fqn.begin, fqn.end)
                } else {
                    return WildcardQueryNode(fqn.field, text, fqn.begin, fqn.end)
                }
            }
        }

        return node
    }

    private fun isWildcard(text: CharSequence?): Boolean {
        if (text == null || text.length <= 0) return false

        // If a un-escaped '*' or '?' if found return true
        // start at the end since it's more common to put wildcards at the end
        for (i in text.length - 1 downTo 0) {
            if ((text[i] == '*' || text[i] == '?') &&
                !UnescapedCharSequence.wasEscaped(text, i)
            ) {
                return true
            }
        }

        return false
    }

    private fun isPrefixWildcard(text: CharSequence?): Boolean {
        if (text == null || text.length <= 0 || !isWildcard(text)) return false

        // Validate last character is a '*' and was not escaped
        // If single '*' is is a wildcard not prefix to simulate old queryparser
        if (text[text.length - 1] != '*') return false
        if (UnescapedCharSequence.wasEscaped(text, text.length - 1)) return false
        if (text.length == 1) return false

        // Only make a prefix if there is only one single star at the end and no '?' or '*' characters
        // If single wildcard return false to mimic old queryparser
        for (i in text.indices) {
            if (text[i] == '?') return false
            if (text[i] == '*' && !UnescapedCharSequence.wasEscaped(text, i)) {
                if (i == text.length - 1) return true
                else return false
            }
        }

        return false
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {
        return children
    }

    companion object {
        private val WILDCARD_PATTERN = Regex("(\\\\.)|([?*]+)")

        // because we call utf8ToString, this will only work with the default TermToBytesRefAttribute
        private fun analyzeWildcard(a: Analyzer, field: String, wildcard: String): String {
            // best effort to not pass the wildcard characters through #normalize
            val wildcardMatches = WILDCARD_PATTERN.findAll(wildcard)
            val sb = StringBuilder()
            var last = 0

            for (wildcardMatch in wildcardMatches) {
                // continue if escaped char
                if (wildcardMatch.groups[1] != null) {
                    continue
                }

                if (wildcardMatch.range.first > 0) {
                    val chunk = wildcard.substring(last, wildcardMatch.range.first)
                    val normalized = a.normalize(field, chunk)
                    sb.append(normalized.utf8ToString())
                }
                // append the wildcard character
                sb.append(wildcardMatch.groups[2]!!.value)

                last = wildcardMatch.range.last + 1
            }
            if (last < wildcard.length) {
                val chunk = wildcard.substring(last)
                val normalized = a.normalize(field, chunk)
                sb.append(normalized.utf8ToString())
            }
            return sb.toString()
        }
    }
}
