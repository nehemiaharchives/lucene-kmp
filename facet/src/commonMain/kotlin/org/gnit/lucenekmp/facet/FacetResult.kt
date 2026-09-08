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
package org.gnit.lucenekmp.facet

/** Counts or aggregates for a single dimension. */
class FacetResult {
    /** Dimension that was requested. */
    val dim: String

    /** Path whose children were requested. */
    val path: Array<String>

    /**
   * Total number of documents containing a value for this path, even those not included in the
   * topN. If a document contains multiple values for the same path, it will only be counted once in
   * this value.
   */
    val value: Number

    /** How many child labels were encountered. */
    val childCount: Int

    /** Child counts. */
    val labelValues: Array<LabelAndValue>

    /** Sole constructor. */
    constructor(dim: String, path: Array<String>, value: Number, labelValues: Array<LabelAndValue>, childCount: Int) {
        this.dim = dim
        this.path = path
        this.value = value
        this.labelValues = labelValues
        this.childCount = childCount
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("dim=")
        sb.append(dim)
        sb.append(" path=")
        sb.append(path.contentToString())
        sb.append(" value=")
        sb.append(value)
        sb.append(" childCount=")
        sb.append(childCount)
        sb.append('\n')
        for (labelValue in labelValues) {
            sb.append("  ").append(labelValue).append("\n")
        }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FacetResult) {
            return false
        }
        return value == other.value && childCount == other.childCount && labelValues.contentEquals(other.labelValues)
    }

    override fun hashCode(): Int {
        var hashCode = value.hashCode() + 31 * childCount
        for (labelValue in labelValues) {
            hashCode = labelValue.hashCode() + 31 * hashCode
        }
        return hashCode
    }
}
