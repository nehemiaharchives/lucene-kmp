package org.gnit.lucenekmp.jdkport

/**
 * @param dstBegin the starting index in the destination array (inclusive)
 * @param dstEnd the ending index in the destination array (exclusive)
 */
fun String.Companion.fromCharArray(value: CharArray, offset: Int, count: Int): String {
    // Check that the specified range is valid.
    if (offset < 0 || count < 0 || offset > value.size - count) {
        throw IndexOutOfBoundsException("offset: $offset, count: $count, array size: ${value.size}")
    }
    // Create and return the string from the given subarray.
    return value.copyOfRange(offset, offset + count).concatToString()
}


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
fun String.Companion.fromByteArray(buf: ByteArray, charset: Charset): String {

    return charset.decode(buf)

}
