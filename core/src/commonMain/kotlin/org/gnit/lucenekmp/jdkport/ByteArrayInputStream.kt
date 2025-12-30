package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.math.min

import org.gnit.lucenekmp.jdkport.Objects

/**
 * A `ByteArrayInputStream` contains an internal buffer that contains bytes that may be read
 * from the stream. An internal counter keeps track of the next byte to be supplied by the
 * `read` method.
 */
@Ported(from = "java.io.ByteArrayInputStream")
class ByteArrayInputStream(
    private val buf: ByteArray,
    offset: Int = 0,
    length: Int = buf.size - offset
) : InputStream() {
    private var pos: Int = offset
    private var mark: Int = offset
    private val count: Int = min(offset + length, buf.size)

    override fun read(): Int {
        return if (pos < count) {
            buf[pos++].toInt() and 0xFF
        } else {
            -1
        }
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        Objects.checkFromIndexSize(off, len, b.size)
        if (pos >= count) {
            return -1
        }
        val available = count - pos
        val toCopy = min(len, available)
        if (toCopy <= 0) {
            return 0
        }
        buf.copyInto(b, off, pos, pos + toCopy)
        pos += toCopy
        return toCopy
    }

    override fun skip(n: Long): Long {
        val k = min(n, (count - pos).toLong())
        if (k < 0) {
            return 0
        }
        pos += k.toInt()
        return k
    }

    override fun available(): Int = count - pos

    override fun mark(readlimit: Int) {
        mark = pos
    }

    override fun reset() {
        pos = mark
    }

    override fun markSupported(): Boolean = true
}
