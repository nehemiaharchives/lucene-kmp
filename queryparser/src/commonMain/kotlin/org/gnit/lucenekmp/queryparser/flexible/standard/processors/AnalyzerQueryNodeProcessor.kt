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

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CachingTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.GroupQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.NoTokenFoundQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QuotedFieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.RangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.TokenizedPhraseQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.Operator
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MultiPhraseQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.SynonymQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode

/**
 * This processor verifies if [ConfigurationKeys.ANALYZER] is defined in the [QueryConfigHandler].
 * If it is and the analyzer is not `null`, it looks for every [FieldQueryNode] that is not
 * [WildcardQueryNode], [FuzzyQueryNode] or [RangeQueryNode] contained in the query node tree, then
 * it applies the analyzer to that [FieldQueryNode] object. <br> <br> If the analyzer return only one
 * term, the returned term is set to the [FieldQueryNode] and it's returned. <br> <br> If the analyzer
 * return more than one term, a [TokenizedPhraseQueryNode] or [MultiPhraseQueryNode] is created,
 * whether there is one or more terms at the same position, and it's returned. <br> <br> If no term
 * is returned by the analyzer a [NoTokenFoundQueryNode] object is returned.
 *
 * @see ConfigurationKeys.ANALYZER
 * @see Analyzer
 * @see TokenStream
 */
class AnalyzerQueryNodeProcessor : QueryNodeProcessorImpl() {
    private var analyzer: Analyzer? = null

    private var positionIncrementsEnabled = false

    private var defaultOperator: Operator = Operator.OR

    init {
        // empty constructor
    }

    override fun process(queryTree: QueryNode): QueryNode {
        val analyzer = requireNotNull(queryConfigHandler).get(ConfigurationKeys.ANALYZER)

        if (analyzer != null) {
            this.analyzer = analyzer
            positionIncrementsEnabled = false
            val positionIncrementsEnabled =
                requireNotNull(queryConfigHandler).get(ConfigurationKeys.ENABLE_POSITION_INCREMENTS)
            val defaultOperator =
                requireNotNull(queryConfigHandler).get(ConfigurationKeys.DEFAULT_OPERATOR)
            this.defaultOperator = defaultOperator ?: Operator.OR

            if (positionIncrementsEnabled != null) {
                this.positionIncrementsEnabled = positionIncrementsEnabled
            }

            if (this.analyzer != null) {
                return super.process(queryTree)
            }
        }

        return queryTree
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is FieldQueryNode &&
            node !is WildcardQueryNode &&
            node !is FuzzyQueryNode &&
            node.parent !is RangeQueryNode<*>
        ) {
            val fieldNode = node
            val text = requireNotNull(fieldNode.getTextAsString())
            val field = requireNotNull(fieldNode.getFieldAsString())

            var buffer: CachingTokenFilter? = null
            var posIncrAtt: PositionIncrementAttribute? = null
            var numTokens = 0
            var positionCount = 0
            var severalTokensAtSamePosition = false

            try {
                try {
                    requireNotNull(analyzer).tokenStream(field, text).use { source ->
                        buffer = CachingTokenFilter(source)
                        buffer!!.reset()

                        if (buffer!!.hasAttribute(PositionIncrementAttribute::class)) {
                            posIncrAtt =
                                buffer!!.getAttribute(PositionIncrementAttribute::class)
                        }

                        try {
                            while (buffer!!.incrementToken()) {
                                numTokens++
                                val positionIncrement =
                                    if (posIncrAtt != null) {
                                        posIncrAtt!!.getPositionIncrement()
                                    } else {
                                        1
                                    }
                                if (positionIncrement != 0) {
                                    positionCount += positionIncrement
                                } else {
                                    severalTokensAtSamePosition = true
                                }
                            }
                        } catch (
                            @Suppress("UNUSED_VARIABLE")
                            e: IOException,
                        ) {
                            // ignore
                        }

                        // rewind the buffer stream
                        buffer!!.reset() // will never through on subsequent reset calls
                    }
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }

                if (!buffer!!.hasAttribute(CharTermAttribute::class)) {
                    return NoTokenFoundQueryNode()
                }

                val termAtt = buffer!!.getAttribute(CharTermAttribute::class)

                if (numTokens == 0) {
                    return NoTokenFoundQueryNode()
                } else if (numTokens == 1) {
                    var term: String? = null
                    try {
                        val hasNext = buffer!!.incrementToken()
                        assert(hasNext == true)
                        term = termAtt.toString()
                    } catch (
                        @Suppress("UNUSED_VARIABLE")
                        e: IOException,
                    ) {
                        // safe to ignore, because we know the number of tokens
                    }

                    fieldNode.text = term

                    return fieldNode
                } else if (severalTokensAtSamePosition || node !is QuotedFieldQueryNode) {
                    if (positionCount == 1 || node !is QuotedFieldQueryNode) {
                        // no phrase query:

                        if (positionCount == 1) {
                            // simple case: only one position, with synonyms
                            val children: MutableList<QueryNode> = mutableListOf()

                            for (i in 0 until numTokens) {
                                var term: String? = null
                                try {
                                    val hasNext = buffer!!.incrementToken()
                                    assert(hasNext == true)
                                    term = termAtt.toString()
                                } catch (
                                    @Suppress("UNUSED_VARIABLE")
                                    e: IOException,
                                ) {
                                    // safe to ignore, because we know the number of tokens
                                }

                                children.add(FieldQueryNode(field, term, -1, -1))
                            }
                            return GroupQueryNode(SynonymQueryNode(children))
                        } else {
                            // multiple positions
                            var q: QueryNode = BooleanQueryNode(emptyList())
                            var currentQuery: QueryNode? = null
                            for (i in 0 until numTokens) {
                                var term: String? = null
                                try {
                                    val hasNext = buffer!!.incrementToken()
                                    assert(hasNext == true)
                                    term = termAtt.toString()
                                } catch (
                                    @Suppress("UNUSED_VARIABLE")
                                    e: IOException,
                                ) {
                                    // safe to ignore, because we know the number of tokens
                                }
                                if (posIncrAtt != null &&
                                    posIncrAtt!!.getPositionIncrement() == 0
                                ) {
                                    if (currentQuery !is BooleanQueryNode) {
                                        val t = currentQuery
                                        currentQuery = SynonymQueryNode(emptyList())
                                        currentQuery.add(requireNotNull(t))
                                    }
                                    currentQuery.add(FieldQueryNode(field, term, -1, -1))
                                } else {
                                    if (currentQuery != null) {
                                        if (defaultOperator == Operator.OR) {
                                            q.add(currentQuery)
                                        } else {
                                            q.add(
                                                ModifierQueryNode(
                                                    currentQuery,
                                                    ModifierQueryNode.Modifier.MOD_REQ,
                                                ),
                                            )
                                        }
                                    }
                                    currentQuery = FieldQueryNode(field, term, -1, -1)
                                }
                            }
                            if (defaultOperator == Operator.OR) {
                                q.add(requireNotNull(currentQuery))
                            } else {
                                q.add(
                                    ModifierQueryNode(
                                        requireNotNull(currentQuery),
                                        ModifierQueryNode.Modifier.MOD_REQ,
                                    ),
                                )
                            }

                            if (q is BooleanQueryNode) {
                                q = GroupQueryNode(q)
                            }
                            return q
                        }
                    } else {
                        // phrase query:
                        val mpq = MultiPhraseQueryNode()

                        val multiTerms: MutableList<FieldQueryNode> = ArrayList()
                        var position = -1
                        var i = 0
                        var termGroupCount = 0
                        while (i < numTokens) {
                            var term: String? = null
                            var positionIncrement = 1
                            try {
                                val hasNext = buffer!!.incrementToken()
                                assert(hasNext == true)
                                term = termAtt.toString()
                                if (posIncrAtt != null) {
                                    positionIncrement = posIncrAtt!!.getPositionIncrement()
                                }
                            } catch (
                                @Suppress("UNUSED_VARIABLE")
                                e: IOException,
                            ) {
                                // safe to ignore, because we know the number of tokens
                            }

                            if (positionIncrement > 0 && multiTerms.size > 0) {
                                for (termNode in multiTerms) {
                                    if (positionIncrementsEnabled) {
                                        termNode.positionIncrement = position
                                    } else {
                                        termNode.positionIncrement = termGroupCount
                                    }

                                    mpq.add(termNode)
                                }

                                // Only increment once for each "group" of
                                // terms that were in the same position:
                                termGroupCount++

                                multiTerms.clear()
                            }

                            position += positionIncrement
                            multiTerms.add(FieldQueryNode(field, term, -1, -1))
                            i++
                        }

                        for (termNode in multiTerms) {
                            if (positionIncrementsEnabled) {
                                termNode.positionIncrement = position
                            } else {
                                termNode.positionIncrement = termGroupCount
                            }

                            mpq.add(termNode)
                        }

                        return mpq
                    }
                } else {
                    val pq = TokenizedPhraseQueryNode()

                    var position = -1

                    for (i in 0 until numTokens) {
                        var term: String? = null
                        var positionIncrement = 1

                        try {
                            val hasNext = buffer!!.incrementToken()
                            assert(hasNext == true)
                            term = termAtt.toString()

                            if (posIncrAtt != null) {
                                positionIncrement = posIncrAtt!!.getPositionIncrement()
                            }
                        } catch (
                            @Suppress("UNUSED_VARIABLE")
                            e: IOException,
                        ) {
                            // safe to ignore, because we know the number of tokens
                        }

                        val newFieldNode = FieldQueryNode(field, term, -1, -1)

                        if (positionIncrementsEnabled) {
                            position += positionIncrement
                            newFieldNode.positionIncrement = position
                        } else {
                            newFieldNode.positionIncrement = i
                        }

                        pq.add(newFieldNode)
                    }

                    return pq
                }
            } finally {
                if (buffer != null) {
                    try {
                        buffer!!.close()
                    } catch (
                        @Suppress("UNUSED_VARIABLE")
                        e: IOException,
                    ) {
                        // safe to ignore
                    }
                }
            }
        }

        return node
    }

    override fun preProcessNode(node: QueryNode): QueryNode {
        return node
    }

    override fun setChildrenOrder(children: MutableList<QueryNode>): MutableList<QueryNode> {
        return children
    }
}
