package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils


/**
 * An indexed Double Range field.
 *
 *
 * This field indexes dimensional ranges defined as min/max pairs. It supports up to a maximum of
 * 4 dimensions (indexed as 8 numeric values). With 1 dimension representing a single double range,
 * 2 dimensions representing a bounding box, 3 dimensions a bounding cube, and 4 dimensions a
 * tesseract.
 *
 *
 * Multiple values for the same field in one document is supported, and open ended ranges can be
 * defined using `Double.NEGATIVE_INFINITY` and `Double.POSITIVE_INFINITY`.
 *
 *
 * This field defines the following static factory methods for common search operations over
 * double ranges:
 *
 *
 *  * [newIntersectsQuery()][.newIntersectsQuery] matches ranges that intersect the defined
 * search range.
 *  * [newWithinQuery()][.newWithinQuery] matches ranges that are within the defined search
 * range.
 *  * [newContainsQuery()][.newContainsQuery] matches ranges that contain the defined search
 * range.
 *
 */
class DoubleRange(name: String, min: DoubleArray, max: DoubleArray) : Field(name, getType(min.size)) {
    /**
     * Create a new DoubleRange type, from min/max parallel arrays
     *
     * @param name field name. must not be null.
     * @param min range min values; each entry is the min value for the dimension
     * @param max range max values; each entry is the max value for the dimension
     */
    init {
        setRangeValues(min, max)
    }

    /**
     * Changes the values of the field.
     *
     * @param min array of min values. (accepts `Double.NEGATIVE_INFINITY`)
     * @param max array of max values. (accepts `Double.POSITIVE_INFINITY`)
     * @throws IllegalArgumentException if `min` or `max` is invalid
     */
    fun setRangeValues(min: DoubleArray, max: DoubleArray) {
        checkArgs(min, max)
        require(
            !(min.size * 2 != type.pointDimensionCount()
                    || max.size * 2 != type.pointDimensionCount())
        ) {
            ("field (name="
                    + name
                    + ") uses "
                    + type.pointDimensionCount() / 2 + " dimensions; cannot change to (incoming) "
                    + min.size
                    + " dimensions")
        }

        val bytes: ByteArray
        if (!isFieldsDataInitialized()) {
            bytes = ByteArray(BYTES * 2 * min.size)
            fieldsData = BytesRef(bytes)
        } else {
            bytes = (fieldsData as BytesRef).bytes
        }
        verifyAndEncode(min, max, bytes)
    }

    /**
     * Get the min value for the given dimension
     *
     * @param dimension the dimension, always positive
     * @return the decoded min value
     */
    fun getMin(dimension: Int): Double {
        Objects.checkIndex(dimension, type.pointDimensionCount() / 2)
        return decodeMin((fieldsData as BytesRef).bytes, dimension)
    }

    /**
     * Get the max value for the given dimension
     *
     * @param dimension the dimension, always positive
     * @return the decoded max value
     */
    fun getMax(dimension: Int): Double {
        Objects.checkIndex(dimension, type.pointDimensionCount() / 2)
        return decodeMax((fieldsData as BytesRef).bytes, dimension)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(this::class.simpleName)
        sb.append(" <")
        sb.append(name)
        sb.append(':')
        val b: ByteArray = (fieldsData as BytesRef).bytes
        toString(b, 0)
        for (d in 0..<type.pointDimensionCount() / 2) {
            sb.append(' ')
            sb.append(toString(b, d))
        }
        sb.append('>')

        return sb.toString()
    }

    companion object {
        /** stores double values so number of bytes is 8  */
        const val BYTES: Int = Double.SIZE_BYTES

        /** set the field type  */
        private fun getType(dimensions: Int): FieldType {
            require(dimensions <= 4) { "DoubleRange does not support greater than 4 dimensions" }

            val ft = FieldType()
            // dimensions is set as 2*dimension size (min/max per dimension)
            ft.setDimensions(dimensions * 2, BYTES)
            ft.freeze()
            return ft
        }

        /** validate the arguments  */
        private fun checkArgs(min: DoubleArray, max: DoubleArray) {
            require(!(min == null || max == null || min.isEmpty() || max.isEmpty())) { "min/max range values cannot be null or empty" }
            require(min.size == max.size) { "min/max ranges must agree" }
            require(min.size <= 4) { "DoubleRange does not support greater than 4 dimensions" }
        }

        /** Encodes the min, max ranges into a byte array  */
        fun encode(min: DoubleArray, max: DoubleArray): ByteArray {
            checkArgs(min, max)
            val b = ByteArray(BYTES * 2 * min.size)
            verifyAndEncode(min, max, b)
            return b
        }

        /**
         * encode the ranges into a sortable byte array (`Double.NaN` not allowed)
         *
         *
         * example for 4 dimensions (8 bytes per dimension value): minD1 ... minD4 | maxD1 ... maxD4
         */
        fun verifyAndEncode(min: DoubleArray, max: DoubleArray, bytes: ByteArray) {
            var d = 0
            var i = 0
            var j = min.size * BYTES
            while (d < min.size) {
                require(!Double.isNaN(min[d])) { "invalid min value (" + Double.NaN + ")" + " in DoubleRange" }
                require(!Double.isNaN(max[d])) { "invalid max value (" + Double.NaN + ")" + " in DoubleRange" }
                require(!(min[d] > max[d])) { "min value (" + min[d] + ") is greater than max value (" + max[d] + ")" }
                encode(min[d], bytes, i)
                encode(max[d], bytes, j)
                ++d
                i += BYTES
                j += BYTES
            }
        }

        /** encode the given value into the byte array at the defined offset  */
        private fun encode(`val`: Double, bytes: ByteArray, offset: Int) {
            NumericUtils.longToSortableBytes(NumericUtils.doubleToSortableLong(`val`), bytes, offset)
        }

        /** decodes the min value (for the defined dimension) from the encoded input byte array  */
        fun decodeMin(b: ByteArray, dimension: Int): Double {
            val offset = dimension * BYTES
            return NumericUtils.sortableLongToDouble(NumericUtils.sortableBytesToLong(b, offset))
        }

        /** decodes the max value (for the defined dimension) from the encoded input byte array  */
        fun decodeMax(b: ByteArray, dimension: Int): Double {
            val offset = b.size / 2 + dimension * BYTES
            return NumericUtils.sortableLongToDouble(NumericUtils.sortableBytesToLong(b, offset))
        }

        /**
         * Create a query for matching indexed ranges that intersect the defined range.
         *
         * @param field field name. must not be null.
         * @param min array of min values. (accepts `Double.NEGATIVE_INFINITY`)
         * @param max array of max values. (accepts `Double.POSITIVE_INFINITY`)
         * @return query for matching intersecting ranges (overlap, within, or contains)
         * @throws IllegalArgumentException if `field` is null, `min` or `max` is
         * invalid
         */
        fun newIntersectsQuery(field: String, min: DoubleArray, max: DoubleArray): Query {
            return newRelationQuery(field, min, max, RangeFieldQuery.QueryType.INTERSECTS)
        }

        /**
         * Create a query for matching indexed ranges that contain the defined range.
         *
         * @param field field name. must not be null.
         * @param min array of min values. (accepts `Double.MIN_VALUE`)
         * @param max array of max values. (accepts `Double.MAX_VALUE`)
         * @return query for matching ranges that contain the defined range
         * @throws IllegalArgumentException if `field` is null, `min` or `max` is
         * invalid
         */
        fun newContainsQuery(field: String, min: DoubleArray, max: DoubleArray): Query {
            return newRelationQuery(field, min, max, RangeFieldQuery.QueryType.CONTAINS)
        }

        /**
         * Create a query for matching indexed ranges that are within the defined range.
         *
         * @param field field name. must not be null.
         * @param min array of min values. (accepts `Double.MIN_VALUE`)
         * @param max array of max values. (accepts `Double.MAX_VALUE`)
         * @return query for matching ranges within the defined range
         * @throws IllegalArgumentException if `field` is null, `min` or `max` is
         * invalid
         */
        fun newWithinQuery(field: String, min: DoubleArray, max: DoubleArray): Query {
            return newRelationQuery(field, min, max, RangeFieldQuery.QueryType.WITHIN)
        }

        /**
         * Create a query for matching indexed ranges that cross the defined range. A CROSSES is defined
         * as any set of ranges that are not disjoint and not wholly contained by the query. Effectively,
         * its the complement of union(WITHIN, DISJOINT).
         *
         * @param field field name. must not be null.
         * @param min array of min values. (accepts `Double.MIN_VALUE`)
         * @param max array of max values. (accepts `Double.MAX_VALUE`)
         * @return query for matching ranges within the defined range
         * @throws IllegalArgumentException if `field` is null, `min` or `max` is
         * invalid
         */
        fun newCrossesQuery(field: String, min: DoubleArray, max: DoubleArray): Query {
            return newRelationQuery(field, min, max, RangeFieldQuery.QueryType.CROSSES)
        }

        /** helper method for creating the desired relational query  */
        private fun newRelationQuery(
            field: String, min: DoubleArray, max: DoubleArray, relation: RangeFieldQuery.QueryType
        ): Query {
            checkArgs(min, max)
            return object : RangeFieldQuery(field, encode(min, max), min.size, relation) {
                override fun toString(ranges: ByteArray, dimension: Int): String {
                    return DoubleRange.toString(ranges, dimension)
                }
            }
        }

        /**
         * Returns the String representation for the range at the given dimension
         *
         * @param ranges the encoded ranges, never null
         * @param dimension the dimension of interest
         * @return The string representation for the range at the provided dimension
         */
        private fun toString(ranges: ByteArray, dimension: Int): String {
            return ("["
                    + decodeMin(ranges, dimension).toString() + " : "
                    + decodeMax(ranges, dimension).toString() + "]")
        }
    }
}
