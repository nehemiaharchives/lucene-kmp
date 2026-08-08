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
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.RangeQueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfig
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointQueryNode
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointRangeQueryNode

/**
 * This processor is used to convert [FieldQueryNode]s to [PointRangeQueryNode]s. It looks for
 * [ConfigurationKeys.POINTS_CONFIG] set in the FieldConfig of every [FieldQueryNode] found. If
 * [ConfigurationKeys.POINTS_CONFIG] is found, it considers that [FieldQueryNode] to be a numeric
 * query and convert it to [PointRangeQueryNode] with upper and lower inclusive and lower and upper
 * equals to the value represented by the [FieldQueryNode] converted to [Number]. It means that
 * **field:1** is converted to **field:[1 TO 1]**. <br> <br> Note that [FieldQueryNode]s children of
 * a [RangeQueryNode] are ignored.
 *
 * @see ConfigurationKeys.POINTS_CONFIG
 * @see FieldQueryNode
 * @see PointsConfig
 * @see PointQueryNode
 */
class PointQueryNodeProcessor : QueryNodeProcessorImpl() {
    /** Constructs a [PointQueryNodeProcessor] object. */
    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is FieldQueryNode && node.parent !is RangeQueryNode<*>) {
            val config: QueryConfigHandler? = queryConfigHandler

            if (config != null) {
                val fieldNode = node
                val fieldConfig =
                    config.getFieldConfig(requireNotNull(fieldNode.getFieldAsString()))

                if (fieldConfig != null) {
                    val numericConfig = fieldConfig.get(ConfigurationKeys.POINTS_CONFIG)

                    if (numericConfig != null) {
                        val numberFormat = numericConfig.numberFormat
                        val text = requireNotNull(fieldNode.getTextAsString())
                        var number: Number?

                        if (text.length > 0) {
                            try {
                                number = numberFormat.parse(text)
                            } catch (e: ParseException) {
                                throw QueryNodeParseException(
                                    MessageImpl(
                                        QueryParserMessages.COULD_NOT_PARSE_NUMBER,
                                        fieldNode.getTextAsString(),
                                        numberFormat::class.qualifiedName,
                                    ),
                                    e,
                                )
                            }

                            if (Int::class == numericConfig.type) {
                                number = number.toInt()
                            } else if (Long::class == numericConfig.type) {
                                number = number.toLong()
                            } else if (Double::class == numericConfig.type) {
                                number = number.toDouble()
                            } else if (Float::class == numericConfig.type) {
                                number = number.toFloat()
                            }
                        } else {
                            throw QueryNodeParseException(
                                MessageImpl(
                                    QueryParserMessages.NUMERIC_CANNOT_BE_EMPTY,
                                    fieldNode.getFieldAsString(),
                                ),
                            )
                        }

                        val lowerNode =
                            PointQueryNode(fieldNode.field, number, numberFormat)
                        val upperNode =
                            PointQueryNode(fieldNode.field, number, numberFormat)

                        return PointRangeQueryNode(lowerNode, upperNode, true, true, numericConfig)
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
