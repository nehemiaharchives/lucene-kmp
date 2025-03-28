package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.floatToRawIntBits
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.jdkport.toUnsignedInt


/**
 * Floating point numbers smaller than 32 bits.
 *
 * @lucene.internal
 */
object SmallFloat {
    /**
     * Converts a 32 bit float to an 8 bit float. <br></br>
     * Values less than zero are all mapped to zero. <br></br>
     * Values are truncated (rounded down) to the nearest 8 bit value. <br></br>
     * Values between zero and the smallest representable value are rounded up.
     *
     * @param f the 32 bit float to be converted to an 8 bit float (byte)
     * @param numMantissaBits the number of mantissa bits to use in the byte, with the remainder to be
     * used in the exponent
     * @param zeroExp the zero-point in the range of exponent values
     * @return the 8 bit float representation
     */
    fun floatToByte(f: Float, numMantissaBits: Int, zeroExp: Int): Byte {
        // Adjustment from a float zero exponent to our zero exponent,
        // shifted over to our exponent position.
        val fzero = (63 - zeroExp) shl numMantissaBits
        val bits: Int = Float.floatToRawIntBits(f)
        val smallfloat = bits shr (24 - numMantissaBits)
        if (smallfloat <= fzero) {
            return if (bits <= 0) 0.toByte() else 1.toByte() // underflow is mapped to smallest non-zero number.
        } else if (smallfloat >= fzero + 0x100) {
            return -1 // overflow maps to largest number
        } else {
            return (smallfloat - fzero).toByte()
        }
    }

    /** Converts an 8 bit float to a 32 bit float.  */
    fun byteToFloat(b: Byte, numMantissaBits: Int, zeroExp: Int): Float {
        // on Java1.5 & 1.6 JVMs, prebuilding a decoding array and doing a lookup
        // is only a little bit faster (anywhere from 0% to 7%)
        if (b.toInt() == 0) return 0.0f
        var bits = (b.toInt() and 0xff) shl (24 - numMantissaBits)
        bits += (63 - zeroExp) shl 24
        return Float.intBitsToFloat(bits)
    }

    //
    // Some specializations of the generic functions follow.
    // The generic functions are just as fast with current (1.5)
    // -server JVMs, but still slower with client JVMs.
    //
    /**
     * floatToByte(b, mantissaBits=3, zeroExponent=15) <br></br>
     * smallest non-zero value = 5.820766E-10 <br></br>
     * largest value = 7.5161928E9 <br></br>
     * epsilon = 0.125
     */
    fun floatToByte315(f: Float): Byte {
        val bits: Int = Float.floatToRawIntBits(f)
        val smallfloat = bits shr (24 - 3)
        if (smallfloat <= ((63 - 15) shl 3)) {
            return if (bits <= 0) 0.toByte() else 1.toByte()
        }
        if (smallfloat >= ((63 - 15) shl 3) + 0x100) {
            return -1
        }
        return (smallfloat - ((63 - 15) shl 3)).toByte()
    }

    /** byteToFloat(b, mantissaBits=3, zeroExponent=15)  */
    fun byte315ToFloat(b: Byte): Float {
        // on Java1.5 & 1.6 JVMs, prebuilding a decoding array and doing a lookup
        // is only a little bit faster (anywhere from 0% to 7%)
        if (b.toInt() == 0) return 0.0f
        var bits = (b.toInt() and 0xff) shl (24 - 3)
        bits += (63 - 15) shl 24
        return Float.intBitsToFloat(bits)
    }

    /** Float-like encoding for positive longs that preserves ordering and 4 significant bits.  */
    fun longToInt4(i: Long): Int {
        require(i >= 0) { "Only supports positive values, got " + i }
        val numBits: Int = 64 - Long.numberOfLeadingZeros(i)
        if (numBits < 4) {
            // subnormal value
            return Math.toIntExact(i)
        } else {
            // normal value
            val shift = numBits - 4
            // only keep the 5 most significant bits
            var encoded: Int = Math.toIntExact(i ushr shift)
            // clear the most significant bit, which is implicit
            encoded = encoded and 0x07
            // encode the shift, adding 1 because 0 is reserved for subnormal values
            encoded = encoded or ((shift + 1) shl 3)
            return encoded
        }
    }

    /** Decode values encoded with [.longToInt4].  */
    fun int4ToLong(i: Int): Long {
        val bits = (i and 0x07).toLong()
        val shift = (i ushr 3) - 1
        val decoded: Long
        if (shift == -1) {
            // subnormal value
            decoded = bits
        } else {
            // normal value
            decoded = (bits or 0x08L) shl shift
        }
        return decoded
    }

    private val MAX_INT4 = longToInt4(Int.Companion.MAX_VALUE.toLong())
    private val NUM_FREE_VALUES = 255 - MAX_INT4

    /**
     * Encode an integer to a byte. It is built upon [.longToInt4] and leverages the fact
     * that `longToInt4(Integer.MAX_VALUE)` is less than 255 to encode low values more
     * accurately.
     */
    fun intToByte4(i: Int): Byte {
        require(i >= 0) { "Only supports positive values, got " + i }
        if (i < NUM_FREE_VALUES) {
            return i.toByte()
        } else {
            return (NUM_FREE_VALUES + longToInt4((i - NUM_FREE_VALUES).toLong())).toByte()
        }
    }

    /** Decode values that have been encoded with [.intToByte4].  */
    fun byte4ToInt(b: Byte): Int {
        val i: Int = Byte.toUnsignedInt(b)
        if (i < NUM_FREE_VALUES) {
            return i
        } else {
            val decoded = NUM_FREE_VALUES + int4ToLong(i - NUM_FREE_VALUES)
            return Math.toIntExact(decoded)
        }
    }
}
