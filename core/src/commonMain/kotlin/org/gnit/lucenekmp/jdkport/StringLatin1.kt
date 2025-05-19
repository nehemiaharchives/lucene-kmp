package org.gnit.lucenekmp.jdkport

object StringLatin1 {
    fun inflate(value: ByteArray, off: Int, len: Int): ByteArray {
        val ret: ByteArray = StringUTF16.newBytesFor(len)
        inflate(value, off, ret, 0, len)
        return ret
    }

    fun inflate(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, len: Int) {
        StringUTF16.inflate(src, srcOff, dst, dstOff, len)
    }
}