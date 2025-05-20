package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.search.PointInSetQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils

/**
 * An indexed `float` field for fast range filters. If you also need to store the value, you
 * should add a separate [StoredField] instance.
 *
 *
 * Finding all documents within an N-dimensional at search time is efficient. Multiple values for
 * the same field in one document is allowed.
 *
 *
 * This field defines static factory methods for creating common queries:
 *
 *
 *  * [.newExactQuery] for matching an exact 1D point.
 *  * [.newSetQuery] for matching a set of 1D values.
 *  * [.newRangeQuery] for matching a 1D range.
 *  * [.newRangeQuery] for matching points/ranges in
 * n-dimensional space.
 *
 *
 * @see PointValues
 */
class FloatPoint
/**
 * Creates a new FloatPoint, indexing the provided N-dimensional float point.
 *
 * @param name field name
 * @param point float[] value
 * @throws IllegalArgumentException if the field name or value is null.
 */
    (name: String, vararg point: Float) : Field(name, pack(*point), getType(point.size)) {
    override fun setFloatValue(value: Float) {
        setFloatValues(value)
    }

    /** Change the values of this field  */
    fun setFloatValues(vararg point: Float) {
        require(type.pointDimensionCount() == point.size) {
            ("this field (name="
                    + name
                    + ") uses "
                    + type.pointDimensionCount()
                    + " dimensions; cannot change to (incoming) "
                    + point.size
                    + " dimensions")
        }
        fieldsData = pack(*point)
    }

    override fun setBytesValue(bytes: BytesRef) {
        throw IllegalArgumentException("cannot change value type from float to BytesRef")
    }

    override fun numericValue(): Number {
        check(type.pointDimensionCount() == 1) {
            ("this field (name="
                    + name
                    + ") uses "
                    + type.pointDimensionCount()
                    + " dimensions; cannot convert to a single numeric value")
        }
        val bytes: BytesRef = fieldsData as BytesRef
        require(bytes.length == Float.SIZE_BYTES)
        return decodeDimension(bytes.bytes, bytes.offset)
    }

    override fun toString(): String {
        val result = StringBuilder()
        result.append(this::class.simpleName)
        result.append(" <")
        result.append(name)
        result.append(':')

        val bytes: BytesRef = fieldsData as BytesRef
        for (dim in 0..<type.pointDimensionCount()) {
            if (dim > 0) {
                result.append(',')
            }
            result.append(decodeDimension(bytes.bytes, bytes.offset + dim * Float.SIZE_BYTES))
        }

        result.append('>')
        return result.toString()
    }

    companion object {
        /**
         * Return the least float that compares greater than `f` consistently with [ ][Float.compare]. The only difference with [Math.nextUp] is that this method returns
         * `+0f` when the argument is `-0f`.
         */
        fun nextUp(f: Float): Float {
            if (Float.floatToIntBits(f) == -0x80000000) { // -0f
                return +0f
            }

            return Math.nextUp(f)
        }

        /**
         * Return the greatest float that compares less than `f` consistently with [ ][Float.compare]. The only difference with [Math.nextDown] is that this method
         * returns `-0f` when the argument is `+0f`.
         */
        fun nextDown(f: Float): Float {
            if (Float.floatToIntBits(f) == 0) { // +0f
                return -0f
            }

            return Math.nextDown(f)
        }

        private fun getType(numDims: Int): FieldType {
            val type: FieldType = FieldType()
            type.setDimensions(numDims, Float.SIZE_BYTES)
            type.freeze()
            return type
        }

        /**
         * Pack a float point into a BytesRef
         *
         * @param point float[] value
         * @throws IllegalArgumentException is the value is null or of zero length
         */
        fun pack(vararg point: Float): BytesRef {
            requireNotNull(point) { "point must not be null" }
            require(point.size != 0) { "point must not be 0 dimensions" }
            val packed = ByteArray(point.size * Float.SIZE_BYTES)

            for (dim in point.indices) {
                encodeDimension(point[dim], packed, dim * Float.SIZE_BYTES)
            }

            return BytesRef(packed)
        }

        // public helper methods (e.g. for queries)
        /** Encode single float dimension  */
        fun encodeDimension(value: Float, dest: ByteArray, offset: Int) {
            NumericUtils.intToSortableBytes(
                NumericUtils.floatToSortableInt(
                    value
                ), dest, offset
            )
        }

        /** Decode single float dimension  */
        fun decodeDimension(value: ByteArray, offset: Int): Float {
            return NumericUtils.sortableIntToFloat(
                NumericUtils.sortableBytesToInt(
                    value,
                    offset
                )
            )
        }

        // static methods for generating queries
        /**
         * Create a query for matching an exact float value.
         *
         *
         * This is for simple one-dimension points, for multidimensional points use [ ][.newRangeQuery] instead.
         *
         * @param field field name. must not be `null`.
         * @param value float value
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: Float): Query {
            return newRangeQuery(field, value, value)
        }

        /**
         * Create a range query for float values.
         *
         *
         * This is for simple one-dimension ranges, for multidimensional ranges use [ ][.newRangeQuery] instead.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue = Float.NEGATIVE_INFINITY` or `upperValue = Float.POSITIVE_INFINITY`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass [nextUp(lowerValue)][.nextUp]
         * or [nextDown(upperValue)][.nextUp].
         *
         *
         * Range comparisons are consistent with [Float.compareTo].
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive).
         * @param upperValue upper portion of the range (inclusive).
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(field: String, lowerValue: Float, upperValue: Float): Query {
            return newRangeQuery(field, floatArrayOf(lowerValue), floatArrayOf(upperValue))
        }

        /**
         * Create a range query for n-dimensional float values.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue[i] = Float.NEGATIVE_INFINITY` or `upperValue[i] =
         * Float.POSITIVE_INFINITY`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass `Math#nextUp(lowerValue[i])` or
         * `Math.nextDown(upperValue[i])`.
         *
         *
         * Range comparisons are consistent with [Float.compareTo].
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive). must not be `null`.
         * @param upperValue upper portion of the range (inclusive). must not be `null`.
         * @throws IllegalArgumentException if `field` is null, if `lowerValue` is null, if
         * `upperValue` is null, or if `lowerValue.length != upperValue.length`
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(
            field: String,
            lowerValue: FloatArray,
            upperValue: FloatArray
        ): Query {
            PointRangeQuery.checkArgs(field, lowerValue, upperValue)
            return object : PointRangeQuery(
                field, pack(*lowerValue).bytes, pack(*upperValue).bytes, lowerValue.size
            ) {
                override fun toString(dimension: Int, value: ByteArray): String {
                    return decodeDimension(value, 0).toString()
                }
            }
        }

        /**
         * Create a query matching any of the specified 1D values. This is the points equivalent of `TermsQuery`.
         *
         * @param field field name. must not be `null`.
         * @param values all values to match
         */
        fun newSetQuery(field: String, vararg values: Float): Query {
            // Don't unexpectedly change the user's incoming values array:

            val sortedValues = values.copyOf()
            Arrays.sort(sortedValues)

            val encoded = BytesRef(ByteArray(Float.SIZE_BYTES))

            return object : PointInSetQuery(
                field,
                1,
                Float.SIZE_BYTES,
                object : Stream() {
                    var upto: Int = 0

                    override fun next(): BytesRef? {
                        if (upto == sortedValues.size) {
                            return null
                        } else {
                            encodeDimension(sortedValues[upto], encoded.bytes, 0)
                            upto++
                            return encoded
                        }
                    }
                }) {
                override fun toString(value: ByteArray): String {
                    require(value.size == Float.SIZE_BYTES)
                    return decodeDimension(value, 0).toString()
                }
            }
        }

        /**
         * Create a query matching any of the specified 1D values. This is the points equivalent of `TermsQuery`.
         *
         * @param field field name. must not be `null`.
         * @param values all values to match
         */
        fun newSetQuery(field: String, values: MutableCollection<Float>): Query {
            val boxed = values.toTypedArray<Float>()
            val unboxed = FloatArray(boxed.size)
            for (i in boxed.indices) {
                unboxed[i] = boxed[i]
            }
            return newSetQuery(field, *unboxed)
        }
    }
}
