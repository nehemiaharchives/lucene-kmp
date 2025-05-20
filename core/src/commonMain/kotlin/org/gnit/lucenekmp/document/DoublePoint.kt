package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.search.PointInSetQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.math.nextDown
import kotlin.math.nextUp

/**
 * An indexed `double` field for fast range filters. If you also need to store the value, you
 * should add a separate [StoredField] instance.
 *
 *
 * Finding all documents within an N-dimensional shape or range at search time is efficient.
 * Multiple values for the same field in one document is allowed.
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
class DoublePoint
/**
 * Creates a new DoublePoint, indexing the provided N-dimensional double point.
 *
 * @param name field name
 * @param point double[] value
 * @throws IllegalArgumentException if the field name or value is null.
 */
    (name: String, vararg point: Double) : Field(name, pack(*point), getType(point.size)) {
    override fun setDoubleValue(value: Double) {
        setDoubleValues(value)
    }

    /** Change the values of this field  */
    fun setDoubleValues(vararg point: Double) {
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
        throw IllegalArgumentException("cannot change value type from double to BytesRef")
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
        require(bytes.length == Double.SIZE_BYTES)
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
            result.append(decodeDimension(bytes.bytes, bytes.offset + dim * Double.SIZE_BYTES))
        }

        result.append('>')
        return result.toString()
    }

    companion object {
        /**
         * Return the least double that compares greater than `d` consistently with [ ][Double.compare]. The only difference with [Math.nextUp] is that this method
         * returns `+0d` when the argument is `-0d`.
         */
        fun nextUp(d: Double): Double {
            if (Double.doubleToLongBits(d) == Long.MIN_VALUE) { // -0d
                return +0.0
            }
            return d.nextUp()
        }

        /**
         * Return the greatest double that compares less than `d` consistently with [ ][Double.compare]. The only difference with [Math.nextDown] is that this method
         * returns `-0d` when the argument is `+0d`.
         */
        fun nextDown(d: Double): Double {
            if (Double.doubleToLongBits(d) == 0L) { // +0d
                return -0.0
            }
            return d.nextDown()
        }

        private fun getType(numDims: Int): FieldType {
            val type = FieldType()
            type.setDimensions(numDims, Double.SIZE_BYTES)
            type.freeze()
            return type
        }

        /**
         * Pack a double point into a BytesRef
         *
         * @param point double[] value
         * @throws IllegalArgumentException is the value is null or of zero length
         */
        fun pack(vararg point: Double): BytesRef {
            requireNotNull(point) { "point must not be null" }
            require(point.isNotEmpty()) { "point must not be 0 dimensions" }
            val packed = ByteArray(point.size * Double.SIZE_BYTES)

            for (dim in point.indices) {
                encodeDimension(point[dim], packed, dim * Double.SIZE_BYTES)
            }

            return BytesRef(packed)
        }

        // public helper methods (e.g. for queries)
        /** Encode single double dimension  */
        fun encodeDimension(value: Double, dest: ByteArray, offset: Int) {
            NumericUtils.longToSortableBytes(
                NumericUtils.doubleToSortableLong(
                    value
                ), dest, offset
            )
        }

        /** Decode single double dimension  */
        fun decodeDimension(value: ByteArray, offset: Int): Double {
            return NumericUtils.sortableLongToDouble(
                NumericUtils.sortableBytesToLong(
                    value,
                    offset
                )
            )
        }

        // static methods for generating queries
        /**
         * Create a query for matching an exact double value.
         *
         *
         * This is for simple one-dimension points, for multidimensional points use [ ][.newRangeQuery] instead.
         *
         * @param field field name. must not be `null`.
         * @param value double value
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: Double): Query {
            return newRangeQuery(field, value, value)
        }

        /**
         * Create a range query for double values.
         *
         *
         * This is for simple one-dimension ranges, for multidimensional ranges use [ ][.newRangeQuery] instead.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue = Double.NEGATIVE_INFINITY` or `upperValue = Double.POSITIVE_INFINITY`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass [nextUp(lowerValue)][.nextUp]
         * or [nextDown(upperValue)][.nextUp].
         *
         *
         * Range comparisons are consistent with [Double.compareTo].
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive).
         * @param upperValue upper portion of the range (inclusive).
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(field: String, lowerValue: Double, upperValue: Double): Query {
            return newRangeQuery(field, doubleArrayOf(lowerValue), doubleArrayOf(upperValue))
        }

        /**
         * Create a range query for n-dimensional double values.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue[i] = Double.NEGATIVE_INFINITY` or `upperValue[i] =
         * Double.POSITIVE_INFINITY`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass `Math#nextUp(lowerValue[i])` or
         * `Math.nextDown(upperValue[i])`.
         *
         *
         * Range comparisons are consistent with [Double.compareTo].
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
            lowerValue: DoubleArray,
            upperValue: DoubleArray
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
        fun newSetQuery(field: String, vararg values: Double): Query {
            // Don't unexpectedly change the user's incoming values array:

            val sortedValues = values.copyOf()
            Arrays.sort(sortedValues)

            val encoded = BytesRef(ByteArray(Double.SIZE_BYTES))

            return object : PointInSetQuery(
                field,
                1,
                Double.SIZE_BYTES,
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
                    require(value.size == Double.SIZE_BYTES)
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
        fun newSetQuery(field: String, values: MutableCollection<Double>): Query {
            val boxed = values.toTypedArray<Double>()
            val unboxed = DoubleArray(boxed.size)
            for (i in boxed.indices) {
                unboxed[i] = boxed[i]
            }
            return newSetQuery(field, *unboxed)
        }
    }
}
