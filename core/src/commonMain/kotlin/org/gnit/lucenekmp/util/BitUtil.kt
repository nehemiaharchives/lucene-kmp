package org.gnit.lucenekmp.util

import dev.scottpierce.envvar.EnvVar
import org.gnit.lucenekmp.jdkport.ByteOrder


/**
 * A variety of high efficiency bit twiddling routines and encoders for primitives.
 *
 * @lucene.internal
 */
object BitUtil {
    /**
     * Native byte order.
     *
     *
     * Warning: This constant is [ByteOrder.nativeOrder] only in production environments,
     * during testing we randomize it. If you need to communicate with native APIs (e.g., Java's
     * Panama API), use [ByteOrder.nativeOrder].
     */
    val NATIVE_BYTE_ORDER: ByteOrder = nativeByteOrder

    private val nativeByteOrder: ByteOrder
        get() {
            try {
                val prop: String? = EnvVar["tests.seed"] /*java.lang.System.getProperty("tests.seed")*/
                if (prop != null) {
                    return if (prop.hashCode() % 2 == 0) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                }
            } catch (e: Exception /*se: java.lang.SecurityException*/) {
                // fall-through
            }
            return ByteOrder.nativeOrder()
        }

    /**
     * A [VarHandle] to read/write little endian `short` from/to a byte array. Shape:
     * `short vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, short
     * val)`
     */
    /*val VH_LE_SHORT: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(ShortArray::class.java, ByteOrder.LITTLE_ENDIAN)*/
    object VH_LE_SHORT {
        fun set(nextBlocks: ByteArray, offset: Int, s: Short) {
            nextBlocks.setShortLE(offset, s)
        }
    }

    /**
     * A [VarHandle] to read/write little endian `int` from a byte array. Shape: `int vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, int val)`
     */
    /*val VH_LE_INT: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(IntArray::class.java, ByteOrder.LITTLE_ENDIAN)*/
    object VH_LE_INT {
        fun get(nextBlocks: ByteArray, offset: Int): Int {
            return nextBlocks.getIntLE(offset)
        }

        fun set(nextBlocks: ByteArray, offset: Int, i: Int) {
            nextBlocks.setIntLE(offset, i)
        }
    }

    /**
     * A [VarHandle] to read/write little endian `long` from a byte array. Shape: `long vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, long val)`
     */
    /*val VH_LE_LONG: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(LongArray::class.java, ByteOrder.LITTLE_ENDIAN)*/
    object VH_LE_LONG {
        fun get(nextBlocks: ByteArray, offset: Int): Long {
            return nextBlocks.getLongLE(offset)
        }

        fun set(nextBlocks: ByteArray, offset: Int, l: Long) {
            nextBlocks.setLongLE(offset, l)
        }
    }


    /**
     * A [VarHandle] to read/write little endian `float` from a byte array. Shape: `float vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, float val)`
     */
    /*val VH_LE_FLOAT: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(FloatArray::class.java, ByteOrder.LITTLE_ENDIAN)*/

    /**
     * A [VarHandle] to read/write little endian `double` from a byte array. Shape: `double vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, double val)`
     */
    /*val VH_LE_DOUBLE: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(DoubleArray::class.java, ByteOrder.LITTLE_ENDIAN)*/

    /**
     * A [VarHandle] to read/write native endian `short` from/to a byte array. Shape:
     * `short vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, short
     * val)`
     *
     *
     * Warning: This handle uses default order only in production environments, during testing we
     * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use [ ][ByteOrder.nativeOrder].
     */
    /*val VH_NATIVE_SHORT: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(ShortArray::class.java, NATIVE_BYTE_ORDER)*/

    /**
     * A [VarHandle] to read/write native endian `int` from a byte array. Shape: `int vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, int val)`
     *
     *
     * Warning: This handle uses default order only in production environments, during testing we
     * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use [ ][ByteOrder.nativeOrder].
     */
    /*val VH_NATIVE_INT: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(IntArray::class.java, NATIVE_BYTE_ORDER)*/
    object VH_NATIVE_INT {
        fun get(nextBlocks: ByteArray, offset: Int): Int {
            return when (NATIVE_BYTE_ORDER) {
                ByteOrder.BIG_ENDIAN -> nextBlocks.getIntBE(offset)
                ByteOrder.LITTLE_ENDIAN -> nextBlocks.getIntLE(offset)
                else -> throw IllegalStateException("Unknown byte order")
            }
        }
    }

    /**
     * A [VarHandle] to read/write native endian `long` from a byte array. Shape: `long vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, long val)`
     *
     *
     * Warning: This handle uses default order only in production environments, during testing we
     * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use [ ][ByteOrder.nativeOrder].
     */
    /*val VH_NATIVE_LONG: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(LongArray::class.java, NATIVE_BYTE_ORDER)*/

    /**
     * A [VarHandle] to read/write native endian `float` from a byte array. Shape: `float vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, float val)`
     *
     *
     * Warning: This handle uses default order only in production environments, during testing we
     * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use [ ][ByteOrder.nativeOrder].
     */
    /*val VH_NATIVE_FLOAT: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(FloatArray::class.java, NATIVE_BYTE_ORDER)*/

    /**
     * A [VarHandle] to read/write native endian `double` from a byte array. Shape: `double vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, double val)`
     *
     *
     * Warning: This handle uses default order only in production environments, during testing we
     * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use [ ][ByteOrder.nativeOrder].
     */
    /*val VH_NATIVE_DOUBLE: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(DoubleArray::class.java, NATIVE_BYTE_ORDER)*/

    /**
     * A [VarHandle] to read/write big endian `short` from a byte array. Shape: `short vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, short val)`
     *
     */
    /*@Deprecated("Better use little endian unless it is needed for backwards compatibility.")
    val VH_BE_SHORT: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(ShortArray::class.java, ByteOrder.BIG_ENDIAN)*/
    object VH_BE_SHORT {
        fun get(nextBlocks: ByteArray, offset: Int): Short {
            return nextBlocks.getShortBE(offset)
        }

        fun set(nextBlocks: ByteArray, offset: Int, s: Short) {
            nextBlocks.setShortBE(offset, s)
        }
    }

    /**
     * A [VarHandle] to read/write big endian `int` from a byte array. Shape: `int
     * vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, int val)`
     *
     */
    /*@Deprecated("Better use little endian unless it is needed for backwards compatibility.")
    val VH_BE_INT: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(IntArray::class.java, ByteOrder.BIG_ENDIAN)*/
    object VH_BE_INT {
        fun get(nextBlocks: ByteArray, offset: Int): Int {
            return nextBlocks.getIntBE(offset)
        }

        fun set(nextBlocks: ByteArray, offset: Int, i: Int) {
            nextBlocks.setIntBE(offset, i)
        }
    }


    /**
     * A [VarHandle] to read/write big endian `long` from a byte array. Shape: `long
     * vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, long val)`
     *
     */
    /*@Deprecated("Better use little endian unless it is needed for backwards compatibility.")
    val VH_BE_LONG: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(LongArray::class.java, ByteOrder.BIG_ENDIAN)*/

    /**
     * A [VarHandle] to read/write big endian `float` from a byte array. Shape: `float vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, float val)`
     *
     */
            /*@Deprecated("Better use little endian unless it is needed for backwards compatibility.")
            val VH_BE_FLOAT: java.lang.invoke.VarHandle =
                java.lang.invoke.MethodHandles.byteArrayViewVarHandle(FloatArray::class.java, ByteOrder.BIG_ENDIAN)*/

    /**
     * A [VarHandle] to read/write big endian `double` from a byte array. Shape: `double vh.get(byte[] arr, int ofs)` and `void vh.set(byte[] arr, int ofs, double val)`
     *
     */
    /*@Deprecated("Better use little endian unless it is needed for backwards compatibility.")
    val VH_BE_DOUBLE: java.lang.invoke.VarHandle =
        java.lang.invoke.MethodHandles.byteArrayViewVarHandle(DoubleArray::class.java, ByteOrder.BIG_ENDIAN)*/

    /**
     * returns the next highest power of two, or the current value if it's already a power of two or
     * zero
     */
    fun nextHighestPowerOfTwo(v: Int): Int {
        var v = v
        v--
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        v++
        return v
    }

    /**
     * returns the next highest power of two, or the current value if it's already a power of two or
     * zero
     */
    fun nextHighestPowerOfTwo(v: Long): Long {
        var v = v
        v--
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        v = v or (v shr 32)
        v++
        return v
    }

    // magic numbers for bit interleaving
    private const val MAGIC0 = 0x5555555555555555L
    private const val MAGIC1 = 0x3333333333333333L
    private const val MAGIC2 = 0x0F0F0F0F0F0F0F0FL
    private const val MAGIC3 = 0x00FF00FF00FF00FFL
    private const val MAGIC4 = 0x0000FFFF0000FFFFL
    private const val MAGIC5 = 0x00000000FFFFFFFFL
    private const val MAGIC6 = -0x5555555555555556L

    // shift values for bit interleaving
    private const val SHIFT0: Long = 1
    private const val SHIFT1: Long = 2
    private const val SHIFT2: Long = 4
    private const val SHIFT3: Long = 8
    private const val SHIFT4: Long = 16

    /**
     * Interleaves the first 32 bits of each long value
     *
     *
     * Adapted from: http://graphics.stanford.edu/~seander/bithacks.html#InterleaveBMN
     */
    fun interleave(even: Int, odd: Int): Long {
        var v1 = 0x00000000FFFFFFFFL and even.toLong()
        var v2 = 0x00000000FFFFFFFFL and odd.toLong()
        v1 = (v1 or (v1 shl SHIFT4.toInt())) and MAGIC4
        v1 = (v1 or (v1 shl SHIFT3.toInt())) and MAGIC3
        v1 = (v1 or (v1 shl SHIFT2.toInt())) and MAGIC2
        v1 = (v1 or (v1 shl SHIFT1.toInt())) and MAGIC1
        v1 = (v1 or (v1 shl SHIFT0.toInt())) and MAGIC0
        v2 = (v2 or (v2 shl SHIFT4.toInt())) and MAGIC4
        v2 = (v2 or (v2 shl SHIFT3.toInt())) and MAGIC3
        v2 = (v2 or (v2 shl SHIFT2.toInt())) and MAGIC2
        v2 = (v2 or (v2 shl SHIFT1.toInt())) and MAGIC1
        v2 = (v2 or (v2 shl SHIFT0.toInt())) and MAGIC0

        return (v2 shl 1) or v1
    }

    /** Extract just the even-bits value as a long from the bit-interleaved value  */
    fun deinterleave(b: Long): Long {
        var b = b
        b = b and MAGIC0
        b = (b xor (b ushr SHIFT0.toInt())) and MAGIC1
        b = (b xor (b ushr SHIFT1.toInt())) and MAGIC2
        b = (b xor (b ushr SHIFT2.toInt())) and MAGIC3
        b = (b xor (b ushr SHIFT3.toInt())) and MAGIC4
        b = (b xor (b ushr SHIFT4.toInt())) and MAGIC5
        return b
    }

    /** flip flops odd with even bits  */
    fun flipFlop(b: Long): Long {
        return ((b and MAGIC6) ushr 1) or ((b and MAGIC0) shl 1)
    }

    /** Same as [.zigZagEncode] but on integers.  */
    fun zigZagEncode(i: Int): Int {
        return (i shr 31) xor (i shl 1)
    }

    /**
     * [Zig-zag](https://developers.google.com/protocol-buffers/docs/encoding#types) encode
     * the provided long. Assuming the input is a signed long whose absolute value can be stored on
     * `n` bits, the returned value will be an unsigned long that can be stored on `
     * n+1` bits.
     */
    fun zigZagEncode(l: Long): Long {
        return (l shr 63) xor (l shl 1)
    }

    /** Decode an int previously encoded with [.zigZagEncode].  */
    fun zigZagDecode(i: Int): Int {
        return ((i ushr 1) xor -(i and 1))
    }

    /** Decode a long previously encoded with [.zigZagEncode].  */
    fun zigZagDecode(l: Long): Long {
        return ((l ushr 1) xor -(l and 1L))
    }

    /**
     * Return true if, and only if, the provided integer - treated as an unsigned integer - is either
     * 0 or a power of two.
     */
    fun isZeroOrPowerOfTwo(x: Int): Boolean {
        return (x and (x - 1)) == 0
    }
}

/**
 * Reads 8 bytes in big-endian order from this ByteArray starting at the given offset.
 */
fun ByteArray.getLongBE(offset: Int): Long {
    require(offset >= 0 && offset + 8 <= size) { "Invalid offset: $offset" }
    return ((this[offset].toLong() and 0xFFL) shl 56) or
            ((this[offset + 1].toLong() and 0xFFL) shl 48) or
            ((this[offset + 2].toLong() and 0xFFL) shl 40) or
            ((this[offset + 3].toLong() and 0xFFL) shl 32) or
            ((this[offset + 4].toLong() and 0xFFL) shl 24) or
            ((this[offset + 5].toLong() and 0xFFL) shl 16) or
            ((this[offset + 6].toLong() and 0xFFL) shl 8) or
            (this[offset + 7].toLong() and 0xFFL)
}

/**
 * Reads 4 bytes in big-endian order from this ByteArray starting at the given offset.
 */
fun ByteArray.getIntBE(offset: Int): Int {
    require(offset >= 0 && offset + 4 <= size) { "Invalid offset: $offset" }
    return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
}

/**
 * Sets a 32-bit integer into this ByteArray in big-endian order.
 *
 * @param offset the start index in the array.
 * @param value the integer value to write.
 * @return the written integer value.
 */
fun ByteArray.setIntBE(offset: Int, value: Int) {
    this[offset]     = (value shr 24).toByte()
    this[offset + 1] = (value shr 16).toByte()
    this[offset + 2] = (value shr 8).toByte()
    this[offset + 3] = value.toByte()
}

/**
 * Reads 2 bytes from this ByteArray in big-endian order, starting at [offset],
 * and returns the result as a Short.
 */
fun ByteArray.getShortBE(offset: Int): Short {
    require(offset >= 0 && offset + 2 <= size) { "Invalid offset: $offset" }
    return (((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)).toShort()
}

/**
 * Writes [value] as a big-endian short into this ByteArray at the specified [offset].
 */
fun ByteArray.setShortBE(offset: Int, value: Short) {
    require(offset in 0..(size - 2)) { "Invalid offset $offset for array of size $size" }
    // In big-endian, the most-significant byte is at the lowest address.
    this[offset] = (value.toInt() shr 8).toByte()
    this[offset + 1] = value.toByte()
}

/**
 * Writes [value] as a little-endian short into this ByteArray at the specified [offset].
 */
fun ByteArray.getIntLE(offset: Int, value: Int): Int {
    require(offset in 0..(size - 2)) { "Invalid offset $offset for array of size $size" }
    return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}

/**
 * Writes [value] as a little-endian int into this ByteArray at the specified [offset].
 */
fun ByteArray.setIntLE(offset: Int, value: Int) {
    require(offset in 0..(size - 4)) { "Invalid offset $offset for array of size $size" }
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

/**
 * Writes [value] as a little-endian short into this ByteArray at the specified [offset].
 */
fun ByteArray.putShortLE(offset: Int, value: Short) {
    require(offset in 0..(size - 2)) { "Invalid offset $offset for array of size $size" }
    // In little-endian, the least-significant byte is at the lowest address.
    this[offset] = value.toInt().and(0xFF).toByte()
    this[offset + 1] = (value.toInt().shr(8)).and(0xFF).toByte()
}

/**
 * Writes a 32-bit integer into the byte array at the given [offset] in little-endian order.
 */
fun ByteArray.putIntLE(offset: Int, value: Int) {
    this[offset]     = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

/**
 * Writes the lowest [numBytes] bytes of the [value] into the [ByteArray] in little‑endian order,
 * starting at [offset].
 */
fun ByteArray.putIntLEPartial(offset: Int, value: Int, numBytes: Int) {
    for (i in 0 until numBytes) {
        this[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }
}


/**
 * Writes [value] as a little-endian long into this ByteArray at the specified [offset].
 */
fun ByteArray.putLongLE(offset: Int, value: Long) {
    require(offset in 0..(size - 8)) { "Invalid offset $offset for array of size $size" }
    this[offset] = value.and(0xFF).toByte()
    this[offset + 1] = (value shr 8).and(0xFF).toByte()
    this[offset + 2] = (value shr 16).and(0xFF).toByte()
    this[offset + 3] = (value shr 24).and(0xFF).toByte()
    this[offset + 4] = (value shr 32).and(0xFF).toByte()
    this[offset + 5] = (value shr 40).and(0xFF).toByte()
    this[offset + 6] = (value shr 48).and(0xFF).toByte()
    this[offset + 7] = (value shr 56).and(0xFF).toByte()
}

/**
 * Sets a 64-bit long into this ByteArray in big-endian order.
 *
 * @param offset the start index in the array.
 * @param value the long value to write.
 */
fun ByteArray.setLongBE(offset: Int, value: Long) {
    this[offset]     = (value shr 56).toByte()
    this[offset + 1] = (value shr 48).toByte()
    this[offset + 2] = (value shr 40).toByte()
    this[offset + 3] = (value shr 32).toByte()
    this[offset + 4] = (value shr 24).toByte()
    this[offset + 5] = (value shr 16).toByte()
    this[offset + 6] = (value shr 8).toByte()
    this[offset + 7] = value.toByte()
}

/**
 * Reads 2 bytes in little-endian order from this ByteArray starting at the given offset.
 *
 * @param offset the offset to read from
 * @return the little-endian short value
 */
fun ByteArray.getShortLE(offset: Int): Short {
    require(offset in 0..(size - 2)) { "Invalid offset: $offset for array of size $size" }
    // In little-endian, the first byte is the least-significant.
    val lo = this[offset].toInt() and 0xFF
    val hi = this[offset + 1].toInt() and 0xFF
    return ((hi shl 8) or lo).toShort()
}

/**
 * Writes a 16-bit short into the byte array at the given [offset] in little-endian order.
 *
 * @param offset the start index in the array.
 * @param value the short value to write.
 */
fun ByteArray.setShortLE(offset: Int, value: Short) {
    require(offset in 0..(size - 2)) { "Invalid offset $offset for array of size $size" }
    // In little-endian, the least-significant byte is at the lowest address.
    this[offset] = (value.toInt() and 0xFF).toByte()
    this[offset + 1] = (value.toInt().shr(8) and 0xFF).toByte()
}

/**
 * Reads 4 bytes in little-endian order from this ByteArray starting at the given offset.
 *
 * @param offset the offset to read from
 * @return the little-endian int value
 */
fun ByteArray.getIntLE(offset: Int): Int {
    require(offset >= 0 && offset + 4 <= size) { "Invalid offset: $offset" }
    return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}

/**
 * Reads 8 bytes in little-endian order from this ByteArray starting at the given offset.
 *
 * @param offset the offset to read from
 * @return the little-endian long value
 */
fun ByteArray.getLongLE(offset: Int): Long {
    require(offset >= 0 && offset + 8 <= size) { "Invalid offset: $offset" }
    return (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24) or
            ((this[offset + 4].toLong() and 0xFF) shl 32) or
            ((this[offset + 5].toLong() and 0xFF) shl 40) or
            ((this[offset + 6].toLong() and 0xFF) shl 48) or
            ((this[offset + 7].toLong() and 0xFF) shl 56)
}

/**
 * Writes a 64-bit long into the byte array at the given [offset] in little-endian order.
 *
 * @param offset the start index in the array.
 * @param value the long value to write.
 */
fun ByteArray.setLongLE(offset: Int, value: Long) {
    require(offset in 0..(size - 8)) { "Invalid offset $offset for array of size $size" }
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
    this[offset + 4] = ((value shr 32) and 0xFF).toByte()
    this[offset + 5] = ((value shr 40) and 0xFF).toByte()
    this[offset + 6] = ((value shr 48) and 0xFF).toByte()
    this[offset + 7] = ((value shr 56) and 0xFF).toByte()
}
