package org.gnit.lucenekmp.util

/**
 * Represents byte[], as a slice (offset + length) into an existing byte[]. The [bytes]
 * property should never be null; use [EMPTY_BYTES] if necessary.
 *
 * Important note: Unless otherwise noted, Lucene uses this class to represent terms that
 * are encoded as UTF8 bytes in the index. To convert them to a String, use [utf8ToString].
 *
 * BytesRef implements [Comparable]. The underlying byte arrays are sorted
 * lexicographically, numerically treating elements as unsigned.
 */
class BytesRef : Comparable<BytesRef> {
    companion object {
        /** An empty byte array for convenience */
        val EMPTY_BYTES = ByteArray(0)

        /**
         * Creates a new BytesRef that points to a copy of the bytes from [other]
         *
         * The returned BytesRef will have a length of other.length and an offset of zero.
         */
        fun deepCopyOf(other: BytesRef): BytesRef {
            return BytesRef(
                copyOfSubArray(other.bytes, other.offset, other.offset + other.length),
                0,
                other.length
            )
        }

        // Platform-independent implementation of Arrays.copyOfSubArray
        private fun copyOfSubArray(array: ByteArray, start: Int, end: Int): ByteArray {
            val length = end - start
            val result = ByteArray(length)
            array.copyInto(result, 0, start, end)
            return result
        }
    }

    /** The contents of the BytesRef. Should never be null. */
    var bytes: ByteArray

    /** Offset of first valid byte. */
    var offset: Int

    /** Length of used bytes. */
    var length: Int

    /** Create a BytesRef with [EMPTY_BYTES] */
    constructor() : this(EMPTY_BYTES)

    /** This instance will directly reference bytes w/o making a copy. bytes should not be null. */
    constructor(bytes: ByteArray, offset: Int, length: Int) {
        this.bytes = bytes
        this.offset = offset
        this.length = length
        check(isValid())
    }

    /** This instance will directly reference bytes w/o making a copy. bytes should not be null */
    constructor(bytes: ByteArray) : this(bytes, 0, bytes.size)

    /**
     * Create a BytesRef pointing to a new array of size [capacity]. Offset and length will
     * both be zero.
     */
    constructor(capacity: Int) {
        bytes = if (capacity == 0) EMPTY_BYTES else ByteArray(capacity)
        offset = 0
        length = 0
    }

    /**
     * Initialize the byte[] from the UTF8 bytes for the provided String.
     *
     * @param text This must be well-formed unicode text, with no unpaired surrogates.
     */
    constructor(text: CharSequence) {
        bytes = ByteArray(maxUTF8Length(text.length))
        offset = 0
        length = utf16ToUTF8(text, bytes)
    }

    /**
     * Expert: compares the bytes against another BytesRef, returning true if the bytes are equal.
     */
    fun bytesEquals(other: BytesRef): Boolean {
        if (this.length != other.length) return false

        for (i in 0 until this.length) {
            if (this.bytes[this.offset + i] != other.bytes[other.offset + i]) {
                return false
            }
        }
        return true
    }

    /**
     * Returns a shallow clone of this instance (the underlying bytes are not copied and will
     * be shared by both the returned object and this object.
     */
    fun clone(): BytesRef {
        return BytesRef(bytes, offset, length)
    }

    /**
     * Calculates the hash code.
     */
    override fun hashCode(): Int {
        // Simple implementation - could be replaced with a better hash function
        var result = 1
        for (i in offset until offset + length) {
            result = 31 * result + bytes[i].toInt()
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other is BytesRef) {
            return this.bytesEquals(other)
        }
        return false
    }

    /**
     * Interprets stored bytes as UTF-8 bytes, returning the resulting string.
     */
    fun utf8ToString(): String {
        // This would need a proper multiplatform UTF-8 decoder implementation
        // Simplified placeholder
        return utf8BytesToString(bytes, offset, length)
    }

    /** Returns hex encoded bytes, e.g. "[6c 75 63 65 6e 65]" */
    override fun toString(): String {
        val sb = StringBuilder(2 + 3 * length)
        sb.append('[')
        val end = offset + length
        for (i in offset until end) {
            if (i > offset) {
                sb.append(' ')
            }
            sb.append((bytes[i].toInt() and 0xff).toString(16))
        }
        sb.append(']')
        return sb.toString()
    }

    /** Unsigned byte order comparison */
    override fun compareTo(other: BytesRef): Int {
        val thisEnd = this.offset + this.length
        val otherEnd = other.offset + other.length
        var i = this.offset
        var j = other.offset

        while (i < thisEnd && j < otherEnd) {
            val a = this.bytes[i].toInt() and 0xff
            val b = other.bytes[j].toInt() and 0xff
            if (a != b) {
                return a - b
            }
            i++
            j++
        }

        return this.length - other.length
    }

    /** Performs internal consistency checks. Always returns true (or throws exception) */
    fun isValid(): Boolean {
        require(bytes.isNotEmpty()) { "bytes is null or empty" }
        require(length >= 0) { "length is negative: $length" }
        require(length <= bytes.size) { "length is out of bounds: $length, bytes.size=${bytes.size}" }
        require(offset >= 0) { "offset is negative: $offset" }
        require(offset <= bytes.size) { "offset out of bounds: $offset, bytes.size=${bytes.size}" }
        require(offset + length >= 0) { "offset+length is negative: offset=$offset, length=$length" }
        require(offset + length <= bytes.size) {
            "offset+length out of bounds: offset=$offset, length=$length, bytes.size=${bytes.size}"
        }
        return true
    }

    /**
     * Calculates the maximum possible length of a UTF-8 encoded string based on its UTF-16 length.
     * In UTF-8, a single character can take up to 4 bytes (not 3 as in the comment).
     */
    private fun maxUTF8Length(utf16Length: Int): Int {
        // Each UTF-16 character can require up to 4 bytes in UTF-8
        return utf16Length * 4
    }

    /**
     * Converts a CharSequence (UTF-16) to UTF-8 bytes.
     * Returns the number of bytes written to the array.
     */
    private fun utf16ToUTF8(text: CharSequence, bytes: ByteArray): Int {
        var byteIndex = 0

        var i = 0
        while (i < text.length) {
            val char = text[i]
            val codePoint = char.code

            // Single byte (0xxxxxxx): ASCII character
            if (codePoint < 0x80) {
                bytes[byteIndex++] = codePoint.toByte()
                i++
            }
            // Two bytes (110xxxxx 10xxxxxx): characters in range 0x80-0x7FF
            else if (codePoint < 0x800) {
                bytes[byteIndex++] = (0xC0 or (codePoint shr 6)).toByte()
                bytes[byteIndex++] = (0x80 or (codePoint and 0x3F)).toByte()
                i++
            }
            // Three bytes (1110xxxx 10xxxxxx 10xxxxxx): characters in range 0x800-0xFFFF (most common for non-ASCII)
            else if (codePoint < 0x10000) {
                bytes[byteIndex++] = (0xE0 or (codePoint shr 12)).toByte()
                bytes[byteIndex++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                bytes[byteIndex++] = (0x80 or (codePoint and 0x3F)).toByte()
                i++
            }
            // Four bytes (11110xxx 10xxxxxx 10xxxxxx 10xxxxxx): surrogate pairs
            // This handles surrogate pairs that represent code points in range 0x10000-0x10FFFF
            else if (i + 1 < text.length && char.isHighSurrogate() && text[i + 1].isLowSurrogate()) {
                val highSurrogate = char.code
                val lowSurrogate = text[i + 1].code
                val codePoint = 0x10000 + ((highSurrogate - 0xD800) shl 10) + (lowSurrogate - 0xDC00)

                bytes[byteIndex++] = (0xF0 or (codePoint shr 18)).toByte()
                bytes[byteIndex++] = (0x80 or ((codePoint shr 12) and 0x3F)).toByte()
                bytes[byteIndex++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                bytes[byteIndex++] = (0x80 or (codePoint and 0x3F)).toByte()

                i += 2 // Skip both high and low surrogates
            } else {
                // Handle invalid characters
                i++
            }
        }

        return byteIndex
    }

    /**
     * Converts UTF-8 bytes to a String.
     */
    private fun utf8BytesToString(bytes: ByteArray, offset: Int, length: Int): String {
        val chars = mutableListOf<Char>()
        var i = offset
        val end = offset + length

        while (i < end) {
            val byte = bytes[i++].toInt() and 0xFF

            // Single byte (0xxxxxxx): ASCII character
            if (byte and 0x80 == 0) {
                chars.add(byte.toChar())
            }
            // Two bytes (110xxxxx 10xxxxxx)
            else if (byte and 0xE0 == 0xC0) {
                if (i >= end) break
                val byte2 = bytes[i++].toInt() and 0xFF

                val codePoint = ((byte and 0x1F) shl 6) or (byte2 and 0x3F)
                chars.add(codePoint.toChar())
            }
            // Three bytes (1110xxxx 10xxxxxx 10xxxxxx)
            else if (byte and 0xF0 == 0xE0) {
                if (i + 1 >= end) break
                val byte2 = bytes[i++].toInt() and 0xFF
                val byte3 = bytes[i++].toInt() and 0xFF

                val codePoint = ((byte and 0x0F) shl 12) or
                        ((byte2 and 0x3F) shl 6) or
                        (byte3 and 0x3F)
                chars.add(codePoint.toChar())
            }
            // Four bytes (11110xxx 10xxxxxx 10xxxxxx 10xxxxxx)
            else if (byte and 0xF8 == 0xF0) {
                if (i + 2 >= end) break
                val byte2 = bytes[i++].toInt() and 0xFF
                val byte3 = bytes[i++].toInt() and 0xFF
                val byte4 = bytes[i++].toInt() and 0xFF

                val codePoint = ((byte and 0x07) shl 18) or
                        ((byte2 and 0x3F) shl 12) or
                        ((byte3 and 0x3F) shl 6) or
                        (byte4 and 0x3F)

                // Convert to surrogate pair
                val highSurrogate = (((codePoint - 0x10000) shr 10) + 0xD800).toChar()
                val lowSurrogate = (((codePoint - 0x10000) and 0x3FF) + 0xDC00).toChar()

                chars.add(highSurrogate)
                chars.add(lowSurrogate)
            }
        }

        return chars.joinToString("")
    }
}