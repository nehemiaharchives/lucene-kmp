package org.gnit.lucenekmp.jdkport

@Ported(from = "java.lang.StringLatin1")
object StringLatin1 {

    fun inflate(value: ByteArray, off: Int, len: Int): ByteArray {
        val ret: ByteArray = StringUTF16.newBytesFor(len)
        inflate(value, off, ret, 0, len)
        return ret
    }

    fun inflate(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, len: Int) {
        StringUTF16.inflate(src, srcOff, dst, dstOff, len)
    }

    fun newString(`val`: ByteArray, index: Int, len: Int): String {
        if (len == 0) {
            return ""
        }

        // try to mimic JDK behavior but kotlin original implementation. originally following code:
        /*
        return new String(Arrays.copyOfRange(val, index, index + len), LATIN1);
        */
        return String.fromByteArray(
            Arrays.copyOfRange(`val`, index, index + len),
            StandardCharsets.ISO_8859_1
        )
    }
}