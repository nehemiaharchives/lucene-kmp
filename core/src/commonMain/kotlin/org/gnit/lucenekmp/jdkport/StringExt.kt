package org.gnit.lucenekmp.jdkport

/**
 * @param dstBegin the starting index in the destination array (inclusive)
 * @param dstEnd the ending index in the destination array (exclusive)
 */
/*fun String.Companion.fromCharArray(value: CharArray, offset: Int, count: Int): String {
    // Check that the specified range is valid.
    if (offset < 0 || count < 0 || offset > value.size - count) {
        throw IndexOutOfBoundsException("offset: $offset, count: $count, array size: ${value.size}")
    }
    // Create and return the string from the given subarray.
    return value.copyOfRange(offset, offset + count).concatToString()
}*/


/**
 * Returns an array of Latin1 characters from this string.
 *
 * This implementation is designed for Latin1 strings only.
 * Each character is assumed to be a single code unit in the range 0..255.
 */
fun String.codePoints(): CharArray =
    CharArray(length) { index -> (this[index].code and 0xff).toChar() }


/**
 * Converts a byte array to a string using Latin1 encoding.
 *
 * This implementation assumes that the byte array contains valid Latin1 characters.
 * Each byte is converted to a character using the Latin1 mapping.
 */
fun String.Companion.fromByteArray(buf: ByteArray, charset: Charset = Charset.UTF_8): String {

    return charset.decode(buf)

}

fun String.Companion.fromCharArray(value: CharArray, offset: Int, count: Int): String {
    return fromByteArray(buf = StringUTF16.toBytes(value, offset, count))
}

object StringUTF16 {
    private var HI_BYTE_SHIFT: Int
    private var LO_BYTE_SHIFT: Int

    init {
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            HI_BYTE_SHIFT = 8;
            LO_BYTE_SHIFT = 0;
        } else {
            HI_BYTE_SHIFT = 0;
            LO_BYTE_SHIFT = 8;
        }
    }

    /**
     * ported from java.lang.StringUTF16.toBytes()
     */
    fun toBytes(value: CharArray, off: Int, len: Int): ByteArray {
        val `val`: ByteArray = newBytesFor(len)

        var offset = off

        for (i in 0..<len) {
            putChar(`val`, i, value[offset].code)
            offset++
        }
        return `val`
    }

    // Return a new byte array for a UTF16-coded string for len chars
    // Throw an exception if out of range
    fun newBytesFor(len: Int): ByteArray {
        return ByteArray(newBytesLength(len))
    }


    // Check the size of a UTF16-coded string
    // Throw an exception if out of range
    fun newBytesLength(len: Int): Int {

        val MAX_LENGTH = Int.Companion.MAX_VALUE shr 1

        if (len < 0) {
            throw /*NegativeArraySize*/Exception()
        }
        if (len >= MAX_LENGTH) {
            throw /*OutOfMemory*/Error(
                "UTF16 String size is " + len +
                        ", should be less than " + MAX_LENGTH
            )
        }
        return len shl 1
    }

    fun putChar(`val`: ByteArray, index: Int, c: Int) {
        var index = index
        require(index >= 0 && index < length(`val`)) { "Trusted caller missed bounds check" }
        index = index shl 1
        `val`[index++] = (c shr HI_BYTE_SHIFT).toByte()
        `val`[index] = (c shr LO_BYTE_SHIFT).toByte()
    }

    fun length(value: ByteArray): Int {
        return value.size shr 1
    }


}

