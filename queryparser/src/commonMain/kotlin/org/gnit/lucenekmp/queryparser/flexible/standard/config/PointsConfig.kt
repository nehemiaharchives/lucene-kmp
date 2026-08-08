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

import kotlin.reflect.KClass
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.NumberFormat

/**
 * This class holds the configuration used to parse numeric queries and create [PointValues]
 * queries.
 *
 * @see PointValues
 * @see NumberFormat
 */
class PointsConfig(format: NumberFormat, type: KClass<out Number>) {
    /**
     * Returns the [NumberFormat] used to parse a [String] to [Number]
     *
     * @return the [NumberFormat] used to parse a [String] to [Number]
     */
    var numberFormat: NumberFormat = format
        /**
         * Sets the [NumberFormat] used to parse a [String] to [Number]
         *
         * @param format the [NumberFormat] used to parse a [String] to [Number], must not be
         *     `null`
         */
        set(format) {
            field = format
        }

    /**
     * Returns the numeric type used to index the numeric values
     *
     * @return the numeric type used to index the numeric values
     */
    var type: KClass<out Number> = type
        /**
         * Sets the numeric type used to index the numeric values
         *
         * @param type the numeric type used to index the numeric values
         */
        set(type) {
            if (type != Int::class &&
                type != Long::class &&
                type != Float::class &&
                type != Double::class
            ) {
                throw IllegalArgumentException("unsupported numeric type: $type")
            }
            field = type
        }

    /**
     * Constructs a [PointsConfig] object.
     *
     * @param format the [NumberFormat] used to parse a [String] to [Number]
     * @param type the numeric type used to index the numeric values
     * @see numberFormat
     */
    init {
        numberFormat = format
        this.type = type
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + numberFormat.hashCode()
        result = prime * result + type.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false
        other as PointsConfig
        if (numberFormat != other.numberFormat) return false
        if (type != other.type) return false
        return true
    }
}
