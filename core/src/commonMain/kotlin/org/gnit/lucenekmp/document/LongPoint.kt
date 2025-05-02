package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.search.PointInSetQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils

/**
 * An indexed `long` field for fast range filters. If you also need to store the value, you
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
class LongPoint
/**
 * Creates a new LongPoint, indexing the provided N-dimensional long point.
 *
 * @param name field name
 * @param point long[] value
 * @throws IllegalArgumentException if the field name or value is null.
 */
    (name: String, vararg point: Long) : Field(name, pack(*point), getType(point.size)) {
    override fun setLongValue(value: Long) {
        setLongValues(value)
    }

    /** Change the values of this field  */
    fun setLongValues(vararg point: Long) {
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
        throw IllegalArgumentException("cannot change value type from long to BytesRef")
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
        require(bytes.length == Long.SIZE_BYTES)
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
            result.append(decodeDimension(bytes.bytes, bytes.offset + dim * Long.SIZE_BYTES))
        }

        result.append('>')
        return result.toString()
    }

    companion object {
        private fun getType(numDims: Int): FieldType {
            val type = FieldType()
            type.setDimensions(numDims, Long.SIZE_BYTES)
            type.freeze()
            return type
        }

        /**
         * Pack a long point into a BytesRef
         *
         * @param point long[] value
         * @throws IllegalArgumentException is the value is null or of zero length
         */
        fun pack(vararg point: Long): BytesRef {
            requireNotNull(point) { "point must not be null" }
            require(point.isNotEmpty()) { "point must not be 0 dimensions" }
            val packed = ByteArray(point.size * Long.SIZE_BYTES)

            for (dim in point.indices) {
                encodeDimension(point[dim], packed, dim * Long.SIZE_BYTES)
            }

            return BytesRef(packed)
        }

        /**
         * Unpack a BytesRef into a long point. This method can be used to unpack values that were packed
         * with [.pack].
         *
         * @param bytesRef BytesRef Value
         * @param start the start offset to unpack the values from
         * @param buf the buffer to store the values in
         * @throws IllegalArgumentException if bytesRef or buf are null
         */
        fun unpack(bytesRef: BytesRef?, start: Int, buf: LongArray?) {
            require(!(bytesRef == null || buf == null)) { "bytesRef and buf must not be null" }

            var i = 0
            var offset = start
            while (i < buf.size) {
                buf[i] = decodeDimension(bytesRef.bytes, offset)
                i++
                offset += Long.SIZE_BYTES
            }
        }

        // public helper methods (e.g. for queries)
        /** Encode single long dimension  */
        fun encodeDimension(value: Long, dest: ByteArray, offset: Int) {
            NumericUtils.longToSortableBytes(value, dest, offset)
        }

        /** Decode single long dimension  */
        fun decodeDimension(value: ByteArray, offset: Int): Long {
            return NumericUtils.sortableBytesToLong(value, offset)
        }

        // static methods for generating queries
        /**
         * Create a query for matching an exact long value.
         *
         *
         * This is for simple one-dimension points, for multidimensional points use [ ][.newRangeQuery] instead.
         *
         * @param field field name. must not be `null`.
         * @param value exact value
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: Long): Query {
            return newRangeQuery(field, value, value)
        }

        /**
         * Create a range query for long values.
         *
         *
         * This is for simple one-dimension ranges, for multidimensional ranges use [ ][.newRangeQuery] instead.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue = Long.MIN_VALUE` or `upperValue = Long.MAX_VALUE`.
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
        fun newRangeQuery(field: String, lowerValue: Long, upperValue: Long): Query {
            return newRangeQuery(field, longArrayOf(lowerValue), longArrayOf(upperValue))
        }

        /**
         * Create a range query for n-dimensional long values.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue[i] = Long.MIN_VALUE` or `upperValue[i] = Long.MAX_VALUE`.
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
        fun newRangeQuery(field: String, lowerValue: LongArray, upperValue: LongArray): Query {
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
        fun newSetQuery(field: String, vararg values: Long): Query {
            // Don't unexpectedly change the user's incoming values array:

            val sortedValues = values.copyOf() as LongArray // this cast is needed for kotlin/native target compilation to pass
            Arrays.sort(sortedValues)

            val encoded = BytesRef(ByteArray(Long.SIZE_BYTES))

            return object : PointInSetQuery(
                field,
                1,
                Long.SIZE_BYTES,
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
                    require(value.size == Long.SIZE_BYTES)
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
        fun newSetQuery(field: String, values: MutableCollection<Long>): Query {
            val boxed = values.toTypedArray<Long>()
            val unboxed = LongArray(boxed.size)
            for (i in boxed.indices) {
                unboxed[i] = boxed[i]
            }
            return newSetQuery(field, *unboxed)
        }
    }
}
