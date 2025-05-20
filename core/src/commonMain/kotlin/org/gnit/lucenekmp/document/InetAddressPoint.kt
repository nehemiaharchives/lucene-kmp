package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.search.PointInSetQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.jdkport.InetAddress
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.UnknownHostException
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

/**
 * An indexed 128-bit `InetAddress` field.
 *
 *
 * Finding all documents within a range at search time is efficient. Multiple values for the same
 * field in one document is allowed.
 *
 *
 * This field defines static factory methods for creating common queries:
 *
 *
 *  * [.newExactQuery] for matching an exact network address.
 *  * [.newPrefixQuery] for matching a network based on CIDR
 * prefix.
 *  * [.newRangeQuery] for matching arbitrary network
 * address ranges.
 *  * [.newSetQuery] for matching a set of network addresses.
 *
 *
 *
 * This field supports both IPv4 and IPv6 addresses: IPv4 addresses are converted to [IPv4-Mapped IPv6 Addresses](https://tools.ietf.org/html/rfc4291#section-2.5.5): indexing
 * `1.2.3.4` is the same as indexing `::FFFF:1.2.3.4`.
 *
 * @see PointValues
 */
class InetAddressPoint(name: String, point: InetAddress) : Field(name, TYPE) {
    /** Change the values of this field  */
    fun setInetAddressValue(value: InetAddress) {
        requireNotNull(value) { "point must not be null" }
        fieldsData = BytesRef(encode(value))
    }

    override fun setBytesValue(bytes: BytesRef) {
        throw IllegalArgumentException("cannot change value type from InetAddress to BytesRef")
    }

    /**
     * Creates a new InetAddressPoint, indexing the provided address.
     *
     * @param name field name
     * @param point InetAddress value
     * @throws IllegalArgumentException if the field name or value is null.
     */
    init {
        setInetAddressValue(point)
    }

    override fun toString(): String {
        val result = StringBuilder()
        result.append(this::class.simpleName)
        result.append(" <")
        result.append(name)
        result.append(':')

        // IPv6 addresses are bracketed, to not cause confusion with historic field:value representation
        val bytes: BytesRef = fieldsData as BytesRef
        val address: InetAddress = decode(BytesRef.deepCopyOf(bytes).bytes)
        if (address.getAddress().size == 16) {
            result.append('[')
            result.append(address.getHostAddress())
            result.append(']')
        } else {
            result.append(address.getHostAddress())
        }

        result.append('>')
        return result.toString()
    }

    companion object {
        // implementation note: we convert all addresses to IPv6: we expect prefix compression of values,
        // so its not wasteful, but allows one field to handle both IPv4 and IPv6.
        /** The number of bytes per dimension: 128 bits  */
        const val BYTES: Int = 16

        // rfc4291 prefix
        val IPV4_PREFIX: ByteArray = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1)

        private val TYPE: FieldType = FieldType().apply { setDimensions(1, BYTES); freeze() }

        /** The minimum value that an ip address can hold.  */
        val MIN_VALUE: InetAddress = decode(ByteArray(BYTES))

        /** The maximum value that an ip address can hold.  */
        val MAX_VALUE: InetAddress = decode(ByteArray(BYTES) { 0xFF.toByte() })

        /**
         * Return the [InetAddress] that compares immediately greater than `address`.
         *
         * @throws ArithmeticException if the provided address is the [maximum ip][.MAX_VALUE]
         */
        fun nextUp(address: InetAddress): InetAddress {
            if (address == MAX_VALUE) {
                throw ArithmeticException(
                    "Overflow: there is no greater InetAddress than " + address.getHostAddress()
                )
            }
            val delta = ByteArray(BYTES)
            delta[BYTES - 1] = 1
            val nextUpBytes = ByteArray(BYTES)
            NumericUtils.add(BYTES, 0, encode(address), delta, nextUpBytes)
            return decode(nextUpBytes)
        }

        /**
         * Return the [InetAddress] that compares immediately less than `address`.
         *
         * @throws ArithmeticException if the provided address is the [minimum ip][.MIN_VALUE]
         */
        fun nextDown(address: InetAddress): InetAddress {
            if (address == MIN_VALUE) {
                throw ArithmeticException(
                    "Underflow: there is no smaller InetAddress than " + address.getHostAddress()
                )
            }
            val delta = ByteArray(BYTES)
            delta[BYTES - 1] = 1
            val nextDownBytes = ByteArray(BYTES)
            NumericUtils.subtract(BYTES, 0, encode(address), delta, nextDownBytes)
            return decode(nextDownBytes)
        }

        // public helper methods (e.g. for queries)
        /** Encode InetAddress value into binary encoding  */
        fun encode(value: InetAddress): ByteArray {
            var address: ByteArray = value.getAddress()
            if (address.size == 4) {
                val mapped = ByteArray(16)
                System.arraycopy(IPV4_PREFIX, 0, mapped, 0, IPV4_PREFIX.size)
                System.arraycopy(address, 0, mapped, IPV4_PREFIX.size, address.size)
                address = mapped
            } else if (address.size != 16) {
                // more of an assertion, how did you create such an InetAddress :)
                throw UnsupportedOperationException("Only IPv4 and IPv6 addresses are supported")
            }
            return address
        }

        /** Decodes InetAddress value from binary encoding  */
        fun decode(value: ByteArray): InetAddress {
            try {
                return InetAddress.getByAddress(value)
            } catch (e: UnknownHostException) {
                // this only happens if value.length != 4 or 16, strange exception class
                throw IllegalArgumentException("encoded bytes are of incorrect length", e)
            }
        }

        // static methods for generating queries
        /**
         * Create a query for matching a network address.
         *
         * @param field field name. must not be `null`.
         * @param value exact value
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: InetAddress): Query {
            return newRangeQuery(field, value, value)
        }

        /**
         * Create a prefix query for matching a CIDR network range.
         *
         * @param field field name. must not be `null`.
         * @param value any host address
         * @param prefixLength the network prefix length for this address. This is also known as the
         * subnet mask in the context of IPv4 addresses.
         * @throws IllegalArgumentException if `field` is null, or prefixLength is invalid.
         * @return a query matching documents with addresses contained within this network
         */
        fun newPrefixQuery(field: String, value: InetAddress, prefixLength: Int): Query {
            requireNotNull(value) { "InetAddress must not be null" }
            require(!(prefixLength < 0 || prefixLength > 8 * value.getAddress().size)) {
                ("illegal prefixLength '"
                        + prefixLength
                        + "'. Must be 0-32 for IPv4 ranges, 0-128 for IPv6 ranges")
            }
            // create the lower value by zeroing out the host portion, upper value by filling it with all
            // ones.
            val lower: ByteArray = value.getAddress()
            val upper: ByteArray = value.getAddress()
            for (i in prefixLength..<8 * lower.size) {
                val m = (1 shl (7 - (i and 7))).toByte()
                lower[i shr 3] = lower[i shr 3] and m.inv()
                upper[i shr 3] = upper[i shr 3] or m
            }
            try {
                return newRangeQuery(field, InetAddress.getByAddress(lower), InetAddress.getByAddress(upper))
            } catch (e: UnknownHostException) {
                throw AssertionError(e) // values are coming from InetAddress
            }
        }

        /**
         * Create a range query for network addresses.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue = InetAddressPoint.MIN_VALUE` or `upperValue =
         * InetAddressPoint.MAX_VALUE`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass `InetAddressPoint#nextUp(lowerValue)`
         * or `InetAddressPoint#nexDown(upperValue)`.
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive). must not be null.
         * @param upperValue upper portion of the range (inclusive). must not be null.
         * @throws IllegalArgumentException if `field` is null, `lowerValue` is null, or
         * `upperValue` is null
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(
            field: String,
            lowerValue: InetAddress,
            upperValue: InetAddress
        ): Query {
            PointRangeQuery.checkArgs(field, lowerValue, upperValue)
            return object : PointRangeQuery(field, encode(lowerValue), encode(upperValue), 1) {
                override fun toString(dimension: Int, value: ByteArray): String {
                    return decode(value).getHostAddress() // for ranges, the range itself is already bracketed
                }
            }
        }

        /**
         * Create a query matching any of the specified 1D values. This is the points equivalent of `TermsQuery`.
         *
         * @param field field name. must not be `null`.
         * @param values all values to match
         */
        fun newSetQuery(field: String, vararg values: InetAddress): Query {
            // We must compare the encoded form (InetAddress doesn't implement Comparable, and even if it
            // did, we do our own thing with ipv4 addresses):

            // NOTE: we could instead convert-per-comparison and save this extra array, at cost of slower
            // sort:

            val sortedValues = Array(values.size) { i -> encode(values[i]) }

            Arrays.sort<ByteArray>(
                sortedValues,
                Comparator { a: ByteArray, b: ByteArray ->
                    Arrays.compareUnsigned(
                        a,
                        0,
                        BYTES,
                        b,
                        0,
                        BYTES
                    )
                })

            val encoded = BytesRef(ByteArray(BYTES))

            return object : PointInSetQuery(
                field,
                1,
                BYTES,
                object : Stream() {
                    var upto: Int = 0

                    override fun next(): BytesRef? {
                        if (upto == sortedValues.size) {
                            return null
                        } else {
                            encoded.bytes = sortedValues[upto]
                            require(encoded.bytes.size == encoded.length)
                            upto++
                            return encoded
                        }
                    }
                }) {
                override fun toString(value: ByteArray): String {
                    require(value.size == BYTES)
                    return decode(value).getHostAddress()
                }
            }
        }
    }
}
