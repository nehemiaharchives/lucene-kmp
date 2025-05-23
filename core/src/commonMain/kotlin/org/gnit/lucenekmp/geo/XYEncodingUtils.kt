package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.util.NumericUtils

/**
 * reusable cartesian geometry encoding methods
 *
 * @lucene.internal
 */
object XYEncodingUtils {
    const val MIN_VAL_INCL: Double = -Float.Companion.MAX_VALUE.toDouble()
    const val MAX_VAL_INCL: Double = Float.Companion.MAX_VALUE.toDouble()

    /** validates value is a number and finite  */
    fun checkVal(x: Float): Float {
        require(Float.isFinite(x) != false) { "invalid value $x; must be between $MIN_VAL_INCL and $MAX_VAL_INCL" }
        return x
    }

    /**
     * Quantizes double (64 bit) values into 32 bits
     *
     * @param x cartesian value
     * @return encoded value as a 32-bit `int`
     * @throws IllegalArgumentException if value is out of bounds
     */
    fun encode(x: Float): Int {
        return NumericUtils.floatToSortableInt(checkVal(x))
    }

    /**
     * Turns quantized value from [.encode] back into a double.
     *
     * @param encoded encoded value: 32-bit quantized value.
     * @return decoded value value.
     */
    fun decode(encoded: Int): Float {
        val result: Float = NumericUtils.sortableIntToFloat(encoded)
        require(result >= MIN_VAL_INCL && result <= MAX_VAL_INCL)
        return result
    }

    /**
     * Turns quantized value from byte array back into a double.
     *
     * @param src byte array containing 4 bytes to decode at `offset`
     * @param offset offset into `src` to decode from.
     * @return decoded value.
     */
    fun decode(src: ByteArray, offset: Int): Float {
        return decode(NumericUtils.sortableBytesToInt(src, offset))
    }

    /**
     * Convert an array of `float` numbers to `double` numbers.
     *
     * @param f The input floats
     * @return Corresponding double array.
     */
    fun floatArrayToDoubleArray(f: FloatArray): DoubleArray {
        val d = DoubleArray(f.size)
        for (i in f.indices) {
            d[i] = f[i].toDouble()
        }
        return d
    }
}
