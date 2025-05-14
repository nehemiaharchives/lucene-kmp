package org.gnit.lucenekmp.jdkport

class ISO_8859_1 : Charset("ISO-8859-1", StandardCharsets.aliases_ISO_8859_1()) {
    override fun contains(cs: Charset): Boolean {
        // LATIN1 contains only itself.
        return cs === this
    }

    override fun decode(bytes: ByteArray): String {
        val chars = CharArray(bytes.size) { index ->
            (bytes[index].toInt() and 0xFF).toChar()
        }

        return chars.concatToString()
    }

    override fun encode(str: String): ByteArray {
        val bytes = ByteArray(str.length) { index ->
            str[index].code.toByte()
        }

        return bytes
    }

    override fun newDecoder(): CharsetDecoder {
        TODO("Not yet implemented")
    }
}
