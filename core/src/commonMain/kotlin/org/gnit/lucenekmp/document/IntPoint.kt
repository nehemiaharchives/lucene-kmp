package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.search.PointInSetQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils

/**
 * An indexed `int` field for fast range filters. If you also need to store the value, you
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
 *  * [.newRangeQuery] for matching points/ranges in n-dimensional
 * space.
 *
 *
 * @see PointValues
 */
class IntPoint
/**
 * Creates a new IntPoint, indexing the provided N-dimensional int point.
 *
 * @param name field name
 * @param point int[] value
 * @throws IllegalArgumentException if the field name or value is null.
 */
    (name: String, vararg point: Int) : Field(name, pack(*point), getType(point.size)) {
    override fun setIntValue(value: Int) {
        setIntValues(value)
    }

    /** Change the values of this field  */
    fun setIntValues(vararg point: Int) {
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
        throw IllegalArgumentException("cannot change value type from int to BytesRef")
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
        require(bytes.length == Int.SIZE_BYTES)
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
            result.append(decodeDimension(bytes.bytes, bytes.offset + dim * Int.SIZE_BYTES))
        }

        result.append('>')
        return result.toString()
    }

    companion object {
        private fun getType(numDims: Int): FieldType {
            val type = FieldType()
            type.setDimensions(numDims, Int.SIZE_BYTES)
            type.freeze()
            return type
        }

        /**
         * Pack an integer point into a BytesRef
         *
         * @param point int[] value
         * @throws IllegalArgumentException is the value is null or of zero length
         */
        fun pack(vararg point: Int): BytesRef {
            requireNotNull(point) { "point must not be null" }
            require(point.size != 0) { "point must not be 0 dimensions" }
            val packed = ByteArray(point.size * Int.SIZE_BYTES)

            for (dim in point.indices) {
                encodeDimension(point[dim], packed, dim * Int.SIZE_BYTES)
            }

            return BytesRef(packed)
        }

        // public helper methods (e.g. for queries)
        /** Encode single integer dimension  */
        fun encodeDimension(value: Int, dest: ByteArray, offset: Int) {
            NumericUtils.intToSortableBytes(value, dest, offset)
        }

        /** Decode single integer dimension  */
        fun decodeDimension(value: ByteArray, offset: Int): Int {
            return NumericUtils.sortableBytesToInt(value, offset)
        }

        // static methods for generating queries
        /**
         * Create a query for matching an exact integer value.
         *
         *
         * This is for simple one-dimension points, for multidimensional points use [ ][.newRangeQuery] instead.
         *
         * @param field field name. must not be `null`.
         * @param value exact value
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: Int): Query {
            return newRangeQuery(field, value, value)
        }

        /**
         * Create a range query for integer values.
         *
         *
         * This is for simple one-dimension ranges, for multidimensional ranges use [ ][.newRangeQuery] instead.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue = Integer.MIN_VALUE` or `upperValue = Integer.MAX_VALUE`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass `Math.addExact(lowerValue, 1)` or
         * `Math.addExact(upperValue, -1)`.
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive).
         * @param upperValue upper portion of the range (inclusive).
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(field: String, lowerValue: Int, upperValue: Int): Query {
            return newRangeQuery(field, intArrayOf(lowerValue), intArrayOf(upperValue))
        }

        /**
         * Create a range query for n-dimensional integer values.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue[i] = Integer.MIN_VALUE` or `upperValue[i] = Integer.MAX_VALUE`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass `Math.addExact(lowerValue[i], 1)` or
         * `Math.addExact(upperValue[i], -1)`.
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive). must not be `null`.
         * @param upperValue upper portion of the range (inclusive). must not be `null`.
         * @throws IllegalArgumentException if `field` is null, if `lowerValue` is null, if
         * `upperValue` is null, or if `lowerValue.length != upperValue.length`
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(field: String, lowerValue: IntArray, upperValue: IntArray): Query {
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
        fun newSetQuery(field: String, vararg values: Int): Query {
            // Don't unexpectedly change the user's incoming values array:

            val sortedValues = values.clone()
            Arrays.sort(sortedValues)

            val encoded = BytesRef(ByteArray(Int.SIZE_BYTES))

            return object : PointInSetQuery(
                field,
                1,
                Int.SIZE_BYTES,
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
                    require(value.size == Int.SIZE_BYTES)
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
        fun newSetQuery(field: String, values: MutableCollection<Int>): Query {
            val boxed = values.toTypedArray<Int>()
            val unboxed = IntArray(boxed.size)
            for (i in boxed.indices) {
                unboxed[i] = boxed[i]!!
            }
            return newSetQuery(field, *unboxed)
        }
    }
}
