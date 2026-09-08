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
package org.gnit.lucenekmp.facet.rangeonrange

/**
 * Base class for a single labeled range.
 *
 * @lucene.experimental
 */
abstract class Range {
    /** Label that identifies this range. */
    val label: String
    /** Number of dims per range */
    val dims: Int

    /** Sole constructor. */
    protected constructor(label: String?, dims: Int) {
        if (label == null) throw NullPointerException("label must not be null")
        this.label = label
        this.dims = dims
    }

    /** Invoke this for a useless range. */
    protected fun failNoMatch(): Nothing {
        throw IllegalArgumentException("range \"$label\" matches nothing")
    }

    companion object {
        /** Gets a corresponding array of labels given a range */
        @kotlin.jvm.JvmStatic
        internal fun getLabelsFromRanges(ranges: Array<out Range>): Array<String> = Array(ranges.size) { ranges[it].label }
    }
}
