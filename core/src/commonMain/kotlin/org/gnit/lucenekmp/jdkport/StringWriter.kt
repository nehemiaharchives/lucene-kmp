package org.gnit.lucenekmp.jdkport

class StringWriter : Writer() {
    private val builder = StringBuilder()

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        if (off < 0 || off > cbuf.size || len < 0 || off + len > cbuf.size || off + len < 0) {
            throw IndexOutOfBoundsException()
        }
        if (len == 0) {
            return
        }
        builder.appendRange(cbuf, off, off + len)
    }

    override fun flush() {
        // No-op for StringWriter
    }

    override fun close() {
        // No-op for StringWriter
    }

    override fun toString(): String {
        return builder.toString()
    }
}
