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

import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.LongValuesSource
import org.gnit.lucenekmp.search.SegmentCacheable
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Base class for producing [MultiLongValues]. See also [LongValuesSource] for a
 * single-valued version.
 *
 * MultiLongValuesSource objects for long and int-valued NumericDocValues/SortedNumericDocValues
 * fields can be obtained by calling [fromLongField] and [fromIntField]. This is valid for both multi-valued and single-valued fields.
 *
 * To obtain a MultiLongValuesSource from a float or double-valued
 * NumericDocValues/SortedNumericDocValues field, use [MultiDoubleValuesSource.fromFloatField] or [MultiDoubleValuesSource.fromDoubleField] and then call [MultiDoubleValuesSource.toMultiLongValuesSource].
 *
 * To obtain a MultiLongValuesSource from an existing [LongValuesSource], see [fromSingleValued]. Instances created in this way can be "unwrapped" using
 * [unwrapSingleton] if necessary. Note that scores are never provided
 * to the underlying `LongValuesSource`. [LongValuesSource.rewrite] will
 * also never be called. The user should be aware of this if using a `LongValuesSource` that
 * relies on rewriting or scores. The faceting use-cases don't call rewrite or provide scores, which
 * is why this simplification was made.
 *
 * Currently meant only for use within the faceting module. Could be further generalized and made
 * available for more use-cases outside faceting if there is a desire to do so.
 *
 * @lucene.experimental
 */
abstract class MultiLongValuesSource : SegmentCacheable {
    /** Instantiates a new MultiLongValuesSource */
    constructor()

    /** Returns a [MultiLongValues] instance for the passed-in LeafReaderContext */
    abstract fun getValues(ctx: LeafReaderContext): MultiLongValues
    abstract override fun hashCode(): Int
    abstract override fun equals(other: Any?): Boolean
    abstract override fun toString(): String

    /** Convert to a MultiDoubleValuesSource by casting long values to doubles */
    fun toMultiDoubleValuesSource(): MultiDoubleValuesSource = DoubleLongValuesSources(this)

    private class FieldMultiValueSource(private val field: String) : MultiLongValuesSource() {
        override fun getValues(ctx: LeafReaderContext): MultiLongValues {
            val docValues = DocValues.getSortedNumeric(ctx.reader(), field)
            return object : MultiLongValues() {
                override fun getValueCount(): Long = docValues.docValueCount().toLong()
                override fun nextValue(): Long = docValues.nextValue()
                override fun advanceExact(doc: Int): Boolean = docValues.advanceExact(doc)
            }
        }
        override fun isCacheable(ctx: LeafReaderContext): Boolean = DocValues.isCacheable(ctx, field)
        override fun hashCode(): Int = 31 + field.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is FieldMultiValueSource) return false
            return field == other.field
        }
        override fun toString(): String = "multi-long($field)"
    }

    private class SingleValuedAsMultiValued(val `in`: LongValuesSource) : MultiLongValuesSource() {
        override fun getValues(ctx: LeafReaderContext): MultiLongValues {
            val singleValued = `in`.getValues(ctx, null)
            return object : MultiLongValues() {
                override fun getValueCount(): Long = 1
                override fun nextValue(): Long = singleValued.longValue()
                override fun advanceExact(doc: Int): Boolean = singleValued.advanceExact(doc)
            }
        }
        override fun isCacheable(ctx: LeafReaderContext): Boolean = `in`.isCacheable(ctx)
        override fun hashCode(): Int = 31 + `in`.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is SingleValuedAsMultiValued) return false
            return `in` == other.`in`
        }
        override fun toString(): String = "multi-long(${`in`})"
    }

    private class DoubleLongValuesSources(val `in`: MultiLongValuesSource) : MultiDoubleValuesSource() {
        override fun getValues(ctx: LeafReaderContext): MultiDoubleValues {
            val longValues = `in`.getValues(ctx)
            return object : MultiDoubleValues() {
                override fun getValueCount(): Long = longValues.getValueCount()
                override fun nextValue(): Double = longValues.nextValue().toDouble()
                override fun advanceExact(doc: Int): Boolean = longValues.advanceExact(doc)
            }
        }
        override fun isCacheable(ctx: LeafReaderContext): Boolean = `in`.isCacheable(ctx)
        override fun hashCode(): Int = 31 + `in`.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is DoubleLongValuesSources) return false
            return `in` == other.`in`
        }
        override fun toString(): String = "multi-long(${`in`})"
    }

    companion object {
        /** Creates a MultiLongValuesSource that wraps a long-valued field */
        fun fromLongField(field: String): MultiLongValuesSource = FieldMultiValueSource(field)
        /** Creates a MultiLongValuesSource that wraps an int-valued field */
        fun fromIntField(field: String): MultiLongValuesSource = fromLongField(field)
        /** Creates a MultiLongValuesSource that wraps a single-valued `LongValuesSource` */
        fun fromSingleValued(singleValued: LongValuesSource): MultiLongValuesSource = SingleValuedAsMultiValued(singleValued)
        /**
   * Returns a single-valued view of the `MultiLongValuesSource` if it was previously wrapped
   * with [fromSingleValued], or null.
   */
        fun unwrapSingleton(`in`: MultiLongValuesSource): LongValuesSource? {
            return if (`in` is SingleValuedAsMultiValued) `in`.`in` else null
        }
    }
}
