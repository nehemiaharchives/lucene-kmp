package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.InetAddress
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef

/**
 * An indexed InetAddress Range Field
 *
 * This field indexes an `InetAddress` range defined as a min/max pairs. It is single
 * dimension only (indexed as two 16 byte paired values).
 *
 * Multiple values are supported.
 *
 * This field defines the following static factory methods for common search operations over Ip
 * Ranges
 *
 *  * `newIntersectsQuery()` matches ip ranges that intersect the defined search range.
 *  * `newWithinQuery()` matches ip ranges that are within the defined search range.
 *  * `newContainsQuery()` matches ip ranges that contain the defined search range.
 *  * `newCrossesQuery()` matches ip ranges that cross the defined search range
 */
/**
 * Create a new InetAddressRange from min/max value
 *
 * @param name field name. must not be null.
 * @param min range min value; defined as an `InetAddress`
 * @param max range max value; defined as an `InetAddress`
 */
class InetAddressRange(name: String, min: InetAddress, max: InetAddress) : Field(name, TYPE) {
    /**
     * Change (or set) the min/max values of the field.
     *
     * @param min range min value; defined as an `InetAddress`
     * @param max range max value; defined as an `InetAddress`
     */
    fun setRangeValues(min: InetAddress, max: InetAddress) {
        val bytes: ByteArray =
            if (!isFieldsDataInitialized()) {
                ByteArray(BYTES * 2).also { fieldsData = BytesRef(it) }
            } else {
                (fieldsData as BytesRef).bytes
            }
        encode(min, max, bytes)
    }

    init {
        setRangeValues(min, max)
    }

    companion object {
        /** The number of bytes per dimension : sync w/ `InetAddressPoint` */
        const val BYTES: Int = InetAddressPoint.BYTES

        private val TYPE: FieldType =
            FieldType().apply {
                setDimensions(2, BYTES)
                freeze()
            }

        /** encode the min/max range into the provided byte array */
        private fun encode(min: InetAddress, max: InetAddress, bytes: ByteArray) {
            // encode min and max value (consistent w/ InetAddressPoint encoding)
            val minEncoded = InetAddressPoint.encode(min)
            val maxEncoded = InetAddressPoint.encode(max)
            // ensure min is lt max
            if (Arrays.compareUnsigned(minEncoded, 0, BYTES, maxEncoded, 0, BYTES) > 0) {
                throw IllegalArgumentException(
                    "min value cannot be greater than max value for InetAddressRange field"
                )
            }
            System.arraycopy(minEncoded, 0, bytes, 0, BYTES)
            System.arraycopy(maxEncoded, 0, bytes, BYTES, BYTES)
        }

        /** encode the min/max range and return the byte array */
        private fun encode(min: InetAddress, max: InetAddress): ByteArray {
            val b = ByteArray(BYTES * 2)
            encode(min, max, b)
            return b
        }

        /**
         * Create a query for matching indexed ip ranges that `INTERSECT` the defined range.
         *
         * @param field field name. must not be null.
         * @param min range min value; provided as an `InetAddress`
         * @param max range max value; provided as an `InetAddress`
         * @return query for matching intersecting ranges (overlap, within, crosses, or contains)
         * @throws IllegalArgumentException if `field` is null, `min` or `max` is invalid
         */
        fun newIntersectsQuery(field: String, min: InetAddress, max: InetAddress): Query {
            return newRelationQuery(field, min, max, RangeFieldQuery.QueryType.INTERSECTS)
        }

        /**
         * Create a query for matching indexed ip ranges that `CONTAINS` the defined range.
         *
         * @param field field name. must not be null.
         * @param min range min value; provided as an `InetAddress`
         * @param max range max value; provided as an `InetAddress`
         * @return query for matching intersecting ranges (overlap, within, crosses, or contains)
         * @throws IllegalArgumentException if `field` is null, `min` or `max` is invalid
         */
        fun newContainsQuery(field: String, min: InetAddress, max: InetAddress): Query {
            return newRelationQuery(field, min, max, RangeFieldQuery.QueryType.CONTAINS)
        }

        /**
         * Create a query for matching indexed ip ranges that are `WITHIN` the defined range.
         *
         * @param field field name. must not be null.
         * @param min range min value; provided as an `InetAddress`
         * @param max range max value; provided as an `InetAddress`
         * @return query for matching intersecting ranges (overlap, within, crosses, or contains)
         * @throws IllegalArgumentException if `field` is null, `min` or `max` is invalid
         */
        fun newWithinQuery(field: String, min: InetAddress, max: InetAddress): Query {
            return newRelationQuery(field, min, max, RangeFieldQuery.QueryType.WITHIN)
        }

        /**
         * Create a query for matching indexed ip ranges that `CROSS` the defined range.
         *
         * @param field field name. must not be null.
         * @param min range min value; provided as an `InetAddress`
         * @param max range max value; provided as an `InetAddress`
         * @return query for matching intersecting ranges (overlap, within, crosses, or contains)
         * @throws IllegalArgumentException if `field` is null, `min` or `max` is invalid
         */
        fun newCrossesQuery(field: String, min: InetAddress, max: InetAddress): Query {
            return newRelationQuery(field, min, max, RangeFieldQuery.QueryType.CROSSES)
        }

        /** helper method for creating the desired relational query */
        private fun newRelationQuery(
            field: String,
            min: InetAddress,
            max: InetAddress,
            relation: RangeFieldQuery.QueryType
        ): Query {
            return object : RangeFieldQuery(field, encode(min, max), 1, relation) {
                override fun toString(ranges: ByteArray, dimension: Int): String {
                    return InetAddressRange.toString(ranges, dimension)
                }
            }
        }

        /**
         * Returns the String representation for the range at the given dimension
         *
         * @param ranges the encoded ranges, never null
         * @param dimension the dimension of interest (not used for this field)
         * @return The string representation for the range at the provided dimension
         */
        private fun toString(ranges: ByteArray, dimension: Int): String {
            val min = ByteArray(BYTES)
            System.arraycopy(ranges, 0, min, 0, BYTES)
            val max = ByteArray(BYTES)
            System.arraycopy(ranges, BYTES, max, 0, BYTES)
            return "[${InetAddressPoint.decode(min)} : ${InetAddressPoint.decode(max)}]"
        }
    }
}
