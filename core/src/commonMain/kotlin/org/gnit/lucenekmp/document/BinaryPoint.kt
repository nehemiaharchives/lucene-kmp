package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.IndexableFieldType
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.toHexString
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.PointInSetQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef

/**
 * An indexed binary field for fast range filters. If you also need to store the value, you should
 * add a separate [StoredField] instance.
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
 *  * [newSetQuery(String, byte[]...)][.newSetQuery] for matching a set of
 * 1D values.
 *  * [.newRangeQuery] for matching a 1D range.
 *  * [.newRangeQuery] for matching points/ranges in
 * n-dimensional space.
 *
 *
 * @see PointValues
 */
class BinaryPoint : Field {
    /**
     * General purpose API: creates a new BinaryPoint, indexing the provided N-dimensional binary
     * point.
     *
     * @param name field name
     * @param point byte[][] value
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(name: String, point: Array<ByteArray>) : super(name, pack(*point), getType(point))

    /** Expert API  */
    constructor(name: String, packedPoint: ByteArray, type: IndexableFieldType) : super(
        name,
        packedPoint,
        type
    ) {
        require(packedPoint.size == type.pointDimensionCount() * type.pointNumBytes()) {
            ("packedPoint is length="
                    + packedPoint.size
                    + " but type.pointDimensionCount()="
                    + type.pointDimensionCount()
                    + " and type.pointNumBytes()="
                    + type.pointNumBytes())
        }
    }

    companion object {
        private fun getType(point: Array<ByteArray>): FieldType {
            requireNotNull(point) { "point must not be null" }
            require(point.isNotEmpty()) { "point must not be 0 dimensions" }
            var bytesPerDim = -1
            for (i in point.indices) {
                val oneDim = point[i]
                requireNotNull(oneDim) { "point must not have null values" }
                require(oneDim.isNotEmpty()) { "point must not have 0-length values" }
                if (bytesPerDim == -1) {
                    bytesPerDim = oneDim.size
                } else require(bytesPerDim == oneDim.size) {
                    ("all dimensions must have same bytes length; got "
                            + bytesPerDim
                            + " and "
                            + oneDim.size)
                }
            }
            return getType(point.size, bytesPerDim)
        }

        private fun getType(numDims: Int, bytesPerDim: Int): FieldType {
            val type = FieldType()
            type.setDimensions(numDims, bytesPerDim)
            type.freeze()
            return type
        }

        private fun pack(vararg point: ByteArray): BytesRef {
            requireNotNull(point) { "point must not be null" }
            require(point.isNotEmpty()) { "point must not be 0 dimensions" }
            if (point.size == 1) {
                return BytesRef(point[0])
            }
            var bytesPerDim = -1
            for (dim in point) {
                requireNotNull(dim) { "point must not have null values" }
                if (bytesPerDim == -1) {
                    require(dim.isNotEmpty()) { "point must not have 0-length values" }
                    bytesPerDim = dim.size
                } else require(dim.size == bytesPerDim) {
                    ("all dimensions must have same bytes length; got "
                            + bytesPerDim
                            + " and "
                            + dim.size)
                }
            }
            val packed = ByteArray(bytesPerDim * point.size)
            for (i in point.indices) {
                System.arraycopy(point[i], 0, packed, i * bytesPerDim, bytesPerDim)
            }
            return BytesRef(packed)
        }

        // static methods for generating queries
        /**
         * Create a query for matching an exact binary value.
         *
         *
         * This is for simple one-dimension points, for multidimensional points use [ ][.newRangeQuery] instead.
         *
         * @param field field name. must not be `null`.
         * @param value binary value
         * @throws IllegalArgumentException if `field` is null or `value` is null
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: ByteArray): Query {
            return newRangeQuery(field, value, value)
        }

        /**
         * Create a range query for binary values.
         *
         *
         * This is for simple one-dimension ranges, for multidimensional ranges use [ ][.newRangeQuery] instead.
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive). must not be `null`
         * @param upperValue upper portion of the range (inclusive). must not be `null`
         * @throws IllegalArgumentException if `field` is null, if `lowerValue` is null, or if
         * `upperValue` is null
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(
            field: String,
            lowerValue: ByteArray,
            upperValue: ByteArray
        ): Query {
            PointRangeQuery.checkArgs(field, lowerValue, upperValue)
            return newRangeQuery(field, arrayOf(lowerValue), arrayOf(upperValue))
        }

        /**
         * Create a range query for n-dimensional binary values.
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive). must not be null.
         * @param upperValue upper portion of the range (inclusive). must not be null.
         * @throws IllegalArgumentException if `field` is null, if `lowerValue` is null, if
         * `upperValue` is null, or if `lowerValue.length != upperValue.length`
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(
            field: String,
            lowerValue: Array<ByteArray>,
            upperValue: Array<ByteArray>
        ): Query {
            return object : PointRangeQuery(
                field, pack(*lowerValue).bytes, pack(*upperValue).bytes, lowerValue.size
            ) {
                override fun toString(dimension: Int, value: ByteArray): String {
                    checkNotNull(value)
                    val sb = StringBuilder()
                    sb.append("binary(")
                    for (i in value.indices) {
                        if (i > 0) {
                            sb.append(' ')
                        }
                        sb.append(Int.toHexString(value[i].toInt() and 0xFF))
                    }
                    sb.append(')')
                    return sb.toString()
                }
            }
        }

        /**
         * Create a query matching any of the specified 1D values. This is the points equivalent of `TermsQuery`.
         *
         * @param field field name. must not be `null`.
         * @param values all values to match
         */
        fun newSetQuery(field: String, values: Array<ByteArray>): Query {
            // Make sure all byte[] have the same length

            var bytesPerDim = -1
            for (value in values) {
                if (bytesPerDim == -1) {
                    bytesPerDim = value.size
                } else require(value.size == bytesPerDim) { "all byte[] must be the same length, but saw " + bytesPerDim + " and " + value.size }
            }

            if (bytesPerDim == -1) {
                // There are no points, and we cannot guess the bytesPerDim here, so we return an equivalent
                // query:
                return MatchNoDocsQuery("empty BinaryPoint.newSetQuery")
            }

            // Don't unexpectedly change the user's incoming values array:
            val sortedValues: Array<ByteArray> = values.copyOf()
            Arrays.sort(
                sortedValues
            ) { a: ByteArray, b: ByteArray ->
                Arrays.compareUnsigned(
                    a,
                    0,
                    a.size,
                    b,
                    0,
                    b.size
                )
            }

            val encoded = BytesRef(ByteArray(bytesPerDim))

            return object : PointInSetQuery(
                field,
                1,
                bytesPerDim,
                object : Stream() {
                    var upto: Int = 0

                    override fun next(): BytesRef? {
                        if (upto == sortedValues.size) {
                            return null
                        } else {
                            encoded.bytes = sortedValues[upto]
                            upto++
                            return encoded
                        }
                    }
                }) {
                override fun toString(value: ByteArray): String {
                    return BytesRef(value).toString()
                }
            }
        }
    }
}
