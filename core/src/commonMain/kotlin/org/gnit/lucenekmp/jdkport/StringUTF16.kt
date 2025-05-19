package org.gnit.lucenekmp.jdkport

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
     * ported from toBytes()
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


    /**
     * {@return Compress the char array (containing UTF16) into a compact strings byte array}
     * If all the chars are LATIN1, it returns an array with len == count,
     * otherwise, it contains UTF16 characters.
     *
     *
     * A UTF16 array is returned *only* if at least 1 non-latin1 character is present.
     * This must be true even if the input array is modified while this method is executing.
     * This is assured by copying the characters while checking for latin1.
     * If all characters are latin1, a byte array with length equals count is returned,
     * indicating all latin1 chars. The scan may be implemented as an intrinsic,
     * which returns the index of the first non-latin1 character.
     * When the first non-latin1 character is found, it switches to creating a new
     * buffer; the saved prefix of latin1 characters is copied to the new buffer;
     * and the remaining input characters are copied to the buffer.
     * The index of the known non-latin1 character is checked, if it is latin1,
     * the input has been changed. In this case, a second attempt is made to compress to
     * latin1 from the copy made in the first pass to the originally allocated latin1 buffer.
     * If it succeeds the return value is latin1, otherwise, the utf16 value is returned.
     * In this unusual case, the result is correct for the snapshot of the value.
     * The resulting string contents are unspecified if the input array is modified during this
     * operation, but it is ensured that at least 1 non-latin1 character is present in
     * the non-latin1 buffer.
     *
     * @param src   a char array
     * @param off   starting offset
     * @param count count of chars to be compressed, `count` > 0
     */
    fun compress(src: CharArray, off: Int, count: Int): ByteArray {
        val latin1 = ByteArray(count)
        val ndx: Int = compressCharToChar(src, off, latin1, 0, count)
        if (ndx != count) {
            // Switch to UTF16
            val utf16: ByteArray = toBytes(src, off, count)
            // If the original character that was found to be non-latin1 is latin1 in the copy
            // try to make a latin1 string from the copy
            if (getChar(utf16, ndx).code > 0xff
                || compressByteToByte(utf16, 0, latin1, 0, count) != count
            ) {
                return utf16
            }
        }
        return latin1 // latin1 success
    }

    // compressedCopy char[] -> byte[]
    fun compressCharToChar(src: CharArray, srcOff: Int, dst: ByteArray, dstOff: Int, len: Int): Int {
        var srcOff = srcOff
        var dstOff = dstOff
        for (i in 0..<len) {
            val c = src[srcOff]
            if (c.code > 0xff) {
                return i // return index of non-latin1 char
            }
            dst[dstOff] = c.code.toByte()
            srcOff++
            dstOff++
        }
        return len
    }

    // compressedCopy byte[] -> byte[]
    fun compressByteToByte(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, len: Int): Int {
        // We need a range check here because 'getChar' has no checks
        var srcOff = srcOff
        var dstOff = dstOff

        // TODO implement if possible
        // checkBoundsOffCount(srcOff, len, src)

        for (i in 0..<len) {
            val c: Char = getChar(src, srcOff)
            if (c.code > 0xff) {
                return i // return index of non-latin1 char
            }
            dst[dstOff] = c.code.toByte()
            srcOff++
            dstOff++
        }
        return len
    }

     // intrinsic performs no bounds checks
    fun getChar(`val`: ByteArray, index: Int): Char {
        var index = index
        require(index >= 0 && index < length(`val`)) { "Trusted caller missed bounds check" }
        index = index shl 1
        return (((`val`[index++].toInt() and 0xff) shl HI_BYTE_SHIFT) or
                ((`val`[index].toInt() and 0xff) shl LO_BYTE_SHIFT)).toChar()
    }

    fun charAt(value: ByteArray, index: Int): Char {
        // implement if needed
        //checkIndex(index, value)

        return getChar(value, index)
    }

    // inflatedCopy byte[] -> byte[]
    fun inflate(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, len: Int) {
        // We need a range check here because 'putChar' has no checks
        var srcOff = srcOff
        var dstOff = dstOff

        // implement if needed
        //checkBoundsOffCount(dstOff, len, dst)

        for (i in 0..<len) {
            putChar(dst, dstOff++, src[srcOff++].toInt() and 0xff)
        }
    }
}
