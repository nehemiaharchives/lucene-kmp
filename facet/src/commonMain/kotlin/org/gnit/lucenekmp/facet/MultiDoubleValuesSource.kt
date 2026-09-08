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
import org.gnit.lucenekmp.search.DoubleValuesSource
import org.gnit.lucenekmp.search.SegmentCacheable
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Base class for producing [MultiDoubleValues]. See also [DoubleValuesSource] for a
 * single-valued version.
 *
 * MultiDoubleValuesSource objects for NumericDocValues/SortedNumericDocValues fields can be
 * obtained by calling [fromFloatField], [fromDoubleField], [fromIntField], or [fromLongField]. If custom long-to-double logic is
 * required, [fromField] can be used. This is valid for both
 * multi-valued and single-valued fields.
 *
 * To obtain a MultiDoubleValuesSource from an existing [DoubleValuesSource], see [fromSingleValued]. Instances created in this way can be "unwrapped" using
 * [unwrapSingleton] if necessary. Note that scores are never
 * provided to the underlying `DoubleValuesSource`. [DoubleValuesSource.rewrite] will also never be called. The user should be aware of
 * this if using a `DoubleValuesSource` that relies on rewriting or scores. The faceting
 * use-cases don't call rewrite or provide scores, which is why this simplification was made.
 *
 * Currently meant only for use within the faceting module. Could be further generalized and made
 * available for more use-cases outside faceting if there is a desire to do so.
 *
 * @lucene.experimental
 */
abstract class MultiDoubleValuesSource : SegmentCacheable {
    /** Instantiates a new MultiDoubleValuesSource */
    constructor()

    /** Returns a [MultiDoubleValues] instance for the passed-in LeafReaderContext */
    abstract fun getValues(ctx: LeafReaderContext): MultiDoubleValues
    abstract override fun hashCode(): Int
    abstract override fun equals(other: Any?): Boolean
    abstract override fun toString(): String

    /** Convert to a MultiLongValuesSource by casting the double values to longs */
    fun toMultiLongValuesSource(): MultiLongValuesSource = LongDoubleValuesSource(this)

    /** Convert to a [MultiLongValuesSource] using [NumericUtils.doubleToSortableLong] */
    fun toSortableMultiLongValuesSource(): MultiLongValuesSource = SortableMultiLongValuesSource(this)

    private class SortableMultiLongValuesSource(val inner: MultiDoubleValuesSource) : MultiLongValuesSource() {
        override fun isCacheable(ctx: LeafReaderContext): Boolean = inner.isCacheable(ctx)
        override fun getValues(ctx: LeafReaderContext): MultiLongValues {
            val doubleValues = inner.getValues(ctx)
            return object : MultiLongValues() {
                override fun getValueCount(): Long = doubleValues.getValueCount()
                override fun nextValue(): Long = NumericUtils.doubleToSortableLong(doubleValues.nextValue())
                override fun advanceExact(doc: Int): Boolean = doubleValues.advanceExact(doc)
            }
        }
        override fun hashCode(): Int = inner.hashCode()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SortableMultiLongValuesSource) return false
            return inner == other.inner
        }
        override fun toString(): String = "sortableMultiLong($inner)"
    }

    private class FieldMultiValuedSource(private val field: String, private val decoder: (Long) -> Double) : MultiDoubleValuesSource() {
        override fun getValues(ctx: LeafReaderContext): MultiDoubleValues {
            val docValues = DocValues.getSortedNumeric(ctx.reader(), field)
            return object : MultiDoubleValues() {
                override fun getValueCount(): Long = docValues.docValueCount().toLong()
                override fun nextValue(): Double = decoder(docValues.nextValue())
                override fun advanceExact(doc: Int): Boolean = docValues.advanceExact(doc)
            }
        }
        override fun isCacheable(ctx: LeafReaderContext): Boolean = DocValues.isCacheable(ctx, field)
        override fun hashCode(): Int = 31 * (31 + field.hashCode()) + decoder.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is FieldMultiValuedSource) return false
            return field == other.field && decoder == other.decoder
        }
        override fun toString(): String = "multi-double($field)"
    }

    private class SingleValuedAsMultiValued(val `in`: DoubleValuesSource) : MultiDoubleValuesSource() {
        override fun getValues(ctx: LeafReaderContext): MultiDoubleValues {
            val singleValues = `in`.getValues(ctx, null)
            return object : MultiDoubleValues() {
                override fun getValueCount(): Long = 1
                override fun nextValue(): Double = singleValues.doubleValue()
                override fun advanceExact(doc: Int): Boolean = singleValues.advanceExact(doc)
            }
        }
        override fun isCacheable(ctx: LeafReaderContext): Boolean = `in`.isCacheable(ctx)
        override fun hashCode(): Int = 31 + `in`.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is SingleValuedAsMultiValued) return false
            return `in` == other.`in`
        }
        override fun toString(): String = "multi-double(${`in`})"
    }

    private class LongDoubleValuesSource(val `in`: MultiDoubleValuesSource) : MultiLongValuesSource() {
        override fun getValues(ctx: LeafReaderContext): MultiLongValues {
            val doubleValues = `in`.getValues(ctx)
            return object : MultiLongValues() {
                override fun getValueCount(): Long = doubleValues.getValueCount()
                override fun nextValue(): Long = doubleValues.nextValue().toLong()
                override fun advanceExact(doc: Int): Boolean = doubleValues.advanceExact(doc)
            }
        }
        override fun isCacheable(ctx: LeafReaderContext): Boolean = `in`.isCacheable(ctx)
        override fun hashCode(): Int = 31 + `in`.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is LongDoubleValuesSource) return false
            return `in` == other.`in`
        }
        override fun toString(): String = "multi-double(${`in`})"
    }

    companion object {
        /**
   * Creates a MultiDoubleValuesSource that wraps a generic NumericDocValues/SortedNumericDocValues
   * field. Uses the long-to-double decoding logic specified in `decoder` for converting the
   * stored value to a double.
   */
        fun fromField(field: String, decoder: (Long) -> Double): MultiDoubleValuesSource = FieldMultiValuedSource(field, decoder)
        /** Creates a MultiDoubleValuesSource that wraps a double-valued field */
        fun fromDoubleField(field: String): MultiDoubleValuesSource = fromField(field, Double::fromBits)
        /** Creates a MultiDoubleValuesSource that wraps a float-valued field */
        fun fromFloatField(field: String): MultiDoubleValuesSource = fromField(field) { v -> Float.fromBits(v.toInt()).toDouble() }
        /** Creates a MultiDoubleValuesSource that wraps a long-valued field */
        fun fromLongField(field: String): MultiDoubleValuesSource = fromField(field) { v -> v.toDouble() }
        /** Creates a MultiDoubleValuesSource that wraps an int-valued field */
        fun fromIntField(field: String): MultiDoubleValuesSource = fromLongField(field)
        /** Creates a MultiDoubleValuesSource that wraps a single-valued `DoubleValuesSource` */
        fun fromSingleValued(singleValued: DoubleValuesSource): MultiDoubleValuesSource = SingleValuedAsMultiValued(singleValued)
        /**
   * Returns a single-valued view of the `MultiDoubleValuesSource` if it was previously
   * wrapped with [fromSingleValued], or null.
   */
        fun unwrapSingleton(`in`: MultiDoubleValuesSource): DoubleValuesSource? {
            return if (`in` is SingleValuedAsMultiValued) `in`.`in` else null
        }
    }
}
