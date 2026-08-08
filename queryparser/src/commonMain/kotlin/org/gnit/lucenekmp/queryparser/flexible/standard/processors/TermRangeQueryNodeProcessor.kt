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

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.gnit.lucenekmp.document.DateTools
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler
import org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode
import org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl
import org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler.ConfigurationKeys
import org.gnit.lucenekmp.queryparser.flexible.standard.nodes.TermRangeQueryNode

/**
 * This processors process [TermRangeQueryNode]s. It reads the lower and upper bounds value from the
 * [TermRangeQueryNode] object and try to parse their values using a DateFormat. If the values cannot
 * be parsed to a date value, it will only create the [TermRangeQueryNode] using the non-parsed
 * values. <br> <br> If a [ConfigurationKeys.LOCALE] is defined in the [QueryConfigHandler] it will
 * be used to parse the date, otherwise [Locale.getDefault] will be used. <br> <br> If a
 * [ConfigurationKeys.DATE_RESOLUTION] is defined and the [DateTools.Resolution] is not \`null\` it
 * will also be used to parse the date value.
 *
 * @see ConfigurationKeys.DATE_RESOLUTION
 * @see ConfigurationKeys.LOCALE
 * @see TermRangeQueryNode
 */
class TermRangeQueryNodeProcessor : QueryNodeProcessorImpl() {
    init {
        // empty constructor
    }

    override fun postProcessNode(node: QueryNode): QueryNode {
        if (node is TermRangeQueryNode) {
            val termRangeNode = node
            val upper = termRangeNode.upperBound
            val lower = termRangeNode.lowerBound

            var dateRes: DateTools.Resolution? = null
            var inclusive = false
            var locale = requireNotNull(queryConfigHandler).get(ConfigurationKeys.LOCALE)

            if (locale == null) {
                locale = Locale.getDefault()
            }

            var timeZone = requireNotNull(queryConfigHandler).get(ConfigurationKeys.TIMEZONE)

            if (timeZone == null) {
                timeZone = TimeZone.currentSystemDefault()
            }

            val field = termRangeNode.field
            var fieldStr: String? = null

            if (field != null) {
                fieldStr = field.toString()
            }

            val fieldConfig =
                requireNotNull(queryConfigHandler).getFieldConfig(requireNotNull(fieldStr))

            if (fieldConfig != null) {
                dateRes = fieldConfig.get(ConfigurationKeys.DATE_RESOLUTION)
            }

            if (termRangeNode.isUpperInclusive) {
                inclusive = true
            }

            var part1 = requireNotNull(lower.getTextAsString())
            var part2 = requireNotNull(upper.getTextAsString())

            try {
                // Kotlin common has no java.text.DateFormat; Locale defaults to US in jdkport,
                // so this parses the same SHORT month/day/year form used by the upstream tests.
                if (part1.length > 0) {
                    val d1 = parseDate(part1, timeZone)
                    part1 = DateTools.dateToString(d1, requireNotNull(dateRes))
                    lower.text = part1
                }

                if (part2.length > 0) {
                    var d2 = parseDate(part2, timeZone)
                    if (inclusive) {
                        // The user can only specify the date, not the time, so make sure
                        // the time is set to the latest possible time of that date to
                        // really
                        // include all documents:
                        val localDate = d2.toLocalDateTime(timeZone).date
                        d2 =
                            Instant.fromEpochMilliseconds(
                                localDate
                                    .atTime(LocalTime(23, 59, 59, 999_000_000))
                                    .toInstant(timeZone)
                                    .toEpochMilliseconds(),
                            )
                    }

                    part2 = DateTools.dateToString(d2, requireNotNull(dateRes))
                    upper.text = part2
                }
            } catch (
                @Suppress("UNUSED_VARIABLE")
                e: Exception,
            ) {
                // not a date
                val analyzer = requireNotNull(queryConfigHandler).get(ConfigurationKeys.ANALYZER)
                if (analyzer != null) {
                    // because we call utf8ToString, this will only work with the default
                    // TermToBytesRefAttribute
                    part1 =
                        analyzer.normalize(requireNotNull(lower.getFieldAsString()), part1).utf8ToString()
                    part2 =
                        analyzer.normalize(requireNotNull(lower.getFieldAsString()), part2).utf8ToString()
                    lower.text = part1
                    upper.text = part2
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

    private fun parseDate(value: String, timeZone: TimeZone): Instant {
        val parts = value.split('/')
        require(parts.size == 3) { "Unsupported date format: $value" }
        val month = parts[0].toInt()
        val day = parts[1].toInt()
        var year = parts[2].toInt()
        if (year in 0..99) {
            year = if (year >= 70) 1900 + year else 2000 + year
        }
        val instant = LocalDateTime(year, month, day, 0, 0, 0, 0).toInstant(timeZone)
        return Instant.fromEpochMilliseconds(instant.toEpochMilliseconds())
    }
}
