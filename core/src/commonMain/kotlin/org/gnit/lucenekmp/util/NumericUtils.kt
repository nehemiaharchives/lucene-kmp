package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.toByteArray
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.longBitsToDouble
import space.kscience.kmath.operations.BigInt


/**
 * Helper APIs to encode numeric values as sortable bytes and vice-versa.
 *
 *
 * To also index floating point numbers, this class supplies two methods to convert them to
 * integer values by changing their bit layout: [.doubleToSortableLong], [ ][.floatToSortableInt]. You will have no precision loss by converting floating point numbers to
 * integers and back (only that the integer form is not usable). Other data types like dates can
 * easily converted to longs or ints (e.g. date to long: [java.util.Date.getTime]).
 *
 * @lucene.internal
 */
object NumericUtils {
    /**
     * Converts a `double` value to a sortable signed `long`. The value is
     * converted by getting their IEEE 754 floating-point &quot;double format&quot; bit layout and
     * then some bits are swapped, to be able to compare the result as long. By this the precision is
     * not reduced, but the value can easily used as a long. The sort order (including [ ][Double.NaN]) is defined by [Double.compareTo]; `NaN` is greater than positive
     * infinity.
     *
     * @see .sortableLongToDouble
     */
    fun doubleToSortableLong(value: Double): Long {
        return sortableDoubleBits(Double.doubleToLongBits(value))
    }

    /**
     * Converts a sortable `long` back to a `double`.
     *
     * @see .doubleToSortableLong
     */
    fun sortableLongToDouble(encoded: Long): Double {
        return Double.longBitsToDouble(sortableDoubleBits(encoded))
    }

    /**
     * Converts a `float` value to a sortable signed `int`. The value is
     * converted by getting their IEEE 754 floating-point &quot;float format&quot; bit layout and then
     * some bits are swapped, to be able to compare the result as int. By this the precision is not
     * reduced, but the value can easily be used as an int. The sort order (including [ ][Float.NaN]) is defined by [Float.compareTo]; `NaN` is greater than positive
     * infinity.
     *
     * @see .sortableIntToFloat
     */
    fun floatToSortableInt(value: Float): Int {
        return sortableFloatBits(Float.floatToIntBits(value))
    }

    /**
     * Converts a sortable `int` back to a `float`.
     *
     * @see .floatToSortableInt
     */
    fun sortableIntToFloat(encoded: Int): Float {
        return Float.intBitsToFloat(sortableFloatBits(encoded))
    }

    /** Converts IEEE 754 representation of a double to sortable order (or back to the original)  */
    fun sortableDoubleBits(bits: Long): Long {
        return bits xor ((bits shr 63) and 0x7fffffffffffffffL)
    }

    /** Converts IEEE 754 representation of a float to sortable order (or back to the original)  */
    fun sortableFloatBits(bits: Int): Int {
        return bits xor ((bits shr 31) and 0x7fffffff)
    }

    /** Result = a - b, where a &gt;= b, else `IllegalArgumentException` is thrown.  */
    fun subtract(bytesPerDim: Int, dim: Int, a: ByteArray, b: ByteArray, result: ByteArray) {
        val start = dim * bytesPerDim
        val end = start + bytesPerDim
        var borrow = 0
        for (i in end - 1 downTo start) {
            var diff = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff) - borrow
            if (diff < 0) {
                diff += 256
                borrow = 1
            } else {
                borrow = 0
            }
            result[i - start] = diff.toByte()
        }
        require(borrow == 0) { "a < b" }
    }

    /**
     * Result = a + b, where a and b are unsigned. If there is an overflow, `IllegalArgumentException` is thrown.
     */
    fun add(bytesPerDim: Int, dim: Int, a: ByteArray, b: ByteArray, result: ByteArray) {
        val start = dim * bytesPerDim
        val end = start + bytesPerDim
        var carry = 0
        for (i in end - 1 downTo start) {
            var digitSum = (a[i].toInt() and 0xff) + (b[i].toInt() and 0xff) + carry
            if (digitSum > 255) {
                digitSum -= 256
                carry = 1
            } else {
                carry = 0
            }
            result[i - start] = digitSum.toByte()
        }
        require(carry == 0) { "a + b overflows bytesPerDim=" + bytesPerDim }
    }

    /**
     * Encodes an integer `value` such that unsigned byte order comparison is consistent with
     * [Integer.compare]
     *
     * @see .sortableBytesToInt
     */
    fun intToSortableBytes(value: Int, result: ByteArray, offset: Int) {
        // Flip the sign bit, so negative ints sort before positive ints correctly:
        var value = value
        value = value xor -0x80000000
        /*BitUtil.VH_BE_INT.set(result, offset, value)*/
        result.setIntBE(offset, value)
    }

    /**
     * Decodes an integer value previously written with [.intToSortableBytes]
     *
     * @see .intToSortableBytes
     */
    fun sortableBytesToInt(encoded: ByteArray, offset: Int): Int {
        /*val x = BitUtil.VH_BE_INT.get(encoded, offset) as Int*/
        val x = encoded.getIntBE(offset)
        // Re-flip the sign bit to restore the original value:
        return x xor -0x80000000
    }

    /**
     * Encodes an long `value` such that unsigned byte order comparison is consistent with
     * [Long.compare]
     *
     * @see .sortableBytesToLong
     */
    fun longToSortableBytes(value: Long, result: ByteArray, offset: Int) {
        // Flip the sign bit so negative longs sort before positive longs:
        val sortableValue = value xor Long.MIN_VALUE
        /*BitUtil.VH_BE_LONG.set(result, offset, value)*/
        result.setLongBE(offset, sortableValue)
    }

    /**
     * Decodes a long value previously written with [.longToSortableBytes]
     *
     * @see .longToSortableBytes
     */
    fun sortableBytesToLong(encoded: ByteArray, offset: Int): Long {
        /*var v = BitUtil.VH_BE_LONG.get(encoded, offset) as Long*/
        val v = encoded.getLongBE(offset)
        return v xor Long.MIN_VALUE
    }

    /**
     * Encodes a BigInteger `value` such that unsigned byte order comparison is consistent with
     * [BigInteger.compareTo]. This also sign-extends the value to `bigIntSize` bytes if necessary: useful to create a fixed-width size.
     *
     * @see .sortableBytesToBigInt
     */
    fun bigIntToSortableBytes(
        bigInt: BigInt, bigIntSize: Int, result: ByteArray, offset: Int
    ) {
        val bigIntBytes: ByteArray = bigInt.toByteArray()
        val fullBigIntBytes: ByteArray

        if (bigIntBytes.size < bigIntSize) {
            fullBigIntBytes = ByteArray(bigIntSize)
            /*java.lang.System.arraycopy(
                bigIntBytes, 0, fullBigIntBytes, bigIntSize - bigIntBytes.size, bigIntBytes.size
            )*/
            bigIntBytes.copyInto(
                destination = fullBigIntBytes,
                destinationOffset = bigIntSize - bigIntBytes.size,
                startIndex = 0,
                endIndex = bigIntBytes.size
            )

            if ((bigIntBytes[0].toInt() and 0x80) != 0) {
                // sign extend
                /*java.util.Arrays.fill(fullBigIntBytes, 0, bigIntSize - bigIntBytes.size, 0xff.toByte())*/
                fullBigIntBytes.fill(
                    element = 0xff.toByte(),
                    fromIndex = 0,
                    toIndex = bigIntSize - bigIntBytes.size
                )
            }
        } else if (bigIntBytes.size == bigIntSize) {
            fullBigIntBytes = bigIntBytes
        } else {
            throw IllegalArgumentException(
                "BigInteger: $bigInt requires more than $bigIntSize bytes storage"
            )
        }
        // Flip the sign bit so negative bigints sort before positive bigints:
        fullBigIntBytes[0] = (fullBigIntBytes[0].toInt() xor 0x80.toInt()).toByte()

        /*java.lang.System.arraycopy(fullBigIntBytes, 0, result, offset, bigIntSize)*/
        fullBigIntBytes.copyInto(
            destination = result,
            destinationOffset = offset,
            startIndex = 0,
            endIndex = bigIntSize
        )

        require(
            sortableBytesToBigInt(result, offset, bigIntSize) == bigInt
        ) { "bigInt=" + bigInt + " converted=" + sortableBytesToBigInt(result, offset, bigIntSize) }
    }

    /**
     * Decodes a BigInteger value previously written with [.bigIntToSortableBytes]
     *
     * @see .bigIntToSortableBytes
     */
    fun sortableBytesToBigInt(encoded: ByteArray, offset: Int, length: Int): BigInt {
        val bigIntBytes = ByteArray(length)
        /*java.lang.System.arraycopy(encoded, offset, bigIntBytes, 0, length)*/
        encoded.copyInto(
            destination = bigIntBytes,
            destinationOffset = 0,
            startIndex = offset,
            endIndex = offset + length
        )
        // Flip the sign bit back to the original
        bigIntBytes[0] = (bigIntBytes[0].toInt() xor 0x80.toInt()).toByte()

        return BigInt.fromByteArray(bigIntBytes)
    }
}
