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

import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeParseException
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.util.StringUtils
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfig
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointRangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.TermRangeQueryNode

/**
 * This processor is used to convert [TermRangeQueryNode]s to [PointRangeQueryNode]s. It looks for
 * [ConfigurationKeys.POINTS_CONFIG] set in the FieldConfig of every [TermRangeQueryNode] found. If
 * [ConfigurationKeys.POINTS_CONFIG] is found, it considers that [TermRangeQueryNode] to be a
 * numeric range query and convert it to [PointRangeQueryNode].
 *
 * @see ConfigurationKeys.POINTS_CONFIG
 * @see TermRangeQueryNode
 * @see PointsConfig
 * @see PointRangeQueryNode
 */
class PointRangeQueryNodeProcessor : QueryNodeProcessorImpl() {
    /** Constructs an empty [PointRangeQueryNodeProcessor] object. */
    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is TermRangeQueryNode) {
            val config: QueryConfigHandler? = queryConfigHandler

            if (config != null) {
                val termRangeNode = node
                val fieldConfig =
                    config.getFieldConfig(requireNotNull(StringUtils.toString(termRangeNode.field)))

                if (fieldConfig != null) {
                    val numericConfig = fieldConfig.get(ConfigurationKeys.POINTS_CONFIG)

                    if (numericConfig != null) {
                        val lower = termRangeNode.lowerBound
                        val upper = termRangeNode.upperBound

                        val lowerText = requireNotNull(lower.getTextAsString())
                        val upperText = requireNotNull(upper.getTextAsString())
                        val numberFormat = numericConfig.numberFormat
                        var lowerNumber: Number? = null
                        var upperNumber: Number? = null

                        if (lowerText.length > 0) {
                            try {
                                lowerNumber = numberFormat.parse(lowerText)
                            } catch (e: ParseException) {
                                throw QueryNodeParseException(
                                    MessageImpl(
                                        QueryParserMessages.COULD_NOT_PARSE_NUMBER,
                                        lower.getTextAsString(),
                                        numberFormat::class.qualifiedName,
                                    ),
                                    e,
                                )
                            }
                        }

                        if (upperText.length > 0) {
                            try {
                                upperNumber = numberFormat.parse(upperText)
                            } catch (e: ParseException) {
                                throw QueryNodeParseException(
                                    MessageImpl(
                                        QueryParserMessages.COULD_NOT_PARSE_NUMBER,
                                        upper.getTextAsString(),
                                        numberFormat::class.qualifiedName,
                                    ),
                                    e,
                                )
                            }
                        }

                        if (Int::class == numericConfig.type) {
                            if (upperNumber != null) upperNumber = upperNumber.toInt()
                            if (lowerNumber != null) lowerNumber = lowerNumber.toInt()
                        } else if (Long::class == numericConfig.type) {
                            if (upperNumber != null) upperNumber = upperNumber.toLong()
                            if (lowerNumber != null) lowerNumber = lowerNumber.toLong()
                        } else if (Double::class == numericConfig.type) {
                            if (upperNumber != null) upperNumber = upperNumber.toDouble()
                            if (lowerNumber != null) lowerNumber = lowerNumber.toDouble()
                        } else if (Float::class == numericConfig.type) {
                            if (upperNumber != null) upperNumber = upperNumber.toFloat()
                            if (lowerNumber != null) lowerNumber = lowerNumber.toFloat()
                        }

                        val lowerNode =
                            PointQueryNode(termRangeNode.field, lowerNumber, numberFormat)
                        val upperNode =
                            PointQueryNode(termRangeNode.field, upperNumber, numberFormat)

                        val lowerInclusive = termRangeNode.isLowerInclusive
                        val upperInclusive = termRangeNode.isUpperInclusive

                        return PointRangeQueryNode(
                            lowerNode,
                            upperNode,
                            lowerInclusive,
                            upperInclusive,
                            numericConfig,
                        )
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
