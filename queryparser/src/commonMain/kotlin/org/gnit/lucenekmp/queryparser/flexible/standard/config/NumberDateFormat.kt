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
package org.gnit.lucenekmp.queryparser.flexible.standard.config

import org.gnit.lucenekmp.jdkport.Date
import org.gnit.lucenekmp.jdkport.DateFormat
import org.gnit.lucenekmp.jdkport.FieldPosition
import org.gnit.lucenekmp.jdkport.NumberFormat
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.ParsePosition

/**
 * This [NumberFormat] parses [Long] into date strings and vice-versa. It uses the given
 * [DateFormat] to parse and format dates, but before, it converts [Long] to [Date] objects or
 * vice-versa.
 */
class NumberDateFormat(
    /**
     * Constructs a [NumberDateFormat] object using the given [DateFormat].
     *
     * @param dateFormat [DateFormat] used to parse and format dates
     */
    private val dateFormat: DateFormat
) : NumberFormat() {

    override fun format(
        number: Double,
        toAppendTo: StringBuilder,
        pos: FieldPosition
    ): StringBuilder {
        return dateFormat.format(Date(number.toLong()), toAppendTo, pos)
    }

    override fun format(
        number: Long,
        toAppendTo: StringBuilder,
        pos: FieldPosition
    ): StringBuilder {
        return dateFormat.format(Date(number), toAppendTo, pos)
    }

    override fun parse(source: String, parsePosition: ParsePosition): Number? {
        val date: Date? = dateFormat.parse(source, parsePosition)
        return date?.time
    }

    override fun format(
        number: Any,
        toAppendTo: StringBuilder,
        pos: FieldPosition
    ): StringBuilder {
        return dateFormat.format(number, toAppendTo, pos)
    }

    override fun parse(source: String): Number {
        val parsePosition = ParsePosition(0)
        return parse(source, parsePosition)
            ?: throw ParseException("Unparseable date: \"$source\"", parsePosition.errorIndex)
    }
}
