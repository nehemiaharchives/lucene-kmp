package org.gnit.lucenekmp.util

/** Helper methods to ease implementing [Object.toString].  */
object ToStringUtils {
    fun byteArray(buffer: StringBuilder, bytes: ByteArray) {
        for (i in bytes.indices) {
            buffer.append("b[").append(i).append("]=").append(bytes[i].toInt())
            if (i < bytes.size - 1) {
                buffer.append(',')
            }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Unlike [Long.toHexString] returns a String with a "0x" prefix and all the leading
     * zeros.
     */
    fun longHex(x: Long): String {
        var x = x
        val asHex = CharArray(16)
        var i = 16
        while (--i >= 0) {
            asHex[i] = HEX[x.toInt() and 0x0F]
            x = x ushr 4
        }
        return "0x$asHex"
    }

    /**
     * Builds a String with both textual representation of the [BytesRef] data and the bytes hex
     * values. For example: `"hello [68 65 6c 6c 6f]"`. If the content is not a valid UTF-8
     * sequence, only the bytes hex values are returned, as per [BytesRef.toString].
     */
    @Suppress("unused")
    fun bytesRefToString(b: BytesRef?): String {
        if (b == null) {
            return "null"
        }
        try {
            return b.utf8ToString() + " " + b
        } catch (t: AssertionError) {
            // If BytesRef isn't actually UTF-8, or it's e.g. a prefix of UTF-8
            // that ends mid-unicode-char, we fall back to hex:
            return b.toString()
        } catch (t: RuntimeException) {
            return b.toString()
        }
    }

    fun bytesRefToString(b: BytesRefBuilder): String {
        return bytesRefToString(b.get())
    }

    fun bytesRefToString(b: ByteArray): String {
        return bytesRefToString(BytesRef(b))
    }
}
